void abort(void);
int a;
int b;

int b8_main() {
  int d = b+30;
  {
    int t;
    t = d < 29 ? 29 : ((d > 28) ? 28 : d);
    a = t;
  }
  volatile int t = a;
  if (a != 28)
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
