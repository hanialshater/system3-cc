#!/usr/bin/env python3
"""Random float/double expression test. Usage: gen_floattest.py <seed> <out.c> <out.expected>"""
import random, struct, sys, math

def f32(x):
    try:
        return struct.unpack('f', struct.pack('f', x))[0]
    except OverflowError:
        return math.copysign(math.inf, x)

def conv(v, t):
    if t == 'float': return f32(float(v))
    if t == 'double': return float(v)
    if t == 'int':
        return int(v) if isinstance(v, int) else int(math.trunc(v))
    if t == 'long':
        return int(v) if isinstance(v, int) else int(math.trunc(v))
    raise ValueError

def common(a, b):
    if 'double' in (a, b): return 'double'
    if 'float' in (a, b): return 'float'
    if 'long' in (a, b): return 'long'
    return 'int'

class E:
    def __init__(s, code, val, ty): s.code, s.val, s.ty = code, val, ty

def fmt(v, ty):
    return repr(v) if ty in ('float', 'double') else str(v)

def gen(depth, vars_):
    if depth == 0 or random.random() < 0.3:
        if random.random() < 0.7:
            n, t, v = random.choice(vars_)
            return E(n, v, t)
        t = random.choice(['float', 'double', 'int'])
        if t == 'int':
            v = random.randint(-100, 100); return E(str(v), v, 'int')
        v = random.choice([0.5, 1.25, 3.0, 0.1, 1e10, 123.456, 2.0 ** -20])
        if t == 'float':
            return E(repr(v) + 'f', f32(v), 'float')
        return E(repr(v), v, 'double')
    k = random.random()
    if k < 0.15:
        e = gen(depth - 1, vars_)
        t = random.choice(['float', 'double', 'int'])
        if t == 'int':
            if e.ty in ('float', 'double') and not (abs(e.val) < 2e9): return e
            return E(f'((int){e.code})', conv(e.val, 'int'), 'int')
        return E(f'(({t}){e.code})', conv(e.val, t), t)
    if k < 0.22:
        e = gen(depth - 1, vars_)
        return E(f'(-({e.code}))', -e.val, e.ty)
    a, b = gen(depth - 1, vars_), gen(depth - 1, vars_)
    op = random.choice(['+', '-', '*', '/', '<', '>', '==', '!=', '<=', '>='])
    ct = common(a.ty, b.ty)
    av, bv = conv(a.val, ct), conv(b.val, ct)
    if op in '<>' or op in ('==', '!=', '<=', '>='):
        r = {'<': av < bv, '>': av > bv, '==': av == bv, '!=': av != bv, '<=': av <= bv, '>=': av >= bv}[op]
        return E(f'({a.code} {op} {b.code})', int(r), 'int')
    if op == '/' and bv == 0:
        op = '+'
    if ct in ('int', 'long'):
        if op == '/':
            q = abs(av) // abs(bv); r = q if (av >= 0) == (bv >= 0) else -q
        else:
            r = {'+': av + bv, '-': av - bv, '*': av * bv}[op]
        if not (-2**31 <= r < 2**31): return a
        return E(f'({a.code} {op} {b.code})', r, ct)
    r = {'+': av + bv, '-': av - bv, '*': av * bv, '/': av / bv if bv else 0.0}[op]
    if ct == 'float': r = f32(r)
    if math.isinf(r) or math.isnan(r) or abs(r) > 1e300: return a
    return E(f'({a.code} {op} {b.code})', r, ct)

def main():
    seed, oc, oe = int(sys.argv[1]), sys.argv[2], sys.argv[3]
    random.seed(seed)
    vars_ = []
    lines = ['#include <stdio.h>', 'int main(void) {']
    for i, (t, v) in enumerate([('float', 1.5), ('float', -0.3), ('double', 2.75), ('double', -1e-3), ('int', 7), ('int', -3), ('long', 100000)]):
        v = conv(v, t)
        lines.append(f'  {t} x{i} = {repr(v) if t != "int" and t != "long" else v}{"f" if t == "float" else ""};')
        vars_.append((f'x{i}', t, v))
    exp = []
    for k in range(300):
        e = gen(4, vars_)
        if e.ty in ('float', 'double'):
            lines.append(f'  printf("%.17g\\n", (double)({e.code}));')
            exp.append('%.17g' % e.val)
        else:
            lines.append(f'  printf("%ld\\n", (long)({e.code}));')
            exp.append(str(e.val))
        if k % 4 == 0:
            n, t, _ = random.choice(vars_)
            if t in ('int', 'long') and e.ty != t and not (isinstance(e.val, int) or abs(e.val) < 2e9):
                continue
            nv = conv(e.val, t)
            lines.append(f'  {n} = {e.code};')
            vars_ = [(a, b, nv if a == n else c) for (a, b, c) in vars_]
    lines.append('  return 0;\n}')
    open(oc, 'w').write('\n'.join(lines) + '\n')
    open(oe, 'w').write('\n'.join(exp) + '\n')

main()
