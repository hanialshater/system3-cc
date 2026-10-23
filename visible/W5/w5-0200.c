void abort(void);
/* PR tree-optimization/108064 */

static inline short
foo (short value)
{
  return ((value >> 8) & 0xff) | ((value & 0xff) << 8);
}


void
bar (short *d, const short *s)
{
  for (unsigned long i = 0; i < 4; i++)
    d[i] = foo (s[i]);
}

int
b8_main ()
{
  short a[4] __attribute__((aligned (16))) = { 0xff, 0, 0, 0 };
  short b[4] __attribute__((aligned (16)));
  short c[4] __attribute__((aligned (16)));

  bar (b, a);
  bar (c, b);
  if (a[0] != c[0])
    abort ();
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
