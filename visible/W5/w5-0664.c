/* PR target/49186 */
extern void abort (void);

int
b8_main ()
{
  int x;
  unsigned long long uv = 0x1000000001ULL;

  x = (uv < 0x80) ? 1 : ((uv < 0x800) ? 2 : 3);
  if (x != 3)
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
