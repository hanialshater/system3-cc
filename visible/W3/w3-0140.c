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
  { struct t {char a;} x; struct t *y = &x; y->a=3; check(28, 3, (x.a)); }

  { struct {int a,b;} x,y; x.a=3; y=x; check(29, 3, (y.a)); }
  { struct t {int a,b;}; struct t x; x.a=7; struct t y; struct t *z=&y; *z=x; check(30, 7, (y.a)); }
  { struct t {int a,b;}; struct t x; x.a=7; struct t y, *p=&x, *q=&y; *q=*p; check(31, 7, (y.a)); }
  { struct t {char a, b;} x, y; x.a=5; y=x; check(32, 5, (y.a)); }

  { struct {int a,b;} x,y; x.a=3; y=x; check(33, 3, (y.a)); }
  { struct t {int a,b;}; struct t x; x.a=7; struct t y; struct t *z=&y; *z=x; check(34, 7, (y.a)); }
  { struct t {int a,b;}; struct t x; x.a=7; struct t y, *p=&x, *q=&y; *q=*p; check(35, 7, (y.a)); }
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
