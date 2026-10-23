int printf(const char*,...);
enum __attribute__((packed)) E { A, B = 200 };
enum F { X = -1, Y } __attribute__((packed));
enum G { P, Q };
int main() { enum E e = B; printf("%d %d %d %d\n", (int)sizeof(enum E), (int)sizeof(enum F), (int)sizeof(enum G), e); return 0; }
