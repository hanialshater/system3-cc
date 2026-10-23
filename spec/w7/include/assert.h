/* Benchmark 8 provided header, W7 set (glibc's __assert_fail; __FILE__ is not predefined in this contract). */
#undef assert
#ifdef NDEBUG
#define assert(e) ((void)0)
#else
void __assert_fail(const char *expr, const char *file, unsigned int line, const char *func);
#define assert(e) ((e) ? (void)0 : __assert_fail(#e, "?", __LINE__, "?"))
#endif
