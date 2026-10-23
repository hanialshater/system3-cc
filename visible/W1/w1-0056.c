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
int add10_int(int x1, int x2, int x3, int x4, int x5, int x6, int x7, int x8, int x9, int x10) {
  return x1 + x2 + x3 + x4 + x5 + x6 + x7 + x8 + x9 + x10;
}
int fib(int x) {
  if (x<=1)
    return 1;
  return fib(x-1) + fib(x-2);
}
int add10_int(int x1, int x2, int x3, int x4, int x5, int x6, int x7, int x8, int x9, int x10);
int many_args1(int a, int b, int c, int d, int e, int f, int g, int h) {
  return g / h;
}
int b8_main() {
  check(9, 55, (fib(9)));


  check(56, 55, (add10_int(1,2,3,4,5,6,7,8,9,10)));

  check(60, 4, (many_args1(1,2,3,4,5,6,40,10)));
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
