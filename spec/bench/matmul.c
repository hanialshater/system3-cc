/* W6 benchmark: 2-D integer arrays, nested loops. */
#include <stdio.h>
#include <stdlib.h>
#define N 500
#define SEED 12345u
static unsigned int state = SEED;
static int rnd(void) { state = state * 1103515245u + 12345u; return (int)((state >> 16) & 0x7fff) % 100 - 50; }
int main(void) {
  int *a = malloc(sizeof(int) * N * N), *b = malloc(sizeof(int) * N * N), *c = malloc(sizeof(int) * N * N);
  for (int i = 0; i < N * N; i++) { a[i] = rnd(); b[i] = rnd(); }
  for (int i = 0; i < N; i++)
    for (int j = 0; j < N; j++) {
      int s = 0;
      for (int k = 0; k < N; k++) s += a[i * N + k] * b[k * N + j];
      c[i * N + j] = s;
    }
  long sum = 0;
  for (int i = 0; i < N * N; i++) sum += (long)c[i] * (i % 7 + 1);
  printf("matmul %d: %ld\n", N, sum);
  free(a); free(b); free(c);
  return 0;
}
