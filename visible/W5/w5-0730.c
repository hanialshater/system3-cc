void abort(void);
/* PR rtl-optimization/64756 */

int a, *tmp, **c = &tmp;
volatile int d;
static int *volatile *e = &tmp;
unsigned int f;

static void
fn1 (int *p)
{
  int g;
  for (; f < 1; f++)
    for (g = 1; g >= 0; g--)
      {
	d || d;
	*c = p;

	if (tmp != &a)
	  abort ();

	*e = 0;
      }
}

int
b8_main ()
{
  fn1 (&a);
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
