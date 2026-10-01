"""Writes tracker-gba/src/main/resources/nuzlocke/families-gen5.tsv (Generation 5: Black, White, Black 2, White 2).

    python tools/nuzlocke/gen5_families.py           write the file (LF endings; run to_crlf.py after)
    python tools/nuzlocke/gen5_families.py --check   re-derive the lines and compare with the shipped file

Sources:
  * tracker-nds/src/main/resources/gen5/evos.tsv: the first two columns are evolution edges (base species number,
    evolved species number); the third column (what evolves into what, as percentages) is not used. The file holds
    all 320 edges of the games, babies and Shedinja included.
  * the evolution archive (a/0/1/9) and the baby table (a/0/2/0) of clean Black 2 and White 2 ROMs, which --check
    compares with evos.tsv edge by edge and species by species.
A line of the output is one connected group of species, written as national dex numbers, a tab and a # comment with
the names, exactly the shape families-gen3.tsv has for the Game Boy Advance games.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen5_common as C
import gen5_rom as R

OUT = C.OUT_DIR / "families-gen5.tsv"
FIRST, LAST = 1, 649

# The tracker's names carry a form letter for a few species; a comment reads better without it.
FORM_NAMES = {
    "BURMY P": "Burmy", "WORMADAM P": "Wormadam", "CHERRIM O": "Cherrim", "SHELLOS W": "Shellos",
    "GASTRODON W": "Gastrodon", "GIRATINA A": "Giratina", "SHAYMIN L": "Shaymin", "UNFEZANT M": "Unfezant",
    "BASCULIN R": "Basculin", "FRILLISH M": "Frillish", "JELLICENT M": "Jellicent", "MELOETTA A": "Meloetta",
}

# Lines worked out by hand from Bulbapedia's evolution family pages, as an independent oracle for --check.
EXPECTED = {
    "Eevee": {133, 134, 135, 136, 196, 197, 470, 471},
    "Wurmple": {265, 266, 267, 268, 269},
    "Poliwag": {60, 61, 62, 186},
    "Ralts": {280, 281, 282, 475},
    "Snorunt": {361, 362, 478},
    "Tyrogue": {106, 107, 236, 237},
    "Burmy": {412, 413, 414},
    "Deerling": {585, 586},
    "Nincada": {290, 291, 292},
    "Pichu": {172, 25, 26},
    "Cleffa": {173, 35, 36},
    "Igglybuff": {174, 39, 40},
    "Smoochum": {238, 124},
    "Elekid": {239, 125, 466},
    "Magby": {240, 126, 467},
    "Azurill": {298, 183, 184},
    "Wynaut": {360, 202},
    "Bonsly": {438, 185},
    "Mime Jr.": {439, 122},
    "Happiny": {440, 113, 242},
    "Munchlax": {446, 143},
    "Riolu": {447, 448},
    "Mantyke": {458, 226},
    "Budew": {406, 315, 407},
    "Chingling": {433, 358},
    "Oddish": {43, 44, 45, 182},
    "Slowpoke": {79, 80, 199},
    "Gligar": {207, 472},
    "Phione": {489},
    "Manaphy": {490},
    "Porygon": {137, 233, 474},
    "Larvesta": {636, 637},
    "Gible": {443, 444, 445},
    "Petilil": {548, 549},
    "Dwebble": {557, 558},
}
BABIES = {
    "Pichu": 172, "Cleffa": 173, "Igglybuff": 174, "Tyrogue": 236, "Smoochum": 238, "Elekid": 239, "Magby": 240,
    "Azurill": 298, "Wynaut": 360, "Bonsly": 438, "Mime Jr.": 439, "Happiny": 440, "Munchlax": 446, "Riolu": 447,
    "Mantyke": 458, "Budew": 406, "Chingling": 433,
}


def read_edges():
    edges = set()
    for p in C.read_tsv_rows(C.NDS_RES / "gen5" / "evos.tsv"):
        edges.add((int(p[0]), int(p[1])))
    return edges


def components(edges):
    parent = {i: i for i in range(FIRST, LAST + 1)}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for a, b in edges:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[max(ra, rb)] = min(ra, rb)
    groups = {}
    for i in range(FIRST, LAST + 1):
        groups.setdefault(find(i), []).append(i)
    return groups


def stage_order(members, edges):
    """Members in stage order: the species nothing evolves into first, then their evolutions, ties by number."""
    inside = set(members)
    out_edges = {}
    incoming = set()
    for a, b in edges:
        if a in inside and b in inside:
            out_edges.setdefault(a, []).append(b)
            incoming.add(b)
    order = []
    seen = set()
    level = sorted(m for m in members if m not in incoming)
    while level:
        for m in level:
            if m not in seen:
                seen.add(m)
                order.append(m)
        nxt = set()
        for m in level:
            nxt.update(out_edges.get(m, []))
        level = sorted(x for x in nxt if x not in seen)
    for m in sorted(members):  # a cycle would leave members out; none exists, but never lose a species
        if m not in seen:
            order.append(m)
    return order


def build_lines(edges, names):
    groups = components(edges)
    multi = sorted((sorted(v) for v in groups.values() if len(v) > 1), key=lambda v: v[0])
    lines = []
    for members in multi:
        ordered = stage_order(members, edges)
        label = ", ".join(FORM_NAMES.get(names[m], C.title_case(names[m])) for m in ordered)
        lines.append("%s\t# %s" % (",".join(str(m) for m in ordered), label))
    return lines, multi


def header(n_lines, n_species):
    return [
        "# Evolution lines for the dupes clause, Generation 5 games (2026-09-29): one line per line, baby forms included.",
        "# Species are National Dex numbers (1 to 649) followed by a # comment with the names, the way the DS files of",
        "# the Nuzlocke mode write them. A species on no line is its own line and has no row.",
        "#",
        "# These are the VANILLA lines, %d of them holding %d species. Source: the first two columns of" % (n_lines, n_species),
        "# tracker-nds/src/main/resources/gen5/evos.tsv (base species, evolved species: one row per evolution, 320 rows,",
        "# babies and Shedinja included), which agree edge for edge with the evolution archive (a/0/1/9) of clean",
        "# Black 2 and White 2 ROMs. The baby table of the ROM (a/0/2/0) names, for every species, the form an egg of it",
        "# hatches as; that is the first member of each line here for all 649 species, so the lines are also complete",
        "# against it (Pichu, Cleffa, Igglybuff, Tyrogue, Smoochum, Elekid, Magby, Azurill, Wynaut, Bonsly, Mime Jr.,",
        "# Happiny, Munchlax, Riolu, Mantyke, Budew, Chingling all sit with their evolutions).",
        "# A branching line is one line: Eevee's eight, Wurmple's five, Poliwag's four, Ralts's four, Snorunt's three,",
        "# Tyrogue's four, Burmy's three, Deerling's two. Members are in stage order (baby first), then by number.",
        "# A randomizer that shuffles evolutions changes who belongs together and this file cannot know: the dupes clause",
        "# is then a suggestion the player corrects by hand.",
        "#",
        "# Regenerate: python tools/nuzlocke/gen5_families.py    Check: python tools/nuzlocke/gen5_families.py --check",
        "#",
        "# species numbers<TAB># names",
    ]


def render_lines(lines, n_species):
    return C.render(header(len(lines), n_species) + lines)


def parse_shipped(path):
    """The shipped file's lines as lists of numbers."""
    out = []
    text = C.normalized(path)
    if text is None:
        return None
    for line in text.split("\n"):
        if not line.strip() or line.startswith("#"):
            continue
        out.append([int(x) for x in line.split("#")[0].strip().split(",")])
    return out


def check_against_roms(edges, groups, names, problems, missing):
    """The evolution archive and the baby table of the clean ROMs against evos.tsv and the derived groups."""
    line_of = {}
    for members in groups.values():
        for m in members:
            line_of[m] = min(members)
    for key in ("black2", "white2"):
        rom = R.open_rom(key)
        if rom is None:
            missing.append("%s ROM (%s)" % (key, R.ROMS[key][0]))
            continue
        evo = R.read_evolutions(rom)
        rom_edges = set()
        for sp, es in evo.items():
            if FIRST <= sp <= LAST:
                for _method, _param, target in es:
                    rom_edges.add((sp, target))
        if rom_edges != edges:
            only_rom = sorted(rom_edges - edges)
            only_tsv = sorted(edges - rom_edges)
            problems.append("%s: evolution archive and evos.tsv differ; only in ROM %s, only in evos.tsv %s" % (key, only_rom[:6], only_tsv[:6]))
        baby = R.read_babies(rom)
        bad = []
        for sp in range(FIRST, LAST + 1):
            b = baby.get(sp)
            if b is None or line_of.get(b) != line_of[sp]:
                bad.append((names[sp], b))
        if bad:
            problems.append("%s: baby table names a form outside the species' line for %s" % (key, bad[:8]))
        # every baby form of the task list is in the ROM's baby table as its own root
        for nm, bid in BABIES.items():
            if baby.get(bid) != bid:
                problems.append("%s: %s (%d) is not its own baby form in the ROM" % (key, nm, bid))
        rom.close()


def check(lines, multi, edges, names, problems, missing):
    shipped = parse_shipped(OUT)
    if shipped is None:
        problems.append("%s does not exist" % OUT)
        return
    line_sets = [set(x) for x in shipped]
    for nm, members in EXPECTED.items():
        found = [s for s in line_sets if members & s]
        if members == {489} or members == {490}:
            # Phione and Manaphy: no evolution link in the games' own data, so each is its own line
            if any(len(s) > 1 and members & s for s in line_sets):
                problems.append("%s should be on no line, but is on %s" % (nm, found))
            continue
        if len(found) != 1 or found[0] != members:
            problems.append("hand-built line for %s is %s, shipped has %s" % (nm, sorted(members), [sorted(s) for s in found]))
    for x in shipped:
        for n in x:
            if not FIRST <= n <= LAST:
                problems.append("species number %d outside 1..649" % n)
    flat = [n for x in shipped for n in x]
    if len(flat) != len(set(flat)):
        problems.append("a species is on two lines")


def main():
    flags = C.main_flags()
    problems, missing = [], []
    names = C.species_names()
    edges = read_edges()
    groups = components(edges)
    lines, multi = build_lines(edges, names)
    n_species = sum(len(v) for v in multi)
    text = render_lines(lines, n_species)
    if not flags["check"]:
        OUT.write_text(text, encoding="ascii", newline="\n")
        print("wrote %s (%d lines, %d species)" % (OUT, len(lines), n_species))
    shipped = C.normalized(OUT)
    if shipped is None:
        problems.append("%s does not exist" % OUT)
    else:
        problems.extend(C.diff_lines(text, shipped))
    check(lines, multi, edges, names, problems, missing)
    check_against_roms(edges, groups, names, problems, missing)
    code = C.finish("families-gen5.tsv", problems, missing)
    if code == C.EXIT_OK:
        print("  %d lines, %d species on a line, %d evolution edges (equal to both ROMs)" % (len(lines), n_species, len(edges)))
    return code


if __name__ == "__main__":
    sys.exit(main())
