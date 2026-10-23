/* W6 benchmark: byte arrays and tight loops. */
#include <stdio.h>
#include <stdlib.h>
#define N 8000000
#define ROUNDS 4
int main(void) {
  char *comp = malloc(N + 1);
  long total = 0;
  for (int r = 0; r < ROUNDS; r++) {
    int count = 0;
    for (int i = 0; i <= N; i++) comp[i] = 0;
    for (int i = 2; (long)i * i <= N; i++)
      if (!comp[i])
        for (int j = i * i; j <= N; j += i) comp[j] = 1;
    for (int i = 2; i <= N; i++) if (!comp[i]) count++;
    total += count;
  }
  printf("primes up to %d: %ld\n", N, total / ROUNDS);
  free(comp);
  return 0;
}
