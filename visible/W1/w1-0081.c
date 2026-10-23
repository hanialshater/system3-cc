void abort (void);
void exit (int);

int gfbyte ( void ) 
{
 return 0;
} 

int b8_main( void ) 
{
 int i,j,k ;

 i = gfbyte();

 i = i + 1 ;

 if ( i == 0 ) 
     k = -0 ;
 else
     k = i + 0 ;

 if (i != 1)
   abort ();

 k = 1 ;
 if ( k <= i)
     do 
	 j = gfbyte () ;
     while ( k++ < i ) ;

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
