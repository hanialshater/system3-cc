extern void abort (void);
struct a1 { char a1c; char *a1p; };
struct a1 a1 = { '4', "62" };
struct a2 { char a2c; char a2p[2]; };
struct a2 a2 = { 'v', "cq" };
struct a3 { char a3c; char a3p[]; };
struct a3 a3 = { 'o', "wx" };
struct a4 { char a4c; char a4p[]; };
struct a4 a4 = { '9', { 'e', 'b' } };

int b8_main()
{
  if (a1.a1c != '4') abort();
  if (a1.a1p[0] != '6') abort();
  if (a1.a1p[1] != '2') abort();
  if (a1.a1p[2] != '\0') abort();
  if (a2.a2c != 'v') abort();
  if (a2.a2p[0] != 'c') abort();
  if (a2.a2p[1] != 'q') abort();
  if (a3.a3c != 'o') abort();
  if (a3.a3p[0] != 'w') abort();
  if (a3.a3p[1] != 'x') abort();
  if (a4.a4c != '9') abort();
  if (a4.a4p[0] != 'e') abort();
  if (a4.a4p[1] != 'b') abort();
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
