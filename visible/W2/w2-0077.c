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
  check(1, 0, (""[0]));

  check(3, 97, ("abc"[0]));
  check(4, 98, ("abc"[1]));
  check(5, 99, ("abc"[2]));
  check(6, 0, ("abc"[3]));

  check(8, 7, ("\a"[0]));
  check(9, 8, ("\b"[0]));
  check(10, 9, ("\t"[0]));
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
