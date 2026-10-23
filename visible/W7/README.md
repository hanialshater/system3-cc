# W7 visible tests: Lua 5.4.7's own test suite

This folder holds 6 of the 28 files of Lua's test suite (`testes/` of github.com/lua/lua at v5.4.7) that the hidden
runner scores, with what they need: the driver `w7test.lua`, the helpers `tracegc.lua` and `bwcoercion.lua`, and
`manifest.json` (`{ "<file>.lua": { "category": …, "gccO0Ms": …, "timeoutMs": … } }`). The other 22 are hidden; they
come from the same suite, in the same categories (spec/W7.md).

To build Lua with your compiler and run one test (from your compiler directory; `INC=spec/w7/include`, `LUA=spec/w7/lua`):

```sh
for f in $(cd spec/w7/lua && ls *.c); do echo "$PWD/spec/w7/lua/$f /tmp/lua7/${f%.c}.s"; done > /tmp/lua7.list   # mkdir -p /tmp/lua7 first
clj-run -m ccc.main -I spec/w7/include -I spec/w7/lua -D LUA_COMPAT_5_3 -D LUA_USE_POSIX --batch /tmp/lua7.list
gcc /tmp/lua7/*.s -o /tmp/lua7/lua -lm
cp -r visible/W7 /tmp/w7t && cd /tmp/w7t && /tmp/lua7/lua w7test.lua full closure.lua < /dev/null | tail -1; echo $?
```

(`spec/w7/lua` holds exactly the 33 sources of spec/w7/build.json plus Lua's headers, so `*.c` is the list.) A test
passes when the interpreter exits 0 and prints the line `W7-TEST-OK <file>`, with an empty stdin, within its time
limit. Run each test in a copy of this folder: some tests write files next to themselves. The hidden runner gives
stdin as an empty pipe (`files.lua` checks that seeking stdin fails); `< /dev/null` is close enough for these six.
