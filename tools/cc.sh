#!/bin/bash
# Usage: tools/cc.sh file.c  -> compile, assemble, run (debug mode)
V="$(dirname "$(readlink -f "$0")")/.."
CLJ="$(dirname "$(readlink -f "$0")")/clj-run"
mkdir -p "$(dirname "$(readlink -f "$0")")/.."/.work/cc
f=$(realpath "$1")
cd "$V"
CCC_DEBUG=1 "$CLJ" -m ccc.main -I "$V/spec/include" "$f" -o "$(dirname "$(readlink -f "$0")")/.."/.work/cc/prog.s || exit 1
gcc "$(dirname "$(readlink -f "$0")")/.."/.work/cc/prog.s -o "$(dirname "$(readlink -f "$0")")/.."/.work/cc/prog || exit 1
"$(dirname "$(readlink -f "$0")")/.."/.work/cc/prog; echo "exit=$?"
