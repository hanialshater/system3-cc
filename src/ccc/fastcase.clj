(ns ccc.fastcase
  "Drop-in replacements for a few clojure.core macros, so that the compiler's
   own code loads faster and stays JIT-compilable. Same expansions, same
   semantics.

   case: two things make clojure.core/case code large, and a function over
   HotSpot's 8000-byte limit is never JIT-compiled:
   - on keywords, strings, chars ... it hashes and, when some shift/mask makes
     the hashes distinct, emits a dense tableswitch of up to 8192 entries
     (4 bytes each), mostly default jumps;
   - a grouped clause `(t1 t2 ... tn) expr` emits expr n times.
   This macro emits the same case* form with a sparse lookupswitch (what
   clojure.core/case itself emits when no mask fits), and for grouped clauses
   first maps the value to the clause's index, then dispatches on that index, so
   each result expression is emitted once. The tests, the result expressions and
   the default are the same, and the value is evaluated once; only the switch
   encoding differs. Hash collisions and compact int ranges use clojure.core/case.

   let, fn, defn, defn-, if-let, when-let, and the core macros whose expansions
   use them (and, or, loop, doseq, for, binding, cond->, some->, dotimes,
   letfn, doto): the expansions of clojure.core's (core's own macro functions
   where there is more than destructuring), minus the clojure.spec validation
   that the Clojure compiler runs on every expansion of clojure.core/let, fn,
   defn, if-let and when-let; that validation was a quarter of the time spent
   loading the compiler. Where core's expansion uses one of those, the
   equivalent macro of this namespace is used instead."
  (:refer-clojure :exclude [case let fn defn defn- if-let when-let and or loop doseq for binding
                            cond-> some-> dotimes letfn doto]))

(defmacro let
  "clojure.core/let without the spec check."
  [bindings & body]
  (if (clojure.core/and (vector? bindings) (even? (count bindings)))
    `(let* ~(destructure bindings) ~@body)
    `(clojure.core/let ~bindings ~@body)))

(defmacro and
  "clojure.core/and, with let* (its binding is a plain symbol)."
  ([] true)
  ([x] x)
  ([x & next]
   `(let* [and# ~x]
      (if and# (and ~@next) and#))))

(defmacro or
  "clojure.core/or, with let* (its binding is a plain symbol)."
  ([] nil)
  ([x] x)
  ([x & next]
   `(let* [or# ~x]
      (if or# or# (or ~@next)))))

(def ^:private equivalents
  '{clojure.core/let ccc.fastcase/let, clojure.core/fn ccc.fastcase/fn, clojure.core/loop ccc.fastcase/loop,
    clojure.core/when-let ccc.fastcase/when-let, clojure.core/if-let ccc.fastcase/if-let,
    clojure.core/and ccc.fastcase/and, clojure.core/or ccc.fastcase/or})

(clojure.core/defn- despec
  "form with clojure.core macros that this namespace replaces, at the head of
   lists, replaced by their equivalents (in lists and vectors; not inside quote
   or case*)."
  [form]
  (cond
    (clojure.core/and (seq? form) (seq form))
    (let* [h (first form)]
      (if (clojure.core/or (= h 'quote) (= h 'case*))
        form
        (let* [r (apply list (get equivalents h h) (map despec (rest form)))]
          (if (meta form) (with-meta r (meta form)) r))))
    (vector? form)
    (let* [r (mapv despec form)]
      (if (meta form) (with-meta r (meta form)) r))
    :else form))

(defmacro fn
  "clojure.core/fn without the spec check."
  [& sigs]
  (despec (apply @#'clojure.core/fn &form &env sigs)))

(defmacro loop
  "clojure.core/loop without the spec check."
  [& args]
  (despec (apply @#'clojure.core/loop &form &env args)))

(defmacro doseq [& args] (despec (apply @#'clojure.core/doseq &form &env args)))
(defmacro for [& args] (despec (apply @#'clojure.core/for &form &env args)))
(defmacro binding [& args] (despec (apply @#'clojure.core/binding &form &env args)))
(defmacro cond-> [& args] (despec (apply @#'clojure.core/cond-> &form &env args)))
(defmacro some-> [& args] (despec (apply @#'clojure.core/some-> &form &env args)))
(defmacro dotimes [& args] (despec (apply @#'clojure.core/dotimes &form &env args)))
(defmacro letfn [& args] (despec (apply @#'clojure.core/letfn &form &env args)))
(defmacro doto [& args] (despec (apply @#'clojure.core/doto &form &env args)))

(defmacro defn
  "clojure.core/defn without the spec check."
  [& args]
  (clojure.core/let [d (apply @#'clojure.core/defn &form &env args)]
    (if (and (seq? d) (= 'def (first d)) (= 3 (count d))
             (seq? (nth d 2)) (= 'clojure.core/fn (first (nth d 2))))
      (clojure.core/let [f (nth d 2)]
        (list 'def (second d) (with-meta (cons `ccc.fastcase/fn (rest f)) (meta f))))
      d)))

(defmacro defn-
  "clojure.core/defn- without the spec check."
  [name & decls]
  (list* `defn (with-meta name (assoc (meta name) :private true)) decls))

(defmacro when-let
  "clojure.core/when-let without the spec check."
  [bindings & body]
  (if (and (vector? bindings) (= 2 (count bindings)))
    (clojure.core/let [form (bindings 0) tst (bindings 1)]
      `(let [temp# ~tst]
         (when temp#
           (let [~form temp#]
             ~@body))))
    `(clojure.core/when-let ~bindings ~@body)))

(defmacro if-let
  "clojure.core/if-let without the spec check."
  ([bindings then]
   `(if-let ~bindings ~then nil))
  ([bindings then else & oldform]
   (if (and (vector? bindings) (= 2 (count bindings)) (nil? oldform))
     (clojure.core/let [form (bindings 0) tst (bindings 1)]
       `(let [temp# ~tst]
          (if temp#
            (let [~form temp#]
              ~then)
            ~else)))
     `(clojure.core/if-let ~bindings ~then ~else ~@oldform))))

(defn- flat-case
  "case over single test constants (no groups)."
  [ge default tests+thens]
  (let [tests (map first tests+thens)
        ints? (every? #(and (integer? %) (<= Integer/MIN_VALUE % Integer/MAX_VALUE)) tests)
        plain `(clojure.core/case ~ge ~@(mapcat identity tests+thens) ~default)]
    (if ints?
      (let [lo (apply min tests) hi (apply max tests)]
        (if (<= (- hi lo) (max 16 (* 2 (count tests))))
          plain
          (let [imap (into (sorted-map) (map (fn [[t then]] [(int t) [(int t) then]]) tests+thens))]
            `(case* ~ge 0 0 ~default ~imap :sparse :int))))
      (let [hashes (map #(clojure.lang.Util/hash %) tests)]
        (if (not (apply distinct? hashes))
          plain
          (let [imap (into (sorted-map) (map (fn [[t then] h] [h [t then]]) tests+thens hashes))
                mode (if (every? keyword? tests) :hash-identity :hash-equiv)]
            `(case* ~ge 0 0 ~default ~imap :sparse ~mode nil)))))))

(defmacro case
  [e & clauses]
  (let [ge (with-meta (gensym "case") {:tag Object})
        default (if (odd? (count clauses))
                  (last clauses)
                  `(throw (IllegalArgumentException. (str "No matching clause: " ~ge))))
        pairs (partition 2 clauses)
        tests (mapcat (fn [[test _]] (if (seq? test) test [test])) pairs)]
    (cond
      (or (< (count clauses) 2) (not (apply distinct? tests)))
      `(clojure.core/case ~e ~@clauses)

      (some (fn [[test _]] (and (seq? test) (> (count test) 1))) pairs)
      ;; groups: value -> clause index, then index -> result expression
      (let [gi (gensym "idx")
            idx-clauses (mapcat (fn [[test _] i] (map (fn [t] [t i]) (if (seq? test) test [test])))
                                pairs (range))]
        `(let* [~ge ~e
                ~gi ~(flat-case ge -1 idx-clauses)]
           (clojure.core/case (int ~gi)
             ~@(mapcat (fn [[_ then] i] [i then]) pairs (range))
             ~default)))

      :else
      `(let* [~ge ~e]
         ~(flat-case ge default (map (fn [[test then]] [(if (seq? test) (first test) test) then]) pairs))))))
