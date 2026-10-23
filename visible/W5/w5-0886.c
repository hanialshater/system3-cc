extern void abort (void);

struct container
{
  unsigned char data[1];
};

unsigned char space[6] = {1, 2, 3, 4, 5, 6};

int
foo (struct container *p)
{
  return p->data[4];
}

int
b8_main ()
{
  if (foo ((struct container *) space) != 5)
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
