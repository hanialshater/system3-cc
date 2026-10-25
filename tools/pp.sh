#!/bin/bash
# Usage: tools/pp.sh file.c -> print preprocessed tokens
cd "$(dirname "$(readlink -f "$0")")/.."
CCC_DEBUG=1 "$(dirname "$(readlink -f "$0")")/clj-run" -m ccc.main -I spec/include -E "$1"
