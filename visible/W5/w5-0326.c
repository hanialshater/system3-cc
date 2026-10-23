void abort (void);
void exit (int);

int
b8_main(void)
{
int j=1081;
struct
{
signed int m:11;
}l;
if((l.m=j)==j)abort();
exit(0);
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
