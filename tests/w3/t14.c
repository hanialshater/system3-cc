struct foo { int a, b; } x = { 1, 2 };
struct foo arr[] = { { 3, 4 }, 5, 6 };
static_like = 7;
char *names[] = { "ab", "cde", 0 };
int tab[3][2] = { 1, 2, 3, 4, 5, 6 };
f (p) struct foo *p; { return p->a + p->b; }
g (s, n) char *s; register n; { return s[n]; }
h (a, len) int a[]; { int i, t = 0; for (i = 0; i < len; i++) t += a[i]; return t; }
struct foo mk (a, b) { struct foo r; r.a = a; r.b = b; return r; }
count (pp) char **pp; { int n = 0; while (*pp++) n++; return n; }
main ()
{
  struct foo y = { 10 }, z = mk (7, 8);
  int loc[] = { 1, 2, 3, 4 };
  if (f (&x) != 3) abort ();
  if (f (&arr[1]) != 11) abort ();
  if (static_like != 7) abort ();
  if (g ("hello", 1) != 'e') abort ();
  if (h (loc, 4) != 10) abort ();
  if (h (tab[1], 2) != 7) abort ();
  if (y.a != 10 || y.b != 0) abort ();
  if (z.b != 8) abort ();
  if (count (names) != 2) abort ();
  if (g (names[1], 2) != 'e') abort ();
  printf ("ok\n");
  exit (0);
}
