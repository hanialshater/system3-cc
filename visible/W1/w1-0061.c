void abort (void);
void exit (int);

int
b8_main(void)
{
  int i;
  for (i = 1; i < 100; i++)
    ;
  if (i == 100) 
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
