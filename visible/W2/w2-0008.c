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

  { char x[3]; x[0]=0; x[1]=1; x[2]=2; char *y=x+1; check(15, 1, (y[0])); }
  { char x[3]; x[0]=0; x[1]=1; x[2]=2; char *y=x+1; check(16, 0, (y[-1])); }
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
