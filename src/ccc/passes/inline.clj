(ns ccc.passes.inline
  "AST pass (docs/INTERFACES.md step 2): calls of small functions with statement bodies over
   scalar parameters are replaced by statement expressions holding a renamed copy of the
   callee's body (parameters become fresh locals, return e becomes rv = e; goto end).
   Program in, program of the same shape out; the caller's :locals gain the copies' variables
   with fresh ids. Off switch: CCC_NOGINLINE."
  (:require [ccc.types :as t]))

(def ^:private no-inline-ops
  #{:stmt-expr :label-addr :alloca :va-arg :va-start :va-copy :vla-alloc :goto-ptr
    :save-sp :restore-sp :memzero :asm})

(def ^:private skip-keys #{:type :var :member :fn-type})

(def inline-depth 3)

(defn- env-long [k d] (if-let [e (System/getenv k)] (parse-long e) d))

(defn node-count [x]
  (cond (map? x) (if (:op x)
                   (reduce-kv (fn [n k v] (if (contains? skip-keys k) n (+ n (long (node-count v))))) 1 x)
                   0)
        (vector? x) (reduce (fn [n v] (+ n (long (node-count v)))) 0 x)
        :else 0))

(defn- candidate
  "{:f f :size n} when f can be inlined, else nil."
  [f limit]
  (let [ty (:type f) body (:body f) params (:params f) ret (:ret ty) self (:name f)
        bad? (fn bad? [x]
               (cond (map? x) (or (contains? no-inline-ops (:op x))
                                  (and (= :call (:op x)) (= :var (:op (:fn x)))
                                       (let [nm (str (:label (:var (:fn x))))]
                                         (or (= nm self) (re-find #"setjmp|alloca|vfork|longjmp|builtin_frame|return_address|getcontext" nm))))
                                  (some (fn [[k v]] (and (not (contains? skip-keys k)) (bad? v))) x))
                     (vector? x) (some bad? x)
                     :else false))
        scalar-ok? (fn [t] (and (t/scalar? t) (not (t/ldouble? t))))]
    (when (and body (= :block (:op body)) (:proto ty) (not (:variadic ty)) (not (:va-area f)) (not (:knr-real ty))
               (not (:align f)) (not= self "main")
               (or (t/void? ret) (scalar-ok? ret))
               (= (count params) (count (:params ty)))
               (every? #(scalar-ok? (:type %)) params)
               (not-any? #(or (:vla-ptr %) (:align %)) (:locals f))
               (<= (node-count body) limit)
               (not (bad? body)))
      {:f f :size (node-count body)})))

(defn- defined-labels
  "Labels defined (not merely used) inside the statement tree x."
  [x]
  (let [out (volatile! [])]
    (letfn [(walk [x]
              (cond
                (map? x)
                (when-let [op (:op x)]
                  (case op
                    :label (when (string? (:label x)) (vswap! out conj (:label x)))
                    (:for :do) (do (vswap! out conj (:brk x) (:cont x)))
                    :switch (do (vswap! out conj (:brk x))
                                (when (:default x) (vswap! out conj (:default x)))
                                (doseq [c (:cases x)] (vswap! out conj (:label c))))
                    nil)
                  (reduce-kv (fn [_ k v] (when-not (contains? skip-keys k) (when-not (= k :cases) (walk v))) nil) nil x))
                (vector? x) (run! walk x)
                :else nil))]
      (walk x))
    (vec (distinct (filter string? @out)))))

(defn- clone-body
  "Copy of the callee statement x: variables through vmap (id -> new var), labels through lmap,
   return e -> rv = e; goto lend."
  [x vmap lmap rv lend rt]
  (letfn [(rvar [v] (if (map? v) (get vmap (:id v) v) v))
          (lbl [s] (if (string? s) (get lmap s s) s))
          (cl [x]
            (cond
              (map? x)
              (if-let [op (:op x)]
                (if (= op :return)
                  (let [g {:op :goto :label lend}]
                    (if-let [e (:expr x)]
                      {:op :block :body [{:op :expr-stmt :expr {:op :assign :type rt :lhs {:op :var :var rv :type rt} :rhs (cl e)}} g]}
                      g))
                  (persistent!
                   (reduce-kv (fn [acc k v]
                                (assoc! acc k
                                        (case k
                                          (:type :member :fn-type) v
                                          (:var :ret-buf :tmp) (rvar v)
                                          (:label :brk :cont :default) (lbl v)
                                          :cases (mapv (fn [c] (assoc c :label (lbl (:label c)))) v)
                                          (cl v))))
                              (transient x) x)))
                x)
              (vector? x) (mapv cl x)
              :else x))]
    (cl x)))

(defn- same-shape?
  "Argument type ta can feed a parameter of type tp without conversion."
  [ta tp]
  (and (= (:kind ta) (:kind tp)) (= (t/type-size ta) (t/type-size tp))
       (= (boolean (t/unsigned? ta)) (boolean (t/unsigned? tp)))))

(defn- inline-function
  "Returns f with the calls to gcands inlined."
  [f gcands limit cold-limit limit-budget counter-start]
  (let [temps (volatile! []) counter (volatile! 0)
        budget (volatile! (min limit-budget (+ 200 (long (* (env-long "CCC_INLINE_GROWTH" 100000) 0.01 (node-count (:body f)))))))
        label-n (volatile! counter-start)
        new-label (fn [] (str ".L.inl." (vswap! label-n inc)))
        inl-call? (fn inl-call? [x]
                    (cond (map? x) (or (and (= :call (:op x)) (= :var (:op (:fn x)))
                                            (contains? gcands (:label (:var (:fn x)))))
                                       (some (fn [[k v]] (and (not (contains? skip-keys k)) (inl-call? v))) x))
                          (vector? x) (some inl-call? x)
                          :else false))]
    (letfn [(rw [x path depth lp]
              (cond
                (map? x)
                (let [path (if (:scope-id x) (conj path (:scope-id x)) path)
                      lp (or lp (= :for (:op x)) (= :do (:op x)))
                      ntemps (count @temps)
                      nested? (inl-call? (:args x))
                      x (if (:op x)
                          (persistent! (reduce-kv (fn [acc k v] (if (contains? skip-keys k) acc (assoc! acc k (rw v path depth lp))))
                                                  (transient x) x))
                          x)
                      fnode (:fn x)
                      g (and (= :call (:op x)) (= :var (:op fnode)) (not (:local (:var fnode)))
                             (t/func? (:type fnode))
                             (:proto (:fn-type x))
                             (pos? @budget)
                             (< depth inline-depth)
                             (get gcands (:label (:var fnode))))]
                  (if (and g (<= (:size g) (if lp limit cold-limit))
                           (let [cf (:f g)]
                             (and (= (count (:args x)) (count (:params cf)))
                                  (every? (fn [[a p]] (same-shape? (:type a) (t/strip (:type p)))) (map vector (:args x) (:params cf)))
                                  (or (t/void? (:ret (:type cf))) (same-shape? (:type x) (t/strip (:ret (:type cf))))))))
                    (let [cf (:f g) prms (:params cf)
                          uid (vswap! counter inc)
                          mark [:inl (:name f) uid]
                          base (conj path mark)
                          pblk (if (or nested? (> (count @temps) ntemps)) path base)
                          rt (t/strip (:ret (:type cf)))
                          void? (t/void? rt)
                          pids (set (map :id prms))
                          vmap (into {} (map (fn [v]
                                               [(:id v)
                                                (assoc v :id [:ginl (:name f) (vswap! counter inc)]
                                                       :name (str "inl." (:name v))
                                                       :block (if (contains? pids (:id v)) pblk (into base (:block v))))])
                                             (:locals cf)))
                          rv (when-not void?
                               {:name "inl.ret" :type rt :id [:ginl (:name f) (vswap! counter inc)] :local true :block base})
                          lend (new-label)
                          lmap (zipmap (defined-labels (:body cf)) (repeatedly new-label))
                          _ (vswap! temps into (vals vmap))
                          _ (when rv (vswap! temps conj rv))
                          _ (vswap! budget - (:size g))
                          body (clone-body (:body cf) vmap lmap rv lend rt)
                          croot (some (fn [v] (first (:block v))) (:locals cf))
                          body (if croot {:op :block :body [body] :scope-id croot} body)
                          body (rw body base (inc depth) lp)
                          assigns (mapv (fn [p a]
                                          (let [tv (vmap (:id p)) pt (t/strip (:type p))]
                                            {:op :expr-stmt
                                             :expr {:op :assign :type pt :lhs {:op :var :var tv :type pt} :rhs a}}))
                                        prms (:args x))]
                      {:op :stmt-expr :type (if void? t/ty-void (:type x)) :scope-id mark
                       :body {:op :block
                              :body (-> assigns
                                        (conj body)
                                        (conj {:op :label :label lend :body {:op :block :body []}})
                                        (cond-> (not void?)
                                          (conj {:op :expr-stmt :expr {:op :var :var rv :type rt}})))}})
                    x))
                (vector? x) (mapv #(rw % path depth lp) x)
                :else x))]
      (let [root (or (some (fn [v] (first (:block v))) (:locals f)) :root)
            body (rw (:body f) [root] 1 false)]
        [(if (seq @temps)
           (assoc f :body body :locals (into (vec (:locals f)) @temps))
           f)
         @label-n]))))

(defn run
  "program -> program"
  [{:keys [functions] :as prog}]
  (if (or (System/getenv "CCC_NOINLINE") (System/getenv "CCC_NOGINLINE"))
    prog
    (let [limit (env-long "CCC_INLINE_LIMIT" 100)
          budget (env-long "CCC_INLINE_BUDGET" 60000)
          gcands (into {} (keep (fn [f] (when-let [c (candidate f limit)] [(:name f) c])) functions))]
      (if (empty? gcands)
        prog
        (loop [fs (seq functions) out [] n 0]
          (if-not fs
            (assoc prog :functions out)
            (let [[f2 n2] (inline-function (first fs) gcands limit (env-long "CCC_INLINE_COLD" 100) budget n)
                  _ (when (System/getenv "CCC_PROF")
                      (binding [*out* *err*] (println "GROW" (:name f2) (node-count (:body (first fs))) (node-count (:body f2)))))]
              (recur (next fs) (conj out f2) (long n2)))))))))
