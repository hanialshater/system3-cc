#!/usr/bin/env python3
"""runt.py DIR... -- compile every DIR/*.c in batch mode, assemble, run, compare with .expected/.exit."""
import os, sys, subprocess, concurrent.futures as cf

V = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLJ = os.path.join(os.path.dirname(V), 'toolchain', 'clj-run')

def run(d):
    w = os.path.join(V, 'build', 't-' + os.path.basename(d))
    os.makedirs(w, exist_ok=True)
    ids = sorted(f[:-2] for f in os.listdir(d) if f.endswith('.c'))
    lst = os.path.join(w, 'list.txt')
    with open(lst, 'w') as f:
        for i in ids:
            f.write('%s %s\n' % (os.path.join(d, i + '.c'), os.path.join(w, i + '.s')))
    for i in ids:
        try: os.remove(os.path.join(w, i + '.s'))
        except OSError: pass
    r = subprocess.run([CLJ, '-m', 'ccc.main', '-I', os.path.join(V, 'spec', 'include'), '--batch', lst], cwd=V,
                       capture_output=True, text=True)
    if r.stderr.strip():
        print(r.stderr[:2000])

    def one(i):
        s = os.path.join(w, i + '.s')
        if not os.path.exists(s):
            return i, 'compile'
        exe = os.path.join(w, i)
        a = subprocess.run(['/usr/bin/gcc', s, '-o', exe, '-lm'], capture_output=True, text=True)
        if a.returncode:
            return i, 'asm: ' + a.stderr[:300]
        try:
            p = subprocess.run([exe], capture_output=True, timeout=20, stdin=subprocess.DEVNULL)
        except subprocess.TimeoutExpired:
            return i, 'timeout'
        ef = os.path.join(d, i + '.expected')
        exp = open(ef, 'rb').read() if os.path.exists(ef) else b''
        xf = os.path.join(d, i + '.exit')
        ec = int(open(xf).read().strip() or 0) if os.path.exists(xf) else 0
        if p.stdout != exp:
            return i, 'output differs'
        if p.returncode != ec:
            return i, 'exit %d != %d' % (p.returncode, ec)
        return i, None

    with cf.ThreadPoolExecutor(4) as ex:
        res = list(ex.map(one, ids))
    bad = [(i, m) for i, m in res if m]
    print(d, '%d/%d passed' % (len(res) - len(bad), len(res)))
    for i, m in bad:
        print('FAIL', i, m)

for d in sys.argv[1:]:
    run(os.path.abspath(d))
