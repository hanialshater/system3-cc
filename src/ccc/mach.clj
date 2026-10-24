(ns ccc.mach
  "Machine-level optimisation of one function body (the instructions between
   the prologue and the epilogue, as emitted by ccc.codegen).

   Every line is parsed into an instruction with exact sets of the general
   registers (and flags) it reads and writes; anything not understood is a
   barrier (reads and writes everything, never moved or deleted). On basic
   blocks with global register liveness it runs, until nothing changes:
   - frame-slot forwarding: a load of a non-escaping frame slot whose value is
     known to be in a register becomes a register copy;
   - copy propagation: uses of a register copied from another are read from
     the original;
   - def retargeting: `op ..., %A; mov %A, %X` with %A dead afterwards
     becomes `op ..., %X`;
   - dead code: side-effect-free instructions whose results are all dead.
   Registers are numbered 0..15 (rax rcx rdx rbx rsp rbp rsi rdi r8..r15),
   flags are bit 16."
  (:refer-clojure :exclude [case let fn defn defn- if-let when-let and or loop doseq for binding cond-> some-> dotimes letfn doto])
  (:require [ccc.fastcase :refer [case let fn defn defn- if-let when-let and or loop doseq for binding cond-> some-> dotimes letfn doto]]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Registers

(def reg-names
  [["%rax" "%eax" "%ax" "%al"] ["%rcx" "%ecx" "%cx" "%cl"] ["%rdx" "%edx" "%dx" "%dl"]
   ["%rbx" "%ebx" "%bx" "%bl"] ["%rsp" "%esp" "%sp" "%spl"] ["%rbp" "%ebp" "%bp" "%bpl"]
   ["%rsi" "%esi" "%si" "%sil"] ["%rdi" "%edi" "%di" "%dil"]
   ["%r8" "%r8d" "%r8w" "%r8b"] ["%r9" "%r9d" "%r9w" "%r9b"] ["%r10" "%r10d" "%r10w" "%r10b"]
   ["%r11" "%r11d" "%r11w" "%r11b"] ["%r12" "%r12d" "%r12w" "%r12b"] ["%r13" "%r13d" "%r13w" "%r13b"]
   ["%r14" "%r14d" "%r14w" "%r14b"] ["%r15" "%r15d" "%r15w" "%r15b"]])

(def ^java.util.HashMap reg-table
  (let [m (java.util.HashMap.)]
    (doseq [f (range 16) [i w] (map vector (range 4) [64 32 16 8])]
      (.put m ((reg-names f) i) [f w]))
    m))

(defn reg-name [f w] ((reg-names f) (case (int w) 64 0 32 1 16 2 8 3)))

(def RAX 0) (def RCX 1) (def RDX 2) (def RSP 4) (def RBP 5)
(def FLAGS 16)
(defn bit ^long [f] (bit-shift-left 1 (long f)))
(def ^:const all-mask 0x1ffff)
(def flags-bit (bit 16))
(def stack-bits (bit-or (bit 4) (bit 5)))
(def call-use-mask (reduce bit-or (map bit [0 1 2 6 7 8 9 10 4])))
(def call-def-mask (reduce bit-or (map bit [0 1 2 6 7 8 9 10 11 16])))
(def exit-live-mask (reduce bit-or (map bit [0 2 4 5])))

;; ---------------------------------------------------------------------------
;; Operands
;; {:t :r :f fam :w width} register; {:t :i :s text} immediate;
;; {:t :m :d disp :b fam :x fam :sc scale-text} memory (:b/:x nil when absent);
;; {:t :o :s text} anything else (labels, %xmm, %st, *%reg ...)

(defn parse-mem [^String s]
  (let [p (.indexOf s "(")]
    (when (and (pos? (inc p)) (.endsWith s ")") (neg? (.indexOf s ":")))
      (let [disp (subs s 0 p)
            inner (subs s (inc p) (dec (count s)))
            parts (str/split inner #"," -1)
            rb (first parts)
            b (when (seq rb) (.get reg-table rb))
            rx (second parts)
            x (when (seq rx) (.get reg-table rx))]
        (cond
          (and (= "%rip" rb) (= 1 (count parts))) {:t :m :d disp :rip true}
          (and (seq rb) (not b)) nil
          (and (seq rx) (not x)) nil
          (and b (not= 64 (b 1))) nil
          (and x (not= 64 (x 1))) nil
          (> (count parts) 3) nil
          :else (let [bf (when b (b 0)) xf (when x (x 0))
                      slot (when (and (= RBP bf) (nil? xf))
                             (cond (= "" disp) 0
                                   (re-matches #"-?\d+" disp) (parse-long disp)
                                   :else nil))]
                  {:t :m :d disp :b bf :x xf :sc (nth parts 2 nil) :slot slot}))))))

(defn parse-operand [^String s]
  (cond
    (.startsWith s "$") {:t :i :s s}
    (.startsWith s "%") (if-let [r (.get reg-table s)] {:t :r :f (r 0) :w (r 1)} {:t :o :s s})
    (.startsWith s "*") {:t :o :s s}
    (.endsWith s ")") (or (parse-mem s) {:t :bad :s s})
    :else {:t :o :s s}))

(defn render-operand [o]
  (case (:t o)
    :r (reg-name (:f o) (:w o))
    :m (if (:rip o)
         (str (:d o) "(%rip)")
         (str (:d o) "(" (when (:b o) (reg-name (:b o) 64))
              (when (or (:x o) (:sc o)) (str "," (when (:x o) (reg-name (:x o) 64)) (when (:sc o) (str "," (:sc o)))))
              ")"))
    (:s o)))

(defn mem-regs ^long [o]
  (if (= :m (:t o))
    (bit-or (if (:b o) (bit (:b o)) 0) (if (:x o) (bit (:x o)) 0))
    0))

(defn frame-slot
  "Offset of a direct rbp-relative operand with a numeric displacement, or nil."
  [o]
  (:slot o))

;; ---------------------------------------------------------------------------
;; Instruction classes

(def suffix-width {\b 8 \w 16 \l 32 \q 64})

(def ^java.util.HashMap op-table
  (let [m (java.util.HashMap.)
        put (fn [k v] (.put m k v))]
    (doseq [[s w] [["b" 8] ["w" 16] ["l" 32] ["q" 64]]]
      (put (str "mov" s) [:move w])
      (doseq [a ["add" "sub" "and" "or" "xor"]] (put (str a s) [:alu w]))
      (doseq [a ["adc" "sbb"]] (put (str a s) [:aluc w]))
      (doseq [a ["cmp" "test"]] (put (str a s) [:cmp w]))
      (doseq [a ["shl" "sal" "shr" "sar"]] (put (str a s) [:shift w]))
      (doseq [a ["rol" "ror"]] (put (str a s) [:rot w]))
      (put (str "neg" s) [:neg w])
      (put (str "not" s) [:not w])
      (doseq [a ["inc" "dec"]] (put (str a s) [:incdec w]))
      (doseq [a ["idiv" "div"]] (put (str a s) [:div w]))
      (put (str "mul" s) [:mul1 w]))
    (doseq [[k w] [["imulw" 16] ["imull" 32] ["imulq" 64]]] (put k [:imul w]))
    (doseq [[k w] [["movzbl" 32] ["movzwl" 32] ["movzbq" 64] ["movzwq" 64] ["movsbl" 32] ["movswl" 32]
                   ["movsbq" 64] ["movswq" 64] ["movslq" 64] ["movzbw" 16] ["movsbw" 16]]]
      (put k [:ext w]))
    (put "leaq" [:lea 64]) (put "leal" [:lea 32])
    (put "movabsq" [:move 64])
    (doseq [k ["bswapl" "bswapq"]] (put k [:not (if (= k "bswapl") 32 64)]))
    (doseq [[k w] [["popcntl" 32] ["popcntq" 64]]] (put k [:popcnt w]))
    (doseq [[k w] [["bsfl" 32] ["bsfq" 64] ["bsrl" 32] ["bsrq" 64]]] (put k [:bitscan w]))
    (doseq [cc ["e" "ne" "z" "nz" "l" "le" "g" "ge" "b" "be" "a" "ae" "s" "ns" "p" "np" "c" "nc" "o" "no"]]
      (put (str "set" cc) [:setcc 8])
      (put (str "j" cc) [:jcc 0])
      (doseq [[s w] [["w" 16] ["l" 32] ["q" 64]]] (put (str "cmov" cc s) [:cmov w])))
    (put "cqto" [:cqto 64]) (put "cqo" [:cqto 64]) (put "cltd" [:cqto 32])
    (put "cltq" [:cltq 64]) (put "cwtl" [:cltq 32])
    (put "jmp" [:jmp 0])
    (put "call" [:call 0])
    (put "pushq" [:push 64]) (put "popq" [:pop 64])
    (put "rep" [:rep 0])
    ;; SSE: GPR operands are read, a GPR destination is fully written
    (doseq [k ["cvttsd2sil" "cvttsd2siq" "cvttss2sil" "cvttss2siq" "cvtsd2sil" "cvtsd2siq" "cvtss2sil" "cvtss2siq"
               "movmskpd" "movmskps"]]
      (put k [:sse2gpr (if (.endsWith ^String k "q") 64 32)]))
    (doseq [k ["movsd" "movss" "movaps" "movapd" "movups" "movupd" "movdqa" "movdqu"
               "addsd" "addss" "subsd" "subss" "mulsd" "mulss" "divsd" "divss" "sqrtsd" "sqrtss"
               "andpd" "andps" "andnpd" "andnps" "orpd" "orps" "xorpd" "xorps" "maxsd" "minsd" "maxss" "minss"
               "cvtss2sd" "cvtsd2ss" "cvtsi2sdl" "cvtsi2sdq" "cvtsi2ssl" "cvtsi2ssq" "unpcklpd" "unpcklps"
               "pxor" "movhlps" "movlhps" "shufps" "shufpd"]]
      (put k [:sse 0]))
    (doseq [k ["ucomisd" "ucomiss" "comisd" "comiss"]] (put k [:sseflags 0]))
    (put "movd" [:movx 32]) (put "movq" [:move 64])
    m))

(def x87-flag-ops #{"fucomip" "fcomip" "fucomi" "fcomi"})

;; An instruction: {:k :ins :op op :a [operands] :c class :w width :u use :d def
;;                  :pure deletable-when-dead :mw writes-memory :text text-or-nil}

(defn reg? [o] (= :r (:t o)))
(defn gp-reg? [o] (= :r (:t o)))
(defn mem? [o] (= :m (:t o)))

(defn- write-mask
  "Bits a write of operand o (at width w) defines fully, and bits it reads (partial writes)."
  [o]
  (if (reg? o)
    (if (>= (long (:w o)) 32) [(bit (:f o)) 0] [(bit (:f o)) (bit (:f o))])
    [0 0]))

(defn- read-mask ^long [o]
  (case (:t o)
    :r (bit (:f o))
    :m (mem-regs o)
    0))

(defn- frame-mem? [o] (and (mem? o) (or (= RBP (:b o)) (= RSP (:b o))) (nil? (:x o))))

(defn analyse
  "Fill in :c :u :d :pure :mw for an instruction (barrier when not understood)."
  [ins]
  (let [op (:op ins) a (:a ins) n (count a)
        cls (.get op-table op)
        barrier (assoc ins :c :barrier :u all-mask :d all-mask :pure false :mw true)
        bad (some #(= :bad (:t %)) a)]
    (if (or bad (nil? cls))
      (if (and (not bad) (.startsWith ^String op "f") (not (.startsWith ^String op "fn")))
        ;; x87: GPRs only in memory operands
        (assoc ins :c :x87 :u (reduce bit-or 0 (map read-mask a))
               :d (if (x87-flag-ops op) flags-bit 0) :pure false
               :mw (boolean (some mem? a)))
        barrier)
      (let [[c w] cls
            src (when (pos? n) (a 0))
            dst (when (pos? n) (a (dec n)))
            [dw dr] (when dst (write-mask dst))
            mw (boolean (and dst (mem? dst)))
            src-mem (some mem? (butlast a))
            pure-src (fn [] (not (and src-mem (not (some frame-mem? (filter mem? (butlast a)))))))
            set (fn [u d pure] (assoc ins :c c :w w :u u :d d :pure (boolean pure) :mw mw))]
        (case c
          :move (cond
                  (and (= 2 n) (= :o (:t src)) (.startsWith ^String (:s src) "%xmm") (reg? dst))
                  (set 0 dw true)                                       ; movq %xmm, %r
                  (and (= 2 n) (= :o (:t dst)) (.startsWith ^String (:s dst) "%xmm"))
                  (set (read-mask src) 0 false)                         ; movq %r/mem, %xmm
                  (= 2 n) (set (bit-or (read-mask src) dr (if (mem? dst) (mem-regs dst) 0)) dw (and (reg? dst) (pure-src)))
                  :else barrier)
          :movx (cond
                  (and (= 2 n) (= :o (:t src)) (reg? dst)) (set 0 dw true)
                  (= 2 n) (set (read-mask src) 0 false)
                  :else barrier)
          :ext (if (and (= 2 n) (reg? dst))
                 (set (bit-or (read-mask src) dr) dw (pure-src))
                 barrier)
          :lea (if (and (= 2 n) (mem? src) (reg? dst))
                 (set (mem-regs src) dw true)
                 barrier)
          (:alu :aluc)
          (if (= 2 n)
            (let [zero (and (= :alu c) (reg? src) (reg? dst) (= (:f src) (:f dst)) (= (:w src) (:w dst))
                            (#{"xorl" "xorq" "subl" "subq"} op))
                  u (if zero 0 (bit-or (read-mask src) (read-mask dst)))
                  u (if (= :aluc c) (bit-or u flags-bit) u)]
              (set u (bit-or dw flags-bit) (and (reg? dst) (pure-src))))
            barrier)
          :cmp (if (= 2 n)
                 (set (bit-or (read-mask src) (read-mask dst)) flags-bit (pure-src))
                 barrier)
          (:shift :rot)
          (case (int n)
            1 (set (read-mask dst) (bit-or dw flags-bit) (reg? dst))
            2 (let [cnt-cl (and (reg? src) (= RCX (:f src)) (= 8 (:w src)))
                    imm (= :i (:t src))
                    zero-cnt (or cnt-cl (= "$0" (:s src)) (= :rot c))]
                (if (or cnt-cl imm)
                  (set (bit-or (read-mask dst) (if cnt-cl (bit RCX) 0) (if zero-cnt flags-bit 0))
                       (bit-or dw flags-bit) (reg? dst))
                  barrier))
            barrier)
          :neg (if (= 1 n) (set (read-mask dst) (bit-or dw flags-bit) (reg? dst)) barrier)
          :not (if (= 1 n) (set (read-mask dst) dw (reg? dst)) barrier)
          :incdec (if (= 1 n) (set (bit-or (read-mask dst) flags-bit) (bit-or dw flags-bit) (reg? dst)) barrier)
          :imul (case (int n)
                  3 (if (and (= :i (:t src)) (reg? dst))
                      (set (read-mask (a 1)) (bit-or dw flags-bit) (pure-src))
                      barrier)
                  2 (if (reg? dst)
                      (set (bit-or (read-mask src) (read-mask dst)) (bit-or dw flags-bit) (pure-src))
                      barrier)
                  1 (set (bit-or (read-mask src) (bit RAX)) (bit-or (bit RAX) (bit RDX) flags-bit) false)
                  barrier)
          (:popcnt :bitscan)
          (if (and (= 2 n) (reg? dst))
            (set (bit-or (read-mask src) (if (= :bitscan c) (bit (:f dst)) 0)) (bit-or dw flags-bit) false)
            barrier)
          :setcc (if (= 1 n) (set (bit-or flags-bit dr (if (mem? dst) (mem-regs dst) 0)) dw (reg? dst)) barrier)
          :cmov (if (and (= 2 n) (reg? dst))
                  (set (bit-or flags-bit (read-mask src) (bit (:f dst))) dw false)
                  barrier)
          :cqto (if (zero? n) (set (bit RAX) (bit RDX) true) barrier)
          :cltq (if (zero? n) (set (bit RAX) (bit RAX) true) barrier)
          (:div :mul1) (if (= 1 n)
                         (set (bit-or (read-mask src) (bit RAX) (if (= :div c) (bit RDX) 0))
                              (bit-or (bit RAX) (bit RDX) flags-bit) false)
                         barrier)
          :jcc (if (and (= 1 n) (= :o (:t src))) (set flags-bit 0 false) barrier)
          :jmp (cond
                 (and (= 1 n) (= :o (:t src)) (not (.startsWith ^String (:s src) "*"))) (set 0 0 false)
                 (and (= 1 n) (.startsWith ^String (:s src) "*%"))
                 (if-let [r (.get reg-table (subs (:s src) 1))]
                   (assoc (set (bit (r 0)) 0 false) :c :ijmp)
                   barrier)
                 :else barrier)
          :call (cond
                  (and (= 1 n) (= :o (:t src)) (not (.startsWith ^String (:s src) "*")))
                  (set call-use-mask call-def-mask false)
                  (and (= 1 n) (.startsWith ^String (:s src) "*%"))
                  (if-let [r (.get reg-table (subs (:s src) 1))]
                    (set (bit-or call-use-mask (bit (r 0))) call-def-mask false)
                    barrier)
                  :else barrier)
          :push (if (= 1 n) (assoc (set (bit-or (read-mask src) (bit RSP)) (bit RSP) false) :mw true) barrier)
          :pop (if (and (= 1 n) (reg? dst) (= 64 (:w dst)))
                 (set (bit RSP) (bit-or (bit RSP) dw) false)
                 barrier)
          :rep (if (and (= 1 n) (= :o (:t src)) (#{"stosb" "stosq" "stosl" "movsb" "movsq" "movsl"} (:s src)))
                 (assoc (set (reduce bit-or (map bit [0 1 6 7])) (reduce bit-or (map bit [1 6 7])) false) :mw true)
                 barrier)
          :sse2gpr (if (and (= 2 n) (reg? dst))
                     (set (read-mask src) dw (not (mem? src)))
                     barrier)
          :sse (set (reduce bit-or 0 (map read-mask a)) 0 false)
          :sseflags (set (reduce bit-or 0 (map read-mask a)) flags-bit false)
          barrier)))))

(defn split-args [^String s]
  (loop [start 0 out (transient [])]
    (let [i (.indexOf s ", " (int start))]
      (if (neg? i)
        (persistent! (conj! out (subs s start)))
        (recur (+ i 2) (conj! out (subs s start i)))))))

(defn parse-line
  "An item for one line of the body."
  [^String l]
  (cond
    (and (pos? (.length l)) (not (.startsWith l " ")) (.endsWith l ":"))
    {:k :label :name (subs l 0 (dec (.length l))) :text l}
    (or (not (.startsWith l "  ")) (.startsWith l "  .") (= "" (str/trim l)))
    {:k :dir :text l}
    :else
    (let [t (subs l 2)
          sp (.indexOf t " ")
          op (if (neg? sp) t (subs t 0 sp))
          args (if (neg? sp) [] (mapv parse-operand (split-args (subs t (inc sp)))))]
      (analyse {:k :ins :op op :a args :text l}))))

(defn render [ins]
  (or (:text ins)
      (str "  " (:op ins) (when (seq (:a ins)) (str " " (str/join ", " (map render-operand (:a ins))))))))

;; ---------------------------------------------------------------------------
;; Basic blocks and liveness

(defn terminator? [ins]
  (case (:c ins) (:jmp :ijmp) true false))

(defn build-blocks
  "items: vector. Returns {:blocks [[start end] ...] :succ [[block ...]] :exit? [bool]}
   where a successor index -1 means the function exit, -2 unknown (live-out all)."
  [items]
  (let [n (count items)
        labels (java.util.HashMap.)
        starts (java.util.ArrayList.)]
    ;; block starts: index 0, every label, every item after a jump/barrier-terminator
    (loop [i 0 start? true]
      (when (< i n)
        (let [it (items i)]
          (if (= :label (:k it))
            (do (.add starts i) (recur (inc i) false))
            (if (= :ins (:k it))
              (do (when start? (.add starts i))
                  (recur (inc i) (or (= :jcc (:c it)) (terminator? it))))
              (recur (inc i) start?))))))
    (let [starts (vec (distinct (sort (vec starts))))
          nb (count starts)
          ends (vec (concat (rest starts) [n]))
          _ (dotimes [b nb]
              (loop [i (starts b)]
                (when (and (< i (ends b)) (= :label (:k (items i))))
                  (.put labels (:name (items i)) b)
                  (recur (inc i)))))
          all-label-blocks (vec (distinct (vals labels)))
          table-targets (fn [i]
                          ;; jump table data right after an indirect jump
                          (loop [j (inc i) out [] in-table false]
                            (if (>= j n)
                              (when in-table out)
                              (let [it (items j) ^String t (:text it)]
                                (cond
                                  (and (= :dir (:k it)) (= "  .section .rodata" t)) (recur (inc j) out true)
                                  (and in-table (= :dir (:k it)) (.startsWith t "  .align")) (recur (inc j) out true)
                                  (and in-table (= :label (:k it))) (recur (inc j) out true)
                                  (and in-table (= :dir (:k it)) (.startsWith t "  .long "))
                                  (let [s (subs t 8) m (.indexOf s "-")]
                                    (recur (inc j) (conj out (if (pos? m) (subs s 0 m) s)) true))
                                  (and in-table (= "  .text" t)) out
                                  :else nil)))))
          succ (vec (for [b (range nb)]
                      (let [last-ins (loop [i (dec (ends b))]
                                       (cond (< i (starts b)) nil
                                             (= :ins (:k (items i))) i
                                             (= :label (:k (items i))) nil
                                             :else (recur (dec i))))
                            it (when last-ins (items last-ins))
                            fall (if (< (inc b) nb) (inc b) -1)
                            target (fn [o] (let [s (:s o)] (if-let [tb (.get labels s)] tb (if (.startsWith ^String s ".L.return.") -1 -2))))]
                        (cond
                          (nil? it) [fall]
                          (= :jmp (:c it)) [(target ((:a it) 0))]
                          (= :jcc (:c it)) [(target ((:a it) 0)) fall]
                          (= :ijmp (:c it)) (if-let [ts (table-targets last-ins)]
                                              (vec (distinct (map (fn [s] (or (.get labels s) -2)) ts)))
                                              (conj all-label-blocks -2))
                          :else [fall]))))]
      {:starts starts :ends ends :succ succ :n nb})))

(defn block-use-def
  "[use def] masks of block b (use = read before written)."
  [items s e]
  (loop [i (dec e) live 0]
    (if (< i s)
      live
      (let [it (items i)]
        (if (= :ins (:k it))
          (recur (dec i) (bit-or (bit-and live (bit-not (long (:d it)))) (long (:u it))))
          (recur (dec i) live))))))

(defn liveness
  "Vector of live-out masks per block."
  [items {:keys [starts ends succ n]}]
  (let [n (long n)
        ;; transfer: live-in = f(live-out); compute gen/kill
        gen (long-array n) kill (long-array n)
        _ (dotimes [b n]
            (loop [i (dec (ends b)) g 0 k 0]
              (if (< i (starts b))
                (do (aset gen b g) (aset kill b k))
                (let [it (items i)]
                  (if (= :ins (:k it))
                    (let [d (long (:d it)) u (long (:u it))]
                      ;; partial defs are already in u
                      (recur (dec i) (bit-or (bit-and g (bit-not d)) u) (bit-or (bit-and k (bit-not u)) d)))
                    (recur (dec i) g k))))))
        live-in (long-array n)
        live-out (long-array n)]
    (loop [changed true]
      (when changed
        (recur
         (loop [b (dec n) ch false]
           (if (< b 0)
             ch
             (let [out (reduce (fn [^long acc s]
                                 (bit-or acc (case (int s) -1 exit-live-mask -2 all-mask (aget live-in (int s)))))
                               0 (succ b))
                   in (bit-or (aget gen b) (bit-and out (bit-not (aget kill b))))]
               (aset live-out b (long out))
               (if (not= in (aget live-in b))
                 (do (aset live-in b in) (recur (dec b) true))
                 (recur (dec b) ch))))))))
    live-out))

;; ---------------------------------------------------------------------------
;; Passes (each works on a vector of items, returns [items changed?])

(defn with-text-cleared [ins] (assoc ins :text nil))

(defn reanalyse [ins] (analyse (with-text-cleared ins)))

(def nop-item {:k :dir :text nil})

(defn dce
  "Delete pure instructions whose results are dead."
  [items bl live-out]
  (let [items (transient items) changed (volatile! false)]
    (dotimes [b (:n bl)]
      (loop [i (dec ((:ends bl) b)) live (aget ^longs live-out b)]
        (when (>= i ((:starts bl) b))
          (let [it (items i)]
            (if (= :ins (:k it))
              (let [d (long (:d it))]
                (if (and (:pure it) (zero? (bit-and d live)) (not (:mw it)) (not (zero? d)))
                  (do (assoc! items i nop-item) (vreset! changed true) (recur (dec i) live))
                  (recur (dec i) (bit-or (bit-and live (bit-not d)) (long (:u it))))))
              (recur (dec i) live))))))
    [(persistent! items) @changed]))

(defn full-def-dst
  "For a retargetable instruction (pure, single full register destination
   that is the last operand), the destination operand; else nil."
  [it]
  (when (and (= :ins (:k it)) (#{:move :ext :lea :sse2gpr :imul :movx} (:c it)))
    (let [a (:a it) dst (peek a)]
      (when (and (reg? dst) (>= (long (:w dst)) 32)
                 (case (:c it) :imul (= 3 (count a)) true)
                 (= (bit-and (long (:d it)) (bit-not flags-bit)) (bit (:f dst))))
        dst))))

(defn copy-move
  "For `movq %S, %D` / `movl %S, %D` (S != D): [S D width], else nil."
  [it]
  (when (and (= :ins (:k it)) (= :move (:c it)) (#{"movq" "movl"} (:op it)))
    (let [[s d] (:a it)]
      (when (and (reg? s) (reg? d) (= (:w s) (:w d)) (not= (:f s) (:f d))
                 (not (#{4 5} (:f s))) (not (#{4 5} (:f d))))
        [(:f s) (:f d) (:w s)]))))

(defn retarget
  "op ..., %A ; mov %A, %X   (A dead after the mov)  =>  op ..., %X"
  [items bl live-out]
  (let [items (transient items) changed (volatile! false)]
    (dotimes [b (:n bl)]
      (let [s ((:starts bl) b) e ((:ends bl) b)
            ;; live-after for each index in the block
            la (long-array (- e s))]
        (loop [i (dec e) live (aget ^longs live-out b)]
          (when (>= i s)
            (aset la (- i s) live)
            (let [it (items i)]
              (recur (dec i) (if (= :ins (:k it))
                               (bit-or (bit-and live (bit-not (long (:d it)))) (long (:u it)))
                               live)))))
        (loop [m s]
          (when (< m e)
            (when-let [[A X w] (copy-move (items m))]
              (when (zero? (bit-and (aget la (- m s)) (bit A)))
                ;; search backward for the definition of A
                (let [ab (bit A) xb (bit X)]
                  (loop [i (dec m)]
                    (when (>= i s)
                      (let [it (items i)]
                        (cond
                          (not= :ins (:k it)) (when (= :dir (:k it)) (recur (dec i)))
                          (not (zero? (bit-and (long (:d it)) ab)))
                          (when-let [dst (full-def-dst it)]
                            (when (and (= A (:f dst))
                                       (or (= 64 w) (= 32 (:w dst)))
                                       ;; I must not read X... it may (read happens first); fine
                                       )
                              (let [a (:a it)
                                    ni (reanalyse (assoc it :a (assoc a (dec (count a)) (assoc dst :f X))))]
                                (assoc! items i ni)
                                (assoc! items m nop-item)
                                (vreset! changed true))))
                          (not (zero? (bit-and (bit-or (long (:u it)) (long (:d it))) (bit-or ab xb)))) nil
                          :else (recur (dec i)))))))))
            (recur (inc m))))))
    [(persistent! items) @changed]))

(defn subst-operand
  "Replace register family D by S in a read operand o (fw: width of the copy fact)."
  [o D S fw]
  (case (:t o)
    :r (if (and (= D (:f o)) (<= (long (:w o)) (long fw))) (assoc o :f S) o)
    :m (if (= 64 fw)
         (cond-> o (= D (:b o)) (assoc :b S) (= D (:x o)) (assoc :x S))
         o)
    o))

(defn read-positions
  "Indices of operands that are pure reads (register or memory) and whose
   register can be renamed, for instruction it."
  [it]
  (let [n (count (:a it))]
    (case (:c it)
      (:move :ext :lea :sse2gpr) (if (= 2 n) [0] [])
      (:alu :aluc :cmov) [0]
      :cmp [0 1]
      :imul (case (int n) 3 [1] 2 [0] [])
      :push [0]
      [])))

(defn copy-prop
  "Forward copy propagation within blocks."
  [items bl]
  (let [items (transient items) changed (volatile! false)
        src (int-array 16) wid (int-array 16)]
    (dotimes [b (:n bl)]
      (java.util.Arrays/fill src (int -1))
      (loop [i ((:starts bl) b) nf 0]   ; nf: number of live facts (D -> src[D] of width wid[D])
        (when (< i ((:ends bl) b))
          (let [it (items i)]
            (if (not= :ins (:k it))
              (recur (inc i) nf)
              (let [sub (fn [o]
                          (case (:t o)
                            :r (let [D (int (:f o)) S (aget src D)]
                                 (if (and (>= S 0) (<= (long (:w o)) (aget wid D))) (assoc o :f S) o))
                            :m (let [bD (:b o) xD (:x o)
                                     o (if (and bD (>= (aget src (int bD)) 0) (= 64 (aget wid (int bD)))) (assoc o :b (aget src (int bD))) o)
                                     o (if (and xD (>= (aget src (int xD)) 0) (= 64 (aget wid (int xD)))) (assoc o :x (aget src (int xD))) o)]
                                 o)
                            o))
                    it2 (if (or (zero? nf) (= :barrier (:c it)))
                          it
                          (let [a (:a it)
                                rp (read-positions it)
                                a2 (reduce (fn [a p] (let [o (a p)] (if (= :r (:t o)) (assoc a p (sub o)) a))) a rp)
                                a2 (reduce (fn [a p] (let [o (a p)] (if (= :m (:t o)) (assoc a p (sub o)) a))) a2 (range (count a2)))]
                            (if (= a2 a) it (reanalyse (assoc it :a a2)))))
                    _ (when-not (identical? it2 it) (assoc! items i it2) (vreset! changed true))
                    d (long (:d it2))
                    nf (if (or (zero? d) (zero? nf))
                         nf
                         (loop [D 0 nf 0]
                           (if (< D 16)
                             (let [S (aget src D)]
                               (if (and (>= S 0) (or (bit-test d D) (bit-test d S)))
                                 (do (aset src D (int -1)) (recur (inc D) nf))
                                 (recur (inc D) (if (>= S 0) (inc nf) nf))))
                             nf)))
                    nf (if-let [[S D w] (copy-move it2)]
                         (do (when (< (aget src (int D)) 0) nil)
                             (let [had (>= (aget src (int D)) 0)]
                               (aset src (int D) (int S)) (aset wid (int D) (int w))
                               (if had nf (inc nf))))
                         nf)]
                (recur (inc i) nf)))))))
    [(persistent! items) @changed]))

(defn slot-forward
  "Within blocks: a load of a safe frame slot whose value is known to be held
   in a register becomes a register copy (or disappears)."
  [items bl safe?]
  (let [items (transient items) changed (volatile! false)
        ;; facts: parallel arrays of [offset size reg width]
        fo (long-array 64) fs (long-array 64) fr (long-array 64) fw (long-array 64)
        n (volatile! 0)
        kill-if (fn [pred]
                  (loop [k 0]
                    (when (< k (long @n))
                      (if (pred k)
                        (let [last (dec (long @n))]
                          (aset fo k (aget fo last)) (aset fs k (aget fs last))
                          (aset fr k (aget fr last)) (aset fw k (aget fw last))
                          (vreset! n last)
                          (recur k))
                        (recur (inc k))))))
        find (fn [off sz] (loop [k 0] (cond (>= k (long @n)) -1
                                             (and (= off (aget fo k)) (= sz (aget fs k))) k
                                             :else (recur (inc k)))))
        add! (fn [off sz r w]
               (when (< (long @n) 64)
                 (let [k (long @n)]
                   (aset fo k (long off)) (aset fs k (long sz)) (aset fr k (long r)) (aset fw k (long w))
                   (vreset! n (inc k)))))
        kill-reg (fn [^long d] (when (pos? (long @n)) (kill-if (fn [k] (bit-test d (aget fr k))))))
        kill-range (fn [^long lo ^long hi] (kill-if (fn [k] (and (< (aget fo k) hi) (< lo (+ (aget fo k) (aget fs k)))))))]
    (dotimes [b (:n bl)]
      (vreset! n 0)
      (loop [i ((:starts bl) b)]
        (when (< i ((:ends bl) b))
          (let [it (items i)]
            (when (= :ins (:k it))
              (let [c (:c it) a (:a it) op (:op it)
                    mv (and (= :move c) (= 2 (count a)) (or (= "movq" op) (= "movl" op)))
                    w (if (= "movq" op) 64 32)
                    sz (quot w 8)
                    s0 (when mv (a 0)) d0 (when mv (a 1))
                    load-slot (when (and mv (reg? d0) (not= 4 (:f d0)) (not= 5 (:f d0))) (:slot s0))
                    store-slot (when (and mv (reg? s0) (not= 4 (:f s0)) (not= 5 (:f s0))) (:slot d0))]
                (cond
                  (and load-slot (safe? load-slot sz))
                  (let [k (find load-slot sz)]
                    (if (>= k 0)
                      (let [R (aget fr k)]
                        (if (= R (:f d0))
                          (do (assoc! items i nop-item) (vreset! changed true))
                          (do (assoc! items i (reanalyse (assoc it :a [{:t :r :f R :w w} d0])))
                              (vreset! changed true)
                              (kill-reg (bit (:f d0)))
                              (add! load-slot sz (:f d0) w))))
                      (do (kill-reg (bit (:f d0)))
                          (add! load-slot sz (:f d0) w))))
                  (and store-slot (safe? store-slot sz))
                  (do (kill-range store-slot (+ store-slot sz))
                      (add! store-slot sz (:f s0) w))
                  (= :barrier c) (vreset! n 0)
                  :else
                  (let [d (long (:d it))]
                    ;; direct writes to frame slots kill overlapping facts
                    (when (and (:mw it) (pos? (long @n)))
                      (doseq [o a]
                        (when-let [off (:slot o)]
                          (kill-range (- (long off) 16) (+ (long off) 16)))))
                    (when-not (zero? d) (kill-reg d))))))
            (recur (inc i))))))
    [(persistent! items) @changed]))

;; ---------------------------------------------------------------------------

(def ^:private off-passes (or (System/getenv "CCC_AO_OFF") ""))
(defn- off? [p] (str/includes? off-passes p))
(def ^:private max-iter (if-let [m (System/getenv "CCC_AO_ITER")] (parse-long m) 1))

(declare optimize-body*)
(defn optimize-body
  "text: the body of one function. opts: {:exposed [[lo hi] ...] frame ranges
   whose address may escape}. Returns the optimised text."
  [^String text opts]
  (if (System/getenv "CCC_AO_TIME")
    (let [t0 (System/nanoTime) r (optimize-body* text opts) ms (quot (- (System/nanoTime) t0) 1000000)]
      (when (> ms 100) (binding [*out* *err*] (println "asmopt" (:name opts) ms "ms" (count (str/split text #"\n")) "lines")))
      r)
    (optimize-body* text opts)))

(defn optimize-body*
  [^String text opts]
  (let [lines (str/split text #"\n")
        items (mapv parse-line lines)
        exposed (:exposed opts)
        leaq-offs (set (keep (fn [it] (when (and (= :ins (:k it)) (= :lea (:c it))) (frame-slot ((:a it) 0)))) items))
        safe? (fn [off sz]
                (let [hi (+ off sz)]
                  (and (not (contains? leaq-offs off))
                       (not (some (fn [[lo h]] (and (< off h) (< lo hi))) exposed)))))
        no-slots (:no-slots opts)]
    (loop [items items iter 0]
      (let [bl (build-blocks items)
            [items c1] (if (or no-slots (off? "SLOT")) [items false] (slot-forward items bl safe?))
            [items c2] (if (off? "CP") [items false] (copy-prop items bl))
            lo (liveness items bl)
            [items c3] (if (off? "RT") [items false] (retarget items bl lo))
            lo (liveness items bl)
            [items c4] (if (off? "DCE") [items false] (dce items bl lo))
            ;; compact
            items (filterv #(not (and (= :dir (:k %)) (nil? (:text %)))) items)]
        (if (and (or c1 c2 c3 c4) (< iter max-iter))
          (recur items (inc iter))
          (let [sb (StringBuilder.)]
            (doseq [it items]
              (.append sb ^String (render it))
              (.append sb "\n"))
            (str sb)))))))
