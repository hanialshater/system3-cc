#include <stdbool.h>

void abort (void);
void exit (int);

int f (int a, int b, int c, _Bool d, _Bool e, _Bool f, char g)
{
  if (g != 1 || d != true || e != true || f != true) abort ();
  return a + b + c;
}

int b8_main (void)
{
  if (f (1, 2, -3, true, true, true, '\001'))
    abort ();
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
