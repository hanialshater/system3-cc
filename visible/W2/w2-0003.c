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
  { int x=3; check(1, 3, (*&x)); }
  { int x=3; int *y=&x; int **z=&y; check(2, 3, (**z)); }
  { int x=3; int *y=&x; *y=5; check(6, 5, (x)); }
  { int x=3; check(9, 5, ((&x+2)-&x+3)); }
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
