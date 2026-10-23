#include <limits.h>

void abort (void);
void exit (int);

int b8_main (void)
{
  void *x = ((void *)((unsigned int)INT_MAX + 2));
  void *y = ((void *)((unsigned long)LONG_MAX + 2));
  if (x >= ((void *)((unsigned int)INT_MAX + 1))
      && x <= ((void *)((unsigned int)INT_MAX + 6))
      && y >= ((void *)((unsigned long)LONG_MAX + 1))
      && y <= ((void *)((unsigned long)LONG_MAX + 6)))
    exit (0);
  else
    abort ();
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
