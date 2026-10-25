#!/bin/bash
# Run the self-checking Lua scripts in tests/lua with .work/lua7/lua.
V="$(dirname "$(readlink -f "$0")")/.."
cd $V/tests/lua
for t in *.lua; do
  echo "== $t"
  timeout 120 $V/.work/lua7/lua $t 2>&1 | tail -6
done
