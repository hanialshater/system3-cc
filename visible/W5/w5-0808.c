void abort(void);
typedef unsigned long long uint64_t;
void f(uint64_t *a, uint64_t aa) ;
void f(uint64_t *a, uint64_t aa)
{
  uint64_t new_value = aa;
  uint64_t old_value = *a;
  int bit_size = 32;
    uint64_t mask = (uint64_t)(unsigned)(-1);
    uint64_t tmp = old_value & mask;
    new_value &= mask;
    /* On overflow we need to add 1 in the upper bits */
    if (tmp > new_value)
        new_value += 1ull<<bit_size;
    /* Add in the upper bits from the old value */
    new_value += old_value & ~mask;
    *a = new_value;
}
int b8_main(void)
{
  uint64_t value, new_value, old_value;
  value = 0x100000001;
  old_value = value;
  new_value = (value+1)&(uint64_t)(unsigned)(-1);
  f(&value, new_value);
  if (value != old_value+1)
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
