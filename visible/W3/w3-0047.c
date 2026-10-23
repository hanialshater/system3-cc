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
  check(1, 1, (sizeof(char)));
  check(5, 4, (sizeof(int)));
  check(9, 8, (sizeof(char *)));
  check(10, 8, (sizeof(int *)));
  check(12, 8, (sizeof(int **)));
  check(13, 8, (sizeof(int(*)[4])));
  check(14, 32, (sizeof(int*[4])));
  check(15, 16, (sizeof(int[4])));
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
