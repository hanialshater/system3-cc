/* PR target/39240 */

extern void abort (void);


static int foo1 (int x)
{
  return x;
}


unsigned int bar1 (int x)
{
  return foo1 (x + 6);
}

volatile unsigned long l1 = (unsigned int) -4;


static short int foo2 (int x)
{
  return x;
}


unsigned short int bar2 (int x)
{
  return foo2 (x + 6);
}

volatile unsigned long l2 = (unsigned short int) -4;


static signed char foo3 (int x)
{
  return x;
}


unsigned char bar3 (int x)
{
  return foo3 (x + 6);
}

volatile unsigned long l3 = (unsigned char) -4;


static unsigned int foo4 (int x)
{
  return x;
}


int bar4 (int x)
{
  return foo4 (x + 6);
}

volatile unsigned long l4 = (int) -4;


static unsigned short int foo5 (int x)
{
  return x;
}


short int bar5 (int x)
{
  return foo5 (x + 6);
}

volatile unsigned long l5 = (short int) -4;


static unsigned char foo6 (int x)
{
  return x;
}


signed char bar6 (int x)
{
  return foo6 (x + 6);
}

volatile unsigned long l6 = (signed char) -4;

int
b8_main (void)
{
  if (bar1 (-10) != l1)
    abort ();
  if (bar2 (-10) != l2)
    abort ();
  if (bar3 (-10) != l3)
    abort ();
  if (bar4 (-10) != l4)
    abort ();
  if (bar5 (-10) != l5)
    abort ();
  if (bar6 (-10) != l6)
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
