int printf(const char*,...);
double g = 1.0/0.0;
int main() { printf("%f %d %d %u %d\n", g, (int)(1e10*2), 1 << 32 == 0, (unsigned)-1.0, (int)(0.0/0.0 != 0.0/0.0)); return 0; }
