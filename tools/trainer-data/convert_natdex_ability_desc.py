"""Ability descriptions for the Nat. Dex and MaxDex builds' abilities past Gen 3, 2026-11-06.

    python -B tools/trainer-data/convert_natdex_ability_desc.py [NatDexExtension.lua]

Writes tracker-gba/src/main/resources/natdex/abilitydesc.tsv in gen3/abilitydesc.tsv's shape: an id, the name, the
description, then the source. Gen 3's 77 are not in it: they keep gen3/abilitydesc.tsv's text (besteon's AbilityData)
on every build. GbaTracker finds a row by the ability's NAME (case, spaces and punctuation ignored), since the builds
number their abilities their own way (Tangled Feet is 78 there, 77 in Gen 5). Two sources:

  nds       Every ability tracker-nds/src/main/resources/nds/ability-desc.tsv has past Gen 3 (the NDS-Ironmon-Tracker's
            AbilityData, GPL-3.0, its Gen 5 wording, the text the DS tracker's ability info shows), id = the DS id. The
            tracker uses it first.
  showdown  Pokemon Showdown's ability text, MIT (sources/showdown-abilities.ts, data/text/abilities.ts at the commit
            showdown-moves.ts is pinned to; sources/showdown-abilities.PINNED.txt), its shortDesc, never its desc, for
            every Nat. Dex 1.2.1 ability past Gen 5 (the Nat. Dex Extension's natDexAbilities, id = the Nat. Dex id).
            Those are the abilities whose own summary-screen text in the Nat. Dex ROM is "Not implemented yet.", and the
            tracker shows this text only where the game's own would be that placeholder (Blake, 2026-11-06: "fill the
            154 Nat. Dex abilities"). MaxDex keeps its own game's words.

The words are the source's own, with the move converter's two changes: a British spelling is respelled through
build_rules.american() and an em dash becomes a comma. The run stops on an ability with no description, a Showdown text
longer than gen3/abilitydesc.tsv's longest, or the placeholder.
"""
import hashlib
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import convert_natdex_move_desc as moves            # TsObject, american, no_em_dash, BRITISH, key, write_rows (g5)

ROOT = HERE.parent.parent
NDS = ROOT / "tracker-nds/src/main/resources/nds/ability-desc.tsv"
GEN3 = ROOT / "tracker-gba/src/main/resources/gen3/abilitydesc.tsv"
OUT = ROOT / "tracker-gba/src/main/resources/natdex/abilitydesc.tsv"
SHOWDOWN = HERE / "sources/showdown-abilities.ts"
SHOWDOWN_SHA256 = "f6f2a0d4fc8be06cc6051d39e11f5f0a3d3d9d11020d5855b09c9b2f34a00568"   # its LF bytes, PINNED.txt
EXTENSION = Path.home() / "ironmon-ref/NatDexExtension/NatDexExtension.lua"
PLACEHOLDER = "Not implemented yet."
key = moves.key

# Nat. Dex's names for the abilities Showdown names by the Pokemon or mask they belong to.
SHOWDOWN_NAME = {
    "As One-I": "As One (Glastrier)", "As One-S": "As One (Spectrier)",
    "Embody Aspect-T": "Embody Aspect (Teal)", "Embody Aspect-H": "Embody Aspect (Hearthflame)",
    "Embody Aspect-W": "Embody Aspect (Wellspring)", "Embody Aspect-C": "Embody Aspect (Cornerstone)",
}


def rows(path: Path):
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or not line.strip():
            continue
        yield line.split("\t")


def showdown_abilities():
    """Showdown's AbilitiesText: {key(name): (name, shortDesc)}, the current text (not a genN one), never the desc."""
    raw = SHOWDOWN.read_bytes().replace(b"\r\n", b"\n")
    if hashlib.sha256(raw).hexdigest() != SHOWDOWN_SHA256:
        sys.exit("%s is not the pinned file: see sources/showdown-abilities.PINNED.txt" % SHOWDOWN.name)
    src = raw.decode("utf-8")
    reader = moves.TsObject(src, src.index("= {", src.index("export const AbilitiesText")) + 2)
    entries = reader.parse()
    reader.skip()
    if not src.startswith(";", reader.i):
        reader.fail("the object should end the file")
    out = {}
    for sid, e in entries.items():
        if not isinstance(e, dict) or not isinstance(e.get("name"), str):
            sys.exit("Showdown entry %s has no name" % sid)
        short = e["shortDesc"].strip() if isinstance(e.get("shortDesc"), str) else ""
        out.setdefault(key(e["name"]), (e["name"], short))
    return out


def natdex_abilities(path: Path):
    """The Nat. Dex Extension's natDexAbilities: [(id, name)], ids 78 on."""
    text = path.read_text(encoding="utf-8")
    block = text[text.index("self.Data.natDexAbilities = {"):]
    block = block[:block.index("\n\t}\n")]
    return [(int(i), n) for i, n in re.findall(r'id = (\d+),\s*name = "([^"]*)"', block)]


def clean(text: str, what: str) -> str:
    us = moves.american(moves.no_em_dash(text))
    if moves.BRITISH.search(us):
        sys.exit("%s keeps a British spelling (%s): add it to build_rules.AMERICAN" % (what, moves.BRITISH.search(us).group(0)))
    if "\t" in us or "\n" in us or "—" in us or PLACEHOLDER.lower() in us.lower():
        sys.exit("%s: a tab, a line break, an em dash or the placeholder in the text" % what)
    return us


def main():
    ext = Path(sys.argv[1]) if len(sys.argv) > 1 else EXTENSION
    gen3 = {key(r[1]) for r in rows(GEN3)}
    cap = max(len(r[2]) for r in rows(GEN3))
    out = ["# id\tname\tdescription\tsource (nds: NDS-Ironmon-Tracker AbilityData, Gen 5, via tracker-nds "
           "nds/ability-desc.tsv, id = DS id; showdown: Pokemon Showdown data/text/abilities.ts shortDesc, MIT, "
           "id = Nat. Dex id). Made by tools/trainer-data/convert_natdex_ability_desc.py"]
    ds = set()
    for r in rows(NDS):
        aid, name, gen5 = int(r[0]), r[1], r[3].strip()
        if aid == 0 or key(name) in gen3:
            continue
        if not gen5:
            sys.exit("DS ability %d %s: no description" % (aid, name))
        out.append("%d\t%s\t%s\tnds" % (aid, name, clean(gen5, "DS ability %d %s" % (aid, name))))
        ds.add(key(name))
    showdown = showdown_abilities()
    left, holes, n = [], [], 0
    for aid, name in natdex_abilities(ext):
        if key(name) in gen3 or key(name) in ds:
            continue
        if name.isdigit():
            holes.append(aid)                        # an empty slot, named by its number (315, 318): no ability
            continue
        hit = showdown.get(key(SHOWDOWN_NAME.get(name, name)))
        if not hit or not hit[1]:
            left.append("%d %s" % (aid, name))
            continue
        if len(hit[1]) > cap:
            sys.exit("ability %d %s: %d characters, longer than gen3/abilitydesc.tsv's longest (%d)" % (aid, name, len(hit[1]), cap))
        out.append("%d\t%s\t%s\tshowdown" % (aid, name, clean(hit[1], "ability %d %s" % (aid, name))))
        n += 1
    if left:
        sys.exit("no source describes %d abilities: %s" % (len(left), ", ".join(left)))
    moves.g5.write_rows(OUT, out)
    print("%d abilities from the DS tracker, %d from Showdown; empty slots skipped: %s -> %s" % (
        len(ds), n, holes, OUT.relative_to(ROOT)))


if __name__ == "__main__":
    main()
