#include <stdio.h>
#include <stdlib.h>

int trace[10], nt;
int f(int v) { trace[nt++] = v; return v; }

int order(void) {
  int a[f(2)][f(3)];
  return (int)sizeof(a) / (int)sizeof(int);
}

int arr_of_vla(int n) {
  int a[3][n];
  for (int i = 0; i < 3; i++)
    for (int j = 0; j < n; j++) a[i][j] = i * n + j;
  return a[2][n - 1] * 100 + (int)(sizeof(a) / sizeof(int));
}

int ptr_to_vla(int n, int m) {
  int (*p)[m] = malloc(sizeof(*p) * n);
  m = 1000;   /* the type's size was fixed at the declaration */
  for (int i = 0; i < n; i++)
    for (int j = 0; j < 3; j++) p[i][j] = i * 10 + j;
  int r = p[2][1] * 100 + (int)(sizeof(*p) / sizeof(int));
  free(p);
  return r;
}

void params(int n, int m, int a[n][m]) {
  printf("%d %d %d\n", (int)sizeof(a[0]), a[1][2], a[n - 1][m - 1]);
}

int main(void) {
  int o = order();
  printf("%d %d %d\n", o, trace[0], trace[1]);
  printf("%d\n", arr_of_vla(4));
  printf("%d\n", ptr_to_vla(4, 3));
  int x[2][3] = {{1, 2, 3}, {4, 5, 6}};
  params(2, 3, x);
  int n = 5;
  typedef int row[n];
  n = 7;
  row r;
  printf("%d\n", (int)sizeof(r));
  return 0;
}
