/* Benchmark 8 provided header, W7 set (glibc's status macros). */
#ifndef B8_SYS_WAIT_H
#define B8_SYS_WAIT_H
#include <sys/types.h>
#define WEXITSTATUS(s) (((s) & 0xff00) >> 8)
#define WTERMSIG(s) ((s) & 0x7f)
#define WIFEXITED(s) (WTERMSIG(s) == 0)
#define WIFSIGNALED(s) (((signed char)(((s) & 0x7f) + 1) >> 1) > 0)
#endif
