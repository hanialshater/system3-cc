/* Benchmark 8 provided header, W7 set (POSIX, the few calls Lua's LUA_USE_POSIX build needs), extended for Benchmark 10's SQLite set. */
#ifndef B8_UNISTD_H
#define B8_UNISTD_H
#include <sys/types.h>
int isatty(int fd);
int close(int fd);
int unlink(const char *path);
/* Benchmark 10 additions */
#ifndef SEEK_SET
#define SEEK_SET 0
#define SEEK_CUR 1
#define SEEK_END 2
#endif
#define F_OK 0
#define X_OK 1
#define W_OK 2
#define R_OK 4
#define _SC_PAGESIZE 30
#define _SC_PAGE_SIZE 30
ssize_t read(int fd, void *buf, size_t n);
ssize_t write(int fd, const void *buf, size_t n);
ssize_t pread(int fd, void *buf, size_t n, off_t off);
ssize_t pwrite(int fd, const void *buf, size_t n, off_t off);
off_t lseek(int fd, off_t off, int whence);
int ftruncate(int fd, off_t len);
int fsync(int fd);
int fdatasync(int fd);
pid_t getpid(void);
unsigned int sleep(unsigned int s);
int usleep(unsigned int us);
int access(const char *path, int mode);
char *getcwd(char *buf, size_t size);
int rmdir(const char *path);
ssize_t readlink(const char *path, char *buf, size_t n);
uid_t geteuid(void);
int fchown(int fd, uid_t owner, gid_t group);
long sysconf(int name);
int getpagesize(void);
int dup(int fd);
int dup2(int a, int b);
#endif
