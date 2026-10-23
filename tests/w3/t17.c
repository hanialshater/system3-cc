struct S1 { int a; int b; };
struct S2 { struct S1 s1; struct S1 *ps1; int arr[2]; };
struct S1 gs1 = { .a = 1, 2 };
struct S2 s = { {.b = 2, .a = 1}, &gs1, {[0] = 1, 1+1} };

int x;
int x = 3;
int x;
int main();
void *foo() { return &main; }

int y, y = 4, y;

int b8_main()
{
  if(s.s1.a != 1) return 1;
  if(s.s1.b != 2) return 2;
  if(s.ps1->a != 1) return 3;
  if(s.ps1->b != 2) return 4;
  if(s.arr[0] != 1) return 5;
  if(s.arr[1] != 2) return 6;
  if (x != 3) return 7;
  if (y != 4) return 8;
  if (foo() == 0) return 9;
  {
    struct S2 l = { {.b = 2, .a = 1}, &gs1, {[0] = 1, 1+1} };
    if (l.s1.a != 1 || l.s1.b != 2 || l.ps1->b != 2 || l.arr[1] != 2) return 10;
  }
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end %d\n", r);
  return r;
}
