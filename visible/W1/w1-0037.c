/* PR middle-end/26561 */

extern void abort(void);

int always_one_1 (int a)
{
  if (a/100 >= -999999999)
    return 1;
  else
    return 0;
}

int always_one_2 (int a)
{
  if (a/100 < -999999999)
    return 0;
  else
    return 1;
}

int b8_main(void)
{
  if (always_one_1 (0) != 1)
    abort ();

  if (always_one_2 (0) != 1)
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
