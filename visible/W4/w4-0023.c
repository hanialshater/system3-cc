#include <stdio.h>

#define FRED 12
#define BLOGGS(x) (12*(x))

int b8_main()
{
   printf("%d\n", FRED);
   printf("%d, %d, %d\n", BLOGGS(1), BLOGGS(2), BLOGGS(3));

   return 0;
  return 0;
}

// vim: set expandtab ts=4 sw=3 sts=3 tw=80 :

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
