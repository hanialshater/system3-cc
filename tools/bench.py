#!/usr/bin/env python3
"""Compile and time the benchmark programs. Usage: bench.py [runs] [names...]"""
import subprocess, sys, os, time
V = '/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V'
CLJ = '/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/toolchain/clj-run'
B = V + '/.work/bench'
os.makedirs(B, exist_ok=True)
runs = int(sys.argv[1]) if len(sys.argv) > 1 else 3
names = sys.argv[2:] or sorted(f[:-2] for f in os.listdir(V + '/spec/bench') if f.endswith('.c'))
with open(B + '/list', 'w') as f:
    for n in names:
        f.write(f'{V}/spec/bench/{n}.c {B}/{n}.s\n')
subprocess.run([CLJ, '-m', 'ccc.main', '-I', V + '/spec/include', '--batch', B + '/list'], cwd=V)
total = 1.0
for n in names:
    if subprocess.run(['gcc', f'{B}/{n}.s', '-o', f'{B}/{n}']).returncode != 0:
        print(n, 'ASM FAIL'); continue
    exp = open(f'{V}/spec/bench/{n}.expected').read()
    ts = []; ok = True
    for i in range(runs):
        t = time.time()
        r = subprocess.run([f'{B}/{n}'], capture_output=True, text=True)
        ts.append(time.time() - t)
        ok = ok and r.stdout == exp and r.returncode == 0
    ts.sort()
    med = ts[len(ts) // 2]
    total *= med
    print(f'{n:10s} {med:.3f}s {"OK" if ok else "WRONG"}')
print(f'geomean {total ** (1 / len(names)):.3f}s')
