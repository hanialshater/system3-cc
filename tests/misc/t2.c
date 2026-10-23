#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
int fails;
#define CHECK(a,b) do { long _a = (long)(a), _b = (long)(b); if (_a != _b) { printf("FAIL line %d: got %ld expected %ld\n", __LINE__, _a, _b); fails++; } } while (0)
struct P { int x, y; };
struct Big { long a, b, c; };
struct Mix { double d; int i; };
struct F2 { float a, b; };
union U { int i; char c[4]; float f; };
typedef struct Node { int val; struct Node *next; } Node;
int garr[5] = {1, 2, 3};
char gstr[] = "hello";
char *gp = "world";
int *gip = &garr[2];
struct P gpt = { .y = 7, .x = 3 };
static int counter = 0;
enum Color { RED, GREEN = 5, BLUE };
struct P mkp(int x, int y) { struct P p; p.x = x; p.y = y; return p; }
struct Big mkbig(long a) { struct Big b = {a, a * 2, a * 3}; return b; }
struct Mix mkmix(double d, int i) { struct Mix m = {d, i}; return m; }
struct F2 mkf2(float a, float b) { struct F2 f = {a, b}; return f; }
int sump(struct P p) { return p.x + p.y; }
long sumbig(struct Big b) { return b.a + b.b + b.c; }
double summix(struct Mix m) { return m.d + m.i; }
int vsum(int n, ...) { va_list ap; va_start(ap, n); int s = 0; for (int i = 0; i < n; i++) s += va_arg(ap, int); va_end(ap); return s; }
double vdsum(int n, ...) { va_list ap; va_start(ap, n); double s = 0; for (int i = 0; i < n; i++) s += va_arg(ap, double); va_end(ap); return s; }
int cmp(const void *a, const void *b) { return *(const int *)a - *(const int *)b; }
int apply(int (*f)(int, int), int a, int b) { return f(a, b); }
int addi(int a, int b) { return a + b; }
double dmul(double a, float b) { return a * b; }
int sw(int x) { switch (x) { case 1: return 10; case 2: case 3: return 20; default: return -1; case 10: break; } return 100; }
void fmt(char *buf, const char *f, ...) { va_list ap; va_start(ap, f); vsprintf(buf, f, ap); va_end(ap); }
int main(void) {
  int a[10]; int i;
  for (i = 0; i < 10; i++) a[i] = i * i;
  CHECK(a[3], 9); CHECK(*(a + 4), 16); CHECK(3[a], 9);
  int *p = a; p += 2; CHECK(*p, 4); CHECK(p[1], 9); CHECK(p - a, 2);
  CHECK(*p++, 4); CHECK(*p, 9); CHECK(*--p, 4);
  CHECK(sizeof(a), 40); CHECK(sizeof(int), 4); CHECK(sizeof(long), 8); CHECK(sizeof(struct Big), 24); CHECK(sizeof(struct Mix), 16);
  CHECK(garr[0] + garr[1] + garr[2] + garr[3], 6); CHECK(strlen(gstr), 5); CHECK(gp[0], 'w'); CHECK(*gip, 3);
  CHECK(gpt.x, 3); CHECK(gpt.y, 7);
  char s[20]; strcpy(s, "abc"); strcat(s, "def"); CHECK(strcmp(s, "abcdef"), 0);
  char c = 200; CHECK(c, -56); unsigned char uc = 200; CHECK(uc, 200);
  short sh = 70000; CHECK(sh, 4464); unsigned u = -1; CHECK(u > 0, 1); CHECK(u, 4294967295L);
  long l = 1L << 40; CHECK(l >> 38, 4); CHECK(-1 >> 1, -1); CHECK(-1u >> 1, 2147483647);
  CHECK(0xff & 0x0f, 15); CHECK(0xf0 | 0x0f, 255); CHECK(0xff ^ 0x0f, 0xf0); CHECK(~0, -1);
  struct P pt = mkp(3, 4); CHECK(pt.x * pt.y, 12); CHECK(sump(pt), 7); CHECK(sump(mkp(5, 6)), 11);
  struct Big bg = mkbig(2); CHECK(bg.c, 6); CHECK(sumbig(bg), 12);
  struct Mix mx = mkmix(1.5, 2); CHECK(summix(mx) * 2, 7);
  struct F2 f2 = mkf2(1.25f, 2.5f); CHECK((f2.a + f2.b) * 4, 15);
  struct P *pp = &pt; pp->x = 10; CHECK(pt.x, 10);
  union U un; un.i = 0x41424344; CHECK(un.c[0], 0x44); CHECK(sizeof(union U), 4);
  Node n3 = {3, 0}, n2 = {2, &n3}, n1 = {1, &n2}; int tot = 0; for (Node *q = &n1; q; q = q->next) tot += q->val; CHECK(tot, 6);
  CHECK(vsum(4, 1, 2, 3, 4), 10); CHECK(vsum(8, 1, 2, 3, 4, 5, 6, 7, 8), 36);
  CHECK(vdsum(3, 1.5, 2.5, 3.0), 7); CHECK(vdsum(10, 1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0,1.0), 10);
  int arr[] = {5, 3, 9, 1, 7}; qsort(arr, 5, sizeof(int), cmp); CHECK(arr[0], 1); CHECK(arr[4], 9);
  CHECK(apply(addi, 2, 3), 5); int (*fp)(int, int) = addi; CHECK(fp(4, 5), 9); CHECK((*fp)(1, 1), 2);
  double d = 3.5; float f = 2.0f; CHECK(d * f, 7); CHECK(dmul(1.5, 4), 6); CHECK((int)(d + 0.6), 4);
  CHECK(d > f, 1); CHECK(d == 3.5, 1); CHECK((int)-2.7, -2); CHECK((long)1e18, 1000000000000000000L);
  CHECK(sw(1), 10); CHECK(sw(3), 20); CHECK(sw(7), -1); CHECK(sw(10), 100);
  CHECK(RED, 0); CHECK(GREEN, 5); CHECK(BLUE, 6);
  int m[3][4]; for (i = 0; i < 12; i++) m[i / 4][i % 4] = i; CHECK(m[2][3], 11); CHECK(sizeof(m[0]), 16);
  char buf[64]; sprintf(buf, "%d-%s-%.2f", 42, "x", 3.14159); CHECK(strcmp(buf, "42-x-3.14"), 0);
  fmt(buf, "%d %d", 7, 8); CHECK(strcmp(buf, "7 8"), 0);
  int *hp = malloc(100 * sizeof(int)); for (i = 0; i < 100; i++) hp[i] = i; CHECK(hp[99], 99); free(hp);
  i = 0; again: i++; if (i < 5) goto again; CHECK(i, 5);
  static int st = 5; st++; CHECK(st, 6);
  int x = ({ int y = 3; y * 2; }); CHECK(x, 6);
  struct P cl = (struct P){8, 9}; CHECK(cl.y, 9);
  int *cp = (int[]){1, 2, 3}; CHECK(cp[2], 3);
  long long ll = 123456789012LL; CHECK(ll % 1000, 12);
  unsigned long ul = 18446744073709551615UL; CHECK(ul / 3, 6148914691236517205L);
  CHECK((unsigned char)300, 44); CHECK((short)-1, -1); CHECK((unsigned short)-1, 65535);
  _Bool b = 5; CHECK(b, 1);
  double dd = ul; CHECK(dd > 1e19, 1);
  unsigned long ul2 = 1e19; CHECK(ul2 / 1000000000, 10000000000L);
  printf("%s %c %5d|%-3d|%x %lu\n", "str", 'Z', 42, 7, 255, sizeof(struct Node));
  printf("fails=%d\n", fails);
  return 0;
}
