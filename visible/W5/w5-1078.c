void abort(void);
/* PR tree-optimization/65418 */

 int
foo (int x)
{
  if (x == -216 || x == -211 || x == -218 || x == -205 || x == -223)
     return 1;
  return 0;
}

int
b8_main ()
{
  volatile int i;
  for (i = -230; i < -200; i++)
    if (foo (i) != (i == -216 || i == -211 || i == -218 || i == -205 || i == -223))
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
