/* PR tree-optimization/53465 */

extern void abort ();

static const int a[] = { 1, 2 };

void
foo (const int *x, int y)
{
  int i;
  int b = 0;
  int c;
  for (i = 0; i < y; i++)
    {
      int d = x[i];
      if (d == 0)
	break;
      if (b && d <= c)
	abort ();
      c = d;
      b = 1;
    }
}

int
b8_main ()
{
  foo (a, 2);
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
