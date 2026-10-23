extern void abort (void);

unsigned int foo (int n)
{
  return ~((unsigned int)~n);
}

int b8_main()
{
  if (foo(0) != 0)
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
