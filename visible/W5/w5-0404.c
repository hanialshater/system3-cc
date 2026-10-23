/* PR c++/14755 */
extern void abort (void);
extern void exit (int);

int
b8_main (void)
{
#if __INT_MAX__ >= 2147483647
  struct { int count: 31; } s = { 0 };
  while (s.count--)
    abort ();
#elif __INT_MAX__ >= 32767
  struct { int count: 15; } s = { 0 };
  while (s.count--)
    abort ();
#else
  /* Don't bother because __INT_MAX__ is too small.  */
#endif
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
