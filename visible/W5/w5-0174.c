void abort(void);
/* PR rtl-optimization/97386 */

 unsigned
foo (int x)
{
  unsigned long long a = (0x800000000000ccccULL << x) | (0x800000000000ccccULL >> (64 - x));
  unsigned int b = a;
  return (b << 24) | (b >> 8);
}

int
b8_main ()
{
  if (__CHAR_BIT__ == 8
      && __SIZEOF_INT__ == 4
      &&  __SIZEOF_LONG_LONG__ == 8
      && foo (1) != 0x99000199U)
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
