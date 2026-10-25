#!/usr/bin/env node
// Benchmark 8, S's integration check (K31: code runs it after each owner's hand-in, on a clean copy of the commit as
// made). It compiles the visible tests of waves 1..n (spec/bench too for W6) with the compiler in --dir (cwd by
// default) in batch mode, links each .s with `gcc out.s -o prog`, runs it and compares stdout and exit status.
//   node visible-check.mjs --wave=Wn [--dir=.] --clj=<clj-run> [--baseline=<json>] [--jobs=4]
// It fails (exit 1) only on a regression: a visible test that passed at the last passing check of this run (the
// baseline file, kept by code outside every arm's reach) and fails now; one `failed: <Wk category>: …` line per
// category, so K30 routes it to its owner. Tests not passing yet are listed as `# ` lines (not findings). A passing
// check writes the new pass set as the baseline. No baseline: nothing can regress (the first check of the run).
// W7 (spec/W7.md): the visible tests of waves 1..5, then Lua built with the compiler (spec/w7/build.json: one batch,
// `gcc *.s -o lua -lm`) and the visible W7 files of Lua's test suite, each in a copy of visible/W7 (`W7 <category>`).
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";

const opt = Object.fromEntries(process.argv.slice(2).filter((a) => a.startsWith("--")).map((a) => { const [k, ...v] = a.slice(2).split("="); return [k, v.join("=") || true]; }));
const N = Number(String(opt.wave ?? "").replace(/^W/i, ""));
if (!(N >= 1 && N <= 7) || !opt.clj) { console.error("usage: visible-check.mjs --wave=Wn --clj=<clj-run> [--dir=.] [--baseline=<json>]"); process.exit(2); }
const DIR = path.resolve(opt.dir ?? "."), JOBS = Number(opt.jobs ?? Math.min(4, os.cpus().length));
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), "b8-vis-"));
process.on("exit", () => { if (!opt.keep) fs.rmSync(TMP, { recursive: true, force: true }); });

function run(cmd, args, { cwd = TMP, timeout = 10000, input = null } = {}) {
  return new Promise((resolve) => {
    const ch = spawn(cmd, args, { cwd, stdio: [input == null ? "ignore" : "pipe", "pipe", "pipe"], detached: true });
    if (input != null) { ch.stdin.on("error", () => {}); ch.stdin.end(input); }
    const out = []; let n = 0, timedOut = false, err = "";
    const t = setTimeout(() => { timedOut = true; try { process.kill(-ch.pid, "SIGKILL"); } catch {} }, timeout);
    ch.stdout.on("data", (d) => { n += d.length; if (n <= 1 << 20) out.push(d); });
    ch.stderr.on("data", (d) => { if (err.length < 4000) err += d; });
    ch.on("error", (e) => { err += String(e); });
    ch.on("close", (code, signal) => { clearTimeout(t); resolve({ code, signal, stdout: Buffer.concat(out).toString("latin1"), err, timedOut }); });
  });
}
const SIG = { SIGABRT: 6, SIGSEGV: 11, SIGFPE: 8, SIGKILL: 9, SIGBUS: 7, SIGILL: 4, SIGTRAP: 5 };
async function pool(items, n, fn) { let i = 0; await Promise.all(Array.from({ length: Math.min(n, items.length) }, async () => { while (i < items.length) { const k = i++; await fn(items[k]); } })); }

// the tests: visible/W1..W(min(n,5)), plus spec/bench for W6
const tests = [];
for (let w = 1; w <= Math.min(N, 5); w++) {
  const d = path.join(DIR, "visible", `W${w}`);
  let man = {}; try { man = JSON.parse(fs.readFileSync(path.join(d, "manifest.json"), "utf8")); } catch { continue; }
  for (const [id, m] of Object.entries(man)) tests.push({ id, wave: w, category: m.category, src: path.join(d, `${id}.c`), expected: path.join(d, `${id}.expected`), exit: m.exit ?? 0, timeoutMs: m.timeoutMs ?? 5000 });
}
if (N === 6) { const d = path.join(DIR, "spec", "bench"); for (const f of (fs.existsSync(d) ? fs.readdirSync(d) : []).filter((x) => x.endsWith(".c")).sort()) tests.push({ id: `bench-${f.slice(0, -2)}`, wave: 6, category: "bench", src: path.join(d, f), expected: path.join(d, f.replace(/\.c$/, ".expected")), exit: 0, timeoutMs: 30000 }); }
tests.forEach((t, i) => { t.name = `v${String(i + 1).padStart(4, "0")}`; t.s = path.join(TMP, `${t.name}.s`); t.bin = path.join(TMP, t.name); });

// compile in JOBS batches (the contract's batch mode); a batch that dies loses its first unwritten file, then restarts
const inc = path.join(DIR, "spec", "include");
const written = (t) => { try { return fs.statSync(t.s).size > 0; } catch { return false; } };
const lists = Array.from({ length: JOBS }, () => []); tests.forEach((t, i) => lists[i % JOBS].push(t));
await Promise.all(lists.map(async (list, k) => {
  let q = list;
  for (let n = 0; q.length && n < 20; n++) {
    const lf = path.join(TMP, `list-${k}-${n}.txt`);
    fs.writeFileSync(lf, q.map((t) => `${t.src} ${t.s}`).join("\n") + "\n");
    const r = await run(opt.clj, ["-m", "ccc.main", "-I", inc, "--batch", lf], { cwd: DIR, timeout: 60000 + 2000 * q.length });
    const missing = q.filter((t) => !written(t));
    if (!missing.length) return;
    if (r.code === 0 && !r.timedOut) { for (const t of missing) t.fail = "compile"; return; }
    missing[0].fail = r.timedOut ? "compile-timeout" : "compile-crash";
    q = missing.slice(1);
  }
  for (const t of q) t.fail ??= "not-reached";
}));
for (const t of tests) if (!t.fail && !written(t)) t.fail = "compile";
await pool(tests.filter((t) => !t.fail), JOBS, async (t) => { const r = await run("gcc", [t.s, "-o", t.bin], { timeout: 60000 }); if (r.code !== 0) t.fail = "link"; });
await pool(tests.filter((t) => !t.fail), JOBS, async (t) => {
  const r = await run(t.bin, [], { timeout: t.timeoutMs + 1000 });
  const st = r.signal ? 128 + (SIG[r.signal] ?? 64) : r.code;
  let exp = ""; try { exp = fs.readFileSync(t.expected, "latin1"); } catch {}
  if (r.timedOut) t.fail = "timeout"; else if (r.stdout !== exp || st !== t.exit) t.fail = "output";
});

// W7: build Lua with the compiler, run the visible W7 files of Lua's test suite
let w7Note = null;
if (N === 7) {
  const vis = path.join(DIR, "visible", "W7"), lua = path.join(DIR, "spec", "w7", "lua");
  let man = {}, build = null; try { man = JSON.parse(fs.readFileSync(path.join(vis, "manifest.json"), "utf8")); build = JSON.parse(fs.readFileSync(path.join(DIR, "spec", "w7", "build.json"), "utf8")); } catch {}
  const w7 = Object.entries(man).map(([file, m]) => ({ id: `w7-${file}`, file, wave: 7, category: m.category, timeoutMs: m.timeoutMs ?? 30000 }));
  const out = path.join(TMP, "lua7"); fs.mkdirSync(out, { recursive: true });
  let ok = !!build;
  if (build) {
    const lf = path.join(TMP, "lua7.list");
    fs.writeFileSync(lf, build.sources.map((f) => `${path.join(lua, f)} ${path.join(out, f.replace(/\.c$/, ".s"))}`).join("\n") + "\n");
    await run(opt.clj, ["-m", "ccc.main", "-I", path.join(DIR, "spec", "w7", "include"), "-I", lua, ...build.defines.flatMap((d) => ["-D", d]), "--batch", lf], { cwd: DIR, timeout: 900000 });
    const missing = build.sources.filter((f) => { try { return !fs.statSync(path.join(out, f.replace(/\.c$/, ".s"))).size; } catch { return true; } });
    if (missing.length) { ok = false; w7Note = `# W7 build: ${missing.length} of ${build.sources.length} Lua sources do not compile (${missing.slice(0, 8).join(", ")}${missing.length > 8 ? ", …" : ""})`; }
    else { const r = await run("gcc", [...build.sources.map((f) => path.join(out, f.replace(/\.c$/, ".s"))), "-o", path.join(out, "lua"), "-lm"], { timeout: 120000 }); if (r.code !== 0) { ok = false; w7Note = `# W7 build: the link fails: ${r.err.split("\n").find(Boolean)?.slice(0, 200) ?? ""}`; } else w7Note = "# W7 build: Lua compiles and links"; }
  } else w7Note = "# W7 build: spec/w7/build.json or visible/W7/manifest.json is missing";
  await pool(w7, JOBS, async (t) => {
    if (!ok) { t.fail = "build"; return; }
    const d = fs.mkdtempSync(path.join(TMP, "w7t-")); fs.cpSync(vis, d, { recursive: true });
    const r = await run(path.join(out, "lua"), ["w7test.lua", "full", t.file], { cwd: d, timeout: t.timeoutMs + 1000, input: "" });
    if (r.timedOut) t.fail = "timeout"; else if (r.code !== 0 || !r.stdout.split("\n").includes(`W7-TEST-OK ${t.file}`)) t.fail = "fail";
    fs.rmSync(d, { recursive: true, force: true });
  });
  tests.push(...w7);
}

// regressions against the baseline
let base = null; if (opt.baseline) { try { base = JSON.parse(fs.readFileSync(opt.baseline, "utf8")); } catch {} }
const passNow = Object.fromEntries(tests.map((t) => [t.id, !t.fail]));
const reg = tests.filter((t) => t.fail && base?.pass?.[t.id] === true);
const cats = new Map();
for (const t of tests) { const k = t.wave === 6 ? "W6 bench" : `W${t.wave} ${t.category}`; const c = cats.get(k) ?? { reg: [], fail: [], total: 0 }; c.total++; if (t.fail) (base?.pass?.[t.id] === true ? c.reg : c.fail).push(`${t.id} (${t.fail})`); cats.set(k, c); }
for (const [k, c] of cats) if (c.reg.length) console.log(`failed: ${k}: ${c.reg.length} visible test(s) that passed at the last passing integration check fail now: ${c.reg.slice(0, 12).join(", ")}`);
for (const [k, c] of cats) if (c.fail.length) console.log(`# ${k}: ${c.fail.length} of ${c.total} visible tests do not pass yet (not a regression)`);
const pass = tests.filter((t) => !t.fail).length;
if (w7Note) console.log(w7Note);
console.log(`# visible tests of waves 1..${Math.min(N, 5)}${N === 6 ? " and the benchmarks" : N === 7 ? " and W7's Lua tests" : ""}: ${pass} of ${tests.length} pass, ${reg.length} regression(s)`);
if (!reg.length && opt.baseline) { fs.mkdirSync(path.dirname(opt.baseline), { recursive: true }); fs.writeFileSync(opt.baseline, JSON.stringify({ wave: `W${N}`, t: new Date().toISOString(), dir: DIR, pass: { ...(base?.pass ?? {}), ...passNow } }, null, 1)); }
process.exit(reg.length ? 1 : 0);
