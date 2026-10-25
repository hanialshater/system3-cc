# Benchmark 8 results: a C-subset compiler in Clojure, one vanilla agent (V) against a specialized org (S1, S2)

owner: vp-meta · date: 2026-10-23 · status: **final** (2026-10-23) · protocol: [PROTOCOL.md](PROTOCOL.md) · predictions: [PREDICTIONS.md](PREDICTIONS.md), committed
before any run · numbers: `setup.mjs tally`, `setup.mjs audit`, the ledgers under `<b8-runs>/_operator/`

## What ran

| Arm | What it is | Run |
|---|---|---|
| **V** | one Agent SDK session for the whole run, resumed every turn; Bash, Read, Write, Edit, Glob, Grep; `settingSources: []`; no company tools | W1-W6 to its caps, then W7 (founder: "rerun S on W7"; V continued as the baseline) |
| **S1** | System 3 Meta, `specs/ccompiler-specialized.json`: architect, four module owners in parallel, a blocking independent evaluator, routed failures, per-module playbooks | stopped by the founder during W3 |
| **S2** | S1 with the fixes the founder asked for (K34-K37): an architect who scopes each wave, owners on demand who may borrow, an advisory time-boxed evaluator after the hidden accept | W1-W5 to its 300-minute cap; then W7 out of competition (its W6 skipped) |

Both arms: the same model, tools, released files, hidden runner, feedback lines (categories only), three feedback
rounds per wave, caps of 300 active minutes and $120 for W1-W6 (W7: 180 minutes, $80 of its own). Isolation:
`--isolation=hooks` for both arms (the box could not authenticate here; PROTOCOL.md "Isolation deviation").

## Results

### Accuracy: tied

| Wave | V first / final (hidden, waves 1..n) | S2 first / final | S1 first / final |
|---|---|---|---|
| W1 | 100 / 100 | 100 / 100 | 100 / 100 |
| W2 | 100 / 100 | 100 / 100 | 100 / 100 |
| W3 | 99.6 / 99.6 | 99.2 / 99.6 | stopped |
| W4 | 99.7 / 99.7 | 99.0 / 99.7 | |
| W5 | 99.0 / **99.6** | **99.5** / **99.6** | |
| W6 (runtime score, lower is better) | 0.77 / **0.641** | not reached (cap) | |
| W7 (Lua 5.4.7, 22 hidden test files) | 100% / **100%**; runtime **0.725**; compile 11.3 s, 277 MB | 100% / **100%**; runtime 0.937 → 1.010; compile 17.0 → 15.0 s, 289 MB | |

At the end of W5 both arms pass 1,126 of 1,131 hidden tests. They do not fail the same ones: each fails 5, and only 2
are common (V: w3-0085, w5-0279, w5-0320, w5-0445, w5-0454; S2: w5-0320, w5-0411, w5-0445, w5-0481, w5-0698). At W5's
first hand-in S2 failed 6 and V 11 (4 in common): **S2's one clear win is W5's first hand-in**, the broadest wave.

### Cost and time: V

| Wave | V $ | S2 $ | S2 / V | V min | S2 min |
|---|---|---|---|---|---|
| W1 | 5.27 | 7.02 | 1.3 | 22.5 | **12.5** |
| W2 | 0.39 | 5.71 | 14.7 | 1.4 | 7.9 |
| W3 | 3.22 | 8.17 | 2.5 | 14.2 | 38.7 |
| W4 | 6.05 | 21.78 | 3.6 | 35.3 | 34.7 |
| W5 | 25.82 | 38.91 | 1.5 | 75.6 | 206.3 |
| **W1-W5** | **40.74** | **81.59** | **2.0** | **149** | **300** (cap) |
| W6 | 31.83 | - | | 153.2 | |
| W7 | 14.38 | 38.82 | 2.7 | 72.5 | 123.8 |
| **Run** | **86.94** | **120.41** (W6 not run) | | 374.7 | 424.9 |

S1 spent $32.63 on W1-W3 (1.85×, 23.7× and 4.3× V per wave) before the founder stopped it.

Both arms built Lua on the first hand-in and passed all 22 hidden test files. S2's three W7 rounds went to speed and
made its Lua slower (runtime 0.937 → 0.961 → 1.010); V's stayed about level (0.726 → 0.725).

### Compile time, measured on a quiet machine

Each final compiler, one batch (one JVM), 5 runs each, interleaved (`bench9/timing.mjs`, B8's compile sandbox, each run
from a fresh read-only copy):

| | V | S2 | S2 / V | gcc -O0 | chibicc |
|---|---|---|---|---|---|
| Lua's 33 sources | **10.8 s** | 14.7 s | 1.37 | 3.1 s | 1.7 s |
| the hidden W1-W5 corpus (1,126 files) | **14.0 s** | 20.4 s | 1.46 | | |
| peak RSS | 280 MB | 290 MB | | 46 MB | |

(gcc -O0 and chibicc: the W7 reference timings, one process per file.)

### Two different compilers from one model

| | V | S2 |
|---|---|---|
| Source | 6 files, 6,324 lines | 12 files, 8,000 lines |
| Shape | compact: the parser also types; code generation straight from the AST | layered as the org chart: AST → semantic passes → IR → backend, and an optimizer module |
| Its own C test programs | 46 | **507** (the owners' and the evaluator's) |

The org's compiler has the org's structure (Conway's law), eleven times the tests, the same hidden score, and is about
40% slower to compile.

### How the org worked (S2, W1-W5)

- **Parallel work was real.** W1 ran four owners at once and finished in 12.5 minutes against V's 22.5.
- **It did not last.** Each later wave fell mostly on one or two owners (W4: semantics $14.45 of $21.78; W5: three
  owners about $10-12 each), and the waves ended at their rounds (W3, W4, W5 cancelled at 3, 3 and 2 rounds) with the
  same final scores V reached in fewer minutes.
- **Cold starts.** 1 of S2's 20 sessions in W1-W3 resumed warm; 8 of 47 over W1-W5.
- **The architect** cost $14.46 of $81.59 (18%; 31% over W1-W3).
- **The advisory evaluator ran only twice (W2, W3) and found nothing.** It runs after the hidden accept passes, and
  from W3 on accept never passed before the rounds ran out: the fix that made the evaluator cheap also made it absent
  where it might have mattered.
- **Routing and ownership:** in W5 the architect routed the triaged categories on (gnu to frontend and backend, vla to
  semantics); in W2 the backend routed an interface finding to the architect. 5 change requests were raised and
  decided (4 on interfaces: the module map, the IR twice, the AST; one of the IR requests is the operator's ISS-16
  below). Guard denials 1-5 per wave. The commit check refused the one write into another owner's paths.
- **One deadlock, cleared by the operator** (12:59-15:01, W5): the frontend edited the architect-owned `src/ccc/ir.clj`
  without a change request; the commit was refused (K7), the refusal left a stage error that held the order, and
  nothing in the kernel escalated it. The operator raised the change request on the frontend's behalf (ISS-16, labelled
  as the operator's), the architect approved it, and `supervisor retry` let the commit land. Logged in
  `_operator/S2/operator.jsonl`. **Kernel fix proposed, not made:** a refused commit should raise the change request
  (or route an ask) by itself instead of holding silently.
- **Learning (K28/K33):** the playbook has 9 rules after 4 retros. P-1 and P-3 (W3 sizeof) were scored as hurt and
  retired by the architect one retro later, each with a stated reason; P-2, P-4, P-5 (W3) and P-6..P-9 (W5 backend)
  were never tested again, because W3's and W5's categories did not recur in a later wave. The one module with rules
  that met another wave, semantics (P-2 and the later-retired P-1 and P-3 were added 4 seconds before W4 started),
  went from 1.5% failing at W3's first hand-in to 0.7% at W4's, while V's stayed at 0.7%: one data point. (The tally's
  per-module rule count shows 0, because these rules are scoped by role, not by module.) With K38's beliefs (added after the run, not used by it), every rule would stand at its prior
  or at 33%.

## Predictions

| # | Verdict |
|---|---|
| P8-0 ceiling ≥ 90% at W3 | **held:** 99.6% both |
| P8-1 S trails on W1 and W2 | **held:** S2's cost was higher on both; its minutes lower on W1, higher on W2 (S1 higher on all four) |
| P8-2 the raise bites (first W5 < 85%) | **wrong:** 99.0% (V) and 99.5% (S2). The waves did not get hard enough for either arm |
| P8-3 S's first hand-in ≥ V's on 2 of W4-W6 | **wrong:** only W5 (99.5 against 99.0); W4 lower; W6 not reached |
| P8-4 S's final W5 ≥ V's | **held:** tied, 1,126 of 1,131 |
| P8-5 S's W6 score better | **wrong:** S2 never reached W6 |
| P8-6 S's total cost ≤ 2× V | **wrong, barely:** 2.00× over W1-W5 (81.59 against 40.74), and S2 did one wave fewer in its minutes |
| P8-7 routing ≥ 80%, triage handled | **not measured as a share.** Every revise line went to the architect first (B8's lines name categories; few matched a route), and the architect routed what it did not handle itself (W5: gnu to frontend and backend, vla to semantics). No triaged category was left unhandled |
| P8-8 ownership holds, ≥ 1 change request | **held**, with the deadlock above: the guard and the commit held; the escalation path was missing |
| P8-9 per-module knowledge | **held on one module, one data point** (semantics, above); the other modules' rules never met a later wave |
| P8-10 clean run | **held, after review.** The audit script reports "NOT clean (or unverified)". Each item, checked: the 7 "hidden text" lines are common C (a `strcmp` or `memcmp` prototype, a `MAX` macro, a small struct initializer) that also occur in hidden tests; the "compiler" and "holes" items are V's own `tools/cc.sh`, which runs V's compiler and gcc only to assemble and link; the "upstream" item is a commit message ("torture/c-testsuite style"). No hidden path, no network attempt, no refused compile, no shim denial, every session hooked and its transcript found. Isolation was hooks, not the box, for both arms |
| P8-11 caps bind, S more likely | **held:** S2 ended at its 300-minute cap in W5; V reached the cap at the end of W6 |
| P8-W7-1 both < 90% on Lua | **wrong:** both 100% |
| P8-W7-2 a first build fails or passes < half | **wrong:** both built and passed 22 of 22 at the first hand-in |
| P8-W7-3 specialization pays more on W7 | **wrong:** S − V is 0 on W7 as on W5 |
| P8-W7-4 runtime between 1.0 and 6.17 | **wrong:** V's Lua is faster than gcc -O0's (0.725); S2's 1.010 |
| P8-W7-5 compile ≥ 15 s and ≥ 300 MB | **wrong:** V 11.3 s, 277 MB; S2 15.0 s, 289 MB |
| P8-W7-6 an arm ends W7 with a Lua failure | **wrong:** neither did |
| P8-W7-7 S's W7 cost ≤ 2× V's | **wrong:** 2.7× ($38.82 against $14.38) |

## What this says

1. **On an exact-referee build task, one strong agent beat the org on cost and time at equal accuracy.** V reached
   the same hidden scores as S2 through W5 for half the money and half the minutes, then did W6 and W7 as well.
   This is the third benchmark (after B5 and B6) with the same shape.
2. **The org's structure was not its problem in the way we expected.** Ownership, routing, change requests and the
   playbook all worked as built. What cost it was cold starts, the architect's hop, and work that did not spread:
   most waves were one module's problem.
3. **Same model, same scores, different compilers.** V and S2 reached identical hidden scores, but by different routes:
   their failures overlap only partly (2 of 5 at W5), and the org built a layered compiler shaped like its org chart,
   with eleven times the tests, that compiles about 40% slower. The roles are different prompts on one model, so the
   org had no capability V lacked; where it could win, on breadth (W5's first hand-in), it did, a little.
4. **What B8 does not say.** Nothing about running work over weeks, institutional knowledge on recurring work,
   governance as a product, populations on open optimization, or working with people (RESEARCH-AGENDA.md Q1-Q6).
   Benchmark 9 (open optimization: compile time) and the later E2 (recurring work) test two of those.

## Corrections

- **V's cost** (2026-10-23, 17:15): the tally first showed V's W7 at $86.94 and V's run at $159.51. A V turn with no
  result (W6 round 2) reports cost 0, and the tally took it as a new baseline, so V's next turn counted the session's
  whole running total. Fixed in `tallyV`: V's W7 cost $14.38, its whole run $86.94.

## Caveats

One task, one model family, one run per arm. Hooks isolation instead of the box (both arms alike). S2 ran after S1
on the same machine, V in parallel with S1 and S2 for part of the time (4 CPUs). Operator interventions: V 2 restarts
(keepalive), S2 1 deadlock clearance (above) and 1 restart; all logged.

## Follow-ups this run produced

- **Kernel:** a refused commit should raise its change request (or route an ask) itself (the W5 deadlock).
- **Kernel, fixed:** a stage's `checked` list split multi-word classes ("W3 sizeof"), so no W3 rule was ever scored on
  W4 or W5 (commit 036f65aa4). Re-scored by hand: P-2 (initializers) helped twice, P-4 and P-5 (sizeof) once; V, with
  no playbook, kept one initializer failure to the end.
- **Kernel, added for B9:** K38 (beliefs) and K39 (learning from measured effects, not only defects).
- **Tally, fixed:** V's W7 cost (above, Corrections).
- **Benchmark 9** (open optimization: compile time; V, a population, the org with K39) and **E2** (recurring work, where
  the playbook should pay) test what B8 could not.
