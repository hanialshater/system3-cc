int
f2(int c, int b)
{
	return c - b;
}

int (*
f1(int a, int b))(int c, int b)
{
	if (a != b)
		return f2;
	return 0;
}

int
b8_main()
{
	int (* (*p)(int a, int b))(int c, int d) = f1;


	return (*(*p)(0, 2))(2, 2);
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
