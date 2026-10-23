void abort(void);
/* PR tree-optimization/89536 */
/* Testcase by Zhendong Su <su@cs.ucdavis.edu> */

int a = 1;

int b8_main (void)
{
  a = ~(a && 1); 
  if (a < -1)
    a = ~a;
  
  if (!a)
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
