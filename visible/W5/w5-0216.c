/* PR middle-end/53084 */

extern void abort (void);

 void
bar (const char *p)
{
  if (p[0] != 'o' || p[1] != 'o' || p[2])
    abort ();
}

int
b8_main ()
{
  static const char *const foo[] = {"foo" + 1};
  bar (foo[0]);
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
