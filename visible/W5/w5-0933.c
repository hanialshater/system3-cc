void abort(void);
void *memcpy(void *, const void *, unsigned long);
/* PR rtl-optimization/63843 */

static inline 
unsigned short foo (unsigned short v)
{
  return (v << 8) | (v >> 8);
}

unsigned short 
bar (unsigned char *x)
{
  unsigned int a;
  unsigned short b;
  memcpy (&a, &x[0], sizeof (a));
  a ^= 0x80808080U;
  memcpy (&x[0], &a, sizeof (a));
  memcpy (&b, &x[2], sizeof (b));
  return foo (b);
}

int
b8_main ()
{
  unsigned char x[8] = { 0x01, 0x01, 0x01, 0x01 };
  if (__CHAR_BIT__ == 8
      && sizeof (short) == 2
      && sizeof (int) == 4
      && bar (x) != 0x8181U)
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
