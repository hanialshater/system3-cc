#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int fails;
#define CHECK(a, b) do { long long _a = (long long)(a), _b = (long long)(b); if (_a != _b) { printf("FAIL %d: %lld != %lld\n", __LINE__, _a, _b); fails++; } } while (0)

struct B1 { unsigned a : 3; signed b : 4; unsigned c : 25; unsigned d : 1; };
struct B2 { char c; int x : 12; int y : 12; char z; };
struct B3 { unsigned long long big : 40; unsigned long long rest : 24; };
struct B4 { int a : 1; unsigned b : 1; _Bool c : 1; };
struct B5 { short s : 9; char ch; int : 0; int after : 5; };
struct B1 gb = { 5, -3, 1000, 1 };
struct B2 gb2 = { 'x', -100, 2047, 'z' };

struct B1 passb(struct B1 v) { v.a++; return v; }

static int counter(void) { static int n = 0; return ++n; }
static int file_static = 42;
extern int ext_var;
int ext_var = 7;
extern int later_fn(int);

int add(int a, int b) { return a + b; }
int sub(int a, int b) { return a - b; }
int apply(int (*f)(int, int), int a, int b) { return f(a, b); }
int (*pick(int which))(int, int) { return which ? add : sub; }
struct ops { const char *name; int (*fn)(int, int); } optab[] = { {"add", add}, {"sub", sub} };
int cmpint(const void *a, const void *b) { return *(const int *)a - *(const int *)b; }

struct P { int x, y; };
int sump(struct P *p) { return p->x + p->y; }

int classify(int v) {
  switch (v) {
    case 0: return 100;
    case 1:
    case 2: v += 10;
    case 3: return v;
    case -5: return -500;
    case 1000000: return 7;
    default: break;
  }
  return -1;
}

int main(void) {
  CHECK(sizeof(struct B1), 8); CHECK(sizeof(struct B2), 8); CHECK(sizeof(struct B3), 8); CHECK(sizeof(struct B4), 4);
  CHECK(sizeof(struct B5), 8);
  CHECK(gb.a, 5); CHECK(gb.b, -3); CHECK(gb.c, 1000); CHECK(gb.d, 1);
  CHECK(gb2.x, -100); CHECK(gb2.y, 2047); CHECK(gb2.z, 'z');
  struct B1 l = { 7, 7, 0, 0 };
  l.a++; CHECK(l.a, 0);
  l.b++; CHECK(l.b, -8);
  CHECK((l.c = 0x3ffffff), 0x1ffffff);
  l.d = 3; CHECK(l.d, 1);
  l.c += 5; CHECK(l.c, 4);
  l.a = 6; l.a |= 1; l.a <<= 1; CHECK(l.a, 6);
  CHECK(l.b * 2, -16);
  struct B3 b3 = { 0xFFFFFFFFFFULL, 5 };
  b3.big += 1; CHECK(b3.big, 0); CHECK(b3.rest, 5);
  b3.big = 0x123456789AULL; CHECK(b3.big >> 32, 0x12);
  struct B4 b4 = { -1, 1, 1 }; CHECK(b4.a, -1); CHECK(b4.b, 1); CHECK(b4.c, 1);
  struct B5 b5 = { -200, 'q', 17 }; CHECK(b5.s, -200); CHECK(b5.ch, 'q'); CHECK(b5.after, -15);
  struct B1 pb = passb(gb); CHECK(pb.a, 6); CHECK(pb.b, -3); CHECK(pb.c, 1000);
  /* compound literals */
  int *ip = (int[]){1, 2, 3}; CHECK(ip[2], 3);
  CHECK(sump(&(struct P){3, 4}), 7);
  struct P cp = (struct P){.y = 9}; CHECK(cp.y, 9); CHECK(cp.x, 0);
  CHECK(((struct P){5, 6}).y, 6);
  for (int i = 0; i < 3; i++) { int *q = (int[2]){i, i * 2}; CHECK(q[1], i * 2); }
  /* storage */
  counter(); counter(); CHECK(counter(), 3);
  CHECK(file_static, 42); CHECK(ext_var, 7); CHECK(later_fn(2), 20);
  { extern int ext_var; CHECK(ext_var, 7); }
  { static int s; s++; CHECK(s, 1); }
  /* function pointers */
  CHECK(apply(add, 2, 3), 5); CHECK(apply(sub, 2, 3), -1); CHECK(pick(1)(4, 4), 8); CHECK((*pick(0))(4, 4), 0);
  int (*fp)(int, int) = add; CHECK(fp == add, 1); CHECK(fp != sub, 1); fp = &sub; CHECK((*fp)(9, 1), 8);
  CHECK(optab[1].fn(10, 3), 7); CHECK(strcmp(optab[0].name, "add"), 0);
  int (*arr[2])(int, int) = {sub, add}; CHECK(arr[1](1, 1), 2);
  int vals[] = {5, 1, 4, 2, 3}; qsort(vals, 5, sizeof(int), cmpint); CHECK(vals[0] * 10000 + vals[1] * 1000 + vals[2] * 100 + vals[3] * 10 + vals[4], 12345);
  /* switch / goto */
  CHECK(classify(0), 100); CHECK(classify(1), 11); CHECK(classify(2), 12); CHECK(classify(3), 3); CHECK(classify(-5), -500);
  CHECK(classify(1000000), 7); CHECK(classify(9), -1);
  int tot = 0;
  for (int i = 0; i < 10; i++) {
    switch (i % 3) { case 0: continue; case 1: tot += 1; break; default: tot += 100; }
    if (i == 8) break;
  }
  CHECK(tot, 1 + 100 + 1 + 100 + 1 + 100);
  long lv = 5000000000L; switch (lv) { case 5000000000L: tot = 1; break; default: tot = 2; }
  CHECK(tot, 1);
  int k = 0;
 top:
  k++;
  if (k < 5) goto top;
  goto skip;
  k = 100;
 skip:
  CHECK(k, 5);
  char ch = 'b'; switch (ch) { case 'a': k = 1; break; case 'b': k = 2; break; }
  CHECK(k, 2);
  unsigned char uc = 200; switch (uc) { case 200: k = 3; break; case -56: k = 4; break; }
  CHECK(k, 3);
  printf("%s %s\n", __func__, __func__);
  _Static_assert(sizeof(int) == 4, "int");
  volatile int vol = 3; int * restrict rp = &vol; CHECK(*rp, 3);
  printf("fails=%d\n", fails);
  return fails;
}

int later_fn(int x) { return x * 10; }
