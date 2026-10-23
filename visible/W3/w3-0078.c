int strcmp(const char *, const char *);
char *strcpy(char *, const char *);
void abort (void);
void exit (int);

struct s {
  char text[11];
  int flag;
} cell;

int
check (struct s p)
{
  if (p.flag != 99)
    return 1;
  return strcmp (p.text, "0123456789");
}

int
b8_main (void)
{
  cell.flag = 99;
  strcpy (cell.text, "0123456789");

  if (check (cell))
    abort();
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
