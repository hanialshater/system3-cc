int printf(const char *fmt, ...);
int fails;
void check(int line, int got, int exp) { if (got != exp) { printf("FAIL line %d: got %d expected %d\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
int add8(int a, int b, int c, int d, int e, int f, int g, int h) { return a*1+b*2+c*3+d*4+e*5+f*6+g*7+h*8; }
int knr(a, b) int b; int a; { return a - b; }
int knr2(a) { return a * 2; }
implicit_int(x) { return x + 1; }
int fact(int n) { return n <= 1 ? 1 : n * fact(n - 1); }
int is_even(int n);
int is_odd(int n) { return n == 0 ? 0 : is_even(n - 1); }
int is_even(int n) { return n == 0 ? 1 : is_odd(n - 1); }
int side;
int bump(int v) { side = side * 10 + v; return v; }
void noret(int x) { if (x) return; side = 99; }
int ack(int m, int n) { if (m == 0) return n + 1; if (n == 0) return ack(m - 1, 1); return ack(m - 1, ack(m, n - 1)); }
int main() {
  int a = 1, b, c = 3;
  b = 2;
  CHECK(a + b * c, 7);
  CHECK((a + b) * c, 9);
  CHECK(7 / 2, 3); CHECK(-7 / 2, -3); CHECK(7 / -2, -3); CHECK(-7 % 3, -1); CHECK(7 % -3, 1);
  CHECK(2147483647 + 1, -2147483647 - 1);
  CHECK(65536 * 65536, 0);
  CHECK(-(-2147483647 - 1), -2147483647 - 1);
  CHECK(0x7fffffff, 2147483647); CHECK(017, 15); CHECK(0, 0); CHECK(00, 0);
  CHECK(1 < 2, 1); CHECK(2 < 1, 0); CHECK(1 <= 1, 1); CHECK(2 >= 3, 0); CHECK(3 > 2, 1); CHECK(-1 < 0, 1);
  CHECK(!0, 1); CHECK(!5, 0); CHECK(!!5, 1); CHECK(-+-3, 3);
  side = 0; CHECK(bump(1) && bump(0) && bump(2), 0); CHECK(side, 10);
  side = 0; CHECK(bump(0) || bump(3) || bump(4), 1); CHECK(side, 3);
  side = 0; CHECK((bump(1), bump(2), bump(3)), 3); CHECK(side, 123);
  CHECK(1 ? 2 : 3, 2); CHECK(0 ? 2 : 3, 3); CHECK(0 ? 1 : 0 ? 2 : 3, 3);
  side = 0; CHECK(1 ? bump(5) : bump(6), 5); CHECK(side, 5);
  a = b = c = 5; CHECK(a + b + c, 15);
  a = 10; a += 5; CHECK(a, 15); a -= 3; CHECK(a, 12); a *= 2; CHECK(a, 24); a /= 5; CHECK(a, 4); a %= 3; CHECK(a, 1);
  a = 5; b = a++; CHECK(b, 5); CHECK(a, 6); b = ++a; CHECK(b, 7); b = a--; CHECK(b, 7); CHECK(a, 6); b = --a; CHECK(b, 5);
  a = 3; b = (a += 2) * 2; CHECK(b, 10);
  CHECK(add8(1,2,3,4,5,6,7,8), 1+4+9+16+25+36+49+64);
  CHECK(add8(add8(1,0,0,0,0,0,0,0),2,3,4,5,6,7,add8(0,0,0,0,0,0,0,1)), 1+4+9+16+25+36+49+64);
  CHECK(knr(10, 3), 7); CHECK(knr2(21), 42); CHECK(implicit_int(4), 5);
  CHECK(fact(10), 3628800); CHECK(is_even(10), 1); CHECK(is_odd(7), 1);
  CHECK(ack(2, 3), 9);
  side = 0; noret(1); CHECK(side, 0); noret(0); CHECK(side, 99);
  { int a = 100; { int a = 200; CHECK(a, 200); } CHECK(a, 100); }
  CHECK(a, 5);
  int i, j, s = 0;
  for (i = 0; i < 10; i++) { if (i == 3) continue; if (i == 8) break; s += i; }
  CHECK(s, 0+1+2+4+5+6+7);
  s = 0; for (int k = 0, m = 10; k < m; k++, m--) s++; CHECK(s, 5);
  s = 0; i = 0; while (1) { i++; if (i > 100) break; if (i % 2) continue; s += i; } CHECK(s, 2550);
  s = 0; i = 0; do { s += i; i++; } while (i < 5); CHECK(s, 10);
  s = 0; i = 10; do s++; while (--i); CHECK(s, 10);
  s = 0; for (;;) { if (++s == 7) break; } CHECK(s, 7);
  s = 0; for (i = 0; i < 5; i++) for (j = 0; j < 5; j++) { if (j == 2) break; s++; } CHECK(s, 10);
  s = 0; i = 0; do { i++; if (i == 2) continue; s += i; } while (i < 5); CHECK(s, 1+3+4+5);
  if (0) ; else s = 1; CHECK(s, 1);
  if (1) if (0) s = 2; else s = 3; CHECK(s, 3);
  ;;;
  {}
  CHECK(-2147483647 - 1 < 0, 1);
  CHECK((-2147483647 - 1) / 2, -1073741824);
  CHECK(1000000 * 3000 / 1000, 1000000 * 3000 / 1000);
  CHECK('a', 97); CHECK('\n', 10); CHECK('\0', 0); CHECK('\x41', 65); CHECK('\101', 65);
  printf("%d %d %d %d %d %d %d %d %d\n", 1, 2, 3, 4, 5, 6, 7, 8, 9);
  printf("fails=%d\n", fails);
  return fails;
}
