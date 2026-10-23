void abort(void);
int array[1000];
void
test (int a)
{
        if (__builtin_expect (a > 3, 1))
                return;
        for (int i = 0; i < a; i++)
                array[i]=i;
}
void
test2 (int a)
{
        if (__builtin_expect (a > 10, 1))
                return;
        for (int i = 0; i < a; i++)
                array[i]=i;
}
int
b8_main()
{
        test(1);
        test(2);
        test(3);
        test2(10);
        if (array[9] != 9)
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
