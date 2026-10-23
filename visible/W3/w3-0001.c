int
b8_main()
{
	struct T { int x; };
	{
		struct T s;
		s.x = 0;
		return s.x;
	}
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
