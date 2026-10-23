#include <stdio.h>
#include <stddef.h>

int fails;
#define CHECK(a, b) do { long long _a = (long long)(a), _b = (long long)(b); if (_a != _b) { printf("FAIL %d: %lld != %lld\n", __LINE__, _a, _b); fails++; } } while (0)

typedef int aint __attribute__((aligned(16)));
struct A { char c; aint x; };
struct P { char c; int i; } __attribute__((packed));
struct __attribute__((packed)) P2 { char c; int i; short s; };
struct Q { char c; int i __attribute__((packed)); };
struct R { char c; int i __attribute__((aligned(8))); };
struct S { char c; } __attribute__((aligned(32)));
struct T { char c; double d; } __attribute__((packed, aligned(4)));
int gal __attribute__((aligned(64))) = 5;

#define MAX(a, b) ({ __typeof__(a) _x = (a); __typeof__(b) _y = (b); _x > _y ? _x : _y; })
#define LOG(fmt, args...) sprintf(buf, fmt, ## args)
#define LOG2(fmt, ...) sprintf(buf, fmt, ## __VA_ARGS__)

int with_local_labels(int n) {
  int r = 0;
#define LOOPN(k) ({ __label__ again; int _i = 0; again: _i++; if (_i < (k)) goto again; _i; })
  r += LOOPN(n);
  r += LOOPN(n + 1);
  return r;
}

int computed(int k) {
  static void *tab[] = { &&l0, &&l1, &&l2 };
  goto *tab[k];
l0: return 100;
l1: return 101;
l2: return 102;
}

int main(void) {
  char buf[64];
  CHECK(sizeof(struct A), 32); CHECK(offsetof(struct A, x), 16);
  CHECK(sizeof(struct P), 5); CHECK(_Alignof(struct P), 1);
  CHECK(sizeof(struct P2), 7);
  CHECK(sizeof(struct Q), 5); CHECK(offsetof(struct Q, i), 1);
  CHECK(sizeof(struct R), 16); CHECK(offsetof(struct R, i), 8);
  CHECK(sizeof(struct S), 32); CHECK(__alignof__(struct S), 32);
  CHECK(sizeof(struct T), 12); CHECK(_Alignof(struct T), 4);
  CHECK((long)&gal % 64, 0);
  int loc __attribute__((aligned(32))) = 1; CHECK((long)&loc % 32, 0);
  CHECK(MAX(3, 7), 7); CHECK(MAX(-1, -5), -1);
  LOG("%d-%d", 1, 2); CHECK(buf[2], '2');
  LOG("x"); CHECK(buf[0], 'x');
  LOG2("y"); CHECK(buf[0], 'y'); LOG2("%d", 9); CHECK(buf[0], '9');
  CHECK(with_local_labels(3), 7);
  CHECK(computed(0), 100); CHECK(computed(2), 102);
  CHECK(__COUNTER__, 0); CHECK(__COUNTER__, 1);
  int x = 5; int y = x ?: 9; CHECK(y, 5); int z = 0 ?: 9; CHECK(z, 9);
  switch (x) { case 1 ... 4: CHECK(0, 1); break; case 5 ... 10: y = 50; break; default: break; }
  CHECK(y, 50);
  int za[0]; CHECK(sizeof(za), 0);
  struct E { int n; int d[0]; }; CHECK(sizeof(struct E), 4);
  CHECK(0b1011, 11);
  void *vp = buf; vp = vp + 3; CHECK((char *)vp - buf, 3); CHECK(sizeof(void), 1);
  CHECK(__builtin_popcount(0xff), 8); CHECK(__builtin_popcountll(0xffffffffffULL), 40);
  CHECK(__builtin_clz(1), 31); CHECK(__builtin_clzll(1), 63); CHECK(__builtin_ctz(8), 3); CHECK(__builtin_ctzl(1UL << 40), 40);
  CHECK(__builtin_ffs(0), 0); CHECK(__builtin_ffs(12), 3); CHECK(__builtin_ffsll(1LL << 50), 51);
  CHECK(__builtin_parity(7), 1); CHECK(__builtin_parity(3), 0);
  CHECK(__builtin_bswap16(0x1234), 0x3412); CHECK(__builtin_bswap32(0x12345678), 0x78563412);
  CHECK(__builtin_bswap64(0x0102030405060708ULL) == 0x0807060504030201ULL, 1);
  int r; CHECK(__builtin_add_overflow(2147483647, 1, &r), 1); CHECK(r, -2147483647 - 1);
  CHECK(__builtin_add_overflow(1, 2, &r), 0); CHECK(r, 3);
  unsigned u; CHECK(__builtin_sub_overflow(1, 2, &u), 1); CHECK(u, 4294967295U);
  long long ll; CHECK(__builtin_mul_overflow(1LL << 40, 1LL << 30, &ll), 1);
  CHECK(__builtin_mul_overflow(-3, 5, &ll), 0); CHECK(ll, -15);
  unsigned char uc; CHECK(__builtin_mul_overflow(16, 16, &uc), 1); CHECK(uc, 0);
  CHECK(__builtin_smul_overflow(65536, 65536, &r), 1); CHECK(__builtin_uaddl_overflow(~0UL, 1UL, (unsigned long *)&ll), 1);
  unsigned long long ull; CHECK(__builtin_mul_overflow(-1, 1, &ull), 1); CHECK(__builtin_add_overflow(-1, 2, &ull), 0); CHECK(ull, 1);
  CHECK(__builtin_choose_expr(1, 10, 20), 10); CHECK(__builtin_choose_expr(0, 10, 20), 20);
  CHECK(__builtin_types_compatible_p(int, int), 1); CHECK(__builtin_types_compatible_p(int, long), 0);
  CHECK(__builtin_constant_p(3), 1); CHECK(__builtin_expect(x, 5), 5);
  char *al = __builtin_alloca(100); al[99] = 1; CHECK(al[99], 1);
  CHECK(__builtin_inf() > 1e308, 1); CHECK(__builtin_huge_valf() > 1e38f, 1);
  CHECK(__builtin_strlen("abc"), 3); CHECK(__builtin_abs(-4), 4);
  __builtin_prefetch(buf);
  CHECK(__builtin_offsetof(struct R, i), 8);
  __extension__ long long ext = 1; CHECK(ext, 1);
  printf("%s %s\n", __func__, __FUNCTION__);
  printf("fails=%d\n", fails);
  return fails;
}
