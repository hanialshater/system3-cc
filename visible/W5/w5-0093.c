struct Foo {
  int *p;
  int *q;
};

void 
bar (int **x)
{
  struct Foo *f = (struct Foo *)x;
  *(f->q) = 0;
}

int foo(void)
{
  struct Foo f;
  int i = 1, j = 2;
  f.p = &i;
  f.q = &j;
  bar(&f.p);
  return j;
}

extern void abort (void);
int b8_main()
{
  if (foo () != 0)
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
