void abort(void);
/* PR target/93494 */

unsigned short a;

int
b8_main ()
{
  register unsigned long long y = 0;
  int x = __builtin_add_overflow (y, 0ULL, &a);
  if (x || a)
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
