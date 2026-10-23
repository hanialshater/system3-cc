int printf(const char *fmt, ...);
int fails;
void check(int line, int got, int exp) { if (got != exp) { printf("FAIL line %d: got %d expected %d\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
int main() {
  { int x[]={1,2,[2]=3,4,[1]=5,6,7}; CHECK(sizeof(x)/sizeof(*x), 4); CHECK(x[0], 1); CHECK(x[1], 5); CHECK(x[2], 6); CHECK(x[3], 7); }
  { int x[5]={[3]=4, 5, [0]=1}; CHECK(x[0], 1); CHECK(x[3], 4); CHECK(x[4], 5); CHECK(x[1], 0); }
  { int x[2][3]={[0][1]=1, 2, [1][1]=4, 5}; CHECK(x[0][1], 1); CHECK(x[0][2], 2); CHECK(x[1][1], 4); CHECK(x[1][2], 5); CHECK(x[1][0], 0); }
  { int x[2][3]={[1]={1}, 2}; CHECK(x[1][0], 1); CHECK(x[1][1], 0); CHECK(x[0][0], 0); }
  { struct {int a,b,c;} x={.b=2, 3, .a=1}; CHECK(x.a, 1); CHECK(x.b, 2); CHECK(x.c, 3); }
  { struct {int a[3]; int b;} x={.a[1]=5, 6, 7}; CHECK(x.a[1], 5); CHECK(x.a[2], 6); CHECK(x.b, 7); CHECK(x.a[0], 0); }
  { struct {int a; int b[2]; int c;} x = {1, .b[1] = 3, 4}; CHECK(x.a, 1); CHECK(x.b[1], 3); CHECK(x.c, 4); CHECK(x.b[0], 0); }
  { int x[3][2] = {[1] = 5, 6}; CHECK(x[1][0], 5); CHECK(x[1][1], 6); CHECK(x[2][0], 0); }
  { int x[3][2] = {[1][1] = 5, 6}; CHECK(x[1][1], 5); CHECK(x[2][0], 6); }
  { struct S {int a, b;} arr[3] = {[2].b = 1, [0] = {2, 3}, 4}; CHECK(arr[0].a, 2); CHECK(arr[0].b, 3); CHECK(arr[1].a, 4); CHECK(arr[2].b, 1); CHECK(arr[1].b, 0); }
  { struct {struct {int a,b;} s[2]; int c;} x = {.s[1].b = 5, 6}; CHECK(x.s[1].b, 5); CHECK(x.c, 6); CHECK(x.s[0].a, 0); }
  { struct {int a; struct {int b, c;} s; int d;} x = {.s.c = 1, 2, .a = 3, 4, 5}; CHECK(x.d, 2); CHECK(x.a, 3); CHECK(x.s.b, 4); CHECK(x.s.c + 0, 5); }
  { int x[4] = {[1] = 1, [1] = 2}; CHECK(x[1], 2); }
  { struct {int a, b;} x = {.a = 1, .a = 2}; CHECK(x.a, 2); CHECK(x.b, 0); }
  { char s[6] = {[2] = 'x', 'y'}; CHECK(s[2], 'x'); CHECK(s[3], 'y'); CHECK(s[0], 0); }
  { char s[2][4] = {[1] = "ab", [0] = "cd"}; CHECK(s[1][1], 'b'); CHECK(s[0][0], 'c'); }
  { struct {char n[4]; int v;} t[] = {[1] = {"ab", 1}, "cd", 2}; CHECK(sizeof(t) / sizeof(t[0]), 3); CHECK(t[2].v, 2); CHECK(t[2].n[1], 'd'); }
  { struct {int a; int b;} x[] = {[0].b = 1, [0].a = 2, 3}; CHECK(x[0].a, 2); CHECK(x[0].b, 3); CHECK(sizeof(x), 8); }
  printf("fails=%d\n", fails);
  return fails;
}
