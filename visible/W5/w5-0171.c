int x;

void  foo (void)
{
  x = -x;
}
void __attribute__((const,noinline)) bar (void)
{
}

int 
test (int c)
{
  int tmp = x;
  (c ? foo : bar) ();
  return tmp + x;
}

extern void abort (void);
int b8_main()
{
  x = 1;
  if (test (1) != 0)
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
