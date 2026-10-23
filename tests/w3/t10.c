int printf(const char *fmt, ...);
int fails;
void check(int line, long got, long exp) { if (got != exp) { printf("FAIL line %d: got %ld expected %ld\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
struct B { int x; int y; };
struct A { int z; struct B b; };
struct A f() { struct B b = { 0, 1 }; struct A a = { 2, b }; return a; }
struct Big { int v[8]; };
struct Big mk(int s) { struct Big r; int i; for (i = 0; i < 8; i++) r.v[i] = s + i; return r; }
struct Big id(struct Big b) { return b; }
struct C { char c[3]; };
struct C mkc() { struct C c = {"ab"}; return c; }
int sumc(struct C a, struct C b, struct C c, struct C d, struct C e, struct C f, struct C g) { return a.c[0] + g.c[1]; }
struct B gb[3] = {{1, 2}, {3, 4}, {5, 6}};
struct B *pick(int i) { return &gb[i]; }
int main() {
  CHECK(f().z, 2); CHECK(f().b.y, 1); CHECK(mk(3).v[7], 10); CHECK(id(mk(1)).v[0], 1); CHECK(id(id(mk(2))).v[3], 5);
  struct Big a, b, c; a = b = c = mk(5); CHECK(a.v[2], 7); CHECK(c.v[7], 12);
  a = a; CHECK(a.v[0], 5);
  struct B s = 1 ? gb[1] : gb[2]; CHECK(s.y, 4);
  gb[0] = gb[2]; CHECK(gb[0].x, 5); *pick(1) = gb[0]; CHECK(gb[1].y, 6); CHECK(pick(2)->x, 5);
  struct C cc = mkc(); CHECK(cc.c[1], 'b'); CHECK(sumc(cc, cc, cc, cc, cc, cc, mkc()), 'a' + 'b');
  struct A arr[2] = {f(), {7, {8, 9}}}; CHECK(arr[0].b.y, 1); CHECK(arr[1].b.x, 8);
  struct B *p = gb; p++; (*p).x = 42; CHECK(gb[1].x, 42); CHECK((p - 1)->x, 5);
  printf("fails=%d\n", fails);
  return fails;
}
