void abort (void);
void exit (int);

typedef unsigned long long u64;
unsigned long foo = 0;
u64 f() ;

u64 f() {
  return ((u64)40) + ((u64) 24) * (int)(foo - 1);
}

int b8_main ()
{
  if (f () != 16)
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
