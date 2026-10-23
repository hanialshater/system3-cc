/* W6 benchmark: structs, malloc/free, recursion (binary-trees). */
#include <stdio.h>
#include <stdlib.h>
#define DEPTH 16
typedef struct Node { struct Node *left, *right; int val; } Node;
static Node *make(int d, int v) {
  Node *n = malloc(sizeof(Node));
  n->val = v;
  if (d > 0) { n->left = make(d - 1, 2 * v - 1); n->right = make(d - 1, 2 * v); } else { n->left = 0; n->right = 0; }
  return n;
}
static long check(Node *n) { return n->left ? n->val + check(n->left) - check(n->right) : n->val; }
static void release(Node *n) { if (n->left) { release(n->left); release(n->right); } free(n); }
int main(void) {
  Node *longlived = make(DEPTH, 0);
  long total = 0;
  for (int d = 4; d <= DEPTH; d += 2) {
    int iters = 1 << (DEPTH - d + 4);
    long c = 0;
    for (int i = 1; i <= iters; i++) { Node *t = make(d, i); c += check(t); release(t); }
    total += c;
    printf("%d trees of depth %d check %ld\n", iters, d, c);
  }
  printf("long lived %ld, total %ld\n", check(longlived), total);
  release(longlived);
  return 0;
}
