#!/bin/bash
# Run the W7 runtime programs with .work/lua7/lua: check output, print CPU time (best of N).
V=/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V
L=${LUA:-$V/.work/lua7/lua}
N=${N:-1}
B=$V/spec/w7/bench
for f in $(cd $B && ls *.lua); do
  n=${f%.lua}
  best=999
  for i in $(seq $N); do
    s=$(date +%s.%N)
    out=$(cd $B && $L $f 2>&1)
    e=$(date +%s.%N)
    t=$(echo "$e - $s" | bc)
    best=$(echo "if ($t < $best) $t else $best" | bc)
  done
  if [ "$out" = "$(cat $B/$n.expected)" ]; then r=OK; else r=WRONG; fi
  printf "%-10s %6.3fs %s\n" $n $best $r
done
