#include <stdio.h>

int b8_main()
{
   int a;

   for (a = 0; a < 2; a++)
   {
      int b = a;
   }

   printf("it's all good\n");

   return 0;
  return 0;
}

/* vim: set expandtab ts=4 sw=3 sts=3 tw=80 :*/

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
