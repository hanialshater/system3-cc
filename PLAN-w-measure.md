# PLAN — w-measure

Baseline (my build, callgrind, visible workloads): geomean 1.7905; every workload 1.72–1.85.
The uniform ratio says the excess is spread across ordinary code generation, not one hot function.

## Method
1. Instruction-level profile: callgrind --dump-instr=yes on all 12 workloads, join the per-address counts with
   objdump of the binary, and weight each emitted instruction by how often it runs. Then count weighted
   *patterns* (opcode n-grams, recurring idioms such as `movq $0,%rax; leaq k(%rax)`, memcpy calls with constant
   sizes, stack push/pop of temporaries, redundant reloads, flag-materialisation before branches).
2. Rank the patterns by dynamic instruction count saved if fixed; pick the biggest fix that is local (peephole or a
   single codegen case), general, and safe.
3. For every change: rebuild, check the visible workloads' outputs + corpus sample, measure all 12 with callgrind,
   commit only if the geomean falls. Post the measured finding to the board.

## First candidates (seen before profiling at scale)
- `memcpy(p, q, constant)` is called through the PLT; gcc inlines small constant copies as moves.
- `offsetof`-style constants are computed at run time (`movq $0,%rax; leaq 24(%rax),%rax`): fold them.
- `x & k; test` sequences, `movzwl` after a 16-bit and before a 16-bit store.

## Coordination
Claim the narrow area I change (peephole, specific codegen case); keep changes small so they merge cleanly with
other engineers' work.
