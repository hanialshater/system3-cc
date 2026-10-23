#!/usr/bin/env python3
"""Generate a random C integer-semantics test plus its expected output.
Usage: gen_inttest.py <seed> <out.c> <out.expected>"""
import random, sys

TYPES = {  # name: (bits, signed)
    'signed char': (8, True), 'unsigned char': (8, False),
    'short': (16, True), 'unsigned short': (16, False),
    'int': (32, True), 'unsigned': (32, False),
    'long': (64, True), 'unsigned long': (64, False),
}
RANK = {8: 1, 16: 2, 32: 3, 64: 4}

def wrap(v, t):
    bits, s = TYPES[t]
    v &= (1 << bits) - 1
    if s and v >= 1 << (bits - 1):
        v -= 1 << bits
    return v

def promote(t):
    bits, s = TYPES[t]
    return 'int' if bits < 32 else t

def common(a, b):
    a, b = promote(a), promote(b)
    (ba, sa), (bb, sb) = TYPES[a], TYPES[b]
    if sa == sb:
        return a if ba >= bb else b
    u, s_ = (a, b) if not sa else (b, a)
    if TYPES[u][0] >= TYPES[s_][0]:
        return u
    return s_  # signed wider type holds all unsigned values

def tdiv(a, b):
    q = abs(a) // abs(b)
    return q if (a >= 0) == (b >= 0) else -q

class E:
    def __init__(self, code, val, ty):
        self.code, self.val, self.ty = code, val, ty

def gen(depth, vars_):
    if depth == 0 or random.random() < 0.25:
        if random.random() < 0.7:
            name, ty, val = random.choice(vars_)
            return E(name, val, ty)
        ty = random.choice(['int', 'unsigned', 'long', 'unsigned long'])
        bits = TYPES[ty][0]
        v = random.choice([0, 1, 2, 3, 7, 31, 255, 256, 65535, random.randrange(1 << (bits - 1))])
        suffix = {'int': '', 'unsigned': 'u', 'long': 'L', 'unsigned long': 'UL'}[ty]
        return E(f'{v}{suffix}', v, ty)
    k = random.random()
    if k < 0.12:
        e = gen(depth - 1, vars_)
        op = random.choice(['~', '-', '!'])
        pt = promote(e.ty)
        if op == '~':
            return E(f'(~{e.code})', wrap(~e.val, pt), pt)
        if op == '-':
            return E(f'(-{e.code})', wrap(-e.val, pt), pt)
        return E(f'(!{e.code})', int(e.val == 0), 'int')
    if k < 0.22:
        e = gen(depth - 1, vars_)
        t = random.choice(list(TYPES))
        return E(f'(({t}){e.code})', wrap(e.val, t), t)
    a, b = gen(depth - 1, vars_), gen(depth - 1, vars_)
    op = random.choice(['+', '-', '*', '/', '%', '&', '|', '^', '<<', '>>', '<', '>', '==', '!=', '<=', '>='])
    if op in ('<<', '>>'):
        pt = promote(a.ty)
        bits = TYPES[pt][0]
        n = b.val % bits
        if n < 0:
            n += bits
        bcode = f'({b.code} & {bits - 1})' if TYPES[promote(b.ty)][0] else ''
        # make the shift count well defined: (b & (bits-1)) computed in C too
        nb = common(b.ty, 'int')
        nval = wrap(b.val, promote(b.ty)) & (bits - 1)
        av = wrap(a.val, pt)
        if op == '<<':
            if TYPES[pt][1] and av < 0:
                av = av & ((1 << bits) - 1)  # shift the bit pattern (gcc wraps)
            r = wrap(av << nval, pt)
        else:
            r = av >> nval if TYPES[pt][1] else (av & ((1 << bits) - 1)) >> nval
            r = wrap(r, pt)
        return E(f'({a.code} {op} ({b.code} & {bits - 1}))', r, pt)
    ct = common(a.ty, b.ty)
    av, bv = wrap(a.val, ct), wrap(b.val, ct)
    if op in ('/', '%'):
        if bv == 0:
            return E(f'({a.code} + {b.code})', wrap(av + bv, ct), ct)
        if TYPES[ct][1] and av == -(1 << (TYPES[ct][0] - 1)) and bv == -1:
            return E(f'({a.code} - {b.code})', wrap(av - bv, ct), ct)
        q = tdiv(av, bv)
        r = q if op == '/' else av - q * bv
        return E(f'({a.code} {op} {b.code})', wrap(r, ct), ct)
    if op in ('<', '>', '==', '!=', '<=', '>='):
        r = {'<': av < bv, '>': av > bv, '==': av == bv, '!=': av != bv, '<=': av <= bv, '>=': av >= bv}[op]
        return E(f'({a.code} {op} {b.code})', int(r), 'int')
    r = {'+': av + bv, '-': av - bv, '*': av * bv, '&': av & bv, '|': av | bv, '^': av ^ bv}[op]
    return E(f'({a.code} {op} {b.code})', wrap(r, ct), ct)

def main():
    seed, out_c, out_e = int(sys.argv[1]), sys.argv[2], sys.argv[3]
    random.seed(seed)
    vars_ = []
    lines = ['#include <stdio.h>', 'int main(void) {']
    for i, t in enumerate(TYPES):
        for j in range(2):
            bits, s = TYPES[t]
            v = wrap(random.choice([0, 1, -1, random.randrange(1 << bits), (1 << (bits - 1)) - 1, 1 << (bits - 1)]), t)
            name = f'v{i}_{j}'
            lines.append(f'  {t} {name} = {v}{"L" if bits == 64 else ""};' if s else f'  {t} {name} = {v}{"UL" if bits == 64 else "u"};')
            vars_.append((name, t, v))
    exp = []
    for k in range(400):
        e = gen(4, vars_)
        res = wrap(e.val, 'long')
        lines.append(f'  printf("%ld\\n", (long)({e.code}));')
        exp.append(str(res))
        if k % 5 == 0:
            name, t, _ = random.choice(vars_)
            nv = wrap(e.val, t)
            lines.append(f'  {name} = {e.code};')
            vars_ = [(n, tt, (nv if n == name else vv)) for (n, tt, vv) in vars_]
            lines.append(f'  printf("%ld\\n", (long){name});')
            exp.append(str(wrap(nv, 'long')))
    lines.append('  return 0;\n}')
    open(out_c, 'w').write('\n'.join(lines) + '\n')
    open(out_e, 'w').write('\n'.join(exp) + '\n')

main()
