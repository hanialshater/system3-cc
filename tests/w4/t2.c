%:define N 3
int printf(const char *fmt, ...);
int a<:N:> = <%1, 2, 3%>;
int main() <% int x<:2:><:2:> = <%<%1%>, <%2, 4%>%>; printf("%d %d\n", a<:2:>, x<:1:><:1:>); return 0; %>
