/* Benchmark 8 provided header, W7 set (glibc x86-64: sigset_t 128 bytes, struct sigaction 152 bytes). */
#ifndef B8_SIGNAL_H
#define B8_SIGNAL_H
typedef int sig_atomic_t;
typedef struct { unsigned long __val[16]; } sigset_t;
#define SIG_ERR ((void (*)(int))-1)
#define SIG_DFL ((void (*)(int))0)
#define SIG_IGN ((void (*)(int))1)
#define SIGINT 2
#define SIGILL 4
#define SIGABRT 6
#define SIGFPE 8
#define SIGSEGV 11
#define SIGTERM 15
struct sigaction {
  void (*sa_handler)(int);
  sigset_t sa_mask;
  int sa_flags;
  void (*sa_restorer)(void);
};
void (*signal(int sig, void (*handler)(int)))(int);
int raise(int sig);
int sigemptyset(sigset_t *set);
int sigaction(int sig, const struct sigaction *act, struct sigaction *old);
#endif
