#!/bin/bash
# Usage: tools/runtests.sh <test-dir> [filter-regex]
# Compiles all tests in batch mode, then assembles, runs, compares.
V=/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V
CLJ=/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/toolchain/clj-run
DIR=$(cd "$1" && pwd)
FILTER=${2:-.}
W=$V/.work/$(basename "$DIR")
rm -rf "$W"; mkdir -p "$W"
LIST=$W/list.txt
: > "$LIST"
for c in "$DIR"/*.c; do
  id=$(basename "$c" .c)
  echo "$id" | grep -qE "$FILTER" || continue
  echo "$c $W/$id.s" >> "$LIST"
done
cd "$V"
start=$(date +%s.%N)
"$CLJ" -m ccc.main -I "$V/spec/include" --batch "$LIST" 2> "$W/compile.err"
end=$(date +%s.%N)
echo "compile time: $(echo "$end - $start" | bc)s"
pass=0; fail=0; failed=()
while read -r c s; do
  id=$(basename "$c" .c)
  exp_exit=$(cat "$DIR/$id.exit" 2>/dev/null || echo 0)
  if [ ! -f "$s" ]; then fail=$((fail+1)); failed+=("$id(compile)"); continue; fi
  if ! gcc "$s" -o "$W/$id" 2> "$W/$id.asm.err"; then fail=$((fail+1)); failed+=("$id(asm)"); continue; fi
  timeout 10 "$W/$id" > "$W/$id.out" 2>/dev/null < /dev/null
  code=$?
  if [ "$code" == "$exp_exit" ] && cmp -s "$W/$id.out" "$DIR/$id.expected"; then
    pass=$((pass+1))
  else
    fail=$((fail+1)); failed+=("$id(run:$code)")
  fi
done < "$LIST"
echo "pass=$pass fail=$fail"
for f in "${failed[@]}"; do echo "  FAIL $f"; done
