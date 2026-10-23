void abort(void);
/* PR tree-optimization/78436 */

struct S
{
  long int a : 24;
  signed char b : 8;
} s;

 void
foo ()
{
  s.b = 0;
  s.a = -1193165L;
}

int
b8_main ()
{
  foo ();
  if (s.b != 0)
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
