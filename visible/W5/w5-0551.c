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
int _Alignas(512) g1;
int _Alignas(512) g2;
int b8_main() {
  { struct { _Alignas(16) char x, y; } a; check(14, 16, (&a.y-&a.x)); }
  { struct T { _Alignas(8) char a; }; check(15, 8, (_Alignof(struct T))); }

  check(16, 0, ((long)(char *)&g1 % 512));
  check(17, 0, ((long)(char *)&g2 % 512));

  { char x; check(20, 1, (_Alignof(x))); }
  { int x; check(21, 4, (_Alignof(x))); }
  { char x; check(22, 1, (_Alignof x)); }
  { int x; check(23, 4, (_Alignof x)); }
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
