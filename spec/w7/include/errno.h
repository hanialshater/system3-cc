/* Benchmark 8 provided header, W7 set (glibc: errno is a per-thread location). */
#ifndef B8_ERRNO_H
#define B8_ERRNO_H
int *__errno_location(void);
#define errno (*__errno_location())
#define EPERM 1
#define ENOENT 2
#define EINTR 4
#define EIO 5
#define EBADF 9
#define EAGAIN 11
#define ENOMEM 12
#define EACCES 13
#define EEXIST 17
#define EINVAL 22
#define ERANGE 34
#define EDOM 33
#endif
