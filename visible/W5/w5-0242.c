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
typedef struct { char a, b[]; } T65;
T65 g65 = {'f','o','o',0};
T65 g66 = {'f','o','o','b','a','r',0};
int b8_main() {

  { int x[3]={}; check(8, 0, (x[0])); }
  { int x[3]={}; check(9, 0, (x[1])); }
  { int x[3]={}; check(10, 0, (x[2])); }

  { struct {int a; int b;} x={}; check(39, 0, (x.a)); }
  { struct {int a; int b;} x={}; check(40, 0, (x.b)); }

  check(107, 4, (sizeof(g65)));
  check(108, 7, (sizeof(g66)));
  check(109, 0, (strcmp(g65.b, "oo")));
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
