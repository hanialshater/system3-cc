/* PR tree-optimization/51466 */

extern void abort (void);

 int
foo (int i)
{
  volatile int v[4];
  int *p;
  v[i] = 6;
  p = (int *) &v[i];
  return *p;
}

 int
bar (int i)
{
  volatile int v[4];
  int *p;
  v[i] = 6;
  p = (int *) &v[i];
  *p = 8;
  return v[i];
}

 int
baz (int i)
{
  volatile int v[4];
  int *p;
  v[i] = 6;
  p = (int *) &v[0];
  *p = 8;
  return v[i];
}

int
b8_main ()
{
  if (foo (3) != 6 || bar (2) != 8 || baz (0) != 8 || baz (1) != 6)
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
