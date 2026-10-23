void abort(void);
void exit(int);

typedef unsigned long long uint64;
const uint64 bigconst = 1ULL << 34;

int a = 1;

static
uint64 getmask(void)
{
    if (a)
      return bigconst;
    else
      return 0;
}

int
b8_main(void)
{
    uint64 f = getmask();
    if (sizeof (long long) == 8
	&& f != bigconst) abort ();
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
