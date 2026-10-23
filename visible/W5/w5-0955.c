void abort (void);
void exit (int);

int
f()
{
  int j = 1;
  long i;
  for (i = -0x70000000L; i < 0x60000000L; i += 0x10000000L) j <<= 1;
  return j;
}

int
b8_main ()
{
  if (f () != 8192)
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
