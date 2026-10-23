-- W7 benchmark: recursive calls (function calls, integer arithmetic, comparisons)
local N = 30
local function fib(n) if n < 2 then return n end return fib(n - 1) + fib(n - 2) end
local s = 0
for i = N - 2, N do s = s + fib(i) end
print(string.format("fib %d: %d", N, s))
