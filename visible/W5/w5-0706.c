void abort(void);
struct mouse_button_str {
        unsigned char left      : 1;
        unsigned char right     : 1;
        unsigned char middle    : 1;
        } button;

static char fct (struct mouse_button_str newbutton) ;
static char
fct (struct mouse_button_str newbutton)
{
  char l = newbutton.left;
  char r = newbutton.right;
  char m = newbutton.middle;
  return l && r && m;
}

int b8_main(void)
{
  struct mouse_button_str newbutton1;
  newbutton1.left = 1;
  newbutton1.middle = 1;
  newbutton1.right = 1;
  if (!fct (newbutton1))
    abort ();

  newbutton1.left = 0;
  newbutton1.middle = 1;
  newbutton1.right = 1;
  if (fct (newbutton1))
    abort ();
  newbutton1.left = 1;
  newbutton1.middle = 0;
  newbutton1.right = 1;
  if (fct (newbutton1))
    abort ();
  newbutton1.left = 1;
  newbutton1.middle = 1;
  newbutton1.right = 0;
  if (fct (newbutton1))
    abort ();


  newbutton1.left = 1;
  newbutton1.middle = 0;
  newbutton1.right = 0;
  if (fct (newbutton1))
    abort ();
  newbutton1.left = 0;
  newbutton1.middle = 1;
  newbutton1.right = 0;
  if (fct (newbutton1))
    abort ();
  newbutton1.left = 0;
  newbutton1.middle = 0;
  newbutton1.right = 1;
  if (fct (newbutton1))
    abort ();
  newbutton1.left = 0;
  newbutton1.middle = 0;
  newbutton1.right = 0;
  if (fct (newbutton1))
    abort ();
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
