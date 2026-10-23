void abort(void);
/* PR rtl-optimization/70429 */

 int
foo (int a)
{
  return (int) (0x14ff6e2207db5d1fLL >> a) >> 4;
}

int
b8_main ()
{
  if (sizeof (int) != 4 || sizeof (long long) != 8 || __CHAR_BIT__ != 8)
    return 0;
  if (foo (1) != 0x3edae8 || foo (2) != -132158092)
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
