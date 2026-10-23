int
b8_main()
{
	int arr[2];

	arr[0] = 1;
	arr[1] = 2;

	return arr[0] + arr[1] - 3;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
