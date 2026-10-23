/* PR rtl-optimization/19579 */

extern void abort (void);

int
foo (int i, int j)
{
  int k = i + 1;

  if (j)
    {
      if (k > 0)
	k++;
      else if (k < 0)
	k--;
    }

  return k;
}

int
b8_main (void)
{
  if (foo (-2, 0) != -1)
    abort ();
  if (foo (-1, 0) != 0)
    abort ();
  if (foo (0, 0) != 1)
    abort ();
  if (foo (1, 0) != 2)
    abort ();
  if (foo (-2, 1) != -2)
    abort ();
  if (foo (-1, 1) != 0)
    abort ();
  if (foo (0, 1) != 2)
    abort ();
  if (foo (1, 1) != 3)
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
