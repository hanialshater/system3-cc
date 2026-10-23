void abort(void);
/* PR c/77767 */

void
foo (int a, int b[a++], int c, int d[c++])
{
  if (a != 2 || c != 2)
    abort ();
}

int
b8_main ()
{
  int e[10];
  foo (1, e, 1, e);
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
