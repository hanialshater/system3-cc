/* This testcase was miscompiled on IA-32, because fold-const
   assumed associate_trees is always done on PLUS_EXPR.  */

extern void abort (void);
extern void exit (int);

void check (unsigned int m)
{
  if (m != (unsigned int) -1)
    abort ();
}

unsigned int n = 1;

int b8_main (void)
{
  unsigned int m;
  m = (1 | (2 - n)) | (-n);
  check (m);
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
