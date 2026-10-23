void abort (void);
void exit (int);
int b8_main(void){if(1.17549435e-38F<=1.1)exit(0);abort();  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
