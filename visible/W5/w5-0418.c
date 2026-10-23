void abort(void);
/* PR rtl-optimization/64260 */

int a = 1, b;

void
foo (char p)
{
  int t = 0;
  for (; b < 1; b++)
    {
      int *s = &a;
      if (--t)
	*s &= p;
      *s &= 1;
    }
}

int
b8_main ()
{
  foo (0);
  if (a != 0)
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
