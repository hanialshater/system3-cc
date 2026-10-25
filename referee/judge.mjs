#!/usr/bin/env node
// Benchmark 10: the judge (B10.md "The score").
//   node judge.mjs fast --dir=<compiler>   what an arm may run as often as it likes: build SQLite + sqlrun with the
//        compiler, the visible SQL corpus against gcc's outputs, the visible W1-W5 tests and Lua (B8's
//        visible-check.mjs, against the start's visible pass set), then the visible workloads under callgrind:
//        instructions executed against gcc -O2's build. Lines only.
//   node judge.mjs full --dir=<compiler> [--out=<json>]   the operator's: the same on the hidden corpus and the hidden
//        workloads, plus B8's hidden W7 runner (no W1-W5 regression against the start, Lua's hidden tests, Lua's runtime
//        score), the compile time and a determinism check. Category lines only, and a JSON summary last.
// Paths come from judge.json beside this script: b8, clj, material (sqlite3.c, sqlrun.c, include/, opts.txt),
// visCorpus / visWork (each <name>.sql with <name>.ref; visWork also o2.json: gcc -O2's instruction counts),
// hidCorpus / hidWork (the same, outside every arm's folders), startResults, startVisible, startRuntime.
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { spawn, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const mode = process.argv[2];
const opt = Object.fromEntries(process.argv.slice(3).filter((a) => a.startsWith("--")).map((a) => { const [k, ...v] = a.slice(2).split("="); return [k, v.join("=") || true]; }));
const cfg = JSON.parse(fs.readFileSync(path.join(HERE, "judge.json"), "utf8"));
const DIR = path.resolve(opt.dir ?? ".");
if (!["fast", "full"].includes(mode)) { console.error("usage: judge.mjs fast|full --dir=<compiler>"); process.exit(2); }
export const LIMITS = { compileSec: 60, runtime: 1.1, failRatio: 2.0 };
// Deviation (operator, 2026-10-24 14:05, during the run, both arms alike): compile time is wall-clock and depends on the
// machine's load (round 1's checks ran four builds beside V's session; two heads that compile far under 60 s alone were
// measured at 72 and 94 s). The fast judge (and the org's merge checks) apply 180 s, a sanity bound; the 60 s gate is
// applied by the full judge in the final scoring, on the quiet machine.
const COMPILE_LIMIT = mode === "fast" ? Number(opt["compile-limit"] ?? 180) : LIMITS.compileSec;
const POOL = Math.max(1, Math.min(4, os.cpus().length));
const r3 = (x) => Math.round(x * 1000) / 1000;
const geo = (xs) => Math.exp(xs.reduce((a, x) => a + Math.log(x), 0) / xs.length);
const lastJson = (s) => { for (const l of String(s ?? "").trim().split("\n").reverse()) { try { return JSON.parse(l); } catch {} } return null; };
const env = { ...process.env, B8_HOME: cfg.b8 }; delete env.NODE_TEST_CONTEXT;
fs.mkdirSync(path.join(cfg.b8, ".tmp"), { recursive: true });
const tmp = fs.mkdtempSync(path.join(cfg.b8, ".tmp", "b10-judge-"));
process.on("exit", () => fs.rmSync(tmp, { recursive: true, force: true }));
const say = (s) => console.log(`B10 ${s}`);

/** run a command, collecting stdout; resolves { code, out, err, ms, timedOut } */
const run = (cmd, args, { cwd, timeout, env: e = env }) => new Promise((resolve) => {
  const t0 = Date.now(), ch = spawn(cmd, args, { cwd, env: e, stdio: ["ignore", "pipe", "pipe"] });
  // bytes, not strings: decoding each pipe chunk on its own split UTF-8 characters at chunk boundaries (w-measure,
  // board #19, during the run: expr-0528 failed at random when its "é" straddled byte 4096)
  const outB = [], errB = []; let outN = 0, errN = 0, timedOut = false;
  const t = setTimeout(() => { timedOut = true; ch.kill("SIGKILL"); }, timeout);
  ch.stdout.on("data", (d) => { if (outN < 32 << 20) { outB.push(d); outN += d.length; } }); ch.stderr.on("data", (d) => { if (errN < 1 << 20) { errB.push(d); errN += d.length; } });
  ch.on("close", (code) => { clearTimeout(t); const outBuf = Buffer.concat(outB); resolve({ code, outBuf, out: outBuf.toString("utf8"), err: Buffer.concat(errB).toString("utf8"), ms: Date.now() - t0, timedOut }); });
});
async function pool(items, fn) { const res = new Array(items.length); let i = 0; await Promise.all(Array.from({ length: POOL }, async () => { while (i < items.length) { const k = i++; res[k] = await fn(items[k]); } })); return res; }

// ---- the build: sqlite3.c and sqlrun.c with the arm's compiler, linked by gcc. The compiler runs with C compilers
// blocked on its PATH (spec/B10.md "Rules": no calling gcc or any other compiler from the compiler).
const NOCC = path.join(tmp, "nocc"); fs.mkdirSync(NOCC);
for (const n of ["gcc", "cc", "clang", "tcc", "cpp", "cc1", "c99", "gcc-13", "clang-18", "x86_64-linux-gnu-gcc"]) fs.writeFileSync(path.join(NOCC, n), `#!/bin/sh\necho "B10 judge: ${n} is not available to the compiler" >&2\nexit 1\n`, { mode: 0o755 });
const ccEnv = { ...env, PATH: `${NOCC}:${env.PATH}` };
const OPTS = fs.readFileSync(path.join(cfg.material, "opts.txt"), "utf8").trim().split(/\s+/).filter(Boolean);
async function build(tag) {
  const inc = ["-I", path.join(cfg.material, "include"), "-I", cfg.material], s1 = path.join(tmp, `sqlite3-${tag}.s`), s2 = path.join(tmp, `sqlrun-${tag}.s`), exe = path.join(tmp, `sqlrun-${tag}`);
  const a = await run(cfg.clj, ["-m", "ccc.main", ...inc, ...OPTS, path.join(cfg.material, "sqlite3.c"), "-o", s1], { cwd: DIR, timeout: 600_000, env: ccEnv });
  if (a.code !== 0 || !fs.existsSync(s1)) return { ok: false, why: `compiling sqlite3.c failed (exit ${a.code}${a.timedOut ? ", timed out" : ""}): ${(a.err || a.out).trim().split("\n").slice(-3).join(" | ").slice(0, 400)}` };
  const b = await run(cfg.clj, ["-m", "ccc.main", ...inc, ...OPTS, path.join(cfg.material, "sqlrun.c"), "-o", s2], { cwd: DIR, timeout: 300_000, env: ccEnv });
  if (b.code !== 0 || !fs.existsSync(s2)) return { ok: false, why: `compiling sqlrun.c failed (exit ${b.code}): ${(b.err || b.out).trim().split("\n").slice(-3).join(" | ").slice(0, 400)}` };
  const l = spawnSync("gcc", [s1, s2, "-o", exe, "-lm"], { encoding: "utf8", timeout: 300_000 });
  if (l.status !== 0) return { ok: false, why: `linking failed: ${String(l.stderr).trim().split("\n").slice(0, 3).join(" | ").slice(0, 400)}` };
  return { ok: true, exe, s1, compileSec: r3(a.ms / 1000), sqlrunSec: r3(b.ms / 1000) };
}

// ---- the SQL corpus: every script's output against gcc's
async function corpus(exe, dir) {
  const names = fs.readdirSync(dir).filter((f) => f.endsWith(".sql")).sort();
  const res = await pool(names, async (n) => { const r = await run(exe, [path.join(dir, n)], { cwd: tmp, timeout: 20_000 }); const ref = fs.readFileSync(path.join(dir, n.replace(/\.sql$/, ".ref"))); return { n, ok: !r.timedOut && r.code === 0 && r.outBuf.equals(ref), why: r.timedOut ? "timeout" : r.code !== 0 ? `exit ${r.code}` : "output differs" }; });
  const fails = res.filter((x) => !x.ok), byFam = {};
  for (const f of fails) { const fam = f.n.replace(/-\d+\.sql$/, ""); byFam[fam] = (byFam[fam] ?? 0) + 1; }
  return { total: names.length, pass: names.length - fails.length, fails, byFam };
}

// ---- the workloads: instructions executed (callgrind), output checked, against gcc -O2's counts
async function workloads(exe, dir) {
  const o2 = JSON.parse(fs.readFileSync(path.join(dir, "o2.json"), "utf8"));
  const names = Object.keys(o2).sort();
  const res = await pool(names, async (n) => {
    const r = await run("valgrind", ["--tool=callgrind", `--callgrind-out-file=${path.join(tmp, `cg-${n}-${process.pid}.out`)}`, exe, path.join(dir, `${n}.sql`)], { cwd: tmp, timeout: 1800_000 });
    const m = r.err.match(/Collected : (\d+)/), ref = fs.readFileSync(path.join(dir, `${n}.ref`));
    fs.rmSync(path.join(tmp, `cg-${n}-${process.pid}.out`), { force: true });
    if (r.timedOut || r.code !== 0 || !m) return { n, ok: false, why: r.timedOut ? "timeout" : `exit ${r.code}` };
    if (!r.outBuf.equals(ref)) return { n, ok: false, why: "output differs from gcc's" };
    const ir = Number(m[1]); return { n, ok: true, ir, o2: o2[n], ratio: ir / o2[n] };
  });
  const ok = res.every((x) => x.ok);
  return { ok, ratio: ok ? r3(geo(res.map((x) => x.ratio))) : null, rows: res };
}
const wline = (w, label) => { for (const x of w.rows) say(`workload ${label}${x.n}: ${x.ok ? `${x.ir} instructions, ${r3(x.ratio)} x gcc -O2's ${x.o2}` : `FAILED (${x.why})`}`); };

if (mode === "fast") {
  const b = await build("fast");
  if (!b.ok) { say(`build: ${b.why}`); process.exit(1); }
  say(`build: ok (sqlite3.c compiled in ${b.compileSec} s; limit here ${COMPILE_LIMIT} s, a sanity bound under load${b.compileSec > COMPILE_LIMIT ? ": OVER" : ""}; the final scoring, on the quiet machine, requires ${LIMITS.compileSec} s)`);
  const c = await corpus(b.exe, cfg.visCorpus);
  say(`visible corpus: ${c.pass} of ${c.total} scripts give gcc's output${c.fails.length ? `; failing: ${c.fails.slice(0, 25).map((f) => `${f.n} (${f.why})`).join(", ")}${c.fails.length > 25 ? ", …" : ""}` : ""}`);
  const base = path.join(tmp, "baseline.json"); fs.copyFileSync(cfg.startVisible, base);
  // --skip-visible (the org's merge checks): the W1-W5/Lua check runs once on main at the end of each round instead
  const v = opt["skip-visible"] ? { status: 0, stdout: "" } : spawnSync(process.execPath, [path.join(HERE, "visible-check.mjs"), "--wave=W7", `--dir=${DIR}`, `--clj=${cfg.clj}`, `--baseline=${base}`], { encoding: "utf8", env, timeout: 3600_000, maxBuffer: 64 << 20 });
  const vf = String(v.stdout ?? "").split("\n").filter((l) => /^failed: /.test(l));
  if (!opt["skip-visible"]) say(v.status === 0 ? "visible W1-W5 and Lua: ok (nothing the start passes fails)" : `visible W1-W5 and Lua: ${vf.length ? vf.map((l) => l.replace(/^failed: /, "")).join("; ").slice(0, 1500) : `exit ${v.status}`}`);
  const gateOk = c.pass === c.total && v.status === 0 && b.compileSec <= COMPILE_LIMIT;
  if (!gateOk) { say("fast check: FAILED (fix the failures above; workloads not counted)"); process.exit(1); }
  say("fast check: ok");
  const w = await workloads(b.exe, cfg.visWork); wline(w, "");
  say(w.ok ? `fast ratio: ${w.ratio} (geometric mean of the visible workloads' instruction ratios against gcc -O2; lower is better; the start is about 1.79)` : "fast ratio: none (a workload failed)");
  process.exit(w.ok ? 0 : 1);
}

// ---- full ----
const b = await build("full");
const summary = { dir: DIR, build: b.ok, compileSec: b.compileSec ?? null };
if (!b.ok) { say(`build: ${b.why}`); summary.gateOk = false; summary.score = LIMITS.failRatio; summary.why = b.why; }
else {
  const b2 = await build("again"); // determinism
  const same = b2.ok && fs.readFileSync(b.s1).equals(fs.readFileSync(b2.s1));
  const c = await corpus(b.exe, cfg.hidCorpus);
  const res = path.join(tmp, "results.json");
  const h = spawnSync(process.execPath, [path.join(cfg.b8, "run-hidden-w7.mjs"), `--dir=${DIR}`, `--results=${res}`, "--no-refs"], { encoding: "utf8", env, timeout: 4 * 3600_000, maxBuffer: 64 << 20 });
  const hj = lastJson(h.stdout), start = JSON.parse(fs.readFileSync(`${cfg.startResults}.w15.json`, "utf8"));
  let now = {}; try { now = JSON.parse(fs.readFileSync(`${res}.w15.json`, "utf8")).tests ?? {}; } catch {}
  const regressed = Object.entries(start.tests).filter(([id, ok]) => ok && now[id] !== true).map(([id]) => id), w7 = hj?.w7 ?? null;
  const gate = {
    corpus: { pass: c.pass, total: c.total, byFamily: c.byFam, ok: c.pass === c.total },
    w15: { regressions: regressed.length, ok: !!hj && regressed.length === 0 },
    lua: { pass: w7?.tests?.pass ?? 0, total: w7?.tests?.total ?? 22, ok: !!w7 && w7.build.failed === 0 && w7.build.linked && w7.build.sanity === "ok" && w7.tests.pass === w7.tests.total },
    luaRuntime: { score: w7?.bench?.score ?? null, limit: r3(cfg.startRuntime * LIMITS.runtime), ok: w7?.bench?.score != null && w7.bench.score <= cfg.startRuntime * LIMITS.runtime },
    compile: { sec: b.compileSec, limit: LIMITS.compileSec, ok: b.compileSec <= LIMITS.compileSec },
    deterministic: { ok: same },
  };
  const gateOk = Object.values(gate).every((g) => g.ok);
  for (const [k, g] of Object.entries(gate)) say(`gate ${k}: ${g.ok ? "ok" : "FAILED"} ${JSON.stringify(g)}`);
  const w = await workloads(b.exe, cfg.hidWork);
  say(`hidden workloads: ${w.ok ? `ratio ${w.ratio}` : `a workload failed: ${w.rows.filter((x) => !x.ok).map((x) => x.why).join("; ")}`}`);
  Object.assign(summary, { gate, gateOk: gateOk && w.ok, ratio: w.ratio, workloads: w.rows.map(({ n, ok, ir, o2, ratio }) => ({ n, ok, ir, o2, ratio: ratio ? r3(ratio) : null })), score: gateOk && w.ok ? w.ratio : LIMITS.failRatio });
}
say(`score: ${summary.score}${summary.gateOk ? "" : " (the gate failed)"}`);
if (opt.out) fs.writeFileSync(path.resolve(opt.out), JSON.stringify(summary, null, 1));
console.log(JSON.stringify(summary));
