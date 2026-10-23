#include <stdio.h>
#include <string.h>
#include <stdlib.h>
char out[200];
typedef struct { double d; int i[3]; } B;
typedef struct { char c[33], c1; double d; } A;
A f (B a, B b, double x, double y)
{
  A s;
  sprintf (out, "%f %d %d %d %f %d %d %d %f %f", a.d, a.i[0], a.i[1], a.i[2], b.d, b.i[0], b.i[1], b.i[2], x, y);
  memset (&s, 0, sizeof (s));
  s.c[0] = 'a';
  s.d = 2.0;
  return s;
}
typedef struct { float x, y, z; } V3;
V3 cross (V3 a, V3 b) { V3 r = { a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x }; return r; }
typedef struct { double re, im; } C;
C cmul (C a, C b) { C r = { a.re * b.re - a.im * b.im, a.re * b.im + a.im * b.re }; return r; }
double many (int n, double a, float b, int c, double d, float e, double f, double g, double h, double i, double j, double k, C cc)
{ return n + a + b + c + d + e + f + g + h + i + j + k + cc.re + cc.im; }
B B1 = {-1.0, {1, 2, 3}};
B B2 = {3.0, {-3, -2, -1}};
int main ()
{
  A s;
  s = f (B1, B2, -0.1, 1.5);
  if (strcmp (out, "-1.000000 1 2 3 3.000000 -3 -2 -1 -0.100000 1.500000") != 0) abort ();
  if (s.c[0] != 'a' || s.d != 2.0) abort ();
  V3 a = {1, 0, 0}, b = {0, 1, 0}, c = cross (a, b);
  C z = cmul ((C){1, 2}, (C){3, 4});
  printf ("%g %g %g | %g %g | %g\n", c.x, c.y, c.z, z.re, z.im, many (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, z));
  return 0;
}
