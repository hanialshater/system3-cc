extern void abort ();

int f(int x)
{
  return (x >> (sizeof (x) * __CHAR_BIT__ - 1)) ? -1 : 1;
}

volatile int one = 1;
int b8_main (void)
{
  /* Test that the function above returns different values for
     different signs.  */
  if (f(one) == f(-one))
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
