/* W6 benchmark: recursive calls. */
#include <stdio.h>
#define N 38
#define SEED 7
int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }
int tak(int x, int y, int z) { return y < x ? tak(tak(x - 1, y, z), tak(y - 1, z, x), tak(z - 1, x, y)) : z; }
int main(void) {
  int r = fib(N);
  int t = tak(18 + SEED % 3, 12, 6);
  printf("fib(%d) = %d, tak = %d\n", N, r, t);
  return 0;
}
