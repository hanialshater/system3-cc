/* PR rtl-optimization/51667 */
/* Testcase by Uros Bizjak <ubizjak@gmail.com> */

extern void abort (void);

void 
bar (int a)
{
  if (a != -1)
    abort ();
}

void 
foo (short *a, int t)
{
  short r = *a;

  if (t)
    bar ((unsigned short) r);
  else
    bar ((signed short) r);
}

short v = -1;

int b8_main(void)
{
  foo (&v, 0);
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
