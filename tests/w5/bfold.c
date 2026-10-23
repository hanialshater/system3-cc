int printf(const char *, ...);
static int pc = __builtin_popcount(0xff);
int arr[__builtin_ctz(16)];
unsigned bs = __builtin_bswap32(0x12345678);
int main(void) {
  int v = 0x1234;
  switch (v) { case __builtin_bswap16(0x3412): printf("case ok\n"); break; default: printf("bad\n"); }
  printf("%d %d %x %d %d %d %d\n", pc, (int)sizeof(arr), bs, __builtin_clz(1), __builtin_ffsll(0), __builtin_parityl(7), __builtin_clrsb(-1));
  printf("%d %d\n", __builtin_constant_p(__builtin_popcount(3)), __builtin_clzll(1ULL << 40));
  return 0;
}
