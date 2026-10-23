void abort (void);
void exit (int);

static unsigned int expr_hash_table_size = 1;

int
b8_main ()
{
  int del = 1;
  unsigned int i = 0;

  if (i < expr_hash_table_size && del)
    exit (0);
  abort ();
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
