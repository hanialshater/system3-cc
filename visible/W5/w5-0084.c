void abort (void);
void exit (int);

unsigned long*
f(p)unsigned long*p;
{
  unsigned long a = (*p++) >> 24;
  return p + a;
}

int
b8_main (void)
{
  unsigned long x = 0x80000000UL;
  if (f(&x) != &x + 0x81)
    abort();
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
