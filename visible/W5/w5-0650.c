extern void abort(void);
extern void exit(int);

unsigned int foo(unsigned int a)
{
  return ((unsigned char)(a + 1)) * 4;
}

int b8_main(void)
{
  if (foo((unsigned char)~0))
    abort ();
  exit(0);
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
