-- Benchmark 8, W7: run ONE file of Lua 5.4's test suite (testes/) the way all.lua runs it, and print the line
-- `W7-TEST-OK <file>` when it returns. Usage (from the tests' directory): lua w7test.lua <mode> <file>
-- A test passes when the interpreter prints that line and exits 0, with empty stdin (a pipe), within its time limit.
-- Every file goes through string.dump/load as all.lua's dofile does (gc.lua too: run directly, its weak-table memory
-- check depends on the string table's size at that point); strings.lua and literals.lua run directly, as in all.lua.
--   mode: "full" (_soft, _port false: all.lua's Linux defaults) | "soft" | "port" | "user" (all.lua's _U)
local mode, name = arg[1], arg[2]
_soft = (mode == "soft" or mode == "user")
_port = (mode == "port" or mode == "user")
_nomsg = true
_G.ARG = { [-1] = arg[-1], [0] = name }   -- what all.lua saves for main.lua: the interpreter's name
debug = nil
T = nil
function Message (m) end
assert(os.setlocale"C")
math.randomseed(42)
-- all.lua's dofile: through string.dump (dump/undump), strip for some
local function dumped (n, strip)
  local f = assert(loadfile(n))
  local b = string.dump(f, strip)
  f = assert(load(b, "@" .. n))
  return f()
end
local STRIP = { ["code.lua"] = true, ["goto.lua"] = true, ["sort.lua"] = true, ["verybig.lua"] = true }
local DIRECT = { ["strings.lua"] = true, ["literals.lua"] = true }
local WANT = { ["attrib.lua"] = 27, ["locals.lua"] = 5, ["events.lua"] = 12, ["verybig.lua"] = 10 }
if name == "big.lua" then
  local f = coroutine.wrap(assert(loadfile(name)))
  assert(f() == 'b'); assert(f() == 'a')
elseif name == "calls.lua" then
  local r = dumped(name)
  assert(r == deep and deep)
  _G.deep = nil
elseif DIRECT[name] then
  dofile(name)
else
  local r = dumped(name, STRIP[name])
  if WANT[name] then assert(r == WANT[name], name .. " returned " .. tostring(r)) end
end
collectgarbage()
io.stdout:write("\nW7-TEST-OK " .. name .. "\n")
