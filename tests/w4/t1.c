#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdbool.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>

int fails;
#define CHECK(a, b) do { if ((a) != (b)) { printf("FAIL line %d: %s\n", __LINE__, #a); fails++; } } while (0)
#define SQUARE(x) ((x) * (x))
#define MAX(a, b) ((a) > (b) ? (a) : (b))
#define CAT(a, b) a##b
#define XCAT(a, b) CAT(a, b)
#define STR(x) #x
#define XSTR(x) STR(x)
#define EMPTY
#define NOARGS() 42
#define VA(fmt, ...) sprintf(buf, fmt, __VA_ARGS__)
#define COUNT(...) count_args(__VA_ARGS__, 0)
#define LONG_MACRO(a, \
                   b) (a + \
                       b)
int RECUR = 1;
#define RECUR RECUR + 1
#define self(x) self(x)

struct S { char c; int i; long l; };

int count_args(int first, ...) {
  va_list ap; int n = 0; int v = first;
  va_start(ap, first);
  while (v != 0) { n++; v = va_arg(ap, int); }
  va_end(ap);
  return n;
}

long sum_mixed(int n, ...) {
  va_list ap; long s = 0;
  va_start(ap, n);
  for (int i = 0; i < n; i++) {
    int kind = va_arg(ap, int);
    if (kind == 0) s += va_arg(ap, int);
    else if (kind == 1) s += va_arg(ap, long);
    else if (kind == 2) s += *va_arg(ap, int *);
    else s += strlen(va_arg(ap, char *));
  }
  va_end(ap);
  return s;
}

int sum_twice(int n, ...) {
  va_list ap, ap2; int s = 0;
  va_start(ap, n);
  va_copy(ap2, ap);
  for (int i = 0; i < n; i++) s += va_arg(ap, int);
  for (int i = 0; i < n; i++) s += va_arg(ap2, int);
  va_end(ap); va_end(ap2);
  return s;
}

void myprintf(const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  vprintf(fmt, ap);
  va_end(ap);
}

int fmt_to(char *buf, size_t n, const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  int r = vsnprintf(buf, n, fmt, ap);
  va_end(ap);
  return r;
}

int sum_va_list(int n, va_list ap) { int s = 0; while (n--) s += va_arg(ap, int); return s; }
int sum_fwd(int n, ...) { va_list ap; va_start(ap, n); int r = sum_va_list(n, ap); va_end(ap); return r; }

int many_named(int a, int b, int c, int d, int e, int f, int g, ...) {
  va_list ap; va_start(ap, g); int s = a + b + c + d + e + f + g;
  s += va_arg(ap, int); s += va_arg(ap, int); va_end(ap); return s;
}

int main(void) {
  char buf[100];
  CHECK(SQUARE(3 + 1), 16);
  CHECK(MAX(3, 7), 7);
  int xy = 5; CHECK(CAT(x, y), 5);
  CHECK(strcmp(STR(hello world), "hello world"), 0);
  CHECK(strcmp(XSTR(SQUARE(2)), "((2) * (2))"), 0);
  CHECK(strcmp(STR( a  +   b ), "a + b"), 0);
  CHECK(strcmp(STR("q\n"), "\"q\\n\""), 0);
  CHECK(NOARGS(), 42);
  EMPTY CHECK(LONG_MACRO(1, 2), 3);
  VA("%d-%s", 7, "x"); CHECK(strcmp(buf, "7-x"), 0);
  CHECK(COUNT(5, 6, 7), 3);
  CHECK(RECUR, 2);
  CHECK(XCAT(1, 2), 12);
  CHECK(sum_mixed(4, 0, 10, 1, 100000000000L, 2, &xy, 3, "abcd"), 100000000019L);
  CHECK(sum_mixed(8, 0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 8), 36);
  CHECK(sum_twice(3, 1, 2, 3), 12);
  CHECK(sum_twice(10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 110);
  CHECK(sum_fwd(7, 1, 1, 1, 1, 1, 1, 1), 7);
  CHECK(many_named(1, 2, 3, 4, 5, 6, 7, 8, 9), 45);
  myprintf("%s %d %ld %c %5.2s|\n", "vp", -3, 1234567890123L, 'z', "abc");
  CHECK(fmt_to(buf, 5, "%d", 123456), 6); CHECK(strcmp(buf, "1234"), 0);
  CHECK(INT_MAX, 2147483647); CHECK(INT_MIN < 0, 1); CHECK(CHAR_BIT, 8); CHECK(sizeof(size_t), 8);
  CHECK(sizeof(int64_t), 8); CHECK(sizeof(uint8_t), 1); CHECK(INT32_MAX, 2147483647);
  CHECK(offsetof(struct S, l), 8); CHECK(offsetof(struct S, i), 4);
  bool t = true; CHECK(t, 1); CHECK(sizeof(bool), 1);
  int *np = NULL; CHECK(np == 0, 1); CHECK(EOF, -1);
  CHECK(__STDC__, 1); CHECK(__STDC_VERSION__, 201112L); CHECK(__x86_64__, 1); CHECK(__LP64__, 1);
  CHECK(__SIZEOF_POINTER__, 8); CHECK(__BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__, 1);
  __SIZE_TYPE__ sz = 3; CHECK(sizeof(sz), 8);
#if INT_MAX == 2147483647 && defined(EOF)
  CHECK(1, 1);
#else
  CHECK(0, 1);
#endif
#ifndef NULL
  CHECK(0, 1);
#elif SQUARE(2) == 4
  CHECK(1, 1);
#endif
#undef SQUARE
#ifdef SQUARE
  CHECK(0, 1);
#endif
  char *s = malloc(10); strcpy(s, "heap"); CHECK(strlen(s), 4); free(s);
  CHECK(abs(-5), 5); CHECK(atoi("123"), 123);
  printf("line %d\n", __LINE__);
  printf("fails=%d\n", fails);
  return fails;
}
