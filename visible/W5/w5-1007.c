/* PR rtl-optimization/57281 */

int a = 1, b, d, *e = &d;
long long c, *g = &c;
volatile long long f;

int
foo (int h)
{
  int j = *g = b;
  return h == 0 ? j : 0;
}

int
b8_main ()
{
  int h = a;
  for (; b != -20; b--)
    {
      (int) f;
      *e = 0;
      *e = foo (h);
    }
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
