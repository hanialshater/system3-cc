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
  { struct { int a,b; } x[]={[1].b=1,2,[0]=3,4,}; check(152, 2, (x[2].a)); }
  { struct { int a,b; } x[]={[1].b=1,2,[0]=3,4,}; check(153, 0, (x[2].b)); }

  { struct { struct { int a; struct { int b; }; }; int c; } x={1,2,3,.b=4,5}; check(165, 1, (x.a)); }
  { struct { struct { int a; struct { int b; }; }; int c; } x={1,2,3,.b=4,5}; check(166, 4, (x.b)); }
  { struct { struct { int a; struct { int b; }; }; int c; } x={1,2,3,.b=4,5}; check(167, 5, (x.c)); }
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
