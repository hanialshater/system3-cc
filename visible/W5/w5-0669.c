int printf(const char *fmt, ...);
void exit(int status);
void check(int id, int expected, int actual) {
  if (expected == actual) {
    printf("%d => %d\n", id, actual);
  } else {
    printf("%d => %d expected but got %d\n", id, expected, actual);
    exit(1);
  }
}
int b8_main() {

  check(9, 35, ((double)(char)35));
  check(10, 35, ((double)(short)35));
  check(11, 35, ((double)(int)35));
  check(12, 35, ((double)(long)35));
  check(13, 35, ((double)(unsigned char)35));
  check(14, 35, ((double)(unsigned short)35));
  check(15, 35, ((double)(unsigned int)35));
  check(16, 35, ((double)(unsigned long)35));
  return 0;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
