void abort (void);
void exit (int);

long f (x, y)
     long x,y;
{
  return (x > 1) ? y : (y & 1);
}

int
b8_main (void)
{
  if (f (2L, 0xdecadeL) != 0xdecadeL)
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
