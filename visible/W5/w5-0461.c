void abort(void);
/* PR tree-optimization/71631 */

volatile char v;
int a = 1, b = 1, c = 1;

void
foo (const char *s)
{
  while (*s++)
    v = *s;
}

int
b8_main ()
{
  volatile int d = 1;
  volatile int e = 1;
  int f = 1 / a;
  int g = 1U < f;
  int h = 2 + g;
  int i = 3 % h;
  int j = e && b;
  int k = 1 == c;
  int l = d != 0;
  short m = (short) (-1 * i * l);
  short x = j * (k * m);
  if (i == 1)
    foo ("AB");
  if (x != -1)
    abort ();
  return 0;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
