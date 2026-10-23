extern void abort ();

void 
bar (unsigned long long i)
{
  if (i)
    abort ();
}

static void 
foo (unsigned long long *r)
{
  int i;

  for (i = 0; ; i++)
    if (*r & ((unsigned long long)1 << (63 - i)))
      break;

  bar (i);
}

void 
do_test (unsigned long long *r)
{
  int i;

  for (i = 0; i < 2; ++i)
    foo (r);
}

int b8_main()
{
  unsigned long long r = 0x8000000000000001ull;

  do_test (&r);
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
