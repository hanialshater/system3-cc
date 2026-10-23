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
typedef struct { unsigned char a[3]; } Ty6;
int struct_test6(Ty6 x, int n) {
  return x.a[n];
}
_Bool bool_fn_sub(_Bool x) { return x - 1; }
int struct_test6(Ty6 x, int n);
int b8_main() {
  check(22, 1, (bool_fn_sub(0)));

  { Ty6 x={10,20,30}; check(70, 10, (struct_test6(x, 0))); }
  { Ty6 x={10,20,30}; check(71, 20, (struct_test6(x, 1))); }
  { Ty6 x={10,20,30}; check(72, 30, (struct_test6(x, 2))); }
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
