/* Benchmark 8 provided header. */
#ifndef B8_STDLIB_H
#define B8_STDLIB_H
#include <stddef.h>
#define EXIT_SUCCESS 0
#define EXIT_FAILURE 1
void *malloc(size_t size);
void *calloc(size_t n, size_t size);
void *realloc(void *p, size_t size);
void free(void *p);
void exit(int status);
void abort(void);
int atoi(const char *s);
long atol(const char *s);
int abs(int x);
long labs(long x);
void qsort(void *base, size_t n, size_t size, int (*cmp)(const void *, const void *));
#endif
