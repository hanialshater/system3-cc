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
int strcmp(const char *a, const char *b);
char g43[][4] = {'f', 'o', 'o', 0, 'b', 'a', 'r', 0};
char *g44 = {"foo"};
int b8_main() {

  { int x[2][3]={0,1,2,3,4,5}; check(94, 0, (x[0][0])); }
  { int x[2][3]={0,1,2,3,4,5}; check(95, 3, (x[1][0])); }

  { struct {int a; int b;} x[2]={0,1,2,3}; check(96, 0, (x[0].a)); }
  { struct {int a; int b;} x[2]={0,1,2,3}; check(97, 2, (x[1].a)); }

  check(98, 0, (strcmp(g43[0], "foo")));
  check(99, 0, (strcmp(g43[1], "bar")));
  check(100, 0, (strcmp(g44, "foo")));

  { int a[]={1,2,3,}; check(101, 3, (a[2])); }
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
