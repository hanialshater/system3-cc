void abort (void);
void exit (int);
long long c=2863311530LL,c3=2863311530LL*3;
int b8_main(void){if(c*3!=c3)abort();exit(0);  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
