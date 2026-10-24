/* Benchmark 8 provided header, W7 set (x86-64 Linux). */
#ifndef B8_SYS_TYPES_H
#define B8_SYS_TYPES_H
#include <stddef.h>
typedef long off_t;
typedef long ssize_t;
typedef int pid_t;
/* Benchmark 10 additions (SQLite's unix VFS) */
typedef unsigned int mode_t;
typedef unsigned int uid_t;
typedef unsigned int gid_t;
typedef unsigned long dev_t;
typedef unsigned long ino_t;
typedef unsigned long nlink_t;
typedef long blksize_t;
typedef long blkcnt_t;
typedef long time_t;
typedef long suseconds_t;
#ifndef B10_TIMESPEC
#define B10_TIMESPEC
struct timespec { long tv_sec; long tv_nsec; };
#endif
#endif
