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
struct {int a[2];} g40[2] = {{1, 2}, 3, 4};
struct {int a[2];} g41[2] = {1, 2, 3, 4};
int b8_main() {

  check(86, 1, (g40[0].a[0]));
  check(87, 2, (g40[0].a[1]));
  check(88, 3, (g40[1].a[0]));
  check(89, 4, (g40[1].a[1]));

  check(90, 1, (g41[0].a[0]));
  check(91, 2, (g41[0].a[1]));
  check(92, 3, (g41[1].a[0]));
  check(93, 4, (g41[1].a[1]));
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
