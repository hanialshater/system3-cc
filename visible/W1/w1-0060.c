int printf(const char *fmt, ...);
void exit(int status);
void check(int id, int expected, int actual) {
  if (expected == actual) {
    printf("%d => %d\n", id, actual);
  } else {
    printf("%d => %d expected but got %d\n", id, expected, actual);
    exit(1);
  }
}
int b8_main() {
  check(9, 10, (- -10));
  check(10, 10, (- - +10));

  check(11, 0, (0==1));
  check(12, 1, (42==42));
  check(13, 1, (0!=1));
  check(14, 0, (42!=42));

  check(15, 1, (0<1));
  check(16, 0, (1<1));
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
