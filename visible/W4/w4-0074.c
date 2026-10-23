int foo(void);
int foo(void);
#define FOO 0

int
b8_main()
{
	return FOO;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
