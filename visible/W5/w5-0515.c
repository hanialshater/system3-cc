void abort (void);
void exit (int);

unsigned
f ()
{
  long long l2;
  unsigned short us;
  unsigned long long ul;
  short s2;

  ul = us = l2 = s2 = -1;
  return ul;
}

unsigned long long
g ()
{
  long long l2;
  unsigned short us;
  unsigned long long ul;
  short s2;

  ul = us = l2 = s2 = -1;
  return ul;
}

int
b8_main (void)
{
  if (f () != (unsigned short) -1)
    abort ();
  if (g () != (unsigned short) -1)
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
