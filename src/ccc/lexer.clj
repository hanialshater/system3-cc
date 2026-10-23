(ns ccc.lexer
  "Tokenizer. Produces a vector of token maps:
   {:kind :ident|:num|:pnum|:str|:char|:punct|:eof
    :text original text, :line n, :file name,
    :bol true if first token on a line, :space true if preceded by whitespace}
   Numbers are kept as :pnum (preprocessing numbers) and converted by convert-number."
  (:refer-clojure :exclude [case let fn defn defn- if-let when-let and or loop doseq for binding cond-> some-> dotimes letfn doto])
  (:require [ccc.fastcase :refer [case let fn defn defn- if-let when-let and or loop doseq for binding cond-> some-> dotimes letfn doto]]
            [clojure.string :as str]))

(defn error [file line msg]
  (throw (ex-info (str file ":" line ": error: " msg) {:type :compile-error})))

(def puncts
  ;; longest first
  ["%:%:" "<<=" ">>=" "..." "->" "<:" ":>" "<%" "%>" "%:" "++" "--" "<<" ">>" "<=" ">=" "==" "!=" "&&" "||"
   "+=" "-=" "*=" "/=" "%=" "&=" "|=" "^=" "##"
   "[" "]" "(" ")" "{" "}" "." "&" "*" "+" "-" "~" "!" "/" "%" "<" ">" "^" "|"
   "?" ":" ";" "=" "," "#"])

(def digraphs {"<:" "[" ":>" "]" "<%" "{" "%>" "}" "%:" "#" "%:%:" "##"})

(defn- ident-start? [c] (or (Character/isLetter (char c)) (= c \_) (= c \$)))
(defn- ident-char? [c] (or (Character/isLetterOrDigit (char c)) (= c \_) (= c \$)))

(defn- hexval [c]
  (Character/digit (char c) 16))

(defn read-escape
  "s is string, i index just after the backslash. Returns [value new-index]."
  [^String s i file line]
  (let [n (.length s)
        c (.charAt s i)]
    (cond
      (<= 0 (Character/digit c 8))
      (loop [j i v 0 k 0]
        (if (and (< j n) (< k 3) (<= 0 (Character/digit (.charAt s j) 8)))
          (recur (inc j) (+ (* v 8) (Character/digit (.charAt s j) 8)) (inc k))
          [v j]))
      (= c \x)
      (loop [j (inc i) v 0]
        (if (and (< j n) (>= (hexval (.charAt s j)) 0))
          (recur (inc j) (+ (* v 16) (hexval (.charAt s j))))
          (do (when (= j (inc i)) (error file line "invalid hex escape"))
              [v j])))
      :else
      [(case c
         \a 7 \b 8 \t 9 \n 10 \v 11 \f 12 \r 13 \e 27
         (int c))
       (inc i)])))

;; Tokens are records: the fields every reader looks up without a default.
;; Absent and nil are the same for these keys, so a record behaves exactly like
;; the map it replaces; other keys (:prefix, :from-macro, ...) live in the
;; record's ext map as in a plain map.
(defrecord Token [kind text line file bol space hideset stray])

(defn tok
  "A token with the six lexer fields."
  [kind text line file bol space]
  (Token. kind text line file bol space nil nil))

(def ^:private punct-table
  "char code -> the puncts starting with that char, in `puncts` order."
  (let [a (object-array 128)]
    (doseq [^String p puncts]
      (let [c (int (.charAt p 0))]
        (aset a c (conj (or (aget a c) []) p))))
    a))

(set! *unchecked-math* true)

(defmacro ^:private ch [s i] `(int (.charAt ~(with-meta s {:tag 'String}) (int ~i))))

(defn- ident-start-code? [^long c]
  (if (< c 128)
    (or (and (>= c 97) (<= c 122)) (and (>= c 65) (<= c 90)) (== c 95) (== c 36))
    (Character/isLetter (char c))))

(defn- ident-char-code? [^long c]
  (if (< c 128)
    (or (and (>= c 97) (<= c 122)) (and (>= c 65) (<= c 90)) (and (>= c 48) (<= c 57)) (== c 95) (== c 36))
    (Character/isLetterOrDigit (char c))))

(defn- count-newlines ^long [^String s ^long i ^long j]
  (loop [k i nl 0]
    (if (< k j)
      (recur (inc k) (if (== 10 (ch s k)) (inc nl) nl))
      nl)))

(defn tokenize
  "Tokenize source string. Returns vector of tokens ending with :eof."
  [^String src file]
  (let [^String src (if (neg? (.indexOf src "\r\n")) src (str/replace src "\r\n" "\n"))
        n (.length src)
        out (java.util.ArrayList.)]
    (loop [i 0 line 1 bol true space false]
      (if (>= i n)
        (do (.add out (tok :eof "" line file true space))
            (vec out))
        (let [c (ch src i)
              c1 (if (< (inc i) n) (ch src (inc i)) -1)]
          (cond
            ;; line splice
            (and (== c 92) (== c1 10))
            (recur (+ i 2) (inc line) bol space)

            (== c 10) (recur (inc i) (inc line) true false)

            (or (== c 32) (== c 9) (== c 12) (== c 11) (== c 13))
            (recur (inc i) line bol true)

            ;; comments
            (and (== c 47) (== c1 47))
            (let [j (loop [j i]
                      (cond (>= j n) j
                            (and (== (ch src j) 92) (< (inc j) n) (== (ch src (inc j)) 10)) (recur (+ j 2))
                            (== (ch src j) 10) j
                            :else (recur (inc j))))]
              (recur j (+ line (count-newlines src i j)) bol true))

            (and (== c 47) (== c1 42))
            (let [j (.indexOf src "*/" (int (+ i 2)))]
              (when (neg? j) (error file line "unterminated comment"))
              (recur (+ j 2) (+ line (count-newlines src i j)) bol true))

            ;; numbers (pp-number)
            (or (and (>= c 48) (<= c 57))
                (and (== c 46) (>= c1 48) (<= c1 57)))
            (let [j (loop [j (inc i)]
                      (if (>= j n) j
                          (let [d (ch src j)]
                            (cond
                              (and (or (== d 101) (== d 69) (== d 112) (== d 80)) (< (inc j) n)
                                   (let [d1 (ch src (inc j))] (or (== d1 43) (== d1 45))))
                              (recur (+ j 2))
                              (or (ident-char-code? d) (== d 46)) (recur (inc j))
                              :else j))))]
              (.add out (tok :pnum (.substring src (int i) (int j)) line file bol space))
              (recur j line false false))

            ;; string / char literals (with optional L/u/U/u8 prefix)
            (or (== c 34) (== c 39)
                (and (or (== c 76) (== c 85) (== c 117)) (or (== c1 34) (== c1 39)))
                (and (== c 117) (< (+ i 2) n) (== c1 56) (== (ch src (+ i 2)) 34)))
            (let [pre-len (cond (or (== c 34) (== c 39)) 0
                                (== c1 56) 2
                                :else 1)
                  prefix (.substring src (int i) (int (+ i pre-len)))
                  q (ch src (+ i pre-len))
                  start (+ i pre-len 1)
                  j (loop [j start]
                      (cond (>= j n) -1
                            (== (ch src j) 10) -1
                            (== (ch src j) 92) (recur (+ j 2))
                            (== (ch src j) q) j
                            :else (recur (inc j))))]
              (if (neg? j)
                ;; unterminated: a stray token (only an error if it reaches the parser)
                (let [e (+ i pre-len 1)]
                  (.add out (Token. :punct (.substring src (int i) (int e)) line file bol space nil true))
                  (recur e line false false))
                (let [^String raw (.substring src (int i) (int (inc j)))
                      spliced (>= (.indexOf raw "\\\n") 0)
                      nsplice (if spliced (count (re-seq #"\\\n" raw)) 0)
                      text (if spliced (str/replace raw "\\\n" "") raw)]
                  (.add out (assoc (tok (if (== q 34) :str :char) text line file bol space) :prefix prefix))
                  (recur (inc j) (+ line nsplice) false false))))

            (ident-start-code? c)
            (let [j (loop [j (inc i)]
                      (if (and (< j n) (ident-char-code? (ch src j))) (recur (inc j)) j))]
              (.add out (tok :ident (.substring src (int i) (int j)) line file bol space))
              (recur j line false false))

            :else
            (let [cands (when (< c 128) (aget ^objects punct-table c))
                  p (some (fn [^String p] (when (.startsWith src p (int i)) p)) cands)]
              (if p
                (do (.add out (tok :punct (get digraphs p p) line file bol space))
                    (recur (+ i (count p)) line false false))
                (do (.add out (Token. :punct (str (char c)) line file bol space nil true))
                    (recur (inc i) line false false))))))))))

(set! *unchecked-math* false)

;; ---------------------------------------------------------------------------
;; Literal conversion

(defn decode-string
  "Decode the body of a string literal token. Returns vector of byte/char values
   (plain strings: the source bytes; wide strings: code points)."
  [tok]
  (let [^String text (:text tok)
        pre (count (:prefix tok ""))
        s (subs text (inc pre) (dec (count text)))
        n (count s)
        wide? (not (or (= "" (:prefix tok "")) (= "u8" (:prefix tok))))]
    (loop [i 0 acc (transient [])]
      (if (>= i n)
        (persistent! acc)
        (let [c (.charAt s i)]
          (if (= c \\)
            (let [[v j] (read-escape s (inc i) (:file tok) (:line tok))]
              (recur j (conj! acc (if wide? v (bit-and v 0xff)))))
            (if (or (not wide?) (< (int c) 128))
              ;; source is read as ISO-8859-1, so each char is one source byte
              (recur (inc i) (conj! acc (bit-and (int c) 0xff)))
              ;; wide literal: decode the run of non-ASCII bytes as UTF-8
              (let [j (loop [j i] (if (and (< j n) (>= (int (.charAt s j)) 128)) (recur (inc j)) j))
                    bs (byte-array (map #(unchecked-byte (int %)) (subs s i j)))
                    ^String u (String. ^bytes bs "UTF-8")]
                (recur j (reduce conj! acc (iterator-seq (.iterator (.boxed (.codePoints u))))))))))))))

(defn char-value
  "Value of a character constant token (as int, char is signed)."
  [tok]
  (let [vals (decode-string tok)]
    (when (empty? vals) (error (:file tok) (:line tok) "empty character constant"))
    (if (= "" (:prefix tok ""))
      (if (= 1 (count vals))
        (let [v (first vals)] (if (> v 127) (- v 256) v))
        ;; multi-char constant: gcc semantics
        (let [v (reduce (fn [a b] (bit-or (bit-shift-left a 8) (bit-and b 0xff))) 0 vals)
              v (bit-and v 0xffffffff)]
          (if (> v 0x7fffffff) (- v 0x100000000) v)))
      (first vals))))
