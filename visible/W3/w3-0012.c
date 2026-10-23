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
typedef int MyInt, MyInt2[4];
int b8_main() {
  { typedef int t; t x=1; check(1, 1, (x)); }
  { typedef struct {int a;} t; t x; x.a=1; check(2, 1, (x.a)); }
  { typedef struct {int a;} t; { typedef int t; } t x; x.a=2; check(4, 2, (x.a)); }
  { MyInt x=3; check(6, 3, (x)); }
  { MyInt2 x; check(7, 16, (sizeof(x))); }
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
