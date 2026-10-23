#include <stdio.h>

int fails;
#define CHECK(a, b) do { unsigned long long _a = (unsigned long long)(a), _b = (unsigned long long)(b); if (_a != _b) { printf("FAIL %d: %llx != %llx\n", __LINE__, _a, _b); fails++; } } while (0)

typedef unsigned char u8; typedef signed char s8; typedef unsigned short u16; typedef short s16;
typedef unsigned int u32; typedef int s32; typedef unsigned long u64; typedef long long s64;

u8 id8(u8 x) { return x; }
s8 ids8(s8 x) { return x; }
u16 id16(u16 x) { return x; }
s16 ids16(s16 x) { return x; }
u32 id32(u32 x) { return x; }
u64 id64(u64 x) { return x; }
_Bool tobool(long x) { return x; }
double u2d(u64 x) { return x; }
u64 d2u(double d) { return d; }
float u2f(u64 x) { return x; }
u32 d2u32(double d) { return d; }

int main(void) {
  s32 m1 = -1; u32 um = m1; u64 big = 0xffffffffffffffffUL;
  CHECK(um, 0xffffffffU);
  CHECK(m1 < 1u, 0);              /* -1 converted to unsigned */
  CHECK(m1 < 1L, 1);              /* long holds all int */
  CHECK((u32)m1 > 5, 1);
  CHECK(-1L < 1u, 1);             /* long holds all unsigned int */
  CHECK(-1LL < 1ULL, 0);
  CHECK(um + 1, 0);
  CHECK(big + 1, 0);
  u8 c = 250; c += 10; CHECK(c, 4);
  s8 sc = 127; sc++; CHECK(sc, (unsigned long long)-128LL);
  u16 us = 65535; us++; CHECK(us, 0);
  s16 ss = -32768; ss--; CHECK(ss, 32767);
  CHECK(id8(300), 44); CHECK(ids8(200), (unsigned long long)-56LL); CHECK(id16(70000), 4464); CHECK(ids16(40000), (unsigned long long)-25536LL);
  CHECK(id32(-1), 0xffffffffU); CHECK(id64(-1), 0xffffffffffffffffULL);
  u8 a8 = 200, b8 = 100; CHECK(a8 + b8, 300);      /* promoted to int */
  CHECK((u8)(a8 + b8), 44);
  s8 n8 = -1; u8 p8 = n8; CHECK(p8, 255); CHECK((int)n8, (unsigned long long)-1LL); CHECK((unsigned)n8, 0xffffffffU);
  CHECK((u64)n8, 0xffffffffffffffffULL); CHECK((u64)p8, 255); CHECK((u64)(u32)n8, 0xffffffffULL);
  CHECK((s64)(s32)0x80000000U, 0xffffffff80000000ULL);
  CHECK((u32)0x123456789ULL, 0x23456789);
  CHECK((s16)0x18765, (unsigned long long)(s64)(s16)0x8765);
  CHECK(tobool(0), 0); CHECK(tobool(256), 1); CHECK(tobool(1L << 40), 1); _Bool bb = 0.5; CHECK(bb, 1);
  /* bitwise */
  u32 x = 0xF0F0F0F0u; s32 sx = -16;
  CHECK(x & 0xFF, 0xF0); CHECK(x | 0xF, 0xF0F0F0FF); CHECK(x ^ 0xFFFFFFFF, 0x0F0F0F0F); CHECK(~x, 0x0F0F0F0F);
  CHECK(x >> 4, 0x0F0F0F0F); CHECK(sx >> 2, (unsigned long long)-4LL); CHECK((u32)sx >> 28, 15);
  CHECK(1 << 31, 0xffffffff80000000ULL); CHECK(1u << 31, 0x80000000U); CHECK(1ULL << 63, 0x8000000000000000ULL);
  int sh = 33; CHECK(1LL << sh, 0x200000000ULL); CHECK(big >> 60, 15); s64 nb = -1; CHECK(nb >> 63, 0xffffffffffffffffULL);
  u32 y = 1; y <<= 4; y |= 3; y &= ~1u; y ^= 0x100; y >>= 1; CHECK(y, 0x89);
  s64 z = 0x1234; z <<= 40; CHECK(z >> 40, 0x1234);
  char ch = 0x80; CHECK(ch >> 7, (unsigned long long)-1LL); CHECK((u8)ch >> 7, 1);
  CHECK(~(u8)0, 0xffffffffffffffffULL); /* ~ on promoted int */
  CHECK((u8)~(u8)0, 255);
  /* division and modulo */
  CHECK(-7 / 2, (unsigned long long)-3LL); CHECK(-7 % 2, (unsigned long long)-1LL); CHECK(7u / 2, 3);
  u32 ud = 0xFFFFFFFE; CHECK(ud / 2, 0x7FFFFFFF); CHECK(ud % 7, 0xFFFFFFFEu % 7);
  u64 bd = 0xFFFFFFFFFFFFFFFEULL; CHECK(bd / 3, 0x5555555555555554ULL); CHECK(bd % 10, 4);
  s64 sd = -9; CHECK(sd / 4, (unsigned long long)-2LL); CHECK(sd % 4, (unsigned long long)-1LL);
  CHECK((-8) / (u32)2, 0x7FFFFFFC);
  /* literals */
  CHECK(sizeof(2147483647), 4); CHECK(sizeof(2147483648), 8); CHECK(sizeof(0x80000000), 4); CHECK(sizeof(4294967296), 8);
  CHECK(sizeof(1u), 4); CHECK(sizeof(1l), 8); CHECK(sizeof(1ll), 8); CHECK(sizeof(1ul), 8); CHECK(sizeof('a'), 4);
  CHECK(0x80000000 > 0, 1); CHECK(-2147483648 < 0, 1); CHECK(0xFFFFFFFFFFFFFFFF, big);
  CHECK(sizeof(short) + sizeof(long long) + sizeof(_Bool) + sizeof(signed char), 2 + 8 + 1 + 1);
  long long int lli = 5; unsigned long long int ulli = 6; short int si = 7; unsigned short int usi = 8; signed sgn = -9; unsigned unsig = 10;
  CHECK(lli + ulli + si + usi + sgn + unsig, 27);
  /* conversions with floats at runtime */
  CHECK(u2d(0x8000000000000001ULL) == 9223372036854775808.0, 1);
  CHECK(d2u(1e19), 10000000000000000000ULL); CHECK(d2u(3.7), 3);
  CHECK(u2f(0xFFFFFFFFFFFFFFFFULL) == 18446744073709551616.0f, 1);
  CHECK(d2u32(4000000000.0), 4000000000U);
  /* mixed comparisons */
  u16 w = 65535; CHECK(w > -1, 1);          /* u16 promotes to int */
  u32 w32 = 1; CHECK(w32 > -1, 0);
  CHECK((s8)-1 == (u8)255, 0);
  CHECK(sizeof(u8) < -1, 1);  /* sizeof is unsigned long: -1 -> huge */
  printf("fails=%d\n", fails);
  return fails;
}
