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
  { char x[2][4]={"abc","def"}; check(20, 'f', (x[1][2])); }

  { int x[]={1,2,3,4}; check(21, 4, (x[3])); }
  { int x[]={1,2,3,4}; check(22, 16, (sizeof(x))); }
  { char x[]="foo"; check(23, 4, (sizeof(x))); }

  { struct {int a; int b; int c;} x={1,2,3}; check(28, 1, (x.a)); }
  { struct {int a; int b; int c;} x={1,2,3}; check(29, 2, (x.b)); }
  { struct {int a; int b; int c;} x={1,2,3}; check(30, 3, (x.c)); }
  { struct {int a; int b; int c;} x={1}; check(31, 1, (x.a)); }
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
