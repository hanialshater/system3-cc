int printf(const char *fmt, ...);
struct In { int x, y; };
struct Out { struct In i; int z; };
struct In gv = {1, 2};
int main() {
  struct In v = {5, 6};
  struct Out o[2] = { v, 3, v, 4 };
  struct Out p = { v, 7 };
  struct Out q[2] = { 1, 2, 3, v, 8 };
  printf("%d %d %d %d %d %d\n", o[0].i.y, o[0].z, o[1].i.x, o[1].z, p.i.y, p.z);
  printf("%d %d %d %d\n", q[0].i.x, q[0].z, q[1].i.y, q[1].z);
  return 0;
}
