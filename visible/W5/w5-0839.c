extern void abort(void);
extern void exit(int);

void
foo (char *bp, unsigned n)
{
  register char c;
  register char *ep = bp + n;
  register char *sp;

  while (bp < ep)
    {
      sp = bp + 3;
      c = *sp;
      *sp = *bp;
      *bp++ = c;
      sp = bp + 1;
      c = *sp;
      *sp = *bp;
      *bp++ = c;
      bp += 2;
    }
}

int b8_main(void)
{
  int one = 1;

  if (sizeof(int) != 4 * sizeof(char))
    exit(0);

  foo((char *)&one, sizeof(one));
  foo((char *)&one, sizeof(one));

  if (one != 1)
    abort();

  exit(0);
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
