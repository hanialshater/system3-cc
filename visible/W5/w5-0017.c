void exit (int);

int
b8_main (void)
{
  unsigned char z = 0;

  do ;
  while (--z > 0);
  exit (0);
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
