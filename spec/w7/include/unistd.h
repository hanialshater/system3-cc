/* Benchmark 8 provided header, W7 set (POSIX, the few calls Lua's LUA_USE_POSIX build needs). */
#ifndef B8_UNISTD_H
#define B8_UNISTD_H
#include <sys/types.h>
int isatty(int fd);
int close(int fd);
int unlink(const char *path);
#endif
