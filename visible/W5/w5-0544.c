void abort(void);
/* PR middle-end/90348 */

void 
set_one (unsigned char *ptr)
{
  *ptr = 1;
}

void 
check_zero (unsigned char const *in, unsigned int len)
{
  for (unsigned int i = 0; i < len; ++i)
    if (in[i] != 0)
      abort ();
}

static void
set_one_on_stack (void)
{
  unsigned char buf[1];
  set_one (buf);
}

int
b8_main ()
{
  for (int i = 0; i <= 4; ++i)
    {
      unsigned char in[4];
      for (int j = 0; j < i; ++j)
	{
	  in[j] = 0;
	  set_one_on_stack ();
	}
      check_zero (in, i);
    }
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
