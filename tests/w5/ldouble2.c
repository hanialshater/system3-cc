#include <stdio.h>
long double g = 18446744073709551615ULL;
int main() { long double l = 9007199254740993LL; printf("%.0Lf %.0Lf %.0Lf\n", g, l, (long double)-5); return 0; }
