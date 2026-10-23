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
  check(50, 1, (5.0f<=5));
  check(51, 1, (4.9f<=5));

  check(52, 6, (2.3+3.8));
  check(53, -1, (2.3-3.8));
  check(54, -3, (-3.8));
  check(55, 13, (3.3*4));
  check(56, 2, (5.0/2));

  check(57, 6, (2.3f+3.8f));
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
