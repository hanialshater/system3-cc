extern void abort (void);
extern void exit (int);

int
foo (int x)
{
  if (x == -2 || -x - 100 >= 0)
    abort ();
  return 0;
}
           
int
b8_main ()
{
  foo (-3);
  foo (-99);
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
