void abort(void);
/* PR tree-optimization/47148 */

static inline unsigned
bar (unsigned x, unsigned y)
{
  if (y >= 32)
    return x;
  else
    return x >> y;
}

static unsigned a = 1, b = 1;

static inline void
foo (unsigned char x, unsigned y)
{
  if (!y)
    return;
  unsigned c = (0x7000U / (x - 2)) ^ a;
  unsigned d = bar (a, a);
  b &= ((a - d) && (a - 1)) + c;
}

int
b8_main (void)
{
  foo (1, 1);
  foo (-1, 1);
  if (b && ((unsigned char) -1) == 255)
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
