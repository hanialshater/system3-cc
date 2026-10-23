int printf(const char *fmt, ...);
int strcmp(const char *a, const char *b);
int fails;
void check(int line, long got, long exp) { if (got != exp) { printf("FAIL line %d: got %ld expected %ld\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
struct R { char name[8]; int v; } grs[] = {"ab", 1, "cd", 2, {"ef"}, 3};
double gd[] = {1, 2.5, -3};
float gfl[3] = {1.5f};
short gsh[] = {-1, 300, -32768};
unsigned char guc[] = {255, 256, -1};
struct { double d; float f; char c; } gmix = {1.25, 2, 'q'};
char gs2[][6] = {"one", "two", "three"};
char *gnames[] = {[1] = "b", [0] = "a"};
struct R *grp = &grs[1];
int *gip = &grs[2].v;
char *gcp = grs[1].name + 1;
long gdiff = &gsh[2] - &gsh[0];
int gx = (3 > 2) + (1 ? 5 : 6) * 2 - -1;
unsigned gu = -1; long gneg = -2147483648; long glong = 0x123456789L;
int counter;
int next() { return ++counter; }
int main() {
  CHECK(strcmp(grs[1].name, "cd"), 0); CHECK(grs[2].v, 0); CHECK(sizeof(grs), 48); CHECK(grs[3].name[0], 3);
  CHECK(gd[1] * 2, 5); CHECK(gd[2], -3); CHECK(gfl[0] * 2, 3); CHECK(gfl[1], 0);
  CHECK(gsh[0], -1); CHECK(gsh[1], 300); CHECK(gsh[2], -32768); CHECK(guc[0], 255); CHECK(guc[1], 0); CHECK(guc[2], 255);
  CHECK(gmix.d * 4, 5); CHECK(gmix.f, 2); CHECK(gmix.c, 'q'); CHECK(strcmp(gs2[2], "three"), 0);
  CHECK(strcmp(gnames[1], "b"), 0); CHECK(grp->v, 2); CHECK(*gip, 0); CHECK(*gcp, 'd'); CHECK(gdiff, 2);
  CHECK(gx, 12); CHECK(gu, 4294967295L); CHECK(gneg, -2147483648L); CHECK(glong, 0x123456789L);
  struct R lrs[] = {"x", 1, "yz", 2}; CHECK(strcmp(lrs[1].name, "yz"), 0); CHECK(lrs[1].v, 2);
  int seq[3] = {next(), next(), next()}; CHECK(seq[0], 1); CHECK(seq[2], 3);
  static int sarr[] = {4, 5, 6}; static int *sp = &sarr[1]; CHECK(*sp, 5);
  static char sstr[] = "stat"; CHECK(sizeof(sstr), 5);
  double ld[] = {1, 2}; CHECK(ld[1], 2); float lf = 3; CHECK(lf, 3);
  char *lp = "abc" + 1; CHECK(*lp, 'b');
  int big[1000] = {[999] = 1}; CHECK(big[999] + big[0] + big[500], 1);
  struct { int a; char s[4]; int b; } ls = {1, "xyz", 2}; CHECK(ls.b, 2); CHECK(ls.s[2], 'z');
  printf("fails=%d\n", fails);
  return fails;
}
