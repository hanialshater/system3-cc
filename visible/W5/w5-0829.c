void abort (void);
void exit (int);
long f(short a,short b){return (long)a/b;}
int b8_main(void){if(f(-32768,-1)!=32768L)abort();else exit(0);  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
