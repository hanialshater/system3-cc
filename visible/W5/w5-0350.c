/* PR middle-end/36013 */

extern void abort (void);

void 
foo (int **__restrict p, int **__restrict q)
{
  *p[0] = 1;
  *q[0] = 2;
  if (*p[0] != 2)
    abort ();
}

int
b8_main (void)
{
  int a;
  int *p1 = &a, *p2 = &a;
  foo (&p1, &p2);
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
