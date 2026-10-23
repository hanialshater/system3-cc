int printf(const char *fmt, ...);
int fails;
void check(int line, long got, long exp) { if (got != exp) { printf("FAIL line %d: got %ld expected %ld\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
struct P { int x, y; };
typedef int F(int);
F twice;
int twice(int v) { return v * 2; }
typedef struct { int k; char tag[4]; } Anon;
union UU { struct { short lo, hi; }; int all; };
int arrsz(int a[10]) { return sizeof(a); }
struct In { int a; struct P p; int b[2]; };
struct In gin[] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
enum { K1 = 3, K2 = K1 * 2, K3 };
int ga[K3];
int main() {
  struct P arr[] = {1, 2, 3, 4, 5}; CHECK(sizeof(arr), 24); CHECK(arr[2].x, 5); CHECK(arr[2].y, 0);
  F *fp = twice; CHECK(fp(5), 10); CHECK(twice(3), 6);
  Anon an = {7, "ab"}; CHECK(an.tag[1], 'b'); CHECK(sizeof(Anon), 8);
  union UU u; u.all = 0x00020001; CHECK(u.lo, 1); CHECK(u.hi, 2); union UU u2 = {.hi = 3}; CHECK(u2.all, 3 << 16);
  CHECK(arrsz(0), 8);
  int i = 0; CHECK(sizeof(i++), 4); CHECK(i, 0);
  CHECK(sizeof(int) * 2, 8); CHECK(sizeof i + 1, 5); CHECK(sizeof(struct P[3]), 24);
  CHECK(gin[0].p.y, 3); CHECK(gin[0].b[1], 5); CHECK(gin[1].a, 6); CHECK(gin[1].b[1], 10); CHECK(sizeof(gin), 40);
  CHECK(K3, 7); CHECK(sizeof(ga), 28);
  int sw = K2; switch (sw) { case K1: CHECK(1, 0); break; case K2: sw = 100; break; } CHECK(sw, 100);
  struct In li = {1, {2, 3}, {4}}; CHECK(li.p.y, 3); CHECK(li.b[0], 4); CHECK(li.b[1], 0);
  struct P *pp = &arr[1]; struct P cpy = *pp; CHECK(cpy.x, 3);
  struct P arr2[2]; arr2[0] = arr[2]; arr2[1] = *pp; CHECK(arr2[0].x + arr2[1].y, 9);
  char *strs[] = {"x", "yy", 0}; int c = 0; while (strs[c]) c++; CHECK(c, 2);
  int m[][2] = {{1, 2}, {3, 4}, {5}}; CHECK(sizeof(m), 24); CHECK(m[2][0], 5);
  printf("fails=%d\n", fails);
  return fails;
}
