void exit (int);

int
sub (int i, int array[i++])
{
  return i;
}

int
b8_main(void)
{
  int array[10];
  exit (sub (10, array) != 11);
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
