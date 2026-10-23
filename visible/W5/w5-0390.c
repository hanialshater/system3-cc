/* PR target/49281 */

extern void abort (void);

 int
foo (int x)
{
  return (x << 2) | 4;
}

 int
bar (int x)
{
  return (x << 2) | 3;
}

int
b8_main ()
{
  if (foo (43) != 172 || foo (1) != 4 || foo (2) != 12)
    abort ();
  if (bar (43) != 175 || bar (1) != 7 || bar (2) != 11)
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
