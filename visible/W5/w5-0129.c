void abort(void);
/* This used to fail as we would convert f into just return (unsigned int)usVlanID
   which is wrong. */

int f(unsigned short usVlanID) ;
int f(unsigned short usVlanID)
{
  unsigned int uiVlanID = 0xffffffff;
  int i;
  if ((unsigned short)0xffff != usVlanID)
    uiVlanID = (unsigned int)usVlanID;
  return uiVlanID;
}

int b8_main(void)
{
  if (f(1) != 1)
    abort ();
  if (f(0xffff) != -1)
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
