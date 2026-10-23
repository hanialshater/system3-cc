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

  check(18, 8, (sizeof(-10 + (long)5)));
  check(19, 8, (sizeof(-10 - (long)5)));
  check(20, 8, (sizeof(-10 * (long)5)));
  check(21, 8, (sizeof(-10 / (long)5)));
  check(22, 8, (sizeof((long)-10 + 5)));
  check(23, 8, (sizeof((long)-10 - 5)));
  check(24, 8, (sizeof((long)-10 * 5)));
  check(25, 8, (sizeof((long)-10 / 5)));
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
