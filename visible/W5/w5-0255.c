/* Origin: PR c/92 from Simon Marlow <t-simonm@microsoft.com>, adapted
   to a testcase by Joseph Myers <jsm28@cam.ac.uk>.
*/

void abort (void);
void exit (int);

typedef struct { } empty;

typedef struct {
  int i;
  empty e;
  int i2;
} st;

st s = { .i = 0, .i2 = 1 };

extern void abort (void);

int
b8_main (void)
{
  if (s.i2 == 1)
    exit (0);
  else
    abort ();
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
