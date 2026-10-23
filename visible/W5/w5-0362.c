void abort (void);
void exit (int);

static double f (float a);
static double (*fp) (float a);

int
b8_main (void)
{
  fp = f;
  if (fp ((float) 1) != 1.0)
    abort ();
  exit (0);
  return 0;
}

static double
f (float a)
{
  return a;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
