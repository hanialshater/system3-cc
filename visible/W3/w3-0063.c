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
  { int x[3]={1,2,3}; check(1, 1, (x[0])); }
  { int x[3]={1,2,3}; check(2, 2, (x[1])); }
  { int x[3]={1,2,3}; check(3, 3, (x[2])); }
  { int x[3]={1,2,3}; check(4, 3, (x[2])); }

  { int x[2][3]={{1,2,3},{4,5,6}}; check(5, 2, (x[0][1])); }
  { int x[2][3]={{1,2,3},{4,5,6}}; check(6, 4, (x[1][0])); }
  { int x[2][3]={{1,2,3},{4,5,6}}; check(7, 6, (x[1][2])); }

  { int x[2][3]={{1,2}}; check(11, 2, (x[0][1])); }
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
