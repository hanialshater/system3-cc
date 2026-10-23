#include <stdio.h>
#include <stdarg.h>

struct F2 { float a, b; };
struct F3 { float a, b, c; };
struct D2 { double a, b; };
struct FI { float f; int i; };
struct ID { int i; double d; };
struct DL { double d; long l; };
struct BIG { double a, b, c; };

struct F2 mkf2(float a, float b) { struct F2 r = {a, b}; return r; }
struct F3 mkf3(float a) { struct F3 r = {a, a * 2, a * 3}; return r; }
struct D2 mkd2(double a) { struct D2 r = {a, -a}; return r; }
struct FI mkfi(float f, int i) { struct FI r = {f, i}; return r; }
struct ID mkid(int i, double d) { struct ID r = {i, d}; return r; }
struct DL mkdl(double d, long l) { struct DL r = {d, l}; return r; }
struct BIG mkbig(double x) { struct BIG r = {x, x + 1, x + 2}; return r; }
double sumf3(struct F3 s) { return s.a + s.b + s.c; }
double sumid(struct ID s, struct DL t) { return s.i + s.d + t.d + t.l; }
double sumbig(struct BIG b) { return b.a + b.b + b.c; }

double many(double a, double b, double c, double d, double e, double f, double g, double h,
            double i, double j, float k, int n1, int n2) {
  return a + b + c + d + e + f + g + h + i + j + k + n1 + n2;
}
float fmix(int a, float b, long c, double d, char e, float f) { return a + b + c + d + e + f; }

double vsum(int n, ...) {
  va_list ap; va_start(ap, n);
  double s = 0;
  for (int i = 0; i < n; i++) s += va_arg(ap, double);
  va_end(ap);
  return s;
}
double vmixed(int n, ...) {
  va_list ap; va_start(ap, n);
  double s = 0;
  for (int i = 0; i < n; i++) { s += va_arg(ap, int); s += va_arg(ap, double); }
  va_end(ap);
  return s;
}
double knr(x, y) float x; double y; { return x * y; }

float gf = 1.5f, garr[3] = {1, 2.5, -3};
double gd = 1e100, gneg = -0.0;
float halfint = 3 / 2;

int main(void) {
  double a = 1.5, b = 2.25;
  float f = 0.1f;
  printf("%.4f %.4f %.4f %.4f\n", a + b, a - b, a * b, a / b);
  printf("%.9g %.17g %g\n", f, (double)f, f * 3);
  printf("%d %d %d %d\n", a < b, a > b, a == 1.5, a != 1.5);
  double nan = 0.0 / 0.0;
  printf("nan: %d %d %d %d %d %d\n", nan == nan, nan != nan, nan < 1, nan >= 1, !nan, nan ? 1 : 0);
  printf("%d %d\n", (int)3.99, (int)-3.99);
  unsigned long big = 18446744073709551615UL;
  double dbig = big;
  unsigned long back = 9223372036854775808.0 + 1024.0;
  printf("%.1f %lu %lu\n", dbig, back, (unsigned long)1e19);
  printf("%.1f %.1f\n", (double)(unsigned)4294967295U, (float)-1L);
  long long ll = -9007199254740993LL;
  printf("%.1f %lld\n", (double)ll, (long long)(double)ll);
  float fr = 16777217;
  printf("%.1f %.1f\n", fr, (double)(float)0.1);
  short sh = (short)-2.5; unsigned char uc = (unsigned char)200.7;
  printf("%d %d\n", sh, uc);
  printf("%.2f\n", many(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0.5f, 100, 1000));
  printf("%.2f\n", fmix(1, 2.5f, 3L, 4.25, 'A', 0.25f));
  printf("%.2f %.2f %.2f\n", vsum(3, 1.0, 2.0, 3.5), vsum(10, 1.,2.,3.,4.,5.,6.,7.,8.,9.,10.), vmixed(5, 1, 0.5, 2, 0.5, 3, 0.5, 4, 0.5, 5, 0.5));
  printf("%.3f\n", knr(1.5f, 2.0));
  struct F2 s2 = mkf2(1.25f, -2.5f);
  struct F3 s3 = mkf3(1.5f);
  struct D2 d2 = mkd2(3.75);
  struct FI fi = mkfi(2.5f, 7);
  struct ID id = mkid(4, 0.125);
  struct DL dl = mkdl(1.5, 10);
  struct BIG bg = mkbig(0.5);
  printf("%.2f %.2f | %.2f %.2f %.2f | %.2f %.2f | %.2f %d | %d %.3f | %.2f %ld\n",
         s2.a, s2.b, s3.a, s3.b, s3.c, d2.a, d2.b, fi.f, fi.i, id.i, id.d, dl.d, dl.l);
  printf("%.2f %.3f %.2f %.2f\n", sumf3(s3), sumid(id, dl), sumbig(bg), bg.c);
  printf("%.2f %.2f %.2f %.2f %g %g %.1f\n", gf, garr[1], garr[2], garr[0], gd, gneg, halfint);
  double x = 1; x += 0.5; x *= 4; x -= 1; x /= 2;
  int i = 10; i *= 2.5; i += 0.9;
  float g = 2; g++; ++g; g--;
  double y = 5; double old = y++;
  printf("%.2f %d %.2f %.2f %.2f\n", x, i, g, old, y);
  printf("%.2f %.2f\n", -a, 1 ? 2 : 3.5);
  int cnt = 0; for (double t = 0; t < 1; t += 0.25) cnt++;
  printf("%d\n", cnt);
  printf("%f %e %g %a\n", 1.0 / 3, 12345.678, 0.0001234, 1.0);
  long double_bits_test = (long)(0.1 + 0.2 == 0.3);
  printf("%ld %d\n", double_bits_test, 0.1f + 0.2f == 0.3f);
  return 0;
}
