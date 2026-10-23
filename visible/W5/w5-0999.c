/* PR target/19005 */
extern void abort (void);

int v, s;

void
bar (int a, int b)
{
  unsigned char x = v;

  if (!s)
    {
      if (a != x || b != (unsigned char) (x + 1))
        abort ();
    }
  else if (a != (unsigned char) (x + 1) || b != x)
    abort ();
  s ^= 1;
}

int
foo (int x)
{
  unsigned char a = x, b = x + 1;

  bar (a, b);
  a ^= b; b ^= a; a ^= b;
  bar (a, b);
  return 0;
}

int
b8_main (void)
{
  for (v = -10; v < 266; v++)
    foo (v);
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
