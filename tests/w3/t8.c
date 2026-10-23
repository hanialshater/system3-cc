int printf(const char *fmt, ...);
struct S { int a, b, c; };
struct S s1 = { b: 2, c: 3 };
int z;
int t1 = &z ? 1 : -1;
int t2 = !&z;
int t3 = &z && 1;
int arr[5] = { [2] 7, [4] 9 };
int main() {
  struct S l = { c: 5, a: 1 };
  int lt[2] = {&z ? 1 : -1, z ? -1 : 1};
  printf("%d %d %d %d %d %d %d %d %d %d %d\n", s1.a, s1.b, s1.c, t1, t2, t3, arr[2], arr[4], l.a, l.c, lt[0] + lt[1]);
  return 0;
}
