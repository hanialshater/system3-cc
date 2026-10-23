/* PR 16348: Make sure that condition-first false loops DTRT.  */

extern void abort ();

int b8_main()
{
  for (; 0 ;)
    {
      abort ();
    label:
      return 0;
    }
  goto label;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
