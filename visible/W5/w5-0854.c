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

  { union { struct { unsigned char a,b,c,d; }; long e; } x; x.e=0xdeadbeef; check(8, 0xef, (x.a)); }
  { union { struct { unsigned char a,b,c,d; }; long e; } x; x.e=0xdeadbeef; check(9, 0xbe, (x.b)); }
  { union { struct { unsigned char a,b,c,d; }; long e; } x; x.e=0xdeadbeef; check(10, 0xad, (x.c)); }
  { union { struct { unsigned char a,b,c,d; }; long e; } x; x.e=0xdeadbeef; check(11, 0xde, (x.d)); }
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
