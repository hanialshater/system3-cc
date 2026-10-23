void abort (void);

char q;
void *a[2];

void foo (char *p)
{
  int i;
  for (i = 0; i < 2; i++)
    a[i] += p - &q;
}

int b8_main (void)
{
  int i;
  foo (&q);
  for (i = 0; i < 2; i ++)
    if (a[i])
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
