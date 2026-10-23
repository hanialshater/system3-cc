#!/usr/bin/env python3
"""Deterministic instruction counts (valgrind/cachegrind) on scaled-down copies of
the benchmarks. Usage: icount.py [names...]"""
import subprocess, sys, os, re
V = '/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/V'
CLJ = '/tmp/claude-0/-home-user/aa2da695-733d-5610-822b-d0f420a6080f/scratchpad/b8-runs/toolchain/clj-run'
B = V + '/.work/icount'
os.makedirs(B, exist_ok=True)
SCALE = {'fib': {'N': '27'}, 'sieve': {'N': '1000000', 'ROUNDS': '1'}, 'matmul': {'N': '120'},
         'sort': {'N': '200000'}, 'bintree': {'DEPTH': '12'}, 'fannkuch': {'N': '8'},
         'crc': {'MB': '1'}, 'vm': {'ITER': '300000'}}
names = sys.argv[1:] or sorted(SCALE)
with open(B + '/list', 'w') as f:
    for n in names:
        src = open(f'{V}/spec/bench/{n}.c').read()
        for k, v in SCALE[n].items():
            src = re.sub(rf'#define {k} \S+', f'#define {k} {v}', src)
        open(f'{B}/{n}.c', 'w').write(src)
        f.write(f'{B}/{n}.c {B}/{n}.s\n')
subprocess.run([CLJ, '-m', 'ccc.main', '-I', V + '/spec/include', '--batch', B + '/list'], cwd=V)
total = 1.0
for n in names:
    subprocess.run(['gcc', f'{B}/{n}.s', '-o', f'{B}/{n}'])
    r = subprocess.run(['valgrind', '--tool=cachegrind', '--cache-sim=no', f'--cachegrind-out-file={B}/cg.out', f'{B}/{n}'],
                       capture_output=True, text=True)
    m = re.search(r'I\s+refs:\s+([\d,]+)', r.stderr)
    ir = int(m.group(1).replace(',', '')) if m else -1
    total *= ir
    print(f'{n:10s} {ir/1e6:10.2f} M')
print(f'geomean {total ** (1 / len(names)) / 1e6:.2f} M')
