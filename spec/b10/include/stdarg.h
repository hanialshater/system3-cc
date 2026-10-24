/* Benchmark 8 provided header.
   Your compiler implements va_list and the four __builtin_va_* forms below as intrinsics (x86-64 SysV ABI).
   The __GNUC__ branch exists only so that gcc and clang can compile the same tests for the expected outputs. */
#ifndef B8_STDARG_H
#define B8_STDARG_H
#if defined(__GNUC__) || defined(__clang__)
typedef __builtin_va_list va_list;
#else
typedef struct {
  unsigned int gp_offset;
  unsigned int fp_offset;
  void *overflow_arg_area;
  void *reg_save_area;
} __b8_va_elem;
typedef __b8_va_elem va_list[1];
#endif
#define va_start(ap, last) __builtin_va_start(ap, last)
#define va_arg(ap, type) __builtin_va_arg(ap, type)
#define va_end(ap) __builtin_va_end(ap)
#define va_copy(dst, src) __builtin_va_copy(dst, src)
#endif
