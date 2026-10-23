unsigned long strlen(const char *);
/* PR optimization/8613 */
/* Contributed by Glen Nakamura */

extern void abort (void);
 
int b8_main (void)
{
  char buf[16] = "1234567890";
  char *p = buf;

  *p++ = (char) strlen (buf);

  if ((buf[0] != 10) || (p - buf != 1))
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
