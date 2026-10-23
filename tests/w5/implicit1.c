int main() { char *p = malloc(100); char *q = __builtin_memcpy(p, "hello", 6); printf("%s %d %d\n", q, (int)strlen(p), q == p); free(p); return 0; }
