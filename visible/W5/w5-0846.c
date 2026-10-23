void abort(void);
/* PR tree-optimization/66233 */

unsigned int v[8];

 void
foo (void)
{
  int i;
  for (i = 0; i < 8; i++)
    v[i] = (float) i;
}

int
b8_main ()
{
  unsigned int i;
  foo ();
  for (i = 0; i < 8; i++)
    if (v[i] != i)
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
