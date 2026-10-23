-- W7 benchmark: binary trees (allocation, the garbage collector, recursion)
local N = 12
local function bottomUp(d) if d == 0 then return {} end d = d - 1; return { bottomUp(d), bottomUp(d) } end
local function check(t) if t[1] then return 1 + check(t[1]) + check(t[2]) end return 1 end
local mindepth, maxdepth = 4, N
local stretch = maxdepth + 1
print(string.format("stretch tree of depth %d\t check: %d", stretch, check(bottomUp(stretch))))
local long = bottomUp(maxdepth)
for d = mindepth, maxdepth, 2 do
  local iters = 2 ^ (maxdepth - d + mindepth) // 1
  local c = 0
  for i = 1, iters do c = c + check(bottomUp(d)) end
  print(string.format("%d\t trees of depth %d\t check: %d", iters, d, c))
end
print(string.format("long lived tree of depth %d\t check: %d", maxdepth, check(long)))
