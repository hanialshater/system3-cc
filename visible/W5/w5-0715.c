void abort(void);
/* PR middle-end/91450 */

 void
foo (int a, int b)
{
  unsigned long long r;
  if (__builtin_mul_overflow (a, b, &r))
    abort ();
  if (r != 0)
    abort ();
}

 void
bar (int a, int b)
{
  unsigned long long r;
  if (a >= 0)
    return;
  if (__builtin_mul_overflow (a, b, &r))
    abort ();
  if (r != 0)
    abort ();
}

 void
baz (int a, int b)
{
  unsigned long long r;
  if (b >= 0)
    return;
  if (__builtin_mul_overflow (a, b, &r))
    abort ();
  if (r != 0)
    abort ();
}

 void
qux (int a, int b)
{
  unsigned long long r;
  if (a >= 0)
    return;
  if (b < 0)
    return;
  if (__builtin_mul_overflow (a, b, &r))
    abort ();
  if (r != 0)
    abort ();
}

 void
quux (int a, int b)
{
  unsigned long long r;
  if (a < 0)
    return;
  if (b >= 0)
    return;
  if (__builtin_mul_overflow (a, b, &r))
    abort ();
  if (r != 0)
    abort ();
}

int
b8_main ()
{
  foo (-4, 0);
  foo (0, -4);
  foo (0, 0);
  bar (-4, 0);
  baz (0, -4);
  qux (-4, 0);
  quux (0, -4);
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
