(ns ccc.main
  "Entry point. Loads the compiler's namespaces on parallel threads, each one
   once and after the namespaces it requires, then runs ccc.driver; the driver
   starts preprocessing while the parser and the code generator still load."
  (:gen-class))

(def ^:private big-stack (* 1024 1024 1024))

(defn- load-after
  "Load namespace ns-sym on a new thread once every promise in deps is
   delivered. Returns a promise of ns-sym, or of the Throwable loading threw."
  [deps ns-sym]
  (let [p (promise)
        th (Thread. nil
                    (fn []
                      (deliver p (try (doseq [d deps]
                                        (let [v @d] (when (instance? Throwable v) (throw v))))
                                      ;; direct linking: calls between the compiler's own
                                      ;; functions (never redefined) compile to static calls
                                      (binding [*compiler-options* (assoc *compiler-options* :direct-linking true)]
                                        (require ns-sym))
                                      (when (System/getenv "CCC_TRACE")
                                        (binding [*out* *err*]
                                          (println (str (- (System/currentTimeMillis)
                                                           (.getStartTime (java.lang.management.ManagementFactory/getRuntimeMXBean)))
                                                        "ms loaded " ns-sym))))
                                      ns-sym
                                      (catch Throwable e e))))
                    (str "load-" ns-sym) big-stack)]
    (.setDaemon th true)
    (.start th)
    p))

(defn- resolved
  "Promise of the var sym once the promise ns-p is delivered (or its Throwable)."
  [ns-p sym]
  (let [p (promise)]
    (future-call (fn [] (deliver p (let [v @ns-p] (if (instance? Throwable v) v (resolve sym))))))
    p))

(defn- ok! [p] (let [v @p] (when (instance? Throwable v) (throw v)) v))

(defn -main [& args]
  ;; libraries everything shares, loaded before any parallel load
  (require 'ccc.fastcase 'clojure.string 'clojure.java.io 'clojure.set)
  (let [types (load-after [] 'ccc.types)
        lexer (load-after [] 'ccc.lexer)
        preproc (load-after [lexer] 'ccc.preproc)
        codegen (load-after [types] 'ccc.codegen)
        parser (load-after [types lexer] 'ccc.parser)
        driver (load-after [types lexer preproc] 'ccc.driver)
        result (promise)
        th (Thread. nil (fn [] (deliver result
                                        (try (ok! driver)
                                             ((resolve 'ccc.driver/trace) "main")
                                             ((resolve 'ccc.driver/run-main) args
                                              (resolved parser 'ccc.parser/parse-program)
                                              (resolved codegen 'ccc.codegen/generate))
                                             (catch Throwable e
                                               (binding [*out* *err*] (println "fatal:" e))
                                               1))))
                    "ccc-main" big-stack)]
    (.start th)
    (let [code @result]
      (flush)
      (.flush System/err)
      (System/exit code))))
