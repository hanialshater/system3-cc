#!/usr/bin/env python3
"""Find the first statement line of main() that makes compilation fail.
Usage: bisect_compile.py <test.c>"""
import subprocess, sys, os
V = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
CLJ = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'clj-run')
lines = open(sys.argv[1]).read().split('\n')
start = next(i for i, l in enumerate(lines) if 'main' in l) + 1
end = next(i for i in range(len(lines) - 1, -1, -1) if 'return 0;' in lines[i])
d = V + '/.work/bisectc'
os.makedirs(d, exist_ok=True)
with open(d + '/list', 'w') as f:
    for c in range(start, end + 1):
        open(f'{d}/c{c}.c', 'w').write('\n'.join(lines[:c] + ['  return 0;', '}']) + '\n')
        f.write(f'{d}/c{c}.c {d}/c{c}.s\n')
subprocess.run([CLJ, '-m', 'ccc.main', '-I', V + '/spec/include', '--batch', d + '/list'], cwd=V,
               stderr=subprocess.DEVNULL)
for c in range(start, end + 1):
    if not os.path.exists(f'{d}/c{c}.s'):
        print('first failing line', c, ':', lines[c - 1])
        break
