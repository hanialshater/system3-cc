void abort(void);
/* PR tree-optimization/109778 */

int a, b, c, d, *e = &c;

static inline unsigned
foo (unsigned char x)
{
  x = 1 | x << 1;
  x = x >> 4 | x << 4;
  return x;
}

static inline void
bar (unsigned x)
{
  *e = 8 > foo (x + 86) - 86;
}

int
b8_main ()
{
  d = a && b;
  bar (d + 4);
  if (c != 1)
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
