#include <string.h>

void abort (void);

void foo (int *BM_tab, int j)
{
  int *BM_tab_base;

  BM_tab_base = BM_tab;
  BM_tab += 0400;
  while (BM_tab_base != BM_tab)
    {
      *--BM_tab = j;
      *--BM_tab = j;
      *--BM_tab = j;
      *--BM_tab = j;
    }
}

int b8_main ()
{
  int BM_tab[0400];
  memset (BM_tab, 0, sizeof (BM_tab));
  foo (BM_tab, 6);
  if (BM_tab[0] != 6)
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
