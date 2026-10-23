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
  check(24, 8, (sizeof(0x0L)));
  check(27, 8, (sizeof(2147483648)));
  check(28, -1, (0xffffffffffffffff));
  check(29, 8, (sizeof(0xffffffffffffffff)));
  check(30, 4, (sizeof(4294967295U)));
  check(31, 8, (sizeof(4294967296U)));

  check(40, -1, (0xffffffffffffffff));
  check(41, 8, (sizeof(0xffffffffffffffff)));
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
