int strcmp(const char *, const char *);
char *strcpy(char *, const char *);
/* PR opt/8165.  */

extern void abort (void);

char buf[64];

int
b8_main (void)
{
  int i;

  strcpy (buf, "mystring");
  if (strcmp (buf, "mystring") != 0)
    abort ();

  for (i = 0; i < 16; ++i)
    {
      strcpy (buf + i, "mystring");
      if (strcmp (buf + i, "mystring") != 0)
	abort ();
    }

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
