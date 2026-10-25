# Benchmark 10 results: make the compiler an optimising one (the full principled org O, against one agent V)

owner: vp-meta · date: 2026-10-24 · status: **final** · protocol and predictions: [B10.md](B10.md) v2 (committed before
the run; two predictions adjusted before it, with the second model's share) · design: [../../PRINCIPLES.md](../../PRINCIPLES.md)

## Result (the full judge on each arm's final committed head, run one after another on the quiet machine)

Score = geometric mean, over 12 hidden SQL workloads, of the instructions the arm's SQLite executes (callgrind, exact)
÷ gcc -O2's; lower is better; the start is 1.791. A version that fails any gate scores 2.0.

| Arm | Score | Hidden instruction ratio | Gate | sqlite3.c compile | Cost (transcripts, list prices) | Active minutes |
|---|---|---|---|---|---|---|
| **O**, the org | **1.256** | 1.256 | **all pass** | 29.7 s | $132.00 (of it about $45 the restart's re-run) | 143 + integration |
| O at round 2 (without round 2b's re-run work) | 1.292 | 1.292 | all pass | 26.5 s | about $87 | 143 |
| **V**, one agent | **2.0** | **about 1.05** (every workload 1.039-1.088) | **fails: 2 hidden W5 tests that the start passes now miscompile** | 21.9 s | **$110.28** | 218 |
| start | 1.791 | 1.791 | all pass | 9.9 s | | |

**By the rules committed before the run, O wins**: V's compiler miscompiles two hidden tests the start passed, so it
scores 2.0. **On the instruction count alone, V is far ahead**: about 1.05 against O's 1.256, for less money, in one
session. Both results are true, and both matter: V nearly matched gcc -O2 (−41% instructions from the start) but
shipped two wrong-code bugs it could not see; O went a third of the way (−30%) and stayed correct.

**V's two miscompiles** (`w5-0343`, `w5-1028`, both reproduce on every run) are integer promotions through
`(unsigned short)` of a `signed char` (`a > (unsigned short)b`; `(unsigned short)c ^ (signed char)0x99999999`): the
narrowing and extension tracking in V's new back end. The SQL corpus (3,000 hidden scripts, all passing), the visible
W1-W5 tests (437, all passing) and Lua did not catch them; the hidden W1-W5 set did. That is what a held-out gate is for.

## What each arm built

- **V** (4 turns; the first two cut by their 90-minute cap, the third ended by V at 36 minutes, the fourth a 2-minute
  tuning pass): a new back end, functions lowered to an intermediate form with virtual registers, linear-scan
  register allocation with lifetime holes and spill rewriting (with a per-function fallback to the old code
  generator), parallel per-function generation, liveness-based dead-definition removal, inline memcpy/memset, narrow
  compares and read-modify-writes, zero-extension tracking, spill-cost tuning. Peak context 668k tokens, **no
  compaction** (a 1M-token context held the whole job).
- **O** (2 rounds of 45 minutes and the round-2b integration): 8 kept merges from all four workers, in codegen and a new
  pass pipeline: w-measure's register-clean argument and assignment codegen and parser changes; w-bold's machine-level
  optimiser (`mach.clj`: slot promotion, then callee-to-caller register renaming); w-sceptic (Sonnet) building on
  w-bold's pass (lea and load folding, known-width extension removal, multiply by 2^k/3/5/9) and a new frame (rsp
  frames, red zone, epilogue copies); w-lean (Sonnet) an inliner and small-frame layout. The architect wrote
  `docs/INTERFACES.md` (the pipeline contract: AST passes slot, mach as the single assembly-level pass) and a profile
  picture each round. Peak context per worker 344-459k, no compactions.

## Predictions

| # | Prediction | Verdict |
|---|---|---|
| B10-1 | O's final ratio ≤ 0.9 × V's | **held by the rules** (V scores 2.0 on the gate); **wrong on the instruction count** (O 1.256, V about 1.05) |
| B10-2 | both arms improve by ≥ 15% (≤ 1.57) | **held on the count** (O 1.256, V 1.05); by the rules only O |
| B10-3 | V's context compacts at least twice | **wrong** (none: peak 668k of a 1M context) |
| B10-4 | O falls in every round, at least 3 | **wrong** (O ran 2 rounds: each round cost $40-63, and the $150 bought two) |
| B10-5 | a kept chain: a new pass another worker's later kept work builds on | **held** (w-bold's `mach.clj`, round 1; w-sceptic's folding and extension removal inside it, round 2; w-bold's renaming on top, round 2b) |
| B10-6 | stewards in ≥ 2 areas, and a steward review leads to a revert or fix | **half**: stewards emerged in 3 areas (parser and codegen: w-measure; passes: w-lean); no steward asked for a revert |
| B10-7 | the referee reverts ≥ 2 of O's merges | **wrong**: it reverted one, falsely (the judge's own UTF-8 bug), and the architect reinstated it; 4 heads were refused before merging (2 on a load-dependent compile-time gate, since corrected) |
| B10-8 | the Sonnet workers earn ≥ 25% of O's kept credit | **held** (about 35%: w-lean 0.147, w-sceptic 0.039 of about 0.53) |
| B10-9 | neither arm reaches gcc -O2 | **held** (V about 1.05, O 1.256) |
| B10-10 | hidden ratio within 5% of visible | **held** (V 1.055 visible, about 1.05 hidden; O 1.258 and 1.256) |

## The harness's own failures (all fixed during the run, both arms alike; every one logged in the ledgers)

1. **Compile time was a wall-clock gate under load.** Two Sonnet heads were refused in round 1 at 72 and 94 s, with four
   builds and V running on four cores; their trees compile in well under 60 s alone. Fixed: 180 s in-run sanity bound,
   60 s in the quiet final. O lost both workers' round-1 work to it.
2. **Turn and round costs ran far above the assumed rates** (V's first turn $47; O's first round $63), with caps checked
   only between turns. Fixed: each turn and round sized to the budget left at the arm's observed rate.
3. **The restart re-ran O's four round-2 turns** (a cut turn did not count as done), about $45 of work the harness then
   ignored. Fixed; the work was integrated afterwards as round 2b by the referee's rule, with no agent turn.
4. **The judge compared outputs as strings decoded per pipe chunk**, splitting UTF-8 characters at 4096-byte boundaries:
   scripts failed at random, and 126 of 3,624 reference files had been written with the same flaw. **O's w-measure
   found it** (board #19, with the byte offsets), the referee then falsely reverted w-measure's own merge on it, and
   **the architect reinstated the merge with a written ruling** (#53) before the operator knew. Fixed: bytes, and every
   reference regenerated.
5. **The SDK's reported cost is unreliable for resumed sessions** (V's 2-minute turn 4 reported $40.74; its transcript
   says $1.43). V stopped at 218 of 360 minutes believing about $20 was left; by the transcripts it had spent $110. The
   table's costs are recomputed from both arms' transcripts at list prices.
6. **Practices never got beliefs**: every worker turn was cut by its round's time cap, so no worker wrote the closing
   "practices:" line. The practices were posted (3) but none was ever credited.

## What this says

1. **One strong agent built a near-gcc optimiser in one session.** It did not need the org's memory or division of
   labour: it held the compiler, the profiles and its own new back end in one 668k-token context, without compaction,
   and kept a coherent design (an IR plus register allocation) for three and a half hours. The "too big for one
   context" premise did not hold: the agent read selectively and the 1M context sufficed.
2. **It also shipped two wrong-code bugs** that every check it could run passed. O's merged compiler, built by four
   agents through a referee, passed the hidden gate. Whether that is the org's doing is not shown: no part of O tested
   the hidden W1-W5 set either, and O's smaller, more conservative changes may simply have touched less. What the run
   does show is that **independent checking caught real defects twice**: the hidden gate caught V's miscompiles, and an
   org worker caught the judge's own bug.
3. **The org worked as designed and was slower.** Claims, the board (54 entries), stewards (3 areas), an architect who
   ruled on evidence, a second model earning a third of the credit, and passes that one worker built and another
   extended. But four warm workers burned the budget four times faster (about $1 a minute against V's $0.50), so the
   org got two 45-minute rounds against V's three and a half hours of continuous work. Depth of one line of work, not
   breadth, won the instruction count.
4. **What to change next** (B10's "if O loses" list, re-read against the run): per-worker budget was too thin (two rounds);
   the rounds were too short for a back-end redesign (every worker turn was cut); and the referee's merge check lacked
   the hidden-style W1-W5 coverage that caught V. The next design should give fewer workers longer rounds, put a
   held-out correctness check inside the referee (the org's advantage should be checking, not breadth), and treat a
   cut turn as a hand-in that still names its practices.

"A philosophy of emergence should be willing to lose an A/B test." On the count, the org lost; on the rules, it won
because the single agent broke something it could not see.
