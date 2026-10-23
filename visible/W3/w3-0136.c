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
int g9[3] = {0, 1, 2};
int b8_main() {
  { struct {int a; int b; int c;} x={1}; check(32, 0, (x.b)); }
  { struct {int a; int b; int c;} x={1}; check(33, 0, (x.c)); }

  { struct {int a; int b;} x[2]={{1,2},{3,4}}; check(34, 1, (x[0].a)); }
  { struct {int a; int b;} x[2]={{1,2},{3,4}}; check(35, 2, (x[0].b)); }
  { struct {int a; int b;} x[2]={{1,2},{3,4}}; check(36, 3, (x[1].a)); }
  { struct {int a; int b;} x[2]={{1,2},{3,4}}; check(37, 4, (x[1].b)); }

  { struct {int a; int b;} x[2]={{1,2}}; check(38, 0, (x[1].b)); }

  check(51, 0, (g9[0]));
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
