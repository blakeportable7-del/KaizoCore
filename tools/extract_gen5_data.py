"""Gen 5 name and data tables for the DS tracker, out of the reference.

Source: ~/ironmon-ref/NDS-Ironmon-Tracker/ironmon_tracker/constants
  PokemonData.lua  POKEMON_MASTER_LIST: index 0 is an empty entry, so list
                   index == species id. movelvls has five arrays, one per
                   version group: DP, Pt, HGSS, BW, B2W2.
  MoveData.lua     MOVES_MASTER_LIST: same convention (index == move id).
  AbilityData.lua  ABILITIES: same.

Output, in the same shapes tracker-nds/src/main/resources/gen4 uses:
  gen5/species.tsv          id  NAME
  gen5/moves.tsv            id  Name  power  accuracy  TYPE  pp  CATEGORY  [power text]
  gen5/abilities.tsv        id  Name
  gen5/items.tsv            id  Name   (ItemData.GEN_5_ITEMS, sparse, ids to 625)
  gen5/movelevels-bw.tsv    id  comma-separated levels   (version group 4)
  gen5/movelevels-b2w2.tsv  id  comma-separated levels   (version group 5)

Moves are not parsed with a regex: they come from RUNNING the reference's own
GameConfigurator.initMoveData with GEN = 5 (lupa), the code the PC tracker runs
at startup. A move attribute there is one value or a per-generation table
{gen 1, ..., gen 5}, and the tracker keeps moveAttribute[gameInfo.GEN]
(GameConfigurator.lua:87). The regex this replaced read neither shape: power =
{"35", "35", "35", "35", "50"} became Tackle power 0, and a type table spread
over several lines ended the entry early, so Gust, Bite and Curse lost every
field after it (70 moves wrong, parity audit 2026-09-28). Power and accuracy
the reference shows as "---" are written 0, which the panel draws as "---";
a power it shows as text (WT, <HP, VAR...) is 0 with the text in an eighth column.
Every row is then read back from the written file and compared with the Lua
values, and any difference stops the script.

Hand entry is banned (docs/EMULATOR_PLAN.md risk 3): this is scripted, and it
prints counts and the first and last rows so a bad parse is visible.
"""
import re
import sys
from pathlib import Path

TRACKER = Path.home() / "ironmon-ref/NDS-Ironmon-Tracker/ironmon_tracker"
REF = TRACKER / "constants"
OUT = Path(__file__).resolve().parent.parent / "tracker-nds/src/main/resources/gen5"
GEN = 5

# An entry is a table whose fields may come in any order - moves and abilities
# put id (and sometimes a comment line) before name. It ends at a closing brace
# on its own line. The body captured runs up to that brace.
ENTRY = re.compile(
    # The last entry of a list has no trailing comma: hence the ",?".
    r"\{\s*(?:(?:--[^\n]*|\w+\s*=\s*[^\n]*)\n\s*)*?name\s*=\s*\"([^\"]*)\"(.*?)\n\s*\},?",
    re.S,
)


def entries(text, list_name):
    """Every {... name = "..."} table inside the named list, in order."""
    start = text.index(list_name + " = {")
    body = text[start:]
    return [(m.group(1), m.group(2)) for m in ENTRY.finditer(body)]


def write_rows(path, rows):
    """Rows as lines, keeping the line ending the file already has (CRLF when new)."""
    nl = "\r\n"
    if path.exists():
        old = path.read_bytes()
        nl = "\r\n" if b"\r\n" in old else ("\n" if b"\n" in old else nl)
    path.write_bytes((nl.join(rows) + nl).encode("utf-8"))


def reference_moves(gen):
    """MoveData.MOVES as GameConfigurator.initMoveData builds it for [gen]: one row per
    move id from 1, each (id, name, power, accuracy, type, pp, category) exactly as
    the tracker holds them, strings where the tracker has strings."""
    from lupa import LuaRuntime, lua_type
    lua = LuaRuntime(unpack_returned_tuples=True)
    # Globals the files touch but that are not loaded here (MiscUtils.readOnly...)
    # resolve to stand-ins that hand back their first argument, as in
    # tools/trainer-data/convert_nds_log_tables.py.
    lua.execute(
        "STANDIN = {}\n"
        "local function proxy() return setmetatable({}, STANDIN) end\n"
        "STANDIN.__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end\n"
        "STANDIN.__call = function(self, a, ...) if a ~= nil then return a end return proxy() end\n"
        "setmetatable(_G, {__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end})\n")
    for f in ("constants/Chars.lua", "constants/Graphics.lua", "constants/PokemonData.lua",
              "constants/MoveData.lua", "GameConfigurator.lua"):
        lua.execute((TRACKER / f).read_text(encoding="utf-8"))
    G = lua.globals()
    G.GameConfigurator.initMoveData(lua.table_from({"GEN": gen}))

    def scalar(v, what):
        if v is None or lua_type(v) is not None:
            sys.exit("move %s: %r is not a plain value after initMoveData" % (what, v))
        if isinstance(v, float) and v.is_integer():
            v = int(v)
        return str(v)

    rows = []
    for index, m in sorted(G.MoveData.MOVES.items(), key=lambda kv: kv[0]):
        if index == 1:
            continue                                     # the empty entry for move id 0
        row = tuple(scalar(m[k], "%d %s" % (index - 1, k))
                    for k in ("id", "name", "power", "accuracy", "type", "pp", "category"))
        if row[0] != str(index - 1):
            sys.exit("MoveData.MOVES[%d] carries id %s" % (index, row[0]))
        rows.append(row)
    return rows


def move_row(ref):
    """The tsv row for one reference move."""
    mid, name, power, acc, typ, pp, cat = ref
    no = "---"                                           # Graphics.TEXT NO_POWER / ALWAYS_HITS
    if not pp.isdigit() or int(pp) <= 0:
        sys.exit("move %s %s: pp %r" % (mid, name, pp))
    if not (acc.isdigit() or acc == no):
        sys.exit("move %s %s: accuracy %r" % (mid, name, acc))
    cells = [mid, name, power if power.isdigit() else "0", acc if acc.isdigit() else "0", typ, pp, cat]
    if not (power.isdigit() or power == no):
        cells.append(power)                              # WT, <HP, VAR...: the text is what it shows
    return "\t".join(cells)


def main():
    OUT.mkdir(parents=True, exist_ok=True)

    # ---- species + move levels
    pk = (REF / "PokemonData.lua").read_text(encoding="utf-8", errors="replace")
    mons = entries(pk, "PokemonData.POKEMON_MASTER_LIST")
    sp, bw, b2w2 = [], [], []
    for sid, (name, body) in enumerate(mons):
        if sid == 0 or sid > 649:
            continue                                  # 0 is the empty entry; >649 are forms
        sp.append("%d\t%s" % (sid, name.upper()))
        # The body ends at the movelvls table's own closing line, so take
        # everything after "movelvls = {" rather than looking for the close.
        m = re.search(r"movelvls\s*=\s*\{(.*)$", body, re.S)
        groups = re.findall(r"\{([0-9,\s]*)\}", m.group(1)) if m else []

        def lv(i):
            if len(groups) <= i:
                return ""
            return ",".join(x.strip() for x in groups[i].split(",") if x.strip())
        bw.append("%d\t%s" % (sid, lv(3)))
        b2w2.append("%d\t%s" % (sid, lv(4)))
    write_rows(OUT / "species.tsv", sp)
    write_rows(OUT / "movelevels-bw.tsv", bw)
    write_rows(OUT / "movelevels-b2w2.tsv", b2w2)

    # ---- moves, from the reference's own initMoveData, then read back and compared.
    ref = reference_moves(GEN)
    write_rows(OUT / "moves.tsv", [move_row(r) for r in ref])
    back = (OUT / "moves.tsv").read_text(encoding="utf-8").splitlines()
    if len(back) != len(ref):
        sys.exit("moves.tsv has %d rows, the reference %d" % (len(back), len(ref)))
    for line, r in zip(back, ref):
        p = line.split("\t")
        shown_power = p[7] if len(p) > 7 else (p[2] if p[2] != "0" else "---")
        shown = (p[0], p[1], shown_power, p[3] if p[3] != "0" else "---", p[4], p[5], p[6])
        if shown != r:
            sys.exit("moves.tsv row %s reads %s, the reference %s" % (p[0], shown, r))
    print("moves", len(ref), "match GameConfigurator.initMoveData(GEN = %d) row for row" % GEN)

    # ---- items: ItemData.GEN_5_ITEMS is keyed [id] = { name = "..." } and is
    # sparse (607 entries, ids to 625), so the key is read, not the position.
    # Names there are plain strings; the accent joins are only in descriptions.
    it = (REF / "ItemData.lua").read_text(encoding="utf-8", errors="replace")
    block = it[it.index("ItemData.GEN_5_ITEMS = {"):]
    items = re.findall(r"\n    \[(\d+)\] = \{\s*name = \"([^\"]*)\"", block)
    rows = ["%d\t%s" % (int(i), n) for i, n in items if int(i) > 0]
    write_rows(OUT / "items.tsv", rows)
    print("items", len(rows), "max id", max(int(i) for i, _ in items))

    # ---- abilities
    ad = (REF / "AbilityData.lua").read_text(encoding="utf-8", errors="replace")
    abilities = entries(ad, "AbilityData.ABILITIES")
    ab = ["%d\t%s" % (i, n) for i, (n, _) in enumerate(abilities) if i > 0]
    write_rows(OUT / "abilities.tsv", ab)

    for f in sorted(OUT.glob("*.tsv")):
        lines = f.read_text(encoding="utf-8").splitlines()
        print("%-22s %4d rows   first: %-28s last: %s" % (f.name, len(lines), lines[0][:28], lines[-1][:40]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
