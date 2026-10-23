int printf(const char *fmt, ...);
void exit(int status);
void check(int id, int expected, int actual) {
  if (expected == actual) {
    printf("%d => %d\n", id, actual);
  } else {
    printf("%d => %d expected but got %d\n", id, expected, actual);
    exit(1);
  }
}
int strcmp(const char *a, const char *b);
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
int include1 = 5;
int include2 = 7;
char *include1_filename = "test/include1.h";
int include1_line = 4;
char *main_filename1 = "test/macro.c";
int main_line1 = __LINE__;
#define LINE() __LINE__
int main_line2 = LINE();
#
#
int ret3(void) { return 3; }
int dbl(int x) { return x*x; }
int add2(int x, int y) {
  return x + y;
}
int add6(int a, int b, int c, int d, int e, int f) {
  return a + b + c + d + e + f;
}
int b8_main() {

#if 0

#if nested
#endif
#endif

  int m = 0;

#if 1
  m = 5;
#endif


#if 1
# if 0
#  if 1
    foo bar
#  endif
# endif
      m = 3;
#endif


#if 1-1
# if 1
# endif
# if 1
# else
# endif
# if 0
# else
# endif
  m = 2;
#else
# if 1
  m = 3;
# endif
#endif


#if 1
  m = 2;
#else
  m = 3;
#endif


#if 1
  m = 2;
#else
  m = 3;
#endif


#if 0
  m = 1;
#elif 0
  m = 2;
#elif 3+5
  m = 3;
#elif 1*5
  m = 4;
#endif


#if 1+5
  m = 1;
#elif 1
  m = 2;
#elif 3
  m = 2;
#endif


#if 0
  m = 1;
#elif 1
# if 1
  m = 2;
# else
  m = 3;
# endif
#else
  m = 5;
#endif


  int M1 = 5;

#undef M1
#define M1 3

#undef M1
#define M1 4


#undef M1
#define M1 3+4+


#undef M1
#define M1 3+4













  if (0);

#undef M
#define M 5
#if M
  m = 5;
#else
  m = 6;
#endif


#undef M
#define M 5
#if M-5
  m = 6;
#elif M
  m = 5;
#endif


  int M2 = 6;
#undef M2
#define M2 M2 + 3


#undef M3
#define M3 M2 + 3


  int M4 = 3;
#undef M4
#define M4 M5 * 5
#undef M5
#define M5 M4 + 2


#ifdef M6
  m = 5;
#else
  m = 3;
#endif


#undef M6
#define M6
#ifdef M6
  m = 5;
#else
  m = 3;
#endif


#ifndef M7
  m = 3;
#else
  m = 5;
#endif


#undef M7
#define M7
#ifndef M7
  m = 3;
#else
  m = 5;
#endif


#if 0
#ifdef NO_SUCH_MACRO
#endif
#ifndef NO_SUCH_MACRO
#endif
#else
#endif

#undef M7
#define M7() 1
  int M7 = 5;

#undef M7
#define M7 ()


#undef M8
#define M8(x,y) x+y


#undef M8
#define M8(x,y) x*y


#undef M8
#define M8(x,y) (x)*(y)


#undef M8
#define M8(x,y) x y


#undef M8
#define M8(x,y) x*y


#undef M8
#define M8(x,y) x*y


#undef dbl
#define dbl(x) M10(x) * x
#undef M10
#define M10(x) dbl(x) + 3


#undef M11
#define M11(x) #x


#undef paste
#define paste(x,y) x##y


#undef i
#define i 5

#undef i

#undef paste2
#define paste2(x) x##5


#undef paste3
#define paste3(x) 2##x


#undef paste4
#define paste4(x, y, z) x##y##z


#undef M12
#define M12
#if defined(M12)
  m = 3;
#else
  m = 4;
#endif


#undef M12
#define M12
#if defined M12
  m = 3;
#else
  m = 4;
#endif


#if defined(M12) - 1
  m = 3;
#else
  m = 4;
#endif


#if defined(NO_SUCH_MACRO)
  m = 3;
#else
  m = 4;
#endif


#if no_such_symbol == 0
  m = 5;
#else
  m = 6;
#endif
  check(57, 5, (m));

#undef STR
#define STR(x) #x
#undef M12
#define M12(x) STR(x)
#undef M13
#define M13(x) M12(foo.x)
  check(58, 0, (strcmp(M13(bar), "foo.bar")));

#undef M13
#define M13(x) M12(foo. x)
  check(59, 0, (strcmp(M13(bar), "foo. bar")));

#undef M12
#define M12 foo
#undef M13
#define M13(x) STR(x)
#undef M14
#define M14(x) M13(x.M12)
  check(60, 0, (strcmp(M14(bar), "bar.foo")));

#undef M14
#define M14(x) M13(x. M12)
  check(61, 0, (strcmp(M14(bar), "bar. foo")));

#undef foo
#undef foo
#define foo 3
  check(62, 3, (foo));

#undef foo
#undef foo
#define foo 4
  check(63, 4, (foo));

#undef foo
#undef foo
#define foo 3
  check(64, 3, (foo));

#undef foo
#undef foo
#define foo 4


#undef foo


#undef M14
#define M14(...) 3


#undef M14
#define M14(...) __VA_ARGS__


#undef M14
#define M14(...) add2(__VA_ARGS__)


#undef M14
#define M14(...) add6(1,2,__VA_ARGS__,6)


#undef M14
#define M14(x, ...) add6(1,2,x,__VA_ARGS__,6)


#undef M14
#define M14(x, ...) x


#undef CONCAT
#define CONCAT(x,y) x##y


#undef M31
#define M31(buf, fmt, ...) sprintf(buf, fmt, ## __VA_ARGS__)


#undef M31
#define M31(x, y) (1, ##x y)


  printf("OK\n");
  return 0;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
