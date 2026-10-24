/* Benchmark 10 provided header, SQLite set (x86-64 Linux, glibc's layouts and values). */
#ifndef B10_UTIME_H
#define B10_UTIME_H
#include <sys/types.h>
struct utimbuf { time_t actime; time_t modtime; };
int utime(const char *path, const struct utimbuf *times);
#endif
