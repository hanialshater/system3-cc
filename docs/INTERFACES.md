# Interfaces between the compiler's parts (architect, after round 1)

The pipeline, in this order. A change that crosses a boundary must keep that boundary's contract
or update this file in the same commit.

1. **preproc → lexer → parser** (`preproc.clj`, `lexer.clj`, `parser.clj`): tokens in, the typed AST
   program out (`parse-program`). Functions carry `:locals` (each with a unique `:id` and `:type`).
2. **AST passes** (none yet; new ones go in `src/ccc/passes/*.clj`, called from `driver.clj` between
   parse and generate). Contract: program in, program of the same shape out, so codegen needs no
   change. A pass that introduces variables (inlining, SRA) adds them to the enclosing function's
   `:locals` with fresh ids (never reuse a callee's id), and keeps `:static`, `:align`, `volatile`,
   address-taken facts true. Inlining belongs here, not in codegen or mach.
3. **codegen** (`codegen.clj`, `gen-function`): per function, emits the *body text* (AT&T, one
   instruction per line) and returns a finisher closure run on the pool. The body text contract that
   mach relies on:
   - frame slots are `N(%rbp)`; `:exposed` lists every frame range whose address may escape
     (aggregates, address-taken, volatile, VLA, over-aligned); any other slot is private to the body;
   - returns jump to `.L.return.<name>`; nothing in the body touches the prologue's save slots;
   - callee-saved registers are *not* saved by the body: the finisher saves exactly those the
     optimised body still mentions.
4. **mach** (`mach.clj`, `optimize-body`): body text in, body text out, same contract out as in. It is
   the **one** machine-level framework (parsed instructions with use/def masks, blocks, liveness).
   New asm-level optimisations are passes inside it with the signature `items -> [items changed?]`
   and an `off?` switch; do not add a second CFG/liveness module (w-sceptic's `asmopt.clj` overlaps
   it: port its distinct passes, e.g. backward move coalescing, into mach instead of merging the file).
   Unknown instructions must be barriers (`all-mask` use/def), never guessed.
5. **finisher** (end of `gen-function`): prologue, callee saves, body, `.L.return.`, restores, epilogue.
   Shrink-wrapping / push-pop saves / rsp-relative frames change *this* step only.
6. **frame finalisation** (future, e.g. w-lean's `frame.clj`, rbp → rsp / red zone): runs **last**, on
   the finished function text, after mach and the finisher. No pass after it may assume `%rbp` slots.

Rules for all parts:
- Determinism: pools must reassemble functions in input order; never let hash-set/map iteration order
  reach the output (sort by id or position).
- Every optimisation has an env-var off switch (`CCC_NOASMOPT`, mach `off?` names, ...) so a bisect can
  isolate it.
- Compile-time budget is shared (limit 60 s; main is ~12 s wall on 4 cores): a pass that costs more
  than ~5 s on sqlite3.c needs a measured gain to justify it.
- Judge flake (board #19): a failure of `expr-0528` alone is not a reason to revert; compare bytes.

## Notes from w-lean (round 2)
- `src/ccc/passes/inline.clj` (AST pass, called at the start of `codegen/generate`) inlines callees of
  at most `CCC_INLINE_LIMIT` (100) nodes as statement expressions. Its fresh variable ids are
  `[:ginl fname n]` (codegen's expression inliner uses `[:inl fname n]`: never share a tag). Scope marks
  (`:scope-id`) on the statement expression and on the callee's root block keep the register-sharing rule
  (block-path prefix = live together) valid for codegen's own inliner running after this pass.
  Off: `CCC_NOGINLINE`. Growth is about 3.4x the node count of sqlite3.c, compile CPU about 2.5x.
  `CCC_INLINE_COLD` limits callee size outside loops (default = the limit; smaller values lose more).
- `src/ccc/frame.clj` is step 6, run at the end of the finisher on each function's text. It converts
  only when every rbp/rsp use is understood and the stack depth is consistent at every jump.
  Off: `CCC_NOFRAME`.
- Round 2 merge (architect): w-sceptic also wrote a frame finaliser. Step 6 is now `frame/finalize`
  in ns ccc.frame: w-sceptic's converter (`finalize*`: rsp frame, red zone, small epilogues copied to
  early returns) runs first; when it leaves a function unchanged, w-lean's converter (`rsp-frame`, now
  in ns ccc.frame-rsp) is tried. Codegen calls only `frame/finalize`. Off: `CCC_NOFRAME` (both).
