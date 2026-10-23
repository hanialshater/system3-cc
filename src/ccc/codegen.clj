(ns ccc.codegen
  "x86-64 AT&T assembly generation from the typed AST (stack-machine style)."
  (:refer-clojure :exclude [integer? pop! load])
  (:require [ccc.types :as t]
            [clojure.string :as str]))

(def ^:dynamic ^StringBuilder *asm* nil)
(def ^:dynamic *depth* nil)     ; volatile: number of 8-byte pushes outstanding
(def ^:dynamic *offsets* nil)   ; map var-id -> rbp offset
(def ^:dynamic *fn* nil)        ; current function map
(def ^:dynamic *defined* nil)   ; set of labels defined in this TU
(def ^:dynamic *counter* nil)   ; volatile
(def ^:dynamic *fninfo* nil)
(def ^:dynamic *overaligned* #{}) ; ids of locals aligned beyond 16, reached through a pointer slot    ; {:gp n :fp n :stack-bytes n :ret-ptr-off n}

(defn emit [^String s] (.append *asm* s) (.append *asm* "\n"))
(defmacro emitf [& parts] `(emit (str ~@parts)))

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

(defn struct-classes
  "Vector of :sse / :int per eightbyte, or nil if passed in memory."
  [ty]
  (let [sz (t/type-size ty)]
    (when (<= sz 16)
      (vec (for [i (range (quot (+ sz 7) 8))]
             (if (has-flonum? ty (* 8 i) (* 8 (inc i)) 0) :sse :int))))))

;; ---------------------------------------------------------------------------
;; Loads, stores, copies

(defn load [ty]
  (case (:kind ty)
    (:array :struct :union :func :vla) nil
    :float (emit "  movss (%rax), %xmm0")
    :double (emit "  movsd (%rax), %xmm0")
    (let [sz (t/type-size ty) u (t/unsigned? ty)]
      (case (int sz)
        1 (emit (if u "  movzbl (%rax), %eax" "  movsbl (%rax), %eax"))
        2 (emit (if u "  movzwl (%rax), %eax" "  movswl (%rax), %eax"))
        4 (emit (if u "  movl (%rax), %eax" "  movslq (%rax), %rax"))
        8 (emit "  movq (%rax), %rax")))))

(defn emit-copy
  "Copy size bytes from (src) to (dst); clobbers %r8."
  [src dst size]
  (loop [off 0]
    (let [rem (- size off)]
      (cond
        (>= rem 8) (do (emitf "  movq " off "(" src "), %r8") (emitf "  movq %r8, " off "(" dst ")") (recur (+ off 8)))
        (>= rem 4) (do (emitf "  movl " off "(" src "), %r8d") (emitf "  movl %r8d, " off "(" dst ")") (recur (+ off 4)))
        (>= rem 2) (do (emitf "  movw " off "(" src "), %r8w") (emitf "  movw %r8w, " off "(" dst ")") (recur (+ off 2)))
        (>= rem 1) (do (emitf "  movb " off "(" src "), %r8b") (emitf "  movb %r8b, " off "(" dst ")") (recur (+ off 1)))
        :else nil))))

(defn store
  "Store value (%rax / %xmm0) to address popped from stack."
  [ty]
  (pop! "%rdi")
  (case (:kind ty)
    (:struct :union) (emit-copy "%rax" "%rdi" (t/type-size ty))
    :float (emit "  movss %xmm0, (%rdi)")
    :double (emit "  movsd %xmm0, (%rdi)")
    (case (int (t/type-size ty))
      1 (emit "  movb %al, (%rdi)")
      2 (emit "  movw %ax, (%rdi)")
      4 (emit "  movl %eax, (%rdi)")
      8 (emit "  movq %rax, (%rdi)"))))

;; ---------------------------------------------------------------------------
;; Casts

(defn tid [ty]
  (case (:kind ty)
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
    (if (and (t/integer? ty) (<= (t/type-size ty) 4))
      (emit "  cmpl $0, %eax")
      (emit "  cmpq $0, %rax"))))

(defn gen-cast [from to]
  (cond
    (= :void (:kind to)) nil
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

(declare gen-expr gen-stmt var-offset)

(defn local-addr
  "Emit the address of local v into reg."
  [v reg]
  (if (or (:vla-ptr v) (contains? *overaligned* (:id v)))
    (emitf "  movq " (var-offset v) "(%rbp), " reg)
    (emitf "  leaq " (var-offset v) "(%rbp), " reg)))

(defn var-offset [v]
  (or (get *offsets* (:id v)) (cerr (str "no offset for variable " (:name v)))))

(defn gen-addr [n]
  (case (:op n)
    :var (let [v (:var n)]
           (if (:local v)
             (local-addr v "%rax")
             (if (contains? *defined* (:label v))
               (emitf "  leaq " (:label v) "(%rip), %rax")
               (emitf "  movq " (:label v) "@GOTPCREL(%rip), %rax"))))
    :deref (gen-expr (:expr n))
    :comma (do (gen-expr (:lhs n)) (gen-addr (:rhs n)))
    :member (do (gen-addr (:expr n))
                (when-not (zero? (:offset (:member n)))
                  (emitf "  addq $" (:offset (:member n)) ", %rax")))
    (:call :assign :cond :stmt-expr :va-arg)
    (if (t/struct? (:type n)) (gen-expr n) (cerr "not an lvalue"))
    :cast (if (t/struct? (:type n)) (gen-addr (:expr n)) (cerr "not an lvalue"))
    (cerr (str "not an lvalue (" (:op n) ")"))))

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

(defn gen-binary [n]
  (let [{:keys [op lhs rhs]} n
        oty (if (#{:eq :ne :lt :le :gt :ge} op) (:type lhs) (:type n))
        oty (if (t/ptr-like? oty) t/ty-ulong oty)]
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
              (emit "  movzbl %al, %eax")))))))

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
              (recur (rest as) gp fp (+ stack words) (conj out {:node a :mode :stack :words words}))))
          (t/flonum? ty)
          (if (< fp 8)
            (recur (rest as) gp (inc fp) stack (conj out {:node a :mode :fp}))
            (recur (rest as) gp fp (inc stack) (conj out {:node a :mode :stack :words 1})))
          :else
          (if (< gp 6)
            (recur (rest as) (inc gp) fp stack (conj out {:node a :mode :gp}))
            (recur (rest as) gp fp (inc stack) (conj out {:node a :mode :stack :words 1}))))))))

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
        {:keys [args stack]} (classify-args (:args n) (if ret-mem 1 0))
        pad (odd? (+ @*depth* stack))]
    (when pad (emit "  subq $8, %rsp") (vswap! *depth* inc))
    ;; stack args, reverse order
    (doseq [a (reverse args)] (when (= :stack (:mode a)) (push-arg! a)))
    ;; register args, reverse order
    (doseq [a (reverse args)] (when (not= :stack (:mode a)) (push-arg! a)))
    (let [direct (and (= :var (:op fnode)) (t/func? (:type fnode)) (not (:local (:var fnode))))]
      (when-not direct
        (gen-expr fnode)
        (emit "  movq %rax, %r10"))
      (when ret-mem
        (local-addr (:ret-buf n) "%rdi"))
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
                            [(if ret-mem 1 0) 0] args)]
        (emitf "  movl $" fp ", %eax")
        (if direct
          (let [label (:label (:var fnode))]
            (if (contains? *defined* label)
              (emitf "  call " label)
              (emitf "  call " label "@PLT")))
          (emit "  call *%r10"))
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
          (emit "  movq %rdi, %rax"))))))

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

(defn gen-expr [n]
  (case (:op n)
    :num
    (let [ty (:type n)]
      (cond
        (= :float (:kind ty))
        (do (emitf "  movl $" (Float/floatToRawIntBits (unchecked-float (:fval n))) ", %eax")
            (emit "  movd %eax, %xmm0"))
        (= :double (:kind ty))
        (do (emitf "  movabsq $" (Double/doubleToRawLongBits (double (:fval n))) ", %rax")
            (emit "  movq %rax, %xmm0"))
        :else
        (let [v (:val n)]
          (if (imm32? v)
            (emitf "  movq $" v ", %rax")
            (emitf "  movabsq $" v ", %rax")))))

    :var (do (gen-addr n) (load (:type n)))
    :member (do (gen-addr n) (load (if (:bitfield (:member n)) (bf-unit (:member n)) (:type n)))
                (when (:bitfield (:member n)) (gen-bitfield-load (:member n))))
    :deref (do (gen-expr (:expr n)) (load (:type n)))
    :addr (gen-addr (:expr n))
    :assign (gen-assign n)
    :incdec (let [ty (:type n) sz (t/type-size ty) d (:delta n)]
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
                  8 (emit "  movq %rdi, %rax"))))
    :comma (do (gen-expr (:lhs n)) (gen-expr (:rhs n)))
    :cast (do (gen-expr (:expr n)) (gen-cast (:type (:expr n)) (:type n)))
    :memzero (let [v (:var n)]
               (local-addr v "%rdi")
               (emitf "  movq $" (t/type-size (:type v)) ", %rcx")
               (emit "  xorl %eax, %eax")
               (emit "  rep stosb"))
    :neg (let [ty (:type n)]
           (gen-expr (:expr n))
           (case (:kind ty)
             :float (do (emit "  movl $0x80000000, %eax") (emit "  movd %eax, %xmm1") (emit "  xorps %xmm1, %xmm0"))
             :double (do (emit "  movabsq $0x8000000000000000, %rax") (emit "  movq %rax, %xmm1") (emit "  xorpd %xmm1, %xmm0"))
             (if (= 8 (t/type-size ty)) (emit "  negq %rax") (emit "  negl %eax"))))
    :bitnot (do (gen-expr (:expr n))
                (if (= 8 (t/type-size (:type n))) (emit "  notq %rax") (emit "  notl %eax")))
    :not (do (gen-expr (:expr n))
             (cmp-zero (:type (:expr n)))
             (emit "  sete %al")
             (emit "  movzbl %al, %eax"))
    (:logand :logor)
    (let [l-short (new-label) l-end (new-label)
          and? (= :logand (:op n))]
      (gen-expr (:lhs n))
      (cmp-zero (:type (:lhs n)))
      (emitf (if and? "  je " "  jne ") l-short)
      (gen-expr (:rhs n))
      (cmp-zero (:type (:rhs n)))
      (emitf (if and? "  je " "  jne ") l-short)
      (emitf "  movl $" (if and? 1 0) ", %eax")
      (emitf "  jmp " l-end)
      (emitf l-short ":")
      (emitf "  movl $" (if and? 0 1) ", %eax")
      (emitf l-end ":"))
    :cond
    (let [l-else (new-label) l-end (new-label)]
      (gen-expr (:cond n))
      (cmp-zero (:type (:cond n)))
      (emitf "  je " l-else)
      (gen-expr (:then n))
      (emitf "  jmp " l-end)
      (emitf l-else ":")
      (gen-expr (:els n))
      (emitf l-end ":"))
    (:add :sub :mul :div :mod :bitand :bitor :bitxor :shl :shr :eq :ne :lt :le :gt :ge)
    (gen-binary n)
    :call (gen-call n)
    :stmt-expr (doseq [s (:body (:body n))] (gen-stmt s))
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

(defn gen-stmt [s]
  (case (:op s)
    :block (doseq [x (:body s)] (gen-stmt x))
    :expr-stmt (gen-expr (:expr s))
    :return (do (when-let [e (:expr s)] (gen-return-value e))
                (emitf "  jmp .L.return." (:name *fn*)))
    :if (let [l-else (new-label) l-end (new-label)]
          (gen-expr (:cond s))
          (cmp-zero (:type (:cond s)))
          (emitf "  je " l-else)
          (gen-stmt (:then s))
          (emitf "  jmp " l-end)
          (emitf l-else ":")
          (when (:els s) (gen-stmt (:els s)))
          (emitf l-end ":"))
    :for (let [l-begin (new-label)]
           (when (:init s) (gen-stmt (:init s)))
           (emitf l-begin ":")
           (when (:cond s)
             (gen-expr (:cond s))
             (cmp-zero (:type (:cond s)))
             (emitf "  je " (:brk s)))
           (gen-stmt (:body s))
           (emitf (:cont s) ":")
           (when (:inc s) (gen-expr (:inc s)))
           (emitf "  jmp " l-begin)
           (emitf (:brk s) ":"))
    :do (let [l-begin (new-label)]
          (emitf l-begin ":")
          (gen-stmt (:body s))
          (emitf (:cont s) ":")
          (gen-expr (:cond s))
          (cmp-zero (:type (:cond s)))
          (emitf "  jne " l-begin)
          (emitf (:brk s) ":"))
    :switch (let [cty (:type (:cond s))
                  w (= 8 (t/type-size cty))
                  ax (if w "%rax" "%eax") di (if w "%rdi" "%edi") sfx (if w "q" "l")
                  u (t/unsigned? cty)]
              (gen-expr (:cond s))
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
              (emitf "  jmp " (or (:default s) (:brk s)))
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
                    (recur (rest ps) gp fp (+ stack (align-to (t/type-size pty) 8))
                           (conj out {:var p :mode :stack :off stack}))))
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

(defn gen-function [f]
  (let [[offsets frame info pinfo] (assign-offsets f)
        name (:name f)]
    (binding [*offsets* offsets
              *fn* f
              *fninfo* info
              *overaligned* (set (map :id (:overaligned info)))
              *depth* (volatile! 0)]
      (when-not (:static f) (emitf "  .globl " name))
      (emit "  .text")
      (when-let [al (:align f)] (emitf "  .align " al))
      (emitf "  .type " name ", @function")
      (emitf name ":")
      (emit "  pushq %rbp")
      (emit "  movq %rsp, %rbp")
      (when (pos? frame) (emitf "  subq $" frame ", %rsp"))
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
      (doseq [pi pinfo]
        (store-param pi (offsets (:id (:var pi)))))
      (gen-stmt (:body f))
      (when (= name "main") (emit "  movl $0, %eax"))
      (emitf ".L.return." name ":")
      (emit "  movq %rbp, %rsp")
      (emit "  popq %rbp")
      (emit "  ret")
      (emitf "  .size " name ", .-" name))))

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

(defn generate [{:keys [globals functions]}]
  (let [sb (StringBuilder.)
        defined (into #{} (concat (map :label (filter #(or (:defined %) (:init %)) globals))
                                  (map :name functions)))]
    (binding [*asm* sb
              *defined* defined
              *counter* (volatile! 0)]
      (doseq [g globals]
        (when (and (not (:func g)) (or (:defined g) (:init g)))
          (emit-data g)))
      (doseq [f functions]
        (gen-function f))
      (emit "  .section .note.GNU-stack,\"\",@progbits"))
    (str sb)))
