extern void abort (void);
extern void exit (int);
extern int printf (const char *,...);

struct s { struct s *n; } *p;
struct s ss;
#define MAX     10
struct s sss[MAX];
int count = 0;

void sub( struct s *p, struct s **pp );
int look( struct s *p, struct s **pp );

int
b8_main(void)
{
    struct s *pp;
    struct s *next;
    int i;

    p = &ss;
    next = p;
    for ( i = 0; i < MAX; i++ ) {
        next->n = &sss[i];
        next = next->n;
    }
    next->n = 0;

    sub( p, &pp );
    if (count != MAX+2)
      abort ();

    exit( 0 );
  return 0;
}

void sub( struct s *p, struct s **pp )
{
   for ( ; look( p, pp ); ) {
        if ( p )
            p = p->n;
        else
            break;
   }
}

int look( struct s *p, struct s **pp )
{
    for ( ; p; p = p->n )
        ;
    *pp = p;
    count++;
    return( 1 );
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
