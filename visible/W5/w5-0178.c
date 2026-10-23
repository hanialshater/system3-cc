void abort(void);
struct S {
  short int i : 12;
  char c1 : 1;
  char c2 : 1;
  char c3 : 1;
  char c4 : 1;
};

int b8_main (void)
{
  struct S s0 = { 341, 1, 1, 1, 1 };
  char *p = (char *) &s0;

#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
  if (*p != 85)
    abort ();
#elif __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
  if (*p != 21)
    abort ();
#endif

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
