/* Benchmark 10 provided header, SQLite set (x86-64 Linux, glibc's layouts and values). */
#ifndef B10_SYS_MMAN_H
#define B10_SYS_MMAN_H
#include <sys/types.h>
#define PROT_NONE 0
#define PROT_READ 1
#define PROT_WRITE 2
#define MAP_SHARED 1
#define MAP_PRIVATE 2
#define MAP_FAILED ((void *)-1)
#define MREMAP_MAYMOVE 1
void *mmap(void *addr, size_t len, int prot, int flags, int fd, off_t off);
int munmap(void *addr, size_t len);
void *mremap(void *old, size_t oldlen, size_t newlen, int flags, ...);
#endif
