typedef int x;

int
b8_main()
{
	x v;
	v = 0;
	return v;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
