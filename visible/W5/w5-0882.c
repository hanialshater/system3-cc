void abort(void);
/* PR tree-optimization/114965 */

static void
foo (const char *x)
{

  char a = '0';
  while (1)
    {
      switch (*x)
	{
	case '_':
	case '+':
	  a = *x;
	  x++;
	  continue;
	default:
	  break;
	}
      break;
    }
  if (a == '0' || a == '+')
    abort ();
}

int
b8_main ()
{
  foo ("_");
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
