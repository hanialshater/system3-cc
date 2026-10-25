# C compiler in Clojure

A C compiler written in Clojure by a company of AI agents in the
[System 3 city](https://github.com/hanialshater/system3-city). It compiles real programs: Lua 5.4.7 and SQLite
(`sqlite3.c`, about 270,000 lines) to x86-64 assembly. It is judged by a referee that checks correctness first, then
how fast the generated code runs.

| | |
|---|---|
| **Instructions executed, against gcc -O2's build** (geometric mean, hidden SQLite workloads) | **1.256x** (B10, 2026-10-24; the start was 1.791x) |
| Correctness gates | all pass: hidden C tests (W1-W5), Lua's own test files, SQLite's SQL corpus, deterministic output |
| Compiling `sqlite3.c` | 29.7 s on a quiet machine (limit 60 s) |
| Built by | B8's compiler (one agent), made 1.85x faster to compile in B9, then made an optimising compiler by an org of five in B10 |
| Cost so far | $190.74 across the three runs it descends from (`company/company.json`) |

## Run it

Java 21 or later, and gcc (to assemble and link).

```sh
./run.sh --fetch-jars                               # Clojure 1.12.6 and its two spec jars into ./lib (or set CLJ_LIB)
./run.sh -I spec/include hello.c -o hello.s && gcc hello.s -o hello && ./hello
```

`spec/include` holds the C library headers the compiler uses. `tools/` has the members' helpers: building Lua,
benchmarks and instruction counts.

## The referee

`referee/judge.mjs`:
- **`fast`** does four things:
  - builds SQLite and `sqlrun` with the compiler;
  - runs the visible SQL corpus against gcc's outputs;
  - checks the visible C tests and Lua against the start's pass set;
  - counts instructions on the visible workloads under callgrind.
- **`full`** runs the same on the hidden sets, plus compile time and determinism.

The hidden sets are held by the referee's owner, outside this repository. See `referee/README.md`.

## The company

| File | What |
|---|---|
| `company/company.json`, `company/milestones.json` | mission, measure, budget lines and milestones |
| `company/org.json` | the org of B10: an architect and four workers (the org contract) |
| `docs/` | the run designs and results: `BENCH8.md`, `B9.md`/`BENCH9.md`, `B10.md`/`BENCH10.md`; `INTERFACES.md` (the architect's pass interfaces) |
| `spec/` | the waves' specs (W1-W7), Lua's sources, and B10's visible material |

The HQ shows its activity from the git history (`src/git-events.mjs` in system3-meta). B8's commits are authored
"B8 V". B10's merges carry the worker's name.

## Licence

Not chosen yet (the owner's decision). Until one is, all rights are reserved.
Third-party sources keep their own licences:
- Lua (`spec/w7/lua`, MIT, Lua.org, PUC-Rio; see the notice at the end of `lua.h`);
- SQLite (`spec/b10/sqlite3.c`, public domain).
