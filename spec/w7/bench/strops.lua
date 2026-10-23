-- W7 benchmark: string operations (concatenation, string.format, gsub, find, byte/char, table.concat, hashing)
local N = 100000
local SEED = 12345
local x = SEED
local function rnd(m) x = (x * 1103515245 + 12345) % 2147483648; return x % m end
local parts = {}
for i = 1, N do parts[#parts + 1] = string.format("%d:%s;", rnd(1000), string.char(97 + rnd(26), 97 + rnd(26), 97 + rnd(26))) end
local s = table.concat(parts)
local count, total = 0, 0
for num, word in s:gmatch("(%d+):(%a+);") do count = count + 1; total = total + tonumber(num) + #word end
local up = s:gsub("%a+", string.upper)
local h = 0
for i = 1, #up, 7 do h = (h * 31 + up:byte(i)) % 1000000007 end
local words = {}
for w in s:gmatch("%a+") do words[w] = (words[w] or 0) + 1 end
local distinct = 0
for _ in pairs(words) do distinct = distinct + 1 end
local acc = ""
for i = 1, N // 10 do acc = acc .. string.rep("ab", i % 5) if #acc > 1000 then acc = acc:sub(-100) end end
local pos, hits = 1, 0
while true do local a = s:find("a", pos, true); if not a then break end; hits = hits + 1; pos = a + 1 end
print(string.format("%d %d %d %d %d %d %s", #s, count, total, h, distinct, hits, acc:sub(1, 20)))
