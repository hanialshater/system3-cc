int printf(const char *fmt, ...);
int strcmp(const char *a, const char *b);
int fails;
void check(int line, long got, long exp) { if (got != exp) { printf("FAIL line %d: got %ld expected %ld\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
char g3 = 3; short g4 = 4; int g5 = 5; long g6 = 6;
int g9[3] = {0, 1, 2};
struct {char a; int b;} g11[2] = {{1, 2}, {3, 4}};
struct {int a[2];} g12[2] = {{{1, 2}}};
union { int a; char b[8]; } g13[2] = {0x01020304, 0x05060708};
char g17[] = "foobar"; char g18[10] = "foobar"; char g19[3] = "foobar";
char *g20 = g17 + 0; char *g21 = g17 + 3; char *g22 = &g17 - 3;
char *g23[] = {g17 + 0, g17 + 3, g17 - 3};
int g24 = 3; int *g25 = &g24;
int g26[3] = {1, 2, 3}; int *g27 = g26 + 1; int *g28 = &g11[1].a;
long g29 = (long)(long)g26;
struct { struct { int a[3]; } a; } g30 = {{{1, 2, 3}}};
int *g31 = g30.a.a;
struct {int a[2];} g40[2] = {{1, 2}, 3, 4};
struct {int a[2];} g41[2] = {1, 2, 3, 4};
char g43[][4] = {'f', 'o', 'o', 0, 'b', 'a', 'r', 0};
char *g44 = {"foo"};
union { int a; char b[4]; } g50 = {.b[0] = 4};
union { int a; char b[4]; } g51[2] = {0, 1, 2};
struct {int a[2];} g60 = {.a[1] = 5};
char g33[2][4] = {"abc", "def"};
typedef int T[];
T gt1 = {1, 2}; T gt2 = {1, 2, 3};
struct { int a, b, c; } g70 = {1, .c = 3};
int g71[] = {[2] = 3, [0] = 1};
struct S { int x; int *p; } g72 = {1, &g24};
struct S g73[] = {{2, &g5}, {3, g26}};
long g74 = (long)&g24 - (long)&g24;
const char *const g75 = "cc";
int g76 = sizeof(g9) / sizeof(*g9);
char g77[2][3][4] = {{"ab", "cd"}, {"ef"}};
int ret3(void) { return 3; }
int (*g78)(void) = ret3;
int (*g79[2])(void) = {ret3, &ret3};
int main() {
  CHECK(g3, 3); CHECK(g4, 4); CHECK(g5, 5); CHECK(g6, 6); CHECK(g9[2], 2);
  CHECK(g11[1].a, 3); CHECK(g11[1].b, 4); CHECK(g12[0].a[1], 2); CHECK(g12[1].a[0], 0);
  CHECK(g13[0].b[0], 4); CHECK(g13[1].b[3], 5); CHECK(sizeof(g13), 16);
  CHECK(strcmp(g17, "foobar"), 0); CHECK(sizeof(g17), 7); CHECK(g18[6], 0); CHECK(sizeof(g19), 3); CHECK(g19[2], 'o');
  CHECK(strcmp(g20, "foobar"), 0); CHECK(strcmp(g21, "bar"), 0); CHECK(strcmp(g23[1], "bar"), 0); CHECK(g23[2] + 3 == g17, 1);
  CHECK(*g25, 3); CHECK(*g27, 2); CHECK(*g28, 3); CHECK(g29 == (long)g26, 1); CHECK(g31[2], 3);
  CHECK(g40[1].a[0], 3); CHECK(g40[1].a[1], 4); CHECK(g41[1].a[1], 4);
  CHECK(sizeof(g43), 8); CHECK(strcmp(g43[1], "bar"), 0); CHECK(strcmp(g44, "foo"), 0);
  CHECK(g50.a, 4); CHECK(g51[1].a, 1); CHECK(sizeof(g51), 8); CHECK(g60.a[1], 5); CHECK(g60.a[0], 0);
  CHECK(strcmp(g33[1], "def"), 0);
  CHECK(sizeof(gt1), 8); CHECK(sizeof(gt2), 12);
  CHECK(g70.b, 0); CHECK(g70.c, 3); CHECK(sizeof(g71), 12); CHECK(g71[2], 3);
  CHECK(*g72.p, 3); CHECK(*g73[0].p, 5); CHECK(g73[1].p[2], 3); CHECK(g74, 0); CHECK(g75[1], 'c'); CHECK(g76, 3);
  CHECK(strcmp(g77[0][1], "cd"), 0); CHECK(strcmp(g77[1][0], "ef"), 0); CHECK(g77[1][1][0], 0);
  CHECK(g78(), 3); CHECK(g79[1](), 3);
  { char x[3] = "foo"; CHECK(x[2], 'o'); }
  { char x[2][4] = {"abc", "def"}; CHECK(x[1][2], 'f'); }
  { struct {int a[2];} x[2] = {{1, 2}, 3, 4}; CHECK(x[1].a[1], 4); }
  { union { int a; char b[4]; } x = {0x01020304}; CHECK(x.b[0], 4); }
  { union { int a; char b[4]; } x = {.b[1] = 2}; CHECK(x.a, 0x200); }
  { T x = {1, 2}; T y = {1, 2, 3}; CHECK(sizeof(x), 8); CHECK(sizeof(y), 12); }
  { struct {int a, b;} x[] = {[1].b = 1, [0].a = 2}; CHECK(x[1].b, 1); CHECK(x[0].a, 2); CHECK(sizeof(x), 16); }
  { int x[2][3] = {[0][1] = 1, [1] = {4}}; CHECK(x[0][1], 1); CHECK(x[1][0], 4); CHECK(x[0][2], 0); }
  { int x[5] = {1, 2, [1] = 7}; CHECK(x[1], 7); CHECK(x[2], 0); }
  { int a = 3; int x[] = {a, a + 1, a * 3}; CHECK(x[2], 9); }
  { char *x[] = {"a", "bb"}; CHECK(x[1][1], 'b'); }
  { struct {char a; int b;} x = {1, 2}; CHECK(x.b, 2); }
  { int x[4] = {[1 ... 2] = 5}; CHECK(x[0] + x[1] + x[2] + x[3], 10); }
  { struct { int a; struct { int b, c; } s; } x = {1, .s.c = 3}; CHECK(x.s.c, 3); CHECK(x.s.b, 0); }
  { struct { int a; struct { int b, c; } s; int d; } x = {.s = {1, 2}, 4}; CHECK(x.d, 4); }
  { struct { int a; struct { int b, c; } s; int d; } x = {.s.b = 1, 2, 4}; CHECK(x.s.c, 2); CHECK(x.d, 4); }
  printf("fails=%d\n", fails);
  return fails;
}
