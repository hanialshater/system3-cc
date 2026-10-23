void abort(void);
/* PR tree-optimization/65418 */

 int
foo (int x)
{
  if (x == -216 || x == -132 || x == -218 || x == -146)
     return 1;
  return 0;
}

int
b8_main ()
{
  volatile int i;
  for (i = -230; i < -120; i++)
    if (foo (i) != (i == -216 || i == -132 || i == -218 || i == -146))
      abort ();
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
