int printf(const char *fmt, ...);
int strcmp(const char *a, const char *b);
int fails;
void check(int line, long got, long exp) { if (got != exp) { printf("FAIL line %d: got %ld expected %ld\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
struct A { int a; union { int b; float c; }; int d; };
struct S1 { char c; int i; char d; };
struct S2 { char c; double d; };
struct S3 { char a, b, c; };
struct S4 { short s; char c; };
struct S5 { long l; char c; };
union U1 { char c[5]; int i; };
struct Nest { struct { int x, y; } pt; int arr[3]; char name[8]; };
struct P { int x, y; };
typedef struct P Pt;
typedef int Arr3[3];
typedef int (*Fn)(int);
int dbl(int x) { return 2 * x; }
int g = 10; int garr[5] = {1, 2, 3, 4, 5};
int *gp = &g; int *gp2 = &garr[2]; int *gp3 = garr + 4; char *gs = "glob"; char *gss[] = {"a", "bc", "def"};
struct P gpts[] = {{1, 2}, {3, 4}, [3] = {7, 8}};
struct Nest gn = {{1, 2}, {3, 4, 5}, "nm"};
struct Nest gn2 = {1, 2, 3, 4, 5, 'a', 'b'};
int gm[2][3] = {1, 2, 3, 4, 5, 6};
int gm2[2][3] = {{1}, {4, 5}};
char gstr[] = "hello"; char gstr2[10] = "hi"; char gstr3[3] = "abc";
void *gself = &gself;
Fn gfn = dbl;
struct { int a; char *s; } ganon = {5, "anon"};
long gl = 1L << 40; double gd = 1.5; float gf = 2.5f;
int *gnull = 0;
enum E { E0, E1, E5 = 5, E6, EN = -3, EM };
enum E ge = E6;
struct Mixed { double d; int i; float f; };
struct Mixed mkm(double d, int i, float f) { struct Mixed m = {d, i, f}; return m; }
struct S3 mk3(char a) { struct S3 s = {a, a + 1, a + 2}; return s; }
struct Big { int a[10]; };
struct Big mkbig(int v) { struct Big b; for (int i = 0; i < 10; i++) b.a[i] = v + i; return b; }
int sumbig(struct Big b) { int s = 0; for (int i = 0; i < 10; i++) s += b.a[i]; return s; }
int many(int a, int b, int c, int d, int e, int f, struct P p, int g, struct Big bb) { return a + b + c + d + e + f + p.x * 100 + p.y * 1000 + g + bb.a[9]; }
double fmix(struct Mixed m) { return m.d + m.i + m.f; }
int main() {
  struct A x = {1, .b = 4, 5}; CHECK(x.a, 1); CHECK(x.b, 4); CHECK(x.d, 5);
  struct A y = {1, 2, 3}; CHECK(y.b, 2); CHECK(y.d, 3);
  struct A z = {.c = 1.5f}; CHECK(z.c * 2, 3); CHECK(z.a, 0);
  CHECK(sizeof(struct A), 12); CHECK(sizeof(struct S1), 12); CHECK(sizeof(struct S2), 16); CHECK(sizeof(struct S3), 3);
  CHECK(sizeof(struct S4), 4); CHECK(sizeof(struct S5), 16); CHECK(sizeof(union U1), 8); CHECK(_Alignof(struct S2), 8);
  CHECK(_Alignof(char), 1); CHECK(_Alignof(union U1), 4); CHECK(sizeof(struct Nest), 28);
  int n = sizeof(n); CHECK(n, 4); void *p = &p; CHECK(p == &p, 1);
  CHECK(sizeof x, 12); CHECK(sizeof(x.a), 4); CHECK(sizeof(int[5]), 20); CHECK(sizeof(char *), 8); CHECK(sizeof(Arr3), 12);
  CHECK(sizeof "abc", 4); CHECK(sizeof(gpts) / sizeof(gpts[0]), 4); CHECK(sizeof(1L), 8); CHECK(sizeof('a'), 4); CHECK(sizeof(gstr), 6);
  CHECK(*gp, 10); CHECK(*gp2, 3); CHECK(*gp3, 5); CHECK(strcmp(gs, "glob"), 0); CHECK(strcmp(gss[2], "def"), 0);
  CHECK(gpts[1].y, 4); CHECK(gpts[2].x, 0); CHECK(gpts[3].y, 8);
  CHECK(gn.pt.y, 2); CHECK(gn.arr[2], 5); CHECK(strcmp(gn.name, "nm"), 0);
  CHECK(gn2.pt.x, 1); CHECK(gn2.arr[2], 5); CHECK(gn2.name[1], 'b'); CHECK(gn2.name[2], 0);
  CHECK(gm[1][0], 4); CHECK(gm2[0][1], 0); CHECK(gm2[1][1], 5);
  CHECK(strcmp(gstr, "hello"), 0); CHECK(gstr2[1], 'i'); CHECK(gstr2[5], 0); CHECK(gstr3[2], 'c');
  CHECK(gself == &gself, 1); CHECK(gfn(4), 8); CHECK(ganon.a, 5); CHECK(strcmp(ganon.s, "anon"), 0);
  CHECK(gl >> 40, 1); CHECK(gd * 2, 3); CHECK(gf * 2, 5); CHECK(gnull == 0, 1);
  CHECK(E1, 1); CHECK(E6, 6); CHECK(EM, -2); CHECK(ge, 6); CHECK(sizeof(enum E), 4);
  int la[] = {1, 2, 3, [6] = 7, 8}; CHECK(sizeof(la), 32); CHECK(la[7], 8); CHECK(la[4], 0);
  int lb[5] = {[2] = 3, [0] = 1, 2}; CHECK(lb[0], 1); CHECK(lb[1], 2); CHECK(lb[2], 3); CHECK(lb[4], 0);
  char ls[] = "abc"; CHECK(sizeof(ls), 4); char ls2[10] = "xy"; CHECK(ls2[9], 0); CHECK(ls2[1], 'y');
  char ls3[] = {'a', 'b', 0}; CHECK(strcmp(ls3, "ab"), 0); char ls4[] = {"brace"}; CHECK(sizeof ls4, 6);
  struct P lp[3] = {{1, 2}, [2].y = 9}; CHECK(lp[2].y, 9); CHECK(lp[1].x, 0); CHECK(lp[0].y, 2);
  struct Nest ln = {.arr = {1, 2}, .pt.y = 5, .name = "zz"}; CHECK(ln.arr[1], 2); CHECK(ln.pt.y, 5); CHECK(ln.name[1], 'z'); CHECK(ln.arr[2], 0);
  int lm[2][2][2] = {1, 2, 3, 4, 5, 6, 7, 8}; CHECK(lm[1][0][1], 6);
  union U1 lu = {{'a', 'b'}}; CHECK(lu.c[1], 'b'); union U1 lu2 = {.i = 0x01020304}; CHECK(lu2.c[0], 4);
  Pt pt = {3, 4}; Pt pt2; pt2 = pt; pt2.x = 9; CHECK(pt.x, 3); CHECK(pt2.x, 9); CHECK(pt2.y, 4);
  Pt *ptp = &pt; ptp->y = 40; CHECK(pt.y, 40); CHECK((*ptp).y, 40);
  Arr3 a3 = {7, 8, 9}; CHECK(a3[2], 9); Fn f = dbl; CHECK(f(21), 42);
  { typedef char Pt; Pt c = 'q'; CHECK(sizeof(Pt), 1); CHECK(c, 'q'); } Pt again = {1, 1}; CHECK(sizeof again, 8);
  { int Pt = 5; CHECK(Pt, 5); }
  struct Mixed m = mkm(1.5, 2, 0.25f); CHECK(fmix(m) * 4, 15); CHECK(m.i, 2);
  struct S3 s3 = mk3('a'); CHECK(s3.c, 'c');
  struct Big bg = mkbig(1); CHECK(bg.a[9], 10); CHECK(sumbig(bg), 55); CHECK(sumbig(mkbig(0)), 45);
  struct P pp = {1, 2}; CHECK(many(1, 2, 3, 4, 5, 6, pp, 7, bg), 21 + 100 + 2000 + 7 + 10);
  struct P arrp[4]; for (int i = 0; i < 4; i++) { arrp[i].x = i; arrp[i].y = i * i; } struct P *q = arrp + 2; CHECK(q->y, 4); q++; CHECK(q->x, 3); CHECK(q - arrp, 3);
  struct L { int v; struct L *next; } l3 = {3, 0}, l2 = {2, &l3}, l1 = {1, &l2}; CHECK(l1.next->next->v, 3);
  struct { int a, b; } anon1 = {1, 2}; CHECK(anon1.b, 2);
  struct Nest n1 = gn; n1.arr[0] = 100; CHECK(gn.arr[0], 3); CHECK(n1.arr[0], 100); CHECK(n1.name[0], 'n');
  int cnt = 0; struct P *ap[2] = {&pp, &pt}; CHECK(ap[1]->x, 3);
  int zz[10] = {0}; for (int i = 0; i < 10; i++) cnt += zz[i]; CHECK(cnt, 0);
  struct P sp = {.y = 1}; CHECK(sp.x, 0);
  int scal = {5}; CHECK(scal, 5);
  struct P cond = 1 ? pp : pt; CHECK(cond.y, 2);
  printf("fails=%d\n", fails);
  return fails;
}
