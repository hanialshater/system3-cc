(ns ccc.codegen
  "x86-64 AT&T assembly generation from the typed AST (stack-machine style)."
  (:refer-clojure :exclude [integer? pop! load case let fn defn defn- if-let when-let and or loop doseq for binding cond-> some-> dotimes letfn doto])
  (:require [ccc.fastcase :refer [case let fn defn defn- if-let when-let and or loop doseq for binding cond-> some-> dotimes letfn doto]]
            [ccc.types :as t]
            [ccc.mach :as mach]
            [clojure.string :as str]))

(def ^:dynamic ^StringBuilder *asm* nil)
(def ^:dynamic *depth* nil)     ; volatile: number of 8-byte pushes outstanding
(def ^:dynamic *offsets* nil)   ; map var-id -> rbp offset
(def ^:dynamic *fn* nil)        ; current function map
(def ^:dynamic *defined* nil)   ; set of labels defined in this TU
(def ^:dynamic *counter* nil)   ; volatile
(def ^:dynamic *fninfo* nil)
(def ^:dynamic *overaligned* #{})
(def ^:dynamic *ldconsts* nil)
(def ^:dynamic *regvars* {})
(def ^:dynamic *temp-free* nil)  ; volatile vector of free callee-saved register indices
(def ^:dynamic *temp-used* nil)  ; volatile set of register indices used as temporaries

(defn alloc-temp!
  "A free callee-saved register for holding a value across code, or nil."
  []
  (when-let [fr *temp-free*]
    (when-let [i (peek @fr)]
      (vswap! fr pop)
      (vswap! *temp-used* conj i)
      i)))

(defn free-temp! [i] (vswap! *temp-free* conj i))

(declare callee-regs)

(def ^:dynamic *r89-slots* nil)    ; [[reg slot] ...] for r8/r9 holding variables (saved around calls)
(def ^:dynamic *loop-depth* nil)   ; volatile: nesting of loops being generated
(def ^:dynamic *spill-free* nil)   ; volatile vector of free spill slot indices
(def ^:dynamic *spill-max* nil)    ; volatile: number of spill slots used
(def ^:dynamic *spill-base* 0)     ; frame offset below which spill slots live

(defn alloc-holder!
  "A place to hold a 64-bit value across arbitrary code: [operand release-fn].
   Outside loops a frame slot (no prologue cost); inside loops a callee-saved
   register if one is free."
  []
  (let [slot (fn []
               (let [k (if (seq @*spill-free*)
                         (let [k (peek @*spill-free*)] (vswap! *spill-free* pop) k)
                         (let [k @*spill-max*] (vswap! *spill-max* inc) k))]
                 [(str (- (+ *spill-base* (* 8 (inc k)))) "(%rbp)")
                  #(vswap! *spill-free* conj k)]))]
    (if (and *loop-depth* (pos? @*loop-depth*))
      (if-let [ti (alloc-temp!)]
        [((nth callee-regs ti) 0) #(free-temp! ti)]
        (slot))
      (slot))))

;; caller-saved registers that code for side-effect-free expressions never uses
(def ^:dynamic *scratch-free* nil)   ; volatile list of free scratch registers    ; var id -> index into callee-regs (register-allocated locals)

(def callee-regs [["%rbx" "%ebx" "%bx" "%bl"] ["%r12" "%r12d" "%r12w" "%r12b"]
                  ["%r13" "%r13d" "%r13w" "%r13b"] ["%r14" "%r14d" "%r14w" "%r14b"]
                  ["%r15" "%r15d" "%r15w" "%r15b"]
                  ;; caller-saved, only for variables of scopes without calls
                  ["%r8" "%r8d" "%r8w" "%r8b"] ["%r9" "%r9d" "%r9w" "%r9b"]
                  ;; scratch registers, only for variables of call-free scopes (then not scratch)
                  ["%r10" "%r10d" "%r10w" "%r10b"] ["%r11" "%r11d" "%r11w" "%r11b"]])
(def n-callee-saved 5)  ; volatile vector of [label mantissa exp] long double constants ; ids of locals aligned beyond 16, reached through a pointer slot    ; {:gp n :fp n :stack-bytes n :ret-ptr-off n}

(defn emit [^String s] (.append *asm* s) (.append *asm* "\n"))
(defmacro emitf [& parts] `(emit (str ~@parts)))

(defn split-lines*
  "clojure.string/split-lines without a regex: pieces between \\n separators,
   a \\r before a separator dropped, trailing empty pieces dropped (a string
   without separator is returned whole)."
  [^String s]
  (if (neg? (.indexOf s "\n"))
    [s]
    (let [out (java.util.ArrayList.)
          n (.length s)]
      (loop [start 0]
        (let [i (.indexOf s "\n" (int start))]
          (if (neg? i)
            (.add out (subs s start n))
            (let [e (if (and (> i start) (= \return (.charAt s (int (dec i))))) (dec i) i)]
              (.add out (subs s start e))
              (recur (inc i))))))
      (loop [k (.size out)]
        (if (and (pos? k) (= "" (.get out (dec k))))
          (do (.remove out (int (dec k))) (recur (dec k)))))
      (vec out))))

(defn new-label [] (str ".L.cg." (vswap! *counter* inc)))

(defn cerr [msg] (throw (ex-info (str "error: " msg) {:type :compile-error})))

(def argreg64 ["%rdi" "%rsi" "%rdx" "%rcx" "%r8" "%r9"])
(def argreg32 ["%edi" "%esi" "%edx" "%ecx" "%r8d" "%r9d"])
(def argreg16 ["%di" "%si" "%dx" "%cx" "%r8w" "%r9w"])
(def argreg8 ["%dil" "%sil" "%dl" "%cl" "%r8b" "%r9b"])

(defn push! [] (emit "  pushq %rax") (vswap! *depth* inc))
(defn pop! [reg] (emitf "  popq " reg) (vswap! *depth* dec))
(defn pushf! [] (emit "  subq $8, %rsp") (emit "  movsd %xmm0, (%rsp)") (vswap! *depth* inc))
(defn popf! [n] (emitf "  movsd (%rsp), %xmm" n) (emit "  addq $8, %rsp") (vswap! *depth* dec))

(defn align-to [n a] (* (quot (+ n a -1) a) a))

(defn imm32? [v] (<= -2147483648 v 2147483647))

;; ---------------------------------------------------------------------------
;; Struct classification (SysV)

(defn has-flonum?
  "True if every scalar within [lo,hi) of ty (placed at offset) is float/double."
  [ty lo hi offset]
  (case (:kind ty)
    (:struct :union)
    (every? (fn [m] (has-flonum? (:type m) lo hi (+ offset (:offset m))))
            (:members (t/struct-info ty)))
    :array
    (let [bsz (t/type-size (:base ty))]
      (every? (fn [i] (has-flonum? (:base ty) lo hi (+ offset (* i bsz))))
              (range (max 0 (:len ty)))))
    (or (< offset lo) (<= hi offset) (t/flonum? ty))))

(defn has-ldouble? [ty]
  (case (:kind ty)
    :ldouble true
    (:struct :union) (some #(has-ldouble? (:type %)) (:members (t/struct-info ty)))
    :array (has-ldouble? (:base ty))
    false))

(defn struct-classes
  "Vector of :sse / :int per eightbyte, or nil if passed in memory."
  [ty]
  (let [sz (t/type-size ty)]
    (when (and (<= sz 16) (not (has-ldouble? ty)))
      (vec (for [i (range (quot (+ sz 7) 8))]
             (if (has-flonum? ty (* 8 i) (* 8 (inc i)) 0) :sse :int))))))

;; ---------------------------------------------------------------------------
;; Loads, stores, copies

(defn load [ty]
  (case (:kind ty)
    (:array :struct :union :func :vla) nil
    :float (emit "  movss (%rax), %xmm0")
    :double (emit "  movsd (%rax), %xmm0")
    :ldouble (emit "  fldt (%rax)")
    (let [sz (t/type-size ty) u (t/unsigned? ty)]
      (case (int sz)
        1 (emit (if u "  movzbl (%rax), %eax" "  movsbl (%rax), %eax"))
        2 (emit (if u "  movzwl (%rax), %eax" "  movswl (%rax), %eax"))
        4 (emit (if u "  movl (%rax), %eax" "  movslq (%rax), %rax"))
        8 (emit "  movq (%rax), %rax")))))

(defn emit-copy
  "Copy size bytes from (src) to (dst); clobbers %rdx."
  [src dst size]
  (loop [off 0]
    (let [rem (- size off)]
      (cond
        (>= rem 8) (do (emitf "  movq " off "(" src "), %rdx") (emitf "  movq %rdx, " off "(" dst ")") (recur (+ off 8)))
        (>= rem 4) (do (emitf "  movl " off "(" src "), %edx") (emitf "  movl %edx, " off "(" dst ")") (recur (+ off 4)))
        (>= rem 2) (do (emitf "  movw " off "(" src "), %dx") (emitf "  movw %dx, " off "(" dst ")") (recur (+ off 2)))
        (>= rem 1) (do (emitf "  movb " off "(" src "), %dl") (emitf "  movb %dl, " off "(" dst ")") (recur (+ off 1)))
        :else nil))))

(defn store
  "Store value (%rax / %xmm0) to address popped from stack."
  [ty]
  (pop! "%rdi")
  (case (:kind ty)
    (:struct :union) (emit-copy "%rax" "%rdi" (t/type-size ty))
    :float (emit "  movss %xmm0, (%rdi)")
    :double (emit "  movsd %xmm0, (%rdi)")
    :ldouble (do (emit "  fstpt (%rdi)") (emit "  fldt (%rdi)"))
    (case (int (t/type-size ty))
      1 (emit "  movb %al, (%rdi)")
      2 (emit "  movw %ax, (%rdi)")
      4 (emit "  movl %eax, (%rdi)")
      8 (emit "  movq %rax, (%rdi)"))))

;; ---------------------------------------------------------------------------
;; Casts

(defn tid [ty]
  (case (:kind ty)
    :ldouble :f80
    :float :f32
    :double :f64
    (:ptr :array :func :vla) :u64
    (let [u (t/unsigned? ty)]
      (case (int (t/type-size ty))
        1 (if u :u8 :i8)
        2 (if u :u16 :i16)
        4 (if u :u32 :i32)
        8 (if u :u64 :i64)))))

(defn cmp-zero
  "Set flags so that ZF=1 iff value is zero."
  [ty]
  (case (:kind ty)
    :float (do (emit "  xorps %xmm1, %xmm1") (emit "  ucomiss %xmm1, %xmm0")
               (emit "  setne %al") (emit "  setp %dl") (emit "  orb %dl, %al") (emit "  testb %al, %al"))
    :double (do (emit "  xorpd %xmm1, %xmm1") (emit "  ucomisd %xmm1, %xmm0")
                (emit "  setne %al") (emit "  setp %dl") (emit "  orb %dl, %al") (emit "  testb %al, %al"))
    :ldouble (do (emit "  fldz") (emit "  fucomip %st(1), %st") (emit "  fstp %st(0)")
                 (emit "  setne %al") (emit "  setp %dl") (emit "  orb %dl, %al") (emit "  testb %al, %al"))
    (if (and (t/integer? ty) (<= (t/type-size ty) 4))
      (emit "  cmpl $0, %eax")
      (emit "  cmpq $0, %rax"))))

(defn gen-cast-ld
  "Conversions to and from long double (value on the x87 stack)."
  [from to]
  (let [f (tid from) tt (tid to)]
    (cond
      (= f tt) nil
      (= tt :f80)
      (case f
        :f32 (do (emit "  subq $8, %rsp") (emit "  movss %xmm0, (%rsp)") (emit "  flds (%rsp)") (emit "  addq $8, %rsp"))
        :f64 (do (emit "  subq $8, %rsp") (emit "  movsd %xmm0, (%rsp)") (emit "  fldl (%rsp)") (emit "  addq $8, %rsp"))
        (do (case f
              (:i8 :i16 :i32) (emit "  movslq %eax, %rax")
              (:u8 :u16 :u32) (emit "  movl %eax, %eax")
              nil)
            (emit "  subq $8, %rsp") (emit "  movq %rax, (%rsp)") (emit "  fildq (%rsp)")
            (when (= f :u64)
              (let [l (new-label)]
                (emit "  testq %rax, %rax")
                (emitf "  jns " l)
                (emit "  movl $0x5f800000, (%rsp)")   ; 2^64 as float
                (emit "  fadds (%rsp)")
                (emitf l ":")))
            (emit "  addq $8, %rsp")))
      :else ; from :f80
      (case tt
        :f32 (do (emit "  subq $8, %rsp") (emit "  fstps (%rsp)") (emit "  movss (%rsp), %xmm0") (emit "  addq $8, %rsp"))
        :f64 (do (emit "  subq $8, %rsp") (emit "  fstpl (%rsp)") (emit "  movsd (%rsp), %xmm0") (emit "  addq $8, %rsp"))
        :u64 (let [l-small (new-label) l-done (new-label)]
               (emit "  subq $8, %rsp")
               (emit "  movl $0x5f000000, (%rsp)")    ; 2^63 as float
               (emit "  flds (%rsp)")
               (emit "  fucomip %st(1), %st")
               (emitf "  ja " l-small)
               (emit "  fsubs (%rsp)")
               (emit "  fisttpq (%rsp)")
               (emit "  movq (%rsp), %rax")
               (emit "  btcq $63, %rax")
               (emitf "  jmp " l-done)
               (emitf l-small ":")
               (emit "  fisttpq (%rsp)")
               (emit "  movq (%rsp), %rax")
               (emitf l-done ":")
               (emit "  addq $8, %rsp"))
        (do (emit "  subq $8, %rsp") (emit "  fisttpq (%rsp)") (emit "  movq (%rsp), %rax") (emit "  addq $8, %rsp")
            (case tt
              :i8 (emit "  movsbl %al, %eax") :u8 (emit "  movzbl %al, %eax")
              :i16 (emit "  movswl %ax, %eax") :u16 (emit "  movzwl %ax, %eax")
              nil))))))

(defn gen-cast [from to]
  (cond
    (and (= :void (:kind to)) (t/ldouble? from)) (emit "  fstp %st(0)")
    (= :void (:kind to)) nil
    (and (or (t/ldouble? from) (t/ldouble? to)) (not= :bool (:kind to))) (gen-cast-ld from to)
    (= :bool (:kind to))
    (do (cmp-zero from) (emit "  setne %al") (emit "  movzbl %al, %eax"))
    (t/struct? to) nil
    :else
    (let [f (tid from) tt (tid to)]
      (when (not= f tt)
        (let [fint (not (#{:f32 :f64} f))
              tint (not (#{:f32 :f64} tt))]
          (cond
            (and fint tint)
            (case tt
              :i8 (emit "  movsbl %al, %eax")
              :u8 (emit "  movzbl %al, %eax")
              :i16 (emit "  movswl %ax, %eax")
              :u16 (emit "  movzwl %ax, %eax")
              (:i32 :u32) nil
              (:i64 :u64) (case f
                            (:i8 :i16 :i32) (emit "  movslq %eax, %rax")
                            (:u8 :u16 :u32) (emit "  movl %eax, %eax")
                            nil))
            (and fint (not tint))
            (let [s (if (= tt :f32) "ss" "sd")]
              (case f
                (:i8 :i16 :i32 :u8 :u16) (emitf "  cvtsi2" s "l %eax, %xmm0")
                :u32 (do (emit "  movl %eax, %eax") (emitf "  cvtsi2" s "q %rax, %xmm0"))
                :i64 (emitf "  cvtsi2" s "q %rax, %xmm0")
                :u64 (let [l1 (new-label) l2 (new-label)]
                       (emit "  testq %rax, %rax")
                       (emitf "  js " l1)
                       (emitf "  cvtsi2" s "q %rax, %xmm0")
                       (emitf "  jmp " l2)
                       (emitf l1 ":")
                       (emit "  movq %rax, %rdi")
                       (emit "  andl $1, %eax")
                       (emit "  shrq %rdi")
                       (emit "  orq %rax, %rdi")
                       (emitf "  cvtsi2" s "q %rdi, %xmm0")
                       (emitf "  add" s " %xmm0, %xmm0")
                       (emitf l2 ":"))))
            (and (not fint) tint)
            (let [s (if (= f :f32) "ss" "sd")]
              (case tt
                (:i8 :i16 :i32 :u8 :u16)
                (do (emitf "  cvtt" s "2sil %xmm0, %eax")
                    (case tt :i8 (emit "  movsbl %al, %eax") :u8 (emit "  movzbl %al, %eax")
                          :i16 (emit "  movswl %ax, %eax") :u16 (emit "  movzwl %ax, %eax") nil))
                (:u32 :i64) (emitf "  cvtt" s "2siq %xmm0, %rax")
                :u64 (do (emitf "  cvtt" s "2siq %xmm0, %rcx")
                         (emit "  movq %rcx, %rdx")
                         (if (= f :f32)
                           (do (emit "  movl $0x5f000000, %eax") (emit "  movd %eax, %xmm1") (emit "  subss %xmm1, %xmm0"))
                           (do (emit "  movabsq $0x43e0000000000000, %rax") (emit "  movq %rax, %xmm1") (emit "  subsd %xmm1, %xmm0")))
                         (emitf "  cvtt" s "2siq %xmm0, %rax")
                         (emit "  sarq $63, %rdx")
                         (emit "  andq %rdx, %rax")
                         (emit "  orq %rcx, %rax"))))
            :else
            (if (= f :f32) (emit "  cvtss2sd %xmm0, %xmm0") (emit "  cvtsd2ss %xmm0, %xmm0"))))))))

;; ---------------------------------------------------------------------------
;; Expressions

(declare gen-memset-inline capture rax-only? gen-sra-copy gen-expr gen-call-finish gen-stmt var-offset gen-discard gen-assign-mem int-operand addr-operand materialize gen-binary-int* cheap-index? index-into-rdi sfx-of fixed-mem-op render-mem)

(defn local-addr
  "Emit the address of local v into reg."
  [v reg]
  (if (or (:vla-ptr v) (contains? *overaligned* (:id v)))
    (emitf "  movq " (var-offset v) "(%rbp), " reg)
    (emitf "  leaq " (var-offset v) "(%rbp), " reg)))

(defn var-offset [v]
  (or (get *offsets* (:id v)) (cerr (str "no offset for variable " (:name v)))))

(defn vreg
  "Register names of a register-allocated variable, or nil."
  [v]
  (when-let [i (get *regvars* (:id v))] (nth callee-regs i)))

(defn var-mem-op
  "Memory operand for a scalar variable that lives in memory, or nil."
  [v]
  (cond
    (vreg v) nil
    (:local v) (when-not (or (:vla-ptr v) (contains? *overaligned* (:id v)))
                 (str (var-offset v) "(%rbp)"))
    (contains? *defined* (:label v)) (str (:label v) "(%rip)")
    :else nil))

(defn load-op
  "Load a value of type ty from memory operand op into %rax/%xmm0/st(0)."
  [ty op]
  (case (:kind ty)
    :float (emitf "  movss " op ", %xmm0")
    :double (emitf "  movsd " op ", %xmm0")
    :ldouble (emitf "  fldt " op)
    (let [sz (t/type-size ty) u (t/unsigned? ty)]
      (case (int sz)
        1 (emitf (if u "  movzbl " "  movsbl ") op ", %eax")
        2 (emitf (if u "  movzwl " "  movswl ") op ", %eax")
        4 (if u (emitf "  movl " op ", %eax") (emitf "  movslq " op ", %rax"))
        8 (emitf "  movq " op ", %rax")))))

(defn store-op
  "Store the scalar value in %rax/%xmm0/st(0) to memory operand op."
  [ty op]
  (case (:kind ty)
    :float (emitf "  movss %xmm0, " op)
    :double (emitf "  movsd %xmm0, " op)
    :ldouble (do (emitf "  fstpt " op) (emitf "  fldt " op))
    (case (int (t/type-size ty))
      1 (emitf "  movb %al, " op)
      2 (emitf "  movw %ax, " op)
      4 (emitf "  movl %eax, " op)
      8 (emitf "  movq %rax, " op))))

(defn reg-load
  "Move register variable v into %rax."
  [v]
  (let [r (vreg v)]
    (if (= 8 (t/type-size (:type v)))
      (emitf "  movq " (r 0) ", %rax")
      (emitf "  movl " (r 1) ", %eax"))))

(defn reg-store
  "Move %rax into register variable v (value already converted to v's type)."
  [v]
  (let [r (vreg v)]
    (if (= 8 (t/type-size (:type v)))
      (emitf "  movq %rax, " (r 0))
      (emitf "  movl %eax, " (r 1)))))

(defn scalar-var? [n]
  (and (= :var (:op n)) (t/scalar? (:type n))))

(defn int-operand
  "An instruction operand (immediate, register or memory) for integer node n
   used at operand width w (4 or 8), or nil if n needs computing."
  [n w]
  (case (:op n)
    :num (when (and (not (:fval n)) (or (t/integer? (:type n)) (t/ptr? (:type n))))
           (let [v (:val n)]
             (when (if (= w 8) (imm32? v) (<= -2147483648 v 4294967295))
               (str "$" (if (= w 8) v (long (unchecked-int v)))))))
    :var (let [v (:var n) ty (:type n)]
           (when (and (or (t/integer? ty) (t/ptr? ty)) (= w (t/type-size ty)))
             (if-let [r (vreg v)]
               (if (= w 8) (r 0) (r 1))
               (var-mem-op v))))
    :member (let [ty (:type n)]
              (when (and (or (t/integer? ty) (t/ptr? ty)) (= w (t/type-size ty))
                         (not (:bitfield (:member n))))
                (fixed-mem-op n)))
    :deref (let [ty (:type n)]
             (when (and (or (t/integer? ty) (t/ptr? ty)) (= w (t/type-size ty)))
               (fixed-mem-op n)))
    ;; a cast between integer/pointer types of the same size changes no bits
    :cast (let [ty (:type n) e (:expr n) ft (:type e)]
            (when (and (or (t/integer? ty) (t/ptr? ty)) (or (t/integer? ft) (t/ptr? ft))
                       (not= :bool (:kind ty)) (not= :bool (:kind ft))
                       (= w (t/type-size ty)) (= w (t/type-size ft)))
              (int-operand e w)))
    nil))

(defn fixed-mem-op
  "Memory operand string for a member chain on a memory variable, or nil."
  [n]
  (letfn [(go [n]
            (case (:op n)
              :var (let [v (:var n)]
                     (when-let [op (var-mem-op v)]
                       (if (:local v) {:disp (var-offset v) :base "%rbp"} {:sym (:label v) :base "%rip"})))
              :member (when-let [m (go (:expr n))] (update m :disp (fnil + 0) (:offset (:member n))))
              ;; *p / p->m with pointer p in a register
              :deref (let [e (:expr n)]
                       (when (and (= :var (:op e)) (t/ptr? (:type e)))
                         (when-let [r (vreg (:var e))] {:base (r 0)})))
              nil))]
    (when-let [m (go n)] (render-mem m))))

(defn gen-addr [n]
  (case (:op n)
    :var (let [v (:var n)]
           (when (vreg v) (cerr (str "internal: address of register variable " (:name v))))
           (if (:local v)
             (local-addr v "%rax")
             (if (contains? *defined* (:label v))
               (emitf "  leaq " (:label v) "(%rip), %rax")
               (emitf "  movq " (:label v) "@GOTPCREL(%rip), %rax"))))
    :deref (materialize (addr-operand n))
    :comma (do (gen-expr (:lhs n)) (gen-addr (:rhs n)))
    :member (materialize (addr-operand n))
    (:call :assign :cond :stmt-expr :va-arg)
    (if (t/struct? (:type n)) (gen-expr n) (cerr "not an lvalue"))
    :cast (if (t/struct? (:type n)) (gen-addr (:expr n)) (cerr "not an lvalue"))
    (cerr (str "not an lvalue (" (:op n) ")"))))

(defn render-mem
  "Render a memory operand map {:disp :sym :base :index :scale}."
  [{:keys [disp sym base index scale]}]
  (let [d (str (or sym "") (cond (nil? sym) (if (and disp (not (zero? disp))) disp "")
                                 (and disp (pos? disp)) (str "+" disp)
                                 (and disp (neg? disp)) disp
                                 :else ""))]
    (cond
      (= base "%rip") (str d "(%rip)")
      index (str d "(" (or base "") "," index "," scale ")")
      :else (str d "(" base ")"))))

(def pure-ops #{:num :var :member :deref :addr :cast :neg :bitnot :not :logand :logor :cond :comma
                :add :sub :mul :div :mod :bitand :bitor :bitxor :shl :shr :eq :ne :lt :le :gt :ge
                :label-addr :bitop :bswap :fabs})

(defn pure?
  "True when evaluating n has no side effects."
  [n]
  (and (map? n)
       (contains? pure-ops (:op n))
       (not (:volatile (:type n)))
       (every? (fn [k] (let [c (get n k)] (or (nil? c) (pure? c))))
               [:lhs :rhs :expr :cond :then :els])))

(defn index-into-rdi
  "Evaluate a long index expression into %rdi without clobbering registers other than %rax."
  [x]
  (let [inner (when (and (= :cast (:op x)) (t/integer? (:type (:expr x)))) (:expr x))
        ity (when inner (:type inner))]
    (cond
      ;; i++ / i-- on a signed int register variable
      (and inner (= :incdec (:op inner)) (not (:pre inner)) (= 4 (t/type-size ity)) (not (t/unsigned? ity))
           (= :var (:op (:expr inner))) (vreg (:var (:expr inner))))
      (let [r (vreg (:var (:expr inner)))]
        (emitf "  movslq " (r 1) ", %rdi")
        (emitf "  addl $" (:delta inner) ", " (r 1)))
      (and inner (= :incdec (:op inner)) (:pre inner) (= 4 (t/type-size ity)) (not (t/unsigned? ity))
           (= :var (:op (:expr inner))) (vreg (:var (:expr inner))))
      (let [r (vreg (:var (:expr inner)))]
        (emitf "  addl $" (:delta inner) ", " (r 1))
        (emitf "  movslq " (r 1) ", %rdi"))
      (and inner (= 4 (t/type-size ity)) (int-operand inner 4))
      (emitf (if (t/unsigned? ity) "  movl " "  movslq ") (int-operand inner 4) (if (t/unsigned? ity) ", %edi" ", %rdi"))
      (int-operand x 8) (emitf "  movq " (int-operand x 8) ", %rdi")
      (and inner (= 4 (t/type-size ity)))
      (do (gen-expr inner)
          (emit (if (t/unsigned? ity) "  movl %eax, %edi" "  movslq %eax, %rdi")))
      :else (do (gen-expr x) (emit "  movq %rax, %rdi")))))

(declare addr-operand fixed-addr?)

(defn cheap-index?
  "Index expressions that index-into-rdi loads without touching %rax."
  [x]
  (let [inner (when (and (= :cast (:op x)) (t/integer? (:type (:expr x)))) (:expr x))]
    (boolean (or (and inner (= 4 (t/type-size (:type inner))) (int-operand inner 4))
                 (int-operand x 8)))))

(defn rdi-base?
  "Pointer/array nodes whose base register is known without computing anything."
  [p]
  (let [ty (:type p)]
    (and (= :var (:op p))
         (or (and (vreg (:var p)) (= 8 (t/type-size ty)))
             (and (t/array? ty) (not= :vla (:kind ty)) (:local (:var p)) (boolean (var-mem-op (:var p))))))))

(defn split-scaled [x]
  (if (and (= :mul (:op x)) (= :num (:op (:rhs x))) (#{1 2 4 8} (:val (:rhs x))))
    [(:lhs x) (:val (:rhs x))]
    [x 1]))

(defn rdi-addr?
  "Scalar loads whose address operand is computed using only %rdi."
  [n]
  (and (#{:deref :member} (:op n))
       (t/scalar? (:type n))
       (pure? n)
       (if (= :member (:op n))
         (and (not (:bitfield (:member n))) (fixed-addr? (:expr n)))
         (let [e (:expr n)]
           (or (and (= :var (:op e)) (vreg (:var e)) (= 8 (t/type-size (:type e))))
               (and (= :add (:op e)) (t/ptr? (:type e)) (rdi-base? (:lhs e))
                    (let [[idx scale] (split-scaled (:rhs e))
                          inner (when (and (= :cast (:op idx)) (t/integer? (:type (:expr idx)))
                                           (not (t/unsigned? (:type (:expr idx)))))
                                  (:expr idx))]
                      (or (= :num (:op (:rhs e))) (cheap-index? idx)
                          (and inner (#{:add :sub} (:op inner)) (= :num (:op (:rhs inner)))
                               (imm32? (* scale (:val (:rhs inner))))
                               (cheap-index? (assoc idx :expr (:lhs inner))))))))))))

(defn simple-base?
  "Pointer expressions whose base register is obtained without using %rdi."
  [p]
  (let [ty (:type p)]
    (and (= :var (:op p))
         (or (and (t/array? ty) (not= :vla (:kind ty)) (var-mem-op (:var p)))
             (and (vreg (:var p)) (= 8 (t/type-size ty)))
             (and (var-mem-op (:var p)) (t/ptr? ty))))))

(defn pointer-base
  "Get pointer-valued node p into a base register; returns {:base reg} or a
   mem map for array-typed variables (whose address is known)."
  [p]
  (let [ty (:type p)]
    (cond
      (and (= :var (:op p)) (t/array? ty) (not= :vla (:kind ty)))
      (let [v (:var p)]
        (cond
          (and (:local v) (var-mem-op v)) {:disp (var-offset v) :base "%rbp"}
          (and (not (:local v)) (var-mem-op v)) (do (emitf "  leaq " (:label v) "(%rip), %rax") {:base "%rax"})
          :else (do (gen-addr p) {:base "%rax"})))
      (and (= :var (:op p)) (vreg (:var p)) (= 8 (t/type-size ty)))
      {:base ((vreg (:var p)) 0)}
      (and (= :var (:op p)) (var-mem-op (:var p)) (t/ptr? ty))
      (do (emitf "  movq " (var-mem-op (:var p)) ", %rax") {:base "%rax"})
      (and (#{:member :deref} (:op p)) (t/array? ty))
      (let [m (addr-operand p)]
        (if (or (:index m) (= "%rip" (:base m)))
          (do (emitf "  leaq " (render-mem m) ", %rax") {:base "%rax"})
          m))
      :else (do (gen-expr p) {:base "%rax"}))))

(defn addr-operand
  "Emit code computing the address of lvalue n; returns a memory operand map.
   Only %rax and %rdi are used (plus the stack for complex bases)."
  [n]
  (case (:op n)
    :var (let [v (:var n)]
           (if (var-mem-op v)
             (if (:local v) {:disp (var-offset v) :base "%rbp"} {:sym (:label v) :base "%rip"})
             (do (gen-addr n) {:base "%rax"})))
    :member (let [m (addr-operand (:expr n))
                  off (:offset (:member n))]
              (if (and (= "%rip" (:base m)) (:index m))
                (do (emitf "  leaq " (render-mem m) ", %rax") {:disp off :base "%rax"})
                (update m :disp (fnil + 0) off)))
    :deref (let [e (:expr n)]
             (cond
               ;; p + i*s
               (and (#{:add :sub} (:op e)) (t/ptr? (:type e)))
               (let [p (:lhs e) x (:rhs e)
                     [idx scale] (cond
                                   (and (= :mul (:op x)) (= :num (:op (:rhs x))) (#{1 2 4 8} (:val (:rhs x))))
                                   [(:lhs x) (:val (:rhs x))]
                                   :else [x 1])
                     ;; i+c with signed int i: fold c*scale into the displacement
                     [idx extra] (let [inner (when (and (= :cast (:op idx)) (t/integer? (:type (:expr idx)))
                                                        (not (t/unsigned? (:type (:expr idx)))))
                                               (:expr idx))]
                                   (if (and inner (#{:add :sub} (:op inner)) (= :num (:op (:rhs inner)))
                                            (imm32? (* scale (:val (:rhs inner)))))
                                     [(assoc idx :expr (:lhs inner))
                                      (* scale (if (= :add (:op inner)) (:val (:rhs inner)) (- (:val (:rhs inner)))))]
                                     [idx 0]))]
                 (cond
                   (and (= :num (:op x)) (imm32? (:val x)))
                   (update (pointer-base p) :disp (fnil + 0) (if (= :sub (:op e)) (- (:val x)) (:val x)))
                   (= :sub (:op e)) (do (gen-expr e) {:base "%rax"})
                   ;; p[i] with i computed: keep i in %rax and use it as the index register
                   (and (pure? p) (simple-base? p) (= :var (:op p)) (not (cheap-index? idx))
                        (or (t/ptr? (:type p)) (:local (:var p))))
                   (let [[_ text] (capture #(index-into-rdi idx))
                         lines (split-lines* text)
                         fix (case (peek lines)
                               "  movq %rax, %rdi" ""
                               "  movslq %eax, %rdi" "  movslq %eax, %rax"
                               "  movl %eax, %edi" "  movl %eax, %eax"
                               nil)]
                     (if (nil? fix)
                       (do (.append ^StringBuilder *asm* text)
                           (-> (pointer-base p) (assoc :index "%rdi" :scale scale) (update :disp (fnil + 0) extra)))
                       (let [v (:var p)]
                         (doseq [l (pop lines)] (emit l))
                         (when (seq fix) (emit fix))
                         (cond
                           (and (t/ptr? (:type p)) (vreg v))
                           {:base ((vreg v) 0) :index "%rax" :scale scale :disp extra}
                           (t/ptr? (:type p))
                           (if (= 1 scale)
                             (do (emitf "  addq " (var-mem-op v) ", %rax") {:base "%rax" :disp extra})
                             (do (emitf "  movq " (var-mem-op v) ", %rdi")
                                 {:base "%rdi" :index "%rax" :scale scale :disp extra}))
                           :else
                           {:disp (+ (var-offset v) extra) :base "%rbp" :index "%rax" :scale scale}))))
                   (and (pure? p) (simple-base? p))
                   (do (index-into-rdi idx)
                       (-> (pointer-base p) (assoc :index "%rdi" :scale scale) (update :disp (fnil + 0) extra)))
                   (pure? p)
                   (let [b (pointer-base p)
                         b (if (or (:index b) (= "%rip" (:base b)))
                             (do (emitf "  leaq " (render-mem b) ", %rax") {:base "%rax"})
                             b)]
                     (if (or (cheap-index? idx) (not= "%rax" (:base b)))
                       (index-into-rdi idx)
                       (do (push!) (index-into-rdi idx) (pop! "%rax")))
                     (-> b (assoc :index "%rdi" :scale scale) (update :disp (fnil + 0) extra)))
                   :else
                   (do (gen-expr p) (push!) (index-into-rdi idx) (pop! "%rax")
                       {:base "%rax" :index "%rdi" :scale scale :disp extra})))
               (and (= :var (:op e)) (vreg (:var e)) (= 8 (t/type-size (:type e))))
               {:base ((vreg (:var e)) 0)}
               (and (= :var (:op e)) (var-mem-op (:var e)) (t/ptr? (:type e)))
               (do (emitf "  movq " (var-mem-op (:var e)) ", %rax") {:base "%rax"})
               :else (do (gen-expr e) {:base "%rax"})))
    (do (gen-addr n) {:base "%rax"})))

(defn materialize
  "Put the address described by operand map m into %rax."
  [m]
  (when-not (and (= "%rax" (:base m)) (not (:index m)) (or (nil? (:disp m)) (zero? (:disp m))) (nil? (:sym m)))
    (emitf "  leaq " (render-mem m) ", %rax")))

(defn simple-lvalue-op?
  "Lvalues whose loads/stores can use addr-operand."
  [n]
  (and (#{:member :deref :var} (:op n))
       (t/scalar? (:type n))
       (not (and (= :member (:op n)) (:bitfield (:member n))))))

(defn simple-operand?
  "Node that can be evaluated into %rdi/%xmm1 without clobbering %rax."
  [n]
  (case (:op n)
    :num (and (not (:fval n)) (imm32? (:val n)))
    :var (and (:local (:var n)) (not (:vla-ptr (:var n))) (not (contains? *overaligned* (:id (:var n))))
              (t/scalar? (:type n)) (t/integer? (:type n)))
    false))

(defn gen-simple-to-rdi [n]
  (case (:op n)
    :num (emitf "  movq $" (:val n) ", %rdi")
    :var (let [ty (:type n) off (var-offset (:var n)) u (t/unsigned? ty)]
           (case (int (t/type-size ty))
             1 (emitf (if u "  movzbl " "  movsbl ") off "(%rbp), %edi")
             2 (emitf (if u "  movzwl " "  movswl ") off "(%rbp), %edi")
             4 (emitf (if u "  movl " "  movslq ") off "(%rbp), " (if u "%edi" "%rdi"))
             8 (emitf "  movq " off "(%rbp), %rdi")))))

(defn gen-operands
  "lhs -> %rax/%xmm0, rhs -> %rdi/%xmm1"
  [lhs rhs]
  (if (t/flonum? (:type lhs))
    (do (gen-expr lhs) (pushf!) (gen-expr rhs) (emit "  movaps %xmm0, %xmm1") (popf! 0))
    (if (simple-operand? rhs)
      (do (gen-expr lhs) (gen-simple-to-rdi rhs))
      (do (gen-expr lhs) (push!) (gen-expr rhs) (emit "  movq %rax, %rdi") (pop! "%rax")))))

(def into-ok-ops
  #{"movl" "movq" "movw" "movb" "movslq" "movzbl" "movzwl" "movsbl" "movswl" "movsbq" "movswq" "movzbq" "movzwq"
    "movabsq" "leaq" "leal" "addl" "addq" "subl" "subq" "andl" "andq" "orl" "orq" "xorl" "xorq"
    "imull" "imulq" "negl" "negq" "notl" "notq" "shll" "shlq" "shrl" "shrq" "sarl" "sarq"})

(def reg-names
  {"%r10" ["%r10" "%r10d" "%r10w" "%r10b"] "%r11" ["%r11" "%r11d" "%r11w" "%r11b"]})

(defn into-lines
  "Code computing rax-only expression n into register r (a key of reg-names)
   instead of %rax, as a vector of lines; nil if not possible."
  [n r]
  (when (rax-only? n)
    (let [sb (StringBuilder.)
          _ (binding [*asm* sb] (gen-expr n))
          lines (split-lines* (str sb))
          ok (every? (fn [^String l]
                       (let [t (str/trim l)
                             op (first (str/split t #" " 2))]
                         (and (contains? into-ok-ops op)
                              (not (re-find #"%ah|%r1[01]" t))
                              (or (not (#{"imull" "imulq"} op)) (str/includes? t ",")))))
                     lines)]
      (when ok
        (let [[q d wd b] (reg-names r)]
          (mapv (fn [l] (str/replace l #"%(rax|eax|ax|al)\b"
                                     (fn [[_ x]] (case x "rax" q "eax" d "ax" wd "al" b))))
                lines))))))

(declare gen-int-operands*)

(defn gen-int-operands
  "lhs -> %rax; returns an operand string for rhs (immediate, register or memory)."
  [lhs rhs w]
  (gen-int-operands* lhs rhs w false))

(defn gen-int-operands*
  "gen-int-operands; with comm (a commutative operation) the result may instead be
   rhs in %rax and the returned operand the held lhs."
  [lhs rhs w comm]
  (if-let [o (int-operand rhs w)]
    (do (gen-expr lhs) o)
    (if (and (= w 8) (cheap-index? rhs))
      (do (gen-expr lhs) (index-into-rdi rhs) "%rdi")
    (if (and (rdi-addr? rhs) (= w (t/type-size (:type rhs))) (or (t/integer? (:type rhs)) (t/ptr? (:type rhs))))
      ;; memory operand whose address needs only %rdi
      (do (gen-expr lhs) (render-mem (addr-operand rhs)))
    (if-let [[sr lines] (when (and *scratch-free* (seq @*scratch-free*) (rax-only? rhs))
                          (let [sr (first @*scratch-free*)]
                            (when-let [ls (into-lines rhs sr)] [sr ls])))]
      ;; lhs into %rax, then the rax-only rhs straight into a scratch register
      (do (gen-expr lhs)
          (doseq [l lines] (emit l))
          (let [[q d] (reg-names sr)] (if (= w 8) q d)))
    (if (or (int-operand lhs w) (rax-only? lhs))
      ;; lhs has no side effects: compute rhs first, then load lhs
      (do (gen-expr rhs)
          (emit "  movq %rax, %rdi")
          (gen-expr lhs)
          (if (= w 8) "%rdi" "%edi"))
      (do
        ;; evaluate lhs first, then pick where to hold it (so nested holders are reused)
        (gen-expr lhs)
        (if-let [sr (when (and *scratch-free* (seq @*scratch-free*) (pure? rhs))
                      (let [r (first @*scratch-free*)] (vswap! *scratch-free* rest) r))]
          (if (and (#{:deref :member} (:op rhs)) (t/scalar? (:type rhs))
                   (or (t/integer? (:type rhs)) (t/ptr? (:type rhs)))
                   (= w (t/type-size (:type rhs)))
                   (not (and (= :member (:op rhs)) (:bitfield (:member rhs)))))
            ;; rhs is a load: use its memory operand directly
            (let [_ (emitf "  movq %rax, " sr)
                  m (addr-operand rhs)
                  m (if (or (= "%rax" (:base m)) (= "%rax" (:index m)))
                      (do (emitf "  leaq " (render-mem m) ", %rsi") {:base "%rsi"})
                      m)]
              (emitf "  movq " sr ", %rax")
              (vswap! *scratch-free* conj sr)
              (render-mem m))
            (do (emitf "  movq %rax, " sr)
                (gen-expr rhs)
                (if comm
                  (do (when (volatile? comm) (vreset! comm true))
                      (vswap! *scratch-free* conj sr)
                      (let [[q d] (reg-names sr)] (if (= w 8) q d)))
                  (do (emit "  movq %rax, %rdi")
                      (emitf "  movq " sr ", %rax")
                      (vswap! *scratch-free* conj sr)
                      (if (= w 8) "%rdi" "%edi")))))
          (let [[_ rtext] (capture #(gen-expr rhs))]
            (if (and (not (str/includes? rtext "call")) (not (re-find #"%(rsi|esi|si|sil)\b" rtext)))
              ;; rhs code leaves %rsi alone: hold lhs there
              (do (emit "  movq %rax, %rsi")
                  (.append ^StringBuilder *asm* rtext)
                  (if comm
                    (do (when (volatile? comm) (vreset! comm true))
                        (if (= w 8) "%rsi" "%esi"))
                    (do (emit "  movq %rax, %rdi")
                        (emit "  movq %rsi, %rax")
                        (if (= w 8) "%rdi" "%edi"))))
              (let [[h release] (alloc-holder!)]
                (emitf "  movq %rax, " h)
                (gen-expr rhs)
                (if comm
                  (do (when (volatile? comm) (vreset! comm true))
                      (release)
                      (if (= w 8) h (or (some (fn [r] (when (= h (r 0)) (r 1))) callee-regs) h)))
                  (do (emit "  movq %rax, %rdi")
                      (emitf "  movq " h ", %rax")
                      (release)
                      (if (= w 8) "%rdi" "%edi"))))))))))))))

(def cc-signed {:eq "e" :ne "ne" :lt "l" :le "le" :gt "g" :ge "ge"})
(def cc-unsigned {:eq "e" :ne "ne" :lt "b" :le "be" :gt "a" :ge "ae"})
(def cc-negate {"e" "ne" "ne" "e" "l" "ge" "ge" "l" "le" "g" "g" "le" "b" "ae" "ae" "b" "be" "a" "a" "be"})

(defn gen-int-compare
  "Emit a compare for an integer/pointer comparison node; returns the condition code."
  [n]
  (let [{:keys [op lhs rhs]} n
        oty (:type lhs)
        oty (if (t/ptr-like? oty) t/ty-ulong oty)
        w (if (= 8 (t/type-size oty)) 8 4)
        u (or (t/unsigned? oty) (t/ptr? oty))
        lo (int-operand lhs w)
        ro (int-operand rhs w)
        reg? #(and % (str/starts-with? % "%"))
        imm? #(and % (str/starts-with? % "$"))]
    (if (and lo (not (imm? lo)) ro (or (reg? lo) (reg? ro) (imm? ro)))
      ;; compare in place
      (if (and (= ro "$0") (reg? lo))
        (emitf "  test" (if (= w 8) "q " "l ") lo ", " lo)
        (emitf "  cmp" (if (= w 8) "q " "l ") ro ", " lo))
      (cond
        ;; memory load compared with a register or immediate
        (and (or (reg? ro) (imm? ro)) (rdi-addr? lhs) (= w (t/type-size (:type lhs))))
        (emitf "  cmp" (if (= w 8) "q " "l ") ro ", " (render-mem (addr-operand lhs)))
        (and (reg? lo) (rdi-addr? rhs) (= w (t/type-size (:type rhs))))
        (emitf "  cmp" (if (= w 8) "q " "l ") (render-mem (addr-operand rhs)) ", " lo)
        ;; lhs a register/memory operand: rhs into %rax, then compare lhs with it in place
        (and lo (not (imm? lo)) (not ro))
        (do (gen-expr rhs)
            (emitf "  cmp" (if (= w 8) "q " "l ") (if (= w 8) "%rax" "%eax") ", " lo))
        :else
      (let [sw (volatile! false)
            o (gen-int-operands* lhs rhs w sw)]
        (cond
          @sw (emitf "  cmp" (if (= w 8) "q " "l ") (if (= w 8) "%rax" "%eax") ", " o)
          (and (= o "$0") (#{:eq :ne} op))
          (emit (if (= w 8) "  testq %rax, %rax" "  testl %eax, %eax"))
          :else
          (emitf "  cmp" (if (= w 8) "q " "l ") o ", " (if (= w 8) "%rax" "%eax"))))))
    ((if u cc-unsigned cc-signed) op)))

(defn gen-binary-int [n oty]
  (if (and (or (= :add (:op n)) (and (= :sub (:op n)) (= :num (:op (:rhs n))) (imm32? (:val (:rhs n)))))
           (t/ptr? (:type n)) (t/integer? (:type (:rhs n))))
    (materialize (addr-operand {:op :deref :expr n :type (:base (:type n))}))
    (gen-binary-int* n oty)))

(defn gen-binary-int* [n oty]
  (let [{:keys [op lhs rhs]} n
        w0 (if (= 8 (t/type-size oty)) 8 4)
        lo0 (int-operand lhs w0) ro0 (int-operand rhs w0)
        [lhs rhs] (if (and (#{:add :mul :bitand :bitor :bitxor} op)
                           lo0 (or (not ro0)
                                   (and (str/starts-with? lo0 "$") (not (str/starts-with? ro0 "$")))))
                    [rhs lhs] [lhs rhs])
        w (if (= 8 (t/type-size oty)) 8 4)
        ax (if (= w 8) "%rax" "%eax") sfx (if (= w 8) "q" "l")
        u (or (t/unsigned? oty) (t/ptr? oty))]
    (case op
      (:eq :ne :lt :le :gt :ge)
      (let [cc (gen-int-compare n)]
        (emitf "  set" cc " %al")
        (emit "  movzbl %al, %eax"))
      (:shl :shr)
      (let [o (gen-int-operands lhs rhs 4)
            ins (if (= op :shl) "shl" (if u "shr" "sar"))]
        (if (str/starts-with? o "$")
          (let [c (bit-and (parse-long (subs o 1)) 255)]
            (when-not (zero? c) (emitf "  " ins sfx " $" c ", " ax)))
          (do (emitf "  movl " o ", %ecx") (emitf "  " ins sfx " %cl, " ax))))
      (:div :mod)
      (if (and (:exact n) (= op :div) (= :num (:op rhs)) (> (:val rhs) 1) (= w 8))
        ;; exact division (pointer difference): shift, then multiply by the odd part's inverse
        (let [c (:val rhs) k (Long/numberOfTrailingZeros c) odd (bit-shift-right c k)
              inv (loop [x odd i 0] (if (= i 6) x (recur (unchecked-multiply x (unchecked-subtract 2 (unchecked-multiply odd x))) (inc i))))]
          (gen-expr lhs)
          (when (pos? k) (emitf "  sarq $" k ", %rax"))
          (when (not= odd 1)
            (emitf "  movabsq $" inv ", %rdx")
            (emit "  imulq %rdx, %rax")))
      (if-let [k (when (= :num (:op rhs))
                   (let [v (:val rhs)
                         v (if (= w 4) (if u (bit-and v 0xffffffff) (long (unchecked-int v))) v)]
                     (when (and (> v 1) (zero? (bit-and v (dec v))) (< v 0x40000000))
                       (Long/numberOfTrailingZeros v))))]
        ;; division by a power of two
        (let [dx (if (= w 8) "%rdx" "%edx") cx (if (= w 8) "%rcx" "%ecx")
              bits (* 8 w)]
          (gen-expr lhs)
          (cond
            (and u (= op :div)) (emitf "  shr" sfx " $" k ", " ax)
            u (emitf "  and" sfx " $" (dec (bit-shift-left 1 k)) ", " ax)
            (= op :div)
            (do (emitf "  mov" sfx " " ax ", " dx)
                (emitf "  sar" sfx " $" (dec bits) ", " dx)
                (emitf "  shr" sfx " $" (- bits k) ", " dx)
                (emitf "  add" sfx " " dx ", " ax)
                (emitf "  sar" sfx " $" k ", " ax))
            :else
            (do (emitf "  mov" sfx " " ax ", " dx)
                (emitf "  sar" sfx " $" (dec bits) ", " dx)
                (emitf "  shr" sfx " $" (- bits k) ", " dx)
                (emitf "  lea" sfx " (%rax,%rdx), " cx)
                (emitf "  and" sfx " $" (- (bit-shift-left 1 k)) ", " cx)
                (emitf "  sub" sfx " " cx ", " ax))))
      (let [o (gen-int-operands lhs rhs w)
            o (if (str/starts-with? o "$")
                (do (emitf "  mov" sfx " " o ", " (if (= w 8) "%rcx" "%ecx")) (if (= w 8) "%rcx" "%ecx"))
                o)]
        (if u
          (do (emit "  xorl %edx, %edx") (emitf "  div" sfx " " o))
          (do (emit (if (= w 8) "  cqto" "  cltd")) (emitf "  idiv" sfx " " o)))
        (when (= op :mod) (emitf "  mov" sfx " " (if (= w 8) "%rdx" "%edx") ", " ax)))))
      :mul (let [ro (int-operand rhs w) lo (int-operand lhs w)]
             (if (and ro (str/starts-with? ro "$") lo (not (str/starts-with? lo "$")))
               (emitf "  imul" sfx " " ro ", " lo ", " ax)
               (let [o (gen-int-operands* lhs rhs w true)]
                 (if (str/starts-with? o "$")
                   (emitf "  imul" sfx " " o ", " ax ", " ax)
                   (emitf "  imul" sfx " " o ", " ax)))))
      (:add :sub)
      (let [lr (when (= :var (:op lhs)) (vreg (:var lhs)))
            ro (int-operand rhs w)]
        (if (and lr ro (str/starts-with? ro "$") (= w (t/type-size (:type lhs))))
          (let [c (parse-long (subs ro 1)) c (if (= op :sub) (- c) c)]
            (if (imm32? c)
              (emitf "  lea" sfx " " c "(" (lr 0) "), " ax)
              (let [o (gen-int-operands lhs rhs w)]
                (emitf "  " (if (= op :add) "add" "sub") sfx " " o ", " ax))))
          (let [o (gen-int-operands* lhs rhs w (= op :add))]
            (emitf "  " (if (= op :add) "add" "sub") sfx " " o ", " ax))))
      (let [o (gen-int-operands* lhs rhs w true)
            ins (case op :add "add" :sub "sub" :bitand "and" :bitor "or" :bitxor "xor")]
        (emitf "  " ins sfx " " o ", " ax)))))

(defn int-cmp-node? [n]
  (and (#{:eq :ne :lt :le :gt :ge} (:op n))
       (let [t (:type (:lhs n))] (or (t/integer? t) (t/ptr-like? t)))))

(defn test-mask-parts
  "For an integer node x & c (c constant, either side): [x c]; else nil."
  [n]
  (when (and (= :bitand (:op n)) (t/integer? (:type n)) (#{4 8} (t/type-size (:type n))))
    (let [{:keys [lhs rhs]} n
          num? #(and (= :num (:op %)) (not (:fval %)))]
      (cond (num? rhs) [lhs (:val rhs)]
            (num? lhs) [rhs (:val lhs)]
            :else nil))))

(defn gen-test-mask
  "Set ZF from (x & c) without computing it into a register where possible."
  [n x c]
  (let [w (t/type-size (:type n))
        intlike? #(or (t/integer? %) (t/ptr? %))
        ;; strip widening integer casts: the low bits tested do not depend on the extension
        xc (loop [e x]
             (if (and (= :cast (:op e)) (intlike? (:type e)) (intlike? (:type (:expr e)))
                      (>= (t/type-size (:type e)) (t/type-size (:type (:expr e)))))
               (recur (:expr e))
               e))
        xw (t/type-size (:type xc))
        cw (if (= w 4) (bit-and c 0xffffffff) c)
        fits? (fn [bytes] (and (<= 0 cw) (or (>= bytes 8) (< cw (bit-shift-left 1 (* 8 bytes))))))
        imm (fn [bytes] (case (int bytes) 1 cw 2 cw 4 (long (unchecked-int cw)) 8 cw))]
    (cond
      ;; narrow load tested in place
      (and (intlike? (:type xc)) (#{1 2 4 8} xw) (< xw w) (fits? xw) (not= :bool (:kind (:type xc)))
           (not= :num (:op xc)) (not (and (= :member (:op xc)) (:bitfield (:member xc))))
           (or (rdi-addr? xc) (int-operand xc xw)
               (and (#{:deref :member} (:op xc)) (fixed-mem-op xc))))
      (let [op (cond (rdi-addr? xc) (render-mem (addr-operand xc))
                     :else (or (int-operand xc xw) (fixed-mem-op xc)))]
        (if (and (str/starts-with? op "%") (#{1 2} xw))
          (emitf "  testl $" cw ", " op)
          (emitf "  test" (sfx-of xw) " $" (imm xw) ", " op)))
      (and (= xw w) (or (= w 4) (imm32? cw)) (fits? w) (not= :num (:op xc))
           (or (int-operand xc w) (rdi-addr? xc)))
      (emitf "  test" (sfx-of w) " $" (imm w) ", " (or (int-operand xc w) (render-mem (addr-operand xc))))
      (or (= w 4) (imm32? c))
      (do (gen-expr x)
          (emitf "  test" (sfx-of w) " $" (if (= w 4) (long (unchecked-int c)) c) ", " (if (= w 8) "%rax" "%eax")))
      :else
      (do (gen-expr n) (emit (if (= w 8) "  testq %rax, %rax" "  testl %eax, %eax"))))))

(defn gen-branch
  "Jump to label when the truth of n equals jump-if; otherwise fall through."
  [n label jump-if]
  (if-let [[x c] (or (test-mask-parts n)
                     (when (and (#{:eq :ne} (:op n)) (= :num (:op (:rhs n))) (not (:fval (:rhs n)))
                                (zero? (:val (:rhs n))))
                       (test-mask-parts (:lhs n))))]
    (do (gen-test-mask (if (#{:eq :ne} (:op n)) (:lhs n) n) x c)
        (emitf (if (= jump-if (not= :eq (:op n))) "  jne " "  je ") label))
  (case (:op n)
    :not (gen-branch (:expr n) label (not jump-if))
    :comma (do (gen-discard (:lhs n)) (gen-branch (:rhs n) label jump-if))
    :cond (if (and (:then n) (:els n) (t/scalar? (:type n)) (not (t/ldouble? (:type n))))
            (let [l-else (new-label) l-end (new-label)]
              (gen-branch (:cond n) l-else false)
              (gen-branch (:then n) label jump-if)
              (emitf "  jmp " l-end)
              (emitf l-else ":")
              (gen-branch (:els n) label jump-if)
              (emitf l-end ":"))
            (do (gen-expr n)
                (cmp-zero (:type n))
                (emitf (if jump-if "  jne " "  je ") label)))
    :logand (if jump-if
              (let [skip (new-label)]
                (gen-branch (:lhs n) skip false)
                (gen-branch (:rhs n) label true)
                (emitf skip ":"))
              (do (gen-branch (:lhs n) label false)
                  (gen-branch (:rhs n) label false)))
    :logor (if jump-if
             (do (gen-branch (:lhs n) label true)
                 (gen-branch (:rhs n) label true))
             (let [skip (new-label)]
               (gen-branch (:lhs n) skip true)
               (gen-branch (:rhs n) label false)
               (emitf skip ":")))
    (if (int-cmp-node? n)
      (let [cc (gen-int-compare n)]
        (emitf "  j" (if jump-if cc (cc-negate cc)) " " label))
      (if (and (= :num (:op n)) (not (:fval n)))
        (when (= jump-if (not (zero? (:val n)))) (emitf "  jmp " label))
        (let [ty (:type n)
              intlike? #(or (t/integer? %) (t/ptr? %))
              core (loop [x n]
                     (if (and (= :cast (:op x)) (intlike? (:type x)) (intlike? (:type (:expr x)))
                              (>= (t/type-size (:type x)) (t/type-size (:type (:expr x)))))
                       (recur (:expr x))
                       x))
              cty (:type core)
              csz (t/type-size cty)
              sfx (case (int csz) 1 "b" 2 "w" 4 "l" 8 "q" nil)]
          (cond
            (and sfx (intlike? cty) (not= :bool (:kind cty)) (rdi-addr? core))
            (emitf "  cmp" sfx " $0, " (render-mem (addr-operand core)))
            (and sfx (intlike? cty) (= :var (:op core)) (vreg (:var core)))
            (let [r (vreg (:var core))] (if (= 8 csz) (emitf "  testq " (r 0) ", " (r 0)) (emitf "  testl " (r 1) ", " (r 1))))
            ;; memory operand (frame/static variable, *p or p->m with p in a register)
            (and (#{4 8} csz) (intlike? cty) (let [o (int-operand core csz)] (and o (str/includes? o "("))))
            (emitf "  cmp" sfx " $0, " (int-operand core csz))
            :else
            (do
          (gen-expr n)
          (if (and (t/integer? ty) (<= (t/type-size ty) 4))
            (emit "  testl %eax, %eax")
            (if (or (t/integer? ty) (t/ptr-like? ty))
              (emit "  testq %rax, %rax")
              (cmp-zero ty)))))
          (emitf (if jump-if "  jne " "  je ") label)))))))

(defn gen-binary-ld [n]
  ;; long double: lhs ends in st(0), rhs in st(1)
  (let [{:keys [op lhs rhs]} n]
    (gen-expr lhs)
    (emit "  subq $16, %rsp") (emit "  fstpt (%rsp)") (vswap! *depth* + 2)
    (gen-expr rhs)
    (emit "  fldt (%rsp)") (emit "  addq $16, %rsp") (vswap! *depth* - 2)
    (case op
      :add (emit "  faddp")
      :sub (emit "  fsubp")
      :mul (emit "  fmulp")
      :div (emit "  fdivp")
      (:eq :ne :gt :ge :lt :le)
      (do (when (#{:lt :le} op) (emit "  fxch %st(1)"))
          (emit "  fucomip %st(1), %st")
          (emit "  fstp %st(0)")
          (case op
            :eq (do (emit "  sete %al") (emit "  setnp %dl") (emit "  andb %dl, %al"))
            :ne (do (emit "  setne %al") (emit "  setp %dl") (emit "  orb %dl, %al"))
            (:gt :lt) (emit "  seta %al")
            (:ge :le) (emit "  setae %al"))
          (emit "  movzbl %al, %eax")))))

(defn gen-binary [n]
  (let [{:keys [op lhs rhs]} n
        oty (if (#{:eq :ne :lt :le :gt :ge} op) (:type lhs) (:type n))
        oty (if (t/ptr-like? oty) t/ty-ulong oty)]
    (if (t/ldouble? oty)
      (gen-binary-ld n)
    (if (not (t/flonum? oty))
      (gen-binary-int n oty)
    (do
    (gen-operands lhs rhs)
    (if (t/flonum? oty)
      (let [s (if (= :float (:kind oty)) "ss" "sd")
            ucom (if (= :float (:kind oty)) "ucomiss" "ucomisd")]
        (case op
          :add (emitf "  add" s " %xmm1, %xmm0")
          :sub (emitf "  sub" s " %xmm1, %xmm0")
          :mul (emitf "  mul" s " %xmm1, %xmm0")
          :div (emitf "  div" s " %xmm1, %xmm0")
          :eq (do (emitf "  " ucom " %xmm1, %xmm0") (emit "  sete %al") (emit "  setnp %dl")
                  (emit "  andb %dl, %al") (emit "  movzbl %al, %eax"))
          :ne (do (emitf "  " ucom " %xmm1, %xmm0") (emit "  setne %al") (emit "  setp %dl")
                  (emit "  orb %dl, %al") (emit "  movzbl %al, %eax"))
          :lt (do (emitf "  " ucom " %xmm0, %xmm1") (emit "  seta %al") (emit "  movzbl %al, %eax"))
          :le (do (emitf "  " ucom " %xmm0, %xmm1") (emit "  setae %al") (emit "  movzbl %al, %eax"))
          :gt (do (emitf "  " ucom " %xmm1, %xmm0") (emit "  seta %al") (emit "  movzbl %al, %eax"))
          :ge (do (emitf "  " ucom " %xmm1, %xmm0") (emit "  setae %al") (emit "  movzbl %al, %eax"))))
      (let [w (= 8 (t/type-size oty))
            ax (if w "%rax" "%eax") di (if w "%rdi" "%edi") dx (if w "%rdx" "%edx")
            sfx (if w "q" "l")
            u (or (t/unsigned? oty) (t/ptr? oty))]
        (case op
          :add (emitf "  add" sfx " " di ", " ax)
          :sub (emitf "  sub" sfx " " di ", " ax)
          :mul (emitf "  imul" sfx " " di ", " ax)
          (:div :mod)
          (do (if u
                (do (emit "  xorl %edx, %edx") (emitf "  div" sfx " " di))
                (do (emit (if w "  cqto" "  cltd")) (emitf "  idiv" sfx " " di)))
              (when (= op :mod) (emitf "  mov" sfx " " dx ", " ax)))
          :bitand (emitf "  and" sfx " " di ", " ax)
          :bitor (emitf "  or" sfx " " di ", " ax)
          :bitxor (emitf "  xor" sfx " " di ", " ax)
          :shl (do (emit "  movl %edi, %ecx") (emitf "  shl" sfx " %cl, " ax))
          :shr (do (emit "  movl %edi, %ecx") (emitf (if u "  shr" "  sar") sfx " %cl, " ax))
          (:eq :ne :lt :le :gt :ge)
          (do (emitf "  cmp" sfx " " di ", " ax)
              (emitf "  set" (case op :eq "e" :ne "ne"
                                   :lt (if u "b" "l") :le (if u "be" "le")
                                   :gt (if u "a" "g") :ge (if u "ae" "ge"))
                     " %al")
              (emit "  movzbl %al, %eax"))))))))))

(defn bf-unit
  "Type used to load/store a bit-field's storage: its declared type, or 8 bytes
   when a packed field straddles the declared unit."
  [m]
  (if (> (+ (:bit-offset m) (:bit-width m)) (* 8 (t/type-size (:type m))))
    t/ty-ulong
    (:type m)))

(defn low-mask [w] (if (>= w 64) -1 (dec (bit-shift-left 1 w))))

(defn gen-bitfield-load [m]
  (let [w (:bit-width m) off (:bit-offset m)]
    (emitf "  shlq $" (- 64 w off) ", %rax")
    (emitf (if (t/unsigned? (:type m)) "  shrq $" "  sarq $") (- 64 w) ", %rax")))

(defn gen-assign [n]
  (let [{:keys [lhs rhs]} n]
    (cond
      (and (= :var (:op lhs)) (vreg (:var lhs)))
      (do (gen-expr rhs) (reg-store (:var lhs)))
      (and (scalar-var? lhs) (var-mem-op (:var lhs)))
      (do (gen-expr rhs) (store-op (:type lhs) (var-mem-op (:var lhs))))
      (and (= :var (:op lhs)) (t/struct? (:type lhs)) (:local (:var lhs)) (var-mem-op (:var lhs))
           (#{:deref :member :var} (:op rhs)) (<= (t/type-size (:type lhs)) 64))
      (let [off (var-offset (:var lhs)) sz (t/type-size (:type lhs))
            m (addr-operand rhs)]
        (loop [k 0]
          (let [rem (- sz k)
                [w r] (cond (>= rem 8) [8 "%rcx"] (>= rem 4) [4 "%ecx"] (>= rem 2) [2 "%cx"] (>= rem 1) [1 "%cl"] :else [0 nil])]
            (when (pos? w)
              (emitf "  mov" (sfx-of w) " " (render-mem (update m :disp (fnil + 0) k)) ", " r)
              (emitf "  mov" (sfx-of w) " " r ", " (+ off k) "(%rbp)")
              (recur (+ k w)))))
        (emitf "  leaq " off "(%rbp), %rax"))
      (and (= :var (:op lhs)) (t/struct? (:type lhs)) (:local (:var lhs)) (var-mem-op (:var lhs)))
      (let [off (var-offset (:var lhs)) sz (t/type-size (:type lhs))]
        (gen-expr rhs)
        (emitf "  leaq " off "(%rbp), %rdi")
        (emit-copy "%rax" "%rdi" sz)
        (emit "  movq %rdi, %rax"))
      (and (simple-lvalue-op? lhs) (pure? lhs))
      (let [ty (:type lhs)]
        (gen-expr rhs)
        (cond
          (t/ldouble? ty) (store-op ty (render-mem (addr-operand lhs)))
          (t/flonum? ty) (do (emit "  movaps %xmm0, %xmm2")
                             (let [op (render-mem (addr-operand lhs))]
                               (emit "  movaps %xmm2, %xmm0")
                               (store-op ty op)))
          (rdi-addr? lhs)
          (do (store-op ty (render-mem (addr-operand lhs))))
          :else (do (emit "  movq %rax, %rsi")
                    (let [op (render-mem (addr-operand lhs))]
                      (case (int (t/type-size ty))
                        1 (emitf "  movb %sil, " op)
                        2 (emitf "  movw %si, " op)
                        4 (emitf "  movl %esi, " op)
                        8 (emitf "  movq %rsi, " op))
                      (emit "  movq %rsi, %rax")))))
      :else (gen-assign-mem n))))

(defn gen-assign-mem [n]
  (let [{:keys [lhs rhs]} n]
    (gen-addr lhs)
    (push!)
    (gen-expr rhs)
    (if (and (= :member (:op lhs)) (:bitfield (:member lhs)))
      (let [m (:member lhs) w (:bit-width m) off (:bit-offset m)
            mask (bit-shift-left (low-mask w) off)]
        (emit "  movq %rax, %r8")
        (emit "  movq %rax, %rdi")
        (emitf "  movabsq $" (low-mask w) ", %r9")
        (emit "  andq %r9, %rdi")
        (emitf "  shlq $" off ", %rdi")
        (emit "  movq (%rsp), %rax")
        (load (bf-unit m))
        (emitf "  movabsq $" (bit-not mask) ", %r9")
        (emit "  andq %r9, %rax")
        (emit "  orq %rdi, %rax")
        (store (bf-unit m))
        (emit "  movq %r8, %rax")
        (emitf "  shlq $" (- 64 w) ", %rax")
        (emitf (if (t/unsigned? (:type m)) "  shrq $" "  sarq $") (- 64 w) ", %rax"))
      (store (:type n)))))

;; function calls

(defn classify-args
  "Returns vector of {:node :mode (:gp|:fp|:struct-reg|:stack) :classes} plus counts."
  [args gp0]
  (loop [as args gp gp0 fp 0 stack 0 out []]
    (if (empty? as)
      {:args out :stack stack :gp gp :fp fp}
      (let [a (first as) ty (:type a)]
        (cond
          (t/struct? ty)
          (let [cls (struct-classes ty)
                ng (count (filter #(= :int %) cls))
                nf (count (filter #(= :sse %) cls))
                words (quot (align-to (t/type-size ty) 8) 8)]
            (if (and cls (<= (+ gp ng) 6) (<= (+ fp nf) 8))
              (recur (rest as) (+ gp ng) (+ fp nf) stack (conj out {:node a :mode :struct-reg :classes cls}))
              (recur (rest as) gp fp (+ stack words)
                     (conj out {:node a :mode :stack :words words :align (if (>= (t/type-align ty) 16) 2 1)}))))
          (t/ldouble? ty)
          (recur (rest as) gp fp (+ stack 2) (conj out {:node a :mode :stack :words 2 :align 2}))
          (t/flonum? ty)
          (if (< fp 8)
            (recur (rest as) gp (inc fp) stack (conj out {:node a :mode :fp}))
            (recur (rest as) gp fp (inc stack) (conj out {:node a :mode :stack :words 1})))
          :else
          (if (< gp 6)
            (recur (rest as) (inc gp) fp stack (conj out {:node a :mode :gp}))
            (recur (rest as) gp fp (inc stack) (conj out {:node a :mode :stack :words 1}))))))))

(defn fixed-addr?
  "Lvalues at a fixed frame/static address (no registers needed)."
  [n]
  (case (:op n)
    :var (and (not (vreg (:var n))) (boolean (var-mem-op (:var n))))
    :member (fixed-addr? (:expr n))
    false))

(defn rax-only?
  "Expressions whose code uses no register but %rax."
  [n]
  (let [ty (:type n)]
    (and (or (t/integer? ty) (t/ptr? ty) (and (t/array? ty) (not= :vla (:kind ty)) (= :var (:op n))))
         (case (:op n)
           :num (not (:fval n))
           :var (boolean (or (vreg (:var n)) (var-mem-op (:var n)) (t/array? ty)))
           :member (and (not (:bitfield (:member n))) (fixed-addr? n))
           :addr (and (= :var (:op (:expr n))) (not (vreg (:var (:expr n)))))
           :cast (and (or (t/integer? (:type (:expr n))) (t/ptr? (:type (:expr n))) (t/array? (:type (:expr n)))) (rax-only? (:expr n)))
           (:add :sub :bitand :bitor :bitxor :mul)
           (let [w (if (= 8 (t/type-size ty)) 8 4)]
             (and (t/integer? ty) (rax-only? (:lhs n)) (boolean (int-operand (:rhs n) w))))
           (:neg :bitnot) (and (t/integer? ty) (rax-only? (:expr n)))
           false))))

(defn push-struct! [ty]
  (let [sz (align-to (t/type-size ty) 8)]
    (emitf "  subq $" sz ", %rsp")
    (vswap! *depth* + (quot sz 8))
    (emit-copy "%rax" "%rsp" (t/type-size ty))))

(defn push-arg! [{:keys [node]}]
  (gen-expr node)
  (let [ty (:type node)]
    (cond (t/struct? ty) (push-struct! ty)
          (t/flonum? ty) (pushf!)
          :else (push!))))

(defn store-ret-regs
  "After call returning small struct: store regs into buffer at (%rdi)."
  [ty cls]
  (let [sz (t/type-size ty)]
    (loop [i 0 gi 0 fi 0]
      (when (< i (count cls))
        (let [off (* 8 i)
              rem (- sz off)]
          (if (= :sse (nth cls i))
            (do (if (>= rem 8)
                  (emitf "  movsd %xmm" fi ", " off "(%rdi)")
                  (emitf "  movss %xmm" fi ", " off "(%rdi)"))
                (recur (inc i) gi (inc fi)))
            (let [reg (nth ["%rax" "%rdx"] gi)]
              (emitf "  movq " reg ", " off "(%rdi)")
              (recur (inc i) (inc gi) fi))))))))

(defn gen-call [n]
  (let [fnode (:fn n)
        ret (:type n)
        ret-mem (and (t/struct? ret) (nil? (struct-classes ret)))
        {:keys [args]} (classify-args (:args n) (if ret-mem 1 0))
        ;; lay out the stack argument area (8-byte words, 16-byte aligned where needed)
        [args stack] (reduce (fn [[out off] a]
                               (if (= :stack (:mode a))
                                 (let [off (if (= 2 (:align a)) (align-to off 2) off)]
                                   [(conj out (assoc a :off off)) (+ off (:words a))])
                                 [(conj out a) off]))
                             [[] 0] args)
        pad (odd? (+ @*depth* stack))]
    (when pad (emit "  subq $8, %rsp") (vswap! *depth* inc))
    (when (pos? stack)
      (emitf "  subq $" (* 8 stack) ", %rsp")
      (vswap! *depth* + stack))
    ;; stack args: evaluate and store into their slots
    (doseq [a args :when (= :stack (:mode a))]
      (let [node (:node a) ty (:type node) off (* 8 (:off a))]
        (gen-expr node)
        (cond
          (t/struct? ty) (do (emitf "  leaq " off "(%rsp), %rdi") (emit-copy "%rax" "%rdi" (t/type-size ty)))
          (t/ldouble? ty) (emitf "  fstpt " off "(%rsp)")
          (= :float (:kind ty)) (emitf "  movss %xmm0, " off "(%rsp)")
          (t/flonum? ty) (emitf "  movsd %xmm0, " off "(%rsp)")
          :else (emitf "  movq %rax, " off "(%rsp)"))))
    (doseq [[r slot] *r89-slots*] (emitf "  movq " r ", " slot))
    (let [direct (and (= :var (:op fnode)) (t/func? (:type fnode)) (not (:local (:var fnode))))
          ;; indirect call through a pointer that is a plain operand: loaded into %r10 after the arguments
          fn-op (when-not direct
                  (let [f (if (and (= :deref (:op fnode)) (t/func? (:type fnode))) (:expr fnode) fnode)
                        o (when (t/ptr? (:type f)) (int-operand f 8))]
                    (if (and o (not (re-find #"%r(8|9|10|11)" o)))
                      (str "  movq " o ", %r10\n")
                      ;; a pure pointer expression whose code uses only %rax: computed after the arguments
                      (when (pure? f)
                        (let [[_ text] (capture #(gen-expr f))]
                          (when-not (re-find #"call|push|pop|%(rdi|edi|di|dil|rsi|esi|si|sil|rdx|edx|dx|dl|rcx|ecx|cx|cl|r8|r9|r10|r11|rsp)" text)
                            (str text "  movq %rax, %r10\n")))))))
          ngp (+ (if ret-mem 1 0) (count (filter #(= :gp (:mode %)) args)))
          fast (and (or direct fn-op) (not-any? #(= :struct-reg (:mode %)) args)
                    (not (and (seq *r89-slots*) (>= ngp 5))))
          ;; register targets
          args (loop [as args gp (if ret-mem 1 0) fp 0 out []]
                 (if (empty? as) out
                     (let [a (first as)]
                       (case (:mode a)
                         :gp (recur (rest as) (inc gp) fp (conj out (assoc a :reg gp)))
                         :fp (recur (rest as) gp (inc fp) (conj out (assoc a :reg fp)))
                         (recur (rest as) gp fp (conj out a))))))
          simple? (fn [a]
                    (let [nd (:node a) ty (:type nd)]
                      (case (:mode a)
                        :gp (or (and (= :num (:op nd)) (not (:fval nd)))
                                (and (#{4 8} (t/type-size ty)) (int-operand nd (t/type-size ty)))
                                (and (= :addr (:op nd)) (= :var (:op (:expr nd))) (:local (:var (:expr nd)))
                                     (not (vreg (:var (:expr nd)))) (var-mem-op (:var (:expr nd)))))
                        :fp (and (= :var (:op nd)) (#{:float :double} (:kind ty)) (var-mem-op (:var nd)))
                        false)))
          regargs (filter #(#{:gp :fp :struct-reg} (:mode %)) args)
          complex (if fast (vec (remove simple? regargs)) (vec regargs))
          simple (if fast (filter simple? regargs) [])
          load-simple (fn [a]
                        (let [nd (:node a) ty (:type nd) i (:reg a)]
                          (case (:mode a)
                            :gp (cond
                                  (= :num (:op nd)) (if (imm32? (:val nd))
                                                      (emitf "  movq $" (:val nd) ", " (nth argreg64 i))
                                                      (emitf "  movabsq $" (:val nd) ", " (nth argreg64 i)))
                                  (= :addr (:op nd)) (emitf "  leaq " (var-mem-op (:var (:expr nd))) ", " (nth argreg64 i))
                                  :else (let [w (t/type-size ty) o (int-operand nd w)]
                                          (if (= w 8)
                                            (emitf "  movq " o ", " (nth argreg64 i))
                                            (emitf "  movl " o ", " (nth argreg32 i)))))
                            :fp (emitf (if (= :float (:kind ty)) "  movss " "  movsd ") (var-mem-op (:var nd)) ", %xmm" i))))
          to-reg (fn [a]
                   (if (= :gp (:mode a))
                     (emitf "  movq %rax, " (nth argreg64 (:reg a)))
                     (when-not (zero? (:reg a)) (emitf "  movaps %xmm0, %xmm" (:reg a)))))
          ;; reg +/- imm and operand * imm computed directly into the argument register
          addr-into (fn [v dst]
                      (cond
                        (vreg v) false
                        (:local v) (do (local-addr v dst) true)
                        (contains? *defined* (:label v)) (do (emitf "  leaq " (:label v) "(%rip), " dst) true)
                        :else (do (emitf "  movq " (:label v) "@GOTPCREL(%rip), " dst) true)))
          direct-into (fn [a]
                        (let [nd (:node a) ty (:type nd) w (t/type-size ty) i (:reg a)
                              inner (when (= :cast (:op nd)) (:expr nd))]
                          (cond
                            (not= :gp (:mode a)) nil
                            (and (= :var (:op nd)) (t/array? ty) (not= :vla (:kind ty)))
                            (addr-into (:var nd) (nth argreg64 i))
                            (and (= :addr (:op nd)) (= :var (:op (:expr nd))))
                            (addr-into (:var (:expr nd)) (nth argreg64 i))
                            (and inner (= :var (:op inner)) (t/array? (:type inner)) (not= :vla (:kind (:type inner))) (t/ptr? ty))
                            (addr-into (:var inner) (nth argreg64 i))
                            (and inner (= 8 w) (t/integer? ty) (t/integer? (:type inner)) (#{1 2} (t/type-size (:type inner)))
                                 (= :var (:op inner)) (vreg (:var inner)))
                            (do (emitf "  movslq " ((vreg (:var inner)) 1) ", " (nth argreg64 i)) true)
                            (and inner (= 8 w) (t/integer? ty) (t/integer? (:type inner)) (= 4 (t/type-size (:type inner)))
                                 (int-operand inner 4))
                            (do (if (t/unsigned? (:type inner))
                                  (emitf "  movl " (int-operand inner 4) ", " (nth argreg32 i))
                                  (emitf "  movslq " (int-operand inner 4) ", " (nth argreg64 i)))
                                true)
                            :else
                          (when (and (= :gp (:mode a)) (t/integer? ty) (#{4 8} w))
                            (let [dst (if (= w 8) (nth argreg64 i) (nth argreg32 i))
                                  sfx (if (= w 8) "q" "l")]
                              (case (:op nd)
                                (:add :sub)
                                (let [lr (when (= :var (:op (:lhs nd))) (vreg (:var (:lhs nd))))
                                      ro (int-operand (:rhs nd) w)]
                                  (when (and lr ro (str/starts-with? ro "$") (= w (t/type-size (:type (:lhs nd)))))
                                    (let [c (parse-long (subs ro 1)) c (if (= :sub (:op nd)) (- c) c)]
                                      (when (imm32? c)
                                        (emitf "  lea" sfx " " c "(" (lr 0) "), " dst)
                                        true))))
                                :mul
                                (let [lo (int-operand (:lhs nd) w) ro (int-operand (:rhs nd) w)
                                      [lo ro] (if (and lo (str/starts-with? lo "$")) [ro lo] [lo ro])]
                                  (when (and lo ro (str/starts-with? ro "$") (not (str/starts-with? lo "$")))
                                    (emitf "  imul" sfx " " ro ", " lo ", " dst)
                                    true))
                                nil))))))]
      (if (and fast (every? #(and (= :gp (:mode %)) (rax-only? (:node %))) complex))
        ;; every complex arg is computed in %rax alone: right to left, straight to registers
        (do (doseq [a (reverse complex)]
              (when-not (direct-into a)
                (gen-expr (:node a))
                (to-reg a)))
            (doseq [a simple] (load-simple a))
            (when ret-mem (local-addr (:ret-buf n) "%rdi")))
      (if fast
        ;; complex args right to left; the leftmost one goes straight to its register
        (let [held (doall
                     (for [a (reverse (rest complex))]
                       (if (= :gp (:mode a))
                         (do (gen-expr (:node a))
                             (let [h (alloc-holder!)]
                               (emitf "  movq %rax, " (first h))
                               [a h]))
                         (do (push-arg! a) [a nil]))))]
            (when-let [a0 (first complex)]
              (gen-expr (:node a0))
              (to-reg a0))
            (doseq [[a h] (reverse held)]
              (if h
                (do (emitf "  movq " (first h) ", " (nth argreg64 (:reg a)))
                    ((second h)))
                (if (= :gp (:mode a)) (pop! (nth argreg64 (:reg a))) (popf! (:reg a)))))
            (doseq [a simple] (load-simple a))
            (when ret-mem (local-addr (:ret-buf n) "%rdi")))
        (do (doseq [a (reverse args)] (when (not= :stack (:mode a)) (push-arg! a)))
            (when-not direct
              (gen-expr fnode)
              (emit "  movq %rax, %r10"))
            (when ret-mem
              (local-addr (:ret-buf n) "%rdi")))))
      (when (and fast fn-op) (.append ^StringBuilder *asm* ^String fn-op))
      (gen-call-finish n args fast direct ret ret-mem stack pad fnode))))

(defn gen-call-finish
  "The end of gen-call: pop register arguments, call, clean up, normalize the result."
  [n args fast direct ret ret-mem stack pad fnode]
      (let [[gp fp] (reduce (fn [[gp fp] a]
                              (case (:mode a)
                                :gp (do (pop! (nth argreg64 gp)) [(inc gp) fp])
                                :fp (do (popf! fp) [gp (inc fp)])
                                :struct-reg
                                (reduce (fn [[gp fp] c]
                                          (if (= c :sse)
                                            (do (popf! fp) [gp (inc fp)])
                                            (do (pop! (nth argreg64 gp)) [(inc gp) fp])))
                                        [gp fp] (:classes a))
                                [gp fp]))
                            [(if ret-mem 1 0) 0] (if fast [] args))
            fp (count (filter #(= :fp (:mode %)) args))
            fty (:fn-type n)]
        (when (or (:variadic fty) (not (:proto fty)) (not direct))
          (emitf "  movl $" fp ", %eax"))
        (if direct
          (let [label (:label (:var fnode))]
            (if (contains? *defined* label)
              (emitf "  call " label)
              (emitf "  call " label "@PLT")))
          (emit "  call *%r10"))
        (doseq [[r slot] *r89-slots*] (emitf "  movq " slot ", " r))
        (let [cleanup (+ stack (if pad 1 0))]
          (when (pos? cleanup)
            (emitf "  addq $" (* 8 cleanup) ", %rsp")
            (vswap! *depth* - cleanup)))
        ;; normalize return value
        (case (:kind ret)
          :bool (emit "  movzbl %al, %eax")
          :char (emit (if (t/unsigned? ret) "  movzbl %al, %eax" "  movsbl %al, %eax"))
          :short (emit (if (t/unsigned? ret) "  movzwl %ax, %eax" "  movswl %ax, %eax"))
          nil)
        (when (and (t/struct? ret) (not ret-mem))
          (local-addr (:ret-buf n) "%rdi")
          (store-ret-regs ret (struct-classes ret))
          (emit "  movq %rdi, %rax"))))

;; varargs

(defn gen-va-start [n]
  (let [{:keys [gp fp stack-bytes]} *fninfo*
        va (:va-area *fn*)]
    (gen-expr (:expr n))
    (emitf "  movl $" (* 8 gp) ", (%rax)")
    (emitf "  movl $" (+ 48 (* 16 fp)) ", 4(%rax)")
    (emitf "  leaq " (+ 16 stack-bytes) "(%rbp), %rdx")
    (emit "  movq %rdx, 8(%rax)")
    (emitf "  leaq " (var-offset va) "(%rbp), %rdx")
    (emit "  movq %rdx, 16(%rax)")))

(defn gen-va-arg [n]
  (let [ty (:base (:type n))]
    (gen-expr (:expr n))
    (emit "  movq %rax, %rcx")
    (cond
      (t/struct? ty)
      (let [cls (struct-classes ty)
            sz (t/type-size ty)
            words (align-to sz 8)
            l-over (new-label) l-end (new-label)]
        (if (nil? cls)
          (do (emit "  movq 8(%rcx), %rax")
              (emitf "  addq $" words ", 8(%rcx)"))
          (let [ng (count (filter #(= :int %) cls))
                nf (count (filter #(= :sse %) cls))
                tmp (var-offset (:tmp n))]
            (when (pos? ng)
              (emit "  movl (%rcx), %edx")
              (emitf "  cmpl $" (- 48 (* 8 ng)) ", %edx")
              (emitf "  ja " l-over))
            (when (pos? nf)
              (emit "  movl 4(%rcx), %edx")
              (emitf "  cmpl $" (- 176 (* 16 nf)) ", %edx")
              (emitf "  ja " l-over))
            ;; copy each eightbyte from register save area
            (doseq [[i c] (map-indexed vector cls)]
              (if (= c :sse)
                (do (emit "  movl 4(%rcx), %edx")
                    (emit "  addq 16(%rcx), %rdx")
                    (emit "  movq (%rdx), %r8")
                    (emitf "  movq %r8, " (+ tmp (* 8 i)) "(%rbp)")
                    (emit "  addl $16, 4(%rcx)"))
                (do (emit "  movl (%rcx), %edx")
                    (emit "  addq 16(%rcx), %rdx")
                    (emit "  movq (%rdx), %r8")
                    (emitf "  movq %r8, " (+ tmp (* 8 i)) "(%rbp)")
                    (emit "  addl $8, (%rcx)"))))
            (emitf "  leaq " tmp "(%rbp), %rax")
            (emitf "  jmp " l-end)
            (emitf l-over ":")
            (emit "  movq 8(%rcx), %rax")
            (emitf "  addq $" words ", 8(%rcx)")
            (emitf l-end ":"))))
      (t/ldouble? ty)
      (do (emit "  movq 8(%rcx), %rax")
          (emit "  addq $15, %rax")
          (emit "  andq $-16, %rax")
          (emit "  leaq 16(%rax), %rdx")
          (emit "  movq %rdx, 8(%rcx)"))
      (t/flonum? ty)
      (let [l-over (new-label) l-end (new-label)]
        (emit "  movl 4(%rcx), %edx")
        (emit "  cmpl $160, %edx")
        (emitf "  ja " l-over)
        (emit "  movq 16(%rcx), %rax")
        (emit "  addq %rdx, %rax")
        (emit "  addl $16, 4(%rcx)")
        (emitf "  jmp " l-end)
        (emitf l-over ":")
        (emit "  movq 8(%rcx), %rax")
        (emit "  addq $8, 8(%rcx)")
        (emitf l-end ":"))
      :else
      (let [l-over (new-label) l-end (new-label)]
        (emit "  movl (%rcx), %edx")
        (emit "  cmpl $40, %edx")
        (emitf "  ja " l-over)
        (emit "  movq 16(%rcx), %rax")
        (emit "  addq %rdx, %rax")
        (emit "  addl $8, (%rcx)")
        (emitf "  jmp " l-end)
        (emitf l-over ":")
        (emit "  movq 8(%rcx), %rax")
        (emit "  addq $8, 8(%rcx)")
        (emitf l-end ":")))))

(defn gen-expr-pop
  "Evaluate n, dropping a long double result from the x87 stack."
  [n]
  (gen-expr n)
  (when (t/ldouble? (:type n)) (emit "  fstp %st(0)")))

(defn sfx-of [sz] (case (int sz) 1 "b" 2 "w" 4 "l" 8 "q"))

(defn store-src
  "Immediate or register operand usable as the source of a store of size sz."
  [rhs sz]
  (let [o (int-operand rhs (if (= sz 8) 8 4))]
    (when (and o (not (str/includes? o "(")))
      (cond
        (str/starts-with? o "$") (when (or (>= sz 4) (= :num (:op rhs))) o)
        (>= sz 4) o
        :else nil))))

(defn same-var? [a b]
  (and (= :var (:op a)) (= :var (:op b))
       (let [va (:var a) vb (:var b)]
         (if (:local va)
           (and (:local vb) (some? (:id va)) (= (:id va) (:id vb)))
           (and (not (:local vb)) (some? (:label va)) (= (:label va) (:label vb)))))))

(defn in-place-update
  "For x = x OP y on an integer variable x: [instruction operand] or nil."
  [lhs rhs sz]
  (when (and (#{:add :sub :bitand :bitor :bitxor} (:op rhs))
             (same-var? lhs (:lhs rhs))
             (= sz (t/type-size (:type rhs))))
    (let [ins (case (:op rhs) :add "add" :sub "sub" :bitand "and" :bitor "or" :bitxor "xor")]
      (if-let [o (int-operand (:rhs rhs) sz)]
        [ins o]
        (when (pure? (:rhs rhs))
          [ins :computed])))))

(defn ext-load
  "Instruction loading a value of integer type from from memory and
   converting it to type to, as [mnemonic dst-width]; nil if not simple."
  [from to]
  (let [fs (t/type-size from) ts (t/type-size to)
        fu (or (t/unsigned? from) (t/ptr? from)) tu (or (t/unsigned? to) (t/ptr? to))]
    (when (and (or (t/integer? from) (t/ptr? from)) (or (t/integer? to) (t/ptr? to))
               (not= :bool (:kind to)) (not= :bool (:kind from))
               (#{1 2 4 8} fs) (#{1 2 4 8} ts)
               (not (and (< fs ts) (< ts 4) (not= fu tu))))
      (let [[sz u] (if (<= ts fs) [ts tu] [fs fu])]
        (case (int sz)
          1 (if u ["movzbl" 4] (if (= ts 8) ["movsbq" 8] ["movsbl" 4]))
          2 (if u ["movzwl" 4] (if (= ts 8) ["movswq" 8] ["movswl" 4]))
          4 (if (or u (< ts 8)) ["movl" 4] ["movslq" 8])
          8 ["movq" 8])))))

(defn load-to-reg!
  "If rhs is a memory load (possibly converted) addressable with %rdi only,
   load it straight into register variable v and return true."
  [v rhs]
  (let [inner (if (and (= :cast (:op rhs)) (or (t/integer? (:type rhs)) (t/ptr? (:type rhs)))) (:expr rhs) rhs)
        lty (:type v)]
    (when (and (rdi-addr? inner) (= (t/type-size (:type rhs)) (t/type-size lty))
               (= (boolean (t/unsigned? (:type rhs))) (boolean (t/unsigned? lty))))
      (when-let [[ins w] (ext-load (:type inner) lty)]
        (let [m (addr-operand inner) r (vreg v)]
          (emitf "  " ins " " (render-mem m) ", " (if (= w 8) (r 0) (r 1)))
          true)))))

(defn capture
  "Run f with emission captured; returns [result text]."
  [f]
  (let [sb (StringBuilder.)
        r (binding [*asm* sb] (f))]
    [r (str sb)]))

(defn gen-struct-copy-nv
  "Struct assignment between pure lvalues, value unused. Returns true if done."
  [lhs rhs]
  (let [sz (t/type-size (:type lhs))]
    (when (and (#{:deref :member :var} (:op lhs)) (#{:deref :member :var} (:op rhs))
               (pure? lhs) (pure? rhs) (<= 1 sz 64)
               (not (and (= :var (:op lhs)) (vreg (:var lhs)))))
      (let [[ms stext] (capture #(addr-operand rhs))
            [md dtext] (capture #(addr-operand lhs))
            bad-src? (or (str/includes? stext "call") (re-find #"%(rsi|esi|si|sil|rdx|edx|dx|dl)\b" stext))
            bad-dst? (or (str/includes? dtext "call") (re-find #"%(rsi|esi|si|sil|rdx|edx|dx|dl)\b" dtext)
                         (#{"%rsi" "%rdx"} (:base md)) (#{"%rsi" "%rdx"} (:index md)))]
        (when-not (or bad-src? bad-dst? (#{"%rdx"} (:base ms)) (#{"%rdx"} (:index ms)))
          (.append ^StringBuilder *asm* stext)
          (let [ms (if (or (#{"%rax" "%rdi"} (:base ms)) (#{"%rax" "%rdi"} (:index ms)))
                     (do (emitf "  leaq " (render-mem ms) ", %rsi") {:base "%rsi"})
                     ms)]
            (.append ^StringBuilder *asm* dtext)
            (loop [k 0]
              (let [rem (- sz k)
                    [w r] (cond (>= rem 8) [8 "%rdx"] (>= rem 4) [4 "%edx"] (>= rem 2) [2 "%dx"] (>= rem 1) [1 "%dl"] :else [0 nil])]
                (when (pos? w)
                  (emitf "  mov" (sfx-of w) " " (render-mem (update ms :disp (fnil + 0) k)) ", " r)
                  (emitf "  mov" (sfx-of w) " " r ", " (render-mem (update md :disp (fnil + 0) k)))
                  (recur (+ k w))))))
          true)))))

(defn gen-assign-nv
  "Assignment whose value is not used."
  [n]
  (let [{:keys [lhs rhs]} n
        ty (:type lhs)
        int? (or (t/integer? ty) (t/ptr? ty))
        sz (t/type-size ty)
        upd (when (and int? (#{4 8} sz)) (in-place-update lhs rhs sz))]
    (cond
      (and (t/struct? ty) (gen-struct-copy-nv lhs rhs))
      nil
      ;; x = x op y with x in a register
      (and upd (vreg (:var lhs)))
      (let [r (vreg (:var lhs)) [ins o] upd
            o (if (= o :computed) (do (gen-expr (:rhs rhs)) (if (= sz 8) "%rax" "%eax")) o)]
        (emitf "  " ins (sfx-of sz) " " o ", " (if (= sz 8) (r 0) (r 1))))
      ;; m = m op y on a pure memory lvalue
      (and int? (#{4 8} sz) (not= :var (:op lhs)) (simple-lvalue-op? lhs) (pure? lhs)
           (#{:add :sub :bitand :bitor :bitxor} (:op rhs)) (= lhs (:lhs rhs))
           (= sz (t/type-size (:type rhs))) (pure? (:rhs rhs)))
      (let [ins (case (:op rhs) :add "add" :sub "sub" :bitand "and" :bitor "or" :bitxor "xor")
            ro (int-operand (:rhs rhs) sz)
            src (cond (and ro (not (str/includes? ro "("))) ro
                      (rdi-addr? lhs) (do (gen-expr (:rhs rhs)) (if (= sz 8) "%rax" "%eax"))
                      :else (do (gen-expr (:rhs rhs)) (emit "  movq %rax, %rsi") (if (= sz 8) "%rsi" "%esi")))]
        (emitf "  " ins (sfx-of sz) " " src ", " (render-mem (addr-operand lhs))))
      ;; impure address, rax-only value: address first, then the value
      (and int? (not= :var (:op lhs)) (simple-lvalue-op? lhs) (not (pure? lhs)) (rax-only? rhs))
      (let [m (addr-operand lhs)
            m (if (or (= "%rax" (:base m)) (= "%rax" (:index m)))
                (do (emitf "  leaq " (render-mem m) ", %rsi") {:base "%rsi"})
                m)]
        (gen-expr rhs)
        (store-op ty (render-mem m)))
      ;; impure address, any pure value: park the address in a scratch register
      (and int? (not= :var (:op lhs)) (simple-lvalue-op? lhs) (not (pure? lhs)) (pure? rhs)
           *scratch-free* (seq @*scratch-free*))
      (let [sr (first @*scratch-free*)
            _ (vswap! *scratch-free* rest)
            m (addr-operand lhs)]
        (emitf "  leaq " (render-mem m) ", " sr)
        (gen-expr rhs)
        (store-op ty (str "(" sr ")"))
        (vswap! *scratch-free* conj sr))
      ;; x = x op y with x in memory and y an immediate or register
      (and upd (var-mem-op (:var lhs)) (not (and (string? (second upd)) (str/includes? (second upd) "("))))
      (let [[ins o] upd
            o (if (= o :computed) (do (gen-expr (:rhs rhs)) (if (= sz 8) "%rax" "%eax")) o)]
        (emitf "  " ins (sfx-of sz) " " o ", " (var-mem-op (:var lhs))))
      (and int? (= :var (:op lhs)) (vreg (:var lhs)) (load-to-reg! (:var lhs) rhs))
      nil
      (and int? (= :var (:op lhs)) (vreg (:var lhs)) (#{4 8} sz) (int-operand rhs sz))
      (let [r (vreg (:var lhs)) o (int-operand rhs sz) rr (if (= sz 8) (r 0) (r 1))]
        (if (= o "$0")
          (emitf "  xorl " (r 1) ", " (r 1))
          (when-not (= o rr) (emitf "  mov" (sfx-of sz) " " o ", " rr))))
      ;; y = x +/- c with x, y in registers: lea
      (and int? (= :var (:op lhs)) (vreg (:var lhs)) (#{4 8} sz)
           (#{:add :sub} (:op rhs)) (= :var (:op (:lhs rhs))) (vreg (:var (:lhs rhs)))
           (= sz (t/type-size (:type (:lhs rhs)))) (= :num (:op (:rhs rhs))) (imm32? (:val (:rhs rhs))))
      (let [c (:val (:rhs rhs)) c (if (= :sub (:op rhs)) (- c) c)
            src ((vreg (:var (:lhs rhs))) 0) dst (vreg (:var lhs))]
        (emitf "  lea" (sfx-of sz) " " c "(" src "), " (if (= sz 8) (dst 0) (dst 1))))
      (and int? (scalar-var? lhs) (var-mem-op (:var lhs)) (store-src rhs sz))
      (emitf "  mov" (sfx-of sz) " " (store-src rhs sz) ", " (var-mem-op (:var lhs)))
      (and int? (not= :var (:op lhs)) (simple-lvalue-op? lhs) (pure? lhs) (store-src rhs sz))
      (let [o (store-src rhs sz) m (addr-operand lhs)]
        (emitf "  mov" (sfx-of sz) " " o ", " (render-mem m)))
      ;; store through an address needing only %rdi
      (and int? (not= :var (:op lhs)) (rdi-addr? lhs))
      (do (gen-expr rhs) (store-op ty (render-mem (addr-operand lhs))))
      ;; store through a pure address of a computed integer value
      ;; store through a pure address needing no code
      (and int? (not= :var (:op lhs)) (simple-lvalue-op? lhs) (pure? lhs)
           (= "" (second (capture #(addr-operand lhs)))))
      (do (gen-expr rhs) (store-op ty (render-mem (addr-operand lhs))))
      (and int? (not= :var (:op lhs)) (simple-lvalue-op? lhs) (pure? lhs))
      (do (gen-expr rhs)
          (emit "  movq %rax, %rsi")
          (let [op (render-mem (addr-operand lhs))]
            (emitf "  mov" (sfx-of sz) " " (case (int sz) 1 "%sil" 2 "%si" 4 "%esi" 8 "%rsi") ", " op)))
      :else (gen-expr-pop n))))

(defn gen-incdec-nv [n]
  (let [e (:expr n) ty (:type n) sz (t/type-size ty) d (:delta n)]
    (cond
      (and (= :var (:op e)) (vreg (:var e)) (#{4 8} sz))
      (let [r (vreg (:var e))] (emitf "  add" (sfx-of sz) " $" d ", " (if (= sz 8) (r 0) (r 1))))
      (and (= :var (:op e)) (var-mem-op (:var e)))
      (emitf "  add" (sfx-of sz) " $" d ", " (var-mem-op (:var e)))
      (and (simple-lvalue-op? e) (pure? e) (not= :var (:op e)))
      (emitf "  add" (sfx-of sz) " $" d ", " (render-mem (addr-operand e)))
      :else (gen-expr-pop n))))

(defn gen-discard
  "Evaluate n for its side effects."
  [n]
  (case (:op n)
    :assign (gen-assign-nv n)
    :incdec (gen-incdec-nv n)
    :comma (do (gen-discard (:lhs n)) (gen-discard (:rhs n)))
    :sra-copy (gen-sra-copy n)
    :memset-inline (gen-memset-inline n)
    :cond (if (and (:then n) (:els n))
            (let [l-else (new-label) l-end (new-label)]
              (gen-branch (:cond n) l-else false)
              (gen-discard (:then n))
              (emitf "  jmp " l-end)
              (emitf l-else ":")
              (gen-discard (:els n))
              (emitf l-end ":"))
            (gen-expr-pop n))
    :cast (if (= :void (:kind (:type n))) (gen-discard (:expr n)) (gen-expr-pop n))
    (if (and (pure? n) (not (t/flonum? (:type n)))) nil (gen-expr-pop n))))

(defn gen-expr-rare
  "gen-expr for the less common operators (apart, so that gen-expr stays small enough to JIT-compile)."
  [n]
  (case (:op n)
    :memset-inline (gen-memset-inline n)
    :incdec (if (and (= :var (:op (:expr n))) (vreg (:var (:expr n))))
              (let [v (:var (:expr n)) ty (:type n) sz (t/type-size ty) d (:delta n) r (vreg v)]
                (if (>= sz 4)
                  (let [rr (if (= sz 8) (r 0) (r 1)) ax (if (= sz 8) "%rax" "%eax") sfx (if (= sz 8) "q" "l")]
                    (when-not (:pre n) (emitf "  mov" sfx " " rr ", " ax))
                    (emitf "  add" sfx " $" d ", " rr)
                    (when (:pre n) (emitf "  mov" sfx " " rr ", " ax)))
                  (do (emitf "  movl " (r 1) ", %eax")
                      (emitf "  leal " d "(%rax), %edi")
                      (emit (case [(int sz) (t/unsigned? ty)]
                              [1 true] "  movzbl %dil, %edi" [1 false] "  movsbl %dil, %edi"
                              [2 true] "  movzwl %di, %edi" [2 false] "  movswl %di, %edi"))
                      (emitf "  movl %edi, " (r 1))
                      (when (:pre n) (emit "  movl %edi, %eax")))))
            (let [ty (:type n) sz (t/type-size ty) d (:delta n)]
              (gen-addr (:expr n))
              (emit "  movq %rax, %rdx")
              (load ty)
              (emitf "  leaq " d "(%rax), %rdi")
              (case (int sz)
                1 (emit "  movb %dil, (%rdx)")
                2 (emit "  movw %di, (%rdx)")
                4 (emit "  movl %edi, (%rdx)")
                8 (emit "  movq %rdi, (%rdx)"))
              (when (:pre n)
                (case (int sz)
                  1 (emit (if (t/unsigned? ty) "  movzbl %dil, %eax" "  movsbl %dil, %eax"))
                  2 (emit (if (t/unsigned? ty) "  movzwl %di, %eax" "  movswl %di, %eax"))
                  4 (emit "  movl %edi, %eax")
                  8 (emit "  movq %rdi, %rax")))))
    :memzero (let [v (:var n)]
               (if-let [r (vreg v)]
                 (emitf "  xorl " (r 1) ", " (r 1))
               (do
               (local-addr v "%rdi")
               (emitf "  movq $" (t/type-size (:type v)) ", %rcx")
               (emit "  xorl %eax, %eax")
               (emit "  rep stosb"))))
    :neg (let [ty (:type n)]
           (gen-expr (:expr n))
           (case (:kind ty)
             :float (do (emit "  movl $0x80000000, %eax") (emit "  movd %eax, %xmm1") (emit "  xorps %xmm1, %xmm0"))
             :double (do (emit "  movabsq $0x8000000000000000, %rax") (emit "  movq %rax, %xmm1") (emit "  xorpd %xmm1, %xmm0"))
             :ldouble (emit "  fchs")
             (if (= 8 (t/type-size ty)) (emit "  negq %rax") (emit "  negl %eax"))))
    :bitnot (do (gen-expr (:expr n))
                (if (= 8 (t/type-size (:type n))) (emit "  notq %rax") (emit "  notl %eax")))
    :stmt-expr (let [body (:body (:body n))]
                 (doseq [s (butlast body)] (gen-stmt s))
                 (when-let [l (last body)]
                   (if (= :expr-stmt (:op l)) (gen-expr (:expr l)) (gen-stmt l))))
    :label-addr (emitf "  leaq " (:label n) "(%rip), %rax")
    :va-start (gen-va-start n)
    :va-arg (gen-va-arg n)
    :va-copy (do (gen-expr (:rhs n)) (push!) (gen-expr (:lhs n)) (pop! "%rcx")
                 (emit-copy "%rcx" "%rax" 24))
    :vla-alloc (let [v (:var n)]
                 (gen-expr (:size n))
                 (emit "  addq $15, %rax")
                 (emit "  andq $-16, %rax")
                 (emit "  subq %rax, %rsp")
                 (emitf "  movq %rsp, " (var-offset v) "(%rbp)"))
    :trap (emit "  ud2")
    :fabs (do (gen-expr (:expr n))
              (if (= :float (:kind (:type n)))
                (do (emit "  movl $0x7fffffff, %eax") (emit "  movd %eax, %xmm1") (emit "  andps %xmm1, %xmm0"))
                (do (emit "  movabsq $0x7fffffffffffffff, %rax") (emit "  movq %rax, %xmm1") (emit "  andpd %xmm1, %xmm0"))))
    :save-sp (emitf "  movq %rsp, " (var-offset (:var n)) "(%rbp)")
    :restore-sp (emitf "  movq " (var-offset (:var n)) "(%rbp), %rsp")
    :alloca (let [d @*depth*]
              (gen-expr (:expr n))
              (emit "  addq $15, %rax")
              (emit "  andq $-16, %rax")
              (emit "  movq %rsp, %rsi")
              (emit "  subq %rax, %rsp")
              ;; move any outstanding pushed temporaries below the new block
              (dotimes [i d]
                (emitf "  movq " (* 8 i) "(%rsi), %rdx")
                (emitf "  movq %rdx, " (* 8 i) "(%rsp)"))
              (emitf "  leaq " (* 8 d) "(%rsp), %rax"))
    :bitop (let [w (= 8 (t/type-size (:type (:expr n))))
                 sfx (if w "q" "l") ax (if w "%rax" "%eax") cx (if w "%rcx" "%ecx")]
             (gen-expr (:expr n))
             (case (:name n)
               :popcount (emitf "  popcnt" sfx " " ax ", " ax)
               :parity (do (emitf "  popcnt" sfx " " ax ", " ax) (emit "  andl $1, %eax"))
               :clz (do (emitf "  bsr" sfx " " ax ", " ax) (emitf "  xor" sfx " $" (if w 63 31) ", " ax))
               :ctz (emitf "  bsf" sfx " " ax ", " ax)
               :ffs (do (emitf "  bsf" sfx " " ax ", " cx)
                        (emitf "  mov" sfx " $-1, " ax)
                        (emitf "  cmovne" sfx " " cx ", " ax)
                        (emit "  incl %eax"))
               :clrsb (do ;; leading redundant sign bits: clz(x ^ (x >> (bits-1))) - 1
                        (emitf "  mov" sfx " " ax ", " cx)
                        (emitf "  sar" sfx " $" (if w 63 31) ", " cx)
                        (emitf "  xor" sfx " " cx ", " ax)
                        (emitf "  bsr" sfx " " ax ", " cx)
                        (emitf "  mov" sfx " $-1, " ax)
                        (emitf "  cmovne" sfx " " cx ", " ax)
                        (emitf "  neg" sfx " " ax)
                        (emitf "  add" sfx " $" (if w 62 30) ", " ax))))
    :bswap (let [sz (t/type-size (:type n))]
             (gen-expr (:expr n))
             (case (int sz)
               2 (do (emit "  rolw $8, %ax") (emit "  movzwl %ax, %eax"))
               4 (emit "  bswapl %eax")
               8 (emit "  bswapq %rax")))
    :overflow (let [rty (:rtype n) sz (t/type-size rty) rs (not (t/unsigned? rty))]
                (gen-expr (:ptr n)) (push!)
                (gen-expr (:lhs n)) (push!)
                (gen-expr (:rhs n))
                (emit "  movq %rax, %rcx")
                (pop! "%rax")
                ;; 128-bit operands: rsi:rax and rdi:rcx
                (if (:lsigned n) (do (emit "  movq %rax, %rsi") (emit "  sarq $63, %rsi")) (emit "  xorl %esi, %esi"))
                (if (:rsigned n) (do (emit "  movq %rcx, %rdi") (emit "  sarq $63, %rdi")) (emit "  xorl %edi, %edi"))
                (case (:kind n)
                  :add (do (emit "  addq %rcx, %rax") (emit "  adcq %rdi, %rsi"))
                  :sub (do (emit "  subq %rcx, %rax") (emit "  sbbq %rdi, %rsi"))
                  :mul (do (emit "  movq %rax, %r8")
                           (emit "  imulq %rcx, %rsi")      ; a_hi * b_lo
                           (emit "  imulq %r8, %rdi")       ; a_lo * b_hi
                           (emit "  addq %rdi, %rsi")
                           (emit "  mulq %rcx")             ; rdx:rax = a_lo * b_lo
                           (emit "  addq %rdx, %rsi")))
                ;; does the 128-bit value fit in the result type?
                (case (int sz)
                  1 (emit (if rs "  movsbq %al, %r10" "  movzbq %al, %r10"))
                  2 (emit (if rs "  movswq %ax, %r10" "  movzwq %ax, %r10"))
                  4 (emit (if rs "  movslq %eax, %r10" "  movl %eax, %r10d"))
                  8 (emit "  movq %rax, %r10"))
                (if rs (do (emit "  movq %r10, %r11") (emit "  sarq $63, %r11")) (emit "  xorl %r11d, %r11d"))
                (emit "  xorq %rax, %r10")
                (emit "  xorq %rsi, %r11")
                (emit "  orq %r10, %r11")
                (emit "  setne %r10b")
                (pop! "%rdi")
                (case (int sz)
                  1 (emit "  movb %al, (%rdi)")
                  2 (emit "  movw %ax, (%rdi)")
                  4 (emit "  movl %eax, (%rdi)")
                  8 (emit "  movq %rax, (%rdi)"))
                (emit "  movzbl %r10b, %eax"))
    (cerr (str "cannot generate expression " (:op n)))))

(defn gen-expr [n]
  (case (:op n)
    :sra-copy (gen-sra-copy n)
    :num
    (let [ty (:type n)]
      (cond
        (= :float (:kind ty))
        (do (emitf "  movl $" (Float/floatToRawIntBits (unchecked-float (:fval n))) ", %eax")
            (emit "  movd %eax, %xmm0"))
        (= :double (:kind ty))
        (do (emitf "  movabsq $" (Double/doubleToRawLongBits (double (:fval n))) ", %rax")
            (emit "  movq %rax, %xmm0"))
        (= :ldouble (:kind ty))
        (let [[m e] (or (:lbits n) [0 0])
              l (str ".L.ld." (vswap! *counter* inc))]
          (vswap! *ldconsts* conj [l m e])
          (emitf "  fldt " l "(%rip)"))
        :else
        (let [v (:val n)]
          (if (imm32? v)
            (emitf "  movq $" v ", %rax")
            (emitf "  movabsq $" v ", %rax")))))

    :var (let [v (:var n) ty (:type n)]
           (cond
             (vreg v) (reg-load v)
             (and (t/scalar? ty) (var-mem-op v)) (load-op ty (var-mem-op v))
             :else (do (gen-addr n) (load ty))))
    :member (cond
              (:bitfield (:member n))
              (let [m (addr-operand n)]
                (load-op (bf-unit (:member n)) (render-mem m))
                (gen-bitfield-load (:member n)))
              (t/scalar? (:type n)) (load-op (:type n) (render-mem (addr-operand n)))
              :else (gen-addr n))
    :deref (if (t/scalar? (:type n))
             (load-op (:type n) (render-mem (addr-operand n)))
             (if (t/func? (:type n))
               (gen-expr (:expr n))
               (materialize (addr-operand n))))
    :addr (gen-addr (:expr n))
    :assign (gen-assign n)
    :comma (do (gen-discard (:lhs n)) (gen-expr (:rhs n)))
    :cast (let [from (:type (:expr n)) to (:type n)
                o (when (and (t/integer? from) (= 4 (t/type-size from)) (or (t/integer? to) (t/ptr? to))
                             (= 8 (t/type-size to)))
                    (int-operand (:expr n) 4))]
            (cond
              (and o (not (str/starts-with? o "$")))
              (emitf (if (t/unsigned? from) "  movl " "  movslq ") o (if (t/unsigned? from) ", %eax" ", %rax"))
              ;; char/short register variables are kept extended to 32 bits
              (and (t/integer? from) (#{1 2} (t/type-size from)) (= :var (:op (:expr n))) (vreg (:var (:expr n)))
                   (or (t/integer? to) (t/ptr? to)) (#{4 8} (t/type-size to)) (not= :bool (:kind to)))
              (let [r ((vreg (:var (:expr n))) 1)]
                (if (= 8 (t/type-size to)) (emitf "  movslq " r ", %rax") (emitf "  movl " r ", %eax")))
              :else (do (gen-expr (:expr n)) (gen-cast from to))))
    :not (if (int-cmp-node? (:expr n))
           (let [cc (gen-int-compare (:expr n))]
             (emitf "  set" (cc-negate cc) " %al")
             (emit "  movzbl %al, %eax"))
           (do (gen-expr (:expr n))
               (cmp-zero (:type (:expr n)))
               (emit "  sete %al")
               (emit "  movzbl %al, %eax")))
    (:logand :logor)
    (let [l-false (new-label) l-end (new-label)]
      (gen-branch n l-false false)
      (emit "  movl $1, %eax")
      (emitf "  jmp " l-end)
      (emitf l-false ":")
      (emit "  xorl %eax, %eax")
      (emitf l-end ":"))
    :cond
    (let [l-else (new-label) l-end (new-label)]
      (gen-branch (:cond n) l-else false)
      (gen-expr (:then n))
      (emitf "  jmp " l-end)
      (emitf l-else ":")
      (gen-expr (:els n))
      (emitf l-end ":"))
    (:add :sub :mul :div :mod :bitand :bitor :bitxor :shl :shr :eq :ne :lt :le :gt :ge)
    (gen-binary n)
    :call (gen-call n)
    (gen-expr-rare n)))

;; ---------------------------------------------------------------------------
;; Statements

(defn gen-return-value [e]
  (let [ty (:type e)]
    (gen-expr e)
    (when (t/struct? ty)
      (let [cls (struct-classes ty)]
        (if (nil? cls)
          (do (emitf "  movq " (:ret-ptr-off *fninfo*) "(%rbp), %rdi")
              (emit-copy "%rax" "%rdi" (t/type-size ty))
              (emit "  movq %rdi, %rax"))
          (let [sz (t/type-size ty)]
            (emit "  movq %rax, %rsi")
            (loop [i 0 gi 0 fi 0]
              (when (< i (count cls))
                (let [off (* 8 i) rem (- sz off)]
                  (if (= :sse (nth cls i))
                    (do (if (>= rem 8)
                          (emitf "  movsd " off "(%rsi), %xmm" fi)
                          (emitf "  movss " off "(%rsi), %xmm" fi))
                        (recur (inc i) gi (inc fi)))
                    (let [reg (nth ["%rax" "%rdx"] gi)
                          r32 (nth ["%eax" "%edx"] gi)]
                      (cond
                        (>= rem 8) (emitf "  movq " off "(%rsi), " reg)
                        (= rem 4) (emitf "  movl " off "(%rsi), " r32)
                        :else
                        (do (emitf "  xorl " r32 ", " r32)
                            (doseq [b (reverse (range rem))]
                              (emitf "  shlq $8, " reg)
                              (emitf "  movb " (+ off b) "(%rsi), %r8b")
                              (emit "  movzbq %r8b, %r8")
                              (emitf "  orq %r8, " reg))))
                      (recur (inc i) (inc gi) fi))))))))))))

(defn jump-table-plan
  "A dense switch gets a jump table: {:lo :span :targets} or nil."
  [cases]
  (when (seq cases)
    (let [lo (reduce min (map :lo cases))
          hi (reduce max (map :hi cases))
          n (reduce + (map #(inc (- (:hi %) (:lo %))) cases))
          span (inc (- hi lo))]
      (when (and (>= n 4) (<= -2147483648 lo) (<= hi 2147483647) (<= span (+ 10 (* 3 n))) (<= span 4096))
        (let [tv (object-array span)]
          ;; first matching case wins (cases are in source order)
          (doseq [{l :lo h :hi label :label} (reverse cases)
                  v (range l (inc h))]
            (aset tv (- v lo) label))
          {:lo lo :span span :targets (vec tv)})))))

(defn gen-stmt [s]
  (case (:op s)
    :block (doseq [x (:body s)] (gen-stmt x))
    :expr-stmt (gen-discard (:expr s))
    :return (do (when-let [e (:expr s)] (gen-return-value e))
                (emitf "  jmp .L.return." (:name *fn*)))
    :if (let [l-else (new-label) l-end (new-label)]
          (gen-branch (:cond s) l-else false)
          (gen-stmt (:then s))
          (if (:els s)
            (do (emitf "  jmp " l-end)
                (emitf l-else ":")
                (gen-stmt (:els s))
                (emitf l-end ":"))
            (emitf l-else ":")))
    :for (let [l-begin (new-label) l-body (new-label)
               _ (when *loop-depth* (vswap! *loop-depth* inc))]
           ;; loop rotation: test at the bottom
           (when (:init s) (gen-stmt (:init s)))
           (if (:cond s)
             (emitf "  jmp " l-begin)
             nil)
           (emitf l-body ":")
           (gen-stmt (:body s))
           (emitf (:cont s) ":")
           (when (:inc s) (gen-discard (:inc s)))
           (emitf l-begin ":")
           (if (:cond s)
             (gen-branch (:cond s) l-body true)
             (emitf "  jmp " l-body))
           (when *loop-depth* (vswap! *loop-depth* dec))
           (emitf (:brk s) ":"))
    :do (let [l-begin (new-label)
              _ (when *loop-depth* (vswap! *loop-depth* inc))]
          (emitf l-begin ":")
          (gen-stmt (:body s))
          (emitf (:cont s) ":")
          (gen-branch (:cond s) l-begin true)
          (when *loop-depth* (vswap! *loop-depth* dec))
          (emitf (:brk s) ":"))
    :switch (let [cty (:type (:cond s))
                  w (= 8 (t/type-size cty))
                  ax (if w "%rax" "%eax") di (if w "%rdi" "%edi") sfx (if w "q" "l")
                  u (t/unsigned? cty)]
              (gen-expr (:cond s))
              (if-let [jt (jump-table-plan (:cases s))]
                (let [{:keys [lo span targets]} jt
                      deflt (or (:default s) (:brk s))
                      tab (new-label)]
                  (if w
                    (do (emitf "  subq $" lo ", %rax")
                        (emitf "  cmpq $" (dec span) ", %rax"))
                    (do (when-not (zero? lo) (emitf "  subl $" lo ", %eax"))
                        (emitf "  cmpl $" (dec span) ", %eax")
                        (when (zero? lo) (emit "  movl %eax, %eax"))))
                  (emitf "  ja " deflt)
                  (emitf "  leaq " tab "(%rip), %rdx")
                  (emit "  movslq (%rdx,%rax,4), %rax")
                  (emit "  addq %rdx, %rax")
                  (emit "  jmp *%rax")
                  (emit "  .section .rodata")
                  (emit "  .align 4")
                  (emitf tab ":")
                  (doseq [t targets] (emitf "  .long " (or t deflt) "-" tab))
                  (emit "  .text"))
              (do
              (doseq [{:keys [lo hi label]} (:cases s)]
                (if (= lo hi)
                  (if (imm32? lo)
                    (do (emitf "  cmp" sfx " $" lo ", " ax) (emitf "  je " label))
                    (do (emitf "  movabsq $" lo ", %rdi") (emit "  cmpq %rdi, %rax") (emitf "  je " label)))
                  (do (emitf "  mov" sfx " " ax ", " di)
                      (if (imm32? lo)
                        (emitf "  sub" sfx " $" lo ", " di)
                        (do (emitf "  movabsq $" lo ", %rsi") (emit "  subq %rsi, %rdi")))
                      (let [d (- hi lo)]
                        (if (imm32? d)
                          (emitf "  cmp" sfx " $" d ", " di)
                          (do (emitf "  movabsq $" d ", %rsi") (emit "  cmpq %rsi, %rdi"))))
                      (emitf "  jbe " label))))
              (emitf "  jmp " (or (:default s) (:brk s)))))
              (gen-stmt (:body s))
              (emitf (:brk s) ":"))
    :label (do (emitf (:label s) ":") (gen-stmt (:body s)))
    :goto (do (when-let [slot (some-> (:restore s) deref)]
                (emitf "  movq " (var-offset slot) "(%rbp), %rsp"))
              (emitf "  jmp " (:label s)))
    :goto-ptr (do (gen-expr (:expr s)) (emit "  jmp *%rax"))
    (cerr (str "cannot generate statement " (:op s)))))

;; ---------------------------------------------------------------------------
;; Functions

(defn assign-offsets
  "Returns [offsets-map frame-size fninfo param-stores]."
  [f]
  (let [ty (:type f)
        ret (:ret ty)
        ret-mem (and (t/struct? ret) (nil? (struct-classes ret)))
        params (:params f)
        ;; classify params
        [pinfo gp fp stack]
        (loop [ps params gp (if ret-mem 1 0) fp 0 stack 16 out []]
          (if (empty? ps)
            [out gp fp stack]
            (let [p (first ps) pty (:type p)]
              (cond
                (t/struct? pty)
                (let [cls (struct-classes pty)
                      ng (count (filter #(= :int %) cls))
                      nf (count (filter #(= :sse %) cls))]
                  (if (and cls (<= (+ gp ng) 6) (<= (+ fp nf) 8))
                    (recur (rest ps) (+ gp ng) (+ fp nf) stack (conj out {:var p :mode :struct-reg :classes cls :gp gp :fp fp}))
                    (let [st (if (>= (t/type-align pty) 16) (+ 16 (align-to (- stack 16) 16)) stack)]
                      (recur (rest ps) gp fp (+ st (align-to (t/type-size pty) 8))
                             (conj out {:var p :mode :stack :off st})))))
                (t/ldouble? pty)
                (let [st (+ 16 (align-to (- stack 16) 16))]
                  (recur (rest ps) gp fp (+ st 16) (conj out {:var p :mode :stack :off st})))
                (t/flonum? pty)
                (if (< fp 8)
                  (recur (rest ps) gp (inc fp) stack (conj out {:var p :mode :fp :reg fp}))
                  (recur (rest ps) gp fp (+ stack 8) (conj out {:var p :mode :stack :off stack})))
                :else
                (if (< gp 6)
                  (recur (rest ps) (inc gp) fp stack (conj out {:var p :mode :gp :reg gp}))
                  (recur (rest ps) gp fp (+ stack 8) (conj out {:var p :mode :stack :off stack})))))))
        stack-param-ids (into {} (for [pi pinfo :when (= :stack (:mode pi))] [(:id (:var pi)) (:off pi)]))
        ;; allocate frame
        [offsets bottom]
        (reduce (fn [[m bottom] v]
                  (if-let [o (stack-param-ids (:id v))]
                    [(assoc m (:id v) o) bottom]
                    (let [over (> (or (:align v) (t/type-align (:type v))) 16)
                          sz (cond over 8
                                   (t/scalar? (:type v)) (max 1 (t/type-size (:type v)))
                                   :else (align-to (max 1 (t/type-size (:type v))) 8))
                          al (if over 8 (max (or (:align v) (t/type-align (:type v))) 1))
                          al (if (and (t/array? (:type v)) (>= (t/type-size (:type v)) 16)) (max al 16) al)
                          bottom (align-to (+ bottom sz) al)]
                      [(assoc m (:id v) (- bottom)) bottom])))
                [{} 0] (:locals f))
        bottom (if ret-mem (+ bottom 8) bottom)
        ret-ptr-off (when ret-mem (- bottom))
        frame (align-to bottom 16)]
    [offsets frame {:gp gp :fp fp :stack-bytes (- stack 16) :ret-ptr-off ret-ptr-off
                    :overaligned (vec (filter #(and (not (stack-param-ids (:id %)))
                                                    (> (or (:align %) (t/type-align (:type %))) 16))
                                              (:locals f)))}
     pinfo]))

(defn store-param [pi off]
  (let [v (:var pi) ty (:type v)]
    (case (:mode pi)
      :gp (let [r (:reg pi)]
            (case (int (t/type-size ty))
              1 (emitf "  movb " (nth argreg8 r) ", " off "(%rbp)")
              2 (emitf "  movw " (nth argreg16 r) ", " off "(%rbp)")
              4 (emitf "  movl " (nth argreg32 r) ", " off "(%rbp)")
              8 (emitf "  movq " (nth argreg64 r) ", " off "(%rbp)")))
      :fp (if (= :float (:kind ty))
            (emitf "  movss %xmm" (:reg pi) ", " off "(%rbp)")
            (emitf "  movsd %xmm" (:reg pi) ", " off "(%rbp)"))
      :struct-reg
      (loop [cls (:classes pi) i 0 gp (:gp pi) fp (:fp pi)]
        (when (seq cls)
          (if (= :sse (first cls))
            (do (emitf "  movsd %xmm" fp ", " (+ off (* 8 i)) "(%rbp)")
                (recur (rest cls) (inc i) gp (inc fp)))
            (do (emitf "  movq " (nth argreg64 gp) ", " (+ off (* 8 i)) "(%rbp)")
                (recur (rest cls) (inc i) (inc gp) fp)))))
      :stack nil)))

(defn usage-info
  "Walk a function body: weighted use counts of variables, variables whose
   address is taken, and variables that must stay in memory."
  [body]
  (let [weights (volatile! {}) addr (volatile! #{}) pinned (volatile! #{})
        unsafe (volatile! #{}) path (volatile! []) seen (volatile! #{}) has-call (volatile! #{})]
    (letfn [(walk [x depth]
              (cond
                (map? x)
                (when-let [op (:op x)]
                  (when (= op :call) (vswap! has-call into @path))
                  (when (or (#{:overflow :va-arg :va-start :va-copy :alloca :vla-alloc :goto-ptr} op)
                            (and (= op :assign) (= :member (:op (:lhs x))) (:bitfield (:member (:lhs x))))
                            (and (= op :return) (:expr x) (t/struct? (:type (:expr x)))))
                    (vswap! unsafe into @path))
                  (when (:scope-id x) (vswap! path conj (:scope-id x)) (vswap! seen conj (:scope-id x)))
                  (case op
                    :var (vswap! weights update (:id (:var x)) (fnil + 0) (long (Math/pow 8 (min depth 5))))
                    :addr (when (= :var (:op (:expr x))) (vswap! addr conj (:id (:var (:expr x)))))
                    (:save-sp :restore-sp :vla-alloc :memzero) (when-let [v (:var x)] (vswap! pinned conj (:id v)))
                    :goto (when-let [slot (some-> (:restore x) deref)] (vswap! pinned conj (:id slot)))
                    nil)
                  (let [inner (case op (:for :do) (inc depth) depth)]
                    (reduce-kv (fn [_ k v]
                                 (case k
                                   (:var :type :member :restore :cases :fn-type :ret-buf :tmp) nil
                                   (walk v (case k (:cond :inc :body) inner depth)))
                                 nil)
                               nil x)
                    (when (= op :call) (walk (:fn x) depth)))
                  (when (:scope-id x) (vswap! path pop)))
                (vector? x) (doseq [y x] (walk y depth))
                :else nil))]
      (walk body 0))
    {:weights @weights :addr @addr :pinned @pinned :unsafe @unsafe :seen @seen :has-call @has-call}))

(defn choose-regvars
  "Assign callee-saved registers to the most used eligible scalar locals."
  [f]
  (let [{:keys [weights addr pinned unsafe seen has-call]} (usage-info (:body f))
        ;; r8/r9 (saved around calls) for variables of scopes without special operations
        r89-ok? (fn [v] (let [b (:block v)]
                          (if (seq b)
                            (and (contains? seen (peek b)) (not (contains? unsafe (peek b))))
                            (and (empty? unsafe) (not (:va-area f))
                                 (< (count (:params f)) 5)))))
        call-free? (fn [v] (let [b (:block v)]
                             (if (seq b) (not (contains? has-call (peek b))) (empty? has-call))))
        ok? (fn [v]
              (let [ty (:type v)]
                (and (or (t/integer? ty) (t/ptr? ty))
                     (#{1 2 4 8} (t/type-size ty))
                     (not (:volatile ty))
                     (not (:vla-ptr v))
                     (not (:align v))
                     (not (contains? addr (:id v)))
                     (not (contains? pinned (:id v)))
                     (pos? (get weights (:id v) 0)))))
        cands (->> (:locals f) (filter ok?) (sort-by #(- (get weights (:id %) 0))))
        ;; variables of disjoint scopes may share a register
        prefix? (fn [a b] (and (<= (count a) (count b)) (= a (subvec b 0 (count a)))))
        conflict? (fn [a b] (let [pa (vec (or (:block a) [])) pb (vec (or (:block b) []))]
                              (or (prefix? pa pb) (prefix? pb pa))))
        nregs (count callee-regs)]
    (loop [cs cands assigned [] out {}]
      (if (empty? cs)
        (with-meta out {:save (set (keep (fn [[v r]] (when (and (#{5 6} r) (not (call-free? v))) r)) assigned))
                        :addr addr :pinned pinned})
        (let [v (first cs)
              taken (set (keep (fn [[w r]] (when (conflict? v w) r)) assigned))
              order (cond (not (r89-ok? v)) (range n-callee-saved)
                          (call-free? v) (concat [5 6] (range n-callee-saved) [7 8])
                          :else (range n-callee-saved))
              r (first (remove taken order))]
          (if r
            (recur (rest cs) (conj assigned [v r]) (assoc out (:id v) r))
            (recur (rest cs) assigned out)))))))

;; ---------------------------------------------------------------------------
;; memcpy/memset with a small constant size, value unused: open-coded copies/stores

(def mem-inline-max 64)

(defn blob-type
  "A struct type of n bytes, alignment 1, with no members (for byte copies)."
  [n]
  (let [id [:ccc-blob n]]
    (when-not (contains? @t/*structs* id)
      (swap! t/*structs* assoc id {:complete true :kind :struct :size n :align 1 :members []}))
    {:kind :struct :id id}))

(defn const-size-arg [a]
  (let [a (if (and (= :cast (:op a)) (t/integer? (:type a))) (:expr a) a)]
    (when (and (= :num (:op a)) (not (:fval a)) (t/integer? (:type a)))
      (:val a))))

(defn retype-ptr
  "Pointer node p seen as pointing to type ty."
  [p ty]
  (let [inner (if (and (= :cast (:op p)) (t/ptr? (:type p)) (t/ptr? (:type (:expr p)))) (:expr p) p)]
    (if (and (t/ptr? (:type inner)) (#{:var :add :sub :member :deref :call :addr} (:op inner)))
      (assoc inner :type (t/ptr-to ty))
      {:op :cast :type (t/ptr-to ty) :expr p})))

(defn lower-mem-call
  "Rewrite a discarded memcpy/memset call node, or return it unchanged."
  [x]
  (let [fnode (:fn x)
        nm (when (and (= :call (:op x)) (= :var (:op fnode)) (not (:local (:var fnode)))
                      (t/func? (:type fnode)) (= 3 (count (:args x))))
             (:label (:var fnode)))]
    (if (and (#{"memcpy" "memset"} nm) (not (contains? *defined* nm)))
      (let [[d s c] (:args x)
            n (const-size-arg c)]
        (cond
          (not (and n (<= 1 n mem-inline-max))) x
          (= nm "memcpy")
          (let [bt (blob-type n)]
            {:op :assign :type bt
             :lhs {:op :deref :type bt :expr (retype-ptr d bt)}
             :rhs {:op :deref :type bt :expr (retype-ptr s bt)}})
          :else
          (if-let [v (const-size-arg s)]
            {:op :memset-inline :type t/ty-void :dst d :val (bit-and v 255) :size n}
            x)))
      x)))

(defn lower-mem-builtins
  "Open-code memcpy/memset calls whose size is a small constant and whose value is unused."
  [body]
  (letfn [(disc [e]
            (case (:op e)
              :call (lower-mem-call (rw e))
              :comma (assoc e :lhs (disc (:lhs e)) :rhs (disc (:rhs e)))
              :cast (if (= :void (:kind (:type e))) (assoc e :expr (disc (:expr e))) (rw e))
              (rw e)))
          (rw [x]
            (cond
              (map? x)
              (if-let [op (:op x)]
                (case op
                  :expr-stmt (assoc x :expr (disc (:expr x)))
                  :comma (assoc x :lhs (disc (:lhs x)) :rhs (rw (:rhs x)))
                  (persistent! (reduce-kv (fn [acc k v]
                                            (if (or (map? v) (vector? v))
                                              (case k
                                                (:type :var :member :fn-type :cases :restore :ret-buf :tmp) acc
                                                (assoc! acc k (if (and (= k :inc) (= op :for)) (disc v) (rw v))))
                                              acc))
                                          (transient x) x)))
                x)
              (vector? x) (mapv rw x)
              :else x))]
    (rw body)))

(defn gen-memset-inline [n]
  (let [sz (:size n) v (:val n)
        pat (reduce (fn [acc _] (bit-or (bit-shift-left acc 8) v)) 0 (range 8))]
    (gen-expr (:dst n))
    (if (zero? v)
      (emit "  xorl %edx, %edx")
      (emitf "  movabsq $" pat ", %rdx"))
    (loop [k 0]
      (let [rem (- sz k)
            [w r] (cond (>= rem 8) [8 "%rdx"] (>= rem 4) [4 "%edx"] (>= rem 2) [2 "%dx"] (>= rem 1) [1 "%dl"] :else [0 nil])]
        (when (pos? w)
          (emitf "  mov" (sfx-of w) " " r ", " (if (zero? k) "" k) "(%rax)")
          (recur (+ k w)))))))

(def sra-skip-keys #{:type :member :cases :fn-type})

(defn sra-candidates [f]
  (let [params (set (map :id (:params f)))]
    (into {}
          (for [v (:locals f)
                :let [ty (:type v) ms (:members (t/struct-info ty))]
                :when (and (= :struct (:kind ty)) (not (contains? params (:id v)))
                           (not (:align v)) (not (:volatile ty)) (not (:vla-ptr v))
                           (seq ms) (<= (count ms) 8) (<= (t/type-size ty) 64)
                           (every? (fn [m] (and (not (:bitfield m))
                                                (or (t/integer? (:type m)) (t/ptr? (:type m)))
                                                (not (:volatile (:type m)))
                                                (#{1 2 4 8} (t/type-size (:type m)))))
                                   ms)
                           ;; members must not overlap
                           (let [spans (sort (map (fn [m] [(:offset m) (+ (:offset m) (t/type-size (:type m)))]) ms))]
                             (every? (fn [[[_ e] [b _]]] (<= e b)) (partition 2 1 spans))))]
            [(:id v) v]))))

(defn sra-function
  "Scalar replacement of small local structs that are only accessed through
   scalar members and whole-struct copies from lvalues."
  [f]
  (let [cands (if (System/getenv "CCC_NOSRA") {} (sra-candidates f))]
    (if (empty? cands)
      f
      (let [bad (volatile! #{})
            cand-var? (fn [x] (and (map? x) (= :var (:op x)) (contains? cands (:id (:var x)))))
            mentions? (fn mentions? [x id]
                        (cond (and (map? x) (:local x) (= id (:id x))) true
                              (map? x) (some (fn [[k v]] (and (not (sra-skip-keys k)) (mentions? v id))) x)
                              (vector? x) (some #(mentions? % id) x)
                              :else false))]
        (letfn [(chk [x ctx]
                  (cond
                    (map? x)
                    (let [op (:op x)]
                      (cond
                        (nil? op) (if (contains? cands (:id x))
                                    (vswap! bad conj (:id x))
                                    (doseq [[k v] x] (when-not (sra-skip-keys k) (chk v :value))))
                        (= :var op) (when (contains? cands (:id (:var x))) (vswap! bad conj (:id (:var x))))
                        (and (= :member op) (cand-var? (:expr x)) (t/scalar? (:type x))) nil
                        (and (= :addr op) (= :member (:op (:expr x))) (cand-var? (:expr (:expr x))))
                        (vswap! bad conj (:id (:var (:expr (:expr x)))))
                        (= :assign op)
                        (if (cand-var? (:lhs x))
                          (if (and (= ctx :discard) (#{:deref :member :var} (:op (:rhs x)))
                                   (t/struct? (:type (:rhs x)))
                                   (= (:id (:type (:rhs x))) (:id (:type (:lhs x))))
                                   (not (mentions? (:rhs x) (:id (:var (:lhs x))))))
                            (chk (:rhs x) :value)
                            (do (vswap! bad conj (:id (:var (:lhs x)))) (chk (:rhs x) :value)))
                          (do (chk (:lhs x) :value) (chk (:rhs x) :value)))
                        (= :expr-stmt op) (chk (:expr x) :discard)
                        (= :comma op) (do (chk (:lhs x) :discard) (chk (:rhs x) ctx))
                        :else
                        (doseq [[k v] x]
                          (when-not (sra-skip-keys k)
                            (chk v (if (and (= op :for) (= k :inc)) :discard :value))))))
                    (vector? x) (doseq [y x] (chk y ctx))
                    :else nil))]
          (chk (:body f) :value))
        (let [good (apply dissoc cands @bad)]
          (if (empty? good)
            f
            (let [newvars (into {}
                                (for [[id v] good]
                                  [id (into {}
                                            (for [m (:members (t/struct-info (:type v)))]
                                              [(:offset m)
                                               (cond-> {:name (str (:name v) "." (:name m))
                                                        :type (:type m) :id [:sra id (:offset m)] :local true}
                                                 (:block v) (assoc :block (:block v)))]))]))
                  good-var? (fn [x] (and (map? x) (= :var (:op x)) (contains? good (:id (:var x)))))]
              (letfn [(rw [x]
                        (cond
                          (map? x)
                          (cond
                            (and (= :member (:op x)) (good-var? (:expr x)))
                            (let [nv (get-in newvars [(:id (:var (:expr x))) (:offset (:member x))])]
                              {:op :var :var nv :type (:type x)})
                            (and (= :assign (:op x)) (good-var? (:lhs x)))
                            (let [vs (sort-by key (newvars (:id (:var (:lhs x)))))]
                              {:op :sra-copy :type t/ty-void :src (rw (:rhs x))
                               :offs (mapv key vs)
                               :targets (mapv (fn [[_ nv]] {:op :var :var nv :type (:type nv)}) vs)})
                            :else
                            (persistent! (reduce-kv (fn [m k v] (if (sra-skip-keys k) m (assoc! m k (rw v))))
                                                    (transient x) x)))
                          (vector? x) (mapv rw x)
                          :else x))]
                (let [body (rw (:body f))
                      used (volatile! #{})
                      _ ((fn walk [x]
                           (cond (map? x) (do (when (= :var (:op x)) (vswap! used conj (:id (:var x))))
                                              (doseq [[k v] x] (when-not (#{:targets :type :member :var} k) (walk v))))
                                 (vector? x) (run! walk x)
                                 :else nil)) body)
                      used @used
                      prune (fn prune [x]
                              (cond
                                (map? x)
                                (if (= :sra-copy (:op x))
                                  (let [keep (filterv #(contains? used (:id (:var (first %)))) (map vector (:targets x) (:offs x)))]
                                    (assoc x :src (prune (:src x)) :targets (mapv first keep) :offs (mapv second keep)))
                                  (persistent! (reduce-kv (fn [m k v] (if (sra-skip-keys k) m (assoc! m k (prune v))))
                                                          (transient x) x)))
                                (vector? x) (mapv prune x)
                                :else x))]
                  (assoc f
                         :body (prune body)
                         :locals (vec (concat (remove #(contains? good (:id %)) (:locals f))
                                              (filter #(contains? used (:id %)) (mapcat vals (vals newvars)))))))))))))))

(defn gen-sra-copy [n]
  (let [m (addr-operand (:src n))]
    (doseq [[tn off] (map vector (:targets n) (:offs n))]
      (let [v (:var tn) ty (:type v) sz (t/type-size ty) u (t/unsigned? ty)
            src (render-mem (update m :disp (fnil + 0) off))]
        (if-let [r (vreg v)]
          (case (int sz)
            1 (emitf (if u "  movzbl " "  movsbl ") src ", " (r 1))
            2 (emitf (if u "  movzwl " "  movswl ") src ", " (r 1))
            4 (emitf "  movl " src ", " (r 1))
            8 (emitf "  movq " src ", " (r 0)))
          (let [dst (str (var-offset v) "(%rbp)")]
            (case (int sz)
              1 (do (emitf "  movb " src ", %cl") (emitf "  movb %cl, " dst))
              2 (do (emitf "  movw " src ", %cx") (emitf "  movw %cx, " dst))
              4 (do (emitf "  movl " src ", %ecx") (emitf "  movl %ecx, " dst))
              8 (do (emitf "  movq " src ", %rcx") (emitf "  movq %rcx, " dst)))))))))

(defn gen-function [f]
  (let [f (sra-function (assoc f :body (lower-mem-builtins (:body f))))
        [offsets frame info pinfo] (assign-offsets f)
        name (:name f)
        regvars (choose-regvars f)
        regset (set (vals regvars))
        body-sb (StringBuilder.)
        used (volatile! regset)
        spill-max (volatile! 0)]
    (binding [*offsets* offsets
              *fn* f
              *fninfo* info
              *overaligned* (set (map :id (:overaligned info)))
              *regvars* regvars
              *temp-free* (volatile! (vec (reverse (remove regset (range n-callee-saved)))))
              *temp-used* used
              *loop-depth* (volatile! 0)
              *spill-free* (volatile! [])
              *spill-max* spill-max
              *spill-base* (+ frame 48)
              *r89-slots* (vec (for [i [5 6] :when (contains? (:save (meta regvars)) i)]
                                 [((nth callee-regs i) 0)
                                  (let [k @spill-max] (vswap! spill-max inc) (str (- (+ frame 48 (* 8 (inc k)))) "(%rbp)"))]))
              *scratch-free* (volatile! (remove #(or (and (= % "%r10") (contains? regset 7))
                                                     (and (= % "%r11") (contains? regset 8)))
                                                (list "%r11" "%r10")))
              *asm* body-sb
              *depth* (volatile! 0)]
      (doseq [v (:overaligned info)]
        (let [al (or (:align v) (t/type-align (:type v)))]
          (emitf "  subq $" (align-to (max 1 (t/type-size (:type v))) 16) ", %rsp")
          (emitf "  andq $" (- al) ", %rsp")
          (emitf "  movq %rsp, " (offsets (:id v)) "(%rbp)")))
      (when (:ret-ptr-off info)
        (emitf "  movq %rdi, " (:ret-ptr-off info) "(%rbp)"))
      (when-let [va (:va-area f)]
        (let [o (offsets (:id va))]
          (doseq [i (range 6)] (emitf "  movq " (nth argreg64 i) ", " (+ o (* 8 i)) "(%rbp)"))
          (doseq [i (range 8)] (emitf "  movsd %xmm" i ", " (+ o 48 (* 16 i)) "(%rbp)"))))
      (doseq [pi (sort-by #(if (#{5 6} (get regvars (:id (:var %)))) 1 0) pinfo)]
        (if-let [r (vreg (:var pi))]
          (let [ty (:type (:var pi)) sz (t/type-size ty) u (t/unsigned? ty)]
            (case (:mode pi)
              :gp (let [i (:reg pi)]
                    (case (int sz)
                      1 (emitf (if u "  movzbl " "  movsbl ") (nth argreg8 i) ", " (r 1))
                      2 (emitf (if u "  movzwl " "  movswl ") (nth argreg16 i) ", " (r 1))
                      4 (emitf "  movl " (nth argreg32 i) ", " (r 1))
                      8 (emitf "  movq " (nth argreg64 i) ", " (r 0))))
              :stack (do (load-op ty (str (offsets (:id (:var pi))) "(%rbp)"))
                         (reg-store (:var pi)))))
          (store-param pi (offsets (:id (:var pi))))))
      (gen-stmt (:body f))
      (when (= name "main") (emit "  movl $0, %eax")))
    ;; the rest (machine-level optimisation of the body, then the prologue,
    ;; knowing which callee-saved registers the body uses) depends only on the
    ;; body text: returned as a function, run in parallel by generate
    (let [{:keys [addr pinned]} (meta regvars)
          over-ids (set (map :id (:overaligned info)))
          exposed (vec (for [v (:locals f)
                             :let [o (get offsets (:id v)) ty (:type v)]
                             :when (and o (or (not (t/scalar? ty)) (contains? addr (:id v)) (contains? pinned (:id v))
                                              (:volatile ty) (:vla-ptr v) (:align v) (contains? over-ids (:id v))))]
                         [o (+ o (max 16 (t/type-size ty)))]))
          txt (str body-sb)
          spill-max @spill-max]
      (fn []
        (let [body-txt (if (System/getenv "CCC_NOASMOPT")
                         txt
                         (let [setjmp? (re-find #"call (_setjmp|setjmp|sigsetjmp|__sigsetjmp|_longjmp|vfork|savectx|getcontext)\b" txt)]
                           (mach/optimize-body txt {:exposed exposed :no-slots (boolean setjmp?) :name name})))
              saved (sort (filter (fn [i] (and (< i n-callee-saved)
                                               (some #(str/includes? body-txt %) (nth callee-regs i))))
                                  (range n-callee-saved)))
              save-bytes (align-to (* 8 n-callee-saved) 16)
              slot (fn [i] (- (+ frame (* 8 (inc i)))))
              out (StringBuilder.)]
          (binding [*asm* out]
            (when-not (:static f) (emitf "  .globl " name))
            (emit "  .text")
            (when-let [al (:align f)] (emitf "  .align " al))
            (emitf "  .type " name ", @function")
            (emitf name ":")
            (emit "  pushq %rbp")
            (emit "  movq %rsp, %rbp")
            (let [sz (cond (pos? spill-max) (align-to (+ frame 48 (* 8 spill-max)) 16)
                           (seq saved) (+ frame save-bytes)
                           :else frame)]
              (when (pos? sz) (emitf "  subq $" sz ", %rsp")))
            (doseq [i saved]
              (emitf "  movq " ((nth callee-regs i) 0) ", " (slot i) "(%rbp)"))
            (.append out ^String body-txt)
            (emitf ".L.return." name ":")
            (doseq [i saved]
              (emitf "  movq " (slot i) "(%rbp), " ((nth callee-regs i) 0)))
            (emit "  leave")
            (emit "  ret")
            (emitf "  .size " name ", .-" name))
          (str out))))))

;; ---------------------------------------------------------------------------
;; Data

(defn emit-data [g]
  (let [ty (:type g)
        sz (if (:init g) (alength ^bytes (:bytes (:init g))) (t/type-size ty))
        base-al (or (:align g) (t/type-align ty))
        agg (#{:array :struct :union} (:kind ty))
        ;; gcc's x86-64 data alignment: aggregates >= 32 bytes get 32, >= 16 get 16;
        ;; long string constants get 32
        al (cond (:align g) base-al
                 (:strlit g) (if (>= sz 32) (max base-al 32) base-al)
                 (and agg (>= sz 32)) (max base-al 32)
                 (and agg (>= sz 16)) (max base-al 16)
                 :else base-al)
        label (:label g)
        init (:init g)
        init (when init
               (if (and (not (:strlit g)) (empty? (:relocs init)) (every? zero? (:bytes init)))
                 nil
                 init))]
    (when-not (:static g) (emitf "  .globl " label))
    (if init
      (let [^bytes bs (:bytes init)
            relocs (:relocs init)]
        (emit (cond (:strlit g) "  .section .rodata"
                    (empty? relocs) "  .data"
                    (every? #(contains? *defined* (:label %)) relocs) "  .section .data.rel.local,\"aw\""
                    :else "  .section .data.rel,\"aw\""))
        (emitf "  .align " al)
        (when-not (str/starts-with? label ".L")
          (emitf "  .type " label ", @object")
          (emitf "  .size " label ", " sz))
        (emitf label ":")
        (loop [pos 0 rs relocs]
          (cond
            (>= pos sz) nil
            (and (seq rs) (= pos (:off (first rs))))
            (let [r (first rs)]
              (let [sz (or (:size r) 8)]
                (emitf (case (int sz) 1 "  .byte " 2 "  .short " 4 "  .long " "  .quad ")
                       (:label r) (if (neg? (:addend r)) "" "+") (:addend r))
                (recur (+ pos sz) (rest rs))))
            :else
            (let [end (min sz (if (seq rs) (:off (first rs)) sz))
                  zrun (fn [p] (loop [q p] (if (and (< q end) (zero? (aget bs q))) (recur (inc q)) q)))]
              ;; emit bytes pos..end, compress zero runs
              (loop [p pos]
                (when (< p end)
                  (let [zr (zrun p)]
                    (if (>= (- zr p) 8)
                      (do (emitf "  .zero " (- zr p)) (recur zr))
                      (let [e (loop [q p cnt 0]
                                (if (or (>= q end) (>= cnt 16)
                                        (and (> cnt 0) (>= (- (zrun q) q) 8)))
                                  q
                                  (recur (inc q) (inc cnt))))]
                        (emitf "  .byte " (str/join "," (map #(bit-and (aget bs %) 0xff) (range p e))))
                        (recur e))))))
              (recur end rs)))))
      (do
        (emit "  .bss")
        (emitf "  .align " al)
        (when-not (str/starts-with? label ".L")
          (emitf "  .type " label ", @object")
          (emitf "  .size " label ", " sz))
        (emitf label ":")
        (emitf "  .zero " (max sz 1))))))

(defn thread-jumps
  "Retarget jumps whose target label is followed by an unconditional jmp."
  [lines]
  (let [n (count lines)
        label? (fn [^String l] (and (str/ends-with? l ":") (not (str/starts-with? l " "))))
        alias (loop [i 0 m {}]
                (if (>= i n) m
                    (let [l (lines i)]
                      (if (label? l)
                        (let [j (loop [j (inc i)] (if (and (< j n) (label? (lines j))) (recur (inc j)) j))
                              nx (when (< j n) (lines j))]
                          (recur (inc i)
                                 (if (and nx (str/starts-with? nx "  jmp ") (not (str/starts-with? nx "  jmp *")))
                                   (assoc m (subs l 0 (dec (count l))) (subs nx 6))
                                   m)))
                        (recur (inc i) m)))))
        resolve (fn [t] (loop [t t seen #{}]
                          (if-let [u (alias t)]
                            (if (contains? seen u) t (recur u (conj seen t)))
                            t)))]
    (if (empty? alias)
      lines
      (mapv (fn [^String l]
              (if-let [[_ ins t] (and (.startsWith l "  j") (re-matches #"  (j[a-z]+) (\S+)" l))]
                (if (= ins "jmp*") l (str "  " ins " " (resolve t)))
                l))
            lines))))

(def reg-family
  (into {} (for [[q d w b] [["rax" "eax" "ax" "al"] ["rbx" "ebx" "bx" "bl"] ["rcx" "ecx" "cx" "cl"]
                            ["rdx" "edx" "dx" "dl"] ["rsi" "esi" "si" "sil"] ["rdi" "edi" "di" "dil"]
                            ["rbp" "ebp" "bp" "bpl"] ["rsp" "esp" "sp" "spl"]
                            ["r8" "r8d" "r8w" "r8b"] ["r9" "r9d" "r9w" "r9b"] ["r10" "r10d" "r10w" "r10b"]
                            ["r11" "r11d" "r11w" "r11b"] ["r12" "r12d" "r12w" "r12b"] ["r13" "r13d" "r13w" "r13b"]
                            ["r14" "r14d" "r14w" "r14b"] ["r15" "r15d" "r15w" "r15b"]]
                 n [q d w b]]
             [(str "%" n) q])))

(def dest-last-ops
  #{"mov" "movq" "movl" "movw" "movb" "movslq" "movzbl" "movzwl" "movsbl" "movswl" "movsbq" "movzbq"
    "movswq" "movzwq" "movsbw" "movzbw" "leaq" "leal" "addq" "addl" "subq" "subl" "andq" "andl" "orq" "orl"
    "xorq" "xorl" "imulq" "imull" "shlq" "shll" "sarq" "sarl" "shrq" "shrl" "salq" "sall"
    "addw" "addb" "subw" "subb" "andw" "andb" "orw" "orb" "xorw" "xorb"
    "sete" "setne" "setl" "setle" "setg" "setge" "setb" "setbe" "seta" "setae" "setp" "setnp"
    "negq" "negl" "notq" "notl" "incq" "incl" "decq" "decl"})
(def no-write-ops #{"cmpq" "cmpl" "cmpw" "cmpb" "testq" "testl" "testw" "testb"})

(defn- kill-family
  "known (register family -> source family) without entries mentioning f."
  [known f]
  (if (empty? known) {} (into {} (remove (fn [[k v]] (or (= k f) (= v f))) known))))

(defn drop-redundant-ext
  "Remove 32->64 sign extensions repeated within a basic block while neither
   register changed in between. (The operands of a line are split only when
   they matter: for movslq, and for writes while some extension is known.)"
  [lines]
  (let [out (java.util.ArrayList.)]
    (loop [ls (seq lines) known {}]
      (if-not ls
        (vec out)
        (let [^String l (first ls)]
          (cond
            (or (not (.startsWith l "  ")) (.startsWith l "  .")) (do (.add out l) (recur (next ls) {}))
            :else
            (let [^String t (str/trim l)
                  sp (str/index-of t " ")
                  op (if sp (subs t 0 sp) t)]
              (cond
                (= op "movslq")
                (let [args (if sp (str/split (subs t (inc sp)) #", ") [])
                      dst (last args)
                      fam (reg-family dst)]
                  (if (and (= 2 (count args)) fam (reg-family (first args)))
                    (let [src (reg-family (first args))]
                      (if (and (= (known fam) src) (not= fam src))
                        (recur (next ls) known)
                        (do (.add out l)
                            (recur (next ls) (cond-> (kill-family known fam) (not= fam src) (assoc fam src))))))
                    ;; movslq is a dest-last op
                    (do (.add out l)
                        (recur (next ls) (if fam
                                           (kill-family known fam)
                                           (if (and dst (str/starts-with? dst "%")) {} known))))))
                (contains? no-write-ops op) (do (.add out l) (recur (next ls) known))
                (str/starts-with? op "j") (do (.add out l) (recur (next ls) known))
                (contains? dest-last-ops op)
                (do (.add out l)
                    (recur (next ls) (if (empty? known)
                                       {}
                                       (let [args (if sp (str/split (subs t (inc sp)) #", ") [])
                                             dst (last args)
                                             fam (reg-family dst)]
                                         (if fam
                                           (kill-family known fam)
                                           (if (and dst (str/starts-with? dst "%")) {} known))))))
                (#{"cqto" "cltd" "cqo"} op) (do (.add out l) (recur (next ls) (kill-family known "rdx")))
                (#{"cltq"} op) (do (.add out l) (recur (next ls) (kill-family known "rax")))
                (#{"idivq" "idivl" "divq" "divl"} op) (do (.add out l) (recur (next ls) (-> known (kill-family "rax") (kill-family "rdx"))))
                :else (do (.add out l) (recur (next ls) {}))))))))))

(defn rdi-dead-after?
  "True when %rdi is overwritten (without being read) by the lines following
   index i, before any control transfer."
  [lines i]
  (let [n (count lines)]
    (loop [j (inc i) steps 0]
      (if (or (>= j n) (> steps 24))
        false
        (let [^String l (lines j)]
          (cond
            (not (str/starts-with? l "  ")) false
            (re-find #"^  (j|call|ret|leave|push|pop|rep|cqto|cltd|idiv|div|stos|movs)" l) false
            :else
            (let [t (str/trim l)
                  sp (str/index-of t " ")
                  args (if sp (subs t (inc sp)) "")
                  parts (str/split args #", ")
                  dst (last parts)
                  srcs (str/join ", " (butlast parts))
                  op (if sp (subs t 0 sp) t)
                  reads-rdi? (fn [x] (boolean (re-find #"%(rdi|edi|di|dil)\b" x)))]
              (cond
                (and (re-matches #"(mov[lq]|movslq|movz[bw]l|movs[bw]l|movs[bw]q|leaq|leal|movabsq)" op)
                     (#{"%rdi" "%edi"} dst) (not (reads-rdi? srcs)))
                true
                (reads-rdi? args) false
                :else (recur (inc j) (inc steps))))))))))

(defn peephole
  "Simple cleanups on the emitted lines."
  [^String text]
  (let [lines (drop-redundant-ext (thread-jumps (vec (split-lines* text))))
        n (count lines)
        out (java.util.ArrayList.)]
    (loop [i 0]
      (when (< i n)
        (let [^String l (lines i)
              nx (when (< (inc i) n) (lines (inc i)))]
          (cond
            ;; jump to the very next label
            (and nx (str/starts-with? l "  jmp ") (= (str (subs l 6) ":") nx))
            (recur (inc i))
            ;; sign extension right after a sign-extending 32->64 load
            (and nx (str/starts-with? l "  movslq ") (str/ends-with? l ", %rax") (= nx "  movslq %eax, %rax"))
            (do (.add out l) (recur (+ i 2)))
            ;; address computed only to be loaded through
            (and nx (.startsWith l "  leaq ") (re-matches #"  leaq (-?\d*)\((%r(?:[bcd]x|si|di|bp|8|9|1[0-5]))\), %rax" l)
                 (re-matches #"  (mov[lqwb]|movslq|movz[bw][lq]|movs[bw][lq]) (-?\d*)\(%rax\), %(rax|eax|ax|al)" nx))
            (let [[_ d r] (re-matches #"  leaq (-?\d*)\((%r(?:[bcd]x|si|di|bp|8|9|1[0-5]))\), %rax" l)
                  [_ op d2 dst] (re-matches #"  (mov[lqwb]|movslq|movz[bw][lq]|movs[bw][lq]) (-?\d*)\(%rax\), %(rax|eax|ax|al)" nx)
                  off (+ (if (seq d) (parse-long d) 0) (if (seq d2) (parse-long d2) 0))]
              (.add out (str "  " op " " (if (zero? off) "" off) "(" r "), %" dst))
              (recur (+ i 2)))
            ;; sign extension of a value known non-negative
            (and (= l "  movslq %eax, %rax") (pos? (.size out))
                 (let [prev (.get out (dec (.size out)))]
                   (or (when-let [[_ c] (re-matches #"  andl \$(\d+), %eax" prev)] (< (parse-long c) 2147483648))
                       (when-let [[_ c] (re-matches #"  shrl \$(\d+), %eax" prev)] (pos? (parse-long c)))
                       (re-matches #"  movz[bw]l .*, %eax" prev))))
            (recur (inc i))
            ;; multiplication by a power of two
            (and (.startsWith l "  imulq $") (re-matches #"  imulq \$(2|4|8|16|32|64|128|256|512|1024|2048|4096), (%r[a-z0-9]+), \2" l))
            (let [[_ c r] (re-matches #"  imulq \$(\d+), (%r[a-z0-9]+), \2" l)]
              (.add out (str "  shlq $" (Long/numberOfTrailingZeros (parse-long c)) ", " r))
              (recur (inc i)))
            ;; index moved to %rdi only to be added to a base
            (and nx (= l "  movq %rax, %rdi") (re-matches #"  leaq \((%r[a-z0-9]+),%rdi,1\), %rax" nx)
                 (not (str/starts-with? nx "  leaq (%rax")) (not (str/starts-with? nx "  leaq (%rdi"))
                 (rdi-dead-after? lines (inc i)))
            (let [[_ b] (re-matches #"  leaq \((%r[a-z0-9]+),%rdi,1\), %rax" nx)]
              (.add out (str "  addq " b ", %rax"))
              (recur (+ i 2)))
            ;; zero extension of a value just written by a 32-bit operation
            (and (= l "  movl %eax, %eax")
                 (let [k (loop [k (dec (.size out))]
                           (if (and (>= k 0) (re-matches #"  (cmp|test)[lq] .*" (.get out k))) (recur (dec k)) k))]
                   (and (>= k 0)
                        (re-matches #"  (movl|andl|addl|subl|shrl|sarl|shll|xorl|orl|imull|leal|movzbl|movzwl|movsbl|movswl) [^,]*(, [^,]*)?, %eax"
                                    (.get out k)))))
            (recur (inc i))
            ;; sign extension of a zero-extended byte/word
            (and nx (.startsWith l "  movz") (re-matches #"  movz[bw]l .*, %e(ax|cx|dx|si|di)" l)
                 (= nx (str "  movslq %e" (subs l (- (count l) 2)) ", %r" (subs l (- (count l) 2)))))
            (do (.add out l) (recur (+ i 2)))
            ;; push/pop pair
            (and nx (= l "  pushq %rax") (str/starts-with? nx "  popq %"))
            (do (when (not= nx "  popq %rax") (.add out (str "  movq %rax, " (subs nx 7))))
                (recur (+ i 2)))
            :else (do (.add out l) (recur (inc i)))))))
    (str (str/join "\n" out) "\n")))

(defn node-count [x]
  (cond (map? x) (if (:op x)
                   (reduce + 1 (map (fn [[k v]] (if (#{:type :var :member :fn-type} k) 0 (node-count v))) x))
                   0)
        (vector? x) (reduce + 0 (map node-count x))
        :else 0))

(def no-inline-ops #{:stmt-expr :label-addr :alloca :va-arg :va-start :va-copy :vla-alloc :goto-ptr
                     :save-sp :restore-sp :memzero :asm})

(defn inline-candidate
  "For a function whose body is a single return statement over scalar params,
   returns {:params :expr :ret}; nil otherwise."
  [f]
  (let [ty (:type f) body (:body f) params (:params f)
        pids (set (map :id params))
        items (when (= :block (:op body)) (:body body))
        st (when (= 1 (count items)) (first items))
        e (when (and (= :return (:op st)) (:expr st)) (:expr st))
        ret (:ret ty)
        bad? (fn bad? [x]
               (cond (map? x) (or (contains? no-inline-ops (:op x))
                                  (and (= :addr (:op x)) (= :var (:op (:expr x))) (contains? pids (:id (:var (:expr x)))))
                                  (some (fn [[k v]] (and (not (#{:type :var :member :fn-type} k)) (bad? v))) x))
                     (vector? x) (some bad? x)
                     :else false))]
    (when (and e (:proto ty) (not (:variadic ty)) (not (:va-area f)) (not (:knr-real ty))
               (or (t/integer? ret) (t/ptr? ret))
               (every? #(contains? pids (:id %)) (:locals f))
               (every? (fn [p] (and (or (t/integer? (:type p)) (t/ptr? (:type p)))
                                    (not (:volatile (:type p))))) params)
               (= (count params) (count (:params ty)))
               (<= (node-count e) 40)
               (not (bad? e)))
      {:params params :expr e :ret ret})))

(def ^:dynamic *inline-depth* 3)

(defn count-calls [x]
  (cond (map? x) (+ (if (= :call (:op x)) 1 0)
                    (reduce + 0 (map (fn [[k v]] (if (#{:type :var :member :fn-type} k) 0 (count-calls v))) x)))
        (vector? x) (reduce + 0 (map count-calls x))
        :else 0))

(defn inline-calls
  "Replace calls to small single-expression functions by their bodies (one level)."
  [f cands]
  (if (empty? cands)
    f
    (let [temps (volatile! []) counter (volatile! 0)]
      (letfn [(subst [x m]
                (cond (map? x) (cond
                                 (and (= :var (:op x)) (contains? m (:id (:var x))))
                                 (assoc x :var (m (:id (:var x))))
                                 (:op x)
                                 (persistent! (reduce-kv (fn [acc k v] (if (#{:type :var :member :fn-type} k) acc (assoc! acc k (subst v m))))
                                                         (transient x) x))
                                 :else x)
                      (vector? x) (mapv #(subst % m) x)
                      :else x))
              (inl-call? [x]
                (cond (map? x) (or (= :stmt-expr (:op x))
                                   (and (= :call (:op x)) (= :var (:op (:fn x))) (contains? cands (:label (:var (:fn x)))))
                                   (some (fn [[k v]] (and (not (#{:type :var :member :fn-type} k)) (inl-call? v))) x))
                      (vector? x) (some inl-call? x)
                      :else false))
              (rw [x path depth]
                (cond
                  (map? x)
                  (let [path (if (:scope-id x) (conj path (:scope-id x)) path)
                        ntemps (count @temps)
                        nested? (inl-call? (:args x))
                        x (if (:op x)
                            (persistent! (reduce-kv (fn [acc k v] (if (#{:type :var :member :fn-type} k) acc (assoc! acc k (rw v path depth))))
                                                    (transient x) x))
                            x)
                        fnode (:fn x)]
                    (if-let [c (and (= :call (:op x)) (= :var (:op fnode)) (not (:local (:var fnode)))
                                    (t/func? (:type fnode))
                                    (:proto (:fn-type x))
                                    (get cands (:label (:var fnode))))]
                      (if (not= (count (:args x)) (count (:params c)))
                        x
                        (let [ps (:params c)
                              uid (vswap! counter inc)
                              blk (when path (if (or nested? (> (count @temps) ntemps)) path (conj path [:inl uid])))
                              tvs (mapv (fn [p]
                                          (cond-> {:name (str "inl." (:name p)) :type (t/strip (:type p))
                                                   :id [:inl (:name f) (vswap! counter inc)] :local true}
                                            (seq blk) (assoc :block blk)))
                                        ps)
                              _ (vswap! temps into tvs)
                              m (zipmap (map :id ps) tvs)
                              body (subst (:expr c) m)
                              body (if (and (< depth *inline-depth*) (<= (count-calls (:expr c)) 2))
                                     (rw body (conj path [:inl uid]) (inc depth))
                                     body)
                              body (if (= (:kind (:type body)) (:kind (:ret c))) body {:op :cast :type (:ret c) :expr body})
                              assigns (map (fn [tv a] {:op :assign :type (:type tv) :lhs {:op :var :var tv :type (:type tv)} :rhs a})
                                           tvs (:args x))]
                          (reduce (fn [acc a] {:op :comma :type (:type acc) :lhs a :rhs acc})
                                  body (reverse assigns))))
                      x))
                  (vector? x) (mapv #(rw % path depth) x)
                  :else x))]
        (let [root (or (some (fn [v] (first (:block v))) (:locals f)) :root)
              body (rw (:body f) [root] 1)]
          (if (seq @temps)
            (assoc f :body body :locals (into (vec (:locals f)) @temps))
            f))))))

(defn generate [{:keys [globals functions]}]
  (let [sb (StringBuilder.)
        defined (into #{} (concat (map :label (filter #(or (:defined %) (:init %)) globals))
                                  (map :name functions)))]
    (binding [*asm* sb
              *defined* defined
              *ldconsts* (volatile! [])
              *counter* (volatile! 0)]
      (doseq [g globals]
        (when (and (not (:func g)) (or (:defined g) (:init g)))
          (emit-data g)))
      (let [cands (if (System/getenv "CCC_NOINLINE")
                    {}
                    (into {} (keep (fn [f] (when-let [c (inline-candidate f)] [(:name f) c])) functions)))]
        (let [pool (java.util.concurrent.Executors/newFixedThreadPool
                    (max 1 (min 3 (dec (.availableProcessors (Runtime/getRuntime)))))
                    (reify java.util.concurrent.ThreadFactory
                      (newThread [_ r] (doto (Thread. nil r "ccc-mach" (* 256 1024 1024)) (.setDaemon true)))))
              ;; each body is finished on the pool while the next one is generated
              futs (try (mapv (fn [f] (.submit pool ^Callable (gen-function (inline-calls f cands)))) functions)
                        (finally (.shutdown pool)))]
          (doseq [^java.util.concurrent.Future fu futs]
            (.append sb ^String (.get fu)))))
      (when (seq @*ldconsts*)
        (emit "  .section .rodata")
        (doseq [[l m e] @*ldconsts*]
          (emit "  .align 16")
          (emitf l ":")
          (emitf "  .quad " m)
          (emitf "  .short " e)
          (emit "  .zero 6")))
      (emit "  .section .note.GNU-stack,\"\",@progbits"))
    (peephole (str sb))))
