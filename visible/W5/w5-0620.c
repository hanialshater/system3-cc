void abort(void);
short *f(short *a, int b, int *d) ;

short *f(short *a, int b, int *d)
{
  short c = *a;
  a++;
  c = b << c;
  *d = c;
  return a;
}

int b8_main(void)
{
  int d;
  short a[2];
  a[0] = 0;
  if (f(a, 1, &d) != &a[1])
    abort ();
  if (d != 1)
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
