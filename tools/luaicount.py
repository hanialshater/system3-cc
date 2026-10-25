#!/usr/bin/env python3
"""Instruction counts (cachegrind) of the W7 runtime programs at reduced sizes.
Usage: luaicount.py [lua-binary ...]"""
import subprocess, sys, os, re, math
V = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
W = V + '/.work/luaic'
os.makedirs(W, exist_ok=True)
scale = {'bintrees': ('local N = 12', 'local N = 10'), 'fannkuch': ('local N = 9', 'local N = 8'),
         'fib': ('local N = 30', 'local N = 24'), 'nbody': ('local N = 100000', 'local N = 10000'),
         'spectral': ('local N = 250', 'local N = 80'), 'strops': ('local N = 100000', 'local N = 10000')}
for n, (a, b) in scale.items():
    src = open(f'{V}/spec/w7/bench/{n}.lua').read()
    assert a in src, n
    open(f'{W}/{n}.lua', 'w').write(src.replace(a, b, 1))
bins = sys.argv[1:] or [V + '/.work/lua7/lua']
res = {}
for b in bins:
    row = []
    for n in scale:
        r = subprocess.run(['valgrind', '--tool=cachegrind', '--cache-sim=no', '--cachegrind-out-file=/dev/null', b, f'{n}.lua'],
                           cwd=W, capture_output=True, text=True)
        m = re.search(r'I\s+refs:\s+([\d,]+)', r.stderr)
        row.append(int(m.group(1).replace(',', '')) / 1e6 if m else float('nan'))
    res[b] = row
print('%-12s' % '' + ''.join('%11s' % n for n in scale) + '    geomean')
for b, row in res.items():
    g = math.exp(sum(math.log(x) for x in row) / len(row))
    print('%-12s' % os.path.basename(b)[:12] + ''.join('%11.2f' % x for x in row) + '%11.2f' % g)
