#include <stdio.h>
#include <stdarg.h>

struct LS { char c; long double v; };
long double g = 0.1L;
long double garr[3] = { 1, 2.5L, -3 };
struct LS gls = { 'q', 7.25L };

long double add3(long double a, int b, long double c) { return a + b + c; }
long double many(int a, double b, long double c, int d, long double e, float f, long double g2) { return a + b + c + d + e + f + g2; }
struct LS mkls(long double v) { struct LS s = { 'z', v }; return s; }
long double getv(struct LS s) { return s.v; }
long double vsum(int n, ...) {
  va_list ap; va_start(ap, n);
  long double s = 0;
  for (int i = 0; i < n; i++) { s += va_arg(ap, long double); s += va_arg(ap, int); }
  va_end(ap);
  return s;
}

int main(void) {
  long double a = 1.0L, b = 3;
  printf("%d %d\n", (int)sizeof(long double), (int)_Alignof(long double));
  printf("%.20Lf\n", a / b);
  printf("%d %d\n", (1.0L + 1e-17L) != 1.0L, 0.1L == 0.1);
  printf("%.20Lf %Lg %Lg %Lg\n", g, garr[1], garr[2], gls.v);
  unsigned long long big = 18446744073709551615ULL;
  long double lb = big;
  printf("%.0Lf %llu\n", lb, (unsigned long long)lb);
  printf("%lld %d %u\n", (long long)-2.5L, (int)(a * 7.9L), (unsigned)(lb / 8589934592.0L));
  printf("%Lg %Lg\n", add3(1.5L, 2, 0.25L), many(1, 2.0, 3.0L, 4, 5.0L, 6.0f, 7.0L));
  struct LS s = mkls(9.5L);
  printf("%c %Lg %Lg\n", s.c, s.v, getv(s));
  printf("%Lg\n", vsum(3, 1.5L, 1, 2.5L, 2, 3.0L, 3));
  long double x = 2; x++; x *= 1.5; x -= 0.5L; x /= 2;
  printf("%Lg %Lg %d %d %d\n", x, -x, x > 1, x < 1, x == 2);
  double d = (double)(a / b); float f = (float)(a / b);
  printf("%.17g %.9g\n", d, f);
  long double nan = 0.0L / 0.0L;
  printf("%d %d %d\n", nan == nan, nan != nan, !a);
  if (x) printf("truthy\n");
  long double t = (a < b) ? a : b;
  printf("%Lg %d\n", t, (int)(long double)'A');
  return 0;
}
