void abort(void);
/* PR tree-optimization/61725 */

int
b8_main ()
{
  int x;
  for (x = -128; x <= 128; x++)
    {
      int a = __builtin_ffs (x);
      if (x == 0 && a != 0)
        abort ();
    }
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
