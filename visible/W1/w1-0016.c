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
int ret3(void) {
  return 3;
  return 5;
}
int add2(int x, int y) {
  return x + y;
}
int sub2(int x, int y) {
  return x - y;
}
int add6(int a, int b, int c, int d, int e, int f) {
  return a + b + c + d + e + f;
}
int b8_main() {
  check(1, 3, (ret3()));
  check(2, 8, (add2(3, 5)));
  check(3, 2, (sub2(5, 3)));
  check(4, 21, (add6(1,2,3,4,5,6)));
  check(5, 66, (add6(1,2,add6(3,4,5,6,7,8),9,10,11)));
  check(6, 136, (add6(1,2,add6(3,add6(4,5,6,7,8,9),10,11,12,13),14,15,16)));

  check(7, 7, (add2(3,4)));
  check(8, 1, (sub2(4,3)));
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
