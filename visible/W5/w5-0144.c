struct X { int *p; } x;

struct X 
foo(int *p) { struct X x; x.p = p; return x; }

void __attribute((noinline))
bar() { *x.p = 1; }

extern void abort (void);
int b8_main()
{
  int i = 0;
  x = foo(&i);
  bar();
  if (i != 1)
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
