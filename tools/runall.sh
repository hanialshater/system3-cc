#!/bin/bash
# Runs all local test suites (visible tests, my tests, random generators) and prints a summary line per suite.
cd "$(dirname "$(readlink -f "$0")")/.."
for d in visible/W[1-6] tests/w* tests/misc .work/src/gentests .work/src/genf .work/src/genm .work/src/genm2 .work/src/genc; do
  [ -d "$d" ] || continue
  r=$(bash tools/runtests.sh "$d" 2>/dev/null | grep -v "compile time")
  echo "$d: $r"
done
