/* Benchmark 8 provided header, W7 set (glibc's libm, double and float; link with -lm). */
#ifndef B8_MATH_H
#define B8_MATH_H
#define HUGE_VAL (1e300 * 1e300)
#define HUGE_VALF (1e30f * 1e30f)
double acos(double x);
double asin(double x);
double atan(double x);
double atan2(double y, double x);
double cos(double x);
double sin(double x);
double tan(double x);
double cosh(double x);
double sinh(double x);
double tanh(double x);
double exp(double x);
double frexp(double x, int *e);
double ldexp(double x, int e);
double log(double x);
double log10(double x);
double log2(double x);
double modf(double x, double *ip);
double pow(double x, double y);
double sqrt(double x);
double ceil(double x);
double fabs(double x);
double floor(double x);
double fmod(double x, double y);
double trunc(double x);
double round(double x);
float floorf(float x);
float ceilf(float x);
float fabsf(float x);
float sqrtf(float x);
float fmodf(float x, float y);
float powf(float x, float y);
#endif
