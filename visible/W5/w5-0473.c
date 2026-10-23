void abort(void);
/* PR target/98853 */

#if __SIZEOF_INT__ == 4 && __SIZEOF_LONG_LONG__ == 8
 unsigned long long
foo (unsigned long long x, unsigned int y)
{
  return ((unsigned) x & 0xfffe0000U) | (y & 0x1ffff);
}
#endif

int
b8_main ()
{
#if __SIZEOF_INT__ == 4 && __SIZEOF_LONG_LONG__ == 8
  if (foo (0xdeadbeefcaf2babeULL, 0xdeaffeedU) != 0x00000000caf3feedULL)
    abort ();
#endif
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
