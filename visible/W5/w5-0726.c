void abort(void);
/* PR rtl-optimization/68321 */

int e = 1, u = 5, t2, t5, i, k;
int a[1], b, m;
char n, t;

int
fn1 (int p1)
{
  int g[1];
  for (;;)
    {
      if (p1 / 3)
	for (; t5;)
	  u || n;
      t2 = p1 & 4;
      if (b + 1)
	return 0;
      u = g[0];
    }
}

int
b8_main ()
{
  for (; e >= 0; e--)
    {
      char c;
      if (!m)
	c = t;
      fn1 (c);
    }
  
  if (a[t2] != 0) 
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
