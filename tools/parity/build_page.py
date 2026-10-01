"""The parity matrix page: every inventory row with its verified status (2026-09-29).

    python tools/parity/build_page.py <docs/parity folder> <out.html>

Reads inventory-*.tsv (what each PC tracker has, extracted from its source), verify-*.tsv
(what KaizoCore has, checked row by row) and gaps.tsv (the fix list, condensed by hand) and
writes one self-contained HTML page: a summary per tracker, the fix list, then every row,
filterable by tracker, status and text.
"""
import sys, json, pathlib, html

root = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2])
TAB = chr(9)

inventory = {}
for p in sorted(root.glob("inventory-*.tsv")):
    for line in p.read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or not line.strip():
            continue
        f = line.split(TAB)
        if len(f) >= 6:
            inventory[f[0]] = {"id": f[0], "tracker": f[1], "area": f[2], "key": f[3], "text": f[4], "kind": f[5]}

verified = {}
for p in sorted(root.glob("verify-*.tsv")):
    for line in p.read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or not line.strip():
            continue
        f = line.split(TAB)
        if len(f) >= 2 and f[0] in inventory:
            verified[f[0]] = {"status": f[1].strip().upper(), "ours": f[2].strip() if len(f) > 2 else "", "note": f[3].strip() if len(f) > 3 else ""}

gaps = []
gp = root / "gaps.tsv"
if gp.exists():
    for line in gp.read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or not line.strip():
            continue
        f = line.split(TAB)
        if len(f) >= 3:
            gaps.append([f[0].strip(), f[1].strip().upper(), f[2].strip(), f[3].strip() if len(f) > 3 else ""])

rows = []
for rid, r in inventory.items():
    if r["tracker"] == "gen2":
        continue   # textually identical to gen1; verified once there
    v = verified.get(rid, {"status": "UNCHECKED", "ours": "", "note": ""})
    rows.append([r["id"], r["tracker"], r["area"], r["key"], r["text"], r["kind"], v["status"], v["ours"], v["note"]])

data = json.dumps(rows, ensure_ascii=False)
gap_data = json.dumps(gaps, ensure_ascii=False)
page = """<title>KaizoCore Tracker Parity</title>
<style>
/* Layout: summary strip of per-tracker status bars, a sticky filter row, then the full matrix. */
:root {
  --bg: #f4f6f3; --panel: #ffffff; --fg: #1d2420; --muted: #5f6b64; --line: #d7ddd8; --accent: #2f6f4e;
  --done: #2e7d4f; --partial: #b7791f; --missing: #b83b3b; --na: #7d8680; --unchecked: #6b5fb3;
  --display: "Press Start 2P", ui-monospace, monospace;
  --body: "Atkinson Hyperlegible", system-ui, sans-serif;
  --mono: "JetBrains Mono", ui-monospace, Menlo, monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #121614; --panel: #1a201c; --fg: #e3e9e5; --muted: #9aa69f; --line: #2c3530; --accent: #6fc39a;
  --done: #5cc48a; --partial: #e0a948; --missing: #ef7b7b; --na: #8b948f; --unchecked: #a79cf0; color-scheme: dark } }
:root[data-theme="dark"] {
  --bg: #121614; --panel: #1a201c; --fg: #e3e9e5; --muted: #9aa69f; --line: #2c3530; --accent: #6fc39a;
  --done: #5cc48a; --partial: #e0a948; --missing: #ef7b7b; --na: #8b948f; --unchecked: #a79cf0; color-scheme: dark }
body { background: var(--bg); color: var(--fg); font-family: var(--body); font-size: 15px; line-height: 1.45; }
.wrap { padding-inline: 16px; padding-block: 20px 40px; max-width: 1200px; margin: 0 auto; display: grid; gap: 18px; }
h1 { font-family: var(--display); font-size: 16px; letter-spacing: .04em; margin: 0; text-wrap: balance; }
.lede { color: var(--muted); max-width: 70ch; margin: 0; }
.summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(260px, 100%), 1fr)); gap: 12px; }
.card { background: var(--panel); border: 1px solid var(--line); border-radius: 6px; padding: 12px 14px; display: grid; gap: 8px; min-width: 0; }
.card h2 { margin: 0; font-size: 13px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); }
.bar { display: flex; height: 10px; border-radius: 3px; overflow: hidden; background: var(--line); }
.bar span { display: block; height: 100%; }
.counts { display: flex; flex-wrap: wrap; gap: 6px 12px; font-size: 13px; font-variant-numeric: tabular-nums; }
.counts b { font-weight: 700; }
.filters { position: sticky; top: env(safe-area-inset-top, 0px); z-index: 2; background: var(--bg); padding-block: 8px; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; border-bottom: 1px solid var(--line); }
.filters select, .filters input { font: inherit; padding: 6px 8px; border: 1px solid var(--line); border-radius: 4px; background: var(--panel); color: var(--fg); }
.filters input { flex: 1 1 220px; min-width: 0; }
.filters label { font-size: 13px; color: var(--muted); display: flex; gap: 6px; align-items: center; }
.shown { font-size: 13px; color: var(--muted); font-variant-numeric: tabular-nums; }
.tablewrap { overflow-x: auto; background: var(--panel); border: 1px solid var(--line); border-radius: 6px; }
table { border-collapse: collapse; width: 100%; font-size: 13.5px; }
th, td { text-align: left; vertical-align: top; padding: 7px 10px; border-bottom: 1px solid var(--line); }
th { font-size: 12px; letter-spacing: .06em; text-transform: uppercase; color: var(--muted); background: var(--panel); }
td.id, td.ours { font-family: var(--mono); font-size: 12px; }
td.ours { color: var(--muted); max-width: 260px; overflow-wrap: anywhere; }
td.area { color: var(--muted); max-width: 200px; overflow-wrap: anywhere; }
td.text { max-width: 360px; overflow-wrap: anywhere; }
.pill { display: inline-block; font-size: 11px; font-weight: 700; letter-spacing: .06em; padding: 2px 7px; border-radius: 999px; color: var(--panel); white-space: nowrap; }
.s-DONE { background: var(--done); } .s-PARTIAL { background: var(--partial); } .s-MISSING { background: var(--missing); }
.s-NA { background: var(--na); } .s-UNCHECKED { background: var(--unchecked); }
.kind { font-size: 11px; color: var(--muted); text-transform: uppercase; letter-spacing: .06em; }
.fixes { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(340px, 100%), 1fr)); gap: 12px; align-items: start; }
.fixes h2 { margin: 0; font-size: 13px; letter-spacing: .08em; text-transform: uppercase; color: var(--muted); }
.fixes .tally { font-size: 12.5px; color: var(--muted); font-variant-numeric: tabular-nums; }
.fixes ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
.fixes li { display: grid; grid-template-columns: 64px 1fr; gap: 8px; align-items: start; min-width: 0; }
.fixes li .t { font-weight: 700; font-size: 13.5px; }
.fixes li .d { color: var(--muted); font-size: 12.5px; overflow-wrap: anywhere; }
.fixes li .pill { justify-self: start; margin-top: 1px; }
.s-BUG { background: var(--missing); } .s-OPEN { background: var(--partial); } .s-FIXED { background: var(--done); } .s-DECIDE { background: var(--unchecked); }
h2.section { font-family: var(--display); font-size: 12px; letter-spacing: .04em; margin: 6px 0 0; }
button.more { font: inherit; padding: 8px 14px; border: 1px solid var(--line); background: var(--panel); color: var(--fg); border-radius: 4px; cursor: pointer; justify-self: center; }
button.more:focus-visible, .filters select:focus-visible, .filters input:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
</style>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible:wght@400;700&family=JetBrains+Mono:wght@400&family=Press+Start+2P&display=swap">
<div class="wrap">
  <h1>KaizoCore tracker parity</h1>
  <p class="lede">Every user-facing string, option and screen of the PC IronMON trackers, pulled out of their source code, and each one checked against KaizoCore's code. Gen 2's list is the same text as Gen 1's, so Game Boy is checked once.</p>
  <div class="summary" id="summary"></div>
  <h2 class="section">Fix list</h2>
  <div class="fixes" id="fixes"></div>
  <h2 class="section">Every row</h2>
  <div class="filters">
    <label for="f-tracker">Tracker <select id="f-tracker"><option value="">All</option><option value="gba">GBA</option><option value="nds">DS</option><option value="gen1">Game Boy</option></select></label>
    <label for="f-status">Status <select id="f-status"><option value="">All</option><option>MISSING</option><option>PARTIAL</option><option>DONE</option><option>NA</option><option>UNCHECKED</option></select></label>
    <input id="f-text" type="search" placeholder="Search text, area or note">
    <span class="shown" id="shown"></span>
  </div>
  <div class="tablewrap"><table>
    <thead><tr><th>Status</th><th>Reference</th><th>Area</th><th>Text / option</th><th>KaizoCore</th><th>Note</th></tr></thead>
    <tbody id="rows"></tbody>
  </table></div>
  <button class="more" id="more" hidden>Show more</button>
</div>
<script>
const ROWS = __DATA__;
const GAPS = __GAPS__;
const NAMES = { gba: "GBA tracker", nds: "DS tracker", gen1: "Game Boy trackers" };
const ORDER = ["DONE", "PARTIAL", "MISSING", "NA", "UNCHECKED"];
const esc = s => String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
function summary() {
  const el = document.getElementById("summary");
  el.innerHTML = Object.keys(NAMES).map(t => {
    const rs = ROWS.filter(r => r[1] === t); const n = rs.length || 1;
    const c = {}; ORDER.forEach(s => c[s] = rs.filter(r => r[6] === s).length);
    const bar = ORDER.map(s => c[s] ? `<span class="s-${s}" style="width:${(100 * c[s] / n).toFixed(2)}%" title="${s} ${c[s]}"></span>` : "").join("");
    const counts = ORDER.filter(s => c[s]).map(s => `<span><b>${c[s]}</b> ${s.toLowerCase()}</span>`).join("");
    return `<div class="card"><h2>${NAMES[t]} &middot; ${rs.length} items</h2><div class="bar">${bar}</div><div class="counts">${counts}</div></div>`;
  }).join("");
}
function fixes() {
  const groups = [["gba", "GBA tracker"], ["nds", "DS tracker"], ["gen1", "Game Boy trackers"], ["all", "Decisions for Blake"]];
  const rank = { BUG: 0, OPEN: 1, DECIDE: 2, FIXED: 3 };
  document.getElementById("fixes").innerHTML = groups.map(([k, name]) => {
    const gs = GAPS.filter(g => g[0] === k).sort((a, b) => rank[a[1]] - rank[b[1]]);
    if (!gs.length) return "";
    const n = s => gs.filter(g => g[1] === s).length;
    const tally = k === "all" ? `${gs.length} open questions`
      : [["BUG", "bugs"], ["OPEN", "missing"], ["FIXED", "fixed"]].filter(([s]) => n(s)).map(([s, w]) => `${n(s)} ${w}`).join(" &middot; ");
    const items = gs.map(g => `<li><span class="pill s-${g[1]}">${g[1]}</span><div><div class="t">${esc(g[2])}</div>${g[3] ? `<div class="d">${esc(g[3])}</div>` : ""}</div></li>`).join("");
    return `<div class="card"><h2>${name}</h2><div class="tally">${tally}</div><ul>${items}</ul></div>`;
  }).join("");
}
let limit = 300;
function render() {
  const t = document.getElementById("f-tracker").value, s = document.getElementById("f-status").value;
  const q = document.getElementById("f-text").value.trim().toLowerCase();
  const hits = ROWS.filter(r => (!t || r[1] === t) && (!s || r[6] === s) &&
    (!q || (r[2] + " " + r[3] + " " + r[4] + " " + r[8] + " " + r[7]).toLowerCase().includes(q)));
  document.getElementById("rows").innerHTML = hits.slice(0, limit).map(r =>
    `<tr><td><span class="pill s-${r[6]}">${r[6]}</span></td><td class="id">${esc(r[0])}<div class="kind">${esc(r[5])}</div></td>` +
    `<td class="area">${esc(r[2])}${r[3] ? "<br>" + esc(r[3]) : ""}</td><td class="text">${esc(r[4])}</td>` +
    `<td class="ours">${esc(r[7])}</td><td>${esc(r[8])}</td></tr>`).join("");
  document.getElementById("shown").textContent = `${Math.min(limit, hits.length)} of ${hits.length} shown`;
  document.getElementById("more").hidden = hits.length <= limit;
}
function save() { try { localStorage.setItem("parity-filters", JSON.stringify(["f-tracker", "f-status", "f-text"].map(i => document.getElementById(i).value))); } catch (e) {} }
try { const v = JSON.parse(localStorage.getItem("parity-filters") || "null"); if (v) ["f-tracker", "f-status", "f-text"].forEach((i, k) => document.getElementById(i).value = v[k] || ""); } catch (e) {}
["f-tracker", "f-status"].forEach(i => document.getElementById(i).addEventListener("change", () => { limit = 300; save(); render(); }));
document.getElementById("f-text").addEventListener("input", () => { limit = 300; save(); render(); });
document.getElementById("more").addEventListener("click", () => { limit += 500; render(); });
summary(); fixes(); render();
</script>
"""
page = page.replace("__GAPS__", gap_data.replace("</", "<" + chr(92) + "/"))
out.write_text(page.replace("__DATA__", data.replace("</", "<\\/")), encoding="utf-8")
print("rows", len(rows), "verified", sum(1 for r in rows if r[6] != "UNCHECKED"))
