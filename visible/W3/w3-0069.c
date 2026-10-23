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

  { struct { int a[2]; } x={.a=1,2}; check(144, 1, (x.a[0])); }
  { struct { int a[2]; } x={.a=1,2}; check(145, 2, (x.a[1])); }

  { struct { int a[2]; } x={.a[1]=1}; check(146, 0, (x.a[0])); }
  { struct { int a[2]; } x={.a[1]=1}; check(147, 1, (x.a[1])); }

  { struct { int a,b; } x[]={[1].b=1,2,[0]=3,4,}; check(148, 3, (x[0].a)); }
  { struct { int a,b; } x[]={[1].b=1,2,[0]=3,4,}; check(149, 4, (x[0].b)); }
  { struct { int a,b; } x[]={[1].b=1,2,[0]=3,4,}; check(150, 0, (x[1].a)); }
  { struct { int a,b; } x[]={[1].b=1,2,[0]=3,4,}; check(151, 1, (x[1].b)); }
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
