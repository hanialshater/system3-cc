#!/usr/bin/env python3
"""Find the first output line where a generated test diverges.
Usage: bisect_gen.py <test.c> <expected>"""
import subprocess, sys, os
V = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
CLJ = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'clj-run')
src, exp = sys.argv[1], sys.argv[2]
lines = open(src).read().split('\n')
expected = open(exp).read().split('\n')
body_start = next(i for i, l in enumerate(lines) if 'printf' in l)
end = next(i for i in range(len(lines) - 1, -1, -1) if 'return 0;' in lines[i])
d = V + '/.work/bisect'
os.makedirs(d, exist_ok=True)
cuts = list(range(body_start, end + 1, max(1, (end - body_start) // 60)))
with open(d + '/list', 'w') as f:
    for c in cuts:
        prog = '\n'.join(lines[:c] + ['  return 0;', '}']) + '\n'
        open(f'{d}/c{c}.c', 'w').write(prog)
        f.write(f'{d}/c{c}.c {d}/c{c}.s\n')
subprocess.run([CLJ, '-m', 'ccc.main', '-I', V + '/spec/include', '--batch', d + '/list'], cwd=V)
for c in cuts:
    subprocess.run(['gcc', f'{d}/c{c}.s', '-o', f'{d}/c{c}'])
    r = subprocess.run([f'{d}/c{c}'], capture_output=True, text=True)
    out = r.stdout.split('\n')
    n = len(out) - 1
    ok = r.returncode == 0 and out[:n] == expected[:n]
    if not ok:
        print('first bad cut', c)
        for l in lines[max(body_start, c - 8):c]:
            print('   ', l)
        for i in range(n):
            if i >= len(expected) or out[i] != expected[i]:
                print('line', i, 'got', out[i], 'expected', expected[i] if i < len(expected) else None)
                break
        print('rc', r.returncode)
        break
