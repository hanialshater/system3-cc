int printf(const char *fmt, ...);
int puts(const char *s);
int fails;
void check(int line, int got, int exp) { if (got != exp) { printf("FAIL line %d: got %d expected %d\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
int g1 = 2 + 3 * 4, g2 = -1, g3 = 'a', g4 = 0x10 - 1, g5 = (1 < 2) ? 10 : 20, g6 = !0;
char gc1 = 'z', gc2 = 200;
int f(p, n) char *p; int n; { return p[n]; }
int h(s) char *s; { int n = 0; while (*s++) n++; return n; }
int deref(int *p) { return *p; }
int main() {
  CHECK(g1, 14); CHECK(g2, -1); CHECK(g3, 97); CHECK(g4, 15); CHECK(g5, 10); CHECK(g6, 1); CHECK(gc1, 'z'); CHECK(gc2, -56);
  CHECK(f("hello", 1), 'e'); CHECK(h("four"), 4);
  char *s = "\xff\x80"; CHECK(s[0], -1); CHECK(s[1], -128);
  unsigned char *u = (unsigned char *)s; CHECK(u[0], 255);
  char a[4]; a[0] = -5; CHECK(a[0] * 2, -10);
  int x = 5; int *p = &x; CHECK(deref(p), 5); CHECK(deref(&x), 5);
  int *n = 0; CHECK(n == 0, 1); if (n) CHECK(1, 0); n = &x; if (!n) CHECK(1, 0);
  char *q = s; q++; CHECK(q - s, 1);
  int arr[2][3][4]; arr[1][2][3] = 77; CHECK(arr[1][2][3], 77); int *flat = &arr[0][0][0]; CHECK(flat[12 + 8 + 3], 77);
  puts("héllo");
  printf("%d\n", fails);
  return 0;
}
