void abort(void);
/* PR target/94134 */

static volatile int a = 0;
static volatile int b = 1;

int
b8_main ()
{
  a++;
  b++;
  if (a != 1 || b != 2)
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
