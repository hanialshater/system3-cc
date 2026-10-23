extern void abort (void);
extern void exit (int);

static char arg0[] = "arg0";
static char arg1[] = "arg1";

static void attr_rtx		(char *, char *);
static char *attr_string        (char *);
static void attr_eq		(char *, char *);

static void 
attr_rtx (char *varg0, char *varg1)
{
  if (varg0 != arg0)
    abort ();

  if (varg1 != arg1)
    abort ();

  return;
}

static void 
attr_eq (name, value)
     char *name, *value;
{
  return attr_rtx (attr_string (name),
		   attr_string (value));
}

static char *
attr_string (str)
     char *str;
{
  return str;
}

int b8_main()
{
  attr_eq (arg0, arg1);
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
