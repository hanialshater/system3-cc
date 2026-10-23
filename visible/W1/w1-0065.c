extern void exit (int);
extern void abort ();

void test(int x, int y)
{
	int c;

	if (x == 1) abort();
	if (y == 1) abort();

	c = x / y;

	if (c != 1) abort();
}

int b8_main()
{
	test(2, 2);
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
