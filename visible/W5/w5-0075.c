/* PR rtl-optimization/83565 */
/* Testcase by Sergei Trofimovich <slyfox@inbox.ru> */

extern void abort (void);

typedef __UINT32_TYPE__ u32;

u32 bug (u32 * result) ;
u32 bug (u32 * result)
{
  volatile u32 ss = 0xFFFFffff;
  volatile u32 d  = 0xEEEEeeee;
  u32 tt = d & 0x00800000;
  u32 r  = tt << 8;

  r = (r >> 31) | (r <<  1);

  u32 u = r^ss;
  u32 off = u >> 1;

  *result = tt;
  return off;
}

int b8_main(void)
{
  u32 l;
  u32 off = bug(&l);
  if (off != 0x7fffffff)
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
