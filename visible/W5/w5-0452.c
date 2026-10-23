void abort(void);

#define comparison (f < 0)
int b8_main() {
  int f = 0;
  int d = comparison | !comparison;
  if (d != 1)
    abort();
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
