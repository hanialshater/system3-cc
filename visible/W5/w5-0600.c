void abort(void);
/* PR tree-optimization/86844 */

 void
foo (int *p)
{
  *p = 0;
  *((char *)p + 3) = 1;
  *((char *)p + 1) = 2;
  *((char *)p + 2) = *((char *)p + 6);
}

int
b8_main ()
{
  int a[2] = { -1, 0 };
  if (sizeof (int) != 4)
    return 0;
  ((char *)a)[6] = 3;
  foo (a);
  if (((char *)a)[0] != 0 || ((char *)a)[1] != 2
      || ((char *)a)[2] != 3 || ((char *)a)[3] != 1)
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
