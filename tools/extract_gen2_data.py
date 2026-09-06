"""Gen 2 species and move NAMES for the Game Boy tracker, out of the reference.

Source: ~/ironmon-ref/Ironmon-gen-2-tracker/ironmon_tracker/data
  PokemonData.lua  entries in id order (index 0 empty); 1..251 for Gen 2
  MoveData.lua     entries with an explicit id; 1..251 for Gen 2

Only names are taken. Types, power, accuracy and PP come from the RANDOMIZED
ROM at play time (Crystal keeps base stats at 0x51424 and moves at 0x41AFB),
because a randomizer changes them and a table would not know.

Output: tracker-gba/src/main/resources/gen2/species.tsv and moves.tsv, same
shape as the gen4 tables (id TAB Name).
"""
import re
import sys
from pathlib import Path

REF = Path.home() / "ironmon-ref/Ironmon-gen-2-tracker/ironmon_tracker/data"
OUT = Path(__file__).resolve().parent.parent / "tracker-gba/src/main/resources/gen2"

ENTRY = re.compile(
    r"\{\s*(?:(?:--[^\n]*|\w+\s*=\s*[^\n]*)\n\s*)*?name\s*=\s*\"([^\"]*)\"(.*?)\n\s*\},?",
    re.S,
)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    pk = (REF / "PokemonData.lua").read_text(encoding="utf-8", errors="replace")
    start = pk.index("name = \"Bulbasaur\"")
    head = pk.rfind("{", 0, start)
    names = [m.group(1) for m in ENTRY.finditer(pk[head:])]
    species = ["%d\t%s" % (i + 1, n.upper()) for i, n in enumerate(names[:251])]
    (OUT / "species.tsv").write_text("\n".join(species) + "\n", encoding="utf-8")

    md = (REF / "MoveData.lua").read_text(encoding="utf-8", errors="replace")
    moves = []
    for m in ENTRY.finditer(md):
        body = m.group(2)
        mid = re.search(r"\bid\s*=\s*\"(\d+)\"", body)
        if mid is None:
            # id may precede name in the entry text; look just before the name
            pre = md[max(0, m.start()):m.start(1)]
            mid = re.search(r"\bid\s*=\s*\"(\d+)\"", pre)
        if mid and int(mid.group(1)) <= 251:
            moves.append((int(mid.group(1)), m.group(1)))
    moves.sort()
    (OUT / "moves.tsv").write_text("\n".join("%d\t%s" % x for x in moves) + "\n", encoding="utf-8")

    for f in sorted(OUT.glob("*.tsv")):
        lines = f.read_text(encoding="utf-8").splitlines()
        print("%-12s %3d rows   first: %-16s last: %s" % (f.name, len(lines), lines[0], lines[-1]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
