(ns ccc.fastcase
  "A drop-in clojure.core/case that keeps methods small.

   Two things make clojure.core/case code large, and a function over HotSpot's
   8000-byte limit is never JIT-compiled:
   - on keywords, strings, chars ... it hashes and, when some shift/mask makes
     the hashes distinct, emits a dense tableswitch of up to 8192 entries
     (4 bytes each), mostly default jumps;
   - a grouped clause `(t1 t2 ... tn) expr` emits expr n times.
   This macro emits the same case* form with a sparse lookupswitch (what
   clojure.core/case itself emits when no mask fits), and for grouped clauses
   first maps the value to the clause's index, then dispatches on that index, so
   each result expression is emitted once. The tests, the result expressions and
   the default are the same, and the value is evaluated once; only the switch
   encoding differs. Hash collisions and compact int ranges use clojure.core/case."
  (:refer-clojure :exclude [case]))

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
        `(let [~ge ~e
               ~gi ~(flat-case ge -1 idx-clauses)]
           (clojure.core/case (int ~gi)
             ~@(mapcat (fn [[_ then] i] [i then]) pairs (range))
             ~default)))

      :else
      `(let [~ge ~e]
         ~(flat-case ge default (map (fn [[test then]] [(if (seq? test) (first test) test) then]) pairs))))))
