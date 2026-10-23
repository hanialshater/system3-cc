#include <stdio.h>

int b8_main() 
{
   int a;
   a = 42;
   printf("%d\n", a);

   int b = 64;
   printf("%d\n", b);

   int c = 12, d = 34;
   printf("%d, %d\n", c, d);

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
