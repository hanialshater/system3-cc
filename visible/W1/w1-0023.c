void abort (void);
void exit (int);

int
test (arg)
     int arg;
{
  if (arg > 0 || arg == 0)
    return 0;
  return -1;
}

int
b8_main (void)
{
  if (test (0) != 0)
    abort ();
  if (test (-1) != -1)
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
