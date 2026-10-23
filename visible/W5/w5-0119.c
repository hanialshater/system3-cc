void abort (void);
void exit (int);

typedef struct
{
  char hours, day, month;
  short year;
} T;

T g (void)
{
  T now;

  now.hours = 1;
  now.day = 2;
  now.month = 3;
  now.year = 4;
  return now;
}

T f (void)
{
  T virk;

  virk = g ();
  return virk;
}

int
b8_main (void)
{
  if (f ().hours != 1 || f ().day != 2 || f ().month != 3 || f ().year != 4)
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
