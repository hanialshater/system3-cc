int x, x = 3, x;

int
b8_main()
{
	if (x != 3)
		return 0;

	x = 0;
	return x;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
