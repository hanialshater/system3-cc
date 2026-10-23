/* Used to be miscompiled at -O0 due to incorrect choice of sign extension
   vs. zero extension.   added to try to make it
   fail at higher optimization levels too.  */

extern void abort (void);

long long 
div (long long val)
{
  return val / 32768;
}

int b8_main (void)
{
  long long d1 = -990000000;
  long long d2 = div(d1);
  if (d2 != -30212)
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
