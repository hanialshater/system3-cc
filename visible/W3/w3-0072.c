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

  { int x; check(9, 4, (sizeof(x))); }
  { int x; check(10, 4, (sizeof x)); }
  { int *x; check(11, 8, (sizeof(x))); }
  { int x[4]; check(12, 16, (sizeof(x))); }
  { int x[3][4]; check(13, 48, (sizeof(x))); }
  { int x[3][4]; check(14, 16, (sizeof(*x))); }
  { int x[3][4]; check(15, 4, (sizeof(**x))); }
  { int x[3][4]; check(16, 5, (sizeof(**x) + 1)); }

  { void *x; }
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
