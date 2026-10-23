int g;

int
effect()
{
	g = 1;
	return 1;
}

int
b8_main()
{
    int x;
    
    g = 0;
    x = 0;
    if(x && effect())
    	return 1;
    if(g)
    	return 2;
    x = 1;
    if(x && effect()) {
    	if(g != 1)
    		return 3;
    } else {
    	return 4;
    }
    g = 0;
    x = 1;
    if(x || effect()) {
    	if(g)
    		return 5;
    } else {
    	return 6;
    }
    x = 0;
    if(x || effect()) {
    	if(g != 1)
    		return 7;
    } else {
    	return 8;
    } 
    return 0;
  return 0;
}

int printf(const char *fmt, ...);
int main(void) {
  printf("b8: start\n");
  int r = b8_main();
  printf("b8: end\n");
  return r;
}
