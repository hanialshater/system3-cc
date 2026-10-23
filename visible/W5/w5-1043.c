void abort(void);
int 
foo (float a)
{
  return a * 4.9f;
}


int
b8_main (void)
{
  if (foo (10.0f) != 49)
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
