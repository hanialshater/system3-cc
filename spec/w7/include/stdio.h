/* Benchmark 8 provided header, W7 set (glibc: FILE is opaque, stdin/stdout/stderr are variables). */
#ifndef B8_STDIO_H
#define B8_STDIO_H
#include <stddef.h>
#include <stdarg.h>
#include <sys/types.h>
typedef struct _IO_FILE FILE;
extern FILE *stdin;
extern FILE *stdout;
extern FILE *stderr;
#define EOF (-1)
#define BUFSIZ 8192
#define FILENAME_MAX 4096
#define L_tmpnam 20
#define TMP_MAX 238328
#define SEEK_SET 0
#define SEEK_CUR 1
#define SEEK_END 2
#define _IOFBF 0
#define _IOLBF 1
#define _IONBF 2
int printf(const char *fmt, ...);
int fprintf(FILE *f, const char *fmt, ...);
int sprintf(char *buf, const char *fmt, ...);
int snprintf(char *buf, size_t n, const char *fmt, ...);
int vprintf(const char *fmt, va_list ap);
int vfprintf(FILE *f, const char *fmt, va_list ap);
int vsprintf(char *buf, const char *fmt, va_list ap);
int vsnprintf(char *buf, size_t n, const char *fmt, va_list ap);
int scanf(const char *fmt, ...);
int fscanf(FILE *f, const char *fmt, ...);
int sscanf(const char *s, const char *fmt, ...);
int puts(const char *s);
int putchar(int c);
int fputs(const char *s, FILE *f);
int fputc(int c, FILE *f);
int putc(int c, FILE *f);
int getchar(void);
int fgetc(FILE *f);
int getc(FILE *f);
int getc_unlocked(FILE *f);
int ungetc(int c, FILE *f);
char *fgets(char *s, int n, FILE *f);
size_t fread(void *p, size_t size, size_t n, FILE *f);
size_t fwrite(const void *p, size_t size, size_t n, FILE *f);
FILE *fopen(const char *path, const char *mode);
FILE *freopen(const char *path, const char *mode, FILE *f);
FILE *tmpfile(void);
FILE *popen(const char *cmd, const char *mode);
int pclose(FILE *f);
int fclose(FILE *f);
int fflush(FILE *f);
int setvbuf(FILE *f, char *buf, int mode, size_t size);
int fseek(FILE *f, long off, int whence);
long ftell(FILE *f);
int fseeko(FILE *f, off_t off, int whence);
off_t ftello(FILE *f);
void rewind(FILE *f);
void clearerr(FILE *f);
int feof(FILE *f);
int ferror(FILE *f);
int fileno(FILE *f);
void flockfile(FILE *f);
void funlockfile(FILE *f);
void perror(const char *s);
int remove(const char *path);
int rename(const char *from, const char *to);
char *tmpnam(char *s);
#endif
