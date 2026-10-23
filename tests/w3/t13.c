int printf(const char *fmt, ...);
int fails;
struct S1 { char c[1]; };
struct S1 mk1(int base) { struct S1 s; int i; for (i = 0; i < 1; i++) s.c[i] = base + i; return s; }
int sum1(int a, struct S1 s, int b) { int t = a + b; int i; for (i = 0; i < 1; i++) t += s.c[i]; return t; }
int sum1s(int a1, int a2, int a3, int a4, int a5, int a6, struct S1 s, int b) { int t = a1 + b; int i; for (i = 0; i < 1; i++) t += s.c[i]; return t; }
struct S1 pass1(struct S1 s) { s.c[0]++; return s; }
struct S2 { char c[2]; };
struct S2 mk2(int base) { struct S2 s; int i; for (i = 0; i < 2; i++) s.c[i] = base + i; return s; }
int sum2(int a, struct S2 s, int b) { int t = a + b; int i; for (i = 0; i < 2; i++) t += s.c[i]; return t; }
int sum2s(int a1, int a2, int a3, int a4, int a5, int a6, struct S2 s, int b) { int t = a1 + b; int i; for (i = 0; i < 2; i++) t += s.c[i]; return t; }
struct S2 pass2(struct S2 s) { s.c[0]++; return s; }
struct S3 { char c[3]; };
struct S3 mk3(int base) { struct S3 s; int i; for (i = 0; i < 3; i++) s.c[i] = base + i; return s; }
int sum3(int a, struct S3 s, int b) { int t = a + b; int i; for (i = 0; i < 3; i++) t += s.c[i]; return t; }
int sum3s(int a1, int a2, int a3, int a4, int a5, int a6, struct S3 s, int b) { int t = a1 + b; int i; for (i = 0; i < 3; i++) t += s.c[i]; return t; }
struct S3 pass3(struct S3 s) { s.c[0]++; return s; }
struct S4 { char c[4]; };
struct S4 mk4(int base) { struct S4 s; int i; for (i = 0; i < 4; i++) s.c[i] = base + i; return s; }
int sum4(int a, struct S4 s, int b) { int t = a + b; int i; for (i = 0; i < 4; i++) t += s.c[i]; return t; }
int sum4s(int a1, int a2, int a3, int a4, int a5, int a6, struct S4 s, int b) { int t = a1 + b; int i; for (i = 0; i < 4; i++) t += s.c[i]; return t; }
struct S4 pass4(struct S4 s) { s.c[0]++; return s; }
struct S5 { char c[5]; };
struct S5 mk5(int base) { struct S5 s; int i; for (i = 0; i < 5; i++) s.c[i] = base + i; return s; }
int sum5(int a, struct S5 s, int b) { int t = a + b; int i; for (i = 0; i < 5; i++) t += s.c[i]; return t; }
int sum5s(int a1, int a2, int a3, int a4, int a5, int a6, struct S5 s, int b) { int t = a1 + b; int i; for (i = 0; i < 5; i++) t += s.c[i]; return t; }
struct S5 pass5(struct S5 s) { s.c[0]++; return s; }
struct S6 { char c[6]; };
struct S6 mk6(int base) { struct S6 s; int i; for (i = 0; i < 6; i++) s.c[i] = base + i; return s; }
int sum6(int a, struct S6 s, int b) { int t = a + b; int i; for (i = 0; i < 6; i++) t += s.c[i]; return t; }
int sum6s(int a1, int a2, int a3, int a4, int a5, int a6, struct S6 s, int b) { int t = a1 + b; int i; for (i = 0; i < 6; i++) t += s.c[i]; return t; }
struct S6 pass6(struct S6 s) { s.c[0]++; return s; }
struct S7 { char c[7]; };
struct S7 mk7(int base) { struct S7 s; int i; for (i = 0; i < 7; i++) s.c[i] = base + i; return s; }
int sum7(int a, struct S7 s, int b) { int t = a + b; int i; for (i = 0; i < 7; i++) t += s.c[i]; return t; }
int sum7s(int a1, int a2, int a3, int a4, int a5, int a6, struct S7 s, int b) { int t = a1 + b; int i; for (i = 0; i < 7; i++) t += s.c[i]; return t; }
struct S7 pass7(struct S7 s) { s.c[0]++; return s; }
struct S8 { char c[8]; };
struct S8 mk8(int base) { struct S8 s; int i; for (i = 0; i < 8; i++) s.c[i] = base + i; return s; }
int sum8(int a, struct S8 s, int b) { int t = a + b; int i; for (i = 0; i < 8; i++) t += s.c[i]; return t; }
int sum8s(int a1, int a2, int a3, int a4, int a5, int a6, struct S8 s, int b) { int t = a1 + b; int i; for (i = 0; i < 8; i++) t += s.c[i]; return t; }
struct S8 pass8(struct S8 s) { s.c[0]++; return s; }
struct S9 { char c[9]; };
struct S9 mk9(int base) { struct S9 s; int i; for (i = 0; i < 9; i++) s.c[i] = base + i; return s; }
int sum9(int a, struct S9 s, int b) { int t = a + b; int i; for (i = 0; i < 9; i++) t += s.c[i]; return t; }
int sum9s(int a1, int a2, int a3, int a4, int a5, int a6, struct S9 s, int b) { int t = a1 + b; int i; for (i = 0; i < 9; i++) t += s.c[i]; return t; }
struct S9 pass9(struct S9 s) { s.c[0]++; return s; }
struct S10 { char c[10]; };
struct S10 mk10(int base) { struct S10 s; int i; for (i = 0; i < 10; i++) s.c[i] = base + i; return s; }
int sum10(int a, struct S10 s, int b) { int t = a + b; int i; for (i = 0; i < 10; i++) t += s.c[i]; return t; }
int sum10s(int a1, int a2, int a3, int a4, int a5, int a6, struct S10 s, int b) { int t = a1 + b; int i; for (i = 0; i < 10; i++) t += s.c[i]; return t; }
struct S10 pass10(struct S10 s) { s.c[0]++; return s; }
struct S11 { char c[11]; };
struct S11 mk11(int base) { struct S11 s; int i; for (i = 0; i < 11; i++) s.c[i] = base + i; return s; }
int sum11(int a, struct S11 s, int b) { int t = a + b; int i; for (i = 0; i < 11; i++) t += s.c[i]; return t; }
int sum11s(int a1, int a2, int a3, int a4, int a5, int a6, struct S11 s, int b) { int t = a1 + b; int i; for (i = 0; i < 11; i++) t += s.c[i]; return t; }
struct S11 pass11(struct S11 s) { s.c[0]++; return s; }
struct S12 { char c[12]; };
struct S12 mk12(int base) { struct S12 s; int i; for (i = 0; i < 12; i++) s.c[i] = base + i; return s; }
int sum12(int a, struct S12 s, int b) { int t = a + b; int i; for (i = 0; i < 12; i++) t += s.c[i]; return t; }
int sum12s(int a1, int a2, int a3, int a4, int a5, int a6, struct S12 s, int b) { int t = a1 + b; int i; for (i = 0; i < 12; i++) t += s.c[i]; return t; }
struct S12 pass12(struct S12 s) { s.c[0]++; return s; }
struct S13 { char c[13]; };
struct S13 mk13(int base) { struct S13 s; int i; for (i = 0; i < 13; i++) s.c[i] = base + i; return s; }
int sum13(int a, struct S13 s, int b) { int t = a + b; int i; for (i = 0; i < 13; i++) t += s.c[i]; return t; }
int sum13s(int a1, int a2, int a3, int a4, int a5, int a6, struct S13 s, int b) { int t = a1 + b; int i; for (i = 0; i < 13; i++) t += s.c[i]; return t; }
struct S13 pass13(struct S13 s) { s.c[0]++; return s; }
struct S14 { char c[14]; };
struct S14 mk14(int base) { struct S14 s; int i; for (i = 0; i < 14; i++) s.c[i] = base + i; return s; }
int sum14(int a, struct S14 s, int b) { int t = a + b; int i; for (i = 0; i < 14; i++) t += s.c[i]; return t; }
int sum14s(int a1, int a2, int a3, int a4, int a5, int a6, struct S14 s, int b) { int t = a1 + b; int i; for (i = 0; i < 14; i++) t += s.c[i]; return t; }
struct S14 pass14(struct S14 s) { s.c[0]++; return s; }
struct S15 { char c[15]; };
struct S15 mk15(int base) { struct S15 s; int i; for (i = 0; i < 15; i++) s.c[i] = base + i; return s; }
int sum15(int a, struct S15 s, int b) { int t = a + b; int i; for (i = 0; i < 15; i++) t += s.c[i]; return t; }
int sum15s(int a1, int a2, int a3, int a4, int a5, int a6, struct S15 s, int b) { int t = a1 + b; int i; for (i = 0; i < 15; i++) t += s.c[i]; return t; }
struct S15 pass15(struct S15 s) { s.c[0]++; return s; }
struct S16 { char c[16]; };
struct S16 mk16(int base) { struct S16 s; int i; for (i = 0; i < 16; i++) s.c[i] = base + i; return s; }
int sum16(int a, struct S16 s, int b) { int t = a + b; int i; for (i = 0; i < 16; i++) t += s.c[i]; return t; }
int sum16s(int a1, int a2, int a3, int a4, int a5, int a6, struct S16 s, int b) { int t = a1 + b; int i; for (i = 0; i < 16; i++) t += s.c[i]; return t; }
struct S16 pass16(struct S16 s) { s.c[0]++; return s; }
struct S17 { char c[17]; };
struct S17 mk17(int base) { struct S17 s; int i; for (i = 0; i < 17; i++) s.c[i] = base + i; return s; }
int sum17(int a, struct S17 s, int b) { int t = a + b; int i; for (i = 0; i < 17; i++) t += s.c[i]; return t; }
int sum17s(int a1, int a2, int a3, int a4, int a5, int a6, struct S17 s, int b) { int t = a1 + b; int i; for (i = 0; i < 17; i++) t += s.c[i]; return t; }
struct S17 pass17(struct S17 s) { s.c[0]++; return s; }
struct S18 { char c[18]; };
struct S18 mk18(int base) { struct S18 s; int i; for (i = 0; i < 18; i++) s.c[i] = base + i; return s; }
int sum18(int a, struct S18 s, int b) { int t = a + b; int i; for (i = 0; i < 18; i++) t += s.c[i]; return t; }
int sum18s(int a1, int a2, int a3, int a4, int a5, int a6, struct S18 s, int b) { int t = a1 + b; int i; for (i = 0; i < 18; i++) t += s.c[i]; return t; }
struct S18 pass18(struct S18 s) { s.c[0]++; return s; }
struct S19 { char c[19]; };
struct S19 mk19(int base) { struct S19 s; int i; for (i = 0; i < 19; i++) s.c[i] = base + i; return s; }
int sum19(int a, struct S19 s, int b) { int t = a + b; int i; for (i = 0; i < 19; i++) t += s.c[i]; return t; }
int sum19s(int a1, int a2, int a3, int a4, int a5, int a6, struct S19 s, int b) { int t = a1 + b; int i; for (i = 0; i < 19; i++) t += s.c[i]; return t; }
struct S19 pass19(struct S19 s) { s.c[0]++; return s; }
struct S20 { char c[20]; };
struct S20 mk20(int base) { struct S20 s; int i; for (i = 0; i < 20; i++) s.c[i] = base + i; return s; }
int sum20(int a, struct S20 s, int b) { int t = a + b; int i; for (i = 0; i < 20; i++) t += s.c[i]; return t; }
int sum20s(int a1, int a2, int a3, int a4, int a5, int a6, struct S20 s, int b) { int t = a1 + b; int i; for (i = 0; i < 20; i++) t += s.c[i]; return t; }
struct S20 pass20(struct S20 s) { s.c[0]++; return s; }
struct M1 { int a; char b; }; struct M2 { char a; int b; char c; }; struct M3 { int a, b, c; }; struct M4 { char a; char b; int c; int d; char e; };
struct M1 idM1(struct M1 x) { return x; }
struct M2 idM2(struct M2 x) { return x; }
struct M3 idM3(struct M3 x) { return x; }
struct M4 idM4(struct M4 x) { return x; }
int main() {
  { struct S1 s = mk1(1); int e = 1; if (sum1(10, s, 20) != e + 30) { printf("FAIL sum1\n"); fails++; }
    if (sum1s(1,2,3,4,5,6, mk1(1), 7) != e + 8) { printf("FAIL sum1s\n"); fails++; }
    struct S1 t = pass1(s); if (t.c[0] != 2 || t.c[0] != 1 - (1 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass1\n"); fails++; } }
  { struct S2 s = mk2(1); int e = 3; if (sum2(10, s, 20) != e + 30) { printf("FAIL sum2\n"); fails++; }
    if (sum2s(1,2,3,4,5,6, mk2(1), 7) != e + 8) { printf("FAIL sum2s\n"); fails++; }
    struct S2 t = pass2(s); if (t.c[0] != 2 || t.c[1] != 2 - (2 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass2\n"); fails++; } }
  { struct S3 s = mk3(1); int e = 6; if (sum3(10, s, 20) != e + 30) { printf("FAIL sum3\n"); fails++; }
    if (sum3s(1,2,3,4,5,6, mk3(1), 7) != e + 8) { printf("FAIL sum3s\n"); fails++; }
    struct S3 t = pass3(s); if (t.c[0] != 2 || t.c[2] != 3 - (3 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass3\n"); fails++; } }
  { struct S4 s = mk4(1); int e = 10; if (sum4(10, s, 20) != e + 30) { printf("FAIL sum4\n"); fails++; }
    if (sum4s(1,2,3,4,5,6, mk4(1), 7) != e + 8) { printf("FAIL sum4s\n"); fails++; }
    struct S4 t = pass4(s); if (t.c[0] != 2 || t.c[3] != 4 - (4 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass4\n"); fails++; } }
  { struct S5 s = mk5(1); int e = 15; if (sum5(10, s, 20) != e + 30) { printf("FAIL sum5\n"); fails++; }
    if (sum5s(1,2,3,4,5,6, mk5(1), 7) != e + 8) { printf("FAIL sum5s\n"); fails++; }
    struct S5 t = pass5(s); if (t.c[0] != 2 || t.c[4] != 5 - (5 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass5\n"); fails++; } }
  { struct S6 s = mk6(1); int e = 21; if (sum6(10, s, 20) != e + 30) { printf("FAIL sum6\n"); fails++; }
    if (sum6s(1,2,3,4,5,6, mk6(1), 7) != e + 8) { printf("FAIL sum6s\n"); fails++; }
    struct S6 t = pass6(s); if (t.c[0] != 2 || t.c[5] != 6 - (6 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass6\n"); fails++; } }
  { struct S7 s = mk7(1); int e = 28; if (sum7(10, s, 20) != e + 30) { printf("FAIL sum7\n"); fails++; }
    if (sum7s(1,2,3,4,5,6, mk7(1), 7) != e + 8) { printf("FAIL sum7s\n"); fails++; }
    struct S7 t = pass7(s); if (t.c[0] != 2 || t.c[6] != 7 - (7 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass7\n"); fails++; } }
  { struct S8 s = mk8(1); int e = 36; if (sum8(10, s, 20) != e + 30) { printf("FAIL sum8\n"); fails++; }
    if (sum8s(1,2,3,4,5,6, mk8(1), 7) != e + 8) { printf("FAIL sum8s\n"); fails++; }
    struct S8 t = pass8(s); if (t.c[0] != 2 || t.c[7] != 8 - (8 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass8\n"); fails++; } }
  { struct S9 s = mk9(1); int e = 45; if (sum9(10, s, 20) != e + 30) { printf("FAIL sum9\n"); fails++; }
    if (sum9s(1,2,3,4,5,6, mk9(1), 7) != e + 8) { printf("FAIL sum9s\n"); fails++; }
    struct S9 t = pass9(s); if (t.c[0] != 2 || t.c[8] != 9 - (9 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass9\n"); fails++; } }
  { struct S10 s = mk10(1); int e = 55; if (sum10(10, s, 20) != e + 30) { printf("FAIL sum10\n"); fails++; }
    if (sum10s(1,2,3,4,5,6, mk10(1), 7) != e + 8) { printf("FAIL sum10s\n"); fails++; }
    struct S10 t = pass10(s); if (t.c[0] != 2 || t.c[9] != 10 - (10 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass10\n"); fails++; } }
  { struct S11 s = mk11(1); int e = 66; if (sum11(10, s, 20) != e + 30) { printf("FAIL sum11\n"); fails++; }
    if (sum11s(1,2,3,4,5,6, mk11(1), 7) != e + 8) { printf("FAIL sum11s\n"); fails++; }
    struct S11 t = pass11(s); if (t.c[0] != 2 || t.c[10] != 11 - (11 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass11\n"); fails++; } }
  { struct S12 s = mk12(1); int e = 78; if (sum12(10, s, 20) != e + 30) { printf("FAIL sum12\n"); fails++; }
    if (sum12s(1,2,3,4,5,6, mk12(1), 7) != e + 8) { printf("FAIL sum12s\n"); fails++; }
    struct S12 t = pass12(s); if (t.c[0] != 2 || t.c[11] != 12 - (12 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass12\n"); fails++; } }
  { struct S13 s = mk13(1); int e = 91; if (sum13(10, s, 20) != e + 30) { printf("FAIL sum13\n"); fails++; }
    if (sum13s(1,2,3,4,5,6, mk13(1), 7) != e + 8) { printf("FAIL sum13s\n"); fails++; }
    struct S13 t = pass13(s); if (t.c[0] != 2 || t.c[12] != 13 - (13 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass13\n"); fails++; } }
  { struct S14 s = mk14(1); int e = 105; if (sum14(10, s, 20) != e + 30) { printf("FAIL sum14\n"); fails++; }
    if (sum14s(1,2,3,4,5,6, mk14(1), 7) != e + 8) { printf("FAIL sum14s\n"); fails++; }
    struct S14 t = pass14(s); if (t.c[0] != 2 || t.c[13] != 14 - (14 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass14\n"); fails++; } }
  { struct S15 s = mk15(1); int e = 120; if (sum15(10, s, 20) != e + 30) { printf("FAIL sum15\n"); fails++; }
    if (sum15s(1,2,3,4,5,6, mk15(1), 7) != e + 8) { printf("FAIL sum15s\n"); fails++; }
    struct S15 t = pass15(s); if (t.c[0] != 2 || t.c[14] != 15 - (15 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass15\n"); fails++; } }
  { struct S16 s = mk16(1); int e = 136; if (sum16(10, s, 20) != e + 30) { printf("FAIL sum16\n"); fails++; }
    if (sum16s(1,2,3,4,5,6, mk16(1), 7) != e + 8) { printf("FAIL sum16s\n"); fails++; }
    struct S16 t = pass16(s); if (t.c[0] != 2 || t.c[15] != 16 - (16 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass16\n"); fails++; } }
  { struct S17 s = mk17(1); int e = 153; if (sum17(10, s, 20) != e + 30) { printf("FAIL sum17\n"); fails++; }
    if (sum17s(1,2,3,4,5,6, mk17(1), 7) != e + 8) { printf("FAIL sum17s\n"); fails++; }
    struct S17 t = pass17(s); if (t.c[0] != 2 || t.c[16] != 17 - (17 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass17\n"); fails++; } }
  { struct S18 s = mk18(1); int e = 171; if (sum18(10, s, 20) != e + 30) { printf("FAIL sum18\n"); fails++; }
    if (sum18s(1,2,3,4,5,6, mk18(1), 7) != e + 8) { printf("FAIL sum18s\n"); fails++; }
    struct S18 t = pass18(s); if (t.c[0] != 2 || t.c[17] != 18 - (18 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass18\n"); fails++; } }
  { struct S19 s = mk19(1); int e = 190; if (sum19(10, s, 20) != e + 30) { printf("FAIL sum19\n"); fails++; }
    if (sum19s(1,2,3,4,5,6, mk19(1), 7) != e + 8) { printf("FAIL sum19s\n"); fails++; }
    struct S19 t = pass19(s); if (t.c[0] != 2 || t.c[18] != 19 - (19 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass19\n"); fails++; } }
  { struct S20 s = mk20(1); int e = 210; if (sum20(10, s, 20) != e + 30) { printf("FAIL sum20\n"); fails++; }
    if (sum20s(1,2,3,4,5,6, mk20(1), 7) != e + 8) { printf("FAIL sum20s\n"); fails++; }
    struct S20 t = pass20(s); if (t.c[0] != 2 || t.c[19] != 20 - (20 == 1 ? -1 : 0) || s.c[0] != 1) { printf("FAIL pass20\n"); fails++; } }
  { struct M1 x = {1, 2}; struct M1 y = idM1(x); if (y.a != 1 || y.b != 2) { printf("FAIL M1\n"); fails++; } }
  { struct M2 x = {1, 2, 3}; struct M2 y = idM2(x); if (y.a != 1 || y.b != 2 || y.c != 3) { printf("FAIL M2\n"); fails++; } }
  { struct M3 x = {1, 2, 3}; struct M3 y = idM3(x); if (y.a != 1 || y.b != 2 || y.c != 3) { printf("FAIL M3\n"); fails++; } }
  { struct M4 x = {1, 2, 3, 4, 5}; struct M4 y = idM4(x); if (y.a != 1 || y.b != 2 || y.c != 3 || y.d != 4 || y.e != 5) { printf("FAIL M4\n"); fails++; } }
  printf("fails=%d\n", fails); return fails; }
