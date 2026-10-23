void abort (void);
void exit (int);

int b = 0;

void func (void) { }

void
testit(int x)
{
  if (x != 20)
    abort ();
}

int
b8_main()

{
  int a = 0;

  if (b)
    func();

  /* simplify_and_const_int would incorrectly omit the mask in
     the line below.  */
  testit ((a + 23) & 0xfffffffc);
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
