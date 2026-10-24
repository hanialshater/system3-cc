# B10: make the code this compiler generates fast

This compiler (a C subset, written in Clojure, emitting x86-64 GNU as) is correct: it passes its own tests (`spec/`,
`visible/`), builds Lua 5.4.7 (`spec/W7.md`), and builds **SQLite 3.53.4** (`spec/b10/sqlite3.c`, the amalgamation,
269,668 lines) so that SQLite gives exactly the output of gcc's build. But the code it generates is slow: the SQLite it
builds executes about **1.8 times as many instructions as gcc -O2's build** on the same work.

**Your job: make the generated code execute as few instructions as possible, without breaking anything.**

## How it is built and measured

```
clj-run -m ccc.main -I spec/b10/include -I spec/b10 $(cat spec/b10/opts.txt) spec/b10/sqlite3.c -o sqlite3.s
clj-run -m ccc.main -I spec/b10/include -I spec/b10 $(cat spec/b10/opts.txt) spec/b10/sqlrun.c -o sqlrun.s
gcc sqlite3.s sqlrun.s -o sqlrun -lm
./sqlrun script.sql          # one SQL statement per line, against an in-memory database
```

- **The score** is the geometric mean, over a set of hidden SQL workloads, of the instructions your build of SQLite
  executes (counted by `valgrind --tool=callgrind`, so the count is exact and does not depend on the machine's load)
  divided by the instructions gcc -O2's build executes. Lower is better. The start is about 1.8.
- **The visible workloads** are in `spec/b10/work/`: twelve kinds of work (bulk insert, index build, sort, GROUP BY,
  joins, window functions, string functions, JSON, recursive CTEs, triggers, updates, numeric functions), each with
  gcc's output (`.ref`) and gcc -O2's instruction count (`o2.json`). The hidden workloads are different scripts of the
  same kinds, at twice the size.
- **The gate.** A version that fails any of these scores 2.0, whatever its instruction count:
  - every script of a hidden SQL corpus (3,000 scripts, the same kinds as the 600 visible ones in `spec/b10/corpus/`,
    each with gcc's output) gives exactly gcc's output, and every workload gives gcc's output;
  - nothing the start passes of W1-W5's hidden tests fails;
  - Lua builds and passes its tests, and its runtime score is no worse than 1.1 times the start's;
  - compiling `sqlite3.c` takes at most 60 seconds (the start: about 18), and compiling the same input twice gives
    the same assembly.
- **The fast judge**, as often as you like: `node <toolchain>/judge/judge.mjs fast --dir=<your tree>` builds SQLite with
  your compiler, runs the visible corpus, the visible W1-W5 tests and Lua, then counts the visible workloads. It prints
  each workload's ratio and a total. It takes several minutes; `valgrind --tool=callgrind ./sqlrun <one workload>` and
  `callgrind_annotate` are faster for looking at one thing.

## Rules

- The compiler stays a compiler: the same input gives the same output, and the output is computed from the C it is
  given. No recognising SQLite, Lua or the workloads by name, hash or content; no precomputed assembly; no calling gcc
  or any other compiler from the compiler; nothing outside `src/` at compile time. Optimisations must be general (they
  must be correct for any C the compiler accepts).
- `spec/b10/` is provided material: do not change it (the judge uses its own copy).
- gcc and valgrind are for measuring and comparing (for example `gcc -O2 -S` to see what a good compiler emits for a
  small function); never as part of the build.
- Commit your work in this repository; only committed work is judged.
