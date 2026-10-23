int printf(const char *fmt, ...);
char g[] = "\0ÿ";
char h[] = "aéz";
int puts(const char *s);
int (*tab[])(const char *) = {puts};
int main() { printf("%d %d %d %d\n", (int)sizeof g, g[1], (int)sizeof h, h[1]); tab[0]("ok"); return 0; }
