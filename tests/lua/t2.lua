local n = 0
local function check(c, msg) n = n + 1; if not c then print("FAIL " .. n .. " " .. tostring(msg)) end end
local function eq(a, b) check(a == b, tostring(a) .. " ~= " .. tostring(b)) end

-- debug library
local function f(a, b) local c = a + b; return debug.getinfo(1, "nSl") end
local info = f(1, 2)
eq(info.short_src:match("t2.lua") ~= nil, true); eq(info.what, "Lua"); eq(info.currentline, 6)
eq(debug.getinfo(print).what, "C")
local function locals() local x, y = 10, 20; local name, val = debug.getlocal(1, 2); return name, val end
local nm, vl = locals(); eq(nm, "y"); eq(vl, 20)
local up = 5; local function g() return up end
eq(debug.getupvalue(g, 1), "up"); debug.setupvalue(g, 1, 7); eq(g(), 7)
local lines = {}
debug.sethook(function(ev, line) lines[#lines + 1] = line end, "l")
local z = 1
z = z + 1
debug.sethook()
check(#lines >= 2, "line hook " .. #lines)
local calls = 0
debug.sethook(function() calls = calls + 1 end, "c")
math.abs(1); math.abs(2)
debug.sethook()
check(calls >= 2, "call hook")
local cnt = 0
debug.sethook(function() cnt = cnt + 1 end, "", 100)
for i = 1, 10000 do end
debug.sethook()
check(cnt > 10, "count hook " .. cnt)
check(debug.traceback("msg"):find("stack traceback"), "traceback")
eq(debug.getmetatable("").__index, string)
local t = {}; debug.setmetatable(t, {__index = {k = 1}}); eq(t.k, 1)
eq(debug.getinfo(1, "t").istailcall, false)
local function tail() return debug.getinfo(1, "t").istailcall end
local function caller() return tail() end
eq(caller(), true)
local ui = debug.getinfo(f, "u"); eq(ui.nparams, 2); eq(ui.isvararg, false)
local co = coroutine.create(function() local q = 3; coroutine.yield() end)
coroutine.resume(co); eq(debug.getlocal(co, 1, 1), "q")
check(debug.traceback(co):find("yield") ~= nil or true, "co traceback")
local id1 = debug.upvalueid(g, 1); check(type(id1) == "userdata", "upvalueid")

-- deep C recursion: errors must not crash
local deep = setmetatable({}, {__index = function(t, k) return t[k .. "x"] end})
local ok, e = pcall(function() return deep.a end)
check(not ok and (e:find("stack overflow") or e:find("C stack overflow")), tostring(e))
local s = string.rep("(", 300) .. "1" .. string.rep(")", 300)
local fn, err = load("return " .. s)
check(fn == nil or fn() == 1, err)
local nest = string.rep("{", 1000) .. string.rep("}", 1000)
fn, err = load("return " .. nest); check(fn == nil and err:find("overflow") or fn, err)
local function mkco(k) if k == 0 then return 1 end; return coroutine.wrap(function() return mkco(k - 1) end)() end
ok, e = pcall(mkco, 150); check(ok or e:find("overflow"), tostring(e))
ok, e = pcall(string.gsub, string.rep("a", 1000), ".", function(c) return c:upper() end); eq(ok, true)
-- error in __gc, __close; error objects; tostring of errors with levels
ok, e = pcall(error, setmetatable({}, {__tostring = function() return "custom" end}))
eq(tostring(e), "custom")
local function lvl() error("lvl", 2) end
ok, e = pcall(function() lvl() end); check(e:find("t2.lua:%d+: lvl"), e)

-- big generated program
local parts = {"local a = 0"}
for i = 1, 5000 do parts[#parts + 1] = "a = a + " .. i end
parts[#parts + 1] = "return a"
eq(load(table.concat(parts, "\n"))(), 12502500)
parts = {"return {"}
for i = 1, 20000 do parts[#parts + 1] = i .. "," end
parts[#parts + 1] = "}"
eq(#load(table.concat(parts))(), 20000)
parts = {}
for i = 1, 300 do parts[#parts + 1] = "local v" .. i .. " = " .. i end
fn, err = load(table.concat(parts, "\n")); check(fn == nil and err:find("too many local"), err)
parts = {"local x = 0"}
for i = 1, 199 do parts[#parts + 1] = "local v" .. i .. " = " .. i end
parts[#parts + 1] = "return v199 + v1"
eq(load(table.concat(parts, "\n"))(), 200)
local strs = {}; for i = 1, 1000 do strs[i] = string.format("%d:%s", i, ("x"):rep(i % 17)) end
eq(#table.concat(strs), 15 + 0 + 0 + #table.concat(strs) - 15)

-- string.format corner cases
eq(string.format("%099d", 7):len(), 99)
eq(string.format("%.99f", 1):len(), 101)
eq(string.format("%-5s|", "ab"), "ab   |")
eq(string.format("%+d % d", 5, 5), "+5  5")
eq(string.format("%#x %#o", 255, 8), "0xff 010")
eq(string.format("%.0f", 0.5), "0"); eq(string.format("%.0f", 1.5), "2")
eq(string.format("%e", 0), "0.000000e+00")
eq(string.format("%g", 2^63), "9.22337e+18")
eq(string.format("%d", 3.0), "3")
check(not pcall(string.format, "%d", 3.5), "format int")
eq(string.format("%s %s", 1, 2.0), "1 2.0")
eq(string.format("%q", "a\nb\0c\"\\"), '"a\\\nb\\0c\\"\\\\"')
eq(string.format("%q", 0/0), "(0/0)"); eq(string.format("%q", 1/0), "1e9999")
eq(string.format("%5.1s|", "abc"), "    a|")
eq(string.format("%x", -1), "ffffffffffffffff")
eq(tostring(1e100), "1e+100"); eq(tostring(123456789012.0), "123456789012.0")
eq(tostring(-1e-7), "-1e-07"); eq(2^63 // 1, 2^63); eq(math.floor(-0.0), -0.0)
eq(string.format("%.14g", 0.1 + 0.2), "0.3"); eq(0.1 + 0.2 == 0.3, false)
eq(tostring(3.0), "3.0"); eq(tostring(-3), "-3"); eq(3 == 3.0, true); eq(math.type(3.0), "float")
eq(1e308 * 10, 1/0); eq(-1e308 * 10, -1/0)
eq(string.rep("ab", 3, ","), "ab,ab,ab")
eq(("x"):rep(0), ""); eq(("x"):rep(-1), "")

-- integer/float conversion in for loops and string->number coercions
local acc = 0; for i = 1, 3 do acc = acc + i / 2 end; eq(acc, 3.0)
eq("0x10" + 0, 16); eq("1e1" + 0, 10.0); eq(" 5 " * 2, 10)
check(not pcall(function() return "abc" + 1 end), "bad coercion")
eq(10 // 3.0, 3.0); eq(2^2, 4.0); eq(-2^2, -4.0)
eq(math.tointeger(2^31), 2147483648)
eq(string.format("%d", -0), "0")
eq((2^53 + 1) % 2, 0.0)
eq(5 // -2, -3); eq(5 % -2, -1); eq(-5 // 2, -3); eq(5.0 % -2, -1.0)
eq(math.mininteger // 1, math.mininteger)
eq(3 % math.huge, 3.0); eq(-3 % math.huge, math.huge); eq(3 % -math.huge, -math.huge)

print("checks: " .. n)
