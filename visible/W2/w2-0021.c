int strlen(char *);

int
b8_main()
{
	char *p;
	
	p = "hello";
	return strlen(p) - 5;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
