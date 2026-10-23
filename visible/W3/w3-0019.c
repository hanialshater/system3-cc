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
  { union { int a; char b[6]; } x; check(1, 8, (sizeof(x))); }
  { union { int a; char b[4]; } x; x.a = 515; check(2, 3, (x.b[0])); }
  { union { int a; char b[4]; } x; x.a = 515; check(3, 2, (x.b[1])); }
  { union { int a; char b[4]; } x; x.a = 515; check(4, 0, (x.b[2])); }
  { union { int a; char b[4]; } x; x.a = 515; check(5, 0, (x.b[3])); }

  { union {int a,b;} x,y; x.a=3; y.a=5; y=x; check(6, 3, (y.a)); }
  { union {struct {int a,b;} c;} x,y; x.c.b=3; y.c.b=5; y=x; check(7, 3, (y.c.b)); }

  {struct { union { int a,b; }; union { int c,d; }; } x; x.a=3; check(12, 3, (x.b)); }
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
