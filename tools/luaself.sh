#!/bin/bash
# Run the self-checking Lua scripts in tests/lua with .work/lua7/lua.
V=/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V
cd $V/tests/lua
for t in *.lua; do
  echo "== $t"
  timeout 120 $V/.work/lua7/lua $t 2>&1 | tail -6
done
