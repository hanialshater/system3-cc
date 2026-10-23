void abort (void);
void exit (int);

typedef struct trio { int a, b, c; } trio;

int
bar (int i, int j, int k, trio t)
{
  if (t.a != 1 || t.b != 2 || t.c != 3 ||
      i != 4 || j != 5 || k != 6)
    abort ();
}

int
foo (trio t, int i, int j, int k)
{
  return bar (i, j, k, t);
}

int
b8_main (void)
{
  trio t = { 1, 2, 3 };

  foo (t, 4, 5, 6);
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
