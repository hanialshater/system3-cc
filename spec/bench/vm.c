/* W6 benchmark: a switch-dispatched bytecode interpreter (enums, structs, switch). */
#include <stdio.h>
#define ITER 8000000
#define MULT 31
#define MODV 1000003
enum Op { PUSH, LOAD, STORE, ADD, SUB, MUL, MOD, LT, JZ, JMP, DUP, POP, HALT };
typedef struct { enum Op op; int arg; } Ins;
/* acc = 0; i = 0; while (i < n) { acc = (acc * MULT + i) % MODV; i = i + 1; } */
static Ins prog[] = {
  {PUSH, 0}, {STORE, 0}, {PUSH, 0}, {STORE, 1},
  {LOAD, 1}, {LOAD, 2}, {LT, 0}, {JZ, 22},
  {LOAD, 0}, {PUSH, MULT}, {MUL, 0}, {LOAD, 1}, {ADD, 0}, {PUSH, MODV}, {MOD, 0}, {STORE, 0},
  {LOAD, 1}, {PUSH, 1}, {ADD, 0}, {STORE, 1}, {JMP, 4}, {HALT, 0},
  {HALT, 0},
};
static long run(long n) {
  long stack[64], vars[4];
  int sp = 0, pc = 0;
  vars[2] = n;
  for (;;) {
    Ins in = prog[pc++];
    switch (in.op) {
    case PUSH: stack[sp++] = in.arg; break;
    case LOAD: stack[sp++] = vars[in.arg]; break;
    case STORE: vars[in.arg] = stack[--sp]; break;
    case ADD: sp--; stack[sp - 1] += stack[sp]; break;
    case SUB: sp--; stack[sp - 1] -= stack[sp]; break;
    case MUL: sp--; stack[sp - 1] *= stack[sp]; break;
    case MOD: sp--; stack[sp - 1] %= stack[sp]; break;
    case LT: sp--; stack[sp - 1] = stack[sp - 1] < stack[sp]; break;
    case JZ: if (!stack[--sp]) pc = in.arg; break;
    case JMP: pc = in.arg; break;
    case DUP: stack[sp] = stack[sp - 1]; sp++; break;
    case POP: sp--; break;
    case HALT: return vars[0];
    }
  }
}
int main(void) {
  long r = run(ITER);
  printf("vm(%d) = %ld\n", ITER, r);
  return 0;
}
