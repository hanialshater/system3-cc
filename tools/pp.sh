#!/bin/bash
# Usage: tools/pp.sh file.c -> print preprocessed tokens
cd /tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V
CCC_DEBUG=1 /tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/toolchain/clj-run -m ccc.main -I spec/include -E "$1"
