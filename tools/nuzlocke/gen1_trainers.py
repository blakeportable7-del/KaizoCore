#!/usr/bin/env python
"""Generate (default) or verify (--check) tracker-gba/src/main/resources/nuzlocke/trainers-gen1.tsv.

    python tools/nuzlocke/gen1_trainers.py            # rewrite the file (LF endings; run to_crlf.py afterwards)
    python tools/nuzlocke/gen1_trainers.py --check    # re-read the disassemblies and the Red, Blue and Yellow ROMs, compare

One row per trainer class (class numbers 1..47 as wTrainerClass holds them, names from data/trainers/names.asm) plus the
specific class:no rows for the fights that need their own label: Giovanni's three parties, and in Yellow Jessie and
James. The party numbers come from the map object data (each trainer object stores its class and party number) and
from the scripts that start a fight by hand; --check reads the same numbers out of the ROMs.
"""
import argparse
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True     # never leave .pyc files behind in the repo
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen1_common as g

OUT = g.NZ_DIR / "trainers-gen1.tsv"

# class constant -> (label, group) for the classes that are more than "a trainer"
NAMED = {
    "PROF_OAK": ("Prof. Oak", "Other"),                # unused in play: three parties exist, no map object or script starts them
    "GIOVANNI": ("Boss Giovanni", "Boss"),             # parties 1 and 2 (Rocket Hideout, Silph Co.); party 3 is the gym (SPECIFIC)
    "ROCKET": ("Team Rocket Grunt", "Other"),          # Gen 1 has no Rocket executives; Yellow's Jessie and James are ROCKET parties (SPECIFIC)
    "BROCK": ("Leader Brock", "Gym"),
    "MISTY": ("Leader Misty", "Gym"),
    "LT_SURGE": ("Leader Lt. Surge", "Gym"),
    "ERIKA": ("Leader Erika", "Gym"),
    "KOGA": ("Leader Koga", "Gym"),
    "BLAINE": ("Leader Blaine", "Gym"),
    "SABRINA": ("Leader Sabrina", "Gym"),
    "LORELEI": ("Elite Four Lorelei", "Elite4"),
    "BRUNO": ("Elite Four Bruno", "Elite4"),
    "AGATHA": ("Elite Four Agatha", "Elite4"),
    "LANCE": ("Elite Four Lance", "Elite4"),
    "RIVAL1": ("Rival Blue", "Rival"),                 # Oak's Lab, Route 22 (first), Cerulean City
    "RIVAL2": ("Rival Blue", "Rival"),                 # S.S. Anne, Pokemon Tower, Silph Co., Route 22 (last)
    "RIVAL3": ("Champion Blue", "Elite4"),             # the Champion fight, Indigo Plateau
}
# (game, class constant, party no, label, group): the fights that get their own row. The party numbers are checked
# against the ROM (map objects for Giovanni, the fight-start scripts for Jessie and James).
SPECIFIC = [
    ("*", "GIOVANNI", 1, "Boss Giovanni", "Boss"),     # Rocket Hideout B4F
    ("*", "GIOVANNI", 2, "Boss Giovanni", "Boss"),     # Silph Co. 11F
    ("*", "GIOVANNI", 3, "Leader Giovanni", "Gym"),    # Viridian Gym
    ("y", "ROCKET", 42, "Boss Jessie and James", "Boss"),   # Mt. Moon B2F
    ("y", "ROCKET", 43, "Boss Jessie and James", "Boss"),   # Rocket Hideout B4F
    ("y", "ROCKET", 44, "Boss Jessie and James", "Boss"),   # Pokemon Tower 7F
    ("y", "ROCKET", 45, "Boss Jessie and James", "Boss"),   # Silph Co. 11F
]
# Where each specific fight happens (map constant), proven from the ROM in --check.
GIOVANNI_MAPS = {1: "ROCKET_HIDEOUT_B4F", 2: "SILPH_CO_11F", 3: "VIRIDIAN_GYM"}
JESSIE_JAMES = {42: "MT_MOON_B2F", 43: "ROCKET_HIDEOUT_B4F", 44: "POKEMON_TOWER_7F", 45: "SILPH_CO_11F"}


def class_label(const, name):
    return NAMED.get(const, (name, "Other"))


def rows():
    """[(game, class, no, label, group)] in file order."""
    d = g.Disasm("red")
    classes = d.class_consts()
    names = d.class_names()
    by_num = {v: k for k, v in classes.items() if v}
    out = []
    for num in range(1, 48):
        const = by_num[num]
        label, group = class_label(const, names[num])
        out.append(("*", num, 0, label, group))
        for game, c, no, lab, grp in SPECIFIC:
            if c == const:
                out.append((game, num, no, lab, grp))
    return out


def render(rs):
    lines = [
        "Who is who in Generation 1 (Red, Blue, Yellow), 2026-09-29: the ledger label and group of each trainer.",
        "",
        "One row per trainer class, then the specific class:no rows. Source: the pret disassemblies pokered and pokeyellow",
        "(constants/trainer_constants.asm and data/trainers/names.asm for the 47 classes, data/trainers/parties.asm for the",
        "parties, data/maps/objects/*.asm and scripts/*.asm for which party number each fight uses); the ROM dumps hold the",
        "same class names, parties, trainer objects and fight-start scripts, and `python tools/nuzlocke/gen1_trainers.py",
        "--check` reads them back.",
        "",
        "game: * for Red, Blue and Yellow, or rb / y for a row only that game has.",
        "class: the class number, what wTrainerClass holds in a trainer battle (BROCK is 34, GIOVANNI 29, RIVAL3 43).",
        "no: the party number within the class (wTrainerNo, 1 based); 0 stands for every party of the class. A row with a",
        "nonzero no wins over the no 0 row of its class, and a game-specific row wins over a * row.",
        "group: Gym, Elite4, Boss, Rival or Other (Elite4 also holds the Champion).",
        "",
        "Notes on particular classes:",
        "- The eight gym leaders: seven have a class of their own with one party (Brock 34, Misty 35, Lt. Surge 36, Erika 37,",
        "  Koga 38, Blaine 39, Sabrina 40). Giovanni (29) has three parties: 1 is the Rocket Hideout B4F fight, 2 the Silph Co.",
        "  11F fight (both Boss) and 3 the Viridian Gym fight (Gym).",
        "- The Elite Four are Lorelei 44, Bruno 33, Agatha 46 and Lance 47, one party each. The Champion is class 43 (RIVAL3),",
        "  which only that fight uses; its party number follows the rival's starter (Red and Blue: 1 Blastoise, 2 Venusaur,",
        "  3 Charizard; Yellow: 1 Jolteon, 2 Flareon, 3 Vaporeon).",
        "- The rival is class 25 (RIVAL1: Oak's Lab, Route 22, Cerulean City), 42 (RIVAL2: S.S. Anne, Pokemon Tower, Silph Co.,",
        "  Route 22) and 43. The game lets the player name him; Blue is the default name, so the label says Blue.",
        "- There are no Rocket executives in Red and Blue. Yellow's Jessie and James are class 30 (ROCKET) parties 42 to 45,",
        "  started by the scripts of Mt. Moon B2F, Rocket Hideout B4F, Pokemon Tower 7F and Silph Co. 11F (parties.asm marks",
        "  them `Jessie & James`, and IsFightingJessieJames in home/trainers2.asm gives class 30 parties from 42 up their",
        "  picture). Parties 46 to 49 of Yellow's class 30 are four unused ones that share that picture; they are not fights.",
        "- Prof. Oak (26) has three parties that no map object or script ever starts. Chief (27) and the first Juggler (13) have",
        "  no party at all. They are here because every class has a row.",
        "",
        "game\tclass\tno\tlabel\tgroup",
    ]
    out = g.header_block(lines[:-1]) + "# " + lines[-1] + "\n"
    for game, c, no, label, group in rs:
        out += "%s\t%d\t%d\t%s\t%s\n" % (game, c, no, label, group)
    return out


def generate():
    return render(rows())


# ----------------------------------------------------------------------------------------------- check

def trainer_objects_rom(rom, d):
    """[(map const, class, no)] for every trainer object in the ROM's map data (objects whose byte is above 200)."""
    out = []
    for m in d.maps():
        if m["unused"] or m["const"] not in d.objects():
            continue
        for o in rom.map_objects(m["id"])["objs"]:
            if o["kind"] == "mon_or_trainer" and o["arg1"] > g.OPP_ID_OFFSET:
                out.append((m["const"], o["arg1"] - g.OPP_ID_OFFSET, o["arg2"]))
    return out


def trainer_objects_asm(d):
    out = []
    cls = d.class_consts()
    for const, rec in d.objects().items():
        for o in rec["objs"]:
            if o["kind"] == "mon_or_trainer" and o["arg1"].startswith("OPP_"):
                out.append((const, cls[o["arg1"][4:]], o["arg2"]))
    return out


_WRAM = {}


def wram(build):
    """Addresses of wCurOpponent and wTrainerNo for a build (tools/wram_layout.py), for the script byte patterns."""
    folder = g.BUILDS[build][0]
    if folder not in _WRAM:
        sys.path.insert(0, str(g.ROOT / "tools"))
        import contextlib
        import io
        import wram_layout
        with contextlib.redirect_stderr(io.StringIO()):
            _WRAM[folder] = wram_layout.layout(g.REFS / folder)[0]
    return _WRAM[folder]["wCurOpponent"], _WRAM[folder]["wTrainerNo"]


def find_all(data, pat):
    out, i = [], data.find(pat)
    while i >= 0:
        out.append(i)
        i = data.find(pat, i + 1)
    return out


def check():
    errors = []

    def err(msg):
        errors.append(msg)
        print("MISMATCH:", msg)

    text = generate()
    if not OUT.exists():
        err("%s does not exist" % OUT)
    elif not g.compare_text(OUT, text):
        err("%s differs from what the disassembly derives (run the script without --check to rewrite it)" % OUT.name)
    if not text.isascii():
        err("non-ASCII text in the generated file")

    # the shipped file, parsed the way the reader does
    shipped = []
    if OUT.exists():
        for line in g.read_text(OUT).splitlines():
            if line and not line.startswith("#"):
                p = line.split("\t")
                shipped.append((p[0], int(p[1]), int(p[2]), p[3], p[4]))
    have0 = {c for (gm, c, no, _, _) in shipped if no == 0 and gm == "*"}
    if have0 != set(range(1, 48)):
        err("classes without a * no-0 row: %s" % sorted(set(range(1, 48)) - have0))
    for gm, c, no, label, group in shipped:
        if group not in ("Gym", "Elite4", "Boss", "Rival", "Other") or gm not in ("*", "rb", "y"):
            err("bad row %r" % ((gm, c, no, label, group),))

    summary = []
    for build in ("red", "blue", "yellow"):
        d = g.Disasm(build)
        rom = g.Rom(build)
        rom.verify_build()
        classes = d.class_consts()
        num = {k: v for k, v in classes.items() if v}
        # 1. the 47 class names in the ROM (TrainerNames, the second TrainerClassNamesOffsets entry) equal the disassembly's
        cm = g.charmap(build)
        p = g.ini_list(rom.ini, "TrainerClassNamesOffsets")[1]
        names = []
        for _ in range(47):
            s = []
            while rom.data[p] != 0x50:
                s.append(cm.get(rom.data[p], "?"))
                p += 1
            p += 1
            names.append(g.norm_class("".join(s)))
        if names != [d.class_names()[i] for i in range(1, 48)]:
            err("%s: class names in the ROM differ from names.asm" % build)
        # 2. every party of every class, ROM against disassembly (the walk gen1_trainer_rom.py ports)
        sp = d.species_consts()
        table = rom.trainer_table()
        n_parties = 0
        for cls, parties in d.parties().items():
            want = [[(lv, sp[s]) for lv, s in pt] for pt in parties]
            if table.get(cls, []) != want:
                err("%s: parties of class %d differ between the ROM and parties.asm" % (build, cls))
            n_parties += len(want)
        # 3. trainer objects: same class and party numbers in the ROM and in the disassembly
        o_rom, o_asm = trainer_objects_rom(rom, d), trainer_objects_asm(d)
        if sorted(o_rom) != sorted(o_asm):
            err("%s: trainer objects differ between the ROM and the map object files" % build)
        # 4. the specific rows
        game = g.GAME_OF[build]
        spec = [(c, no) for (gm, c, no, _, _) in shipped if no and gm in ("*", game)]
        for (cnum, no) in spec:
            if cnum == num["GIOVANNI"]:
                where = [m for (m, c, n) in o_rom if c == cnum and n == no]
                if where != [GIOVANNI_MAPS[no]]:
                    err("%s: Giovanni party %d is used by %s in the ROM, expected %s" % (build, no, where, GIOVANNI_MAPS[no]))
        # Giovanni's three parties are the Rocket Hideout, Silph Co. and gym fights, in that order
        gp = table[num["GIOVANNI"]]
        if len(gp) != 3:
            err("%s: Giovanni has %d parties in the ROM" % (build, len(gp)))
        rock = table[num["ROCKET"]]
        opp, no_addr = wram(build)
        if build == "yellow":
            # Jessie and James: the script that starts each fight sets wCurOpponent to ROCKET and wTrainerNo to 42..45
            for no, mp in JESSIE_JAMES.items():
                pat = bytes([0x3E, g.OPP_ID_OFFSET + num["ROCKET"], 0xEA, opp & 255, opp >> 8, 0x3E, no, 0xEA, no_addr & 255, no_addr >> 8])
                hits = find_all(rom.data, pat)
                if len(hits) != 1:
                    err("yellow: expected one script for Jessie and James party %d (%s), found %d" % (no, mp, len(hits)))
                if any(x[1:] == (num["ROCKET"], no) for x in o_rom):
                    err("yellow: a map object starts Rocket party %d (they are script fights)" % no)
            if len(rock) != 49:
                err("yellow: Rocket class has %d parties, expected 49" % len(rock))
            jj = [tuple(s for _, s in rock[no - 1]) for no in JESSIE_JAMES]
            want = [tuple(sp[x] for x in t) for t in [("EKANS", "MEOWTH", "KOFFING"), ("KOFFING", "MEOWTH", "EKANS"),
                                                      ("MEOWTH", "ARBOK", "WEEZING"), ("WEEZING", "ARBOK", "MEOWTH")]]
            if jj != want:
                err("yellow: Rocket parties 42-45 are not Jessie and James's teams")
        else:
            if len(rock) != 41:
                err("%s: Rocket class has %d parties, expected 41" % (build, len(rock)))
        # the three rival classes are started by a script that loads their OPP_ constant into wCurOpponent
        for c in ("RIVAL1", "RIVAL2", "RIVAL3"):
            pat = bytes([0x3E, g.OPP_ID_OFFSET + num[c], 0xEA, opp & 255, opp >> 8])
            if not find_all(rom.data, pat):
                err("%s: no script loads %s into wCurOpponent" % (build, c))
            # the Oak's Lab and S.S. Anne rival objects carry OPP_RIVAL1, 1 as a placeholder; a script starts the real fight
            if c != "RIVAL1" and any(x[1] == num[c] for x in o_rom):
                err("%s: a map object starts a %s party" % (build, c))
        summary.append("%s: %d class names, %d parties and %d trainer objects equal in ROM and disassembly" % (
            build, len(names), n_parties, len(o_rom)))
    print("\n".join(summary))
    if errors:
        print("FAILED: %d mismatch(es)" % len(errors))
        return 1
    print("OK: trainers-gen1.tsv matches the disassemblies and the Red, Blue and Yellow ROM dumps")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="verify the shipped file against the sources, do not write")
    ap.add_argument("--stdout", action="store_true", help="print the file instead of writing it")
    a = ap.parse_args()
    if a.check:
        return check()
    text = generate()
    if a.stdout:
        sys.stdout.write(text)
    else:
        g.write_lf(OUT, text)
        print("wrote", OUT, "(%d lines)" % text.count("\n"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
