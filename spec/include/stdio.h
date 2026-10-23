/* Benchmark 8 provided header. */
#ifndef B8_STDIO_H
#define B8_STDIO_H
#include <stddef.h>
#include <stdarg.h>
#define EOF (-1)
int printf(const char *fmt, ...);
int sprintf(char *buf, const char *fmt, ...);
int snprintf(char *buf, size_t n, const char *fmt, ...);
int vprintf(const char *fmt, va_list ap);
int vsprintf(char *buf, const char *fmt, va_list ap);
int vsnprintf(char *buf, size_t n, const char *fmt, va_list ap);
int puts(const char *s);
int putchar(int c);
#endif
