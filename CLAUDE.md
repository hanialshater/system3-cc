# C compiler in Clojure: rules for every session

A company of the System 3 city. It builds a C compiler in Clojure (`src/ccc/`) that compiles Lua and SQLite. It is
judged only by its referee (`referee/`). **The disk is the memory**: the next session knows only what is here.

## Start of session

1. `node referee/judge.mjs fast --dir=.` (needs `referee/judge.json`, valgrind and gcc).
2. Read `company/milestones.json`, the newest results in `docs/`, and `docs/INTERFACES.md` before changing a pass's boundary.

## Hard rules

- **Correct before fast.** A change that miscompiles anything the start compiled is reverted, whatever it gains.
- **Main never gets worse.** Work lands on main only past the referee.
- **No other compiler inside.** The compiler never calls gcc, clang or another C compiler to compile; gcc only
  assembles and links its output, and builds the reference.
- **Predictions before runs.** A new run's design and predictions go in `docs/` before it starts.
- **Spending needs the owner's approval** (`company/company.json` budget lines).
- **This repository is public.** No secrets, no personal data, no session transcripts.
