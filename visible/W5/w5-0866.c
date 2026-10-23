int
b8_main()
{
	int x;
	
	x = 1;
	x = x & 3;
	return x - 1;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
