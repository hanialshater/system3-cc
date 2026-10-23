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

  struct bit1 {
    short a;
    char b;
    int c : 2;
    int d : 3;
    int e : 3;
  };
  { struct bit1 x={1,2,3,4,5}; check(9, -3, (x.e)); }

  typedef struct {
    int a : 10;
    int b : 10;
    int c : 10;
  } T3;

  { T3 x={1,2,3}; check(16, 1, (x.a++)); }
  { T3 x={1,2,3}; check(17, 2, (x.b++)); }
  { T3 x={1,2,3}; check(18, 3, (x.c++)); }

  { T3 x={1,2,3}; check(19, 2, (++x.a)); }
  { T3 x={1,2,3}; check(20, 3, (++x.b)); }
  { T3 x={1,2,3}; check(21, 4, (++x.c)); }
  check(23, 8, (sizeof(struct {int a:3; int:0; int c:5;})));
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
