"""Writes tracker-gba/src/main/resources/nuzlocke/species-gen5.tsv (Generation 5: Black, White, Black 2, White 2).

    python tools/nuzlocke/gen5_species.py           write the file (LF endings; run to_crlf.py after)
    python tools/nuzlocke/gen5_species.py --check   re-read the ROMs and compare with the shipped file

Source: the personal data archive (a/0/1/6, 76 bytes per species) of a clean Pokemon Black 2 (US) ROM, every species
cross-checked against a clean White 2 (US) ROM. Byte 0x12 is the gender ratio, bytes 6 and 7 the two types.
Nothing but those three numbers per species leaves the ROM.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen5_common as C
import gen5_rom as R

OUT = C.OUT_DIR / "species-gen5.tsv"
FIRST, LAST = 1, 649

# The species the task pinned down by hand, from the games' own numbers: (name, gender ratio, type1, type2).
KNOWN = {
    "PIKACHU": (127, 13, 13),
    "LUCARIO": (31, 1, 8),
    "MAGNEMITE": (255, 13, 8),
    "CHANSEY": (254, 0, 0),
    "TAUROS": (0, 0, 0),
    "ZORUA": (31, 17, 17),
    "VOLCARONA": (127, 6, 10),
    "MILTANK": (254, 0, 0),
    "HITMONLEE": (0, 1, 1),
    "BULBASAUR": (31, 12, 3),
    "CHARIZARD": (31, 10, 2),
    "GARDEVOIR": (127, 14, 14),
    "MEWTWO": (255, 14, 14),
    "KYUREM": (255, 16, 15),
    "MELOETTA A": (255, 0, 14),
    "GENESECT": (255, 6, 8),
}


def gen3_type(raw):
    """Gen 5's raw type byte has no unused slot between Steel (8) and Fire (9): map it to the app's ids (Fire 10)."""
    if not 0 <= raw <= 16:
        raise ValueError("unexpected raw type byte %d" % raw)
    return raw if raw <= 8 else raw + 1


def build(problems):
    """Rows (id, name, gender, type1, type2) from the ROMs, and the list of ROMs that were missing."""
    names = C.species_names()
    missing = []
    b2 = R.open_rom("black2")
    w2 = R.open_rom("white2")
    if b2 is None:
        missing.append("Black 2 ROM (%s)" % R.ROMS["black2"][0])
        return None, missing
    if w2 is None:
        missing.append("White 2 ROM (%s)" % R.ROMS["white2"][0])
    pb = R.read_personal(b2)
    pw = R.read_personal(w2) if w2 is not None else None
    rows = []
    for sid in range(FIRST, LAST + 1):
        name = names.get(sid)
        if name is None:
            problems.append("species %d has no name in gen5/species.tsv" % sid)
            continue
        rec = pb[sid]
        if len(rec) != 76:
            problems.append("species %d: personal record is %d bytes, not 76" % (sid, len(rec)))
            continue
        gender = rec[R.PERSONAL_GENDER]
        t1 = gen3_type(rec[R.PERSONAL_TYPE1])
        t2 = gen3_type(rec[R.PERSONAL_TYPE2])
        if pw is not None:
            other = pw[sid]
            if (other[R.PERSONAL_GENDER], other[R.PERSONAL_TYPE1], other[R.PERSONAL_TYPE2]) != (gender, rec[6], rec[7]):
                problems.append("species %d %s: Black 2 and White 2 disagree on gender or types" % (sid, name))
        rows.append((sid, name, gender, t1, t2))
    b2.close()
    if w2 is not None:
        w2.close()
    return rows, missing


HEADER = [
    "# Species facts for the Generation 5 games, Black, White, Black 2 and White 2 (2026-09-29).",
    "#",
    "# What the DS tracker's sidecar cannot show: the gender ratio and the two types of every species 1 to 649",
    "# (National Dex numbers). NuzlockeFamilies also reads the id and name columns as its list of species.",
    "#",
    "# Source: the personal data archive (a/0/1/6, 76 bytes per species) of a clean Pokemon Black 2 (US) ROM: byte 0x12",
    "# is the gender ratio, bytes 6 and 7 are the types. Every species was compared with a clean White 2 (US) ROM and the",
    "# two agree on all 649. No ROM bytes are copied: only these three facts. The layout is the one the Universal",
    "# Pokemon Randomizer ZX reads (Gen5Constants bs*Offset). Checked by hand on Pikachu 127, Lucario 31, Magnemite 255,",
    "# Chansey 254, Tauros 0, Zorua 31 and Volcarona 127; the same values are asserted by --check.",
    "#",
    "# id: National Dex number.  name: as tracker-nds/src/main/resources/gen5/species.tsv spells it (forms carry a",
    "#   letter, for example BURMY P).",
    "# genderRatio: the game's own byte: 0 always male, 254 always female, 255 genderless, otherwise the threshold the",
    "#   game compares the low byte of the personality value against (female when the low byte is below it).",
    "# type1, type2: Gen 3 style type ids, 0 Normal, 1 Fighting, 2 Flying, 3 Poison, 4 Ground, 5 Rock, 6 Bug, 7 Ghost,",
    "#   8 Steel, 10 Fire, 11 Water, 12 Grass, 13 Electric, 14 Psychic, 15 Ice, 16 Dragon, 17 Dark. type2 equals type1",
    "#   for a single type species. Note: Gen 5's raw type byte has no unused slot after Steel (Fire is 9 there), so the",
    "#   raw byte is converted (raw 9 to 16 become 10 to 17).",
    "#",
    "# Regenerate: python tools/nuzlocke/gen5_species.py    Check: python tools/nuzlocke/gen5_species.py --check",
    "#",
    "# id\tname\tgenderRatio\ttype1\ttype2",
]


def render_rows(rows):
    return C.render(HEADER + ["%d\t%s\t%d\t%d\t%d" % r for r in rows])


def check(rows, problems):
    """Independent assertions on the generated rows (the hand-verified species and sanity ranges)."""
    by_name = {r[1]: r for r in rows}
    for name, (gender, t1, t2) in KNOWN.items():
        r = by_name.get(name)
        if r is None:
            problems.append("known species %s missing" % name)
        elif (r[2], r[3], r[4]) != (gender, t1, t2):
            problems.append("known species %s: got %s, expected %s" % (name, (r[2], r[3], r[4]), (gender, t1, t2)))
    ids = [r[0] for r in rows]
    if ids != list(range(FIRST, LAST + 1)):
        problems.append("ids are not 1..649 in order")
    for r in rows:
        if r[2] not in (0, 31, 63, 127, 191, 254, 255):
            problems.append("species %d %s: odd gender ratio %d" % (r[0], r[1], r[2]))
        if r[3] not in (0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17) or r[4] not in (0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17):
            problems.append("species %d %s: bad type ids %d/%d" % (r[0], r[1], r[3], r[4]))


def main():
    flags = C.main_flags()
    problems = []
    rows, missing = build(problems)
    if rows is None:
        return C.finish("species-gen5.tsv", problems, missing)
    text = render_rows(rows)
    check(rows, problems)
    if flags["check"]:
        shipped = C.normalized(OUT)
        if shipped is None:
            problems.append("%s does not exist" % OUT)
        else:
            problems.extend(C.diff_lines(text, shipped))
        code = C.finish("species-gen5.tsv", problems, missing)
        if code == C.EXIT_OK:
            print("  %d species, gender ratios %s" % (len(rows), sorted({r[2] for r in rows})))
        return code
    if problems:
        print("refusing to write, problems:")
        for p in problems:
            print("  - " + p)
        return C.EXIT_MISMATCH
    OUT.write_text(text, encoding="ascii", newline="\n")
    print("wrote %s (%d species)" % (OUT, len(rows)))
    return C.EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
