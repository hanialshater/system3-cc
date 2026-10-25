#!/bin/bash
# Compile and time the benchmark programs (median of 3 runs each).
cd "$(dirname "$(readlink -f "$0")")/.."
B=.work/bench
mkdir -p $B
: > $B/list
for f in spec/bench/*.c; do
  n=$(basename $f .c)
  echo "$PWD/$f $PWD/$B/$n.s" >> $B/list
done
"$(dirname "$(readlink -f "$0")")/clj-run" -m ccc.main -I spec/include --batch $B/list
for f in spec/bench/*.c; do
  n=$(basename $f .c)
  gcc $B/$n.s -o $B/$n || { echo "$n: asm fail"; continue; }
  ts=()
  ok=1
  for i in 1 2 3; do
    s=$(date +%s.%N)
    $B/$n > $B/$n.out
    e=$(date +%s.%N)
    ts+=($(echo "$e - $s" | bc))
    cmp -s $B/$n.out spec/bench/$n.expected || ok=0
  done
  med=$(printf "%s\n" "${ts[@]}" | sort -n | sed -n 2p)
  echo "$n: ${med}s $( [ $ok = 1 ] && echo OK || echo WRONG)"
done
