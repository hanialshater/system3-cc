int printf(const char *, ...);
int i; double d; float f; long l; long long ll; char c; signed char sc; unsigned char uc;
struct S1 { int a; } s1; struct S2 { int a; } s2;
typedef __typeof__ (i) myint;
enum E { A, B } e;
int main(void) {
  int a[5]; int *b; const int ci = 1; char *cp; const char *ccp;
  printf("%d%d%d%d%d%d%d%d%d\n",
    __builtin_types_compatible_p(int, int), __builtin_types_compatible_p(typeof(i), int),
    __builtin_types_compatible_p(typeof(d), double), __builtin_types_compatible_p(typeof(a), int[5]),
    __builtin_types_compatible_p(int[5], int[]), __builtin_types_compatible_p(myint, int),
    __builtin_types_compatible_p(typeof(ci), int), __builtin_types_compatible_p(typeof(b), int *),
    __builtin_types_compatible_p(typeof(e), unsigned int));
  printf("%d%d%d%d%d%d%d%d%d%d\n",
    __builtin_types_compatible_p(char *, int), __builtin_types_compatible_p(char *, const char *),
    __builtin_types_compatible_p(typeof(i), typeof(d)), __builtin_types_compatible_p(typeof(s1), typeof(s2)),
    __builtin_types_compatible_p(char, int), __builtin_types_compatible_p(char *, char **),
    __builtin_types_compatible_p(char, signed char), __builtin_types_compatible_p(char, unsigned char),
    __builtin_types_compatible_p(long, long long), __builtin_types_compatible_p(typeof(cp), typeof(ccp)));
  printf("%d%d%d\n", __builtin_types_compatible_p(typeof(sc), signed char), __builtin_types_compatible_p(typeof(ll), long long), __builtin_types_compatible_p(typeof(l), long int));
  return 0;
}
