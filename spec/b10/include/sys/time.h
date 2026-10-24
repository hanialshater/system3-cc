/* Benchmark 10 provided header, SQLite set (x86-64 Linux, glibc's layouts and values). */
#ifndef B10_SYS_TIME_H
#define B10_SYS_TIME_H
#include <sys/types.h>
struct timeval { time_t tv_sec; suseconds_t tv_usec; };
int gettimeofday(struct timeval *tv, void *tz);
int utimes(const char *path, const struct timeval times[2]);
#endif
