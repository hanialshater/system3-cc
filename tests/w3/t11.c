int printf(const char *fmt, ...);
int fails;
void check(int line, int got, int exp) { if (got != exp) { printf("FAIL line %d: got %d expected %d\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
int f2(int c, int b) { return c - b; }
int (*f1(int a, int b))(int c, int b) { if (a != b) return f2; return 0; }
int x, x = 3, x;
int f(int a), g(int a), aa;
int f(int a) { return a; }
int g(int a) { return a * 2; }
struct S1 { int a; int b; };
struct S2 { int a; int *p; struct S1 s; int arr[2]; };
int gv = 10;
struct S2 v = {1, &gv, {4, 5}, {6}};
struct S2 v2 = {.p = &gv, .s.b = 7, 8};
int *pa[] = {&v.a, &v.s.b, v.arr + 1, &x};
int (*fps[])(int) = {f, g};
struct { int (*fn)(int); int k; } tbl[] = {{f, 1}, {g, 2}, {0, 0}};
int main() {
  int (*(*p)(int a, int b))(int c, int d) = f1;
  CHECK((*p(0, 2))(2, 2), 0);
  CHECK(p(1, 2)(5, 3), 2);
  CHECK(x, 3); CHECK(f(1) - g(1), -1); CHECK(aa, 0);
  CHECK(v.s.b, 5); CHECK(*v.p, 10); CHECK(v.arr[0] + v.arr[1], 6);
  CHECK(v2.s.b, 7); CHECK(v2.arr[0], 8); CHECK(v2.a, 0);
  CHECK(*pa[0] + *pa[1] + *pa[2] + *pa[3], 1 + 5 + 0 + 3);
  CHECK(fps[1](4), 8);
  int s = 0; for (int i = 0; tbl[i].fn; i++) s += tbl[i].fn(tbl[i].k); CHECK(s, 1 + 4);
  int a = 1, b = a + 1, c[2] = {a, b}, *q = &c[1]; CHECK(*q, 2);
  struct S1 l1 = {1, 2}, l2 = l1, *lp = &l2; CHECK(lp->b, 2);
  printf("fails=%d\n", fails);
  return fails;
}
