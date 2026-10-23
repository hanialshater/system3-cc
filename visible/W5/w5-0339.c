void abort(void);
/* PR tree-optimization/47155 */

unsigned int a;
static signed char b = -127;
int c = 1;

int
b8_main (void)
{
  a = b <= (unsigned char) (-6 * c);
  if (!a)
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
