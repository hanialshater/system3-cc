#!/usr/bin/env node
// The demo site: a static page (site/dist/index.html) built from real runs, nothing typed by hand.
//   - the benchmark programs in spec/bench/*.c, compiled by this compiler and by gcc -O2: their output (must match)
//     and the instructions each executes (valgrind, exact), with one function's assembly side by side
//   - Lua 5.4.7 built by this compiler, running the Lua benchmarks in spec/w7/bench against their expected output
//   - the referee's fast check: SQLite built by this compiler, its visible workloads' instructions against gcc -O2's
// gcc only assembles and links this compiler's output, and builds the reference: the compiler never calls it.
//   node site/build.mjs [--out=site/dist] [--no-referee] [--no-lua]
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url)), ROOT = path.join(HERE, "..");
const opt = Object.fromEntries(process.argv.slice(2).filter((a) => a.startsWith("--")).map((a) => { const [k, ...v] = a.slice(2).split("="); return [k, v.join("=") || true]; }));
const OUT = path.resolve(opt.out ?? path.join(HERE, "dist")), W = path.join(ROOT, ".work", "site"); fs.mkdirSync(W, { recursive: true });
const run = (cmd, args, o = {}) => spawnSync(cmd, args, { cwd: ROOT, encoding: "utf8", maxBuffer: 256 << 20, timeout: 1800_000, ...o });
const instr = (bin) => { const r = run("valgrind", ["--tool=cachegrind", "--cache-sim=no", `--cachegrind-out-file=${W}/cg.out`, bin]); const m = /I\s+refs:\s+([\d,]+)/.exec(r.stderr); return { n: m ? Number(m[1].replace(/,/g, "")) : null, out: r.stdout }; };
// one function's body from an assembly file (GNU as, AT&T): from its label to the next global function
const fnAsm = (s, name) => { const L = s.split("\n"), i = L.findIndex((l) => l.trim() === `${name}:`); if (i < 0) return null; let j = i + 1; while (j < L.length && !/^\s*\.(globl|type)\s/.test(L[j]) && !/^[A-Za-z_]\w*:$/.test(L[j].trim())) j++; return L.slice(i, j).filter((l) => !/^\s*\.(cfi_|p2align|size|ident|section|loc|file)/.test(l)).join("\n").trim(); };

// 1. the benchmark programs, at sizes valgrind can count in seconds (tools/icount.py's)
const SCALE = { fib: { N: "27" }, sieve: { N: "1000000", ROUNDS: "1" }, matmul: { N: "120" }, sort: { N: "200000" }, bintree: { DEPTH: "12" }, fannkuch: { N: "8" }, crc: { MB: "1" }, vm: { ITER: "300000" } };
const FN = { fib: "fib", sieve: "main", matmul: "main", sort: "main", bintree: "check", fannkuch: "main", crc: "main", vm: "run" };
const progs = Object.keys(SCALE).filter((n) => fs.existsSync(path.join(ROOT, "spec/bench", `${n}.c`)));
let list = ""; for (const n of progs) { let src = fs.readFileSync(path.join(ROOT, "spec/bench", `${n}.c`), "utf8"); for (const [k, v] of Object.entries(SCALE[n])) src = src.replace(new RegExp(`#define ${k} \\S+`), `#define ${k} ${v}`); fs.writeFileSync(path.join(W, `${n}.c`), src); list += `${W}/${n}.c ${W}/${n}.s\n`; }
fs.writeFileSync(path.join(W, "list"), list);
const t0 = Date.now(); run(path.join(ROOT, "run.sh"), ["-I", "spec/include", "--batch", path.join(W, "list")]); const compileSec = (Date.now() - t0) / 1000;
const programs = progs.map((n) => {
  const c = path.join(W, `${n}.c`), ours = path.join(W, n), ref = path.join(W, `${n}.gcc`);
  run("gcc", [path.join(W, `${n}.s`), "-o", ours, "-lm"]); run("gcc", ["-O2", "-w", "-I", "spec/include", c, "-o", ref, "-lm"]); run("gcc", ["-O2", "-w", "-S", "-fno-asynchronous-unwind-tables", c, "-o", `${ref}.s`]);
  const a = instr(ours), b = instr(ref), src = fs.readFileSync(c, "utf8");
  const fn = FN[n], oursAsm = fs.existsSync(`${ours}.s`) ? fnAsm(fs.readFileSync(`${ours}.s`, "utf8"), fn) : null, gccAsm = fnAsm(fs.readFileSync(`${ref}.s`, "utf8"), fn);
  return { name: n, lines: src.split("\n").length, source: src.slice(0, 6000), output: a.out.trim().slice(0, 600), same: a.out === b.out && a.out.length > 0, ours: a.n, gcc: b.n, fn, oursAsm: oursAsm?.slice(0, 5000) ?? null, gccAsm: gccAsm?.slice(0, 5000) ?? null, oursAsmLines: oursAsm?.split("\n").length ?? null, gccAsmLines: gccAsm?.split("\n").length ?? null };
});

// 2. Lua 5.4.7, compiled by this compiler, running the Lua benchmarks
let lua = null;
if (!opt["no-lua"]) {
  const b = run("bash", [path.join(ROOT, "tools/buildlua.sh")]), bin = path.join(ROOT, ".work/lua7/lua");
  if (fs.existsSync(bin)) lua = { files: 33, scripts: fs.readdirSync(path.join(ROOT, "spec/w7/bench")).filter((f) => f.endsWith(".lua")).sort().map((f) => {
    const t = Date.now(), r = run(bin, [f], { cwd: path.join(ROOT, "spec/w7/bench") }), sec = (Date.now() - t) / 1000, exp = path.join(ROOT, "spec/w7/bench", f.replace(/\.lua$/, ".expected"));
    return { name: f, sec, same: fs.existsSync(exp) && r.stdout === fs.readFileSync(exp, "utf8"), output: r.stdout.trim().slice(0, 300) };
  }), log: /compiled \d+ of 33/.exec(b.stdout)?.[0] ?? null };
}

// 3. the referee's fast check: SQLite built by this compiler, and its visible workloads
let referee = null;
if (!opt["no-referee"]) {
  const r = run("node", ["referee/judge.mjs", "fast", "--dir=."]), L = r.stdout.split("\n");
  const build = /sqlite3\.c compiled in ([\d.]+) s/.exec(r.stdout)?.[1], corpus = /visible corpus: (\d+) of (\d+) scripts/.exec(r.stdout);
  referee = { compileSec: build ? Number(build) : null, corpusPass: corpus ? +corpus[1] : null, corpusAll: corpus ? +corpus[2] : null, regressions: /visible W1-W5 and Lua: ok/.test(r.stdout) ? 0 : null,
    workloads: L.map((l) => /workload (\S+): (\d+) instructions, ([\d.]+) x gcc -O2's (\d+)/.exec(l)).filter(Boolean).map((m) => ({ name: m[1], ours: +m[2], ratio: +m[3], gcc: +m[4] })), ratio: Number(/fast ratio: ([\d.]+)/.exec(r.stdout)?.[1] ?? NaN) || null };
}

const org = JSON.parse(fs.readFileSync(path.join(ROOT, "company/org.json"), "utf8")).roles, company = JSON.parse(fs.readFileSync(path.join(ROOT, "company/company.json"), "utf8"));
const data = { built: new Date().toISOString(), commit: run("git", ["rev-parse", "--short", "HEAD"]).stdout.trim(), compileSec, programs, lua, referee,
  hidden: { ratio: 1.256, start: 1.791, source: "docs/BENCH10.md" }, org: org.filter((o) => o.reportsTo && o.id !== "referee").map((o) => ({ id: o.id, title: o.title, focus: o.focus, model: o.model })), cost: company.budget.lines.reduce((a, l) => a + (l.spent ?? 0), 0), lines: company.budget.lines.map((l) => ({ id: l.id, title: l.title, spent: l.spent })) };
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, "index.html"), fs.readFileSync(path.join(HERE, "page.html"), "utf8").replace("/*DATA*/null", JSON.stringify(data).replace(/</g, "\\u003c")));
console.log(JSON.stringify({ out: OUT, programs: programs.map((p) => `${p.name} ${p.same ? "same" : "DIFFERS"} ${p.ours && p.gcc ? (p.ours / p.gcc).toFixed(2) : "?"}x`), lua: lua?.scripts.map((s) => `${s.name} ${s.same ? "ok" : "DIFFERS"}`), referee: referee && { ratio: referee.ratio, corpus: `${referee.corpusPass}/${referee.corpusAll}` } }));
