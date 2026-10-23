int * __restrict__ x;

int foo (int y)
{
  *x = y;
  return *x;
}

extern void abort (void);

int b8_main()
{
  int i = 0;
  x = &i;
  if (foo(1) != 1)
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
