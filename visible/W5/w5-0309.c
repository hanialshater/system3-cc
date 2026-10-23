typedef struct s s;

struct s {
	struct s1 {
		int s;
		struct s2 {
			int s;
		} s1;
	} s;
} s2;

#define s s

int
b8_main(void)
{
#undef s
	goto s;
	struct s s;
		{
			int s;
			return s;
		}
	return s.s.s + s.s.s1.s;
	s:
		{
			return 0;
		}
	return 1;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
