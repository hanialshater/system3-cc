extern void abort (void);

#define N	(1 << (sizeof(int) * __CHAR_BIT__ - 2))

int f(int n)
{
  if (-N <= n && n <= N-1)
    return 1;
  return 0;
}

int b8_main ()
{
  if (f (N))
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
