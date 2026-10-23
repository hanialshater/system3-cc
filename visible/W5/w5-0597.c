void abort (void);
void exit (int);

int i, a[99];

void f (int one)
{
  if (one != 1)
    abort ();
}

void
g ()
{
  f (a[i & 0x3f]);
}

int
b8_main ()
{
  a[0] = 1;
  i = 0x40;
  g ();
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
