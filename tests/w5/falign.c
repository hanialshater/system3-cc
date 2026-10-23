int printf(const char *, ...);
void func(void) __attribute__((aligned(256)));
void func(void) {}
int x __attribute__((aligned(16)));
char y;
int main() {
  int loc __attribute__((aligned(32)));
  printf("%d %d %d %d %d\n", ((long)func & 0xFF) == 0, (int)__alignof__(func), (int)__alignof__(x), (int)__alignof__(y), (int)__alignof__(loc));
  return 0;
}
