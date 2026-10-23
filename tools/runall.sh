#!/bin/bash
# Runs all local test suites and prints a summary line per suite.
cd /tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V
for d in visible/W* tests/*; do
  [ -d "$d" ] || continue
  r=$(bash tools/runtests.sh "$d" | grep -v "compile time")
  echo "$d: $r"
done
