#define ARGS(...) __VA_ARGS__

int
none()
{
	return 0;
}

int
one(int a)
{
	if (a != 1)
		return 1;
	
	return 0;
}

int
two(int a, int b)
{
	if (a != 1)
		return 1;
	if (b != 2)
		return 1;
	
	return 0;
}

int
three(int a, int b, int c)
{
	if (a != 1)
		return 1;
	if (b != 2)
		return 1;
	if (c != 3)
		return 1;
	
	return 0;
}

int
b8_main()
{
	if (none(ARGS()))
		return 1;
	if (one(ARGS(1)))
		return 2;
	if (two(ARGS(1, 2)))
		return 3;
	if (three(ARGS(1, 2, 3)))
		return 4;
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
