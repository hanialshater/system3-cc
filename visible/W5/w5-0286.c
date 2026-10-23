void abort(void);
int a;
int b;
int c(int d, int e, int f) {
  if (d < e)
    return e;
  if (d > f)
    return f;
  return d;
}
int b8_main() {
  int g = -1;
  a = c(b + 30, 29, g + 29);
  volatile int t = a;
  if (t != 28)
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
