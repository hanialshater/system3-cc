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
  check(16, 8, (sizeof(0L)));
  check(17, 8, (sizeof(0LU)));
  check(18, 8, (sizeof(0UL)));
  check(19, 8, (sizeof(0LL)));
  check(20, 8, (sizeof(0LLU)));
  check(21, 8, (sizeof(0Ull)));
  check(22, 8, (sizeof(0l)));
  check(23, 8, (sizeof(0ll)));
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
