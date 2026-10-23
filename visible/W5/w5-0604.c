typedef unsigned char uint8_t;
uint8_t foo[1][0];
extern void abort (void);
int b8_main()
{
  if (sizeof (foo) != 0)
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
