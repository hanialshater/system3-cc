(ns ccc.parser
  "Recursive-descent C parser producing a typed AST."
  (:refer-clojure :exclude [integer?])
  (:require [ccc.types :as t :refer [ty-void ty-bool ty-char ty-uchar ty-short ty-ushort
                                     ty-int ty-uint ty-long ty-ulong ty-float ty-double
                                     ptr-to array-of func-type]]
            [ccc.lexer :as lx]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; State

(def ^:dynamic *toks* nil)
(def ^:dynamic *pos* nil)
(def ^:dynamic *scope* nil)        ; volatile list of {:vars {} :tags {}}
(def ^:dynamic *locals* nil)       ; volatile vector of local vars of current fn
(def ^:dynamic *globals* nil)      ; volatile map name -> global var
(def ^:dynamic *global-order* nil) ; volatile vector of global names (incl. anon)
(def ^:dynamic *functions* nil)    ; volatile vector of function defs
(def ^:dynamic *counter* nil)      ; volatile long
(def ^:dynamic *cur-fn* nil)       ; {:name :type}
(def ^:dynamic *brk* nil)
(def ^:dynamic *cont* nil)
(def ^:dynamic *switch* nil)       ; volatile {:cases [] :default nil :type T}
(def ^:dynamic *labels* nil)
(def ^:dynamic *strlits* nil)
(def ^:dynamic *vla-blocks* nil)   ; volatile vector: save-sp slot of each live VLA, in declaration order
(def ^:dynamic *gotos* nil)        ; volatile vector of {:label :live :restore atom}
(def ^:dynamic *label-live* nil)   ; volatile map asm-label -> live VLA slots at the label
(def ^:dynamic *brk-depth* 0)      ; (count @*vla-blocks*) where the break target was opened
(def ^:dynamic *cont-depth* 0)      ; volatile map: literal contents -> global       ; volatile map user-label -> asm label

(defn tok [] (let [t (nth *toks* @*pos*)]
              (when (:stray t)
                (throw (ex-info (str (:file t) ":" (:line t) ": error: stray '" (:text t) "' in program")
                                {:type :compile-error})))
              t))
(defn peek-tok [k]
  (let [i (+ @*pos* k) n (count *toks*)]
    (nth *toks* (if (< i n) i (dec n)))))
(defn advance! [] (vswap! *pos* inc) nil)

(defn err-tok [tk msg]
  (throw (ex-info (str (:file tk) ":" (:line tk) ": error: " msg
                       (when (seq (:text tk)) (str " (at '" (:text tk) "')")))
                  {:type :compile-error})))
(defn err [msg] (err-tok (tok) msg))

(defn is-tok? [tk s]
  (and (or (= :punct (:kind tk)) (= :ident (:kind tk))) (= (:text tk) s)))
(defn is? [s] (is-tok? (tok) s))
(defn consume! [s] (when (is? s) (advance!) true))
(defn expect! [s] (if (is? s) (advance!) (err (str "expected '" s "'"))))

(defn next-id! [] (vswap! *counter* inc))
(defn new-label [] (str ".L." (next-id!)))

;; ---------------------------------------------------------------------------
;; Scopes

(defn enter-scope! [] (vswap! *scope* conj {:vars {} :tags {}}))
(defn leave-scope! [] (vswap! *scope* pop))

(defn find-ident [name]
  (some #(get-in % [:vars name]) @*scope*))

(defn push-ident! [name entry]
  (vswap! *scope* (fn [sc] (conj (pop sc) (assoc-in (peek sc) [:vars name] entry)))))

(defn find-tag [name]
  (some #(get-in % [:tags name]) @*scope*))
(defn find-tag-current [name]
  (get-in (peek @*scope*) [:tags name]))
(defn push-tag! [name ty]
  (vswap! *scope* (fn [sc] (conj (pop sc) (assoc-in (peek sc) [:tags name] ty)))))

(defn typedef-type [name]
  (:typedef (find-ident name)))

(def keywords
  #{"void" "_Bool" "char" "short" "int" "long" "float" "double" "signed" "unsigned"
    "struct" "union" "enum" "typedef" "static" "extern" "auto" "register" "const"
    "volatile" "restrict" "__restrict" "__restrict__" "inline" "__inline" "__inline__"
    "_Noreturn" "_Alignas" "_Thread_local" "__thread" "__attribute__" "__extension__"
    "typeof" "__typeof__" "__typeof" "_Atomic" "__const" "__volatile__" "__volatile"
    "__signed__" "__signed" "_Complex" "if" "else" "while" "do" "for" "return" "break"
    "continue" "goto" "switch" "case" "default" "sizeof" "_Alignof" "__alignof__"
    "_Static_assert" "_Generic" "asm" "__asm__" "__asm"})

(def typename-keywords
  #{"void" "_Bool" "char" "short" "int" "long" "float" "double" "signed" "unsigned"
    "struct" "union" "enum" "typedef" "static" "extern" "auto" "register" "const"
    "volatile" "restrict" "__restrict" "__restrict__" "inline" "__inline" "__inline__"
    "_Noreturn" "_Alignas" "_Thread_local" "__thread" "__attribute__" "__extension__"
    "typeof" "__typeof__" "__typeof" "_Atomic" "__const" "__volatile__" "__volatile"
    "__signed__" "__signed" "_Complex"})

(defn typename-tok? [tk]
  (and (= :ident (:kind tk))
       (or (contains? typename-keywords (:text tk))
           (some? (typedef-type (:text tk))))))

(defn typename? [] (typename-tok? (tok)))

;; ---------------------------------------------------------------------------
;; Vars

(defn new-lvar! [name ty]
  (let [v {:name name :type ty :id (next-id!) :local true}]
    (vswap! *locals* conj v)
    (when name (push-ident! name {:var v}))
    v))

(defn new-temp! [ty]
  (let [v {:name nil :type ty :id (next-id!) :local true}]
    (vswap! *locals* conj v)
    v))

(defn add-global! [v]
  (let [nm (:label v)]
    (when-not (contains? @*globals* nm)
      (vswap! *global-order* conj nm))
    (vswap! *globals* assoc nm v)
    v))

(defn anon-global! [ty init]
  (let [label (str ".L.anon." (next-id!))]
    (add-global! {:name label :label label :type ty :global true :static true
                  :defined true :init init})))

(defn var-node [v] {:op :var :var v :type (:type v)})

;; ---------------------------------------------------------------------------
;; Constant evaluation

(defn wrap [v ty]
  (let [v (long v)]
    (case (:kind ty)
      :bool (if (zero? v) 0 1)
      :char (if (:unsigned ty) (bit-and v 0xff) (long (unchecked-byte v)))
      :short (if (:unsigned ty) (bit-and v 0xffff) (long (unchecked-short v)))
      :int (if (:unsigned ty) (bit-and v 0xffffffff) (long (unchecked-int v)))
      v)))

(defn not-const [] (throw (ex-info "not constant" {:type :not-const})))

(declare eval-reloc eval-double eval-truth)

(defn ulong-type? [ty] (and (t/unsigned? ty) (= 8 (t/type-size ty))))

(defn double->long [d ty]
  (let [d (double d)]
    (if (and (t/unsigned? ty) (= 8 (t/type-size ty)) (>= d 9.223372036854775807E18))
      (unchecked-add (long (- d 9.223372036854775807E18)) Long/MIN_VALUE)
      (long d))))

(defn eval-addr [n]
  (case (:op n)
    :var (if (:local (:var n)) (not-const) [(:label (:var n)) 0])
    :deref (eval-reloc (:expr n))
    :member (let [[l v] (eval-addr (:expr n))] [l (+ v (:offset (:member n)))])
    :comma (eval-addr (:rhs n))
    (not-const)))

(defn eval-int [n]
  (let [[l v] (eval-reloc n)]
    (when l (not-const))
    v))

(defn eval-truth
  "Truth value of a constant scalar expression (addresses of objects are true)."
  [n]
  (if (t/flonum? (:type n))
    (not (zero? (eval-double n)))
    (let [[l v] (eval-reloc n)]
      (boolean (or l (not (zero? v)))))))

(defn eval-reloc
  "Evaluate constant expression. Returns [label-or-nil value]."
  [n]
  (let [ty (:type n)]
    (case (:op n)
      :num (if (:fval n) [nil (double->long (:fval n) ty)] [nil (:val n)])
      :add (let [[l1 a] (eval-reloc (:lhs n)) [l2 b] (eval-reloc (:rhs n))]
             (when (and l1 l2) (not-const))
             [(or l1 l2) (wrap (unchecked-add a b) ty)])
      :sub (let [[l1 a] (eval-reloc (:lhs n)) [l2 b] (eval-reloc (:rhs n))]
             (cond (and l1 l2 (= l1 l2)) [nil (wrap (unchecked-subtract a b) ty)]
                   ;; difference of two label addresses: an assemble-time constant
                   (and l1 l2 (str/starts-with? l1 ".L") (str/starts-with? l2 ".L")
                        (not (str/includes? l1 "-")) (not (str/includes? l2 "-")))
                   [(str l1 "-" l2) (unchecked-subtract a b)]
                   l2 (not-const)
                   :else [l1 (wrap (unchecked-subtract a b) ty)]))
      (:mul :div :mod :bitand :bitor :bitxor :shl :shr)
      (let [a (eval-int (:lhs n)) b (eval-int (:rhs n))
            u8 (ulong-type? ty)]
        (when (and (#{:div :mod} (:op n)) (zero? b)) (not-const))
        [nil (wrap (case (:op n)
                     :mul (unchecked-multiply a b)
                     :div (if u8 (Long/divideUnsigned a b)
                              (if (and (= a Long/MIN_VALUE) (= b -1)) a (quot a b)))
                     :mod (if u8 (Long/remainderUnsigned a b)
                              (if (= b -1) 0 (rem a b)))
                     :bitand (bit-and a b)
                     :bitor (bit-or a b)
                     :bitxor (bit-xor a b)
                     :shl (bit-shift-left a (bit-and b 63))
                     :shr (if (t/unsigned? ty)
                            (unsigned-bit-shift-right a (bit-and b 63))
                            (bit-shift-right a (bit-and b 63))))
                   ty)])
      (:eq :ne :lt :le :gt :ge)
      (let [lt (:type (:lhs n))]
        (if (t/flonum? lt)
          (let [a (eval-double (:lhs n)) b (eval-double (:rhs n))]
            [nil (if (case (:op n) :eq (== a b) :ne (not (== a b)) :lt (< a b)
                           :le (<= a b) :gt (> a b) :ge (>= a b)) 1 0)])
          (let [[l1 a] (eval-reloc (:lhs n)) [l2 b] (eval-reloc (:rhs n))
                _ (when (or l1 l2)
                    (if (and (= l1 l2) (#{:eq :ne} (:op n))) nil (not-const)))
                c (if (or (ulong-type? lt) (t/ptr? lt)) (Long/compareUnsigned a b) (compare a b))]
            [nil (if (case (:op n) :eq (zero? c) :ne (not (zero? c)) :lt (neg? c)
                           :le (<= c 0) :gt (pos? c) :ge (>= c 0)) 1 0)])))
      :logand [nil (if (and (eval-truth (:lhs n)) (eval-truth (:rhs n))) 1 0)]
      :logor [nil (if (or (eval-truth (:lhs n)) (eval-truth (:rhs n))) 1 0)]
      :not [nil (if (eval-truth (:expr n)) 0 1)]
      :neg [nil (wrap (unchecked-negate (eval-int (:expr n))) ty)]
      :bitnot [nil (wrap (bit-not (eval-int (:expr n))) ty)]
      :cond (eval-reloc (if (eval-truth (:cond n)) (:then n) (:els n)))
      :comma (eval-reloc (:rhs n))
      :cast (let [inner (:expr n) it (:type inner)]
              (cond
                (t/flonum? it) (if (= :bool (:kind ty))
                                 [nil (if (zero? (eval-double inner)) 0 1)]
                                 [nil (wrap (double->long (eval-double inner) ty) ty)])
                :else
                (let [[l v] (eval-reloc inner)]
                  (if l
                    (if (or (>= (t/type-size ty) 8) (str/includes? l "-")) [l v] (not-const))
                    [nil (wrap v ty)]))))
      :addr (eval-addr (:expr n))
      :label-addr [(:label n) 0]
      :var (if (and (or (t/array? ty) (t/func? ty)) (not (:local (:var n))))
             [(:label (:var n)) 0]
             (not-const))
      :member (if (t/array? ty) (eval-addr n) (not-const))
      :deref (if (or (t/array? ty) (t/func? ty)) (eval-reloc (:expr n)) (not-const))
      (not-const))))

(defn canon-nan
  "gcc folds NaN-producing operations to the positive quiet NaN."
  [d]
  (if (Double/isNaN d) Double/NaN d))

(defn eval-double [n]
  (let [ty (:type n)]
    (if (t/integer? ty)
      (let [v (eval-int n)]
        (if (ulong-type? ty)
          (if (neg? v) (Double/parseDouble (Long/toUnsignedString v)) (double v))
          (double v)))
      (case (:op n)
        :num (double (:fval n))
        :add (canon-nan (+ (eval-double (:lhs n)) (eval-double (:rhs n))))
        :sub (canon-nan (- (eval-double (:lhs n)) (eval-double (:rhs n))))
        :mul (canon-nan (* (eval-double (:lhs n)) (eval-double (:rhs n))))
        :div (canon-nan (/ (eval-double (:lhs n)) (eval-double (:rhs n))))
        :neg (- (eval-double (:expr n)))
        :cond (if (not (zero? (if (t/flonum? (:type (:cond n))) (eval-double (:cond n)) (eval-int (:cond n)))))
                (eval-double (:then n)) (eval-double (:els n)))
        :comma (eval-double (:rhs n))
        :cast (let [inner (:expr n) d (eval-double inner)]
                (if (= :float (:kind ty)) (double (unchecked-float d)) d))
        (not-const)))))

(defn try-eval [n]
  (try (eval-int n)
       (catch clojure.lang.ExceptionInfo e
         (if (= :not-const (:type (ex-data e))) nil (throw e)))))

;; ---------------------------------------------------------------------------
;; Node constructors with typing

(defn num-node [v ty] {:op :num :type ty :val (wrap v ty)})
(defn fnum-node [d ty] {:op :num :type ty :fval (if (= :float (:kind ty)) (double (unchecked-float d)) (double d))})

(defn decay [ty]
  (case (:kind ty)
    (:array :vla) (ptr-to (:base ty))
    :func (ptr-to ty)
    ty))

(defn same-scalar? [a b]
  (and (= (:kind a) (:kind b))
       (= (t/unsigned? a) (t/unsigned? b))
       (not (#{:struct :union :array :func :vla} (:kind a)))))

(defn new-cast [n ty]
  (let [ty (t/strip ty)
        nt (:type n)]
    (cond
      (= :void (:kind ty)) {:op :cast :type ty :expr n}
      (and (same-scalar? nt ty) (not= :ptr (:kind ty))) n
      (and (= :ptr (:kind ty)) (= :ptr (:kind nt))) (assoc n :type ty)
      (and (= :num (:op n)) (not (:fval n)) (t/integer? ty)) (num-node (:val n) ty)
      (and (= :num (:op n)) (not (:fval n)) (= :ptr (:kind ty))) (assoc n :type ty)
      (and (= :num (:op n)) (t/flonum? ty))
      (fnum-node (if (:fval n) (:fval n) (eval-double n)) ty)
      (and (= :num (:op n)) (:fval n) (t/integer? ty) (not= :bool (:kind ty)))
      (num-node (double->long (:fval n) ty) ty)
      :else {:op :cast :type ty :expr n})))

(defn check-scalar [n]
  (let [ty (:type n)]
    (when-not (or (t/scalar? ty) (t/array? ty) (t/func? ty))
      (err (str "scalar required, got " (t/type-str ty))))
    n))

(defn arith-conv [op lhs rhs]
  (let [lt (:type lhs) rt (:type rhs)]
    (when-not (and (t/numeric? lt) (t/numeric? rt))
      (err (str "invalid operands to binary operator (" (t/type-str lt) ", " (t/type-str rt) ")")))
    (let [ct (t/common-type lt rt)]
      {:op op :type ct :lhs (new-cast lhs ct) :rhs (new-cast rhs ct)})))

(defn int-conv [op lhs rhs]
  (when-not (and (t/integer? (:type lhs)) (t/integer? (:type rhs)))
    (err "invalid operands (integer required)"))
  (arith-conv op lhs rhs))

(defn ptr-base-size [ty]
  (let [b (:base ty)]
    (if (or (t/void? b) (t/func? b)) 1 (t/type-size b))))

(declare vla-type?)

(defn runtime-sized? [ty]
  (case (:kind ty)
    :vla true
    :array (runtime-sized? (:base ty))
    false))

(defn vla-size-expr
  "Node computing sizeof(ty) at run time (ty may contain VLA dimensions)."
  [ty]
  (cond
    (and (= :vla (:kind ty)) (:size-var ty)) (var-node (:size-var ty))
    (= :vla (:kind ty))
    {:op :mul :type ty-ulong
     :lhs (cond (:len-var ty) (var-node (:len-var ty))
                (:len-expr ty) (new-cast (:len-expr ty) ty-ulong)
                :else (num-node 0 ty-ulong))
     :rhs (vla-size-expr (:base ty))}
    (and (= :array (:kind ty)) (runtime-sized? (:base ty)))
    {:op :mul :type ty-ulong :lhs (num-node (max 0 (:len ty)) ty-ulong) :rhs (vla-size-expr (:base ty))}
    :else (num-node (t/type-size ty) ty-ulong)))

(defn scale-index [idx ty]
  ;; idx * sizeof(base) as long
  (let [base (:base ty)]
    (if (= :vla (:kind base))
      {:op :mul :type ty-long :lhs (new-cast idx ty-long) :rhs (new-cast (vla-size-expr base) ty-long)}
      (let [sz (ptr-base-size ty)
            i (new-cast idx ty-long)]
        (if (= sz 1) i
            (if (= :num (:op i))
              (num-node (* (:val i) sz) ty-long)
              {:op :mul :type ty-long :lhs i :rhs (num-node sz ty-long)}))))))

(defn new-add [lhs rhs]
  (let [lt (:type lhs) rt (:type rhs)]
    (cond
      (and (t/numeric? lt) (t/numeric? rt)) (arith-conv :add lhs rhs)
      (and (t/ptr-like? lt) (t/integer? rt))
      (let [pt (decay lt)] {:op :add :type pt :lhs lhs :rhs (scale-index rhs pt)})
      (and (t/integer? lt) (t/ptr-like? rt))
      (let [pt (decay rt)] {:op :add :type pt :lhs rhs :rhs (scale-index lhs pt)})
      :else (err "invalid operands to +"))))

(defn new-sub [lhs rhs]
  (let [lt (:type lhs) rt (:type rhs)]
    (cond
      (and (t/numeric? lt) (t/numeric? rt)) (arith-conv :sub lhs rhs)
      (and (t/ptr-like? lt) (t/integer? rt))
      (let [pt (decay lt)] {:op :sub :type pt :lhs lhs :rhs (scale-index rhs pt)})
      (and (t/ptr-like? lt) (t/ptr-like? rt))
      (let [pt (decay lt)
            diff {:op :sub :type ty-long :lhs (new-cast lhs ty-long) :rhs (new-cast rhs ty-long)}
            base (:base pt)]
        (if (= :vla (:kind base))
          {:op :div :type ty-long :lhs diff :rhs (new-cast (vla-size-expr base) ty-long)}
          (let [sz (ptr-base-size pt)]
            (if (= sz 1) diff {:op :div :type ty-long :lhs diff :rhs (num-node sz ty-long)}))))
      :else (err "invalid operands to -"))))

(defn new-mul [l r] (arith-conv :mul l r))
(defn new-div [l r] (arith-conv :div l r))
(defn new-mod [l r] (int-conv :mod l r))
(defn new-bitand [l r] (int-conv :bitand l r))
(defn new-bitor [l r] (int-conv :bitor l r))
(defn new-bitxor [l r] (int-conv :bitxor l r))

(defn new-shift [op lhs rhs]
  (when-not (and (t/integer? (:type lhs)) (t/integer? (:type rhs)))
    (err "invalid operands to shift"))
  (let [pt (t/promote (:type lhs))]
    {:op op :type pt :lhs (new-cast lhs pt) :rhs (new-cast rhs ty-int)}))

(defn new-compare [op lhs rhs]
  (let [lt (:type lhs) rt (:type rhs)]
    (if (and (t/numeric? lt) (t/numeric? rt))
      (assoc (arith-conv op lhs rhs) :type ty-int)
      (do (check-scalar lhs) (check-scalar rhs)
          {:op op :type ty-int :lhs (new-cast lhs ty-ulong) :rhs (new-cast rhs ty-ulong)}))))

(defn new-not [e] (check-scalar e) {:op :not :type ty-int :expr e})

(defn new-neg [e]
  (when-not (t/numeric? (:type e)) (err "invalid operand to unary -"))
  (let [pt (if (t/flonum? (:type e)) (:type e) (t/promote (:type e)))]
    (let [c (new-cast e pt)]
      (if (and (= :num (:op c)) (not (:fval c)))
        (num-node (unchecked-negate (:val c)) pt)
        (if (and (= :num (:op c)) (:fval c))
          (fnum-node (- (:fval c)) pt)
          {:op :neg :type pt :expr c})))))

(defn new-bitnot [e]
  (when-not (t/integer? (:type e)) (err "invalid operand to ~"))
  (let [pt (t/promote (:type e))]
    {:op :bitnot :type pt :expr (new-cast e pt)}))

(defn new-logical [op l r]
  (check-scalar l) (check-scalar r)
  {:op op :type ty-int :lhs l :rhs r})

(defn new-comma [l r] {:op :comma :type (:type r) :lhs l :rhs r})

(defn null-ptr-const? [n]
  (or (and (t/integer? (:type n)) (= 0 (try-eval n)))
      ;; (void *)0
      (and (t/ptr? (:type n)) (t/void? (:base (:type n)))
           (= :num (:op n)) (not (:fval n)) (zero? (:val n)))))

(defn new-cond [c then els]
  (check-scalar c)
  (let [tt (decay (:type then)) et (decay (:type els))]
    (cond
      (and (t/numeric? tt) (t/numeric? et))
      (let [ct (t/common-type tt et)]
        {:op :cond :type ct :cond c :then (new-cast then ct) :els (new-cast els ct)})
      (and (t/void? tt) (t/void? et)) {:op :cond :type ty-void :cond c :then then :els els}
      (or (t/void? tt) (t/void? et)) {:op :cond :type ty-void :cond c :then (new-cast then ty-void) :els (new-cast els ty-void)}
      (and (t/ptr? tt) (t/ptr? et))
      (let [rt (cond (t/void? (:base tt)) et (t/void? (:base et)) tt
                     (null-ptr-const? then) et
                     :else tt)
            rt (if (or (and (t/void? (:base tt)) (not (null-ptr-const? then)))
                       (and (t/void? (:base et)) (not (null-ptr-const? els))))
                 (ptr-to ty-void) rt)]
        {:op :cond :type rt :cond c :then (new-cast then rt) :els (new-cast els rt)})
      (and (t/ptr? tt) (t/integer? et)) {:op :cond :type tt :cond c :then then :els (new-cast els tt)}
      (and (t/integer? tt) (t/ptr? et)) {:op :cond :type et :cond c :then (new-cast then et) :els els}
      (and (t/struct? tt) (t/struct? et)) {:op :cond :type tt :cond c :then then :els els}
      :else (err "type mismatch in conditional expression"))))

(defn new-assign [lhs rhs]
  (let [lt (:type lhs)]
    (when (t/array? lt) (err "assignment to array"))
    (if (t/struct? lt)
      (do (when-not (t/struct? (:type rhs)) (err "incompatible types in struct assignment"))
          {:op :assign :type lt :lhs lhs :rhs rhs})
      {:op :assign :type (t/strip lt) :lhs lhs :rhs (new-cast rhs lt)})))

(defn new-addr [e]
  (when (and (= :member (:op e)) (:bitfield (:member e))) (err "cannot take address of bit-field"))
  (if (= :deref (:op e))
    (let [inner (:expr e)]
      (if (t/func? (:type inner)) inner (assoc inner :type (ptr-to (:type e)))))
    {:op :addr :type (ptr-to (:type e)) :expr e}))

(defn new-deref [e]
  (let [ty (:type e)]
    (cond
      (t/func? ty) e
      (t/ptr-like? ty)
      (let [b (:base ty)]
        (if (t/void? b) {:op :deref :type ty-void :expr e}
            {:op :deref :type b :expr e}))
      :else (err (str "invalid pointer dereference of " (t/type-str ty))))))

(defn find-member-path
  "Returns vector of members to traverse, or nil."
  [sty name]
  (let [info (t/struct-info sty)]
    (some (fn [m]
            (cond
              (= (:name m) name) [m]
              (and (nil? (:name m)) (t/struct? (:type m)))
              (when-let [p (find-member-path (:type m) name)] (into [m] p))
              :else nil))
          (:members info))))

(defn member-value-type
  "Type of a member's value. Bit-fields narrower than int promote like gcc:
   to int if int holds every value, else to unsigned int."
  [m]
  (let [ty (:type m)]
    (if (and (:bitfield m) (t/integer? ty))
      (let [w (:bit-width m)]
        (cond (< w 32) ty-int
              (and (= w 32) (not (t/unsigned? ty))) ty-int
              (= w 32) ty-uint
              :else (t/strip ty)))
      ty)))

(defn new-member [e name]
  (let [ty (:type e)]
    (when-not (t/struct? ty) (err (str "member reference base is not a struct (" (t/type-str ty) ")")))
    (when-not (:complete (t/struct-info ty)) (err "incomplete struct type"))
    (let [path (find-member-path ty name)]
      (when-not path (err (str "no member named '" name "'")))
      (reduce (fn [n m] {:op :member :type (member-value-type m) :expr n :member m}) e path))))

(defn simple-lvalue? [n] (= :var (:op n)))

(defn with-lvalue-ref
  "Calls (f lv-node) where lv-node is an lvalue equivalent to lhs that can be
   evaluated multiple times without side effects. Returns expression."
  [lhs f]
  (cond
    (simple-lvalue? lhs) (f lhs)
    (= :member (:op lhs))
    (let [obj (:expr lhs)]
      (if (simple-lvalue? obj)
        (f lhs)
        (let [tmp (new-temp! (ptr-to (:type obj)))
              ref {:op :member :type (:type lhs) :expr (new-deref (var-node tmp)) :member (:member lhs)}]
          (new-comma (new-assign (var-node tmp) (new-addr obj)) (f ref)))))
    :else
    (let [tmp (new-temp! (ptr-to (:type lhs)))]
      (new-comma (new-assign (var-node tmp) (new-addr lhs))
                 (f (new-deref (var-node tmp)))))))

(defn compound-assign [op-fn lhs rhs]
  (with-lvalue-ref lhs (fn [lv] (new-assign lv (op-fn lv rhs)))))

(defn direct-incdec
  "Single-node increment/decrement for integer and pointer lvalues, or nil."
  [lhs delta pre]
  (let [ty (:type lhs)]
    (when (and (or (and (t/integer? ty) (not= :bool (:kind ty)))
                   (and (t/ptr? ty) (not= :vla (:kind (:base ty)))))
               (not (and (= :member (:op lhs)) (:bitfield (:member lhs)))))
      (let [d (if (t/ptr? ty) (* delta (ptr-base-size ty)) delta)]
        (when (<= -2147483648 d 2147483647)
          {:op :incdec :type (t/strip ty) :expr lhs :delta d :pre pre})))))

(defn post-incdec [lhs delta]
  (check-scalar lhs)
  (or (direct-incdec lhs delta false)
   (with-lvalue-ref lhs
    (fn [lv]
      (let [old (new-temp! (t/strip (:type lv)))]
        (new-comma (new-assign (var-node old) lv)
                   (new-comma (new-assign lv (new-add (var-node old) (num-node delta ty-int)))
                              (var-node old))))))))

(defn default-promote [n]
  (let [ty (:type n)]
    (cond
      (= :float (:kind ty)) (new-cast n ty-double)
      (t/integer? ty) (new-cast n (t/promote ty))
      :else n)))

(defn new-call [fexpr args]
  (let [fty (:type fexpr)
        fty (cond (t/func? fty) fty
                  (and (t/ptr? fty) (t/func? (:base fty))) (:base fty)
                  :else (err "called object is not a function"))
        params (:params fty)
        nparams (count params)
        args (vec (map-indexed
                   (fn [i a]
                     (if (and (:proto fty) (< i nparams))
                       (let [pt (nth params i)]
                         (if (t/struct? pt) a (new-cast a pt)))
                       (default-promote a)))
                   args))
        ret (t/strip (:ret fty))]
    (when (and (:proto fty) (not (:variadic fty)) (not= nparams (count args)))
      (err "wrong number of arguments"))
    (when (and (:proto fty) (< (count args) nparams))
      (err "too few arguments"))
    (cond-> {:op :call :type ret :fn fexpr :args args :fn-type fty}
      (t/struct? ret) (assoc :ret-buf (new-temp! ret)))))

;; ---------------------------------------------------------------------------
;; Number literals

(defn parse-number [tk]
  (let [text (:text tk)
        lower (str/lower-case text)
        hex? (str/starts-with? lower "0x")
        float? (if hex? (str/includes? lower "p")
                   (or (str/includes? lower ".") (str/includes? lower "e")))]
    (if float?
      (let [[body ty] (cond (str/ends-with? lower "f") [(subs text 0 (dec (count text))) ty-float]
                            (str/ends-with? lower "l") [(subs text 0 (dec (count text))) ty-double]
                            :else [text ty-double])
            d (try (Double/parseDouble (if (and hex? (not (re-find #"[pP]" body))) (str body "p0") body))
                   (catch Exception _ (err-tok tk "invalid floating constant")))]
        (fnum-node d ty))
      (let [m (re-matches #"(?i)(0x[0-9a-f]+|0b[01]+|[0-9]+)(u|ul|lu|ull|llu|l|ll|uL|Lu)?" text)
            _ (when-not m (err-tok tk "invalid number"))
            digits (nth m 1)
            suffix (str/lower-case (or (nth m 2) ""))
            [base ds] (cond (str/starts-with? (str/lower-case digits) "0x") [16 (subs digits 2)]
                            (str/starts-with? (str/lower-case digits) "0b") [2 (subs digits 2)]
                            (and (> (count digits) 1) (= \0 (first digits))) [8 (subs digits 1)]
                            :else [10 digits])
            big (try (BigInteger. ^String ds (int base)) (catch Exception _ (err-tok tk "invalid number")))
            u? (str/includes? suffix "u")
            l? (str/includes? suffix "l")
            fits-int (<= (.compareTo big (biginteger 0x7fffffff)) 0)
            fits-uint (<= (.compareTo big (biginteger 0xffffffff)) 0)
            fits-long (<= (.compareTo big (biginteger Long/MAX_VALUE)) 0)
            dec? (= base 10)
            ty (cond
                 (and u? l?) ty-ulong
                 l? (if fits-long ty-long ty-ulong)
                 u? (if fits-uint ty-uint ty-ulong)
                 dec? (cond fits-int ty-int fits-long ty-long :else ty-ulong)
                 :else (cond fits-int ty-int fits-uint ty-uint fits-long ty-long :else ty-ulong))]
        {:op :num :type ty :val (.longValue big)}))))

;; ---------------------------------------------------------------------------
;; Attributes

(defn skip-parens! []
  (expect! "(")
  (loop [depth 1]
    (when (> depth 0)
      (let [tk (tok)]
        (when (= :eof (:kind tk)) (err "unterminated parenthesis"))
        (advance!)
        (recur (cond (is-tok? tk "(") (inc depth)
                     (is-tok? tk ")") (dec depth)
                     :else depth))))))

(declare const-expr assign-expr expr typename)

(defn parse-attribute!
  "At __attribute__. Returns map of interesting attributes."
  []
  (advance!)
  (expect! "(") (expect! "(")
  (loop [attrs {}]
    (cond
      (consume! ")") (do (expect! ")") attrs)
      (consume! ",") (recur attrs)
      :else
      (let [tk (tok) nm (str/replace (:text tk) #"^__(.*)__$" "$1")]
        (advance!)
        (cond
          (= nm "packed") (recur (assoc attrs :packed true))
          (= nm "aligned")
          (if (is? "(")
            (do (advance!)
                (let [v (const-expr)] (expect! ")") (recur (assoc attrs :aligned v))))
            (recur (assoc attrs :aligned 16)))
          (is? "(") (do (skip-parens!) (recur attrs))
          :else (recur attrs))))))

(defn skip-attributes!
  "Skips any __attribute__ / asm labels; returns merged attrs."
  []
  (loop [attrs {}]
    (cond
      (is? "__attribute__") (recur (merge attrs (parse-attribute!)))
      (or (is? "__asm__") (is? "asm") (is? "__asm")) (do (advance!) (skip-parens!) (recur attrs))
      :else attrs)))

;; ---------------------------------------------------------------------------
;; Declarations

(declare declarator abstract-declarator struct-decl enum-decl type-suffix)

(def type-spec-words #{"void" "_Bool" "char" "short" "int" "long" "float" "double" "signed"
                       "__signed__" "__signed" "unsigned" "_Complex"})

(defn build-base-type [counts tk]
  (let [c #(get counts % 0)
        u? (pos? (c "unsigned"))]
    (cond
      (pos? (c "void")) ty-void
      (pos? (c "_Bool")) ty-bool
      (pos? (c "float")) ty-float
      (pos? (c "double")) ty-double
      (pos? (c "char")) (if u? ty-uchar ty-char)
      (pos? (c "short")) (if u? ty-ushort ty-short)
      (pos? (c "long")) (if u? ty-ulong ty-long)
      :else (if u? ty-uint ty-int))))

(declare typeof-spec)

(defn declspec
  "Parses declaration specifiers. Returns [type attrs]."
  []
  (loop [counts {} other nil attr {} seen false]
    (let [tk (tok) s (:text tk)]
      (if-not (= :ident (:kind tk))
        [(or other (build-base-type counts tk)) attr]
        (cond
          (#{"typedef" "static" "extern" "inline" "__inline" "__inline__" "_Noreturn" "auto"
             "register" "_Thread_local" "__thread"} s)
          (do (advance!)
              (recur counts other (assoc attr (keyword (str/replace s #"^_+" "")) true) seen))

          (#{"const" "volatile" "restrict" "__restrict" "__restrict__" "__const" "__volatile__"
             "__volatile" "_Atomic" "__extension__"} s)
          (do (advance!) (recur counts other attr seen))

          (= s "_Alignas")
          (do (advance!) (expect! "(")
              (let [a (if (typename?) (t/type-align (typename)) (const-expr))]
                (expect! ")")
                (recur counts other (assoc attr :align (max a (get attr :align 0))) seen)))

          (= s "__attribute__")
          (let [a (parse-attribute!)]
            (recur counts other (cond-> attr (:aligned a) (assoc :align (:aligned a))) seen))

          (or (= s "struct") (= s "union"))
          (do (advance!) (recur counts (struct-decl (keyword s)) attr true))

          (= s "enum")
          (do (advance!) (recur counts (enum-decl) attr true))

          (#{"typeof" "__typeof__" "__typeof"} s)
          (do (advance!) (recur counts (typeof-spec) attr true))

          (contains? type-spec-words s)
          (do (advance!)
              (let [s (if (#{"__signed__" "__signed"} s) "signed" s)]
                (recur (update counts s (fnil inc 0)) other attr true)))

          (and (not seen) (typedef-type s))
          (do (advance!) (recur counts (typedef-type s) attr true))

          :else [(or other (build-base-type counts tk)) attr])))))

(defn typeof-spec []
  (expect! "(")
  (let [ty (if (typename?) (typename) (:type (expr)))]
    (expect! ")")
    ty))

(defn pointers [ty]
  (loop [ty ty]
    (cond
      (consume! "*")
      (recur (loop [ty (ptr-to ty)]
               (cond
                 (#{"const" "volatile" "restrict" "__restrict" "__restrict__" "__const" "_Atomic" "__volatile__" "__volatile"} (:text (tok)))
                 (do (advance!) (recur ty))
                 (is? "__attribute__") (do (parse-attribute!) (recur ty))
                 :else ty)))
      :else ty)))

(defn adjust-param-type [ty]
  (case (:kind ty)
    (:array :vla) (ptr-to (:base ty))
    :func (ptr-to ty)
    ty))

(defn func-params
  "After '('. Returns function type."
  [ret]
  (cond
    (and (is? "void") (is-tok? (peek-tok 1) ")"))
    (do (advance!) (advance!) (func-type ret [] false true))

    (consume! ")") (assoc (func-type ret [] false false) :param-names [])

    ;; K&R identifier list
    (and (= :ident (:kind (tok))) (not (typename?)) (not (contains? keywords (:text (tok)))))
    (let [names (loop [names [(:text (tok))]]
                  (advance!)
                  (if (consume! ",")
                    (recur (conj names (:text (tok))))
                    names))]
      (expect! ")")
      (assoc (func-type ret (vec (repeat (count names) ty-int)) false false)
             :knr-names names :param-names names))

    :else
    ;; prototype scope: earlier parameters are visible in later declarators
    (let [own-locals (nil? *locals*)]
      (binding [*locals* (or *locals* (volatile! []))]
        (enter-scope!)
        (let [result
              (loop [params [] names [] toks [] pvars [] dtypes []]
                (cond
                  (is? "...")
                  (do (advance!) (expect! ")")
                      [(func-type ret params true true) names toks pvars dtypes])
                  :else
                  (let [[base _] (declspec)
                        {:keys [type name name-tok]} (declarator base)
                        pty (adjust-param-type type)
                        pv {:name name :type pty :id (next-id!) :local true}
                        _ (when name (push-ident! name {:var pv}))
                        params (conj params pty) names (conj names name) toks (conj toks name-tok)
                        pvars (conj pvars pv) dtypes (conj dtypes type)]
                    (cond
                      (consume! ")") [(func-type ret params false true) names toks pvars dtypes]
                      :else (do (expect! ",") (recur params names toks pvars dtypes))))))
              [fty names toks pvars dtypes] result]
          (leave-scope!)
          (assoc fty :param-names names :param-toks toks :param-vars pvars :param-decl-types dtypes
                 :param-temps (when own-locals @*locals*)))))))

(defn array-dims [ty]
  ;; after '['
  (loop [] (when (#{"static" "const" "volatile" "restrict" "__restrict"} (:text (tok))) (advance!) (recur)))
  (cond
    (consume! "]")
    (let [inner (type-suffix ty)]
      (array-of inner -1))
    ;; [*]: VLA of unspecified size (prototypes only)
    (and (is? "*") (is-tok? (peek-tok 1) "]"))
    (do (advance!) (advance!)
        {:kind :vla :base (type-suffix ty) :len-expr nil})
    :else
    (let [e (assign-expr)]
      (expect! "]")
      (let [inner (type-suffix ty)
            n (try-eval e)]
        (if n
          (array-of inner n)
          (if *locals*
            {:kind :vla :base inner :len-expr e}
            (err "variable length array at file scope")))))))

(defn type-suffix [ty]
  (cond
    (consume! "(") (func-params ty)
    (consume! "[") (array-dims ty)
    :else ty))

(defn nested-declarator? []
  ;; at "(": is this a nested declarator rather than a parameter list?
  (let [nx (peek-tok 1)]
    (or (is-tok? nx "*") (is-tok? nx "(") (is-tok? nx "[") (is-tok? nx "^")
        (is-tok? nx "__attribute__")
        (and (= :ident (:kind nx)) (not (typename-tok? nx))
             (not (contains? keywords (:text nx)))))))

(defn declarator
  "Returns {:type :name :name-tok}. Name may be nil (abstract)."
  [ty]
  (let [ty (pointers ty)
        a0 (skip-attributes!)]
    (cond
      (and (is? "(") (nested-declarator?))
      (let [start @*pos*]
        (advance!)
        (declarator ty-int)            ; skip
        (expect! ")")
        (let [ty2 (type-suffix ty)
              end @*pos*]
          (vreset! *pos* (inc start))
          (let [res (declarator ty2)]
            (expect! ")")
            (vreset! *pos* end)
            (update res :attrs merge a0 (skip-attributes!)))))

      (and (= :ident (:kind (tok))) (not (contains? keywords (:text (tok)))))
      (let [name-tok (tok)]
        (advance!)
        (let [ty (type-suffix ty)
              a1 (skip-attributes!)]
          {:type ty :name (:text name-tok) :name-tok name-tok :attrs (merge a0 a1)}))

      :else
      (let [ty (type-suffix ty)
            a1 (skip-attributes!)]
        {:type ty :name nil :name-tok (tok) :attrs (merge a0 a1)}))))

(defn abstract-declarator [ty]
  (:type (declarator ty)))

(defn typename []
  (let [[base _] (declspec)]
    (abstract-declarator base)))

;; struct layout

(defn align-to [n a] (if (zero? a) n (* (quot (+ n a -1) a) a)))

(defn layout-struct [kind members packed align-attr]
  (if (= kind :union)
    (let [members (mapv #(assoc % :offset 0 :bit-offset 0) members)
          al (reduce max 1 (map #(or (:align %) (if packed 1 (t/type-align (:type %)))) members))
          al (max al (or align-attr 1))
          sz (reduce max 0 (map #(t/type-size (:type %)) members))]
      {:members members :size (align-to sz al) :align al})
    (loop [ms members bits 0 al 1 out []]
      (if (empty? ms)
        (let [al (max al (or align-attr 1))]
          {:members out :size (quot (align-to bits (* 8 al)) 8) :align al})
        (let [m (first ms)
              mty (:type m)
              sz (t/type-size mty)
              malign (or (:align m) (if packed 1 (t/type-align mty)))]
          (cond
            (and (:bitfield m) (zero? (:bit-width m)))
            (recur (rest ms) (align-to bits (* 8 sz)) al out)

            (:bitfield m)
            (let [w (:bit-width m)
                  bits (if (and (not packed) (not= (quot bits (* sz 8)) (quot (+ bits w -1) (* sz 8))))
                         (align-to bits (* sz 8)) bits)
                  off (if packed (quot bits 8) (* (quot (quot bits 8) sz) sz))
                  m (assoc m :offset off :bit-offset (- bits (* off 8)))]
              (recur (rest ms) (+ bits w) (if (:name m) (max al malign) al) (conj out m)))

            :else
            (let [bits (align-to bits (* 8 malign))
                  m (assoc m :offset (quot bits 8))]
              (recur (rest ms) (+ bits (* 8 sz)) (max al malign) (conj out m)))))))))

(defn struct-members []
  ;; after "{"
  (loop [members []]
    (cond
      (consume! "}") members
      (is? "_Static_assert") (do (advance!) (skip-parens!) (expect! ";") (recur members))
      :else
      (let [[base attr] (declspec)]
        (if (and (t/struct? base) (consume! ";"))
          ;; anonymous struct/union member
          (recur (conj members {:name nil :type base :idx (count members)}))
          (let [ms (loop [ms [] first? true]
                     (if (consume! ";")
                       ms
                       (do (when-not first? (expect! ","))
                           (let [{:keys [type name attrs]} (if (is? ":") {:type base :name nil} (declarator base))
                                 m {:name name :type type :align (or (:aligned attrs) (:align attr))}
                                 m (if (and (:packed attrs) (not (:aligned attrs))) (assoc m :align 1) m)
                                 m (if (consume! ":")
                                     (assoc m :bitfield true :bit-width (const-expr))
                                     m)
                                 a (skip-attributes!)
                                 m (cond-> m (:aligned a) (assoc :align (:aligned a)))]
                             (recur (conj ms m) false)))))]
            (recur (into members ms))))))))

(defn struct-decl [kind]
  (let [a1 (skip-attributes!)
        tag (when (and (= :ident (:kind (tok))) (not (is? "{"))) (let [s (:text (tok))] (advance!) s))]
    (if (and tag (not (is? "{")))
      (let [existing (find-tag tag)]
        (if (and existing (= kind (:kind existing)))
          existing
          (let [id (next-id!)
                ty {:kind kind :id id :tag tag}]
            (swap! t/*structs* assoc id {:complete false})
            (push-tag! tag ty)
            ty)))
      (do
        (expect! "{")
        (let [existing (when tag (find-tag-current tag))
              ty (if (and existing (= kind (:kind existing)) (not (:complete (t/struct-info existing))))
                   existing
                   (let [id (next-id!)] {:kind kind :id id :tag tag}))
              _ (when tag (push-tag! tag ty))
              members (struct-members)
              a2 (skip-attributes!)
              attrs (merge a1 a2)
              members (vec (map-indexed (fn [i m] (assoc m :idx i)) members))
              ;; flexible array member
              members (if (and (seq members) (= :array (:kind (:type (peek members))))
                               (<= (:len (:type (peek members))) 0))
                        (update members (dec (count members)) #(assoc % :type (assoc (:type %) :len 0) :flex true))
                        members)
              lay (layout-struct kind members (:packed attrs) (:aligned attrs))]
          (swap! t/*structs* assoc (:id ty) (assoc lay :complete true :kind kind))
          ty)))))

(defn enum-decl []
  (let [a1 (skip-attributes!)
        tag (when (= :ident (:kind (tok))) (let [s (:text (tok))] (advance!) s))]
    (if (and tag (not (is? "{")))
      (or (find-tag tag) ty-uint)
      (do
        (expect! "{")
        (let [vals (loop [v 0 vals []]
                     (if (consume! "}")
                       vals
                       (let [nm (:text (tok))]
                         (advance!)
                         (skip-attributes!)
                         (let [v (if (consume! "=") (const-expr) v)
                               cty (if (<= -2147483648 v 2147483647) ty-int ty-long)]
                           (push-ident! nm {:enum-val v :type cty})
                           (when-not (is? "}") (expect! ","))
                           (recur (inc v) (conj vals v))))))
              lo (reduce min 0 vals)
              hi (reduce max 0 vals)
              ;; gcc: unsigned int when no enumerator is negative
              ety (if (neg? lo)
                    (if (and (>= lo -2147483648) (<= hi 2147483647)) ty-int ty-long)
                    (if (<= hi 0xffffffff) ty-uint ty-ulong))
              a2 (skip-attributes!)
              ety (if (:packed (merge a1 a2))
                    (cond (and (>= lo -128) (<= hi 127)) ty-char
                          (and (>= lo 0) (<= hi 255)) ty-uchar
                          (and (>= lo -32768) (<= hi 32767)) ty-short
                          (and (>= lo 0) (<= hi 65535)) ty-ushort
                          :else ety)
                    ety)
              ety (assoc ety :enum true)]
          (when tag (push-tag! tag ety))
          ety)))))

;; ---------------------------------------------------------------------------
;; Initializers (mutable tree, chibicc style)

(defn new-init [ty]
  (let [ty (t/strip ty)]
    (atom (cond-> {:ty ty}
            (= :array (:kind ty)) (assoc :elems {} :count 0)
            (t/struct? ty) (assoc :fields
                                  (mapv (fn [m] (new-init (if (:flex m) (assoc (:type m) :len -1) (:type m))))
                                        (:members (t/struct-info ty))))))))

(defn arr-len [ty] (let [l (:len ty)] (if (neg? l) Long/MAX_VALUE l)))

(defn init-child [init i]
  (let [ty (:ty @init)]
    (or (get-in @init [:elems i])
        (let [c (new-init (:base ty))]
          (swap! init (fn [m] (-> m (assoc-in [:elems i] c) (update :count max (inc i)))))
          c))))

(defn is-end? []
  (or (is? "}") (and (is? ",") (is-tok? (peek-tok 1) "}"))))

(defn consume-end! []
  (cond (is? "}") (do (advance!) true)
        (and (is? ",") (is-tok? (peek-tok 1) "}")) (do (advance!) (advance!) true)
        :else false))

(declare initializer2 designation field-desig?)

(defn skip-excess! []
  (if (consume! "{")
    (do (loop [] (when-not (consume-end!) (skip-excess!) (when-not (is? "}") (expect! ",")) (recur))))
    (assign-expr)))

(defn string-tok-values
  "Parse adjacent string tokens starting at current. Returns [vals elem-type]."
  []
  (loop [vals [] wide nil]
    (if (= :str (:kind (tok)))
      (let [tk (tok)
            pre (:prefix tk "")
            w (case pre "L" ty-int "u" ty-ushort "U" ty-uint nil)]
        (advance!)
        (recur (into vals (lx/decode-string tk)) (or wide w)))
      [vals (or wide ty-char)])))

(defn string-initializer! [init]
  (let [[vals _] (string-tok-values)
        ty (:ty @init)
        vals (conj vals 0)
        n (if (neg? (:len ty)) (count vals) (min (:len ty) (count vals)))]
    (doseq [i (range n)]
      (swap! (init-child init i) assoc :expr (num-node (nth vals i) (:base ty))))))

(defn array-designator []
  ;; at "[" ; returns [begin end]
  (expect! "[")
  (let [b (const-expr)
        e (if (consume! "...") (const-expr) b)]
    (expect! "]")
    [b e]))

(defn array-initializer2! [init i]
  (let [len (arr-len (:ty @init))]
    (loop [i i first? true]
      (if (and (< i len) (not (is-end?)))
        (let [start @*pos*]
          (when (> i 0) (expect! ","))
          (if (or (is? "[") (field-desig?))
            (do (vreset! *pos* start) i)
            (do (initializer2 (init-child init i))
                (recur (inc i) false))))
        i))))

(defn members-of [ty] (:members (t/struct-info ty)))

(defn field-desig? []
  (or (is? ".")
      (and (= :ident (:kind (tok))) (is-tok? (peek-tok 1) ":"))))

(defn struct-designator
  "at '.' (or GNU 'name:'); returns [path-of-member-indices] resolving anonymous members"
  [ty]
  (let [gnu (not (is? "."))
        _ (when-not gnu (advance!))
        nm (:text (tok))]
    (advance!)
    (when gnu (expect! ":"))
    (let [path (find-member-path ty nm)]
      (when-not path (err (str "no member named '" nm "'")))
      path)))

(defn skip-unnamed
  "Index of the first member at or after idx that takes an initializer
   (unnamed bit-fields do not)."
  [ms idx]
  (loop [i idx]
    (if (and (< i (count ms)) (:bitfield (nth ms i)) (nil? (:name (nth ms i))))
      (recur (inc i))
      i)))

(defn struct-initializer2! [init start-idx post-desig]
  (let [ms (members-of (:ty @init))]
    (loop [idx (skip-unnamed ms start-idx) first? (not post-desig)]
      (when (and (< idx (count ms)) (not (is-end?)))
        (let [start @*pos*]
          (when-not first? (expect! ","))
          (if (or (is? "[") (field-desig?))
            (vreset! *pos* start)
            (do (initializer2 (nth (:fields @init) idx))
                (recur (skip-unnamed ms (inc idx)) false))))))))

(defn designate-path!
  "Follow anonymous member path from a struct/union init; returns [init-of-last-parent last-member]."
  [init path]
  (loop [init init path path]
    (let [m (first path)
          ty (:ty @init)]
      (when (= :union (:kind ty)) (swap! init assoc :mem (:idx m)))
      (swap! init dissoc :expr)
      (if (next path)
        (recur (nth (:fields @init) (:idx m)) (rest path))
        [init m]))))

(defn designation [init]
  (let [ty (:ty @init)]
    (cond
      (is? "[")
      (do (when-not (= :array (:kind ty)) (err "array index in non-array initializer"))
          (let [[b e] (array-designator)
                start @*pos*]
            (doseq [j (range b (inc e))]
              (vreset! *pos* start)
              (designation (init-child init j)))
            (array-initializer2! init (inc e))))

      (and (field-desig?) (t/struct? ty))
      (let [path (struct-designator ty)
            [parent m] (designate-path! init path)]
        (designation (nth (:fields @parent) (:idx m)))
        (when (= :struct (:kind (:ty @parent)))
          (struct-initializer2! parent (inc (:idx m)) true)))

      (is? ".") (err "field name not in record or union initializer")

      :else (do (consume! "=") (initializer2 init)))))

(defn array-initializer1! [init]
  (expect! "{")
  (let [ty (:ty @init)]
    (if (and (= :str (:kind (tok))) (t/integer? (:base ty)) (<= (t/type-size (:base ty)) 4)
             (let [save @*pos*] (string-tok-values) (let [r (is-end?)] (vreset! *pos* save) r)))
      (do (string-initializer! init) (consume-end!))
      (let [len (arr-len ty)]
        (loop [i 0 first? true]
          (when-not (consume-end!)
            (when-not first? (expect! ","))
            (if (is? "[")
              (let [[b e] (array-designator)
                    start @*pos*]
                (doseq [j (range b (inc e))]
                  (vreset! *pos* start)
                  (designation (init-child init j)))
                (recur (array-initializer2! init (inc e)) false))
              (do (if (< i len)
                    (initializer2 (init-child init i))
                    (skip-excess!))
                  (recur (inc i) false)))))))))

(defn struct-initializer1! [init]
  (expect! "{")
  (let [ms (members-of (:ty @init))]
    (loop [idx 0 first? true]
      (when-not (consume-end!)
        (when-not first? (expect! ","))
        (if (field-desig?)
          (let [path (struct-designator (:ty @init))
                [parent m] (designate-path! init path)]
            (designation (nth (:fields @parent) (:idx m)))
            (if (identical? parent init)
              (recur (inc (:idx m)) false)
              (do (when (= :struct (:kind (:ty @parent)))
                    (struct-initializer2! parent (inc (:idx m)) true))
                  (recur (inc (:idx (first path))) false))))
          (let [idx (skip-unnamed ms idx)]
            (if (< idx (count ms))
              (initializer2 (nth (:fields @init) idx))
              (skip-excess!))
            (recur (inc idx) false)))))))

(defn union-initializer! [init]
  (let [ms (members-of (:ty @init))]
    (if (consume! "{")
      (do
        (if (field-desig?)
          (let [path (struct-designator (:ty @init))
                [parent m] (designate-path! init path)]
            (designation (nth (:fields @parent) (:idx m))))
          (when (seq ms)
            (swap! init assoc :mem 0)
            (initializer2 (nth (:fields @init) 0))))
        (loop [] (when-not (consume-end!) (expect! ",") (skip-excess!) (recur))))
      (when (seq ms)
        (swap! init assoc :mem 0)
        (initializer2 (nth (:fields @init) 0))))))

(defn initializer2 [init]
  (let [ty (:ty @init)]
    (cond
      (and (= :array (:kind ty)) (= :str (:kind (tok))) (t/integer? (:base ty)))
      (string-initializer! init)

      ;; gcc extension: parenthesized string literal
      (and (= :array (:kind ty)) (is? "(") (= :str (:kind (peek-tok 1))) (t/integer? (:base ty)))
      (do (advance!) (string-initializer! init) (expect! ")"))

      (= :array (:kind ty))
      (if (is? "{")
        (array-initializer1! init)
        (array-initializer2! init 0))

      (= :struct (:kind ty))
      (if (is? "{")
        (struct-initializer1! init)
        (let [start @*pos*
              e (assign-expr)]
          (if (and (t/struct? (:type e)) (= (:id (:type e)) (:id ty)))
            (swap! init assoc :expr e)
            (do (vreset! *pos* start)
                (struct-initializer2! init 0 false)))))

      (= :union (:kind ty))
      (if (is? "{")
        (union-initializer! init)
        (let [start @*pos*
              e (assign-expr)]
          (if (and (t/struct? (:type e)) (= (:id (:type e)) (:id ty)))
            (swap! init assoc :expr e)
            (do (vreset! *pos* start)
                (union-initializer! init)))))

      (and (is? "{") (is-tok? (peek-tok 1) "}"))
      (do (advance!) (advance!))

      (is? "{")
      (do (advance!)
          (initializer2 init)
          (loop [] (when-not (consume-end!) (expect! ",") (skip-excess!) (recur))))

      :else
      (swap! init assoc :expr (assign-expr)))))

(defn parse-initializer
  "Returns [init-atom final-type]."
  [ty]
  (let [init (new-init ty)]
    (initializer2 init)
    (let [ty (t/strip ty)
          ty (if (and (= :array (:kind ty)) (neg? (:len ty)))
               (assoc ty :len (:count @init))
               ty)]
      (swap! init assoc :ty ty)
      [init ty])))

(defn init-has-data? [init]
  (let [m @init]
    (or (some? (:expr m))
        (some init-has-data? (vals (:elems m)))
        (if (and (:mem m) (= :union (:kind (:ty m))))
          (init-has-data? (nth (:fields m) (:mem m)))
          (some init-has-data? (:fields m))))))

(defn lvar-init-exprs
  "Assignment expressions for initializing designator node `desg`."
  [init desg]
  (let [m @init ty (:ty m)]
    (cond
      (some? (:expr m)) [(new-assign desg (:expr m))]
      (= :array (:kind ty))
      (vec (mapcat (fn [[i c]]
                     (lvar-init-exprs c (new-deref (new-add desg (num-node i ty-long)))))
                   (sort-by key (:elems m))))
      (= :struct (:kind ty))
      (let [ms (members-of ty)]
        (vec (mapcat (fn [mem c]
                       (when (init-has-data? c)
                         (lvar-init-exprs c {:op :member :type (:type mem) :expr desg :member mem})))
                     ms (:fields m))))
      (= :union (:kind ty))
      (if-let [i (:mem m)]
        (let [mem (nth (members-of ty) i)]
          (lvar-init-exprs (nth (:fields m) i) {:op :member :type (:type mem) :expr desg :member mem}))
        [])
      :else [])))

(defn lvar-initializer [v init]
  (let [desg (var-node v)
        m @init
        assigns (lvar-init-exprs init desg)]
    (if (and (or (t/scalar? (:type v)) (and (t/struct? (:type v)) (some? (:expr m)))))
      (if (seq assigns) (reduce new-comma assigns) {:op :memzero :type ty-void :var v})
      (reduce new-comma {:op :memzero :type ty-void :var v} assigns))))

;; Global data

(defn write-int! [^bytes buf off v size]
  (dotimes [i size]
    (aset buf (+ off i) (unchecked-byte (bit-shift-right (long v) (* 8 i))))))

(defn read-int [^bytes buf off size]
  (reduce (fn [acc i] (bit-or acc (bit-shift-left (bit-and (long (aget buf (+ off i))) 0xff) (* 8 i)))) 0 (range size)))

(defn write-gvar-data!
  "Writes init into buf at offset; returns vector of relocs."
  [init ^bytes buf off]
  (let [m @init ty (:ty m)]
    (cond
      (and (= :array (:kind ty)) (nil? (:expr m)))
      (let [sz (t/type-size (:base ty))]
        (vec (mapcat (fn [[i c]] (write-gvar-data! c buf (+ off (* i sz)))) (:elems m))))

      (and (= :struct (:kind ty)) (nil? (:expr m)))
      (vec (mapcat (fn [mem c]
                     (if (:bitfield mem)
                       (when-let [e (:expr @c)]
                         (let [v (eval-int e)
                               sz (t/type-size (:type mem))
                               o (+ off (:offset mem))
                               w (:bit-width mem)
                               bo (:bit-offset mem)
                               nbytes (quot (+ bo w 7) 8)
                               old (read-int buf o nbytes)
                               mask (bit-shift-left (if (>= w 64) -1 (dec (bit-shift-left 1 w))) bo)
                               nv (bit-or (bit-and old (bit-not mask)) (bit-and (bit-shift-left v bo) mask))]
                           (write-int! buf o nv nbytes)
                           nil))
                       (write-gvar-data! c buf (+ off (:offset mem)))))
                   (members-of ty) (:fields m)))

      (and (= :union (:kind ty)) (nil? (:expr m)))
      (if-let [i (:mem m)]
        (write-gvar-data! (nth (:fields m) i) buf (+ off (:offset (nth (members-of ty) i))))
        [])

      (nil? (:expr m)) []

      (t/struct? ty)
      ;; struct initialized from expression: allow compound literal variables
      (let [e (:expr m)]
        (if (and (= :var (:op e)) (:init (:var e)))
          (let [src (:init (@*globals* (:label (:var e))))]
            (System/arraycopy (:bytes src) 0 buf off (t/type-size ty))
            (mapv #(update % :off + off) (:relocs src)))
          (if (and (= :comma (:op e)) (= :var (:op (:rhs e))))
            (let [src (:init (@*globals* (:label (:var (:rhs e)))))]
              (System/arraycopy (:bytes src) 0 buf off (t/type-size ty))
              (mapv #(update % :off + off) (:relocs src)))
            (err "initializer element is not constant"))))

      (t/flonum? ty)
      (let [d (try (eval-double (new-cast (:expr m) ty))
                   (catch clojure.lang.ExceptionInfo e
                     (if (= :not-const (:type (ex-data e))) (err "initializer element is not constant") (throw e))))]
        (if (= :float (:kind ty))
          (write-int! buf off (Float/floatToRawIntBits (unchecked-float d)) 4)
          (write-int! buf off (Double/doubleToRawLongBits d) 8))
        [])

      :else
      (let [[l v] (try (eval-reloc (new-cast (:expr m) ty))
                       (catch clojure.lang.ExceptionInfo e
                         (if (= :not-const (:type (ex-data e))) (err "initializer element is not constant") (throw e))))]
        (if l
          [{:off off :label l :addend v :size (t/type-size ty)}]
          (do (write-int! buf off v (t/type-size ty)) []))))))

(defn init-object-size
  "Size of the object, including statically initialized flexible array members."
  [init ty]
  (let [sz (t/type-size ty)]
    (if (and (= :struct (:kind ty)) (nil? (:expr @init)))
      (let [ms (members-of ty) m (peek ms)]
        (if (and m (:flex m))
          (let [c (:count @(peek (:fields @init)))]
            (max sz (+ (:offset m) (* c (t/type-size (:base (:type m)))))))
          sz))
      sz)))

(defn gvar-init-data [init ty]
  (let [buf (byte-array (init-object-size init ty))
        relocs (write-gvar-data! init buf 0)]
    {:bytes buf :relocs (vec (sort-by :off relocs))}))

;; ---------------------------------------------------------------------------
;; Expressions

(declare cast-expr unary postfix primary compound-stmt stmt declaration)

(defn expr []
  (loop [n (assign-expr)]
    (if (consume! ",")
      (recur (new-comma n (assign-expr)))
      n)))

(def assign-ops {"+=" new-add "-=" new-sub "*=" new-mul "/=" new-div "%=" new-mod
                 "&=" new-bitand "|=" new-bitor "^=" new-bitxor
                 "<<=" #(new-shift :shl %1 %2) ">>=" #(new-shift :shr %1 %2)})

(declare conditional)

(defn assign-expr []
  (let [lhs (conditional)]
    (cond
      (consume! "=") (new-assign lhs (assign-expr))
      (and (= :punct (:kind (tok))) (assign-ops (:text (tok))))
      (let [f (assign-ops (:text (tok)))]
        (advance!)
        (compound-assign f lhs (assign-expr)))
      :else lhs)))

(declare logor)

(defn conditional []
  (let [c (logor)]
    (if (consume! "?")
      (if (consume! ":")
        ;; GNU a ?: b
        (let [els (conditional)
              tmp (new-temp! (decay (:type c)))]
          (new-comma (new-assign (var-node tmp) c)
                     (new-cond (var-node tmp) (var-node tmp) els)))
        (let [then (expr)]
          (expect! ":")
          (new-cond c then (conditional))))
      c)))

(defmacro binlevel [name next ops]
  `(defn ~name []
     (loop [n# (~next)]
       (let [tk# (tok)]
         (if-let [f# (and (= :punct (:kind tk#)) (get ~ops (:text tk#)))]
           (do (advance!) (recur (f# n# (~next))))
           n#)))))

(declare logand bitor bitxor bitand equality relational shift additive multiplicative)

(binlevel multiplicative cast-expr {"*" new-mul "/" new-div "%" new-mod})
(binlevel additive multiplicative {"+" new-add "-" new-sub})
(binlevel shift additive {"<<" #(new-shift :shl %1 %2) ">>" #(new-shift :shr %1 %2)})
(binlevel relational shift {"<" #(new-compare :lt %1 %2) "<=" #(new-compare :le %1 %2)
                            ">" #(new-compare :gt %1 %2) ">=" #(new-compare :ge %1 %2)})
(binlevel equality relational {"==" #(new-compare :eq %1 %2) "!=" #(new-compare :ne %1 %2)})
(binlevel bitand equality {"&" new-bitand})
(binlevel bitxor bitand {"^" new-bitxor})
(binlevel bitor bitxor {"|" new-bitor})
(binlevel logand bitor {"&&" #(new-logical :logand %1 %2)})
(binlevel logor logand {"||" #(new-logical :logor %1 %2)})

(defn const-expr []
  (let [n (conditional)]
    (try (eval-int n)
         (catch clojure.lang.ExceptionInfo e
           (if (= :not-const (:type (ex-data e)))
             (err "expression is not an integer constant")
             (throw e))))))

(defn compound-literal [ty]
  ;; at "{"
  (if (nil? *cur-fn*)
    (let [[init ty] (parse-initializer ty)
          g (anon-global! ty (gvar-init-data init ty))]
      (var-node g))
    (let [[init ty] (parse-initializer ty)
          v (new-temp! ty)]
      (new-comma (lvar-initializer v init) (var-node v)))))

(defn cast-expr []
  (if (and (is? "(") (typename-tok? (peek-tok 1)))
    (let [start @*pos*]
      (advance!)
      (let [ty (typename)]
        (expect! ")")
        (if (is? "{")
          (do (vreset! *pos* start) (unary))
          (let [e (cast-expr)]
            (when-not (or (t/scalar? (decay (:type e))) (t/void? ty) (t/struct? (:type e)))
              (err "invalid cast operand"))
            (new-cast (if (t/void? ty) e e) ty)))))
    (unary)))

(defn sizeof-type [ty]
  (when (= :vla (:kind ty)) (err "sizeof VLA unsupported here"))
  (num-node (t/type-size ty) ty-ulong))

(defn vla-size-node [ty]
  ;; returns node computing sizeof(ty) at runtime
  (if (runtime-sized? ty)
    (vla-size-expr ty)
    (num-node (t/type-size ty) ty-ulong)))

(defn unary []
  (let [tk (tok) s (:text tk) k (:kind tk)]
    (cond
      (and (= k :punct) (= s "+")) (do (advance!) (let [e (cast-expr)] (new-cast e (if (t/integer? (:type e)) (t/promote (:type e)) (:type e)))))
      (and (= k :punct) (= s "-")) (do (advance!) (new-neg (cast-expr)))
      (and (= k :punct) (= s "!")) (do (advance!) (new-not (cast-expr)))
      (and (= k :punct) (= s "~")) (do (advance!) (new-bitnot (cast-expr)))
      (and (= k :punct) (= s "&")) (do (advance!) (new-addr (cast-expr)))
      (and (= k :punct) (= s "*")) (do (advance!) (new-deref (cast-expr)))
      (and (= k :punct) (= s "++")) (do (advance!) (let [e (unary)] (or (direct-incdec e 1 true) (compound-assign new-add e (num-node 1 ty-int)))))
      (and (= k :punct) (= s "--")) (do (advance!) (let [e (unary)] (or (direct-incdec e -1 true) (compound-assign new-sub e (num-node 1 ty-int)))))
      (and (= k :punct) (= s "&&"))
      (do (advance!)
          (let [nm (:text (tok))]
            (advance!)
            {:op :label-addr :type (ptr-to ty-void) :label (or (@*labels* nm)
                                                                (let [l (new-label)] (vswap! *labels* assoc nm l) l))}))
      (and (= k :ident) (= s "sizeof"))
      (do (advance!)
          (if (and (is? "(") (typename-tok? (peek-tok 1)))
            (let [start @*pos*]
              (advance!)
              (let [ty (typename)]
                (expect! ")")
                (if (is? "{")
                  (do (vreset! *pos* start)
                      (let [e (unary)] (vla-size-node (:type e))))
                  (vla-size-node ty))))
            (let [e (unary)]
              (if (runtime-sized? (:type e))
                (vla-size-node (:type e))
                (num-node (t/type-size (:type e)) ty-ulong)))))
      (and (= k :ident) (#{"_Alignof" "__alignof__" "__alignof"} s))
      (do (advance!)
          (if (and (is? "(") (typename-tok? (peek-tok 1)))
            (do (advance!) (let [ty (typename)] (expect! ")") (num-node (t/type-align ty) ty-ulong)))
            (let [e (unary)] (num-node (t/type-align (:type e)) ty-ulong))))
      (and (= k :ident) (= s "__extension__")) (do (advance!) (cast-expr))
      :else (postfix))))

(defn func-args []
  ;; after "("
  (if (consume! ")")
    []
    (loop [args [(assign-expr)]]
      (if (consume! ")")
        args
        (do (expect! ",") (recur (conj args (assign-expr))))))))

(defn postfix-ops [n]
  (loop [n n]
    (let [tk (tok)]
      (cond
        (is? "[")
        (do (advance!)
            (let [idx (expr)]
              (expect! "]")
              (recur (new-deref (new-add n idx)))))
        (is? "(")
        (do (advance!)
            (recur (new-call n (func-args))))
        (is? ".")
        (do (advance!)
            (let [nm (:text (tok))] (advance!) (recur (new-member n nm))))
        (is? "->")
        (do (advance!)
            (let [nm (:text (tok))] (advance!) (recur (new-member (new-deref n) nm))))
        (is? "++") (do (advance!) (recur (post-incdec n 1)))
        (is? "--") (do (advance!) (recur (post-incdec n -1)))
        :else n))))

(defn postfix []
  (if (and (is? "(") (typename-tok? (peek-tok 1)))
    ;; compound literal
    (do (advance!)
        (let [ty (typename)]
          (expect! ")")
          (when-not (is? "{") (err "expected compound literal"))
          (postfix-ops (compound-literal ty))))
    (postfix-ops (primary))))

(defn string-literal-node []
  (let [[vals ety] (string-tok-values)
        vals (conj vals 0)
        n (count vals)
        ty (array-of ety n)
        esz (t/type-size ety)
        buf (byte-array (* n esz))]
    (doseq [i (range n)] (write-int! buf (* i esz) (nth vals i) esz))
    ;; identical literals share storage, as with gcc
    (let [k [(:kind ety) (:unsigned ety) vals]]
      (var-node (or (get @*strlits* k)
                    (let [g (add-global! (assoc (anon-global! ty {:bytes buf :relocs []}) :strlit true))]
                      (vswap! *strlits* assoc k g)
                      g))))))

(def libc-protos
  "Prototypes gcc knows as builtins for the libc functions of the provided headers."
  (let [vp (ptr-to ty-void) cp (ptr-to ty-char) ul ty-ulong
        cmpfn (ptr-to (func-type ty-int [vp vp] false true))]
    {"printf" [ty-int [cp] true] "sprintf" [ty-int [cp cp] true] "snprintf" [ty-int [cp ul cp] true]
     "vprintf" [ty-int [cp vp] false] "vsprintf" [ty-int [cp cp vp] false] "vsnprintf" [ty-int [cp ul cp vp] false]
     "puts" [ty-int [cp] false] "putchar" [ty-int [ty-int] false]
     "malloc" [vp [ul] false] "calloc" [vp [ul ul] false] "realloc" [vp [vp ul] false] "free" [ty-void [vp] false]
     "exit" [ty-void [ty-int] false] "abort" [ty-void [] false] "atoi" [ty-int [cp] false] "atol" [ty-long [cp] false]
     "abs" [ty-int [ty-int] false] "labs" [ty-long [ty-long] false] "llabs" [ty-long [ty-long] false]
     "qsort" [ty-void [vp ul ul cmpfn] false]
     "strlen" [ul [cp] false] "strcmp" [ty-int [cp cp] false] "strncmp" [ty-int [cp cp ul] false]
     "strcpy" [cp [cp cp] false] "strncpy" [cp [cp cp ul] false] "strcat" [cp [cp cp] false]
     "strncat" [cp [cp cp ul] false] "strchr" [cp [cp ty-int] false] "strrchr" [cp [cp ty-int] false]
     "strstr" [cp [cp cp] false] "memcpy" [vp [vp vp ul] false] "memmove" [vp [vp vp ul] false]
     "memset" [vp [vp ty-int ul] false] "memcmp" [ty-int [vp vp ul] false] "memchr" [vp [vp ty-int ul] false]}))

(defn declare-implicit-func! [name]
  (let [ty (if-let [[r plist v] (libc-protos name)] (func-type r plist v true) (func-type ty-int [] false false))
        g (or (@*globals* name)
              (add-global! {:name name :label name :type ty :global true :func true}))]
    (vswap! *scope* (fn [sc] (let [v (vec sc)
                                    i (dec (count v))]
                               ;; push into outermost (file) scope
                               (into () (reverse (assoc-in v [i :vars name] {:var g})))
                               )))
    g))

(declare builtin-call)

(defn primary []
  (let [tk (tok)]
    (case (:kind tk)
      :pnum (do (advance!) (parse-number tk))
      :char (do (advance!)
                (let [v (lx/char-value tk)]
                  (num-node v (case (:prefix tk "") "" ty-int "L" ty-int "u" ty-ushort "U" ty-uint ty-int))))
      :str (string-literal-node)
      :punct
      (cond
        (and (is? "(") (is-tok? (peek-tok 1) "{"))
        ;; statement expression
        (do (advance!) (advance!)
            (let [body (compound-stmt)]
              (expect! ")")
              (let [last-stmt (last (:body body))
                    ty (if (and last-stmt (= :expr-stmt (:op last-stmt)))
                         (:type (:expr last-stmt)) ty-void)]
                {:op :stmt-expr :type (decay ty) :body body})))
        (consume! "(")
        (let [e (expr)] (expect! ")") e)
        :else (err "expected expression"))
      :ident
      (let [nm (:text tk)]
        (cond
          (str/starts-with? nm "__builtin_") (builtin-call)
          (= nm "_Generic")
          (do (advance!) (expect! "(")
              (let [ctl (assign-expr)
                    cty (decay (t/strip (:type ctl)))]
                (loop [chosen nil default nil]
                  (if (consume! ")")
                    (or chosen default (err "no matching _Generic association"))
                    (do (expect! ",")
                        (if (consume! "default")
                          (do (expect! ":") (recur chosen (assign-expr)))
                          (let [ty (typename)]
                            (expect! ":")
                            (let [e (assign-expr)]
                              (recur (or chosen (when (t/same-type? (t/strip ty) cty) e)) default)))))))))
          (or (= nm "__func__") (= nm "__FUNCTION__") (= nm "__PRETTY_FUNCTION__"))
          (do (advance!)
              (let [s (:name *cur-fn*)
                    bs (.getBytes (str s) "ISO-8859-1")
                    buf (byte-array (inc (count bs)))]
                (System/arraycopy bs 0 buf 0 (count bs))
                (var-node (anon-global! (array-of ty-char (count buf)) {:bytes buf :relocs []}))))
          (contains? keywords nm) (err "expected expression")
          :else
          (do (advance!)
              (let [e (find-ident nm)]
                (cond
                  ;; fabs is always expanded inline by gcc (it is not in libc)
                  (and (#{"fabs" "fabsf"} nm) (is? "(") (not (:defined (@*globals* nm))))
                  (do (expect! "(") (let [a (assign-expr) ty (if (= nm "fabsf") ty-float ty-double)]
                                      (expect! ")")
                                      {:op :fabs :type ty :expr (new-cast a ty)}))
                  (:var e) (var-node (:var e))
                  (contains? e :enum-val) (num-node (:enum-val e) (:type e))
                  ;; gcc treats undeclared alloca as the builtin
                  (and (= nm "alloca") (is? "("))
                  (do (expect! "(") (let [n (assign-expr)] (expect! ")")
                                      {:op :alloca :type (ptr-to ty-void) :expr (new-cast n ty-ulong)}))
                  (is? "(") (var-node (declare-implicit-func! nm))
                  :else (err-tok tk (str "undeclared identifier '" nm "'")))))))
      (err "expected expression"))))

(defn builtin-call []
  (let [tk (tok) nm (:text tk)]
    (advance!)
    (case nm
      "__builtin_va_start"
      (do (expect! "(")
          (let [ap (assign-expr)]
            (when (consume! ",") (assign-expr))
            (expect! ")")
            {:op :va-start :type ty-void :expr ap}))
      "__builtin_va_end"
      (do (expect! "(") (let [ap (assign-expr)] (expect! ")") {:op :cast :type ty-void :expr ap}))
      "__builtin_va_copy"
      (do (expect! "(")
          (let [d (assign-expr)] (expect! ",")
               (let [s (assign-expr)] (expect! ")")
                    {:op :va-copy :type ty-void :lhs d :rhs s})))
      "__builtin_va_arg"
      (do (expect! "(")
          (let [ap (assign-expr)]
            (expect! ",")
            (let [ty (t/strip (typename))]
              (expect! ")")
              (new-deref {:op :va-arg :type (ptr-to ty) :expr ap
                          :tmp (when (t/struct? ty) (new-temp! ty))}))))
      "__builtin_offsetof"
      (do (expect! "(")
          (let [ty (typename)]
            (expect! ",")
            (let [base {:op :deref :type ty :expr (num-node 0 (ptr-to ty))}
                  e (loop [e base]
                      (cond
                        (consume! ".") (let [n (:text (tok))] (advance!) (recur (new-member e n)))
                        (consume! "[") (let [i (expr)] (expect! "]") (recur (new-deref (new-add e i))))
                        (= :ident (:kind (tok))) (let [n (:text (tok))] (advance!) (recur (new-member e n)))
                        :else e))]
              (expect! ")")
              (num-node (second (eval-addr e)) ty-ulong))))
      "__builtin_expect"
      (do (expect! "(") (let [a (assign-expr)] (expect! ",") (assign-expr) (expect! ")") (new-cast a ty-long)))
      "__builtin_constant_p"
      (do (expect! "(") (let [a (assign-expr)] (expect! ")") (num-node (if (try-eval a) 1 0) ty-int)))
      "__builtin_unreachable"
      (do (expect! "(") (expect! ")") {:op :cast :type ty-void :expr (num-node 0 ty-int)})
      "__builtin_trap"
      (do (expect! "(") (expect! ")") {:op :trap :type ty-void})
      "__builtin_types_compatible_p"
      (do (expect! "(") (let [a (typename)] (expect! ",") (let [b (typename)] (expect! ")")
                                                                 (num-node (if (t/same-type? (t/strip a) (t/strip b)) 1 0) ty-int))))
      ("__builtin_choose_expr")
      (do (expect! "(")
          (let [c (const-expr) _ (expect! ",") a (assign-expr) _ (expect! ",") b (assign-expr)]
            (expect! ")")
            (if (zero? c) b a)))
      ("__builtin_alloca")
      (do (expect! "(") (let [n (assign-expr)] (expect! ")")
                          {:op :alloca :type (ptr-to ty-void) :expr (new-cast n ty-ulong)}))
      ("__builtin_prefetch")
      (do (expect! "(")
          (let [args (func-args)]
            (reduce new-comma {:op :cast :type ty-void :expr (num-node 0 ty-int)}
                    (map #(new-cast % ty-void) args))))
      ("__builtin_fabs" "__builtin_fabsf")
      (do (expect! "(") (let [a (assign-expr) ty (if (= nm "__builtin_fabsf") ty-float ty-double)]
                          (expect! ")")
                          {:op :fabs :type ty :expr (new-cast a ty)}))
      ("__builtin_inf" "__builtin_huge_val")
      (do (expect! "(") (expect! ")") (fnum-node Double/POSITIVE_INFINITY ty-double))
      ("__builtin_inff" "__builtin_huge_valf")
      (do (expect! "(") (expect! ")") (fnum-node Double/POSITIVE_INFINITY ty-float))
      ("__builtin_nan" "__builtin_nanf")
      (do (expect! "(") (assign-expr) (expect! ")")
          (fnum-node Double/NaN (if (= nm "__builtin_nanf") ty-float ty-double)))
      ("__builtin_popcount" "__builtin_popcountl" "__builtin_popcountll"
       "__builtin_clz" "__builtin_clzl" "__builtin_clzll"
       "__builtin_ctz" "__builtin_ctzl" "__builtin_ctzll"
       "__builtin_ffs" "__builtin_ffsl" "__builtin_ffsll"
       "__builtin_parity" "__builtin_parityl" "__builtin_parityll"
       "__builtin_clrsb" "__builtin_clrsbl" "__builtin_clrsbll")
      (do (expect! "(")
          (let [a (assign-expr)
                wide (or (str/ends-with? nm "l") (str/ends-with? nm "ll"))
                base (str/replace (subs nm 10) #"l+$" "")
                at (if wide (if (#{"ffs" "clrsb"} base) ty-long ty-ulong)
                       (if (#{"ffs" "clrsb"} base) ty-int ty-uint))]
            (expect! ")")
            {:op :bitop :name (keyword base) :type ty-int :expr (new-cast a at)}))
      ("__builtin_bswap16" "__builtin_bswap32" "__builtin_bswap64")
      (do (expect! "(")
          (let [a (assign-expr)
                ty (case nm "__builtin_bswap16" ty-ushort "__builtin_bswap32" ty-uint ty-ulong)]
            (expect! ")")
            {:op :bswap :type ty :expr (new-cast a ty)}))
      ("__builtin_add_overflow" "__builtin_sub_overflow" "__builtin_mul_overflow"
       "__builtin_sadd_overflow" "__builtin_saddl_overflow" "__builtin_saddll_overflow"
       "__builtin_uadd_overflow" "__builtin_uaddl_overflow" "__builtin_uaddll_overflow"
       "__builtin_ssub_overflow" "__builtin_ssubl_overflow" "__builtin_ssubll_overflow"
       "__builtin_usub_overflow" "__builtin_usubl_overflow" "__builtin_usubll_overflow"
       "__builtin_smul_overflow" "__builtin_smull_overflow" "__builtin_smulll_overflow"
       "__builtin_umul_overflow" "__builtin_umull_overflow" "__builtin_umulll_overflow")
      (do (expect! "(")
          (let [a (assign-expr) _ (expect! ",") b (assign-expr) _ (expect! ",") r (assign-expr)
                _ (expect! ")")
                m (re-matches #"__builtin_([su]?)(add|sub|mul)(l*)_overflow" nm)
                sign (nth m 1) op (keyword (nth m 2)) lsuf (nth m 3)
                fixed (when (seq sign)
                        (let [ty (if (empty? lsuf) ty-int ty-long)]
                          (if (= sign "u") (t/to-unsigned ty) ty)))
                a (if fixed (new-cast a fixed) a)
                b (if fixed (new-cast b fixed) b)
                widen (fn [e] (new-cast e (if (t/unsigned? (:type e)) ty-ulong ty-long)))
                rty (t/strip (:base (:type r)))]
            (when-not (and (t/integer? (:type a)) (t/integer? (:type b)) (t/ptr? (:type r)) (t/integer? rty))
              (err "invalid arguments to overflow builtin"))
            {:op :overflow :type ty-bool :kind op
             :lhs (widen a) :rhs (widen b) :ptr r :rtype rty
             :lsigned (not (t/unsigned? (:type a))) :rsigned (not (t/unsigned? (:type b)))}))
      ;; otherwise: treat __builtin_foo as call to foo
      (let [real (subs nm 10)
            e (find-ident real)]
        (cond
          (:var e) (var-node (:var e))
          :else (var-node (declare-implicit-func! real)))))))

;; ---------------------------------------------------------------------------
;; Statements

(declare vla-exit-stmts vla-type? vla-inner-type)

(defn user-label [nm]
  (or (@*labels* nm)
      (let [l (new-label)] (vswap! *labels* assoc nm l) l)))

(defn expr-stmt-node [e] {:op :expr-stmt :expr e})

(defn stmt []
  (let [tk (tok) s (:text tk)]
    (cond
      (and (= :ident (:kind tk)) (is-tok? (peek-tok 1) ":")
           (not (contains? keywords s)))
      (do (advance!) (advance!)
          (skip-attributes!)
          (let [lbl (user-label s)]
            (vswap! *label-live* assoc lbl @*vla-blocks*)
            (if (is? "}")
              {:op :label :label lbl :body {:op :block :body []}}
              {:op :label :label lbl :body (stmt)})))

      (= :ident (:kind tk))
      (case s
        "return"
        (do (advance!)
            (if (consume! ";")
              {:op :return}
              (let [e (expr)
                    rt (:ret (:type *cur-fn*))]
                (expect! ";")
                {:op :return :expr (if (or (t/struct? rt) (t/void? rt)) e (new-cast e rt))})))
        "if"
        (do (advance!) (expect! "(")
            (let [c (check-scalar (expr))]
              (expect! ")")
              (let [then (stmt)
                    els (when (consume! "else") (stmt))]
                {:op :if :cond c :then then :els els})))
        "switch"
        (do (advance!) (expect! "(")
            (let [c (expr)
                  _ (when-not (t/integer? (:type c)) (err "switch quantity not an integer"))
                  c (new-cast c (t/promote (:type c)))]
              (expect! ")")
              (let [sw (volatile! {:cases [] :default nil :type (:type c)})
                    brk (new-label)
                    body (binding [*switch* sw *brk* brk *brk-depth* (count @*vla-blocks*)] (stmt))]
                {:op :switch :cond c :body body :cases (:cases @sw) :default (:default @sw) :brk brk})))
        "case"
        (do (advance!)
            (when-not *switch* (err "case label not within a switch"))
            (let [ty (:type @*switch*)
                  lo (wrap (const-expr) ty)
                  hi (if (consume! "...") (wrap (const-expr) ty) lo)
                  lbl (new-label)]
              (expect! ":")
              (vswap! *switch* update :cases conj {:lo lo :hi hi :label lbl})
              (if (is? "}")
                {:op :label :label lbl :body {:op :block :body []}}
                {:op :label :label lbl :body (stmt)})))
        "default"
        (do (advance!) (expect! ":")
            (when-not *switch* (err "default label not within a switch"))
            (let [lbl (new-label)]
              (vswap! *switch* assoc :default lbl)
              (if (is? "}")
                {:op :label :label lbl :body {:op :block :body []}}
                {:op :label :label lbl :body (stmt)})))
        "for"
        (do (advance!) (expect! "(")
            (enter-scope!)
            (let [init (cond (consume! ";") nil
                             (typename?) {:op :block :body (declaration)}
                             :else (let [e (expr)] (expect! ";") (expr-stmt-node e)))
                  c (when-not (is? ";") (check-scalar (expr)))
                  _ (expect! ";")
                  inc (when-not (is? ")") (expr))
                  _ (expect! ")")
                  brk (new-label) cont (new-label)
                  body (binding [*brk* brk *cont* cont *brk-depth* (count @*vla-blocks*) *cont-depth* (count @*vla-blocks*)] (stmt))]
              (leave-scope!)
              {:op :for :init init :cond c :inc inc :body body :brk brk :cont cont}))
        "while"
        (do (advance!) (expect! "(")
            (let [c (check-scalar (expr))]
              (expect! ")")
              (let [brk (new-label) cont (new-label)
                    body (binding [*brk* brk *cont* cont *brk-depth* (count @*vla-blocks*) *cont-depth* (count @*vla-blocks*)] (stmt))]
                {:op :for :cond c :body body :brk brk :cont cont})))
        "do"
        (do (advance!)
            (let [brk (new-label) cont (new-label)
                  body (binding [*brk* brk *cont* cont *brk-depth* (count @*vla-blocks*) *cont-depth* (count @*vla-blocks*)] (stmt))]
              (when-not (consume! "while") (err "expected 'while'"))
              (expect! "(")
              (let [c (check-scalar (expr))]
                (expect! ")") (expect! ";")
                {:op :do :cond c :body body :brk brk :cont cont})))
        "goto"
        (do (advance!)
            (if (consume! "*")
              (let [e (expr)] (expect! ";") {:op :goto-ptr :expr e})
              (let [nm (:text (tok))
                    lbl (user-label nm)
                    a (atom nil)]
                (advance!) (expect! ";")
                (vswap! *gotos* conj {:label lbl :live @*vla-blocks* :restore a})
                {:op :goto :label lbl :restore a})))
        "break"
        (do (advance!) (expect! ";")
            (when-not *brk* (err "break statement not within loop or switch"))
            {:op :block :body (conj (vla-exit-stmts *brk-depth*) {:op :goto :label *brk*})})
        "continue"
        (do (advance!) (expect! ";")
            (when-not *cont* (err "continue statement not within a loop"))
            {:op :block :body (conj (vla-exit-stmts *cont-depth*) {:op :goto :label *cont*})})
        ("asm" "__asm__" "__asm")
        (do (advance!)
            (loop [] (when (#{"volatile" "__volatile__" "goto"} (:text (tok))) (advance!) (recur)))
            (skip-parens!) (expect! ";") {:op :block :body []})
        (let [e (expr)] (expect! ";") (expr-stmt-node e)))

      (is? "{") (do (advance!) (compound-stmt))
      (is? ";") (do (advance!) {:op :block :body []})
      :else (let [e (expr)] (expect! ";") (expr-stmt-node e)))))

(declare declare-function! declare-gvar! parse-gvar-init)

(defn declaration
  "Parses a block-scope declaration (after determining it starts with a typename).
   Returns vector of statement nodes."
  []
  (let [[base attr] (declspec)]
    (if (consume! ";")
      []
      (loop [out [] first? true]
        (if (consume! ";")
          out
          (do
            (when-not first? (expect! ","))
            (let [{:keys [type name name-tok attrs]} (declarator base)
                  attr (if (:aligned attrs) (assoc attr :align (max (:aligned attrs) (get attr :align 0))) attr)]
              (when-not name (err "declarator name expected"))
              (cond
                (:typedef attr)
                (let [[ty st] (if (vla-type? type) (vla-inner-type type) [type []])]
                  (push-ident! name {:typedef (if (:align attr) (assoc ty :xalign (:align attr)) ty)})
                  (recur (into out st) false))

                (t/func? type)
                (let [g (declare-function! name type attr)]
                  (push-ident! name {:var g})
                  (recur out false))

                (:extern attr)
                (let [g (declare-gvar! name type (assoc attr :no-define true))]
                  (push-ident! name {:var g})
                  (recur out false))

                (:static attr)
                (let [label (str name "." (next-id!))
                      g0 {:name name :label label :type type :global true :static true :defined true}
                      _ (push-ident! name {:var g0})
                      g (if (consume! "=")
                          (let [[init ty] (parse-initializer type)]
                            (assoc g0 :type ty :init (gvar-init-data init ty)))
                          g0)]
                  (add-global! (cond-> g (:align attr) (assoc :align (:align attr))))
                  (push-ident! name {:var g})
                  (recur out false))

                (runtime-sized? type)
                ;; VLA: compute sizes at runtime
                (let [[vty size-stmts] (vla-inner-type type)
                      sizes (atom (mapv :expr size-stmts))
                      v (new-lvar! name (ptr-to (:base vty)))
                      ;; v holds pointer to storage; type for expressions is the vla
                      v2 (assoc v :type vty :vla-ptr true)]
                  (push-ident! name {:var v2})
                  (vswap! *locals* (fn [ls] (mapv #(if (= (:id %) (:id v)) v2 %) ls)))
                  (let [alloc {:op :vla-alloc :type ty-void :var v2 :size (vla-size-expr vty)}
                        slot (new-temp! ty-ulong)
                        save [(expr-stmt-node {:op :save-sp :type ty-void :var slot})]]
                    (vswap! *vla-blocks* conj slot)
                    (recur (conj (into (into out save) (map expr-stmt-node @sizes)) (expr-stmt-node alloc)) false)))

                (vla-type? type)
                ;; pointer to VLA: fix the dimensions now
                (let [[ty st] (vla-inner-type type)
                      v (new-lvar! name ty)]
                  (if (consume! "=")
                    (let [[init _] (parse-initializer ty)]
                      (recur (conj (into out st) (expr-stmt-node (lvar-initializer v init))) false))
                    (recur (into out st) false)))

                :else
                (let [v (new-lvar! name type)
                      v (if (:align attr)
                          (let [v2 (assoc v :align (:align attr))]
                            (vswap! *locals* (fn [ls] (mapv #(if (= (:id %) (:id v)) v2 %) ls)))
                            (push-ident! name {:var v2})
                            v2)
                          v)]
                  (when (t/void? type) (err "variable has incomplete type 'void'"))
                  (if (consume! "=")
                    (let [[init ty] (parse-initializer type)
                          v2 (assoc v :type ty)]
                      (when (not= ty type)
                        (vswap! *locals* (fn [ls] (mapv #(if (= (:id %) (:id v)) v2 %) ls)))
                        (push-ident! name {:var v2}))
                      (recur (conj out (expr-stmt-node (lvar-initializer v2 init))) false))
                    (recur out false)))))))))))

(defn restore-sp-stmt [slot] (expr-stmt-node {:op :restore-sp :type ty-void :var slot}))

(defn vla-exit-stmts
  "Statements releasing the space of live VLAs beyond the first depth ones."
  [depth]
  (let [live @*vla-blocks*]
    (if (> (count live) depth) [(restore-sp-stmt (nth live depth))] [])))

(defn compound-stmt
  "After '{'."
  []
  (enter-scope!)
  (let [n0 (count @*vla-blocks*)
        saved-labels (volatile! {})
        items (loop [acc []]
                (cond
                  (consume! "}") acc
                  ;; GNU local labels: fresh asm labels for this block
                  (is? "__label__")
                  (do (advance!)
                      (loop []
                        (let [nm (:text (tok))]
                          (advance!)
                          (when-not (contains? @saved-labels nm)
                            (vswap! saved-labels assoc nm (get @*labels* nm ::none)))
                          (vswap! *labels* assoc nm (new-label))
                          (when (consume! ",") (recur))))
                      (expect! ";")
                      (recur acc))
                  (is? "__extension__") (do (advance!) (recur acc))
                  (is? "_Static_assert")
                  (do (advance!) (expect! "(") (const-expr)
                      (when (consume! ",") (advance!)) (expect! ")") (expect! ";") (recur acc))
                  (and (typename?) (not (is-tok? (peek-tok 1) ":")))
                  (recur (into acc (declaration)))
                  :else (recur (conj acc (stmt)))))
        live @*vla-blocks*
        _ (vswap! *vla-blocks* #(subvec % 0 n0))
        _ (doseq [[nm old] @saved-labels]
            (if (= old ::none) (vswap! *labels* dissoc nm) (vswap! *labels* assoc nm old)))
        items (if (> (count live) n0) (conj items (restore-sp-stmt (nth live n0))) items)]
    (leave-scope!)
    {:op :block :body items}))

;; ---------------------------------------------------------------------------
;; Top level

(defn declare-function! [name ty attr]
  (let [old (@*globals* name)
        ty (if (and old (:proto (:type old)) (not (:proto ty))) (:type old) ty)
        g (merge old {:name name :label name :type ty :global true :func true}
                 (when (:static attr) {:static true}))]
    (add-global! g)))

(defn declare-gvar! [name ty attr]
  (let [old (@*globals* name)
        ty (if (and old (= :array (:kind ty)) (neg? (:len ty))) (:type old) ty)
        g (merge {:name name :label name :global true}
                 old
                 {:type ty}
                 (when (:static attr) {:static true})
                 (when (:align attr) {:align (:align attr)})
                 (when-not (:no-define attr) {:defined true}))]
    (add-global! g)))

(defn vla-type? [ty]
  (case (:kind ty)
    :vla true
    (:array :ptr) (vla-type? (:base ty))
    false))

(defn vla-inner-type
  "Fix every VLA dimension in ty: length expressions are evaluated once, in
   order (outermost first), then sizes computed. Returns [type stmts]."
  [ty]
  (let [lens (atom [])
        sizes (atom [])
        walk1 (fn walk1 [ty]
                (case (:kind ty)
                  :vla (if (:size-var ty)
                         ty
                         (let [lv (when (:len-expr ty) (new-temp! ty-ulong))]
                         (when lv
                           (swap! lens conj (expr-stmt-node (new-assign (var-node lv) (new-cast (:len-expr ty) ty-ulong)))))
                         (assoc ty :len-var lv :base (walk1 (:base ty)))))
                  (:array :ptr) (assoc ty :base (walk1 (:base ty)))
                  ty))
        walk2 (fn walk2 [ty]
                (case (:kind ty)
                  :vla (if (:size-var ty) ty
                       (let [inner (walk2 (:base ty))
                             sv (new-temp! ty-ulong)
                             len (if (:len-var ty) (var-node (:len-var ty)) (num-node 0 ty-ulong))]
                         (swap! sizes conj (expr-stmt-node (new-assign (var-node sv)
                                                                       {:op :mul :type ty-ulong :lhs len :rhs (vla-size-expr inner)})))
                         {:kind :vla :base inner :size-var sv}))
                  :array (assoc ty :base (walk2 (:base ty)))
                  :ptr (ptr-to (walk2 (:base ty)))
                  ty))
        t2 (walk2 (walk1 ty))]
    [t2 (into @lens @sizes)]))

(defn param-vla-type
  "Declared parameter type -> [adjusted type, entry statements]."
  [dt]
  (case (:kind dt)
    :vla (let [st0 (if (:len-expr dt) [(expr-stmt-node (new-cast (:len-expr dt) ty-void))] [])
               [inner st] (vla-inner-type (:base dt))]
           [(ptr-to inner) (into st0 st)])
    :array (let [[inner st] (vla-inner-type (:base dt))] [(ptr-to inner) st])
    :ptr (let [[inner st] (vla-inner-type (:base dt))] [(ptr-to inner) st])
    [dt []]))

(defn function-def [ty name attr]
  (let [g (declare-function! name ty attr)
        g (add-global! (assoc g :defined true))]
    (push-ident! name {:var g})
    (binding [*locals* (volatile! [])
              *vla-blocks* (volatile! [])
              *gotos* (volatile! [])
              *label-live* (volatile! {})
              *cur-fn* {:name name :type ty}
              *labels* (volatile! {})]
      (enter-scope!)
      (let [;; K&R parameter declarations
            knr-types (when (:knr-names ty)
                        (loop [m {}]
                          (if (is? "{")
                            m
                            (let [[base _] (declspec)]
                              (let [m (loop [m m]
                                        (let [{dty :type dname :name} (declarator base)
                                              m (assoc m dname (adjust-param-type dty))]
                                          (if (consume! ",") (recur m) (do (expect! ";") m))))]
                                (recur m))))))
            [ty params]
            (if (:knr-names ty)
              (let [names (:knr-names ty)
                    ptys (mapv #(get knr-types % ty-int) names)
                    ;; promoted types as passed
                    passed (mapv #(cond (= :float (:kind %)) ty-double
                                        (t/integer? %) (t/promote %)
                                        :else %) ptys)
                    pvars (mapv (fn [nm pty] (new-lvar! nm pty)) names passed)
                    ty (assoc ty :params passed :knr-real ptys)]
                [ty pvars])
              (if-let [pvs (:param-vars ty)]
                (do (vswap! *locals* into (:param-temps ty))
                    (let [pvars (mapv (fn [pv]
                                        (vswap! *locals* conj pv)
                                        (when (:name pv) (push-ident! (:name pv) {:var pv}))
                                        pv)
                                      pvs)]
                      [ty pvars]))
                (let [names (:param-names ty)
                      pvars (vec (map-indexed (fn [i pty] (new-lvar! (nth names i nil) pty)) (:params ty)))]
                  [ty pvars])))
            ;; VLA parameters: evaluate size expressions on entry, in order
            [params vla-stmts]
            (if-let [dts (:param-decl-types ty)]
              (reduce (fn [[plist stmts] [pv dt]]
                        (let [[nt st] (param-vla-type dt)]
                          (if (seq st)
                            (let [pv2 (assoc pv :type nt)]
                              (vswap! *locals* (fn [ls] (mapv #(if (= (:id %) (:id pv)) pv2 %) ls)))
                              (when (:name pv) (push-ident! (:name pv) {:var pv2}))
                              [(conj plist pv2) (into stmts st)])
                            [(conj plist pv) stmts])))
                      [[] []] (map vector params dts))
              [params []])
            ;; K&R params declared float/char/short: convert from promoted
            conv-stmts (when (:knr-real ty)
                         (vec (keep (fn [[pv real]]
                                      (when (not= (:kind real) (:kind (:type pv)))
                                        (let [nv (new-lvar! (:name pv) real)]
                                          (expr-stmt-node (new-assign (var-node nv) (var-node pv))))))
                                    (map vector params (:knr-real ty)))))
            va-area (when (:variadic ty) (new-temp! (array-of ty-char 176)))
            _ (expect! "{")
            body (binding [*cur-fn* {:name name :type ty}] (compound-stmt))
            body (if (seq conv-stmts) {:op :block :body (into conv-stmts [body])} body)
            body (if (seq vla-stmts) {:op :block :body (conj vla-stmts body)} body)
            _ (doseq [{:keys [label live restore]} @*gotos*]
                (when-let [ll (get @*label-live* label)]
                  (let [k (count (take-while true? (map #(= (:id %1) (:id %2)) live ll)))]
                    (when (> (count live) k)
                      (reset! restore (nth live k))))))]
        (leave-scope!)
        (vswap! *functions* conj
                {:name name :type ty :params params :body body
                 :locals @*locals* :static (:static (@*globals* name)) :va-area va-area})))))

(defn global-declaration []
  (let [[base attr] (declspec)]
    (if (consume! ";")
      nil
      (loop [first? true]
        (when-not first? (expect! ","))
        (let [{:keys [type name attrs]} (declarator base)
              attr (if (:aligned attrs) (assoc attr :align (max (:aligned attrs) (get attr :align 0))) attr)]
          (when-not name (err "declarator name expected"))
          (cond
            (:typedef attr)
            (do (push-ident! name {:typedef (if (:align attr) (assoc type :xalign (:align attr)) type)})
                (when-not (consume! ";") (recur false)))

            (t/func? type)
            (if (and first? (or (is? "{") (and (:knr-names type) (typename?))))
              (function-def type name attr)
              (let [g (declare-function! name type attr)]
                (push-ident! name {:var g})
                (when-not (consume! ";") (recur false))))

            :else
            (let [g (declare-gvar! name type (if (:extern attr) (assoc attr :no-define true) attr))]
              (push-ident! name {:var g})
              (if (consume! "=")
                (let [[init ty] (parse-initializer (:type g))
                      g (declare-gvar! name ty (dissoc attr :no-define :extern))
                      g (add-global! (assoc g :init (gvar-init-data init ty)))]
                  (push-ident! name {:var g}))
                nil)
              (when-not (consume! ";") (recur false)))))))))

(def keyword-aliases
  {"__attribute" "__attribute__" "__const__" "const" "__const" "const"
   "__volatile__" "volatile" "__volatile" "volatile" "__signed__" "signed" "__signed" "signed"
   "__restrict__" "restrict" "__restrict" "restrict" "__inline__" "inline" "__inline" "inline"
   "__typeof__" "typeof" "__typeof" "typeof" "__alignof__" "_Alignof" "__alignof" "_Alignof"
   "__asm__" "asm" "__asm" "asm" "_Noreturn" "_Noreturn"})

(defn normalize-tokens [toks]
  (mapv (fn [t] (if-let [a (and (= :ident (:kind t)) (keyword-aliases (:text t)))]
                  (assoc t :text a)
                  t))
        toks))

(defn parse-program [toks]
  (binding [*toks* (normalize-tokens toks)
            *pos* (volatile! 0)
            *scope* (volatile! (list {:vars {} :tags {}}))
            *globals* (volatile! {})
            *global-order* (volatile! [])
            *functions* (volatile! [])
            *strlits* (volatile! {})
            *counter* (volatile! 0)]
    (loop []
      (when-not (= :eof (:kind (tok)))
        (cond
          (consume! ";") nil
          (is? "_Static_assert") (do (advance!) (skip-parens!) (expect! ";"))
          (or (is? "asm") (is? "__asm__")) (do (advance!) (skip-parens!) (expect! ";"))
          :else (global-declaration))
        (recur)))
    {:globals (mapv @*globals* @*global-order*)
     :functions @*functions*}))
