# Benchmark 9 results: make the compiler compile fast (one agent V, a population P, the org S)

owner: vp-meta · date: 2026-10-24 · status: **final** · protocol: [PROTOCOL.md](PROTOCOL.md) (with its deviations) ·
predictions: [PREDICTIONS.md](PREDICTIONS.md) v1 and v2, committed before any arm ran · principles that came out of it:
[../PRINCIPLES.md](../PRINCIPLES.md)

## What ran

All arms started from the same tree (B8 V's compiler at `5a10774`), with the same model, tools, rules, judge and caps
($70, 240 active minutes), one after another so no arm's timings overlapped another's. Score = geometric mean of two
timing ratios against the start (Lua's 33 sources; a held-out corpus of 1,126 hidden test programs), lower is better;
a candidate that breaks the gate (hidden tests, Lua's 22 hidden test files, runtime ≤ 1.10×, peak RSS ≤ 1.10×) scores 1.0.

| Arm | What it is |
|---|---|
| **V** | one Agent SDK session, three full-judge rounds |
| **P** | a population: generations of 4 variant sessions (25 min, $3.50 each), each with one of four strategy briefs or a crossover of two parents; selection by the fast judge, by code |
| **S** | System 3 Meta (arm S9 on B8's harness): an architect, three module owners (frontend, parser, backend), an advisory evaluator; three one-round orders; K38 beliefs and K39 effect learning |

## Results (the quiet final scoring: 5 interleaved runs each, nothing else on the machine)

| Arm | Score | Lua | Held-out corpus | Cost | Active minutes |
|---|---|---|---|---|---|
| **S** (the org) | **0.540** | 0.546 | 0.534 | $60.94 | 217 |
| **V** (one agent) | **0.542** | 0.557 | 0.527 | **$18.00** | **114** |
| **P** (population) | 0.613 | 0.571 | 0.658 | $21.26 | 227 |
| start (re-judged) | 1.000 / 1.019 | | | | |

**S and V are tied** (0.540 against 0.542; the start re-judged at 1.019 in S's session, so the judge's noise is about
±2%). Both compile about 1.85× faster than the start; P about 1.6×. V got there for $18 in 114 minutes; S for $61
(3.4×) in 217; every arm passed every gate check at its final version.

**Curves:**
- V by round: 0.556, 0.546, 0.546 (it stopped improving after round 1).
- S by round: 0.606, 1.0 (round 2 "broke the gate": **a harness artifact**, see Corrections; its committed version passes and scores about 0.58), 0.549.
- P's best by generation, re-judged quietly: 0.89, 0.79, 0.74, 0.77, 0.73, 0.69, 0.63, 0.62, 0.61; still falling when its
  clock ran out, at $21 of $70.

**Context use per session** (the transcripts' peak input):

| Arm | Sessions | Peak context per session |
|---|---|---|
| V | 1 | 442k tokens (no compaction) |
| P | 40 | median 35k, max 57k |
| S | 28 | median 41k, max 302k |

The whole problem (compiler about 90k tokens, Lua's sources about 250k, specs about 10k) fits one context; V held it,
the multi-agent sessions saw a tenth each.

## Predictions

| # | Verdict |
|---|---|
| P9-1 every arm ≤ 0.67 | **held** (0.540, 0.542, 0.613) |
| P9-2 P beats V by ≥ 5% | **wrong** (P 0.613, V 0.542) |
| P9-3 S beats V | **held by 0.4%, within the judge's noise: a tie** |
| P9-4 P beats S | **wrong** |
| P9-5 P's best descends from ≥ 2 briefs | **held** (g8-v0 descends from all four: startup, middle, back, front) |
| P9-6 an arm tunes to Lua (Lua ratio ÷ corpus ratio ≤ 0.77) | **wrong** (P 0.87, V 1.06, S 1.02): the gains were general |
| P9-7 some hand-in fails the gate | **wrong** once corrected: S's round-2 failure was the harness's (it judged S's live worktree; the commit passes); no arm's committed hand-in failed the full gate |
| P9-8 at $20, P no better than V | **held** (V was at 0.556 for $14; P at about 0.62 for $20) |
| P9-9 S's architect and evaluator ≥ 30% of its cost | **wrong** (24%: architect $13.16, evaluator $1.44 of $60.94) |
| P9-10 (v2) an effect belief ends ≥ 0.7 or ≤ 0.3 | **held only on the artifact**: the three rules fell to 5-8% on the harness's false round-2 failure; void as a test of K39 |
| P9-11 P's and S's in-loop and quiet ratios differ more than V's | **held** (P 0.739 → 0.613; S 0.549 → 0.540; V 0.546 → 0.542) |
| P9-12 clean run | **held**: every final `src/` holds only .clj/.cljc/.edn; no Lua file names or input hashing (the "hash" hits are the word "share" in comments) |
| P9-13 (v2) S improves more from round 1 to 3 than V | **held** (S 0.606 → 0.549, ×0.91; V 0.556 → 0.546, ×0.98) |
| P9-14 (v2) owners cite rules in rounds 2-3 | **held for round 3** (all four hand-in notes cite a rule, "gate first, P-4"); none in round 2 |

## What happened inside the org (S)

- **Round 1** (54 min): the architect loaded modules in parallel; frontend indexed punctuation by first character;
  parser moved its token state to per-thread arrays; backend tuned codegen. 0.606.
- **Round 2** stalled 24 minutes (04:58-05:22): a K29 commit refusal over stray interface edits (the architect's own,
  charged to the frontend) left a stage error that nothing retried, as in B8 S2. Then its full judge reported all
  1,126 hidden tests and all 33 Lua sources failing, while the timings (0.532) beat V's. **That failure was the
  harness's** (Corrections): S9's accept judged S's live worktree, not its commit, and the commit passes every gate check.
  The owners were right in round 3 that the cause was the working directory, not the compiler.
- **K39 learned the wrong lesson from it.** The false failure counted as a +65% "slowdown", every effect rule in force was
  charged with it, and all three (time load separately; primitives on the hot path; memoize repeated work) were retired
  at 5-8% belief. Even a real gate failure is not evidence about a speed practice (now fixed: K40). The architect's next retro wrote better ones:
  "gate first" (no hand-in while any gate line fails), bounded buffers, hot functions under HotSpot's 8,000-byte limit.
- **Round 3** (46 min): every owner's note cites "gate first"; back to correct, 0.549 in the loop, 0.540 quietly.

## Operator interventions and deviations

1. **Container reclaim** (19:08, 19:20): the container is reclaimed about 5 minutes after the operator's session goes
   idle. V lost about 30 minutes of wall clock (not of its active clock) and resumed its session both times. Fixed with
   a tracked background watcher for the rest of the run.
2. **P's in-loop full judge ran under load** (it ran beside the next generation), so its in-loop scores were noise; the
   quiet final scoring re-judged every gated P candidate (PROTOCOL.md "Deviation"). P's own pick was its best anyway.
3. **The fast judge** stopped taking timings after a failed check (a deadlocking P candidate would have spent 90 minutes
   of timeouts); no score changed.
4. **S's first start** failed its preflight on a 108-byte socket path (limit 107) and spent nothing; S ran later from a
   shorter root, after V's and P's final scoring.
5. **S, round 2:** the operator raised two change requests on the frontend's behalf (ISS-8/9) on a misreading; the
   architect rightly refused them. The operator then ran `supervisor retry`, which resumed the order at its accept stage
   (the commit had passed the integration check). Logged in `S/_operator/S9/operator.jsonl`.

## Corrections (2026-10-24, after the write-up)

**S's round 2 was not broken.** Its committed head (`wave-W9`, `8c834e9`) passes B8's hidden W1 runner 60 of 60 and
bench9's full judge (every gate check ok; 0.58 on one repetition). The round's accept had judged **S's live worktree**
(bench9/s9.mjs passed the worktree directory to the judge), while V's and P's full judge always judged a clean clone of
their committed head; whatever the worktree held at 05:22 failed. The harness, not S, cost S its round 2, and with it
the three effect rules K39 retired and a round-3 spent on "gate first". S's final score (0.540) is unaffected: the final
scoring judged its committed final version. Fixed: s9.mjs now judges a clean export of the head.

## What this says

1. **A single strong agent is hard to beat when the problem fits its context.** V tied the org at 30% of its cost
   and half its time, and beat the population. It held the whole problem (442k tokens) and found the cross-cutting wins
   (parallel load, direct linking, faster tokens) in its first round.
2. **The org matched V, and learned across rounds, but paid for its structure.** Its best round equalled V; its
   learning visibly changed its behaviour (round 3's "gate first", a lesson drawn from a failure that was the harness's);
   its coordination cost 3.4×, a kernel gap stalled a round, and a harness flaw falsely failed another.
3. **The population kept improving and never plateaued**, with every brief contributing to the winner; it was cut by
   time, not money. A longer horizon, or a population seeded from a warm strong session, is the open question.
4. **Our multi-agent designs used a tenth of the context each and were told what to look at.** The founder's diagnosis
   (2026-10-24) became [PRINCIPLES.md](../PRINCIPLES.md): autonomy, diversity without restriction, full context,
   shared organised learning, an independent referee, in proportion, with roles and ownership as stewardship.

## Kernel fixes this run produced (K40, made 2026-10-24; 338 tests green)

- **A held stage error asks its manager** after `holdAskMin` minutes (default 10), once per hold; a yes lifts it.
- **A lifted workspace hold re-runs the integration check** on the order's head before it goes on (a failing recheck
  holds it again, and is asked about the same way).
- **K39's `learn.metric.valid`:** an order whose valid flag is 0 (B9: the gate failed) scores no effect rule and is no
  later order's baseline.

## Next

B9b (P-fork: a population forked from a warm strong session, generic temperament prompts, a findings board) and B9c
(the autonomous org of PRINCIPLES.md: warm starts, claims and stewardship instead of fixed scopes, a findings board,
the architect as arbiter, a coverage map), each against V's 0.542 at $70, the lean versions the proportion principle
asks for.
