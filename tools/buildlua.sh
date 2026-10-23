#!/bin/bash
# Build Lua 5.4.7 with our compiler into .work/lua7 (lua binary at .work/lua7/lua).
V=/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V
CLJ=/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/toolchain/clj-run
O=$V/.work/lua7
mkdir -p $O
rm -f $O/*.s $O/lua
: > $O/list
for f in $(cd $V/spec/w7/lua && ls *.c); do echo "$V/spec/w7/lua/$f $O/${f%.c}.s" >> $O/list; done
cd $V
time $CLJ -m ccc.main -I spec/w7/include -I spec/w7/lua -D LUA_COMPAT_5_3 -D LUA_USE_POSIX --batch $O/list
n=$(ls $O/*.s 2>/dev/null | wc -l)
echo "compiled $n of 33"
if [ "$n" = 33 ]; then gcc $O/*.s -o $O/lua -lm && echo "link ok"; fi
