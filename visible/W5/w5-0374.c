void abort (void);
void exit (int);

unsigned long
sub (int a)
{
  return ((0 > a - 2) ? 0 : a - 2) * sizeof (long);
}

int
b8_main (void)
{
  if (sub (0) != 0)
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
