(ns ccc.preproc
  "C preprocessor operating on lexer tokens. Hideset-based macro expansion."
  (:refer-clojure :exclude [ident?])
  (:require [ccc.lexer :as lx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set])
  (:import [java.util ArrayDeque]))

(def predefined-src
  "#define __STDC__ 1
#define __STDC_VERSION__ 201112L
#define __STDC_HOSTED__ 1
#define __x86_64__ 1
#define __x86_64 1
#define __amd64__ 1
#define __amd64 1
#define __LP64__ 1
#define _LP64 1
#define __linux__ 1
#define __linux 1
#define __unix__ 1
#define __unix 1
#define __ELF__ 1
#define __SIZE_TYPE__ long unsigned int
#define __UINTMAX_TYPE__ long unsigned int
#define __UINTPTR_TYPE__ long unsigned int
#define __UINT64_TYPE__ long unsigned int
#define __PTRDIFF_TYPE__ long int
#define __INTMAX_TYPE__ long int
#define __INTPTR_TYPE__ long int
#define __INT64_TYPE__ long int
#define __INT8_TYPE__ signed char
#define __INT16_TYPE__ short int
#define __INT32_TYPE__ int
#define __UINT8_TYPE__ unsigned char
#define __UINT16_TYPE__ short unsigned int
#define __UINT32_TYPE__ unsigned int
#define __WCHAR_TYPE__ int
#define __WINT_TYPE__ unsigned int
#define __CHAR_BIT__ 8
#define __SCHAR_MAX__ 0x7f
#define __SHRT_MAX__ 0x7fff
#define __INT_MAX__ 0x7fffffff
#define __LONG_MAX__ 0x7fffffffffffffffL
#define __PTRDIFF_MAX__ 0x7fffffffffffffffL
#define __INTMAX_MAX__ 0x7fffffffffffffffL
#define __INTPTR_MAX__ 0x7fffffffffffffffL
#define __INT64_MAX__ 0x7fffffffffffffffL
#define __LONG_LONG_MAX__ 0x7fffffffffffffffLL
#define __SIZE_MAX__ 0xffffffffffffffffUL
#define __UINTMAX_MAX__ 0xffffffffffffffffUL
#define __UINTPTR_MAX__ 0xffffffffffffffffUL
#define __UINT64_MAX__ 0xffffffffffffffffUL
#define __INT8_MAX__ 0x7f
#define __INT16_MAX__ 0x7fff
#define __INT32_MAX__ 0x7fffffff
#define __UINT8_MAX__ 0xff
#define __UINT16_MAX__ 0xffff
#define __UINT32_MAX__ 0xffffffffU
#define __WCHAR_MAX__ 0x7fffffff
#define __WCHAR_MIN__ (-__WCHAR_MAX__ - 1)
#define __SIZEOF_SHORT__ 2
#define __SIZEOF_INT__ 4
#define __SIZEOF_FLOAT__ 4
#define __SIZEOF_WCHAR_T__ 4
#define __SIZEOF_LONG__ 8
#define __SIZEOF_LONG_LONG__ 8
#define __SIZEOF_POINTER__ 8
#define __SIZEOF_DOUBLE__ 8
#define __SIZEOF_SIZE_T__ 8
#define __SIZEOF_PTRDIFF_T__ 8
#define __ORDER_LITTLE_ENDIAN__ 1234
#define __ORDER_BIG_ENDIAN__ 4321
#define __ORDER_PDP_ENDIAN__ 3412
#define __BYTE_ORDER__ __ORDER_LITTLE_ENDIAN__
#define __FLOAT_WORD_ORDER__ __ORDER_LITTLE_ENDIAN__
")

(defn perr [tk msg]
  (throw (ex-info (str (:file tk) ":" (:line tk) ": error: " msg) {:type :compile-error})))

(def ^:dynamic *macros* nil)    ; atom name -> macro
(def ^:dynamic *includes* nil)  ; vector of include dirs
(def ^:dynamic *once* nil)      ; atom set of files with #pragma once
(def ^:dynamic *guards* nil)    ; atom file -> guard macro name
(def ^:dynamic *counter* nil)   ; atom for __COUNTER__

(def eof-tok {:kind :eof :text "" :line 0 :file ""})

(defn ident? [tk] (= :ident (:kind tk)))
(defn is? [tk s] (and (#{:punct :ident} (:kind tk)) (= s (:text tk))))

;; ---------------------------------------------------------------------------
;; Reading directive lines

(defn read-line-toks
  "Pop tokens until the next token at beginning of line. Returns vector."
  [^ArrayDeque dq]
  (loop [acc []]
    (let [tk (.peekFirst dq)]
      (if (or (nil? tk) (:bol tk) (= :eof (:kind tk)))
        acc
        (do (.pollFirst dq) (recur (conj acc tk)))))))

(defn push-front! [^ArrayDeque dq toks]
  (doseq [tk (rseq (vec toks))] (.addFirst dq tk)))

;; ---------------------------------------------------------------------------
;; Stringize / paste

(defn quote-str [^String s]
  (str "\"" (-> s (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))

(defn tokens-spelling [toks]
  (let [sb (StringBuilder.)]
    (doseq [[i tk] (map-indexed vector toks)]
      (when (and (pos? i) (:space tk)) (.append sb " "))
      (.append sb (:text tk)))
    (str sb)))

(defn stringize [toks origin]
  (let [s (tokens-spelling (map (fn [t] (if (#{:str :char} (:kind t))
                                          (assoc t :text (-> (:text t) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")))
                                          t))
                                toks))
        s (str "\"" s "\"")]
    (assoc origin :kind :str :text s :prefix "" :hideset #{})))

(defn paste [lhs rhs]
  (let [s (str (:text lhs) (:text rhs))
        toks (try (lx/tokenize s (:file lhs)) (catch Exception _ nil))]
    (if (and toks (= 2 (count toks)))
      (merge (first toks) {:line (:line lhs) :file (:file lhs) :bol false :space (:space lhs)
                           :hideset (or (:hideset lhs) #{})})
      (perr lhs (str "pasting \"" (:text lhs) "\" and \"" (:text rhs) "\" does not give a valid preprocessing token")))))

;; ---------------------------------------------------------------------------
;; Macro expansion

(declare expand-list)

(defn read-macro-args
  "dq positioned just after macro name; next is '('. Returns [args rparen] where
   args is vector of token vectors. nparams = number of declared params."
  [^ArrayDeque dq macro name-tok]
  (.pollFirst dq) ; (
  (loop [args [] cur [] depth 0]
    (let [tk (.pollFirst dq)]
      (cond
        (or (nil? tk) (= :eof (:kind tk)))
        (perr name-tok (str "unterminated argument list invoking macro '" (:name macro) "'"))
        (and (zero? depth) (is? tk ")"))
        [(conj args cur) tk]
        (and (zero? depth) (is? tk ",")
             (not (and (:variadic macro) (>= (count args) (count (:params macro))))))
        (recur (conj args cur) [] depth)
        :else
        (recur args (conj cur tk)
               (cond (is? tk "(") (inc depth) (is? tk ")") (dec depth) :else depth))))))

(defn bind-args [macro args name-tok]
  (let [params (:params macro)
        np (count params)
        args (if (and (= 1 (count args)) (empty? (first args)) (zero? np)) [] args)]
    (if (:variadic macro)
      (let [named (take np args)
            _ (when (< (count args) np)
                (when-not (= (count args) np)
                  (when (< (count args) np) (perr name-tok "macro requires more arguments"))))
            va (if (> (count args) np) (nth args np) [])]
        (assoc (zipmap params named) (:va-name macro) va))
      (do
        (when (not= (count args) np)
          (if (and (zero? np) (= 1 (count args)) (empty? (first args)))
            nil
            (perr name-tok (str "macro '" (:name macro) "' passed " (count args) " arguments, but takes " np))))
        (zipmap params args)))))

(defn raw-or-pm [toks origin]
  (if (empty? toks)
    [{:kind :placemarker :text "" :line (:line origin) :file (:file origin)}]
    (assoc-in (vec toks) [0 :space] (:space origin))))

(defn subst [macro argmap origin]
  (let [body (:body macro)
        n (count body)
        expanded (memoize (fn [p] (expand-list (get argmap p))))
        arg? #(and (ident? %) (contains? argmap (:text %)))
        va-name (:va-name macro)]
    (loop [i 0 out []]
      (if (>= i n)
        (filterv #(not= :placemarker (:kind %)) out)
        (let [tk (nth body i)
              nxt (when (< (inc i) n) (nth body (inc i)))]
          (cond
            ;; # param
            (and (is? tk "#") nxt (arg? nxt))
            (recur (+ i 2) (conj out (assoc (stringize (get argmap (:text nxt)) tk) :space (:space tk))))

            ;; GNU , ## __VA_ARGS__
            (and (is? tk ",") nxt (is? nxt "##") (< (+ i 2) n)
                 (:variadic macro) (= va-name (:text (nth body (+ i 2)))))
            (let [va (get argmap va-name)]
              (if (empty? va)
                (recur (+ i 3) out)
                (recur (+ i 3) (into (conj out tk) va))))

            ;; ## rhs
            (is? tk "##")
            (do
              (when (or (empty? out) (nil? nxt)) (perr tk "'##' cannot appear at either end of macro expansion"))
              (let [rhs (if (arg? nxt) (raw-or-pm (get argmap (:text nxt)) nxt) [nxt])
                    lhs (peek out)
                    p (cond (= :placemarker (:kind lhs)) (first rhs)
                            (= :placemarker (:kind (first rhs))) lhs
                            :else (paste lhs (first rhs)))]
                (recur (+ i 2) (into (conj (pop out) p) (rest rhs)))))

            ;; __VA_OPT__
            (and (is? tk "__VA_OPT__") (:variadic macro) nxt (is? nxt "("))
            (let [[inner j] (loop [j (+ i 2) depth 0 acc []]
                              (let [t2 (nth body j)]
                                (cond (and (zero? depth) (is? t2 ")")) [acc (inc j)]
                                      :else (recur (inc j) (cond (is? t2 "(") (inc depth) (is? t2 ")") (dec depth) :else depth)
                                                   (conj acc t2)))))]
              (if (empty? (get argmap va-name))
                (recur j out)
                (recur j (into out (subst (assoc macro :body inner) argmap origin)))))

            ;; param followed by ##: raw tokens (or a placemarker)
            (and (arg? tk) nxt (is? nxt "##"))
            (recur (inc i) (into out (raw-or-pm (get argmap (:text tk)) tk)))

            (arg? tk)
            (let [e (expanded (:text tk))]
              (recur (inc i) (into out (if (seq e) (assoc-in e [0 :space] (:space tk)) e))))

            :else (recur (inc i) (conj out tk))))))))

(defn expand-step!
  "tk was just popped from dq. If it is a macro invocation, push its expansion
   onto dq and return true."
  [^ArrayDeque dq tk]
  (when (ident? tk)
    (let [name (:text tk)
          hs (or (:hideset tk) #{})]
      (cond
        (= name "__COUNTER__")
        (do (.addFirst dq (assoc tk :kind :pnum :text (str (dec (swap! *counter* inc))) :hideset hs)) true)
        (= name "__LINE__")
        (do (.addFirst dq (assoc tk :kind :pnum :text (str (:line tk)) :hideset hs)) true)
        (= name "__FILE__")
        (do (.addFirst dq (assoc tk :kind :str :text (quote-str (str (:file tk))) :prefix "" :hideset hs)) true)
        (contains? hs name) false
        :else
        (when-let [m (get @*macros* name)]
          (if (nil? (:params m))
            ;; object-like
            (let [hs2 (conj hs name)
                  raw (if (some #(is? % "##") (:body m)) (subst m {} tk) (:body m))
                  body (mapv #(assoc % :hideset (into (or (:hideset %) #{}) hs2)
                                     :line (:line tk) :file (:file tk) :bol false)
                             raw)
                  body (if (seq body) (assoc body 0 (assoc (body 0) :space (:space tk))) body)]
              (push-front! dq body)
              true)
            ;; function-like
            (let [nx (.peekFirst dq)]
              (when (and nx (is? nx "("))
                (let [[args rparen] (read-macro-args dq m tk)
                      argmap (bind-args m args tk)
                      hs2 (conj (clojure.set/intersection hs (or (:hideset rparen) #{})) name)
                      body (subst m argmap tk)
                      body (mapv #(assoc % :hideset (into (or (:hideset %) #{}) hs2)
                                         :line (:line tk) :file (:file tk) :bol false)
                                 body)
                      body (if (seq body) (assoc body 0 (assoc (body 0) :space (:space tk))) body)]
                  (push-front! dq body)
                  true)))))))))

(defn expand-list
  "Fully macro-expand a token vector (no directives)."
  [toks]
  (let [dq (ArrayDeque.)]
    (doseq [tk toks] (.addLast dq tk))
    (.addLast dq eof-tok)
    (loop [out []]
      (let [tk (.pollFirst dq)]
        (if (= :eof (:kind tk))
          out
          (if (expand-step! dq tk)
            (recur out)
            (recur (conj out tk))))))))

;; ---------------------------------------------------------------------------
;; #if expression evaluation

(defn pp-number-value [tk]
  (let [text (str/lower-case (:text tk))
        m (re-matches #"(0x[0-9a-f]+|0b[01]+|[0-9]+)([ul]*)" text)]
    (when-not m (perr tk (str "invalid integer in #if: " (:text tk))))
    (let [d (nth m 1)
          [base ds] (cond (str/starts-with? d "0x") [16 (subs d 2)]
                          (str/starts-with? d "0b") [2 (subs d 2)]
                          (and (> (count d) 1) (str/starts-with? d "0")) [8 (subs d 1)]
                          :else [10 d])
          big (BigInteger. ^String ds (int base))]
      [(.longValue big) (or (str/includes? (nth m 2) "u")
                            (> (.bitLength big) 63))])))

(defn eval-if-tokens [toks origin]
  (let [toks (vec toks)
        pos (volatile! 0)
        peek* (fn [] (get toks @pos))
        adv (fn [] (let [t (get toks @pos)] (vswap! pos inc) t))
        isp (fn [s] (let [t (peek*)] (and t (is? t s))))
        err (fn [msg] (perr origin (str "#if: " msg)))
        noeval (volatile! 0)
        div0 (fn [] (if (pos? @noeval) true (err "division by zero")))
        skipping (fn [f] (vswap! noeval inc) (let [r (f)] (vswap! noeval dec) r))]
    (letfn [(primary []
              (let [t (adv)]
                (cond
                  (nil? t) (err "unexpected end of expression")
                  (is? t "(") (let [v (cond-expr)] (when-not (isp ")") (err "expected )")) (adv) v)
                  (= :pnum (:kind t)) (pp-number-value t)
                  (= :char (:kind t)) [(lx/char-value t) false]
                  (ident? t) [0 false]
                  :else (err (str "unexpected token " (:text t))))))
            (unary []
              (cond
                (isp "+") (do (adv) (unary))
                (isp "-") (do (adv) (let [[v u] (unary)] [(unchecked-negate v) u]))
                (isp "~") (do (adv) (let [[v u] (unary)] [(bit-not v) u]))
                (isp "!") (do (adv) (let [[v _] (unary)] [(if (zero? v) 1 0) false]))
                :else (primary)))
            (binary [next ops]
              (loop [[a ua] (next)]
                (let [t (peek*)]
                  (if-let [f (and t (= :punct (:kind t)) (get ops (:text t)))]
                    (do (adv) (let [[b ub] (next)] (recur (f a b (or ua ub)))))
                    [a ua]))))
            (cmp [f] (fn [a b u] [(if (f (if u (Long/compareUnsigned a b) (compare a b))) 1 0) false]))
            (mul [] (binary unary {"*" (fn [a b u] [(unchecked-multiply a b) u])
                                   "/" (fn [a b u] (if (zero? b) (do (div0) [0 u])
                                         [(if u (Long/divideUnsigned a b) (quot a b)) u]))
                                   "%" (fn [a b u] (if (zero? b) (do (div0) [0 u])
                                         [(if u (Long/remainderUnsigned a b) (rem a b)) u]))}))
            (add [] (binary mul {"+" (fn [a b u] [(unchecked-add a b) u])
                                 "-" (fn [a b u] [(unchecked-subtract a b) u])}))
            (shift [] (binary add {"<<" (fn [a b u] [(bit-shift-left a b) u])
                                   ">>" (fn [a b u] [(if u (unsigned-bit-shift-right a b) (bit-shift-right a b)) u])}))
            (rel [] (binary shift {"<" (cmp neg?) "<=" (cmp #(<= % 0)) ">" (cmp pos?) ">=" (cmp #(>= % 0))}))
            (eq [] (binary rel {"==" (cmp zero?) "!=" (cmp #(not (zero? %)))}))
            (band [] (binary eq {"&" (fn [a b u] [(bit-and a b) u])}))
            (bxor [] (binary band {"^" (fn [a b u] [(bit-xor a b) u])}))
            (bor [] (binary bxor {"|" (fn [a b u] [(bit-or a b) u])}))
            (land [] (loop [[a ua] (bor) seen false]
                       (if (isp "&&")
                         (do (adv) (let [[b _] (if (zero? a) (skipping bor) (bor))]
                                     (recur [(if (and (not (zero? a)) (not (zero? b))) 1 0) false] true)))
                         [a (if seen false ua)])))
            (lor [] (loop [[a ua] (land) seen false]
                      (if (isp "||")
                        (do (adv) (let [[b _] (if (zero? a) (land) (skipping land))]
                                    (recur [(if (or (not (zero? a)) (not (zero? b))) 1 0) false] true)))
                        [a (if seen false ua)])))
            (cond-expr []
              (let [c (lor)]
                (if (isp "?")
                  (do (adv)
                      (let [t? (not (zero? (first c)))
                            a (if t? (cond-expr) (skipping cond-expr))]
                        (when-not (isp ":") (err "expected :"))
                        (adv)
                        (let [b (if t? (skipping cond-expr) (cond-expr))]
                          (if t? a b))))
                  c)))]
      (when (empty? toks) (err "no expression"))
      (let [[v _] (cond-expr)]
        (when (< @pos (count toks)) (err (str "extra tokens: " (:text (peek*)))))
        v))))

(defn eval-if-line [toks origin]
  ;; handle defined, then expand
  (let [toks (loop [ts toks out []]
               (if (empty? ts)
                 out
                 (let [t (first ts)]
                   (if (is? t "defined")
                     (let [r (rest ts)]
                       (if (is? (first r) "(")
                         (let [nm (second r)]
                           (when-not (is? (nth (vec r) 2 nil) ")") (perr t "expected ) after defined"))
                           (recur (drop 3 r) (conj out (assoc t :kind :pnum :text (if (contains? @*macros* (:text nm)) "1" "0")))))
                         (let [nm (first r)]
                           (recur (rest r) (conj out (assoc t :kind :pnum :text (if (contains? @*macros* (:text nm)) "1" "0")))))))
                     (recur (rest ts) (conj out t))))))
        expanded (expand-list toks)
        ;; `defined` might appear after expansion too
        expanded (loop [ts expanded out []]
                   (if (empty? ts) out
                       (let [t (first ts)]
                         (if (is? t "defined")
                           (let [r (rest ts)]
                             (if (is? (first r) "(")
                               (recur (drop 3 r) (conj out (assoc t :kind :pnum :text (if (contains? @*macros* (:text (second r))) "1" "0"))))
                               (recur (rest r) (conj out (assoc t :kind :pnum :text (if (contains? @*macros* (:text (first r))) "1" "0"))))))
                           (recur (rest ts) (conj out t))))))]
    (not (zero? (eval-if-tokens expanded origin)))))

;; ---------------------------------------------------------------------------
;; Directives

(defn parse-define [toks origin]
  (let [nm (first toks)]
    (when-not (and nm (ident? nm)) (perr origin "macro name must be an identifier"))
    (let [r (vec (rest toks))]
      (if (and (seq r) (is? (first r) "(") (not (:space (first r))))
        ;; function-like
        (let [[params variadic va-name body]
              (loop [i 1 params []]
                (let [t (get r i)]
                  (cond
                    (nil? t) (perr origin "unterminated macro parameter list")
                    (is? t ")") [params false nil (subvec r (inc i))]
                    (is? t "...") (do (when-not (is? (get r (inc i)) ")") (perr t "expected )"))
                                      [params true "__VA_ARGS__" (subvec r (+ i 2))])
                    (is? t ",") (recur (inc i) params)
                    (ident? t)
                    (if (is? (get r (inc i)) "...")
                      (do (when-not (is? (get r (+ i 2)) ")") (perr t "expected )"))
                          [params true (:text t) (subvec r (+ i 3))])
                      (recur (inc i) (conj params (:text t))))
                    :else (perr t "invalid macro parameter"))))]
          {:name (:text nm) :params params :variadic variadic :va-name va-name :body (vec body)})
        {:name (:text nm) :params nil :body r}))))

(defn find-include [fname quoted cur-file]
  (let [cands (concat (when quoted
                        [(io/file (or (.getParent (io/file cur-file)) ".") fname)])
                      (map #(io/file % fname) *includes*))]
    (some #(when (.isFile ^java.io.File %) (.getPath ^java.io.File %)) cands)))

(declare include-file-tokens)

(defn skip-cond-block!
  "Skip tokens until matching #elif/#else/#endif at same nesting. Returns the
   directive name token (left consumed) and leaves the rest of its line in dq."
  [^ArrayDeque dq origin]
  (loop [depth 0]
    (let [tk (.pollFirst dq)]
      (cond
        (or (nil? tk) (= :eof (:kind tk))) (perr origin "unterminated conditional directive")
        (and (:bol tk) (is? tk "#") (not (:from-macro tk)))
        (let [d (.peekFirst dq)]
          (if (and d (not (:bol d)) (ident? d))
            (let [nm (:text d)]
              (cond
                (#{"if" "ifdef" "ifndef"} nm) (do (read-line-toks dq) (recur (inc depth)))
                (and (= nm "endif") (pos? depth)) (do (read-line-toks dq) (recur (dec depth)))
                (and (#{"elif" "else" "endif"} nm) (zero? depth)) (do (.pollFirst dq) d)
                :else (do (read-line-toks dq) (recur depth))))
            (recur depth)))
        :else (recur depth)))))

(defn preprocess-tokens
  "Main loop. Returns output vector of tokens (ending with :eof)."
  [^ArrayDeque dq]
  (let [out (transient [])
        cond-stack (volatile! [])] ; each: {:taken bool :else-seen bool}
    (loop []
      (let [tk (.pollFirst dq)]
        (cond
          (or (nil? tk) (= :eof (:kind tk)))
          (do (when (seq @cond-stack) (perr (or tk eof-tok) "unterminated #if"))
              (persistent! (conj! out (or tk eof-tok))))

          (and (:bol tk) (is? tk "#") (nil? (:hideset tk)))
          (let [line (read-line-toks dq)
                d (first line)
                dname (when d (:text d))
                args (vec (rest line))]
            (cond
              (nil? d) nil
              (= dname "define") (let [m (parse-define args d)] (swap! *macros* assoc (:name m) m))
              (= dname "undef") (swap! *macros* dissoc (:text (first args)))
              (= dname "include")
              (let [args (if (and (seq args) (#{:str} (:kind (first args)))) args (expand-list args))
                    [fname quoted]
                    (cond
                      (= :str (:kind (first args))) [(let [s (:text (first args))] (subs s 1 (dec (count s)))) true]
                      (is? (first args) "<")
                      [(apply str (map (fn [t] (str (when (:space t) " ") (:text t)))
                                       (take-while #(not (is? % ">")) (rest args))))
                       false]
                      :else (perr d "#include expects \"FILENAME\" or <FILENAME>"))
                    path (find-include (str/trim fname) quoted (:file d))]
                (when-not path (perr d (str "'" fname "' file not found")))
                (when-not (contains? @*once* (.getCanonicalPath (io/file path)))
                  (let [g (get @*guards* (.getCanonicalPath (io/file path)))]
                    (when-not (and g (contains? @*macros* g))
                      (push-front! dq (include-file-tokens path))))))
              (= dname "if")
              (let [v (eval-if-line args d)]
                (vswap! cond-stack conj {:taken v})
                (when-not v
                  (loop []
                    (let [dd (skip-cond-block! dq d)]
                      (case (:text dd)
                        "elif" (let [l (read-line-toks dq)]
                                 (if (eval-if-line l dd)
                                   (vswap! cond-stack (fn [s] (conj (pop s) {:taken true})))
                                   (recur)))
                        "else" (do (read-line-toks dq)
                                   (vswap! cond-stack (fn [s] (conj (pop s) {:taken true :else true}))))
                        "endif" (do (read-line-toks dq) (vswap! cond-stack pop)))))))
              (or (= dname "ifdef") (= dname "ifndef"))
              (let [defined (contains? @*macros* (:text (first args)))
                    v (if (= dname "ifdef") defined (not defined))]
                (vswap! cond-stack conj {:taken v})
                (when-not v
                  (loop []
                    (let [dd (skip-cond-block! dq d)]
                      (case (:text dd)
                        "elif" (let [l (read-line-toks dq)]
                                 (if (eval-if-line l dd)
                                   (vswap! cond-stack (fn [s] (conj (pop s) {:taken true})))
                                   (recur)))
                        "else" (do (read-line-toks dq)
                                   (vswap! cond-stack (fn [s] (conj (pop s) {:taken true :else true}))))
                        "endif" (do (read-line-toks dq) (vswap! cond-stack pop)))))))
              (or (= dname "elif") (= dname "else"))
              (do
                (when (empty? @cond-stack) (perr d (str "#" dname " without #if")))
                ;; previous branch was taken: skip to endif
                (loop []
                  (let [dd (skip-cond-block! dq d)]
                    (read-line-toks dq)
                    (when-not (= "endif" (:text dd)) (recur))))
                (vswap! cond-stack pop))
              (= dname "endif")
              (do (when (empty? @cond-stack) (perr d "#endif without #if"))
                  (vswap! cond-stack pop))
              (= dname "error") (perr d (str "#error " (tokens-spelling args)))
              (= dname "warning") nil
              (= dname "line") nil
              (= dname "pragma")
              (when (is? (first args) "once")
                (swap! *once* conj (.getCanonicalPath (io/file (:file d)))))
              (= :pnum (:kind d)) nil ; # 123 "file"
              :else (perr d (str "invalid preprocessing directive #" dname)))
            (recur))

          (expand-step! dq tk) (recur)

          :else (do (conj! out tk) (recur)))))))

(defn detect-guard
  "If file tokens are wrapped as #ifndef X / #define X ... #endif, return X."
  [toks]
  (let [v toks]
    (when (and (>= (count v) 5)
               (is? (v 0) "#") (is? (v 1) "ifndef") (ident? (v 2))
               (is? (v 3) "#") (:bol (v 3)) (is? (v 4) "define") (= (:text (get v 5)) (:text (v 2))))
      ;; check that the last directive is #endif and it closes the first #ifndef
      (let [n (count v)
            ;; v ends with :eof
            last-hash (loop [i (- n 2)] (when (>= i 0) (if (and (is? (v i) "#") (:bol (v i))) i (recur (dec i)))))]
        (when (and last-hash (is? (v (inc last-hash)) "endif") (= (+ last-hash 2) (dec n)))
          ;; verify nesting: depth never returns to 0 before the end
          (let [ok (loop [i 0 depth 0]
                     (if (>= i last-hash) true
                         (let [t (v i)]
                           (if (and (is? t "#") (:bol t))
                             (let [d (:text (v (inc i)))]
                               (cond (#{"if" "ifdef" "ifndef"} d) (recur (inc i) (inc depth))
                                     (= "endif" d) (if (= depth 1) false (recur (inc i) (dec depth)))
                                     (and (#{"else" "elif"} d) (= depth 1)) false
                                     :else (recur (inc i) depth)))
                             (recur (inc i) depth)))))]
            (when ok (:text (v 2)))))))))

(defn include-file-tokens [path]
  (let [src (slurp path :encoding "ISO-8859-1")
        toks (lx/tokenize src path)
        canon (.getCanonicalPath (io/file path))]
    (when-let [g (detect-guard toks)]
      (swap! *guards* assoc canon g))
    (pop toks))) ; drop :eof

(defn preprocess [src file include-dirs]
  (let [toks (lx/tokenize src file)]
    (if (and (not (some #(and (:bol %) (is? % "#")) toks))
             false)
      toks
      (binding [*macros* (atom {})
                *includes* (vec include-dirs)
                *once* (atom #{})
                *guards* (atom {})
                *counter* (atom 0)]
        ;; predefined
        (let [dq (ArrayDeque.)]
          (doseq [t (lx/tokenize predefined-src "<built-in>")] (.addLast dq t))
          (preprocess-tokens dq))
        (let [dq (ArrayDeque.)]
          (doseq [t toks] (.addLast dq t))
          (preprocess-tokens dq))))))
