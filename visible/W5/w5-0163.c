void abort(void);
/* PR target/94567 */

volatile int a = 1, b;
short c, d = 4, f = 2, g;
unsigned short e = 53736;

int
foo (int i, int j)
{
  return i && j ? 0 : i + j;
}

int
b8_main ()
{
  for (; a; a = 0)
    {
      unsigned short k = e;
      g = k >> 3;
      if (foo (g < (f || c), b))
	d = 0;
    }
  if (d != 4)
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
