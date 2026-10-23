int
b8_main()
{
	short s = 1;
	long l = 1;

	s -= l;
	return s;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
