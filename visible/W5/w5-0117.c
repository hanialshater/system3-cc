void *memcpy(void *, const void *, unsigned long);
/* PR middle-end/29272 */

extern void abort (void);

struct S { struct S *s; } s;
struct T { struct T *t; } t;

static inline void
foo (void *s)
{
  struct T *p = s;
  memcpy (&p->t, &t.t, sizeof (t.t));
}

void *

bar (void *p, struct S *q)
{
  q->s = &s;
  foo (p);
  return q->s;
}

int
b8_main (void)
{
  t.t = &t;
  if (bar (&s, &s) != (void *) &t)
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
