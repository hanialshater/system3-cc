#include <stdio.h>

int fails;
#define CHECK(a, b) do { long long _a = (long long)(a), _b = (long long)(b); if (_a != _b) { printf("FAIL %d: %lld != %lld\n", __LINE__, _a, _b); fails++; } } while (0)

int arr3[3] = {7, 8, 9};
int (*getarr(void))[3] { return &arr3; }
int (*(*fpa)(void))[3] = getarr;
typedef void (*handler_t)(int);
int hits;
void h1(int x) { hits += x; }
handler_t table[2] = { h1, 0 };
int twice(int x) { return 2 * x; }
int callit(int (*f)(int), int v) { return f(v); }
int callabs(int (int), int);
int callabs(int f(int), int v) { return f(v) + 1; }
int knr(f, v) int (*f)(); int v; { return f(v) + 100; }
long addr_as_long(void *p) { return (long)p; }
struct S { int a; char b; } __attribute__((aligned(8)));
union U { int i; float f; unsigned char c[4]; };

int main(void) {
  CHECK((*getarr())[1], 8);
  CHECK((*fpa())[2], 9);
  table[0](5); CHECK(hits, 5);
  CHECK(callit(twice, 4), 8);
  CHECK(callabs(twice, 4), 9);
  CHECK(knr(twice, 1), 102);
  void *vp = (void *)twice;
  CHECK(((int (*)(int))vp)(21), 42);
  CHECK(sizeof(int (*)[3]), 8); CHECK(sizeof(int (*)(void)), 8); CHECK(sizeof(int[2][3]), 24);
  CHECK(sizeof(char *[4]), 32); CHECK(sizeof(int (*[4])(int)), 32);
  int x = 5; int *px = &x;
  CHECK(addr_as_long(px) == (long)px, 1);
  CHECK(*(int *)(long)px, 5);
  CHECK((char *)(px + 1) - (char *)px, 4);
  unsigned long ul = (unsigned long)px; CHECK(*(int *)ul, 5);
  CHECK((_Bool)px, 1); CHECK((_Bool)0.0, 0); CHECK((_Bool)-0.5, 1);
  union U u; u.f = 1.0f; CHECK(u.i, 0x3f800000); CHECK(u.c[3], 0x3f);
  union U u2 = { .c = {0, 0, 0x80, 0x3f} }; CHECK(u2.f == 1.0f, 1);
  CHECK(sizeof(struct S), 8);
  const char *names[] = { "a", "bb", "ccc" };
  const char **pp = names; CHECK(pp[2][1], 'c');
  char (*rows)[4] = (char (*)[4])"abcdefgh"; CHECK(rows[1][0], 'e');
  int m[2][3] = {{1, 2, 3}, {4, 5, 6}}; int (*mp)[3] = m; mp++; CHECK((*mp)[2], 6);
  int *ptrs[2] = { &m[0][1], &m[1][2] }; CHECK(*ptrs[1] - *ptrs[0], 4);
  printf("fails=%d\n", fails);
  return fails;
}
