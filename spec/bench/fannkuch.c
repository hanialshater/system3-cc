/* W6 benchmark: small arrays, permutations (fannkuch-redux). */
#include <stdio.h>
#define N 10
#define START 0
int main(void) {
  int perm[16], perm1[16], count[16];
  int maxflips = 0, checksum = 0, permcount = 0, r = N;
  for (int i = 0; i < N; i++) perm1[i] = (i + START) % N;
  for (;;) {
    while (r != 1) { count[r - 1] = r; r--; }
    for (int i = 0; i < N; i++) perm[i] = perm1[i];
    int flips = 0, k;
    while ((k = perm[0]) != 0) {
      for (int i = 0, j = k; i < j; i++, j--) { int t = perm[i]; perm[i] = perm[j]; perm[j] = t; }
      flips++;
    }
    if (flips > maxflips) maxflips = flips;
    checksum += permcount % 2 == 0 ? flips : -flips;
    for (;;) {
      if (r == N) { printf("%d\nPfannkuchen(%d) = %d\n", checksum, N, maxflips); return 0; }
      int p0 = perm1[0];
      for (int i = 0; i < r; i++) perm1[i] = perm1[i + 1];
      perm1[r] = p0;
      count[r]--;
      if (count[r] > 0) break;
      r++;
    }
    permcount++;
  }
}
