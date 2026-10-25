# The demo site

`node site/build.mjs` writes `site/dist/index.html`, a static page built from real runs:
- the benchmark programs in `spec/bench/`, compiled by this compiler and by gcc -O2: their outputs, the
  instructions each executes (valgrind), and one function's assembly from both;
- Lua 5.4.7 built by this compiler, running `spec/w7/bench/*.lua` against their expected output;
- the referee's fast check: SQLite built by this compiler, its SQL corpus and its visible workloads against gcc -O2.

It needs Java, gcc, valgrind and `referee/judge.json`. `site/dist/` is committed because a static host cannot run the
compiler: rebuild and commit it when the compiler changes.
