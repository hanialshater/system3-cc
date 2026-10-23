int printf(const char *fmt, ...);
int strcmp(const char *a, const char *b);
int memcmp(const void *a, const void *b, unsigned long n);
int fails;
void check(int line, long got, long exp) { if (got != exp) { printf("FAIL line %d: got %ld expected %ld\n", line, got, exp); fails++; } }
#define CHECK(a,b) check(__LINE__, (a), (b))
typedef char T60[];
T60 g60 = {1, 2, 3};
T60 g61 = {1, 2, 3, 4, 5, 6};
typedef struct { char a, b[]; } T65;
T65 g65 = {'f','o','o',0};
T65 g66 = {'f','o','o','b','a','r',0};
char g43[][4] = {"foo", "bar"};
int main() {
  CHECK(sizeof(g60), 3); CHECK(sizeof(g61), 6); CHECK(sizeof(g65), 1); CHECK(strcmp(g65.b, "oo"), 0); CHECK(strcmp(g66.b, "oobar"), 0);
  CHECK(strcmp(g43[1], "bar"), 0);
  { typedef char T[]; T x="foo"; T y="x"; CHECK(sizeof(x), 4); CHECK(sizeof(y), 2); }
  { typedef char T[]; T x="x"; T y="foo"; CHECK(sizeof(x), 2); CHECK(sizeof(y), 4); }
  { struct { int a,b; } x[]={[1].b=1,2,[0]=3,4,}; CHECK(x[2].a, 2); CHECK(x[0].a, 3); CHECK(x[0].b, 4); CHECK(x[1].b, 1); CHECK(sizeof(x), 24); }
  { struct { struct { int a; struct { int b; }; }; int c; } x={1,2,3,.b=4,5}; CHECK(x.a, 1); CHECK(x.b, 4); CHECK(x.c, 5); }
  { char x[]={[2 ... 10]='a', [7]='b', [15 ... 15]='c', [3 ... 5]='d'}; CHECK(sizeof(x), 16); CHECK(memcmp(x, "\0\0adddabaaa\0\0\0\0c", 16), 0); }
  { union { struct { char a,b,c,d; } e; int f; } x={{4,3,2,1}}; CHECK(x.f, 0x01020304); }
  { typedef struct {int a,b,c,d,e,f;} T; T x={1,2,3,4,5,6}; T y; y=x; CHECK(y.e, 5); }
  { typedef struct {int a,b;} T; T x={1,2}; T y, z; z=y=x; CHECK(z.b, 2); }
  { struct { int a[2]; } x={.a=1,2}; CHECK(x.a[1], 2); }
  { struct { int a,b; } x[]={[1]={1,2}, [0].b=5}; CHECK(x[0].b, 5); CHECK(x[1].a, 1); }
  { struct { union { int a; char b; }; int c; } x = {.b = 1, 2}; CHECK(x.c, 2); }
  { struct { union { int a; char b; }; int c; } x = {.a = 1, .c = 3}; CHECK(x.c, 3); }
  { int x[2][3]={[1][2]=1, [0]=2}; CHECK(x[0][0], 2); CHECK(x[1][2], 1); }
  { int x[2][3]={[1]={1}, [0][1]=2, 3, 4}; CHECK(x[0][1], 2); CHECK(x[0][2], 3); CHECK(x[1][0], 4); }
  printf("fails=%d\n", fails);
  return fails;
}
