void abort (void);
void exit (int);

double x = 0x1.fp1;
int b8_main()
{
  if (x !=  3.875)
    abort ();
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
