int
foo(int a, int b)
{
	return 2 + a - b;
}

int
b8_main()
{
	return foo(1, 3);
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
