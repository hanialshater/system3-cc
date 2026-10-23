void abort (void);
void exit (int);
int strcmp (const char *, const char *);
char *strcpy (char *, const char *);

struct foo { int a, b, c; };
void brother (int a, int b, int c) { if (a) abort (); }
void sister (struct foo f, int b, int c) { brother ((f.b == b), b, c); }

struct baz { int a, b, c; };
void foo2 (int a, int b, int c) { if (a != 4) abort (); }
void bar2 (struct baz x, int b, int c) { foo2 (x.b, b, c); }

struct s { char text[11]; int flag; } cell;
int check (struct s p) { if (p.flag != 99) return 1; return strcmp (p.text, "0123456789"); }

int a[2] = { 18, 6 };
char line[4] = { '1', '9', '9', '\0' };

struct { int a; int b[5]; } x;
int *y;
int foo3 (void) { return y - x.b; }

void foo4 (char *p1, char **p2) {}

int b8_main ()
{
  struct foo f = { 7, 8, 9 };
  sister (f, 1, 2);
  struct baz bz = { 3, 4, 5 };
  bar2 (bz, 1, 2);
  cell.flag = 99;
  strcpy (cell.text, "0123456789");
  if (check (cell)) abort ();
  int b = (-3 * a[0] -3 * a[1]) / 12;
  if (b != -6) abort ();
  char *ptr = line + 3;
  while ((*--ptr += 1) > '9') *ptr = '0';
  if (line[0] != '2' || line[1] != '0' || line[2] != '0') abort ();
  y = x.b;
  if (foo3 ()) abort ();
  char str[] = "foo { xx }";
  char *p = str + 5;
  foo4 (p, &p);
  while (*p && (*p == 13 || *p == 32)) p++;
  if (*p != 'x') abort ();
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
