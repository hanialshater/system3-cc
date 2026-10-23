void abort (void);

static unsigned char magic[] = "\235";
static unsigned char value = '\235';

int b8_main()
{
  if (value != magic[0])
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
