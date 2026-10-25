# The referee

Score: the geometric mean, over the hidden SQLite workloads, of the instructions the compiler's SQLite build executes
(callgrind, exact), divided by gcc -O2's. Lower is better; gcc -O2 is 1.0.

**Gates.** A build that fails any gate scores 2.0:
- hidden C tests (waves W1-W5): no test the start passed may fail;
- Lua 5.4.7 built by the compiler passes its hidden test files, at no more than 1.10x the start's runtime;
- SQLite's hidden SQL corpus gives gcc's outputs;
- compiling `sqlite3.c` takes at most 60 s;
- compiling is deterministic.

**Running it.**
- `node referee/judge.mjs fast --dir=.` runs the visible half: `spec/b10/corpus`, `spec/b10/work`, `visible/` and
  Lua. Members run it as often as they like.
- `full` needs the hidden sets. The referee's owner holds them outside this repository.
- Setup: copy `judge.example.json` to `judge.json` (ignored by git) and fill in the paths. Then
  `visible-check.mjs` (the C and Lua visible check) and the start's pass sets (`start-*.json`) are found beside it.

**Reproduced on 2026-10-25 in this repository.** `fast` builds SQLite in 19.6 s and gives all 600 visible SQL scripts
gcc's output. It reports no visible C or Lua regression. Its visible ratio is 1.258 (B10's hidden result was 1.256).
17 visible `.ref` files in `spec/b10/corpus` were replaced with the referee's own copies, which differ in a few
non-UTF-8 bytes.
