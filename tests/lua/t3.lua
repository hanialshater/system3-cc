local n = 0
local function check(c, msg) n = n + 1; if not c then print("FAIL " .. n .. " " .. tostring(msg)) end end
local function eq(a, b) check(a == b, tostring(a) .. " ~= " .. tostring(b)) end

local tmp = os.tmpname()
local f = assert(io.open(tmp, "wb"))
f:write("0x10 .5 -.5e-3 1e2 0x1p4 12abc\n")
f:write("second line\r\n")
f:write(string.rep("x", 10000), "\n")
f:close()
f = assert(io.open(tmp, "rb"))
eq(f:read("n"), 16); eq(f:read("n"), 0.5); eq(f:read("n"), -0.0005); eq(f:read("n"), 100.0)
eq(f:read("n"), 16.0); eq(f:read("n"), 12); eq(f:read(3), "abc"); eq(f:read("l"), "")
eq(f:read("L"), "second line\r\n"); eq(#f:read("l"), 10000); eq(f:read("a"), ""); eq(f:read("a"), "")
eq(f:read(0), nil); f:close()
check(not pcall(f.read, f), "closed file")
eq(tostring(f), "file (closed)")
eq(io.type(f), "closed file")
-- lines with formats
local cnt = 0
for a, b in io.lines(tmp, 1, 2) do cnt = cnt + 1; if cnt == 1 then eq(a, "0"); eq(b, "x1") end end
check(cnt > 3000, "lines count " .. cnt)
for l in io.lines(tmp, "L") do eq(l, "0x10 .5 -.5e-3 1e2 0x1p4 12abc\n"); break end
check(not pcall(io.lines, "/nonexistent/file"), "lines nofile")
-- io.input / io.output
local old = io.output()
io.output(tmp); io.write("abc", 1, 2.5); io.close(); io.output(old)
io.input(tmp); eq(io.read("a"), "abc12.5"); io.close(io.input()); io.input(io.stdin)
-- append, seek, setvbuf
f = io.open(tmp, "a+"); f:write("XYZ"); eq(f:seek("cur"), 10); eq(f:seek("set"), 0); eq(f:read(3), "abc"); f:close()
f = io.open(tmp, "w"); eq(f:setvbuf("full", 1024), true); eq(f:setvbuf("no"), true); eq(f:setvbuf("line"), true); f:close()
check(not pcall(io.open, tmp, "rw"), "bad mode")
-- rename / remove
local tmp2 = tmp .. ".2"
eq(os.rename(tmp, tmp2), true); eq(io.open(tmp), nil); eq(os.remove(tmp2), true)
local r1, r2, r3 = os.remove(tmp2); eq(r1, nil); check(type(r2) == "string", "remove msg"); check(type(r3) == "number", "errno")
-- tmpfile
f = io.tmpfile(); f:write("hello"); f:seek("set"); eq(f:read("a"), "hello"); f:close()
-- stdin seek on a pipe/devnull, stdout
check(io.stdout:write("") == io.stdout, "write returns file")
-- os.date / time
local t0 = os.time({year = 2000, month = 1, day = 1, hour = 0})
local d = os.date("*t", t0); eq(d.year, 2000); eq(d.month, 1); eq(d.day, 1); eq(d.yday, 1); eq(d.wday, 7)
eq(os.date("%Y-%m-%d", t0), "2000-01-01")
local norm = os.time({year = 2000, month = 13, day = 1, hour = 12}); eq(os.date("%Y-%m", norm), "2001-01")
local t2 = os.time({year = 2000, month = 1, day = 1, hour = 0, min = 0, sec = -1}); eq(t0 - t2, 1)
eq(os.difftime(t0, t2), 1.0)
check(#os.date("%c") > 10, "date c"); check(not pcall(os.date, "%Ez"), "bad date conv")
check(not pcall(os.date, "%"), "bad date conv2")
eq(os.date("!%H:%M", 3600 * 5 + 60 * 7), "05:07")
eq(os.date("!%j %a %b %p", 0), "001 Thu Jan AM")
local tt = os.date("!*t", 0); eq(tt.isdst, false); eq(tt.hour, 0)
check(not pcall(os.time, {year = 2000}), "time missing field")
eq(os.time({year = 2000, month = 1, day = 1, hour = 0, isdst = false}) ~= nil, true)
check(not pcall(os.time, {year = 1e10, month = 1, day = 1}), "big year")
check(not pcall(os.time, {year = 2^60, month = 1, day = 1}), "year overflow")
check(type(os.clock()) == "number", "clock")
eq(os.getenv("__NOPE_NOT_SET__"), nil)
-- popen read/write
local p = io.popen("cat > /dev/null", "w"); p:write("data"); local ok, how, code = p:close(); eq(ok, true); eq(how, "exit"); eq(code, 0)
p = io.popen("exit 5"); local okp, howp, codep = p:close(); eq(okp, nil); eq(howp, "exit"); eq(codep, 5)
p = io.popen("printf 'a\\nb\\n'"); local ls = {}; for l in p:lines() do ls[#ls + 1] = l end; p:close(); eq(#ls, 2)
eq(os.execute(), true)
-- string <-> number formatting through io
f = io.tmpfile(); f:write(1e100, " ", -0.0, " ", math.mininteger, " ", 2^63); f:seek("set")
eq(f:read("a"), "1e+100 -0 -9223372036854775808 9.2233720368548e+18"); f:close()
-- require / package
eq(type(package.path), "string"); eq(type(package.loaded.string), "table")
eq(package.config:sub(1, 1), "/")
local okr, er = pcall(require, "no_such_module_xyz"); eq(okr, false); check(er:find("not found"), er)
package.preload.mymod = function(name, extra) return {name = name, extra = extra} end
local m = require("mymod"); eq(m.name, "mymod"); eq(m.extra, ":preload:")
local modf = os.tmpname()
f = io.open(modf .. ".lua", "w"); f:write("return {v = 42, args = select('#', ...)}"); f:close()
package.path = modf:match("^(.*)/") .. "/?.lua;" .. package.path
local mm = require(modf:match("([^/]*)$")); eq(mm.v, 42); eq(mm.args, 2)
os.remove(modf .. ".lua"); os.remove(modf)
eq(select(2, package.searchpath("nonexist", "./?.lua")):find("no file") ~= nil, true)
check(package.loadlib == nil or type(package.loadlib) == "function", "loadlib")
os.remove(tmp)
print("checks: " .. n)
