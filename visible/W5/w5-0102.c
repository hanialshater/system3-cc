extern void abort ();
extern void exit (int);

struct s
{
    int value;
    char *string;
};

int b8_main (void)
{
  int i;
  for (i = 0; i < 4; i++)
    {
      struct s *t = & (struct s) { 3, "hey there" };
      if (t->value != 3)
	abort();
      t->value = 4;
      if (t->value != 4)
	abort();
    }
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
