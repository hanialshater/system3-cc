void abort(void);
/* PR tree-optimization/78720 */

 long int
foo (signed char x)
{
  return x < 0 ? 0x80000L : 0L;
}

 long int
bar (signed char x)
{
  return x < 0 ? 0x80L : 0L;
}

 long int
baz (signed char x)
{
  return x < 0 ? 0x20L : 0L;
}

int
b8_main ()
{
  if (foo (-1) != 0x80000L || bar (-1) != 0x80L || baz (-1) != 0x20L
      || foo (0) != 0L || bar (0) != 0L || baz (0) != 0L
      || foo (31) != 0L || bar (31) != 0L || baz (31) != 0L)
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
