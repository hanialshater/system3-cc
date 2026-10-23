int a[] = {1, 2, 3, 4};

int
b8_main()
{
	if (sizeof(a) != 4*sizeof(int))
		return 1;
	
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
