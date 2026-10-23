(ns ccc.main
  "Entry point: single-file and --batch compilation."
  (:require [ccc.lexer :as lx]
            [ccc.preproc :as pp]
            [ccc.parser :as parser]
            [ccc.codegen :as cg]
            [ccc.types :as t]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:gen-class))

(defn compile-file
  "Compile in-path to assembly text. Throws on error."
  [in-path include-dirs]
  (let [src (slurp in-path :encoding "ISO-8859-1")
        toks (pp/preprocess src in-path include-dirs)]
    (binding [t/*structs* (atom {})]
      (let [prog (parser/parse-program toks)]
        (cg/generate prog)))))

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

(defn run-main [args]
  (let [{:keys [includes inputs output batch defines]} (parse-args args)]
   (binding [pp/*cmdline-defines* (or defines [])]
    (if (:pp-only (parse-args args))
      (do (doseq [tk (pp/preprocess (slurp (first inputs) :encoding "ISO-8859-1") (first inputs) includes)]
            (when (:bol tk) (println))
            (print (str (when (:space tk) " ") (:text tk))))
          (println)
          0)
    (if batch
      (do
        (doseq [line (str/split-lines (slurp batch))
                :let [line (str/trim line)]
                :when (seq line)]
          (let [[in out] (str/split line #"\s+")]
            (compile-one in out includes)
            (flush)))
        0)
      (if (and (= 1 (count inputs)) output)
        (if (compile-one (first inputs) output includes) 0 1)
        (do (binding [*out* *err*] (println "usage: ccc.main [-I dir]... <in.c> -o <out.s> | --batch <list>"))
            2)))))))

(defn -main [& args]
  (let [result (promise)
        th (Thread. nil (fn [] (deliver result (try (run-main args)
                                                    (catch Throwable e
                                                      (binding [*out* *err*] (println "fatal:" e))
                                                      1))))
                    "ccc-main" (* 1024 1024 1024))]
    (.start th)
    (let [code @result]
      (flush)
      (.flush System/err)
      (System/exit code))))
