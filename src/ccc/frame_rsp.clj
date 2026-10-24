(ns ccc.frame-rsp
  "Frame finalisation (docs/INTERFACES.md step 6): turns a finished function's text from an
   rbp frame into an rsp-relative one (no push/mov/leave; leaf functions with small frames use
   the red zone). A function is converted only when every rbp and rsp use in it is understood
   and the stack depth is consistent at every jump; otherwise it is left alone."
  (:import [java.util ArrayList HashMap]))

(defn- imm-rsp
  "N for \"  subq $N, %rsp\" / \"  addq $N, %rsp\" lines, else nil."
  [^String l]
  (when (and (.endsWith l ", %rsp") (> (.length l) 14) (= \$ (.charAt l 7)))
    (try (Long/parseLong (subs l 8 (- (.length l) 6))) (catch Exception _ nil))))

(defn- rbp-ok?
  "True when every %rbp in l is the base register of a memory operand."
  [^String l]
  (loop [from 0]
    (let [i (.indexOf l "%rbp" from)]
      (cond (neg? i) true
            (and (pos? i) (= \( (.charAt l (dec i)))) (recur (+ i 4))
            :else false))))

(defn- rewrite-rbp ^String [^String l ^long k ^long d]
  (let [sb (StringBuilder.)]
    (loop [from 0]
      (let [i (.indexOf l "(%rbp" from)]
        (if (neg? i)
          (do (.append sb l from (.length l)) (.toString sb))
          (let [s (loop [j i]
                    (if (and (pos? j) (let [c (.charAt l (dec j))] (or (Character/isDigit c) (= c \-))))
                      (recur (dec j)) j))
                n (if (= s i) 0 (Long/parseLong (subs l s i)))]
            (.append sb l from s)
            (.append sb (+ n k d))
            (.append sb "(%rsp")
            (recur (+ i 5))))))))

(defn- analyze
  "Walks lines [from, to) of ls. Returns nil when the function cannot be converted, else
   {:has-call :rsp-mod :depths int-array (depth before each line)}"
  [^ArrayList ls from to]
  (let [labels (HashMap.)
        depths (int-array (- to from))]
    (loop [i from d 0 dead false has-call false rsp-mod false leaves 0]
      (if (>= i to)
        (when (and (zero? d) (pos? leaves)) {:has-call has-call :rsp-mod rsp-mod :depths depths})
        (let [^String l (.get ls i)
              _ (aset depths (- i from) (int d))
              n (.length l)]
          (cond
            (zero? n) (recur (inc i) d dead has-call rsp-mod leaves)
            (not= \space (.charAt l 0))
            ;; label
            (if (= \: (.charAt l (dec n)))
              (let [k (subs l 0 (dec n))
                    known (.get labels k)]
                (cond
                  (and known dead) (do (aset depths (- i from) (int known)) (recur (inc i) (long known) false has-call rsp-mod leaves))
                  (and known (not= (long known) (long d))) nil
                  :else (do (.put labels k (long d)) (recur (inc i) d false has-call rsp-mod leaves))))
              (recur (inc i) d dead has-call rsp-mod leaves))
            (< n 3) (recur (inc i) d dead has-call rsp-mod leaves)
            (= \. (.charAt l 2)) (recur (inc i) d dead has-call rsp-mod leaves)
            (.endsWith l ", %rsp")
            ;; the only writes to rsp understood are subq/addq with an immediate
            (if-let [v (imm-rsp l)]
              (cond (.startsWith l "  subq ") (recur (inc i) (+ d v) dead has-call true leaves)
                    (.startsWith l "  addq ") (recur (inc i) (- d v) dead has-call true leaves)
                    :else nil)
              nil)
            :else
            (let [c (.charAt l 2)]
              (cond
                (and (= c \p) (.startsWith l "  pushq "))
                (if (.contains l "%rbp") nil (recur (inc i) (+ d 8) dead has-call true leaves))
                (and (= c \p) (.startsWith l "  popq "))
                (if (.contains l "(") nil (recur (inc i) (- d 8) dead has-call true leaves))
                (and (= c \l) (.equals l "  leave"))
                (if (and (zero? d) (< (inc i) to) (.equals "  ret" (.get ls (inc i))))
                  (recur (inc i) d dead has-call rsp-mod (inc leaves))
                  nil)
                (and (= c \r) (.equals l "  ret"))
                (recur (inc i) d true has-call rsp-mod leaves)
                (and (= c \c) (.startsWith l "  call"))
                (recur (inc i) d dead true rsp-mod leaves)
                (= c \j)
                (if (.startsWith l "  jmp *")
                  (if (and (zero? d) (not (.contains l "%rbp"))) (recur (inc i) d true has-call rsp-mod leaves) nil)
                  (let [uncond (.startsWith l "  jmp ")
                        t (subs l (inc (.lastIndexOf l " "))) known (.get labels t)]
                    (if (and known (not= (long known) (long d)))
                      nil
                      (do (.put labels t (long d)) (recur (inc i) d (or uncond dead) has-call rsp-mod leaves)))))
                (.contains l "%rbp")
                (if (rbp-ok? l) (recur (inc i) d dead has-call rsp-mod leaves) nil)
                :else (recur (inc i) d dead has-call rsp-mod leaves)))))))))

(defn- split-lines ^ArrayList [^String text]
  (let [out (ArrayList. 256) n (.length text)]
    (loop [from 0]
      (when (< from n)
        (let [i (.indexOf text 10 (int from))]
          (if (neg? i)
            (.add out (.substring text from))
            (do (.add out (.substring text from i)) (recur (inc i)))))))
    out))

(defn rsp-frame
  "Text of one function (from its .type line to its .size line) -> text. The function must
   start (after any directives) with pushq %rbp / movq %rsp, %rbp [/ subq $N, %rsp]."
  [^String text]
  (let [ls (split-lines text)
        n (.size ls)
        p (loop [i 0] (cond (>= i n) -1 (.equals "  pushq %rbp" (.get ls i)) i :else (recur (inc i))))
        e (loop [j (dec n)] (cond (neg? j) -1 (.startsWith ^String (.get ls j) "  .size ") j :else (recur (dec j))))]
    (or
     (when (and (pos? p) (> e (+ p 2)) (.equals "  movq %rsp, %rbp" (.get ls (+ p 1))))
       (let [^String l2 (.get ls (+ p 2))
             sub? (and (.startsWith l2 "  subq $") (.endsWith l2 ", %rsp"))
             sz (if sub? (imm-rsp l2) 0)
             body-from (+ p (if sub? 3 2))]
         (when sz
           (when-let [{:keys [has-call rsp-mod depths]} (analyze ls body-from e)]
             (let [fnew (cond has-call (+ sz 8)
                              (and (not rsp-mod) (<= sz 120)) 0
                              (zero? sz) 0
                              :else (+ sz 8))
                   k (- fnew 8)
                   sb (StringBuilder. (+ 64 (.length text)))]
               (dotimes [i p] (.append sb ^String (.get ls i)) (.append sb \newline))
               (when (pos? fnew) (.append sb "  subq $") (.append sb (long fnew)) (.append sb ", %rsp\n"))
               (loop [i body-from]
                 (when (< i e)
                   (let [^String l (.get ls i)]
                     (cond
                       (.equals l "  leave") (when (pos? fnew) (.append sb "  addq $") (.append sb (long fnew)) (.append sb ", %rsp\n"))
                       (.contains l "(%rbp") (do (.append sb (rewrite-rbp l k (aget depths (- i body-from)))) (.append sb \newline))
                       :else (do (.append sb l) (.append sb \newline)))
                     (recur (inc i)))))
               (loop [i e] (when (< i n) (.append sb ^String (.get ls i)) (.append sb \newline) (recur (inc i))))
               (.toString sb))))))
     text)))
