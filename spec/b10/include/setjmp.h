/* Benchmark 8 provided header, W7 set (glibc's jmp_buf: 200 bytes, struct __jmp_buf_tag[1]).
   setjmp and _setjmp return twice: a value your code keeps in a register that longjmp does not restore is lost. */
#ifndef B8_SETJMP_H
#define B8_SETJMP_H
struct __jmp_buf_tag {
  long __jmpbuf[8];
  int __mask_was_saved;
  unsigned long __saved_mask[16];
};
typedef struct __jmp_buf_tag jmp_buf[1];
int setjmp(jmp_buf env);
int _setjmp(jmp_buf env);
void longjmp(jmp_buf env, int val);
void _longjmp(jmp_buf env, int val);
#endif
