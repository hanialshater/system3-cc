int printf(const char *fmt, ...);
int strcmp(const char *a, const char *b);
int fails;
void check(int line, int got, int exp) { if (got != exp) { printf("FAIL line %d: got %d expected %d\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
int g; int garr[10]; char gc; char gbuf[100]; int *gp; int gi = 42; int gneg = -7; char gch = 'x'; int *gpa[3]; int gm[3][4];
int sum(int *a, int n) { int s = 0; for (int i = 0; i < n; i++) s += a[i]; return s; }
int sum2(int a[], int n) { int s = 0; while (n--) s += *a++; return s; }
int msum(int m[][4], int r) { int s = 0; for (int i = 0; i < r; i++) for (int j = 0; j < 4; j++) s += m[i][j]; return s; }
int msum10(int m[3][4]) { return m[1][2]; }
void swap(int *a, int *b) { int t = *a; *a = *b; *b = t; }
int *find(int *a, int n, int v) { for (int i = 0; i < n; i++) if (a[i] == v) return &a[i]; return 0; }
int my_strlen(char *s) { char *p = s; while (*p) p++; return p - s; }
void my_strcpy(char *d, char *s) { while ((*d++ = *s++)) ; }
char *ret_str() { return "returned"; }
void set_ptr(int **pp, int *v) { *pp = v; }
char upper(char c) { return c >= 'a' && c <= 'z' ? c - 32 : c; }
int count_char(char *s, char c) { int n = 0; for (; *s; s++) if (*s == c) n++; return n; }
void inc_global() { g++; }
int main() {
  int a[5]; int i;
  for (i = 0; i < 5; i++) a[i] = i + 1;
  CHECK(sum(a, 5), 15); CHECK(sum2(a, 5), 15); CHECK(sum(a + 2, 3), 12); CHECK(sum(&a[1], 2), 5);
  int x = 1, y = 2; swap(&x, &y); CHECK(x, 2); CHECK(y, 1);
  int *f = find(a, 5, 4); CHECK(f - a, 3); CHECK(*f, 4); CHECK(find(a, 5, 9) == 0, 1); CHECK(!find(a, 5, 9), 1);
  CHECK(f > a, 1); CHECK(f < a, 0); CHECK(f >= &a[3], 1); CHECK(f != a, 1); CHECK(a + 3 == f, 1);
  int *p = a + 4; CHECK(*(p - 1), 4); CHECK(p[-2], 3); CHECK(*p--, 5); CHECK(*p, 4); p -= 2; CHECK(*p, 2);
  int **pp = &p; CHECK(**pp, 2); *pp = a; CHECK(*p, 1); set_ptr(&p, &a[4]); CHECK(*p, 5);
  int m[3][4]; for (i = 0; i < 3; i++) for (int j = 0; j < 4; j++) m[i][j] = i * 10 + j;
  CHECK(msum(m, 3), (0+1+2+3)+(40+6)+(80+6)); CHECK(msum10(m), 12); CHECK(*(*(m + 2) + 1), 21); CHECK(*m[1], 10);
  int (*rowp)[4] = m + 1; CHECK((*rowp)[3], 13); CHECK(rowp[1][0], 20);
  int *ptrs[3]; ptrs[0] = &x; ptrs[1] = &y; ptrs[2] = a; CHECK(*ptrs[0] + *ptrs[1] + ptrs[2][2], 6);
  char s[20]; s[0] = 'h'; s[1] = 'i'; s[2] = 0; CHECK(my_strlen(s), 2); CHECK(my_strlen("hello world"), 11);
  char *str = "abc" "def" "\n"; CHECK(my_strlen(str), 7); CHECK(str[6], 10); CHECK(str[3], 'd');
  my_strcpy(s, "copy"); CHECK(strcmp(s, "copy"), 0);
  CHECK(strcmp(ret_str(), "returned"), 0);
  CHECK("abc"[1], 'b'); CHECK(*"xyz", 'x');
  CHECK('\t', 9); CHECK('\\', 92); CHECK('\'', 39); CHECK('\"', 34); CHECK("\"q\""[1], 'q'); CHECK('\x7f', 127); CHECK('\377', -1); CHECK('\0', 0); CHECK("\101\x42"[1], 66);
  CHECK("a\0b"[2], 'b'); CHECK(my_strlen("a\0b"), 1); CHECK('\a', 7); CHECK('\r', 13); CHECK('\v', 11); CHECK('\f', 12); CHECK('\b', 8); CHECK('\?', 63);
  char c = 127; c++; CHECK(c, -128); c = -1; CHECK(c, -1); CHECK(c == 255, 0); CHECK(c + 1, 0);
  char c2 = 300; CHECK(c2, 44); char c3 = 'a'; CHECK(c3 + 1, 98); CHECK(upper('q'), 'Q'); CHECK(upper('Q'), 'Q');
  CHECK(count_char("banana", 'a'), 3);
  char carr[3][5]; carr[1][2] = 'z'; CHECK(carr[1][2], 'z'); char *cp = carr[1]; CHECK(cp[2], 'z');
  CHECK(g, 0); g = 5; inc_global(); CHECK(g, 6); CHECK(gi, 42); CHECK(gneg, -7); CHECK(gch, 'x');
  CHECK(garr[9], 0); garr[3] = 33; CHECK(garr[3], 33); gp = &garr[3]; CHECK(*gp, 33); gp[1] = 7; CHECK(garr[4], 7);
  gc = 'A'; CHECK(gc, 65); gbuf[0] = 'o'; gbuf[1] = 'k'; CHECK(my_strlen(gbuf), 2);
  gpa[1] = &gi; CHECK(*gpa[1], 42); gm[2][3] = 9; CHECK(gm[2][3], 9); CHECK(gm[1][1], 0);
  char *names[3]; names[0] = "zero"; names[1] = "one"; names[2] = "two"; CHECK(names[2][1], 'w');
  char **np = names; np++; CHECK(**np, 'o'); CHECK(*np[1], 't');
  int z = 0; int *zp = &z; *zp += 5; (*zp)++; ++*zp; CHECK(z, 7);
  void *vp = a; int *ip = vp; CHECK(ip[1], 2);
  CHECK(&a[4] - &a[0], 4); CHECK((char *)&a[1] - (char *)&a[0], 4);
  printf("%s|%c|%s\n", "str\tok", 'c', names[1]);
  printf("fails=%d\n", fails);
  return fails;
}
