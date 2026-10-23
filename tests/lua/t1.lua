-- self-checking tests of library behaviour that depends on the C code generator
local n = 0
local function check(c, msg) n = n + 1; if not c then print("FAIL " .. n .. " " .. tostring(msg)); end end
local function eq(a, b) check(a == b, tostring(a) .. " ~= " .. tostring(b)) end

-- numbers / conversions
eq(math.maxinteger + 1, math.mininteger)
eq(math.mininteger // -1, math.mininteger)
eq(math.mininteger % -1, 0)
eq(7 // 2, 3); eq(-7 // 2, -4); eq(7 % -3, -2); eq(-7 % 3, 2)
eq(7.5 // 2, 3.0); eq(-7.5 % 2, 0.5)
eq(3 / 2, 1.5)
eq(math.tointeger(3.0), 3); eq(math.tointeger(3.5), nil); eq(math.tointeger(2^63), nil)
eq(math.tointeger(-2^63), math.mininteger)
eq(1e15, 10^15); eq(tostring(1e15), "1e+15"); eq(tostring(2^53), "9.007199254741e+15")
eq(tostring(0.1), "0.1"); eq(tostring(-0.0), "-0.0"); eq(tostring(1/0), "inf"); eq(tostring(-1/0), "-inf")
check(tostring(0/0):find("nan"), "nan")
eq(string.format("%.3f", 2/3), "0.667")
eq(string.format("%5.1f|%-6d|%x|%X|%o", 3.14159, 42, 255, 255, 8), "  3.1|42    |ff|FF|10")
eq(string.format("%g %g %g", 1e20, 0.0001, 123456789), "1e+20 0.0001 1.23457e+08")
eq(string.format("%a", 1.0), "0x1p+0")
eq(string.format("%q", 1/3), "0x1.5555555555555p-2")
eq(string.format("%q", math.mininteger), "0x8000000000000000")
eq(string.format("%10.4s|", "abcdefg"), "      abcd|")
eq(string.format("%c%c%c", 76, 117, 97), "Lua")
eq(string.format("%i", -17), "-17")
eq(string.format("%5.2e", 12345.678), "1.23e+04")
eq(tonumber("0x10"), 16); eq(tonumber("  10  "), 10); eq(tonumber("1e2"), 100.0)
eq(tonumber("0x1p4"), 16.0); eq(tonumber("10", 2), 2); eq(tonumber("zz", 36), 1295)
eq(tonumber("9223372036854775808"), 9.2233720368547758e18)
eq(math.type(9223372036854775807), "integer")
eq(tonumber("0x7fffffffffffffff"), math.maxinteger)
eq(tonumber("0xffffffffffffffff"), -1)
eq(1 < 1.5, true); eq(2^53 < 2^53 + 1, false)
eq(math.maxinteger < 2^63, true); eq(math.maxinteger + 0.0 == 2^63, true)
eq(math.maxinteger == 2^63, false)
eq(math.mininteger == -2^63, true)
eq(-2^63 <= math.mininteger, true); eq(math.mininteger < -2^63, false)
local nan = 0/0
check(not (nan < nan) and not (nan == nan) and nan ~= nan and not (nan <= 1) and not (1 >= nan), "nan cmp")
eq(math.abs(math.mininteger), math.mininteger)
eq(math.fmod(7, 3), 1); eq(math.fmod(-7, 3), -1); eq(math.fmod(7.5, 2), 1.5)
eq(math.floor(-3.5), -4); eq(math.ceil(-3.5), -3); eq(math.floor(2^62 + 0.0), 2^62)
eq(math.ult(1, -1), true); eq(math.ult(-1, 1), false)
eq(math.max(3, 7.5, -1), 7.5); eq(math.min(3, 7, -1), -1)
eq(3 | 5, 7); eq(3 & 5, 1); eq(3 ~ 5, 6); eq(~0, -1); eq(1 << 63, math.mininteger); eq(1 << 64, 0)
eq(-1 >> 1, math.maxinteger); eq(-1 >> 64, 0); eq(5 << -1, 2); eq(2.0 | 1, 3)
eq(math.huge > math.maxinteger, true)
eq(string.format("%.14g", 100 * 1.1), "110")
eq(7 // 0.0, 1/0); eq(-7 // 0.0, -1/0); eq(0/0 ~= 0/0, true)
check(not pcall(function() return 1 // 0 end), "idiv by zero")
check(not pcall(function() return 1 % 0 end), "mod by zero")
eq(math.sqrt(16), 4.0); eq(math.exp(0), 1.0); eq(math.log(8, 2), 3.0); eq(math.log(100, 10), 2.0)
eq(math.pi - math.pi % 0.001, 3.141)
eq(string.format("%.17g", 0.1), "0.10000000000000001")
eq(8 % 3.5, 1.0); eq(2^-1074 > 0, true); eq(5e-324 / 2, 0.0)
eq(math.tointeger("8"), 8)
eq(("10" + 1), 11); eq("3" * "4", 12); eq(10 .. 20, "1020"); eq(1.5 .. "", "1.5")
eq(-0.0 == 0.0, true); eq(1/(-0.0), -1/0)
for i = 1, 100 do local x = math.random(1, 10); check(x >= 1 and x <= 10, "random") end
math.randomseed(42); local a = math.random(1000000); math.randomseed(42); eq(math.random(1000000), a)
local f = math.random(); check(f >= 0 and f < 1, "random float")

-- strings
eq(("hello"):rep(3, "-"), "hello-hello-hello")
eq(("abc"):byte(-1), 99); eq(select("#", ("abc"):byte(1, -1)), 3)
eq(("Hello"):upper(), "HELLO"); eq(("Hello"):lower(), "hello"); eq(("abc"):reverse(), "cba")
eq(("hello world"):find("o w"), 5); eq(("hello"):find("l+"), 3)
eq(("hello world from lua"):match("(%w+) (%w+)"), "hello")
eq(select(2, ("abc abc"):gsub("b", "X")), 2)
eq(("abc"):gsub("%w", "%0%0"), "aabbcc")
eq(("hello world"):gsub("(%w+) (%w+)", "%2 %1"), "world hello")
eq(("  trim  "):match("^%s*(.-)%s*$"), "trim")
eq(("key=val"):gsub("(%w+)=(%w+)", function(k, v) return v .. "=" .. k end), "val=key")
local t = {}; for w in ("one two three"):gmatch("%a+") do t[#t + 1] = w end; eq(#t, 3); eq(t[3], "three")
eq(("f(a(b)c)"):match("%b()"), "(a(b)c)")
eq(("THE (quick) fox"):find("%f[%a]%a+%f[%A]"), 1)
eq(("x = 10"):match("()=()"), 3)
eq(string.len("\0\1\2"), 3); eq(#"\u{10FFFF}", 4); eq("\x41\65\z
      B", "AAB")
eq(("%d"):format(3), "3")
eq(table.concat({1, 2, 3}, ", "), "1, 2, 3"); eq(table.concat({}, "x"), "")
eq(("abc"):sub(2), "bc"); eq(("abc"):sub(-2, -2), "b"); eq(("abc"):sub(0), "abc"); eq(("abc"):sub(5), "")
eq(string.char(0xe4):byte(), 228)
eq(("a,b,,c"):gsub(",", ";", 2), "a;b;,c")
eq(tostring(nil), "nil"); eq(tostring(true), "true")
check(tostring({}):match("^table: 0x%x+$") or tostring({}):match("^table: "), "table tostring")
eq(("abc"):find("b", 1, true), 2); eq(("a.c"):find(".", 1, true), 2)
eq(string.format("%s", setmetatable({}, {__tostring = function() return "OBJ" end})), "OBJ")
eq(("%5s"):format("ab"), "   ab")

-- pack / unpack
eq(string.pack("i4", 100):byte(1), 100)
eq(string.unpack("<i2", "\255\255"), -1); eq(string.unpack("<I2", "\255\255"), 65535)
eq(string.unpack(">i4", string.pack(">i4", -123456)), -123456)
eq(string.unpack("d", string.pack("d", 3.14)), 3.14)
eq(string.unpack("f", string.pack("f", 0.5)), 0.5)
eq(string.packsize("i4i8"), 12); eq(string.packsize("!1i4i8"), 12); eq(string.packsize("<!4 i1 i8"), 12)
eq(string.unpack("z", "hello\0rest"), "hello")
eq(string.unpack("s1", "\5hello"), "hello")
eq(string.pack(">I3", 0x010203), "\1\2\3")
eq(string.unpack("<j", string.pack("<j", math.mininteger)), math.mininteger)
eq(string.unpack("<i16", string.pack("<i16", -2)), -2)
eq(#string.pack("i16", 1), 16)
check(not pcall(string.unpack, "<i9", string.rep("\255", 8) .. "\127"), "overflow i9")
eq(select("#", string.unpack("i4 i4", string.pack("i4 i4", 1, 2))), 3)
eq(string.unpack("<n", string.pack("<n", -0.25)), -0.25)

-- utf8
eq(utf8.char(72, 228, 8364, 0x10FFFF), "H\u{E4}\u{20AC}\u{10FFFF}")
eq(utf8.len("H\u{E4}\u{20AC}"), 3); eq(utf8.codepoint("\u{20AC}"), 8364)
local cps = {}; for p, c in utf8.codes("a\u{E4}b") do cps[#cps + 1] = c end; eq(#cps, 3); eq(cps[2], 228)
eq(utf8.offset("a\u{E4}b", 3), 4); eq(utf8.len("\xff"), nil)
eq(utf8.charpattern, "[\0-\x7F\xC2-\xFD][\x80-\xBF]*")

-- tables
local tt = {5, 2, 8, 1, 9}; table.sort(tt); eq(table.concat(tt, ","), "1,2,5,8,9")
table.sort(tt, function(a, b) return a > b end); eq(tt[1], 9)
table.insert(tt, 1, 0); eq(tt[1], 0); eq(table.remove(tt), 1); eq(#tt, 5)
eq(select("#", table.unpack({1, 2, nil, 4}, 1, 4)), 4)
eq(table.pack(1, nil, 3).n, 3)
local mv = table.move({1, 2, 3}, 1, 3, 2); eq(mv[3], 2)
local big = {}; for i = 1, 10000 do big[i] = (i * 7919) % 10007 end; table.sort(big)
local ok = true; for i = 2, #big do if big[i - 1] > big[i] then ok = false end end; check(ok, "big sort")
local h = {}; for i = 1, 1000 do h["k" .. i] = i end; local cnt = 0; for k, v in pairs(h) do cnt = cnt + v end
eq(cnt, 500500)
eq(next({}), nil); eq(rawlen({1, 2}), 2); eq(rawequal(h, h), true)
eq(#setmetatable({}, {__len = function() return 42 end}), 42)

-- metatables / closures / varargs / goto
local mt = {__add = function(a, b) return a.v + b.v end, __index = function(t, k) return k .. "!" end,
            __call = function(self, x) return x * 2 end, __eq = function() return true end,
            __lt = function(a, b) return a.v < b.v end, __le = function(a, b) return a.v <= b.v end,
            __concat = function(a, b) return "cat" end, __unm = function() return "neg" end,
            __idiv = function() return "idiv" end, __band = function() return "band" end,
            __shl = function() return "shl" end, __bnot = function() return "bnot" end}
local A, B = setmetatable({v = 1}, mt), setmetatable({v = 2}, mt)
eq(A + B, 3); eq(A.foo, "foo!"); eq(A(21), 42); eq(A == B, true); eq(A < B, true); eq(B <= A, false)
eq(A .. "x", "cat"); eq(-A, "neg"); eq(A // 1, "idiv"); eq(A & 1, "band"); eq(1 << A, "shl"); eq(~A, "bnot")
local function counter() local c = 0; return function() c = c + 1; return c end end
local c1, c2 = counter(), counter(); c1(); c1(); eq(c1(), 3); eq(c2(), 1)
local function va(...) return select("#", ...), ... end
eq(va(1, nil, nil), 3); eq(select(-1, 1, 2, 3), 3)
do local i = 1; ::top:: i = i + 1; if i < 10 then goto top end; eq(i, 10) end
local s = 0; for i = 10, 1, -3 do s = s + i end; eq(s, 22)
s = 0; for i = 1.0, 2.0, 0.25 do s = s + i end; eq(s, 7.5)
s = 0; for i = math.maxinteger - 2, math.maxinteger do s = s + 1 end; eq(s, 3)
s = 0; for i = math.mininteger, math.mininteger + 2 do s = s + 1 end; eq(s, 3)
s = 0; for i = 1, 0 do s = s + 1 end; eq(s, 0)

-- errors
local ok2, e = pcall(error, {code = 1}); eq(e.code, 1)
ok2, e = pcall(error, "msg", 0); eq(e, "msg")
ok2, e = pcall(function() local x = nil; return x.y end); check(e:find("attempt to index"), e)
ok2, e = pcall(function() return 1 + {} end); check(e:find("arithmetic"), e)
ok2, e = pcall(function() return #5 end); check(e:find("length"), e)
eq(select(2, xpcall(function() error("x") end, function(m) return "handled" end)), "handled")
ok2, e = pcall(string.rep); check(e:find("bad argument #1"), e)
ok2, e = pcall(function() local t = setmetatable({}, {__index = function(t, k) error("deep") end}); return t.x end)
check(e:find("deep"), e)
local function rec(n) if n == 0 then error("bottom") end return rec(n - 1) end
ok2, e = pcall(rec, 1000); check(e:find("bottom"), e)
local function inf(n) return 1 + inf(n) end
ok2, e = pcall(inf, 1); check(e:find("stack overflow"), e)
ok2, e = pcall(function() local a <close> = setmetatable({}, {__close = function() error("in close") end}) end)
check(e and e:find("in close"), e)
do
  local log = {}
  do local x <close> = setmetatable({}, {__close = function() log[#log + 1] = "closed" end}) end
  eq(log[1], "closed")
end

-- coroutines
local co = coroutine.create(function(a, b)
  local c = coroutine.yield(a + b)
  local d, e2 = coroutine.yield(c * 2)
  return d + e2
end)
eq(select(2, coroutine.resume(co, 1, 2)), 3); eq(select(2, coroutine.resume(co, 10)), 20)
eq(select(2, coroutine.resume(co, 3, 4)), 7); eq(coroutine.status(co), "dead")
eq(coroutine.resume(co), false)
local gen = coroutine.wrap(function() for i = 1, 3 do coroutine.yield(i) end end)
eq(gen() + gen() + gen(), 6)
local co2 = coroutine.create(function() error("in co") end)
local okc, ec = coroutine.resume(co2); eq(okc, false); check(ec:find("in co"), ec)
eq(coroutine.isyieldable(), false); eq(select(2, coroutine.running()), true)
local co3 = coroutine.wrap(function() local t = setmetatable({}, {__index = function(t, k) return coroutine.yield(k) end}); return t.abc end)
eq(co3(), "abc"); eq(co3("ret"), "ret")
local co4 = coroutine.create(function() coroutine.yield() end); coroutine.resume(co4)
eq(coroutine.close(co4), true); eq(coroutine.status(co4), "dead")
-- yields across pcall
local co5 = coroutine.wrap(function() local ok, v = pcall(function() return coroutine.yield(1) + 1 end); return v end)
eq(co5(), 1); eq(co5(41), 42)
-- table.sort with yields is not allowed; sort with errors
check(not pcall(table.sort, {3, 2, 1}, function(a, b) error("cmp") end), "sort err")

-- gc
collectgarbage(); local before = collectgarbage("count")
do local junk = {}; for i = 1, 100000 do junk[i] = {i} end end
collectgarbage(); collectgarbage()
check(collectgarbage("count") < before + 1000, "gc frees")
local weak = setmetatable({}, {__mode = "k"}); weak[{}] = 1; collectgarbage(); eq(next(weak), nil)
local fin = 0; do setmetatable({}, {__gc = function() fin = fin + 1 end}) end; collectgarbage(); eq(fin, 1)
eq(collectgarbage("isrunning"), true)
collectgarbage("generational"); for i = 1, 10000 do local x = {i} end; collectgarbage("incremental")
eq(type(collectgarbage("step")), "boolean")

-- load / dump
local fn = load("return 1 + ...", "chunk", "t"); eq(fn(41), 42)
local d = string.dump(function(x) return x * 3.5 + 1 end); eq(load(d, "d", "b")(2), 8.0)
local d2 = string.dump(load("local t = {} for i = 1, 10 do t[i] = i * 1.5 end return t[10], 'str', 2^63, math.mininteger"))
local r1, r2, r3, r4 = load(d2)(); eq(r1, 15.0); eq(r2, "str"); eq(r3, 2^63); eq(r4, math.mininteger)
check(not load("x ="), "syntax error")
local env = {y = 5}; eq(load("return y", "c", "t", env)(), 5)

-- os / io
check(type(os.time()) == "number", "os.time")
eq(os.time({year = 2020, month = 1, day = 1, hour = 12}) - os.time({year = 2020, month = 1, day = 2, hour = 12}), -86400)
eq(os.date("!%Y-%m-%d %H:%M:%S", 0), "1970-01-01 00:00:00")
eq(os.date("!*t", 86400).day, 2)
check(os.clock() >= 0, "clock"); check(os.getenv("HOME") ~= nil or true, "getenv")
local tmp = os.tmpname(); local fh = io.open(tmp, "w"); fh:write("line1\n", 42, " ", 3.5, "\nline3"); fh:close()
fh = io.open(tmp, "r"); eq(fh:read("l"), "line1"); eq(fh:read("n"), 42); eq(fh:read("n"), 3.5)
eq(fh:read("L"), "\n"); eq(fh:read("a"), "line3"); eq(fh:read("l"), nil); fh:close()
local lines = 0; for l in io.lines(tmp) do lines = lines + 1 end; eq(lines, 3)
fh = io.open(tmp, "r"); eq(fh:seek("end"), 18); eq(fh:seek("set", 2), 2); eq(fh:read(3), "ne1"); fh:close()
eq(os.remove(tmp), true); eq(io.open(tmp), nil)
local p = io.popen("echo hi"); eq(p:read("l"), "hi"); check(p:close(), "popen close")
eq(io.type(io.stdout), "file"); eq(io.type(42), nil)
check(select(2, os.execute("exit 3")) == "exit", "execute")
eq(select(3, os.execute("exit 3")), 3)

print("checks: " .. n)
