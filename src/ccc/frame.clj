(ns ccc.frame
  "Frame finalisation: the last text pass over a finished function.

   The code generator builds every function on a %rbp frame:
       pushq %rbp; movq %rsp,%rbp; [subq $N,%rsp] ... leave; ret
   with every frame slot addressed as K(%rbp).  This pass turns that into an %rsp-relative
   frame:  subq $N+8,%rsp ... addq $N+8,%rsp; ret   (rsp after the prologue is unchanged, so
   call alignment is kept), and into no frame at all (the red zone) for a leaf function with a
   small frame and no stack traffic.  Every K(%rbp) becomes (K-8+M+d)(%rsp), where M is the new
   frame size and d the number of bytes pushed at that point; d is computed over the control flow
   graph and the function is left alone when it is not the same on every path into an instruction
   or when the text does anything with %rsp / %rbp this pass does not understand."
  (:refer-clojure :exclude [case let fn defn defn- if-let when-let and or loop doseq for binding cond-> some-> dotimes letfn doto])
  (:require [ccc.fastcase :refer [case let fn defn defn- if-let when-let and or loop doseq for binding cond-> some-> dotimes letfn doto]]
            [ccc.frame-rsp :as frame-rsp]
            [clojure.string :as str]))

(def ^:private ^:const UNKNOWN Integer/MIN_VALUE)

(defn- label-line? [^String l]
  (and (pos? (.length l)) (not= \space (.charAt l 0)) (.endsWith l ":")))

(defn- dir-line? [^String l]
  (or (< (.length l) 3)
      (and (= \space (.charAt l 0)) (or (= \. (.charAt l 2)) (= \# (.charAt l 2))))))

(defn- opname ^String [^String l]
  (let [i (.indexOf l " " 2)]
    (if (neg? i) (subs l 2) (subs l 2 i))))

(defn- operand-str ^String [^String l]
  (let [i (.indexOf l " " 2)]
    (if (neg? i) "" (subs l (inc i)))))

(defn- imm-of
  "Value of an operand of the form $N, or nil."
  [^String s]
  (when (and (pos? (.length s)) (= \$ (.charAt s 0)))
    (parse-long (subs s 1))))

(defn- stack-delta
  "Change of %rsp (bytes pushed, positive) by the instruction line, or nil when it does something with
   %rsp that is not understood. Mentions of %rsp inside memory operands are fine."
  [^String l ^String op]
  (cond
    (= op "pushq") 8
    (= op "popq") -8
    (or (= op "subq") (= op "addq"))
    (let [a (operand-str l)
          i (.indexOf a ", %rsp")]
      (if (and (pos? i) (= i (- (.length a) 6)))
        (when-let [v (imm-of (subs a 0 i))] (if (= op "subq") v (- v)))
        (if (.contains a "%rsp") nil 0)))
    :else
    ;; any other instruction naming %rsp as a register (not in a memory operand)
    (let [i (.indexOf l "%rsp")]
      (if (neg? i)
        0
        (loop [i i]
          (cond (neg? i) 0
                (and (pos? i) (= \( (.charAt l (dec i)))) (recur (.indexOf l "%rsp" (int (inc i))))
                (and (> i 1) (= \( (.charAt l (dec i)))) (recur (.indexOf l "%rsp" (int (inc i))))
                :else nil))))))

(defn- rbp-ok?
  "Every %rbp in the line is the base of a memory operand."
  [^String l]
  (loop [i (.indexOf l "%rbp")]
    (cond (neg? i) true
          (and (pos? i) (= \( (.charAt l (dec i)))
               (< (+ i 4) (.length l))
               (let [c (.charAt l (+ i 4))] (or (= c \)) (= c \,))))
          (recur (.indexOf l "%rbp" (int (inc i))))
          :else false)))

(defn- rewrite-rbp
  "Replace each K(%rbp... by (K+delta)(%rsp...."
  ^String [^String l ^long delta]
  (let [sb (StringBuilder.)
        n (.length l)]
    (loop [pos 0]
      (let [i (.indexOf l "(%rbp" (int pos))]
        (if (neg? i)
          (.append sb l pos n)
          ;; displacement: digits and sign immediately before i
          (let [ds (loop [j i] (if (and (pos? j) (let [c (.charAt l (dec j))] (or (Character/isDigit c) (= c \-)))) (recur (dec j)) j))
                dtxt (subs l ds i)
                d (if (= dtxt "") 0 (parse-long dtxt))]
            (.append sb l pos ds)
            (let [nd (+ d delta)]
              (when-not (zero? nd) (.append sb nd)))
            (.append sb "(%rsp")
            (recur (+ i 5))))))
    (.toString sb)))

(defn- analyse
  "Stack depth at every line (int[], UNKNOWN for unreachable lines) or nil when it is inconsistent or
   the function does something this pass does not understand. Also returns whether it calls / pushes."
  [^objects lines first-body]
  (let [n (alength lines)
        depth (int-array n UNKNOWN)
        labels (java.util.HashMap.)
        seeds (java.util.ArrayList.)
        work (java.util.ArrayDeque.)
        ok (volatile! true)
        flags (long-array 2)]
    (dotimes [i n]
      (let [^String l (aget lines i)]
        (when (label-line? l) (.put labels (subs l 0 (dec (.length l))) (int i)))))
    (dotimes [i n]
      (let [^String l (aget lines i)]
        (cond
          (and (dir-line? l) (.startsWith l "  .long "))
          (let [a (subs l 8) j (.indexOf a "-")]
            (when (pos? j) (when-let [t (.get labels (subs a 0 j))] (.add seeds t))))
          (and (.startsWith l "  leaq ") (.contains l "(%rip)"))
          (let [a (subs l 7) j (.indexOf a "(%rip)")]
            (when (pos? j) (when-let [t (.get labels (subs a 0 j))] (.add seeds t)))))))
    (letfn [(set-depth! [i d]
              (let [cur (aget depth i)]
                (cond (== cur UNKNOWN) (do (aset depth i (int d)) (.add work (int i)))
                      (== cur d) nil
                      :else (vreset! ok false))))]
      (dotimes [s (.size seeds)] (set-depth! (long (.get seeds s)) 0))
      (set-depth! first-body 0)
      (loop []
        (when (and @ok (not (.isEmpty work)))
          (let [i (long (.poll work))
                ^String l (aget lines i)
                d (long (aget depth i))]
            (if (or (label-line? l) (dir-line? l))
              (when (< (inc i) n) (set-depth! (inc i) d))
              (let [op (opname l)
                    delta (stack-delta l op)]
                (cond
                  (nil? delta) (vreset! ok false)
                  (= op "ret") nil
                  (= op "jmp")
                  (let [a (operand-str l)]
                    (if (.startsWith a "*")
                      (when-not (zero? d) (vreset! ok false))
                      (if-let [t (.get labels a)] (set-depth! t d) (vreset! ok false))))
                  (and (= \j (.charAt op 0)) (not= op "jmp"))
                  (if-let [t (.get labels (operand-str l))]
                    (do (set-depth! t d) (when (< (inc i) n) (set-depth! (inc i) d)))
                    (vreset! ok false))
                  :else
                  (do (when (= op "call") (aset flags 0 1))
                      (when (not (zero? delta)) (aset flags 1 1))
                      (when (< (inc i) n) (set-depth! (inc i) (+ d delta))))))))
          (recur))))
    (when @ok [depth (== 1 (aget flags 0)) (== 1 (aget flags 1))])))

(defn finalize*
  "text: the finished text of one function (from .type to .size). Returns the converted text, or text itself."
  ^String [^String text]
  (let [^objects lines (to-array (str/split text #"\n"))
        n (alength lines)
        lab (loop [i 0] (cond (>= i n) -1 (label-line? (aget lines i)) i :else (recur (inc i))))]
    (if (or (neg? lab) (>= (+ lab 3) n)
            (not= (aget lines (+ lab 1)) "  pushq %rbp")
            (not= (aget lines (+ lab 2)) "  movq %rsp, %rbp"))
      text
      (let [^String l3 (aget lines (+ lab 3))
            sub3 (when (and (.startsWith l3 "  subq $") (.endsWith l3 ", %rsp"))
                   (parse-long (subs l3 8 (- (.length l3) 6))))
            N (long (or sub3 0))
            first-body (+ lab 3 (if sub3 1 0))
            res (when (every? (fn [i] (let [^String l (aget lines i)]
                                        (or (label-line? l) (dir-line? l) (rbp-ok? l))))
                              (range first-body n))
                  (analyse lines first-body))]
        (if-not res
          text
          (let [[^ints depth has-call has-stack] res
                red? (and (not has-call) (not has-stack) (<= N 120)
                          (not (.contains text "%rsp")) (not (.contains text "rep ")))
                M (if red? 0 (+ N 8))
                sb (StringBuilder. (+ 64 (.length text)))]
            (let [conv (fn [i]
                         (let [^String l (aget lines i)
                               d (let [x (aget depth i)] (if (== x UNKNOWN) 0 x))]
                           (cond
                             (or (label-line? l) (dir-line? l)) (str l "\n")
                             (= l "  leave") (let [k (+ M d)] (if (pos? k) (str "  addq $" k ", %rsp\n") ""))
                             (.contains l "%rbp") (str (rewrite-rbp l (+ (- M 8) d)) "\n")
                             :else (str l "\n"))))
                  ;; the epilogue after .L.return.<name>: small ones are copied to the early returns
                  rl (loop [i (inc lab)]
                       (cond (>= i n) -1
                             (and (label-line? (aget lines i)) (.startsWith ^String (aget lines i) ".L.return.")) i
                             :else (recur (inc i))))
                  re (when (pos? rl)
                       (loop [i (inc rl)]
                         (cond (or (>= i n) (> (- i rl) 10)) -1
                               (= "  ret" (aget lines i)) i
                               (or (label-line? (aget lines i)) (dir-line? (aget lines i))) -1
                               :else (recur (inc i)))))
                  epi (when (and re (pos? re))
                        (let [es (StringBuilder.)]
                          (loop [i (inc rl)]
                            (when (<= i re) (.append es ^String (conv i)) (recur (inc i))))
                          (.toString es)))
                  jmp-ret (when (pos? rl) (str "  jmp " (let [^String x (aget lines rl)] (subs x 0 (dec (.length x))))))]
              (dotimes [i n]
                (let [^String l (aget lines i)]
                  (cond
                    (<= i lab) (do (.append sb l) (.append sb "\n")
                                   (when (and (== i lab) (not red?))
                                     (.append sb "  subq $") (.append sb M) (.append sb ", %rsp\n")))
                    (< i first-body) nil
                    (and epi (= l jmp-ret) (not (and (< (inc i) n) (= (aget lines (inc i)) (aget lines rl)))))
                    (.append sb ^String epi)
                    :else (.append sb ^String (conv i))))))
            (.toString sb)))))))

(defn finalize
  "Frame finalisation of one finished function: finalize* (rsp frame, red zone, small epilogues
   copied to early returns) first; when it leaves the function alone, ccc.frame-rsp/rsp-frame
   (round 2 merge: the two were written independently and each declines functions the other
   converts)."
  ^String [^String text]
  (let [r (finalize* text)]
    (if (identical? r text) (frame-rsp/rsp-frame text) r)))
