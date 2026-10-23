#include <stdio.h>
#include <string.h>

struct P { int x, y; };

int sum2d(int n, int m, int a[n][m]) {
  int s = 0;
  for (int i = 0; i < n; i++)
    for (int j = 0; j < m; j++) s += a[i][j];
  return s;
}

int side(int *p) { return (*p)++; }

int f(int n, int b[n++], int c) { return n * 100 + c; }

int main(void) {
  char *first = 0, *last = 0;
  for (int i = 0; i < 100000; i++) {
    int n = 100 + i % 7;
    char buf[n];
    memset(buf, 'a', n);
    if (i == 0) first = buf;
    last = buf;
    if (i % 3 == 0) continue;
    if (i == 99999) break;
  }
  printf("stack stable: %d\n", first == last || (first - last < 1000 && last - first < 1000));
  int n = 3, m = 4;
  int a[n][m];
  for (int i = 0; i < n; i++)
    for (int j = 0; j < m; j++) a[i][j] = i * m + j;
  printf("%d %d %d\n", sum2d(n, m, a), (int)sizeof(a), (int)sizeof(a[0]));
  struct P ps[n];
  ps[2].y = 7;
  printf("%d %d\n", ps[2].y, (int)sizeof ps);
  int k = 5;
  int v[side(&k)];
  printf("%d %d\n", k, (int)(sizeof v / sizeof v[0]));
  int dummy[1];
  printf("%d\n", f(4, dummy, 7));
  for (int i = 1; i <= 3; i++) {
    int w[i];
    printf("w%d ", (int)sizeof w);
  }
  printf("\n");
  return 0;
}
