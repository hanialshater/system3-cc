void abort(void);
/* PR tree-optimization/86492 */

union U
{
  unsigned int r;
  struct S
  {
    unsigned int a:12;
    unsigned int b:4;
    unsigned int c:16;
  } f;
};

 unsigned int
foo (unsigned int x)
{
  union U u;
  u.r = 0;
  u.f.c = x;
  u.f.b = 0xe;
  return u.r;
}

int
b8_main ()
{
  union U u;
  if (__CHAR_BIT__ * __SIZEOF_INT__ != 32 || sizeof (u.r) != sizeof (u.f))
    return 0;
  u.r = foo (0x72);
  if (u.f.a != 0 || u.f.b != 0xe || u.f.c != 0x72)
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
