void abort(void);
struct S { char c1, c2, c3, c4; } __attribute__((aligned(4)));

static char bar (char **p) ;
static struct S foo (void) ;

int i;

static char
bar (char **p)
{
  i = 1;
  return 0;
}

static struct S
foo (void)
{
  struct S ret;
  char r, s, c1, c2;
  char *p = &r;

  s = bar (&p);
  if (s)
    c2 = *p;
  c1 = 0;

  ret.c1 = c1;
  ret.c2 = c2;
  return ret;
}

int b8_main (void)
{
  struct S s = foo ();
  if (s.c1 != 0)
    abort ();
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
