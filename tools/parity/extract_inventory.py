"""Tracker parity inventory, extracted from the PC trackers' own source (2026-09-29).

    python tools/parity/extract_inventory.py <ironmon-ref folder> <out folder>

Blake: "i'm tired of recalling from memory what the trackers should do. please verify our
trackers share all the qualities of the pc versions". So the list of what a tracker does is
not written by anyone: it is pulled out of each reference mechanically, and every row is then
checked against KaizoCore (docs/parity/verify-*.tsv).

- gba   Ironmon-Tracker (besteon): every ScreenResources string in Languages/English.lua (the
        complete UI text, section by section), every Options.lua option with its default, and
        every screen file.
- nds   NDS-Ironmon-Tracker: every MiscConstants.DEFAULT_SETTINGS setting with its default,
        every TextField / label string in ui/*.lua, and every screen file.
- gen1  Ironmon-gen-tracker, gen2 Ironmon-gen-2-tracker: every Options.lua option, every
        user-facing string literal in screens/*.lua, and every screen file.

Rows: id, tracker, area, key, text, kind (ui | option | setting | screen).
"""
import sys, re, pathlib
from lupa import LuaRuntime

ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
TAB = chr(9); NL = chr(10)


def clean(s):
    return str(s).replace(TAB, " ").replace(NL, " ").replace(chr(13), " ").strip()


def write(name, rows):
    with open(out / ("inventory-%s.tsv" % name), "w", encoding="utf-8", newline=NL) as f:
        f.write("# id" + TAB + "tracker" + TAB + "area" + TAB + "key" + TAB + "text" + TAB + "kind  (tools/parity/extract_inventory.py)" + NL)
        for i, (area, key, text, kind) in enumerate(rows, 1):
            f.write("%s-%04d" % (name, i) + TAB + name + TAB + clean(area) + TAB + clean(key) + TAB + clean(text) + TAB + kind + NL)
    print(name, len(rows), "rows")


def lua_string_literals(src):
    # "..." and '...' literals, ignoring escaped quotes; comments stripped first.
    src = re.sub(r"--\[\[.*?\]\]", "", src, flags=re.S)
    src = re.sub(r"--[^\n]*", "", src)
    return re.findall(r'"((?:[^"\\\n]|\\.)*)"|\'((?:[^\'\\\n]|\\.)*)\'', src)


# Strings that are code, not text a player reads.
NOT_TEXT = re.compile(r"^(?:[A-Z0-9_]+|[a-z][a-zA-Z0-9_]*|.*\.(?:lua|png|gif|json|txt|ini|rnqs|gba|nds|jar|log|tdat|bmp|wav)|.*[/\\].*|%.*|#?[0-9A-Fa-f]{6,8}|0x[0-9A-Fa-f]+|.*color|.*Color|[\W\d_]*)$")


def ui_texts(src):
    seen = []
    for a, b in lua_string_literals(src):
        s = (a or b).strip()
        if len(s) < 2 or NOT_TEXT.match(s):
            continue
        if not re.search(r"[A-Za-z]", s):
            continue
        if s not in seen:
            seen.append(s)
    return seen


# ------------------------------------------------------------------ GBA (besteon)
gba = ref / "Ironmon-Tracker" / "ironmon_tracker"
rows = []
L = LuaRuntime(unpack_returned_tuples=True)
L.execute("CAPTURE = {}; function ScreenResources(t) CAPTURE.screen = t end; function GameResources(t) CAPTURE.game = t end")
L.execute((gba / "Languages" / "English.lua").read_text(encoding="utf-8"))
screen = L.globals().CAPTURE.screen


def walk(tbl, path):
    items = sorted(dict(tbl).items(), key=lambda kv: str(kv[0]))
    for k, v in items:
        if hasattr(v, "keys"):
            yield from walk(v, path + [str(k)])
        else:
            yield path, str(k), str(v)


for section in sorted(dict(screen).keys(), key=str):
    v = screen[section]
    if section == "GameOverScreenQuotes":
        rows.append((section, "(list)", "%d announcer quotes" % len(v), "ui"))
        continue
    if hasattr(v, "keys"):
        for path, k, text in walk(v, [str(section)]):
            rows.append(("/".join(path), k, text, "ui"))
    else:
        rows.append((str(section), "", str(v), "ui"))
opt = (gba / "Options.lua").read_text(encoding="utf-8")
body = opt[opt.index("Options = {"): opt.index("\n}", opt.index("Options = {"))]
for m in re.finditer(r'\[\s*"([^"]+)"\s*\]\s*=\s*([^,\n]+)', body):
    rows.append(("Options.lua", m.group(1), "default " + m.group(2).strip(), "option"))
for p in sorted((gba / "screens").glob("*.lua")):
    rows.append(("screens", p.stem, "screen file " + p.name, "screen"))
write("gba", rows)

# ------------------------------------------------------------------ NDS (Brian0255)
nds = ref / "NDS-Ironmon-Tracker" / "ironmon_tracker"
rows = []
N = LuaRuntime(unpack_returned_tuples=True)
N.execute("""
STUB = {}
STUB.__index = function(t, k) local v = setmetatable({}, STUB); rawset(t, k, v); return v end
STUB.__call = function() return setmetatable({}, STUB) end
STUB.__concat = function(a, b) return tostring(type(a) == 'table' and '' or a) .. tostring(type(b) == 'table' and '' or b) end
STUB.__tostring = function() return '' end
STUB.__eq = function() return false end; STUB.__lt = function() return false end; STUB.__le = function() return false end
setmetatable(_G, {__index = STUB.__index})
""")
N.execute((nds / "constants" / "Chars.lua").read_text(encoding="utf-8"))
N.execute((nds / "constants" / "PlaythroughConstants.lua").read_text(encoding="utf-8"))
N.execute((nds / "constants" / "MiscConstants.lua").read_text(encoding="utf-8"))
ds = N.globals().MiscConstants.DEFAULT_SETTINGS
for group in sorted(dict(ds).keys(), key=str):
    g = ds[group]
    for k in sorted(dict(g).keys(), key=str):
        v = g[k]
        rows.append(("DEFAULT_SETTINGS/" + str(group), str(k), "default " + ("(table)" if hasattr(v, "keys") else str(v)), "setting"))
for p in sorted((nds / "ui").rglob("*.lua")):
    rel = p.relative_to(nds).as_posix()
    rows.append(("screens", p.stem, "screen file " + rel, "screen"))
    for s in ui_texts(p.read_text(encoding="utf-8", errors="replace")):
        rows.append((rel, "", s, "ui"))
write("nds", rows)

# ------------------------------------------------------------------ Gen 1 / Gen 2
for name, folder in (("gen1", "Ironmon-gen-tracker"), ("gen2", "Ironmon-gen-2-tracker")):
    root = ref / folder / "ironmon_tracker"
    rows = []
    o = (root / "Options.lua").read_text(encoding="utf-8", errors="replace")
    for m in re.finditer(r'\[\s*"([^"]+)"\s*\]\s*=\s*([^,\n]+)', o):
        rows.append(("Options.lua", m.group(1), "default " + m.group(2).strip(), "option"))
    for p in sorted((root / "screens").glob("*.lua")):
        rows.append(("screens", p.stem, "screen file " + p.name, "screen"))
        for s in ui_texts(p.read_text(encoding="utf-8", errors="replace")):
            rows.append(("screens/" + p.name, "", s, "ui"))
    write(name, rows)
