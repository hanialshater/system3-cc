void abort (void);
void exit (int);
void *memset (void *, int, unsigned long);
void *memcpy (void *, const void *, unsigned long);
int printf (const char *, ...);

void *volatile p;

int dealloc1 (void)
{
  int n = 0;
  if (0)
    {
    lab:;
    }
  int x[n % 1000 + 1];
  x[0] = 1;
  x[n % 1000] = 2;
  p = x;
  n++;
  if (n < 1000000)
    goto lab;
  return 0;
}

int dealloc2 (void)
{
  int n = 0;
 lab:;
  int x[n % 1000 + 1];
  x[0] = 1;
  x[n % 1000] = 2;
  p = x;
  n++;
  if (n < 1000000)
    goto lab;
  return 0;
}

int sub1 (int i, int j)
{
  typedef char c[i+2];
  int x[10], y[10];
  if (j == 2)
    {
      memcpy (x, y, 10 * sizeof (int));
      return sizeof (c);
    }
  else
    return sizeof (c) * 3;
}

int N = 1;
void bar (char a[2][N]) { a[1][0] = N; }

int sizes (int n)
{
  int k = sizeof (int[n++]);
  int m = sizeof (char[n][n]);
  return k * 1000 + m * 10 + n;
}

int walk (int n)
{
  int a[n][n + 1];
  int (*p)[n + 1] = a;
  for (int i = 0; i < n; i++)
    for (int j = 0; j <= n; j++)
      a[i][j] = i * 10 + j;
  p += 2;
  return (*p)[3] + (int)(sizeof (*p) / sizeof (int)) * 100 + (int)(p - a) * 10000;
}

int main (void)
{
  void *x;
  dealloc1 ();
  dealloc2 ();
  if (sub1 (20, 3) != 66) abort ();
  if (sub1 (20, 2) != 22) abort ();
  N = 4;
  x = alloca (2 * N);
  memset (x, 0, 2 * N);
  bar (x);
  if (N[(char *) x] != N) abort ();
  printf ("%d %d\n", sizes (3), walk (4));
  return 0;
}
