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
int common_ext2 = 3;
int x;
int x = 5;
int y = 7;
int y;
int common_ext1;
int common_ext2;
int b8_main() {
  check(1, 5, (x));
  check(2, 7, (y));
  check(3, 0, (common_ext1));
  check(4, 3, (common_ext2));
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
