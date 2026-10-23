void abort(void);
int
main (int argc, char **argv)
{
  int c;
  unsigned char out[][1] = { {71}, {71}, {71} };

  for (int i = 0; i < 3; i++)
    if (!out[i][0])
      abort ();
  return 0;
}
