#!/bin/bash
# Run the visible W7 Lua tests (or the given ones) with .work/lua7/lua, each in a fresh copy.
V=/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V
L=$V/.work/lua7/lua
T=$V/.work/w7t
tests="$@"
[ -z "$tests" ] && tests=$(cd $V/visible/W7 && ls *.lua | grep -v -e w7test -e tracegc -e bwcoercion)
pass=0; fail=0
for t in $tests; do
  rm -rf $T; cp -r $V/visible/W7 $T
  out=$(cd $T && timeout 120 $L w7test.lua full $t < /dev/null 2>&1)
  code=$?
  if [ $code = 0 ] && echo "$out" | grep -q "W7-TEST-OK $t"; then pass=$((pass+1)); echo "ok   $t";
  else fail=$((fail+1)); echo "FAIL $t (exit $code)"; echo "$out" | tail -5 | sed 's/^/    /'; fi
done
echo "pass=$pass fail=$fail"
