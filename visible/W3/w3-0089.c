int a;

int
b8_main (void)
{
  char e[2] = { 0, 0 }, f = 0;
  if (a == 131072)
    f = e[a];
  return f;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
