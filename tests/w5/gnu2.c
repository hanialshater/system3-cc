#include <stdio.h>
#include <string.h>

int fails;
#define CHECK(a, b) do { long long _a = (long long)(a), _b = (long long)(b); if (_a != _b) { printf("FAIL %d: %lld != %lld\n", __LINE__, _a, _b); fails++; } } while (0)

struct Z { int n; int d[0]; };
struct Z gz = { 2, { 7, 8 } };
struct PB { char a; int b : 4; int c : 20; } __attribute__((packed));
struct PB2 { unsigned a : 3; unsigned b : 7; unsigned c : 31; } __attribute__((packed));
struct NB { char a[3]; int b : 9; };
struct NB2 { char a; long long b : 3; };
struct S { int x, y; };

int f_unused(int a __attribute__((unused)), int b) { return b; }
static int __attribute__((noinline)) noinl(int x) { return x + 1; }
__attribute__((noreturn)) void die(void);
void die(void) { for (;;); }

int main(void) {
  int s = {};
  CHECK(s, 0);
  struct S e = {}; CHECK(e.y, 0);
  CHECK(gz.d[1], 8);
  CHECK(sizeof(struct PB), 4); CHECK(sizeof(struct PB2), 6);
  struct PB pb = { 'x', -3, 300000 }; CHECK(pb.b, -3); CHECK(pb.c, 300000);
  struct PB2 pb2 = { 5, 100, 0x7fffffff }; CHECK(pb2.a, 5); CHECK(pb2.b, 100); CHECK(pb2.c, 0x7fffffff);
  CHECK(sizeof(struct NB), 8); CHECK(sizeof(struct NB2), 8); CHECK(_Alignof(struct NB2), 8);
  CHECK(f_unused(1, 2), 2); CHECK(noinl(1), 2);
  struct S r = ({ struct S t = { 3, 4 }; t; }); CHECK(r.y, 4);
  CHECK(({ struct S t = { 5, 6 }; t; }).x, 5);
  int v = ({ int q = 10; if (q > 5) q *= 2; q; }); CHECK(v, 20);
  __typeof__(v) w = 3; CHECK(sizeof(w), 4);
  typeof(int[4]) arr4; CHECK(sizeof(arr4), 16);
  typeof(char *) cp = "hi"; CHECK(cp[1], 'i');
  int i = 0; typeof(i++) j = 5; CHECK(i, 0); CHECK(j, 5);
  CHECK(__alignof__(double), 8); CHECK(__alignof__(struct S), 4);
  char c = 'm'; int cls = 0;
  switch (c) { case 'a' ... 'l': cls = 1; break; case 'm' ... 'z': cls = 2; break; default: cls = 3; }
  CHECK(cls, 2);
  void *labs2[] = { &&L1, &&L2 };
  int k = 1;
  goto *labs2[k];
L1: CHECK(0, 1);
L2: ;
  int cnt = 0;
  int x = 0;
  int y = (x++ ?: 42); CHECK(y, 42); CHECK(x, 1);
  y = (x++ ?: 42); CHECK(y, 1); CHECK(x, 2);
  CHECK(0b1111u + 0B1, 16);
  char buf[16]; void *vp = buf; CHECK((char *)(vp + 5) - buf, 5); vp += 2; vp++; CHECK((char *)vp - buf, 3);
  CHECK(strcmp(__PRETTY_FUNCTION__, "main"), 0);
  CHECK(__builtin_types_compatible_p(typeof(x), int), 1);
  CHECK(__builtin_types_compatible_p(unsigned, int), 0);
  CHECK(__builtin_types_compatible_p(char *, typeof("a")), 0);
  CHECK(__builtin_constant_p(1 + 2), 1); CHECK(__builtin_constant_p(x), 0);
  __extension__ ({ cnt++; });
  CHECK(cnt, 1);
  int aligned_arr[4] __attribute__((aligned(16))); CHECK((long)aligned_arr % 16, 0);
  CHECK(__builtin_offsetof(struct S, y), 4);
  printf("fails=%d\n", fails);
  return fails;
}
