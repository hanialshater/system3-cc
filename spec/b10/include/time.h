/* Benchmark 8 provided header, W7 set (glibc's struct tm: 56 bytes). */
#ifndef B8_TIME_H
#define B8_TIME_H
#include <stddef.h>
#include <sys/types.h>
typedef long clock_t;
#define CLOCKS_PER_SEC ((clock_t)1000000)
struct tm {
  int tm_sec;
  int tm_min;
  int tm_hour;
  int tm_mday;
  int tm_mon;
  int tm_year;
  int tm_wday;
  int tm_yday;
  int tm_isdst;
  long tm_gmtoff;
  const char *tm_zone;
};
clock_t clock(void);
time_t time(time_t *t);
double difftime(time_t a, time_t b);
time_t mktime(struct tm *tm);
struct tm *gmtime(const time_t *t);
struct tm *localtime(const time_t *t);
struct tm *gmtime_r(const time_t *t, struct tm *out);
struct tm *localtime_r(const time_t *t, struct tm *out);
int nanosleep(const struct timespec *req, struct timespec *rem);
size_t strftime(char *s, size_t max, const char *fmt, const struct tm *tm);
#endif
