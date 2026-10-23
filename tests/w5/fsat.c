int printf(const char*,...);
int f1() { return (int)2147483648.0f; }
int f2() { return (int)(float)(2147483647); }
unsigned char f3() { return (unsigned char)300.0; }
int main() { printf("%d %d %d %d %lu\n", f1(), f2(), f3(), (int)-1e20, (unsigned long)1e30); return 0; }
