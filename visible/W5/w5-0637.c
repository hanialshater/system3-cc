/* { dg-skip-if "small alignment" { pdp11-*-* } } */

void abort (void);
void exit (int);

struct s1
{
  int __attribute__ ((aligned (8))) a;
};

struct
{
  char c;
  struct s1 m;
} v;

int
b8_main (void)
{
  if ((int)&v.m & 7)
    abort ();
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
