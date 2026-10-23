#!/usr/bin/env python3
"""Random programs with many small single-expression functions (inlining
candidates), recursion, nested calls and small structs.
Usage: gen_calltest.py <seed> <out.c>
The expected output is produced by compiling with optimizations disabled."""
import random, sys

TYPES = ['int', 'unsigned', 'long', 'unsigned long', 'char', 'short', 'unsigned char', 'unsigned short']

def main():
    seed, oc = int(sys.argv[1]), sys.argv[2]
    random.seed(seed)
    out = ['#include <stdio.h>',
           'unsigned garr[16] = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5, 8, 9, 7, 9, 3};',
           'unsigned gv = 7;',
           'struct P { int x; unsigned char c; long l; short s; };',
           'struct P parr[4] = {{1, 2, 3, 4}, {-5, 6, -7, 8}, {9, 10, 11, -12}, {13, 14, 15, 16}};',
           'unsigned long gside = 0;',
           'static unsigned side(unsigned x) { gside += x; return x ^ 85; }']
    funcs = []  # (name, ret, [param types])
    cost = {}
    budget = [0]

    def expr(params, depth, allow_calls=True):
        """An unsigned-safe expression (computed in unsigned long)."""
        k = random.random()
        if depth <= 0 or k < 0.2:
            c = random.random()
            if params and c < 0.6:
                p, t = random.choice(params)
                return f'(unsigned long)({p})'
            if c < 0.75:
                return f'{random.randrange(0, 50)}u'
            if c < 0.85:
                return 'gv'
            return f'garr[{random.randrange(16)}]'
        if k < 0.45:
            op = random.choice(['+', '-', '*', '&', '|', '^'])
            return f'({expr(params, depth - 1, allow_calls)} {op} {expr(params, depth - 1, allow_calls)})'
        if k < 0.55:
            return f'({expr(params, depth - 1, allow_calls)} >> ({expr(params, depth - 1, allow_calls)} & 7))'
        if k < 0.62:
            return f'({expr(params, depth - 1, allow_calls)} / ({expr(params, depth - 1, allow_calls)} | 1))'
        if k < 0.72:
            cmp = random.choice(['<', '>', '==', '!=', '<=', '>='])
            return (f'({expr(params, depth - 1, allow_calls)} {cmp} {expr(params, depth - 1, allow_calls)} ? '
                    f'{expr(params, depth - 1, allow_calls)} : {expr(params, depth - 1, allow_calls)})')
        if k < 0.78 and params:
            # modify a parameter inside the expression
            p, t = random.choice(params)
            if t == 'ro' or t.startswith('struct') or t.endswith('*'):
                return expr(params, depth - 1, allow_calls)
            op = random.choice(['+=', '-=', '^=', '++', '--'])
            if op in ('++', '--'):
                return f'({op}{p}, {expr(params, depth - 1, allow_calls)})'
            return f'({p} {op} {random.randrange(1, 9)}, {expr(params, depth - 1, allow_calls)})'
        if k < 0.82:
            return f'(unsigned long)side({expr(params, depth - 1, allow_calls)})'
        if k < 0.86:
            return f'garr[{expr(params, depth - 1, allow_calls)} & 15]'
        cheap = [f for f in funcs if cost[f[0]] < 200]
        if allow_calls and cheap and budget[0] < 2000:
            name, ret, pts = random.choice(cheap)
            budget[0] += cost[name]
            args = ', '.join(arg_for(t, params, depth - 1) for t in pts)
            return f'(unsigned long){name}({args})'
        return expr(params, depth - 1, allow_calls)

    def arg_for(t, params, depth):
        if t == 'unsigned *':
            return f'&garr[{random.randrange(8)}]'
        if t == 'struct P *':
            return f'&parr[{random.randrange(4)}]'
        return f'({t})({expr(params, depth)})'

    nf = random.randrange(6, 12)
    for i in range(nf):
        n = random.randrange(1, 4)
        pts = [random.choice(TYPES + ['unsigned *', 'struct P *']) for _ in range(n)]
        params = [(f'a{j}', t) for j, t in enumerate(pts)]
        # pointer params are used through dereferences
        uses = []
        for p, t in params:
            if t == 'unsigned *':
                uses.append((f'{p}[{random.randrange(4)}]', 'ro'))
            elif t == 'struct P *':
                uses.append((f'{p}->{random.choice("xcls")}', 'ro'))
            else:
                uses.append((p, t))
        ret = random.choice(TYPES)
        name = f'f{i}'
        budget[0] = 1
        body = expr(uses, random.randrange(1, 4))
        cost[name] = budget[0]
        st = 'static ' if random.random() < 0.7 else ''
        decl = ', '.join(f'{t} {p}' for p, t in params)
        out.append(f'{st}{ret} {name}({decl}) {{ return {body}; }}')
        funcs.append((name, ret, pts))
    # recursive functions
    out.append('static unsigned rfib(unsigned n) { return n < 2 ? n : rfib(n - 1) + rfib(n - 2); }')
    out.append('static long racc(int n, long acc) { return n <= 0 ? acc : racc(n - 1, acc * 3 + n); }')
    out.append('static int rtak(int x, int y, int z) { return y < x ? rtak(rtak(x - 1, y, z), rtak(y - 1, z, x), rtak(z - 1, x, y)) : z; }')
    out.append('static unsigned mix(unsigned a, unsigned b) { return (a ^ (b << 3)) + (a >> 2); }')
    out.append('static unsigned two(unsigned a) { return mix(mix(a, a + 1), mix(a + 2, rfib(a & 7))); }')
    # main
    out.append('int main(void) {')
    out.append('  unsigned long acc = 0;')
    out.append('  struct P q = parr[1];')
    for i in range(random.randrange(10, 25)):
        name, ret, pts = random.choice(funcs)
        budget[0] = 0
        c = random.random()
        if c < 0.5:
            args = ', '.join(arg_for(t, [('acc', 'ro'), ('q.x', 'ro')], 2) for t in pts)
            out.append(f'  acc = acc * 31 + (unsigned long){name}({args});')
        elif c < 0.7:
            out.append(f'  for (int i = 0; i < {random.randrange(2, 6)}; i++) {{')
            args = ', '.join(arg_for(t, [('i', 'ro'), ('acc', 'ro')], 2) for t in pts)
            out.append(f'    acc += (unsigned long){name}({args}) ^ i;')
            out.append('  }')
        elif c < 0.8:
            out.append(f'  q = parr[acc & 3];')
            out.append(f'  q.x += (int)(acc & 1023); q.c ^= (unsigned char)acc;')
            out.append(f'  acc += (unsigned long)q.x + q.c + (unsigned long)q.l + (unsigned long)q.s;')
        else:
            r = random.choice(['rfib(acc % 12)', 'racc((int)(acc % 9), (long)(acc & 255))',
                               'rtak(9, 6, (int)(acc % 4))', 'two((unsigned)acc)'])
            out.append(f'  acc = acc * 7 + (unsigned long){r};')
        out.append('  printf("%lu %u %lu\\n", acc, gv, gside);')
    out.append('  return 0;\n}')
    open(oc, 'w').write('\n'.join(out) + '\n')

main()
