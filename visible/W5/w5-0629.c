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

  check(25, 35, ((char)(double)35));
  check(26, 35, ((short)(double)35));
  check(27, 35, ((int)(double)35));
  check(28, 35, ((long)(double)35));
  check(29, 35, ((unsigned char)(double)35));
  check(30, 35, ((unsigned short)(double)35));
  check(31, 35, ((unsigned int)(double)35));
  check(32, 35, ((unsigned long)(double)35));
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
