"""Generate (and check) tracker-gba/src/main/resources/nuzlocke/families-gen2.tsv (2026-09-29).

    python tools/nuzlocke/gen2_families.py            # write the file (LF; then run to_crlf.py)
    python tools/nuzlocke/gen2_families.py --check    # rebuild from the ROM dumps and the disassemblies, compare with the
                                                      # shipped file and with the Gen 1 to 3 block of families-gen3.tsv

The evolution lines come from the games' own evolution tables. In a ROM, EvosAttacksPointers (the randomizer's
PokemonMovesetsTableOffset) is 251 bank-local pointers, one per species; each points at that species' evolution
records, then its level-up moves. A record is
    EVOLVE_LEVEL (1), level, species          EVOLVE_ITEM (2), item, species          EVOLVE_TRADE (3), held item, species
    EVOLVE_HAPPINESS (4), time of day, species          EVOLVE_STAT (5), level, attack-defense test, species  (4 bytes)
and the last record is followed by a 0. Two species are on one line when one evolves into the other. The seven Gen 2
babies (Pichu, Cleffa, Igglybuff, Tyrogue, Smoochum, Elekid, Magby) evolve into their adults in the same table, so they
join their lines with no special case.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen2_common as C  # noqa: E402

OUT = os.path.join(C.NZ, "families-gen2.tsv")
GEN3 = os.path.join(C.NZ, "families-gen3.tsv")
RECORD_SIZE = {1: 3, 2: 3, 3: 3, 4: 3, 5: 4}


def rom_evolutions(rom, game):
    """{species: [evolves into ...]} for species 1..251, walked out of a ROM dump."""
    ini = C.ini_section("Gold (U)" if game == "gs" else "Crystal (U)")
    table = int(ini["PokemonMovesetsTableOffset"], 16)
    bank_base = (table // 0x4000) * 0x4000
    out = {}
    for sp in range(1, 252):
        ptr = rom[table + 2 * (sp - 1)] | (rom[table + 2 * (sp - 1) + 1] << 8)
        pos = bank_base + ptr - 0x4000
        into = []
        while rom[pos] != 0:
            kind = rom[pos]
            size = RECORD_SIZE[kind]
            into.append(rom[pos + size - 1])         # the species is the last byte of a record
            pos += size
        out[sp] = into
    return out


def source_evolutions(root):
    """The same map read from data/pokemon/evos_attacks.asm and evos_attacks_pointers.asm of a disassembly."""
    ids = C.parse_species_ids(root)
    labels = []
    for raw in C.lines_of(root + "/data/pokemon/evos_attacks_pointers.asm"):
        m = re.match(r"^\s*dw\s+(\w+EvosAttacks)\s*$", raw)
        if m:
            labels.append(m.group(1))
    by_label = {}
    cur = None
    for raw in C.lines_of(root + "/data/pokemon/evos_attacks.asm"):
        line = C.strip_comment(raw)
        m = re.match(r"^(\w+EvosAttacks):$", line)
        if m:
            cur = m.group(1)
            by_label[cur] = []
            continue
        if cur is None:
            continue
        m = re.match(r"^db\s+EVOLVE_(LEVEL|ITEM|TRADE|HAPPINESS|STAT)\s*,(.+)$", line)
        if m:
            by_label[cur].append(ids[m.group(2).split(",")[-1].strip()])
        elif line == "db 0":
            cur = None       # the first `db 0` ends the evolutions; the learnset that follows is not read
    if len(labels) != 251:
        C.die("%s lists %d species in EvosAttacksPointers" % (root, len(labels)))
    return {i + 1: by_label[lab] for i, lab in enumerate(labels)}


def families(evo):
    """The evolution lines: [[species, ...]] each ordered by stage (root first, then dex number within a stage),
    the lines ordered by the lowest dex number in them (which keeps every baby with the line it belongs to,
    as families-gen3.tsv does)."""
    parent = {s: s for s in evo}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for a, into in evo.items():
        for b in into:
            parent[find(b)] = find(a)
    groups = {}
    for s in evo:
        groups.setdefault(find(s), []).append(s)
    incoming = {b for into in evo.values() for b in into}
    lines = []
    for members in groups.values():
        if len(members) < 2:
            continue
        roots = sorted(m for m in members if m not in incoming)
        depth = {r: 0 for r in roots}
        queue = list(roots)
        while queue:
            a = queue.pop(0)
            for b in evo[a]:
                if b not in depth:
                    depth[b] = depth[a] + 1
                    queue.append(b)
        lines.append(sorted(members, key=lambda s: (depth[s], s)))
    lines.sort(key=lambda l: min(l))
    return lines


HEADER = """# Evolution lines for the dupes clause, Generation 2 (2026-09-29): one line per line, baby forms included, names as
# natdex/species.tsv spells them. A species on no line is its own line. Species 1 to 251 only.
#
# Derived from the games' own evolution tables: the ROM's EvosAttacksPointers (Gold (U) and Crystal (U) dumps, the
# randomizer's PokemonMovesetsTableOffset) and data/pokemon/evos_attacks.asm of pokegold and pokecrystal, which agree.
# Two species share a line when one evolves into the other by any method (level, stone, trade, happiness, Attack and
# Defense). Where a line branches (Eevee, Tyrogue, Poliwag, Gloom, Slowpoke) every branch is on the one line. The Gen 2
# babies (Pichu, Cleffa, Igglybuff, Tyrogue, Smoochum, Elekid, Magby) can be hatched from eggs, so they are on their lines.
# These are the VANILLA lines; a randomizer that shuffles evolutions changes who belongs together and this file cannot know.
# Gold, Silver and Crystal have identical lines. The Gen 3 babies (Azurill, Wynaut) are not in Generation 2.
#
# tools/nuzlocke/gen2_families.py --check also compares this file with the Gen 1 to 3 block of families-gen3.tsv.
"""


def build_lines():
    names = C.natdex_names()
    rom = C.load_rom("c")
    evo = rom_evolutions(rom, "c")
    return [[names[s] for s in line] for line in families(evo)], families(evo)


def render(lines):
    return HEADER + "".join(",".join(l) + "\n" for l in lines)


def generate():
    lines, _ = build_lines()
    C.write_lf(OUT, render(lines))
    print("wrote %s: %d lines, %d species" % (OUT, len(lines), sum(len(l) for l in lines)))


def gen3_block_restricted():
    """The Gen 1 to 3 block of families-gen3.tsv cut to species 1..251, lines of one species dropped, as name lists."""
    names = C.natdex_names()
    by_name = {v: k for k, v in names.items() if v != "none"}
    out = []
    for raw in C.lines_of(GEN3):
        line = raw.strip()
        if line == "#natdex":
            break
        if not line or line.startswith("#"):
            continue
        ids = [by_name[n] for n in line.split(",")]
        keep = [i for i in ids if i <= 251]
        if len(keep) >= 2:
            out.append([names[i] for i in keep])
    return out


def check():
    probs = C.Problems()
    lines, id_lines = build_lines()
    shipped = [r[0].split(",") for r in C.data_rows(OUT)]
    probs.check(shipped == lines, "the shipped file differs from what the generator builds")
    probs.check(C.clean_crlf_ascii(OUT), "the shipped file is not clean CRLF ASCII (run to_crlf.py)")
    probs.check([l for l in C.lines_of(OUT) if l.startswith("#")] == [l for l in HEADER.split("\n") if l.startswith("#")],
                "header comment differs")
    # both ROM dumps and both disassemblies give the same table
    rom_gs = rom_evolutions(C.load_rom("gs"), "gs")
    rom_c = rom_evolutions(C.load_rom("c"), "c")
    src_gs = source_evolutions(C.REPO["gs"])
    src_c = source_evolutions(C.REPO["c"])
    probs.check(rom_gs == src_gs, "Gold ROM evolutions differ from pokegold")
    probs.check(rom_c == src_c, "Crystal ROM evolutions differ from pokecrystal")
    probs.check(rom_gs == rom_c, "Gold and Crystal evolution tables differ: %s" % [s for s in rom_gs if rom_gs[s] != rom_c[s]])
    probs.check(families(src_gs) == id_lines and families(src_c) == id_lines, "lines from the disassemblies differ from the ROM's")
    # every species is on at most one line, all names resolve, all are Gen 2 species
    names = C.natdex_names()
    flat = [n for l in shipped for n in l]
    probs.check(len(flat) == len(set(flat)), "a species is on two lines")
    by_name = {v: k for k, v in names.items() if v != "none"}
    probs.check(all(n in by_name and by_name[n] <= 251 for n in flat), "a name does not resolve to species 1..251")
    babies = ["Pichu", "Cleffa", "Igglybuff", "Tyrogue", "Smoochum", "Elekid", "Magby"]
    probs.check(all(b in flat for b in babies), "a Gen 2 baby is missing from the lines")
    # the evolved-from counts: every species that evolves is on a line, and every line member is linked by evolution
    evolves = {s for s, into in rom_c.items() if into} | {b for into in rom_c.values() for b in into}
    probs.check({by_name[n] for n in flat} == evolves, "the species on lines are not exactly those in an evolution")
    # against the Gen 1 to 3 block of families-gen3.tsv
    probs.check(gen3_block_restricted() == shipped, "the Gen 1 to 3 block of families-gen3.tsv, cut to species 1..251, differs")
    print("families: %d lines, %d species; evolution records read: %d (Gold ROM), %d (Crystal ROM)" % (
        len(shipped), len(flat), sum(len(v) for v in rom_gs.values()), sum(len(v) for v in rom_c.values())))
    probs.finish("gen2_families --check")


if __name__ == "__main__":
    if sys.argv[1:2] == ["--check"]:
        check()
    else:
        generate()
