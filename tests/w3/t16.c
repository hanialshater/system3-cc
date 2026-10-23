int printf(const char *fmt, ...);
char g[] = ("ab" "c");
int main() { char l[] = ("xy"); printf("%d %s %d %s\n", (int)sizeof g, g, (int)sizeof l, l); return 0; }
