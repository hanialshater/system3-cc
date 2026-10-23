void abort(void);
int memcmp(const void *, const void *, unsigned long);
void *memset(void *, int, unsigned long);
/* PR middle-end/77718 */

char a[64] __attribute__((aligned (8)));

 int
foo (void)
{
  return memcmp ("bbbbbb", a, 6);
}

 int
bar (void)
{
  return memcmp (a, "bbbbbb", 6);
}

int
b8_main ()
{
  memset (a, 'a', sizeof (a));
  if (((foo () < 0) ^ ('a' > 'b'))
      || ((bar () < 0) ^ ('a' < 'b')))
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
