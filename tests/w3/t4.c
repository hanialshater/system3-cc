int printf(const char *fmt, ...);
struct F { int n; int a[]; };
struct F gf = {3, {10, 20, 30}};
struct F gf0 = {1};
struct G { char c; short s[]; } gg = {'x', 1, 2};
int after = 99;
int main() {
  static struct F sf = {2, {7, 8}};
  printf("%d %d %d %d\n", gf.n, gf.a[0], gf.a[2], (int)sizeof(struct F));
  printf("%d %d %d %d\n", gf0.n, sf.a[1], gg.s[1], after);
  return 0;
}
