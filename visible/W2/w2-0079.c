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

  { int x; int *p=&x; check(100, 20, (p+20-p)); }
  { int x; int *p=&x; check(101, 1, (p+20-p>0)); }
  { int x; int *p=&x; check(102, -20, (p-20-p)); }
  { int x; int *p=&x; check(103, 1, (p-20-p<0)); }
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
