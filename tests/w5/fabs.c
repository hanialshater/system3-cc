double fabs(double);
int printf(const char*,...);
int main() { double x = -2.5; float f = -1.5f; printf("%.2f %.2f %.2f\n", fabs(x), __builtin_fabsf(f), fabs(3)); return 0; }
