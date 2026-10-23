void abort (void);
void exit (int);

static inline int
p (int *p)
{
  return !((long) p & 1);
}

int
f (int *q)
{
  if (p (q) && *q)
    return 1;
  return 0;
}

int
b8_main (void)
{
  if (f ((int*) 0xffffffff) != 0)
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
