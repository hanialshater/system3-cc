int
b8_main()
{
        int x;

        x = 3;
        x = !x;
        x = !x;
        x = ~x;
        x = -x;
        if(x != 2)
                return 1;
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
