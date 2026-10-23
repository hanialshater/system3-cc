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

  check(4, 511, (0777));
  check(5, 0, (0x0));
  check(6, 10, (0xa));
  check(7, 10, (0XA));
  check(8, 48879, (0xbeef));
  check(9, 48879, (0xBEEF));
  check(10, 48879, (0XBEEF));
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
