void abort(void);
/* PR rtl-optimization/119071 */

int a, b;

int
b8_main ()
{
  int c = 0;
  if (a + 2)
    c = 1;
  int d = (1 + c - 2 + c == 1) - 1;
  b = ((d + 1) << d) + d;
  if (b != 1)
    abort ();
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
