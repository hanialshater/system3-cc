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
typedef struct { int a,b; short c; char d; } Ty4;
typedef struct { long a, b, c; } Ty7;
int struct_test4(Ty4 x, int n) {
  switch (n) {
  case 0: return x.a;
  case 1: return x.b;
  case 2: return x.c;
  default: return x.d;
  }
}
int struct_test7(Ty7 x, int n) {
  switch (n) {
  case 0: return x.a;
  case 1: return x.b;
  default: return x.c;
  }
}
int struct_test4(Ty4 x, int n);
int struct_test7(Ty7 x, int n);
int struct_test14(Ty4 x, int n) {
  switch (n) {
  case 0: return x.a;
  case 1: return x.b;
  case 2: return x.c;
  default: return x.d;
  }
}
int b8_main() {

  { Ty4 x={10,20,30,40}; check(63, 10, (struct_test4(x, 0))); }
  { Ty4 x={10,20,30,40}; check(64, 20, (struct_test4(x, 1))); }
  { Ty4 x={10,20,30,40}; check(65, 30, (struct_test4(x, 2))); }
  { Ty4 x={10,20,30,40}; check(66, 40, (struct_test4(x, 3))); }

  { Ty7 x={10,20,30}; check(73, 10, (struct_test7(x, 0))); }
  { Ty7 x={10,20,30}; check(74, 20, (struct_test7(x, 1))); }
  { Ty7 x={10,20,30}; check(75, 30, (struct_test7(x, 2))); }

  { Ty4 x={10,20,30,40}; check(76, 10, (struct_test14(x, 0))); }
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
