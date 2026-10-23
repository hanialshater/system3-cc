/* W6 benchmark: recursion, pointers, swaps (quicksort + insertion sort). */
#include <stdio.h>
#include <stdlib.h>
#define N 2000000
#define SEED 99u
static unsigned long state = SEED;
static int rnd(void) { state = state * 6364136223846793005ul + 1442695040888963407ul; return (int)(state >> 33); }
static void isort(int *a, int n) {
  for (int i = 1; i < n; i++) { int v = a[i], j = i - 1; while (j >= 0 && a[j] > v) { a[j + 1] = a[j]; j--; } a[j + 1] = v; }
}
static void qs(int *a, int n) {
  while (n > 16) {
    int p = a[n / 2], i = 0, j = n - 1;
    while (i <= j) {
      while (a[i] < p) i++;
      while (a[j] > p) j--;
      if (i <= j) { int t = a[i]; a[i] = a[j]; a[j] = t; i++; j--; }
    }
    if (j + 1 < n - i) { qs(a, j + 1); a += i; n -= i; } else { qs(a + i, n - i); n = j + 1; }
  }
  isort(a, n);
}
int main(void) {
  int *a = malloc(sizeof(int) * N);
  for (int i = 0; i < N; i++) a[i] = rnd();
  qs(a, N);
  long h = 0;
  for (int i = 1; i < N; i++) { if (a[i - 1] > a[i]) { printf("unsorted at %d\n", i); return 1; } h = h * 31 + (a[i] & 1023); h %= 1000000007; }
  printf("sorted %d: %ld\n", N, h);
  free(a);
  return 0;
}
