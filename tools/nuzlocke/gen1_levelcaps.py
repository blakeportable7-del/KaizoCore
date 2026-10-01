#!/usr/bin/env python
"""Generate (default) or verify (--check) tracker-gba/src/main/resources/nuzlocke/levelcaps-gen1.tsv.

    python tools/nuzlocke/gen1_levelcaps.py            # rewrite the file (LF endings; run to_crlf.py afterwards)
    python tools/nuzlocke/gen1_levelcaps.py --check    # re-read the disassemblies and the Red, Blue and Yellow ROMs, compare

Each cap is the highest level on the boss's team, computed from pokered / pokeyellow data/trainers/parties.asm. The
check reads the same level out of the ROMs with gen1_trainer_rom.max_level, finds each leader in the map object data
of the ROM, finds the badge bit each gym script sets in the ROM's code, and compares the numbers with the research
doc's tables (docs/research/nuzlocke-gen1-3.md sections 2.1 and 3.1; a disagreement is printed, the game data wins).
"""
import argparse
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True     # never leave .pyc files behind in the repo
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen1_common as g
import gen1_trainer_rom as tr

OUT = g.NZ_DIR / "levelcaps-gen1.tsv"

# key, label, class constant, party number, gym script file (in the order of the badge byte, which is also the usual order)
GYMS = [
    ("gym1", "Brock", "BROCK", 1, "PewterGym"),
    ("gym2", "Misty", "MISTY", 1, "CeruleanGym"),
    ("gym3", "Lt. Surge", "LT_SURGE", 1, "VermilionGym"),
    ("gym4", "Erika", "ERIKA", 1, "CeladonGym"),
    ("gym5", "Koga", "KOGA", 1, "FuchsiaGym"),
    ("gym6", "Sabrina", "SABRINA", 1, "SaffronGym"),
    ("gym7", "Blaine", "BLAINE", 1, "CinnabarGym"),
    ("gym8", "Giovanni", "GIOVANNI", 3, "ViridianGym"),
]
# the map each leader stands in (for the ROM object check)
LEADER_MAP = {"BROCK": "PEWTER_GYM", "MISTY": "CERULEAN_GYM", "LT_SURGE": "VERMILION_GYM", "ERIKA": "CELADON_GYM",
              "KOGA": "FUCHSIA_GYM", "SABRINA": "SAFFRON_GYM", "BLAINE": "CINNABAR_GYM", "GIOVANNI": "VIRIDIAN_GYM",
              "LORELEI": "LORELEIS_ROOM", "BRUNO": "BRUNOS_ROOM", "AGATHA": "AGATHAS_ROOM", "LANCE": "LANCES_ROOM"}
E4 = [("e4-1", "Lorelei", "LORELEI", 1), ("e4-2", "Bruno", "BRUNO", 1), ("e4-3", "Agatha", "AGATHA", 1),
      ("e4-4", "Lance", "LANCE", 1)]
CHAMPION_ACE = {"rb": "his starter's last stage", "y": "his Eevee's last stage"}
GAME_KEYS = (("rb", "red"), ("y", "yellow"))


def badge_bit(d, script):
    """The bit of wObtainedBadges the gym's script sets when the leader is beaten (its first `set BIT_xBADGE`)."""
    ram = d.ram_consts()
    for line in d.lines("scripts/%s.asm" % script):
        m = re.match(r"set\s+(BIT_\w+BADGE)\s*,", line)
        if m:
            return ram[m.group(1)]
    raise ValueError("no badge bit set in scripts/%s.asm" % script)


def ace_of(d, party, names):
    top = max(lv for lv, _ in party)
    seen = []
    for lv, s in party:
        n = names[d.dex_of_internal()[d.species_consts()[s]]]
        if lv == top and n not in seen:
            seen.append(n)
    return " or ".join(seen)


def boss_rows(build):
    """[(seq, key, kind, label, cap, ace, ids, badge)] for one build's game."""
    d = g.Disasm(build)
    game = g.GAME_OF[build]
    names = g.natdex_names()
    cls = d.class_consts()
    parties = d.parties()
    rows = []
    seq = 0
    for key, label, const, no, script in GYMS:
        seq += 1
        party = parties[cls[const]][no - 1]
        rows.append((seq, key, "gym", label, max(lv for lv, _ in party), ace_of(d, party, names), ["%d:%d" % (cls[const], no)],
                     badge_bit(d, script)))
    for key, label, const, no in E4:
        seq += 1
        party = parties[cls[const]][no - 1]
        rows.append((seq, key, "e4", label, max(lv for lv, _ in party), ace_of(d, party, names), ["%d:%d" % (cls[const], no)], None))
    seq += 1
    champ = parties[cls["RIVAL3"]]
    caps = {max(lv for lv, _ in p) for p in champ}
    if len(caps) != 1:
        raise ValueError("the three Champion parties have different top levels: %s" % caps)
    rows.append((seq, "champion", "champion", "Blue", caps.pop(), CHAMPION_ACE[game],
                 ["%d:%d" % (cls["RIVAL3"], i + 1) for i in range(len(champ))], None))
    return rows


def render(by_game):
    lines = [
        "Hardcore Nuzlocke level caps for the vanilla Generation 1 games (2026-09-29).",
        "",
        "A cap is the level of the highest-level Pokemon on the boss's team in the first-run fight. That is the definition",
        "Bulbapedia gives for the Hardcore Level Cap rule and the one Nuzlocke University uses.",
        "",
        "Source: the pret disassemblies pokered and pokeyellow, data/trainers/parties.asm. Every number was computed from",
        "that file and read back out of the clean ROM dumps (red-u, blue-u, yellow-u) with tools/nuzlocke/gen1_trainer_rom.py",
        "(`python tools/nuzlocke/gen1_levelcaps.py --check` does both and compares docs/research/nuzlocke-gen1-3.md",
        "sections 2.1 and 3.1). Red and Blue share every party, so one set (rb) covers both; Yellow (y) has its own.",
        "",
        "These are the FALLBACK. When the tracker can read the trainer data out of the loaded ROM it uses the boss's real",
        "party levels instead (that is what a randomized or level-scaled game needs).",
        "",
        "game: rb = Red and Blue, y = Yellow.",
        "key: gymN is the Nth badge in the order of the game's badge byte, which is also the usual order of the fights (gyms 3",
        "to 7 can be fought in almost any order; Koga and Sabrina both cap at 43 in Red and Blue and at 50 in Yellow, Blaine",
        "is 47 and 54); e4-N is the Nth Elite Four member; champion is the rival's fight in the Champion's room.",
        "ids: class:no, the trainer class (what wTrainerClass holds) and the party number within the class (wTrainerNo),",
        "both in decimal. Giovanni's Viridian Gym fight is party 3 of class 29 (parties 1 and 2 are his Rocket Hideout and",
        "Silph Co. fights). The Champion is class 43, one party per rival starter: Red and Blue 1 Blastoise, 2 Venusaur,",
        "3 Charizard (the rival takes the starter that beats yours); Yellow 1 Jolteon (you won at Oak's Lab and on Route 22),",
        "2 Flareon (you won at the lab and lost or skipped Route 22), 3 Vaporeon (you lost at the lab). Every team of a game",
        "tops out at the same level, so the cap does not depend on the starter.",
        "badge: the 0-based bit of the badge byte (wObtainedBadges) that the leader's script sets when you win: Boulder 0",
        "(Brock), Cascade 1 (Misty), Thunder 2 (Lt. Surge), Rainbow 3 (Erika), Soul 4 (Koga), Marsh 5 (Sabrina), Volcano 6",
        "(Blaine), Earth 7 (Giovanni), read from `set BIT_xBADGE` in scripts/<Gym>.asm. That is bit N-1 for gym N. The badge",
        "byte's address is 0xD356 in Red and Blue and 0xD355 in Yellow. The Earth Badge is always last: Viridian Gym stays",
        "shut until the other seven badges are held.",
        "group: empty for every Generation 1 row.",
        "",
        "game\tseq\tkey\tkind\tlabel\tcap\tace\tids\tbadge\tgroup",
    ]
    out = g.header_block(lines[:-1]) + "# " + lines[-1] + "\n"
    for game, _ in GAME_KEYS:
        for seq, key, kind, label, cap, ace, ids, badge in by_game[game]:
            out += "%s\t%d\t%s\t%s\t%s\t%d\t%s\t%s\t%s\t\n" % (
                game, seq, key, kind, label, cap, ace, ",".join(ids), "" if badge is None else badge)
    return out


def generate():
    by_game = {game: boss_rows(build) for game, build in GAME_KEYS}
    return render(by_game), by_game


def doc_tables():
    """{'rb': [(label, cap)], 'y': [...]} from the research doc's first table under 2.1 and 3.1 (13 rows each)."""
    text = g.read_text(g.ROOT / "docs/research/nuzlocke-gen1-3.md")
    out = {}
    for game, head in (("rb", "### 2.1 Level caps"), ("y", "### 3.1 Level caps")):
        start = text.index(head)
        rows = []
        for line in text[start:].splitlines()[1:]:
            m = re.match(r"\|\s*(\d+|E4-\d|C)\s*\|\s*([^|]+?)\s*\|\s*[^|]*\|\s*(\d+)\s*\|\s*([^|]+?)\s*\|", line)
            if m:
                rows.append((m.group(2), int(m.group(3)), m.group(4)))
            elif rows and not line.startswith("|"):
                break
        out[game] = rows
    return out


def wram_badges(build):
    sys.path.insert(0, str(g.ROOT / "tools"))
    import contextlib
    import io
    import wram_layout
    with contextlib.redirect_stderr(io.StringIO()):
        return wram_layout.layout(g.REFS / g.BUILDS[build][0])[0]["wObtainedBadges"]


def find_all(data, pat):
    out, i = [], data.find(pat)
    while i >= 0:
        out.append(i)
        i = data.find(pat, i + 1)
    return out


def check():
    import gen1_trainers as T
    errors = []

    def err(msg):
        errors.append(msg)
        print("MISMATCH:", msg)

    text, by_game = generate()
    if not OUT.exists():
        err("%s does not exist" % OUT)
    elif not g.compare_text(OUT, text):
        err("%s differs from what the disassembly derives (run the script without --check to rewrite it)" % OUT.name)
    if not text.isascii():
        err("non-ASCII text in the generated file")

    # the shipped file, parsed the way the reader does
    shipped = {"rb": [], "y": []}
    if OUT.exists():
        for line in g.read_text(OUT).splitlines():
            if line and not line.startswith("#"):
                p = line.split("\t")
                shipped[p[0]].append((int(p[1]), p[2], p[3], p[4], int(p[5]), p[6], p[7].split(","), None if p[8] == "" else int(p[8])))
    for game in shipped:
        if shipped[game] != by_game[game]:
            err("shipped rows for %s differ from the derived ones" % game)

    docs = doc_tables()
    summary = []
    for build in ("red", "blue", "yellow"):
        game = g.GAME_OF[build]
        d = g.Disasm(build)
        rom = g.Rom(build)
        rom.verify_build()
        cls = d.class_consts()
        o_rom = T.trainer_objects_rom(rom, d)
        badges_addr = wram_badges(build)
        n_ids = 0
        for seq, key, kind, label, cap, ace, ids, badge in shipped[game]:
            got = 0
            for tok in ids:
                c, no = (int(x) for x in tok.split(":"))
                got = max(got, tr.max_level(rom.data, game, c, no))
                n_ids += 1
                if kind in ("gym", "e4"):
                    const = [k for k, v in cls.items() if v == c][0]
                    if (LEADER_MAP[const], c, no) not in o_rom:
                        err("%s: no trainer object (%s, class %d, party %d) in the ROM" % (build, LEADER_MAP[const], c, no))
            if got != cap:
                err("%s %s: ROM says the top level is %d, the file says %d" % (build, key, got, cap))
            if kind == "champion":
                for tok in ids:
                    c, no = (int(x) for x in tok.split(":"))
                    if tr.max_level(rom.data, game, c, no) != cap:
                        err("%s: Champion party %d top level differs" % (build, no))
                if len(ids) != len(d.parties()[cls["RIVAL3"]]):
                    err("%s: the Champion ids are not every party of class 43" % build)
            if badge is not None:
                # the leader's script sets that bit of the badge byte in the ROM: ld hl, wObtainedBadges ; set bit, [hl]
                pat = bytes([0x21, badges_addr & 255, badges_addr >> 8, 0xCB, 0xC6 + 8 * badge])
                if not find_all(rom.data, pat):
                    err("%s %s: no `set %d, [wObtainedBadges]` in the ROM" % (build, key, badge))
        # the research doc's numbers (the doc has one Red/Blue table and one Yellow table)
        if build != "blue":
            for (seq, key, kind, label, cap, ace, ids, badge), (dlabel, dcap, dace) in zip(shipped[game], docs[game]):
                if dcap != cap:
                    print("DOC DISAGREES: %s %s cap %d, research doc %s says %d" % (game, key, cap, dlabel, dcap))
                    errors.append("doc")
            if len(docs[game]) != len(shipped[game]):
                print("DOC DISAGREES: %s has %d rows in the doc, %d here" % (game, len(docs[game]), len(shipped[game])))
                errors.append("doc")
        summary.append("%s: %d fights (%d class:no ids) read from the ROM, top levels and %d badge bits match" % (
            build, len(shipped[game]), n_ids, sum(1 for r in shipped[game] if r[7] is not None)))
    print("\n".join(summary))
    if errors:
        print("FAILED: %d mismatch(es)" % len(errors))
        return 1
    print("OK: levelcaps-gen1.tsv matches the disassemblies, the Red, Blue and Yellow ROM dumps and the research doc")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="verify the shipped file against the sources, do not write")
    ap.add_argument("--stdout", action="store_true", help="print the file instead of writing it")
    a = ap.parse_args()
    if a.check:
        return check()
    text, _ = generate()
    if a.stdout:
        sys.stdout.write(text)
    else:
        g.write_lf(OUT, text)
        print("wrote", OUT, "(%d lines)" % text.count("\n"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
