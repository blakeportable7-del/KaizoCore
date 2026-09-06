"""Gen 5 name and data tables for the DS tracker, out of the reference.

Source: ~/ironmon-ref/NDS-Ironmon-Tracker/ironmon_tracker/constants
  PokemonData.lua  POKEMON_MASTER_LIST: index 0 is an empty entry, so list
                   index == species id. movelvls has five arrays, one per
                   version group: DP, Pt, HGSS, BW, B2W2.
  MoveData.lua     MOVES_MASTER_LIST: same convention (index == move id).
  AbilityData.lua  ABILITIES: same.

Output, in the same shapes tracker-nds/src/main/resources/gen4 uses:
  gen5/species.tsv          id  NAME
  gen5/moves.tsv            id  Name  power  accuracy  TYPE  pp  CATEGORY
  gen5/abilities.tsv        id  Name
  gen5/items.tsv            id  Name   (ItemData.GEN_5_ITEMS, sparse, ids to 625)
  gen5/movelevels-bw.tsv    id  comma-separated levels   (version group 4)
  gen5/movelevels-b2w2.tsv  id  comma-separated levels   (version group 5)

Hand entry is banned (docs/EMULATOR_PLAN.md risk 3): this is scripted, and it
prints counts and the first and last rows so a bad parse is visible.
"""
import re
import sys
from pathlib import Path

REF = Path.home() / "ironmon-ref/NDS-Ironmon-Tracker/ironmon_tracker/constants"
OUT = Path(__file__).resolve().parent.parent / "tracker-nds/src/main/resources/gen5"

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


def field(body, key):
    m = re.search(r"\b%s\s*=\s*(\"[^\"]*\"|[A-Za-z_.0-9]+)" % key, body)
    return m.group(1).strip('"') if m else ""


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
    (OUT / "species.tsv").write_text("\n".join(sp) + "\n", encoding="utf-8")
    (OUT / "movelevels-bw.tsv").write_text("\n".join(bw) + "\n", encoding="utf-8")
    (OUT / "movelevels-b2w2.tsv").write_text("\n".join(b2w2) + "\n", encoding="utf-8")

    # ---- moves
    md = (REF / "MoveData.lua").read_text(encoding="utf-8", errors="replace")
    moves = entries(md, "MoveData.MOVES_MASTER_LIST")
    rows = []
    for mid, (name, body) in enumerate(moves):
        if mid == 0:
            continue
        typ = field(body, "type").split(".")[-1]          # ...POKEMON_TYPES.FIRE -> FIRE
        cat = field(body, "category").split(".")[-1]      # ...MOVE_CATEGORIES.PHYSICAL -> PHYSICAL
        # Status moves carry symbols (Graphics.TEXT.NO_POWER, ALWAYS_HITS);
        # the gen4 table's convention for those is 0, which the tracker reads
        # as "-" for power and "never misses" for accuracy.
        def num(v):
            return v if v.isdigit() else "0"
        power = num(field(body, "power"))
        acc = num(field(body, "accuracy"))
        pp = num(field(body, "pp"))
        rows.append("%d\t%s\t%s\t%s\t%s\t%s\t%s" % (mid, name, power, acc, typ, pp, cat))
    (OUT / "moves.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")

    # ---- items: ItemData.GEN_5_ITEMS is keyed [id] = { name = "..." } and is
    # sparse (607 entries, ids to 625), so the key is read, not the position.
    # Names there are plain strings; the accent joins are only in descriptions.
    it = (REF / "ItemData.lua").read_text(encoding="utf-8", errors="replace")
    block = it[it.index("ItemData.GEN_5_ITEMS = {"):]
    items = re.findall(r"\n    \[(\d+)\] = \{\s*name = \"([^\"]*)\"", block)
    rows = ["%d\t%s" % (int(i), n) for i, n in items if int(i) > 0]
    (OUT / "items.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    print("items", len(rows), "max id", max(int(i) for i, _ in items))

    # ---- abilities
    ad = (REF / "AbilityData.lua").read_text(encoding="utf-8", errors="replace")
    abilities = entries(ad, "AbilityData.ABILITIES")
    ab = ["%d\t%s" % (i, n) for i, (n, _) in enumerate(abilities) if i > 0]
    (OUT / "abilities.tsv").write_text("\n".join(ab) + "\n", encoding="utf-8")

    for f in sorted(OUT.glob("*.tsv")):
        lines = f.read_text(encoding="utf-8").splitlines()
        print("%-22s %4d rows   first: %-28s last: %s" % (f.name, len(lines), lines[0][:28], lines[-1][:40]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
