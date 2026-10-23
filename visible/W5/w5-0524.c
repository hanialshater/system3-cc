void abort(void);
/* PR sanitizer/81281 */

void
foo (unsigned p, unsigned a, unsigned b)
{
  unsigned q = p + 7;
  if (a - (1U + __INT_MAX__) >= 2)
    __builtin_unreachable ();
  int d = p + b;
  int c = p + a;
  if (c - d != __INT_MAX__)
    abort ();
}

void
bar (unsigned p, unsigned a)
{
  unsigned q = p + 7;
  if (a - (1U + __INT_MAX__) >= 2)
    __builtin_unreachable ();
  int c = p;
  int d = p + a;
  if (c - d != -__INT_MAX__ - 1)
    abort ();
}

int
b8_main ()
{
  foo (-1U, 1U + __INT_MAX__, 1U);
  bar (-1U, 1U + __INT_MAX__);
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
