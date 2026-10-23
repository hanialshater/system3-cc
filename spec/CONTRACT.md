# Benchmark 8: the compiler contract

You build a compiler for a subset of C, written in Clojure, that emits x86-64 assembly for GNU as on Linux. The work
comes in six waves on one growing codebase. W1.md to W6.md give each wave's exact scope. This file holds what stays
the same in every wave.

## 1. Where the compiler lives and how it runs

- Your compiler is the namespace `ccc.main`, in `src/ccc/main.clj` under your compiler directory. Other namespaces go
  under `src/` too.
- It runs only through the provided wrapper `clj-run`, started from your compiler directory:
  `clj-run` = Clojure 1.12.6 + spec.alpha + core.specs.alpha + `./src`, on OpenJDK 21.
  - There are no other jars, no Leiningen, no tools.deps and no network.
  - The hidden runner copies only the text files of your directory: no binaries, no `.class` or `.jar` files, no
    symlinks, nothing over 5 MB, at most 100 MB in all. So AOT-compiled classes are never used.
- **Single file:**

  ```
  clj-run -m ccc.main [-I <dir>]... <in.c> -o <out.s>
  ```

  Exit status 0 and `<out.s>` written on success. On an error, exit non-zero, write a message to stderr, and leave no
  `<out.s>`.
- **Batch:**

  ```
  clj-run -m ccc.main [-I <dir>]... --batch <list-file>
  ```

  - Each non-empty line of the list is `<in.c> <out.s>`: two absolute paths with no spaces.
  - Compile them **in list order** in this one JVM.
  - A file that fails must not stop the batch. Leave no `<out.s>` for it (remove a partial one), report it on stderr,
    and go on to the next.
  - **Exit 0 when the batch has run to its end**, even if some files failed.
  - The hidden runner uses batch mode. If a batch exits non-zero, dies or hangs, the runner fails the first file it
    did not write and restarts the batch on the rest. So a crash costs you that file, plus a JVM start.
- **`-I <dir>`.** The runner always passes `-I <dir>` with the provided headers (section 3). `#include <name.h>`
  searches the `-I` directories in order. There is no other include path.

## 2. What the output must be

- **Format.** GNU as syntax for x86-64. AT&T syntax is the default; `.intel_syntax noprefix` is also allowed.
- **Assembling and linking.** The runner uses exactly `gcc out.s -o prog` (Ubuntu 24.04, gcc 13, binutils 2.42).
  - That links a PIE by default, so address data RIP-relative (`leaq x(%rip), %rax`).
  - Call libc as `call printf` or `call printf@PLT`.
  - glibc is linked in. Any libc function the provided headers declare may be called.
- **ABI.** Follow the System V AMD64 ABI wherever your code meets libc: arguments, return values, a 16-byte-aligned
  stack at each call, and `%al` = the number of vector registers used when calling a variadic function. Between your
  own functions you may use any convention you like.
- **Types (LP64):**
  - `char` is 1 byte and signed, `short` 2, `int` 4, `long` and `long long` 8, pointers 8.
  - `float` is 4 and `double` 8. `_Bool` is 1.
  - Struct and union layout follows the ABI's size and alignment rules.
- **How a test runs.** Each program runs with no arguments and empty stdin, under a time limit (section 5). A test
  passes when its stdout is byte-for-byte the expected output **and** its exit status is the expected one.

## 3. The provided headers (`spec/include/`)

| Header | What it declares |
|---|---|
| `stdio.h` | printf, sprintf, snprintf, vprintf, vsprintf, vsnprintf, puts, putchar, EOF |
| `stdlib.h` | malloc, calloc, realloc, free, exit, abort, atoi, atol, abs, labs, qsort, EXIT_SUCCESS/FAILURE |
| `string.h` | strlen, strcmp, strncmp, strcpy, strncpy, strcat, strncat, strchr, strrchr, strstr, memcpy, memmove, memset, memcmp, memchr |
| `stdarg.h` | va_list, va_start, va_arg, va_end, va_copy |
| `stddef.h` | size_t (unsigned long), ptrdiff_t (long), NULL (0), offsetof |
| `stdbool.h` | bool (_Bool), true, false |
| `limits.h` | CHAR_BIT, the SCHAR/UCHAR/CHAR/SHRT/USHRT/INT/UINT/LONG/ULONG/LLONG/ULLONG limits |
| `stdint.h` | int8_t … uint64_t, intptr_t, uintptr_t, intmax_t, uintmax_t, the INTn/UINTn limits |

- **The `__GNUC__` branch.** Some headers have one, for gcc and clang only: the expected outputs were made with gcc
  and clang on these same headers. Do not define `__GNUC__` or `__clang__`.
- **What your compiler implements in `stdarg.h`'s other branch:**
  - `va_list` is an array of one `__b8_va_elem`, the ABI's struct `{gp_offset, fp_offset, overflow_arg_area,
    reg_save_area}`.
  - `__builtin_va_start(ap, last)`, `__builtin_va_arg(ap, type)`, `__builtin_va_end(ap)` and
    `__builtin_va_copy(dst, src)` are intrinsics.
  - In `stddef.h`, `offsetof` expands to `((size_t)&(((type *)0)->member))`.
- **Predefined macros.** `__STDC__` (1), `__STDC_VERSION__` (201112L), `__x86_64__` (1), `__LP64__` (1), `__LINE__`,
  and gcc's type, limit, size and byte-order macros with gcc's values on x86-64 Linux (from W4, the wave that brings
  the preprocessor; some torture tests use them):

  | Macros | Values |
  |---|---|
  | `__SIZE_TYPE__`, `__UINTMAX_TYPE__`, `__UINTPTR_TYPE__`, `__UINT64_TYPE__` | `long unsigned int` |
  | `__PTRDIFF_TYPE__`, `__INTMAX_TYPE__`, `__INTPTR_TYPE__`, `__INT64_TYPE__` | `long int` |
  | `__INT8_TYPE__`, `__INT16_TYPE__`, `__INT32_TYPE__` | `signed char`, `short int`, `int` |
  | `__UINT8_TYPE__`, `__UINT16_TYPE__`, `__UINT32_TYPE__` | `unsigned char`, `short unsigned int`, `unsigned int` |
  | `__WCHAR_TYPE__`, `__WINT_TYPE__` | `int`, `unsigned int` |
  | `__CHAR_BIT__` | `8` |
  | `__SCHAR_MAX__`, `__SHRT_MAX__`, `__INT_MAX__` | `0x7f`, `0x7fff`, `0x7fffffff` |
  | `__LONG_MAX__`, `__PTRDIFF_MAX__`, `__INTMAX_MAX__`, `__INTPTR_MAX__`, `__INT64_MAX__` | `0x7fffffffffffffffL` |
  | `__LONG_LONG_MAX__` | `0x7fffffffffffffffLL` |
  | `__SIZE_MAX__`, `__UINTMAX_MAX__`, `__UINTPTR_MAX__`, `__UINT64_MAX__` | `0xffffffffffffffffUL` |
  | `__INT8_MAX__`, `__INT16_MAX__`, `__INT32_MAX__` | `0x7f`, `0x7fff`, `0x7fffffff` |
  | `__UINT8_MAX__`, `__UINT16_MAX__`, `__UINT32_MAX__` | `0xff`, `0xffff`, `0xffffffffU` |
  | `__WCHAR_MAX__`, `__WCHAR_MIN__` | `0x7fffffff`, `(-__WCHAR_MAX__ - 1)` |
  | `__SIZEOF_SHORT__`, `__SIZEOF_INT__`, `__SIZEOF_FLOAT__`, `__SIZEOF_WCHAR_T__` | `2`, `4`, `4`, `4` |
  | `__SIZEOF_LONG__`, `__SIZEOF_LONG_LONG__`, `__SIZEOF_POINTER__`, `__SIZEOF_DOUBLE__`, `__SIZEOF_SIZE_T__`, `__SIZEOF_PTRDIFF_T__` | `8` |
  | `__ORDER_LITTLE_ENDIAN__`, `__ORDER_BIG_ENDIAN__`, `__ORDER_PDP_ENDIAN__` | `1234`, `4321`, `3412` |
  | `__BYTE_ORDER__`, `__FLOAT_WORD_ORDER__` | `__ORDER_LITTLE_ENDIAN__` |

  No other `__NAME__` macro is predefined (not `__GNUC__`, `__OPTIMIZE__`, `__FILE__`, `__DATE__`), and no test uses one.
- **`long double`.** Out of scope in every wave, and no test uses it (W5.md says why).
- **Parsing headers.** From W4, the wave that brings `#include`, your compiler must parse these headers. That means
  `unsigned long` (as an 8-byte integer), `const`, `void *`, prototypes with `...` and with function-pointer
  parameters (qsort), and typedefs.

## 4. Rules

1. **Your compiler compiles the C itself.** It must never run gcc, clang, cc1, cpp, tcc, chibicc or any other C
   compiler or preprocessor, or any other program at all.
   - Your own environment has none either: gcc's C back end (cc1) and clang are not installed there, so `gcc` only
     assembles and links. The network reaches nothing but the model's own API.
   - The runner compiles inside a sandbox where no C compiler exists. It has no network, and it sees only your
     compiler's copy and the test sources.
   - A `.s` that carries a gcc or clang `.ident`, or that uses `.incbin` or `.include`, fails its test.
2. **You (the agent) do not compile C with gcc, clang or anything else either**, not even to get reference outputs.
   Use gcc only to assemble and link your compiler's `.s` (`gcc out.s -o prog`), and run the programs. The visible
   tests carry their expected outputs.
3. **No downloads.** Do not fetch C compilers, C test suites or other code from the network.
4. **No special cases.** Do not recognize specific test or benchmark programs (their text, names or hashes) and emit
   canned code for them. Your compiler must translate what it reads.

## 5. Tests and feedback

- **Visible tests.** `visible/Wn/` holds about a quarter of each wave's tests:
  - `<id>.c`
  - `<id>.expected`: the exact stdout
  - `<id>.exit`: the exit status
  - `manifest.json`: category, exit status and time limit per test
- **Hidden tests.** The other three quarters. They come from the same sources and categories, and the same feature
  tagger chose them.
- **Which tests a wave counts.** The hidden run after wave n counts the hidden tests of **waves 1 to n**. Earlier
  waves are regression tests.
- **How a test is tagged.** A test belongs to the first wave whose features cover everything it uses. Its category is
  that wave's defining feature, so a W3 test may use anything from W1 and W2.
- **What the hidden run tells you.** Only category lines, such as `W3 structs: 12 of 40 fail`, the totals, and for W6
  the benchmark lines. It never gives test names, sources or outputs.
- **Limits:**
  - each program has a run-time limit of at least 3 s (30 × gcc -O0's time for that test);
  - a batch has 60 s + 2 s per file;
  - each JVM gets 2 GB of heap (clj-run);
  - a program gets 4 GB of address space and 1 MB of stdout.
