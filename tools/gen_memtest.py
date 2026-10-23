#!/usr/bin/env python3
"""Random test of loads/stores through globals, locals, arrays, pointers and
struct fields, with compound assignments and increments.
Usage: gen_memtest.py <seed> <out.c> <out.expected>"""
import random, sys

TYPES = {'signed char': (8, True), 'unsigned char': (8, False), 'short': (16, True),
         'unsigned short': (16, False), 'int': (32, True), 'unsigned': (32, False),
         'long': (64, True), 'unsigned long': (64, False)}

def wrap(v, t):
    bits, s = TYPES[t]
    v &= (1 << bits) - 1
    if s and v >= 1 << (bits - 1):
        v -= 1 << bits
    return v

def promote(t):
    return 'int' if TYPES[t][0] < 32 else t

def common(a, b):
    a, b = promote(a), promote(b)
    (ba, sa), (bb, sb) = TYPES[a], TYPES[b]
    if sa == sb:
        return a if ba >= bb else b
    u, s_ = (a, b) if not sa else (b, a)
    return u if TYPES[u][0] >= TYPES[s_][0] else s_

class Loc:
    """An lvalue: C code and a key into the state dict."""
    def __init__(self, code, key, ty):
        self.code, self.key, self.ty = code, key, ty

def main():
    seed, oc, oe = int(sys.argv[1]), sys.argv[2], sys.argv[3]
    random.seed(seed)
    state = {}
    locs = []
    out = ['#include <stdio.h>']
    body = []
    # scalars: globals and locals
    for i in range(10):
        t = random.choice(list(TYPES))
        v = wrap(random.randrange(-1000, 100000), t)
        g = i % 2 == 0
        name = f'{"g" if g else "l"}{i}'
        decl = f'{t} {name} = {v};'
        (out if g else body).append(decl if g else '  ' + decl)
        state[name] = v
        locs.append(Loc(name, name, t))
    # arrays
    ptrs = []
    for i, t in enumerate(['int', 'unsigned char', 'long', 'short']):
        g = i % 2 == 0
        name = f'{"ga" if g else "la"}{i}'
        n = 6
        vals = [wrap(random.randrange(-500, 500), t) for _ in range(n)]
        decl = f'{t} {name}[{n}] = {{{", ".join(map(str, vals))}}};'
        (out if g else body).append(decl if g else '  ' + decl)
        for j in range(n):
            state[(name, j)] = vals[j]
            locs.append(Loc(f'{name}[{j}]', (name, j), t))
        # pointer into the array (global and local)
        pname = f'p{i}'
        off = random.randrange(n - 2)
        pdecl = f'{t} *{pname} = &{name}[{off}];'
        if g:
            out.append(pdecl)
        else:
            body.append('  ' + pdecl)
        state[('PTR', pname)] = off
        ptrs.append((pname, name, n))
        locs.append(Loc(f'*{pname}', ('P', name, pname, 0), t))
        locs.append(Loc(f'{pname}[1]', ('P', name, pname, 1), t))
        # variable index
        locs.append(Loc(f'{name}[idx]', ('IDX', name, 0), t))
        locs.append(Loc(f'{name}[idx + 1]', ('IDX', name, 1), t))
        locs.append(Loc(f'{name}[idx - 1 + 1]', ('IDX', name, 0), t))
    # struct
    out.append('long churn(long x) { long a = x, b = 3, c = 5, d = 7; for (int i = 0; i < 4; i++) { a = a * 7 + b; b = a ^ c; c += d; d = b - a; } return x + (a - a); }')
    out.append('struct S { char c; int i; long l; unsigned short us; } gs = {1, 2, 3, 4};')
    body.append('  struct S ls = {5, 6, 7, 8};')
    body.append('  struct S *ps = &ls;')
    for nm, init in (('gs', [1, 2, 3, 4]), ('ls', [5, 6, 7, 8])):
        for (f, t), v in zip((('c', 'signed char'), ('i', 'int'), ('l', 'long'), ('us', 'unsigned short')), init):
            state[(nm, f)] = v
            locs.append(Loc(f'{nm}.{f}', (nm, f), t))
    for f, t in (('c', 'signed char'), ('i', 'int'), ('l', 'long'), ('us', 'unsigned short')):
        locs.append(Loc(f'ps->{f}', ('ls', f), t))
    body.append('  int idx = 2;')
    idx = 2

    def key_of(l):
        if isinstance(l.key, tuple) and l.key[0] == 'IDX':
            return (l.key[1], idx + l.key[2])
        if isinstance(l.key, tuple) and l.key[0] == 'P':
            return (l.key[1], state[('PTR', l.key[2])] + l.key[3])
        return l.key

    def rval():
        """A random rvalue: (code, value, type)."""
        k = random.random()
        if k < 0.25:
            t = random.choice(['int', 'unsigned', 'long'])
            v = random.randrange(0, 1000)
            return (f'{v}{"u" if t == "unsigned" else "L" if t == "long" else ""}', v, t)
        l = random.choice(locs)
        a = (l.code, state[key_of(l)], l.ty)
        if k < 0.6:
            return a
        l2 = random.choice(locs)
        b = (l2.code, state[key_of(l2)], l2.ty)
        op = random.choice(['+', '-', '&', '|', '^', '*'])
        ct = common(a[2], b[2])
        av, bv = wrap(a[1], ct), wrap(b[1], ct)
        r = {'+': av + bv, '-': av - bv, '&': av & bv, '|': av | bv, '^': av ^ bv, '*': av * bv}[op]
        return (f'({a[0]} {op} {b[0]})', wrap(r, ct), ct)

    exp = []
    for step in range(400):
        l = random.choice(locs)
        k = key_of(l)
        kind = random.random()
        if kind < 0.4:
            code, v, t = rval()
            body.append(f'  {l.code} = {code};')
            state[k] = wrap(v, l.ty)
        elif kind < 0.7:
            code, v, t = rval()
            op = random.choice(['+=', '-=', '&=', '|=', '^='])
            ct = common(l.ty, t)
            a, b = wrap(state[k], ct), wrap(v, ct)
            r = {'+=': a + b, '-=': a - b, '&=': a & b, '|=': a | b, '^=': a ^ b}[op]
            body.append(f'  {l.code} {op} {code};')
            state[k] = wrap(wrap(r, ct), l.ty)
        elif kind < 0.85:
            op = random.choice(['++', '--'])
            pre = random.random() < 0.5
            body.append(f'  {op}({l.code});' if pre else f'  ({l.code}){op};')
            state[k] = wrap(state[k] + (1 if op == '++' else -1), l.ty)
        elif kind < 0.89:
            # a nested scope with its own locals, a loop, maybe a call
            code, v, t = rval()
            code2, v2, t2 = rval()
            call = random.random() < 0.5
            tt = random.choice(['int', 'long', 'unsigned'])
            tv = wrap(v, tt)
            add = wrap(v2, tt)
            lines_ = [f'  {{ {tt} t1 = {code}; {tt} t2 = {code2};']
            for _ in range(3):
                tv = wrap(tv + add, tt)
            lines_.append('    for (int q = 0; q < 3; q++) t1 += t2;')
            if call:
                lines_.append('    t1 = churn(t1);')
            lines_.append(f'    {l.code} = t1; }}')
            body.extend(lines_)
            state[k] = wrap(tv, l.ty)
        elif kind < 0.905:
            # whole-struct copies
            dst, src = random.choice([('ls', 'gs'), ('gs', 'ls'), ('gs', '*ps')])
            body.append(f'  {dst} = {src};')
            sname = 'ls' if src == '*ps' else src
            for f in ('c', 'i', 'l', 'us'):
                state[(dst, f)] = state[(sname, f)]
            continue
        elif kind < 0.92:
            idx = random.randrange(5)
            body.append(f'  idx = {idx};')
            continue
        else:
            pname, aname, n = random.choice(ptrs)
            off = random.randrange(n - 2)
            body.append(f'  {pname} = &{aname}[{off}];' if random.random() < 0.5 else f'  {pname} = {aname} + {off};')
            state[('PTR', pname)] = off
            continue
        # print the stored value and sometimes an unrelated one
        body.append(f'  printf("%ld\\n", (long)({l.code}));')
        exp.append(str(wrap(state[key_of(l)], 'long')))
        l2 = random.choice(locs)
        body.append(f'  printf("%ld\\n", (long)({l2.code}));')
        exp.append(str(wrap(state[key_of(l2)], 'long')))
    out.append('int main(void) {')
    out.extend(body)
    out.append('  return 0;\n}')
    open(oc, 'w').write('\n'.join(out) + '\n')
    open(oe, 'w').write('\n'.join(exp) + '\n')

main()
