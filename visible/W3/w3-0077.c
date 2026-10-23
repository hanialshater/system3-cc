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
union { int a; char b[8]; } g13[2] = {0x01020304, 0x05060708};
int b8_main() {

  { union { int a; char b[4]; } x={0x01020304}; check(44, 4, (x.b[0])); }
  { union { int a; char b[4]; } x={0x01020304}; check(45, 3, (x.b[1])); }

  { union { struct { char a,b,c,d; } e; int f; } x={{4,3,2,1}}; check(46, 0x01020304, (x.f)); }

  check(62, 4, (g13[0].b[0]));
  check(63, 3, (g13[0].b[1]));
  check(64, 8, (g13[1].b[0]));
  check(65, 7, (g13[1].b[1]));
  { union {int a; char b;} x={1,}; check(103, 1, (x.a)); }
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
