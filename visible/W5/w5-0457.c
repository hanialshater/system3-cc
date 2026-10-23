void abort (void);

typedef int new_int __attribute__ ((aligned(16)));
struct S { int x; };
 
int b8_main()
{
  if (sizeof(struct S) != sizeof(int))
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
