int printf(const char *fmt, ...);
int strcmp(const char *a, const char *b);
int fails;
void check(int line, long got, long exp) { if (got != exp) { printf("FAIL line %d: got %ld expected %ld\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
extern int earr[];
int f1() { return earr[1]; }
int earr[] = {4, 5, 6};
int tent; int tent = 7;
int tent2; int tent2;
int tarr[];
struct a1 { char a1c; char *a1p; } a1 = { '4', "62" };
struct a2 { int a2i; char a2c[]; } a2 = { 1, "a" };
struct a3 { int i; char c[5]; } a3[] = {{1, "ab"}, 2, "cd"};
void *vp[] = {(void *)&("X"[0]), "Y" + 0};
struct S1 { int a; int b; } gs1 = { .a = 1, 2 };
int zero() { return 0; }
struct Z { int (*zf)(); } zs = { &zero };
typedef struct { int v; int sub[2]; } SS;
SS sarr[1] = {{1, {2, 3}}};
struct { int x; struct { int y, z; } nest; } snest = { 1, { 2, 3 } };
char *strs[] = {"one", "two", 0};
int len(char **s) { int n = 0; while (*s++) n++; return n; }
int main() {
  CHECK(f1(), 5); CHECK(sizeof(earr), 12); CHECK(tent, 7); CHECK(tent2, 0); CHECK(tarr[0], 0);
  CHECK(a1.a1c, '4'); CHECK(strcmp(a1.a1p, "62"), 0); CHECK(a2.a2c[0], 'a'); CHECK(a2.a2c[1], 0);
  CHECK(a3[1].i, 2); CHECK(strcmp(a3[1].c, "cd"), 0);
  CHECK(*(char *)vp[0], 'X'); CHECK(*(char *)vp[1], 'Y');
  CHECK(gs1.b, 2); CHECK(zs.zf(), 0); CHECK(sarr[0].sub[1], 3); CHECK(snest.nest.z, 3); CHECK(len(strs), 2);
  printf("fails=%d\n", fails);
  return fails;
}
