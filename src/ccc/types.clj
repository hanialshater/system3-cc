(ns ccc.types
  "C type representation. Types are plain maps with :kind.
   Struct/union bodies live in a per-compilation registry keyed by :id so that
   incomplete types can be completed later."
  (:refer-clojure :exclude [integer?]))

(def ^:dynamic *structs* nil) ; atom: id -> {:members [...] :size n :align n :complete bool}

(def ty-void   {:kind :void :size 1 :align 1})
(def ty-bool   {:kind :bool :size 1 :align 1 :unsigned true})
(def ty-char   {:kind :char :size 1 :align 1})
(def ty-uchar  {:kind :char :size 1 :align 1 :unsigned true})
(def ty-short  {:kind :short :size 2 :align 2})
(def ty-ushort {:kind :short :size 2 :align 2 :unsigned true})
(def ty-int    {:kind :int :size 4 :align 4})
(def ty-uint   {:kind :int :size 4 :align 4 :unsigned true})
(def ty-long   {:kind :long :size 8 :align 8})
(def ty-ulong  {:kind :long :size 8 :align 8 :unsigned true})
(def ty-float  {:kind :float :size 4 :align 4})
(def ty-double {:kind :double :size 8 :align 8})
(def ty-ldouble {:kind :ldouble :size 16 :align 16})

(defn ptr-to [base] {:kind :ptr :size 8 :align 8 :base base})

(declare type-size type-align type-align*)

(defn array-of [base len]
  {:kind :array :base base :len len})

(defn vla-of [base len-var]
  {:kind :vla :base base :len-var len-var})

(defn func-type [ret params variadic proto]
  {:kind :func :ret ret :params params :variadic variadic :proto proto})

(defn struct-info [t] (get @*structs* (:id t)))

(defn type-size [t]
  (case (:kind t)
    :array (if (and (:len t) (>= (:len t) 0)) (* (:len t) (type-size (:base t))) 0)
    (:struct :union) (or (:size (struct-info t)) 0)
    :func 1
    :vla 8
    (:size t)))

(defn type-align [t]
  (if-let [x (:xalign t)]
    x
    (type-align* t)))

(defn type-align* [t]
  (case (:kind t)
    :array (type-align (:base t))
    (:struct :union) (or (:align (struct-info t)) 1)
    :func 1
    :vla 8
    (:align t)))

(defn integer? [t] (contains? #{:bool :char :short :int :long :enum} (:kind t)))
(defn flonum? [t] (contains? #{:float :double :ldouble} (:kind t)))
(defn ldouble? [t] (= :ldouble (:kind t)))
(defn numeric? [t] (or (integer? t) (flonum? t)))
(defn ptr? [t] (= :ptr (:kind t)))
(defn array? [t] (contains? #{:array :vla} (:kind t)))
(defn ptr-like? [t] (contains? #{:ptr :array :vla} (:kind t)))
(defn func? [t] (= :func (:kind t)))
(defn void? [t] (= :void (:kind t)))
(defn struct? [t] (contains? #{:struct :union} (:kind t)))
(defn scalar? [t] (or (numeric? t) (ptr? t)))
(defn unsigned? [t] (boolean (:unsigned t)))

(defn rank [t]
  (case (:kind t) :bool 0 :char 1 :short 2 :int 3 :long 4 3))

(defn strip
  "Remove qualifier-ish annotations that don't affect representation."
  [t]
  (dissoc t :const :volatile :name-tok))

(defn promote
  "Integer promotion."
  [t]
  (if (and (integer? t) (< (rank t) 3)) ty-int (strip t)))

(defn to-unsigned [t] (assoc (strip t) :unsigned true))

(defn common-type
  "Usual arithmetic conversions for two numeric types."
  [a b]
  (cond
    (or (= :ldouble (:kind a)) (= :ldouble (:kind b))) ty-ldouble
    (or (= :double (:kind a)) (= :double (:kind b))) ty-double
    (or (= :float (:kind a)) (= :float (:kind b))) ty-float
    :else
    (let [a (promote a) b (promote b)
          ra (rank a) rb (rank b)
          ua (unsigned? a) ub (unsigned? b)]
      (cond
        (= ua ub) (if (>= ra rb) a b)
        :else
        (let [[u s] (if ua [a b] [b a])]
          (cond
            (>= (rank u) (rank s)) u
            (> (type-size s) (type-size u)) s
            :else (to-unsigned s)))))))

(defn same-type?
  "Structural compatibility, loosely."
  [a b]
  (and (= (:kind a) (:kind b))
       (case (:kind a)
         (:ptr :array) (same-type? (:base a) (:base b))
         (:struct :union) (= (:id a) (:id b))
         (:char :short :int :long) (= (unsigned? a) (unsigned? b))
         :func (and (same-type? (:ret a) (:ret b)))
         true)))

(defn compatible?
  "gcc's __builtin_types_compatible_p: top-level qualifiers are ignored,
   nested ones must match; char, signed char and unsigned char differ, as do
   long and long long."
  [a b top]
  (and (= (:kind a) (:kind b))
       (or top (and (= (boolean (:const a)) (boolean (:const b)))
                    (= (boolean (:volatile a)) (boolean (:volatile b)))))
       (case (:kind a)
         :ptr (compatible? (:base a) (:base b) false)
         :array (and (compatible? (:base a) (:base b) false)
                     (or (neg? (or (:len a) -1)) (neg? (or (:len b) -1)) (= (:len a) (:len b))))
         (:struct :union) (= (:id a) (:id b))
         :char (and (= (unsigned? a) (unsigned? b)) (= (boolean (:schar a)) (boolean (:schar b))))
         :long (and (= (unsigned? a) (unsigned? b)) (= (boolean (:llong a)) (boolean (:llong b))))
         (:short :int :bool) (= (unsigned? a) (unsigned? b))
         :func (compatible? (:ret a) (:ret b) false)
         true)))

(defn type-str [t]
  (case (:kind t)
    :ptr (str (type-str (:base t)) "*")
    :array (str (type-str (:base t)) "[" (:len t) "]")
    :func (str (type-str (:ret t)) "()")
    (:struct :union) (str (name (:kind t)) " " (or (:tag t) "<anon>"))
    (str (when (unsigned? t) "unsigned ") (name (:kind t)))))
