struct Foo {
  int i;
  int j[];
};

struct Foo x = { 1, { 2, 0, 2, 3 } };

int foo(void)
{
  x.j[0] = 1;
  return x.j[1];
}

extern void abort(void);

int b8_main()
{
  if (foo() != 0)
    abort();
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
