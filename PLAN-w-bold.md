# w-bold plan

## Where the instructions go (group.sql, our build, sqlite3.s only: 312M Ir; gcc -O2 whole program 192M)
- reg-reg mov 20%, other memory mov 15%, frame-slot loads 12%, frame-slot stores 9%, jumps 10%,
  ALU 10%, cmp/test 8%, push/pop 5%, lea 4%, call/ret/leave ~5%.
- Ratios are uniform (1.75-1.85) over all 12 workloads: the cost is the code shape everywhere
  (stack-machine expression code through %rax, values shuttled to %rdi/%r11 and back,
  parameters and locals living in frame slots), not one hot function.

## The restructuring
A machine-level optimiser over each function's emitted instructions (new namespace
`ccc.asmopt`, called from `gen-function` on the body before the prologue is written, so it can
also hand back which callee-saved registers it used):
1. parse each line into an instruction record with exact register/flag read and write sets
   (unknown instructions = full barrier, so it is conservative for any C);
2. basic blocks + CFG (labels, jcc, jmp, jump tables -> treat indirect jmp as "all labels");
3. global liveness of the 16 GPRs + flags;
4. passes, iterated: copy propagation, def retargeting (`op ..., %rax; movq %rax, %rX` with %rax
   dead -> `op ..., %rX`), dead instruction elimination, frame-slot store->load forwarding within
   blocks (codegen tells which rbp slots are non-escaping scalars).
Later: promote whole non-escaping frame slots to free registers (prologue knows used regs).

This removes the shuffle class wholesale and composes with any codegen improvement others make
(it runs after them). Risk: instruction semantics; mitigated by a whitelist with exact semantics
and barrier-on-unknown, verified on corpus + W1-W5 + Lua.
