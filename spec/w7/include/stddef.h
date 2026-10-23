/* Benchmark 8 provided header, W7 set (x86-64 Linux, glibc's layouts). */
#ifndef B8_STDDEF_H
#define B8_STDDEF_H
typedef unsigned long size_t;
typedef long ptrdiff_t;
typedef int wchar_t;
#define NULL ((void *)0)
#if defined(__GNUC__) || defined(__clang__)
#define offsetof(type, member) __builtin_offsetof(type, member)
#else
#define offsetof(type, member) ((size_t)&(((type *)0)->member))
#endif
#endif
