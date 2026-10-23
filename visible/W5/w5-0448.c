void abort (void);
void exit (int);

int a = 1;
int b = -1;

int c = 1;
int d = 0;

int
b8_main (void)
{
  double e;
  double f;
  double g;

  f = c;
  g = d;
  e = (a < b) ? f : g;
  if (e)
    abort ();
  exit(0);
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
