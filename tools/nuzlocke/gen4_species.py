"""Generates and checks tracker-gba/src/main/resources/nuzlocke/species-gen4.tsv (2026-09-29).

    python tools/nuzlocke/gen4_species.py            # writes the file (LF; then run to_crlf.py on it)
    python tools/nuzlocke/gen4_species.py --check    # re-reads the ROM dumps and pret, compares with the shipped file

One row per National Dex number 1..493: id, name, gender ratio byte, type1, type2 (Gen 3 style type ids).

Sources
  * Platinum (US rev 0) dump: poketool/personal/pl_personal.narc, 44-byte records, gender ratio at +0x10, types at
    +0x06 and +0x07 (Platinum also carries the Diamond-era personal.narc; the game itself reads pl_personal.narc).
  * Diamond (US rev 5) dump: poketool/personal/personal.narc, the same record layout. Every species must agree with
    Platinum in these three fields. No Pearl dump exists here, but pret pokediamond carries Pearl's own personal data
    (files/poketool/personal_pearl/personal.json, which differs from Diamond's only in the wild held items of the
    Electabuzz and Magmar lines); it and Diamond's personal.json are compared for all 493 species too.
  * HeartGold/SoulSilver: pret pokeheartgold files/poketool/personal/personal.json (genderRatio as a female fraction:
    0.0 always male, 1.0 always female, 2.0 genderless, otherwise the byte is int(fraction * 256) - 1), compared for all
    493 species. No HG/SS dump exists here.
  * Both ROMs are also identified by SHA1 against the hashes pret publishes for the builds it reproduces byte for byte.
"""
import os
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen4_common as C  # noqa: E402
import gen4_nds as N  # noqa: E402

FILENAME = "species-gen4.tsv"

# Species whose gender ratio and types are certain from Bulbapedia (Generation IV typing), used to verify the record
# offsets BEFORE anything is read: (dex number, gender ratio byte, type1, type2).
KNOWN = [
    (25, 127, 13, 13),    # Pikachu 50/50 Electric
    (448, 31, 1, 8),      # Lucario 87.5% male Fighting/Steel
    (81, 255, 13, 8),     # Magnemite genderless Electric/Steel
    (113, 254, 0, 0),     # Chansey always female Normal
    (128, 0, 0, 0),       # Tauros always male Normal
    (1, 31, 12, 3),       # Bulbasaur
    (4, 31, 10, 10),      # Charmander
    (7, 31, 11, 11),      # Squirtle
    (6, 31, 10, 2),       # Charizard Fire/Flying
    (94, 127, 7, 3),      # Gengar Ghost/Poison
    (95, 127, 5, 4),      # Onix Rock/Ground
    (208, 127, 8, 4),     # Steelix Steel/Ground
    (248, 127, 5, 17),    # Tyranitar Rock/Dark
    (212, 127, 6, 8),     # Scizor Bug/Steel
    (122, 127, 14, 14),   # Mr. Mime Psychic (no Fairy in Gen 4)
    (124, 254, 15, 14),   # Jynx Ice/Psychic
    (241, 254, 0, 0),     # Miltank
    (242, 254, 0, 0),     # Blissey
    (133, 31, 0, 0),      # Eevee
    (134, 31, 11, 11),    # Vaporeon
    (143, 31, 0, 0),      # Snorlax
    (150, 255, 14, 14),   # Mewtwo
    (151, 255, 14, 14),   # Mew
    (132, 255, 0, 0),     # Ditto
    (137, 255, 0, 0),     # Porygon
    (201, 255, 14, 14),   # Unown
    (249, 255, 14, 2),    # Lugia Psychic/Flying
    (250, 255, 10, 2),    # Ho-Oh Fire/Flying
    (384, 255, 16, 2),    # Rayquaza Dragon/Flying
    (385, 255, 8, 14),    # Jirachi Steel/Psychic
    (386, 255, 14, 14),   # Deoxys
    (376, 255, 8, 14),    # Metagross Steel/Psychic
    (359, 127, 17, 17),   # Absol Dark
    (445, 127, 16, 4),    # Garchomp Dragon/Ground
    (387, 31, 12, 12),    # Turtwig
    (390, 31, 10, 10),    # Chimchar
    (393, 31, 11, 11),    # Piplup
    (479, 255, 13, 7),    # Rotom Electric/Ghost
    (487, 255, 7, 16),    # Giratina Ghost/Dragon
    (492, 255, 12, 12),   # Shaymin Grass
    (493, 255, 0, 0),     # Arceus Normal
    (442, 127, 7, 17),    # Spiritomb Ghost/Dark
    (416, 254, 6, 2),     # Vespiquen Bug/Flying
    (415, 31, 6, 2),      # Combee
    (440, 254, 0, 0),     # Happiny
    (439, 127, 14, 14),   # Mime Jr.
    (29, 254, 3, 3),      # Nidoran F
    (32, 0, 3, 3),        # Nidoran M
    (475, 0, 14, 1),      # Gallade male only Psychic/Fighting
    (478, 254, 15, 7),    # Froslass female only Ice/Ghost
    (237, 0, 1, 1),       # Hitmontop
    (238, 254, 15, 14),   # Smoochum
    (313, 0, 6, 6),       # Volbeat male only
    (314, 254, 6, 6),     # Illumise female only
    (200, 127, 7, 7),     # Misdreavus
    (429, 127, 7, 7),     # Mismagius
]

HG_TYPES = {"NORMAL": 0, "FIGHTING": 1, "FLYING": 2, "POISON": 3, "GROUND": 4, "ROCK": 5, "BUG": 6, "GHOST": 7,
            "STEEL": 8, "MYSTERY": 9, "FIRE": 10, "WATER": 11, "GRASS": 12, "ELECTRIC": 13, "PSYCHIC": 14,
            "ICE": 15, "DRAGON": 16, "DARK": 17}


def hg_ratio_byte(r):
    if r == 0.0:
        return 0
    if r == 1.0:
        return 254
    if r == 2.0:
        return 255
    return int(r * 256) - 1


def load_roms():
    pt = N.NdsRom(C.rom_path("NZ_ROM_PLATINUM", N.PLATINUM_ROM))
    dp = N.NdsRom(C.rom_path("NZ_ROM_DIAMOND", N.DIAMOND_ROM))
    return pt, dp


def rows_and_evidence():
    """Returns (rows, evidence lines, problems)."""
    problems = []
    evidence = []
    pt, dp = load_roms()
    # ROM identity against the SHA1 pret publishes.
    pt_sha = C.pret_text("pokeplatinum", "platinum.us/rom_rev0.sha1").split()[0]
    dp_sha = C.pret_text("pokediamond", "pokediamond.us.sha1").split()[0]
    if pt.sha1 != pt_sha:
        problems.append("Platinum dump SHA1 %s is not pret's %s" % (pt.sha1, pt_sha))
    if dp.sha1 != dp_sha:
        problems.append("Diamond dump SHA1 %s is not pret's %s" % (dp.sha1, dp_sha))
    evidence.append("Platinum %s rev %d sha1 %s == pret rom_rev0.sha1: %s" % (pt.game_code, pt.revision, pt.sha1[:10],
                                                                              pt.sha1 == pt_sha))
    evidence.append("Diamond %s rev %d sha1 %s == pret pokediamond.us.sha1: %s" % (dp.game_code, dp.revision,
                                                                                  dp.sha1[:10], dp.sha1 == dp_sha))
    pers_pt = N.read_personal(pt)
    pers_dp = N.read_personal(dp)
    # 1. verify the offsets on known species first
    for sp, ratio, t1, t2 in KNOWN:
        for label, pers in (("Platinum", pers_pt), ("Diamond", pers_dp)):
            got = (pers[sp][0x10], pers[sp][6], pers[sp][7])
            if got != (ratio, t1, t2):
                problems.append("%s species %d: read %s, expected %s" % (label, sp, got, (ratio, t1, t2)))
    evidence.append("offsets verified on %d known species in both ROMs" % len(KNOWN))
    # 2. every species must agree between the two ROMs; HGSS from pret
    names = C.species_names()
    hg = C.pret_json("pokeheartgold", "files/poketool/personal/personal.json")["baseStats"]
    pd_d = C.pret_json("pokediamond", "files/poketool/personal/personal.json")["baseStats"]
    pd_p = C.pret_json("pokediamond", "files/poketool/personal_pearl/personal.json")["baseStats"]
    by, rev = C.species_by_const()
    rows = []
    disagree_dp = disagree_hg = disagree_pd = 0
    for sp in range(1, 494):
        p = pers_pt[sp]
        d = pers_dp[sp]
        a = (p[0x10], p[6], p[7])
        b = (d[0x10], d[6], d[7])
        if a != b:
            disagree_dp += 1
            problems.append("species %d %s: Platinum %s vs Diamond %s" % (sp, names[sp], a, b))
        h = hg[sp]
        if "SPECIES_" + h["species"] != rev[sp]:
            problems.append("HGSS personal.json entry %d is %s, expected %s" % (sp, h["species"], rev[sp]))
        hga = (hg_ratio_byte(h["genderRatio"]), HG_TYPES[h["types"][0].replace("TYPE_", "")],
               HG_TYPES[h["types"][1].replace("TYPE_", "")])
        if hga != a:
            disagree_hg += 1
            problems.append("species %d %s: Platinum %s vs HeartGold data %s" % (sp, names[sp], a, hga))
        for label, table in (("pret Diamond personal.json", pd_d), ("pret Pearl personal.json", pd_p)):
            e = table[sp]
            if "SPECIES_" + e["species"] != rev[sp]:
                problems.append("%s entry %d is %s, expected %s" % (label, sp, e["species"], rev[sp]))
            ea = (hg_ratio_byte(e["genderRatio"]), HG_TYPES[e["types"][0].replace("TYPE_", "")],
                  HG_TYPES[e["types"][1].replace("TYPE_", "")])
            if ea != a:
                disagree_pd += 1
                problems.append("species %d %s: Platinum %s vs %s %s" % (sp, names[sp], a, label, ea))
        if a[1] < 0 or a[1] > 17 or a[1] == 9 or a[2] < 0 or a[2] > 17 or a[2] == 9:
            problems.append("species %d has an impossible type %s" % (sp, a))
        rows.append((sp, names[sp], a[0], a[1], a[2]))
    evidence.append("Platinum == Diamond for all 493 species in ratio, type1, type2: %s" % (disagree_dp == 0))
    evidence.append("Platinum == pret HeartGold personal.json for all 493 species: %s" % (disagree_hg == 0))
    evidence.append("Platinum == pret pokediamond personal.json (Diamond) and personal_pearl/personal.json (Pearl) "
                    "for all 493 species: %s" % (disagree_pd == 0))
    return rows, evidence, problems


def build(rows):
    raw = C.raw_species_names()
    diff = ["%d %s -> %s" % (i, raw[i].encode("ascii", "backslashreplace").decode("ascii"), n)
            for (i, n, _r, _a, _b) in rows if raw[i] != n]
    lines = [
        "# Species data for the Generation 4 DS games (2026-09-29): what the tracker cannot see from the sidecar.",
        "#",
        "# One row per National Dex number 1..493 (Diamond, Pearl, Platinum, HeartGold, SoulSilver).",
        "# Columns: id, name, genderRatio, type1, type2.",
        "#   id: National Dex number. name: as tracker-nds gen4/species.tsv spells it, made plain ASCII (that file has",
        "#   the female and male signs on the two Nidoran and a typographic apostrophe in Farfetch'd; here they read",
        "#   NIDORAN F, NIDORAN M and FARFETCH'D).",
        "#   genderRatio: the game's own byte. 0 always male, 254 always female, 255 genderless, otherwise the threshold the",
        "#   game compares the personality value's low byte against (female when the low byte is below it).",
        "#   type1, type2: Gen 3 style type ids, 0 Normal, 1 Fighting, 2 Flying, 3 Poison, 4 Ground, 5 Rock, 6 Bug,",
        "#   7 Ghost, 8 Steel, 10 Fire, 11 Water, 12 Grass, 13 Electric, 14 Psychic, 15 Ice, 16 Dragon, 17 Dark.",
        "#   type2 equals type1 for a single-type species (the game stores it that way).",
        "#",
        "# Source: the games' own personal data archive. poketool/personal/pl_personal.narc of a clean Platinum (US rev 0)",
        "# dump, 44-byte records (gender ratio at +0x10, types at +0x06 and +0x07). The offsets were checked on Pikachu",
        "# (127), Lucario (31), Magnemite (255), Chansey (254), Tauros (0) and %d more species before any row was written." % (len(KNOWN) - 5),
        "# Cross-checks, all 493 species: the Diamond (US rev 5) dump's personal.narc agrees in all three fields, and so",
        "# do pret pokeheartgold's personal.json (HeartGold and SoulSilver have no dump here) and pret pokediamond's",
        "# personal.json and personal_pearl/personal.json (Pearl has no dump here; its file differs from Diamond's only",
        "# in the wild held items of the Electabuzz and Magmar lines). The dumps' SHA1 hashes equal the ones pret",
        "# publishes for the builds it reproduces byte for byte (pokeplatinum rom_rev0.sha1, pokediamond.us.sha1).",
        "# Regenerate and check with tools/nuzlocke/gen4_species.py (--check exits non-zero on any mismatch).",
        "#",
        "# id\tname\tgenderRatio\ttype1\ttype2",
    ]
    for (i, n, r, a, b) in rows:
        lines.append("%d\t%s\t%d\t%d\t%d" % (i, n, r, a, b))
    return C.render(lines), diff


def main(argv):
    check = "--check" in argv
    try:
        rows, evidence, problems = rows_and_evidence()
    except C.MissingSource as e:
        return C.finish(FILENAME, [], missing=[str(e)])
    text, diff = build(rows)
    for e in evidence:
        print("  " + e)
    print("  names that differ from gen4/species.tsv only in non-ASCII characters: %s" % "; ".join(diff))
    if check:
        C.compare_with_shipped(FILENAME, text, problems)
        return C.finish(FILENAME, problems)
    if problems:
        return C.finish(FILENAME, problems)
    C.write_out(FILENAME, text)
    return C.EXIT_OK


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
