extern void abort (void);

struct delay_block {
  struct delay_block *succ;
};

static struct delay_block Timer_Queue;

struct delay_block* time_enqueue (struct delay_block *d)
{
  struct delay_block *q = Timer_Queue.succ; 
  d->succ =  (void *)0;
  return Timer_Queue.succ;
}

int b8_main(void)
{
  Timer_Queue.succ = &Timer_Queue;
  if (time_enqueue (&Timer_Queue) != (void*)0)
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
