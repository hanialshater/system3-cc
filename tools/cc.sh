#!/bin/bash
# Usage: tools/cc.sh file.c  -> compile, assemble, run (debug mode)
V=/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V
CLJ=/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/toolchain/clj-run
mkdir -p /tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V/.work/cc
f=$(realpath "$1")
cd "$V"
CCC_DEBUG=1 "$CLJ" -m ccc.main -I "$V/spec/include" "$f" -o /tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V/.work/cc/prog.s || exit 1
gcc /tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V/.work/cc/prog.s -o /tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V/.work/cc/prog || exit 1
/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V/.work/cc/prog; echo "exit=$?"
