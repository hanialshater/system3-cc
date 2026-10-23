void abort(void);
/* PR tree-optimization/93744 */

int w;

int
foo (int x, int y, int z)
{
  int r = z - ((z - w++) & -(x < y));
  return r;
}

int
b8_main ()
{
  w = 4;
  if (foo (5, 7, 12) != 4 || w != 5)
    abort ();
  if (foo (7, 5, 12) != 12 || w != 6)
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
