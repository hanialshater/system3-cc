void abort (void);
void exit (int);

long long
f (long long x)
{
  return x / 10000000000LL;
}

int
b8_main (void)
{
  if (f (10000000000LL) != 1 || f (100000000000LL) != 10)
    abort ();
  exit (0);
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
