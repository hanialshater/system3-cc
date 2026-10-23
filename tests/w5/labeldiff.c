int printf(const char *fmt, ...);
int run(int i) {
  static const int offs[] = { &&l0 - &&l0, &&l1 - &&l0, &&l2 - &&l0 };
  goto *(&&l0 + offs[i]);
 l0: return 10;
 l1: return 11;
 l2: return 12;
}
int main() { printf("%d %d %d\n", run(0), run(1), run(2)); return 0; }
