/* PR middle-end/48973 */

extern void abort (void);
struct S { int f : 1; } s;
int v = -1;

void
foo (unsigned int x)
{
  if (x != -1U)
    abort ();
}

int
b8_main ()
{
  s.f = (v & 1) > 0;
  foo (s.f);
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
