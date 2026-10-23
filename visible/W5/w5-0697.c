void abort (void);
void exit (int);

static void
compare (long long foo)
{
  if (foo < 4294967297LL)
    abort();
}
int b8_main(void)
{
  compare (8589934591LL);
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
