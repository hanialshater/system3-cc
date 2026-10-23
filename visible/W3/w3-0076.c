void abort (void);
void exit (int);

int x[10] = { 0,1,2,3,4,5,6,7,8,9};

int
b8_main()
{
  int niterations = 0, i;

  for (;;) {
    int i, mi, max;
    max = 0;
    for (i = 0; i < 10 ; i++) {
      if (x[i] > max) {
	max = x[i];
	mi = i;
      }
    }
    if (max == 0)
      break;
    x[mi] = 0;
    niterations++;
    if (niterations > 10)
      abort ();
  }

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
