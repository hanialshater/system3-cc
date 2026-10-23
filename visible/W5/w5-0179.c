extern void exit (int);
extern void abort (void);
extern unsigned short f (short a) ;

unsigned short
f (short a)
{
  short b;

  if (a > 0)
    return 0;
  b = ((int) a) + - (int) 32768;
  return b;
}

int
b8_main (void)
{
  if (sizeof (short) < 2
      || sizeof (short) >= sizeof (int))
    exit (0);

  if (f (-32767) != 1)
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
