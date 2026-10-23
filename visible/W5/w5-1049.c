void abort (void);
void exit (int);
int l[]={0,1};
int b8_main(void){int*p=l;switch(*p++){case 0:exit(0);case 1:break;case 2:break;case 3:case 4:break;}abort();  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
