(ns ccc.driver
  "Single-file and --batch compilation (started by ccc.main).
   The parser and code generator may still be loading (ccc.main loads them on
   background threads) while the first files are preprocessed; batch files are
   compiled by a small pool of worker threads, and their diagnostics are printed
   in batch order."
  (:refer-clojure :exclude [case let fn defn defn- if-let when-let and or loop doseq for binding cond-> some-> dotimes letfn doto])
  (:require [ccc.fastcase :refer [case let fn defn defn- if-let when-let and or loop doseq for binding cond-> some-> dotimes letfn doto]]
            [ccc.lexer :as lx]
            [ccc.preproc :as pp]
            [ccc.types :as t]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util.concurrent CountDownLatch]
           [java.util.concurrent.atomic AtomicInteger]
           [java.io StringWriter File]))

(def big-stack (* 1024 1024 1024))

(def ^:private trace? (System/getenv "CCC_TRACE"))
(defn trace [& xs]
  (when trace?
    (let [ms (- (System/currentTimeMillis) (.getStartTime (java.lang.management.ManagementFactory/getRuntimeMXBean)))]
      (binding [*out* *err*] (println (apply str ms "ms " (interpose " " xs)))))))

(def ^:private parse-var (atom nil)) ; promise of #'ccc.parser/parse-program (or a Throwable)
(def ^:private gen-var (atom nil))   ; promise of #'ccc.codegen/generate (or a Throwable)

(defn- await-var [a]
  (let [v @@a]
    (if (instance? Throwable v) (throw v) v)))

(defn compile-file
  "Compile in-path to assembly text. Throws on error."
  [in-path include-dirs]
  (let [src (slurp in-path :encoding "ISO-8859-1")
        toks (pp/preprocess src in-path include-dirs)]
    (binding [t/*structs* (atom {})]
      (let [prog ((await-var parse-var) toks)]
        ((await-var gen-var) prog)))))

(defn delete-quietly [path]
  (try (io/delete-file path true) (catch Exception _ nil)))

(defn compile-one
  "Returns true on success."
  [in-path out-path include-dirs]
  (try
    (let [asm (compile-file in-path include-dirs)]
      (spit out-path asm :encoding "ISO-8859-1")
      true)
    (catch Throwable e
      (delete-quietly out-path)
      (binding [*out* *err*]
        (let [d (ex-data e)]
          (if (= :compile-error (:type d))
            (println (str in-path ": " (.getMessage e)))
            (do (println (str in-path ": internal error: " e))
                (when (System/getenv "CCC_DEBUG")
                  (.printStackTrace e))))))
      false)))

(defn parse-args [args]
  (loop [as args opts {:includes [] :inputs []}]
    (if (empty? as)
      opts
      (let [a (first as)]
        (cond
          (= a "-I") (recur (drop 2 as) (update opts :includes conj (second as)))
          (str/starts-with? a "-I") (recur (rest as) (update opts :includes conj (subs a 2)))
          (= a "-o") (recur (drop 2 as) (assoc opts :output (second as)))
          (or (= a "-D") (str/starts-with? a "-D"))
          (let [d (if (= a "-D") (second as) (subs a 2))
                [n v] (let [i (str/index-of d "=")] (if i [(subs d 0 i) (subs d (inc i))] [d "1"]))]
            (recur (if (= a "-D") (drop 2 as) (rest as)) (update opts :defines (fnil conj []) [n v])))
          (= a "--batch") (recur (drop 2 as) (assoc opts :batch (second as)))
          (= a "-E") (recur (rest as) (assoc opts :pp-only true))
          :else (recur (rest as) (update opts :inputs conj a)))))))

(defn- n-workers [njobs]
  (max 1 (min njobs (if-let [w (System/getenv "CCC_WORKERS")] (parse-long w) 4) (.availableProcessors (Runtime/getRuntime)))))

(defn run-jobs
  "jobs: vector of [in out]. Compiles them on worker threads; diagnostics go to
   stderr in job order. Returns a vector of booleans (success per job)."
  [jobs includes defines]
  (let [n (count jobs)
        ;; largest inputs first, for load balance
        order (vec (sort-by (fn [i] (- (.length (File. ^String (first (jobs i)))))) (range n)))
        next-i (AtomicInteger. 0)
        results (object-array n)
        latches (vec (repeatedly n #(CountDownLatch. 1)))
        work (fn []
               (binding [pp/*cmdline-defines* defines]
                 (loop []
                   (let [k (.getAndIncrement next-i)]
                     (when (< k n)
                       (let [i (order k)
                             [in out] (jobs i)
                             sw (StringWriter.)
                             ok (binding [*err* sw]
                                  (try (compile-one in out includes)
                                       (catch Throwable e
                                         (binding [*out* *err*] (println (str in ": internal error: " e)))
                                         false)))]
                         (trace "done" in)
                         (aset results i [ok (str sw)])
                         (.countDown ^CountDownLatch (latches i))
                         (recur)))))))
        nw (n-workers n)
        threads (doall (for [w (range nw)]
                         (doto (Thread. nil ^Runnable work (str "ccc-worker-" w) big-stack)
                           (.setDaemon true)
                           (.start))))]
    (mapv (fn [i]
            (.await ^CountDownLatch (latches i))
            (let [[ok err] (aget results i)]
              (when (seq err) (let [^java.io.Writer w *err*] (.write w ^String err) (.flush w)))
              ok))
          (range n))))

(defn run-main [args parse-p gen-p]
  (reset! parse-var parse-p)
  (reset! gen-var gen-p)
  (let [{:keys [includes inputs output batch defines pp-only]} (parse-args args)
        defines (or defines [])]
    (binding [pp/*cmdline-defines* defines]
      (if pp-only
        (do (doseq [tk (pp/preprocess (slurp (first inputs) :encoding "ISO-8859-1") (first inputs) includes)]
              (when (:bol tk) (println))
              (print (str (when (:space tk) " ") (:text tk))))
            (println)
            0)
        (if batch
          (let [jobs (vec (for [line (str/split-lines (slurp batch))
                                :let [line (str/trim line)]
                                :when (seq line)]
                            (let [[in out] (str/split line #"\s+")] [in out])))]
            (run-jobs jobs includes defines)
            0)
          (if (and (= 1 (count inputs)) output)
            (if (first (run-jobs [[(first inputs) output]] includes defines)) 0 1)
            (do (binding [*out* *err*] (println "usage: ccc.main [-I dir]... <in.c> -o <out.s> | --batch <list>"))
                2)))))))
