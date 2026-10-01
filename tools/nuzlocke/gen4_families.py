"""Generates and checks tracker-gba/src/main/resources/nuzlocke/families-gen4.tsv (2026-09-29).

    python tools/nuzlocke/gen4_families.py            # writes the file (LF; then run to_crlf.py on it)
    python tools/nuzlocke/gen4_families.py --check    # re-reads the ROM dumps and pret, compares with the shipped file

Evolution lines for the dupes clause, National Dex numbers 1..493, one line per line of 2 or more species (babies
included, every branch of a branching line on the one line). A species on no line is its own line.

Sources, all five must give the same 246 evolution edges (base species, target species):
  * tracker-nds/src/main/resources/gen4/evos.tsv, first two columns (the reference tracker's table; claim verified here);
  * the Platinum dump's poketool/personal/evo.narc (7 slots of method, parameter, target per species);
  * the Diamond dump's poketool/personal/evo.narc;
  * pret pokeheartgold files/poketool/personal/evo.json (HeartGold and SoulSilver have no dump here);
  * pret pokediamond files/poketool/personal/evo.json (the one evolution file both the Diamond and the Pearl build use).
The lines are the connected components of those edges. Baby forms are edges of the game's own table (Pichu to Pikachu by
friendship and so on), so they sit on their lines without any special case; Shedinja is a second slot of Nincada's
entry. The result is also checked against a hand-written list of lines from Bulbapedia's evolution pages.
"""
import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen4_common as C  # noqa: E402
import gen4_nds as N  # noqa: E402

FILENAME = "families-gen4.tsv"

# Lines that must come out exactly as written (from Bulbapedia's evolution family pages, Generation IV species only).
EXPECTED_LINES = [
    (133, 134, 135, 136, 196, 197, 470, 471),   # Eevee and its seven evolutions
    (236, 106, 107, 237),                       # Tyrogue, Hitmonlee, Hitmonchan, Hitmontop
    (265, 266, 267, 268, 269),                  # Wurmple, Silcoon, Beautifly, Cascoon, Dustox
    (412, 413, 414),                            # Burmy, Wormadam, Mothim
    (60, 61, 62, 186),                          # Poliwag, Poliwhirl, Poliwrath, Politoed
    (280, 281, 282, 475),                       # Ralts, Kirlia, Gardevoir, Gallade
    (361, 362, 478),                            # Snorunt, Glalie, Froslass
    (290, 291, 292),                            # Nincada, Ninjask, Shedinja
    (79, 80, 199),                              # Slowpoke, Slowbro, Slowking
    (43, 44, 45, 182),                          # Oddish, Gloom, Vileplume, Bellossom
    (366, 367, 368),                            # Clamperl, Huntail, Gorebyss
    (172, 25, 26),                              # Pichu, Pikachu, Raichu
    (173, 35, 36),                              # Cleffa, Clefairy, Clefable
    (174, 39, 40),                              # Igglybuff, Jigglypuff, Wigglytuff
    (238, 124),                                 # Smoochum, Jynx
    (239, 125, 466),                            # Elekid, Electabuzz, Electivire
    (240, 126, 467),                            # Magby, Magmar, Magmortar
    (298, 183, 184),                            # Azurill, Marill, Azumarill
    (360, 202),                                 # Wynaut, Wobbuffet
    (438, 185),                                 # Bonsly, Sudowoodo
    (439, 122),                                 # Mime Jr., Mr. Mime
    (440, 113, 242),                            # Happiny, Chansey, Blissey
    (446, 143),                                 # Munchlax, Snorlax
    (447, 448),                                 # Riolu, Lucario
    (458, 226),                                 # Mantyke, Mantine
    (406, 315, 407),                            # Budew, Roselia, Roserade
    (433, 358),                                 # Chingling, Chimecho
    (137, 233, 474),                            # Porygon, Porygon2, Porygon-Z
    (81, 82, 462),                              # Magnemite, Magneton, Magnezone
    (355, 356, 477),                            # Duskull, Dusclops, Dusknoir
    (415, 416),                                 # Combee, Vespiquen
    (420, 421),                                 # Cherubi, Cherrim
    (422, 423),                                 # Shellos, Gastrodon
    (401, 402),                                 # Kricketot, Kricketune
    (443, 444, 445),                            # Gible, Gabite, Garchomp
    (387, 388, 389),                            # Turtwig line
    (390, 391, 392),                            # Chimchar line
    (393, 394, 395),                            # Piplup line
]
# Species that have no evolution and no pre-evolution in Generation 4 (must stay off every line).
LONERS = [83, 115, 127, 128, 131, 132, 142, 150, 151, 201, 203, 206, 211, 213, 214, 222, 227, 234, 235, 241, 249, 250,
          251, 302, 303, 311, 312, 324, 327, 335, 336, 337, 338, 351, 352, 357, 359, 369, 370, 377, 378, 379, 380, 381,
          382, 383, 384, 385, 386, 417, 441, 442, 455] + list(range(479, 494))


def rom_edges(rom):
    evo = N.read_evo(rom)
    edges = set()
    for sp in range(1, 494):
        for (_m, _p, t) in evo[sp]:
            edges.add((sp, t))
    return edges


def tsv_edges():
    edges = set()
    for p in C.read_tsv_rows(C.NDS_RES / "gen4" / "evos.tsv"):
        edges.add((int(p[0]), int(p[1])))
    return edges


def hg_edges():
    by, _rev = C.species_by_const()
    edges = set()
    for e in C.pret_json("pokeheartgold", "files/poketool/personal/evo.json")["evoTable"]:
        b = by.get(e["baseSpecies"])
        if b is None:
            continue
        for x in e["evos"]:
            edges.add((b, by[x["target"]]))
    return edges


def pd_edges():
    by, _rev = C.species_by_const()
    edges = set()
    for e in C.pret_json("pokediamond", "files/poketool/personal/evo.json")["evos"]:
        b = by.get("SPECIES_" + e["species"])
        if b is None:
            continue
        for x in e["evos"]:
            edges.add((b, by["SPECIES_" + x["target"]]))
    return edges


def lines_from(edges):
    """Connected components of the edge set, each ordered baby-first by a depth-first walk (children by number)."""
    kids = {}
    has_parent = set()
    for a, b in edges:
        kids.setdefault(a, []).append(b)
        has_parent.add(b)
    for k in kids:
        kids[k].sort()
    roots = sorted(a for a in kids if a not in has_parent)
    out = []
    seen = set()
    for r in roots:
        order = []
        stack = [r]
        while stack:
            n = stack.pop()
            if n in seen:
                raise ValueError("species %d reached twice: the evolution graph is not a forest" % n)
            seen.add(n)
            order.append(n)
            for c in reversed(kids.get(n, [])):
                stack.append(c)
        out.append(order)
    stray = has_parent - seen
    if stray:
        raise ValueError("species with a parent but no root: %s" % sorted(stray))
    out.sort(key=lambda l: min(l))
    return out


def build(lines):
    names = C.species_names()
    text_lines = [
        "# Evolution lines for the dupes clause, Generation 4 (2026-09-29): one line per line of two or more species,",
        "# National Dex numbers 1..493 separated by commas, a tab and a comment with the names. Baby forms are on their",
        "# lines and every branch of a branching line is on the one line (Eevee, Tyrogue, Wurmple, Burmy, Poliwag,",
        "# Ralts, Snorunt ...). A species on no line is its own line and needs no row.",
        "#",
        "# These are the VANILLA lines, from the games' own evolution tables. A randomizer that shuffles evolutions",
        "# changes who belongs together and this file cannot know: the dupes clause is then a suggestion the player",
        "# corrects by hand.",
        "#",
        "# Source: the evolution edges (species, target) of poketool/personal/evo.narc in a clean Platinum (US rev 0) dump:",
        "# 246 edges for species 1..493. They are identical in a clean Diamond (US rev 5) dump, in pret pokediamond's",
        "# evo.json (the one file the Diamond and Pearl builds share), in pret pokeheartgold's evo.json (HeartGold and",
        "# SoulSilver have no dump here) and in the first two columns of tracker-nds gen4/evos.tsv.",
        "# The babies (Pichu, Cleffa, Igglybuff, Tyrogue, Smoochum, Elekid, Magby, Azurill, Wynaut, Bonsly, Mime Jr.,",
        "# Happiny, Munchlax, Riolu, Mantyke, Budew, Chingling) are edges of that table, so they are already on their",
        "# lines; Shedinja is Nincada's second slot. A line is a connected component of the edges, written baby first.",
        "# Regenerate and check with tools/nuzlocke/gen4_families.py (--check exits non-zero on any mismatch).",
        "#",
        "# numbers\t# names",
    ]
    for line in lines:
        text_lines.append("%s\t# %s" % (",".join(str(n) for n in line),
                                        ", ".join(C.pretty_species(names[n]) for n in line)))
    return C.render(text_lines)


def evidence_and_problems():
    problems = []
    evidence = []
    pt = N.NdsRom(C.rom_path("NZ_ROM_PLATINUM", N.PLATINUM_ROM))
    dp = N.NdsRom(C.rom_path("NZ_ROM_DIAMOND", N.DIAMOND_ROM))
    pt_sha = C.pret_text("pokeplatinum", "platinum.us/rom_rev0.sha1").split()[0]
    dp_sha = C.pret_text("pokediamond", "pokediamond.us.sha1").split()[0]
    if pt.sha1 != pt_sha or dp.sha1 != dp_sha:
        problems.append("a ROM dump does not match pret's SHA1")
    e_pt, e_dp, e_tsv, e_hg, e_pd = rom_edges(pt), rom_edges(dp), tsv_edges(), hg_edges(), pd_edges()
    evidence.append("edges: Platinum ROM %d, Diamond ROM %d, tracker evos.tsv %d, pret HeartGold evo.json %d, "
                    "pret Diamond evo.json %d" % (len(e_pt), len(e_dp), len(e_tsv), len(e_hg), len(e_pd)))
    for label, e in (("Diamond ROM", e_dp), ("tracker evos.tsv", e_tsv), ("pret HeartGold evo.json", e_hg),
                     ("pret Diamond evo.json", e_pd)):
        if e != e_pt:
            problems.append("%s edges differ from the Platinum ROM: only there %s, only in Platinum %s"
                            % (label, sorted(e - e_pt)[:8], sorted(e_pt - e)[:8]))
    lines = lines_from(e_pt)
    members = [n for l in lines for n in l]
    if len(members) != len(set(members)):
        problems.append("a species is on two lines")
    if any(n < 1 or n > 493 for n in members):
        problems.append("a species outside 1..493 is on a line")
    evidence.append("%d lines carrying %d species; %d species stand alone" % (len(lines), len(members),
                                                                              493 - len(members)))
    as_sets = {frozenset(l): l for l in lines}
    for exp in EXPECTED_LINES:
        got = as_sets.get(frozenset(exp))
        if got is None:
            problems.append("expected line %s is not a line of the ROM data" % (exp,))
    for n in LONERS:
        if n in members:
            problems.append("species %d should have no line but is on one" % n)
    evidence.append("%d Bulbapedia lines matched exactly; %d species without a line confirmed off every line"
                    % (len(EXPECTED_LINES), len(LONERS)))
    babies = [172, 173, 174, 236, 238, 239, 240, 298, 360, 438, 439, 440, 446, 447, 458, 406, 433]
    for b in babies:
        if b not in members:
            problems.append("baby %d is on no line" % b)
    evidence.append("all %d baby forms are on a line" % len(babies))
    return lines, evidence, problems


def main(argv):
    check = "--check" in argv
    try:
        lines, evidence, problems = evidence_and_problems()
    except C.MissingSource as e:
        return C.finish(FILENAME, [], missing=[str(e)])
    for e in evidence:
        print("  " + e)
    text = build(lines)
    if check:
        C.compare_with_shipped(FILENAME, text, problems)
        return C.finish(FILENAME, problems)
    if problems:
        return C.finish(FILENAME, problems)
    C.write_out(FILENAME, text)
    return C.EXIT_OK


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
