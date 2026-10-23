#define M(x) x
#define A(a,b) a(b)

int
b8_main(void)
{
	char *a = A(M,"hi");

	return (a[1] == 'i') ? 0 : 1;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
