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
/* Benchmark 10 additions */
#define ESRCH 3
#define ENXIO 6
#define E2BIG 7
#define ECHILD 10
#define EFAULT 14
#define EBUSY 16
#define EXDEV 18
#define ENODEV 19
#define ENOTDIR 20
#define EISDIR 21
#define ENFILE 23
#define EMFILE 24
#define ENOTTY 25
#define EFBIG 27
#define ENOSPC 28
#define ESPIPE 29
#define EROFS 30
#define EMLINK 31
#define EPIPE 32
#define EDEADLK 35
#define ENAMETOOLONG 36
#define ENOLCK 37
#define ENOSYS 38
#define ENOTEMPTY 39
#define ELOOP 40
#define EWOULDBLOCK EAGAIN
#define EOVERFLOW 75
#define ENOTSUP 95
#define EOPNOTSUPP 95
#define ETIMEDOUT 110
#endif
