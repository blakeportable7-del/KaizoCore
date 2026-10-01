"""Generate (and check) tracker-gba/src/main/resources/nuzlocke/trainers-gen2.tsv (2026-09-29).

    python tools/nuzlocke/gen2_trainers.py            # write the file (LF; then run to_crlf.py)
    python tools/nuzlocke/gen2_trainers.py --check    # rebuild in memory, compare with the shipped file, and prove
                                                      # every row against the disassemblies, the map scripts and the ROMs

Columns: game, class, no, label, group. See the header comment the generator writes for the meaning of each.
Class numbers and party numbers are the ones the game stores in wOtherTrainerClass and wOtherTrainerID.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen2_common as C  # noqa: E402
import gen2_trainer_rom as R  # noqa: E402

OUT = os.path.join(C.NZ, "trainers-gen2.tsv")

# ---------------------------------------------------------------------------------------- the people
# Johto and Kanto gym leaders: class constant -> name as the fight reads. Every one is a class of one party.
LEADERS = {
    "FALKNER": "Falkner", "BUGSY": "Bugsy", "WHITNEY": "Whitney", "MORTY": "Morty", "CHUCK": "Chuck",
    "JASMINE": "Jasmine", "PRYCE": "Pryce", "CLAIR": "Clair",
    "BROCK": "Brock", "MISTY": "Misty", "LT_SURGE": "Lt. Surge", "ERIKA": "Erika", "JANINE": "Janine",
    "SABRINA": "Sabrina", "BLAINE": "Blaine", "BLUE": "Blue",
}
ELITE4 = {"WILL": "Elite Four Will", "KOGA": "Elite Four Koga", "BRUNO": "Elite Four Bruno",
          "KAREN": "Elite Four Karen", "CHAMPION": "Champion Lance"}
# Rival fights, by stage (the party number is (stage - 1) * 3 + starter for RIVAL1 and RIVAL2 alike), each with the
# map script that starts it, checked by --check. RIVAL1 = the rival in Johto, RIVAL2 = after the Elite Four.
RIVAL_STAGES = {
    "RIVAL1": [("Cherrygrove City", "CherrygroveCity.asm"), ("Azalea Town", "AzaleaTown.asm"),
               ("Burned Tower", "BurnedTower1F.asm"),
               ("Goldenrod Tunnel", "GoldenrodUndergroundSwitchRoomEntrances.asm"),
               ("Victory Road", "VictoryRoad.asm")],
    "RIVAL2": [("Mt. Moon", "MountMoon.asm"), ("Indigo Plateau", "IndigoPlateauPokecenter1F.asm")],
}
# The Team Rocket executives. The games call every one of them EXECUTIVE (the party name in parties.asm); the names
# below are the community's (Bulbapedia, HeartGold and SoulSilver), matched to the fights by team and place:
# the map script that starts each fight is checked by --check.
EXECUTIVES = [
    ("EXECUTIVEM", 1, "Executive Archer", "RadioTower5F.asm"),
    ("EXECUTIVEM", 2, "Executive Proton", "RadioTower4F.asm"),
    ("EXECUTIVEM", 3, "Executive Petrel, Radio Tower", "RadioTower5F.asm"),
    ("EXECUTIVEM", 4, "Executive Petrel, Team Rocket HQ", "TeamRocketBaseB3F.asm"),
    ("EXECUTIVEF", 1, "Executive Ariana, Radio Tower", "RadioTower5F.asm"),
    ("EXECUTIVEF", 2, "Executive Ariana, Team Rocket HQ", "TeamRocketBaseB2F.asm"),
]
# Ordinary class names as the ledger spells them, where the game's own text cannot be used as is (it holds the
# male and female signs, the Poke abbreviation or another class's word).
CLASS_LABEL = {
    "POKEMON_PROF": "Pokemon Prof.", "CAL": "PKMN Trainer",
    "GRUNTM": "Team Rocket Grunt", "GRUNTF": "Team Rocket Grunt",
    "COOLTRAINERM": "Cooltrainer (M)", "COOLTRAINERF": "Cooltrainer (F)",
    "SWIMMERM": "Swimmer (M)", "SWIMMERF": "Swimmer (F)",
    "POKEFANM": "Pokefan (M)", "POKEFANF": "Pokefan (F)",
    "POKEMANIAC": "Pokemaniac", "MYSTICALMAN": "Mystical Man",
}
GROUPS = ("Gym", "Elite4", "Boss", "Rival", "Other")
RESEARCH = os.path.join(C.WORKTREE, "docs", "research", "nuzlocke-gen1-3.md")
# The research doc's "Other cap points" rows (sections 4.1 and 5.1): doc label, class, party, words my label must hold.
DOC_ROWS = [
    ("Rival Silver, Cherrygrove City", "RIVAL1", 1, ["Rival", "Cherrygrove"]),
    ("Rival Silver, Azalea Town", "RIVAL1", 4, ["Rival", "Azalea"]),
    ("Rival Silver, Burned Tower", "RIVAL1", 7, ["Rival", "Burned Tower"]),
    ("Rival Silver, Goldenrod Tunnel", "RIVAL1", 10, ["Rival", "Goldenrod"]),
    ("Rival Silver, Victory Road", "RIVAL1", 13, ["Rival", "Victory Road"]),
    ("Rival Silver, Mt. Moon", "RIVAL2", 1, ["Rival", "Mt. Moon"]),
    ("Rival Silver, Indigo Plateau", "RIVAL2", 4, ["Rival", "Indigo Plateau"]),
    ("Executive Petrel, Rocket HQ", "EXECUTIVEM", 4, ["Petrel", "Rocket HQ"]),
    ("Executive Ariana, Rocket HQ", "EXECUTIVEF", 2, ["Ariana", "Rocket HQ"]),
    ("Executive Petrel, Radio Tower", "EXECUTIVEM", 3, ["Petrel", "Radio Tower"]),
    ("Executive Ariana, Radio Tower", "EXECUTIVEF", 1, ["Ariana", "Radio Tower"]),
    ("Executive Archer, Radio Tower", "EXECUTIVEM", 1, ["Archer"]),
    ("Executive Proton, Radio Tower", "EXECUTIVEM", 2, ["Proton"]),
    ("Eusine (Crystal only)", "MYSTICALMAN", 1, ["Eusine"]),
]


def class_rows(game_key):
    """[(class_no, const, name text)] for one game, class 1 upward."""
    root = C.REPO[game_key]
    classes = C.parse_trainer_classes(root)[1:]
    names = C.parse_class_names(root)
    assert len(classes) == len(names), (game_key, len(classes), len(names))
    return [(no, const, nm) for (no, const, _), nm in zip(classes, names)]


def plain_label(const, text):
    if const in CLASS_LABEL:
        return CLASS_LABEL[const]
    if not text.replace(" ", "").isalpha():
        C.die("class %s (%r) needs a label override" % (const, text))
    return " ".join(w.capitalize() for w in text.split(" "))


def build_rows():
    """[(game, class, no, label, group)] in file order, plus the notes the header needs."""
    gs = class_rows("gs")
    c = class_rows("c")
    # classes 1..66 are the same constants in the same order in both games; Crystal adds 67
    for a, b in zip(gs, c):
        if a[:2] != b[:2]:
            C.die("class tables differ between Gold and Crystal at %r / %r" % (a, b))
    if len(c) != len(gs) + 1 or c[-1][1] != "MYSTICALMAN":
        C.die("Crystal is expected to add exactly MYSTICALMAN")
    cls_no = {const: no for no, const, _ in c}
    text = {const: t for _, const, t in c}
    rows = []

    def add(game, const, no, label, group):
        rows.append((game, cls_no[const], no, label, group))

    for no, const, _ in c:
        game = "c" if const == "MYSTICALMAN" else "*"
        if const in LEADERS:
            add(game, const, 0, "Leader " + LEADERS[const], "Gym")
            add(game, const, 1, "Leader " + LEADERS[const], "Gym")
        elif const in ELITE4:
            add(game, const, 0, ELITE4[const], "Elite4")
            add(game, const, 1, ELITE4[const], "Elite4")
        elif const in RIVAL_STAGES:
            add(game, const, 0, "Rival Silver", "Rival")
            for stage, (place, _f) in enumerate(RIVAL_STAGES[const], 1):
                for starter in range(3):
                    add(game, const, (stage - 1) * 3 + starter + 1, "Rival Silver, " + place, "Rival")
        elif const in ("EXECUTIVEM", "EXECUTIVEF"):
            add(game, const, 0, "Team Rocket Executive", "Boss")
            for cn, party, label, _f in EXECUTIVES:
                if cn == const:
                    add(game, const, party, label, "Boss")
        elif const == "RED":
            add(game, const, 0, "Boss Red", "Boss")
            add(game, const, 1, "Boss Red", "Boss")
        elif const == "MYSTICALMAN":
            add(game, const, 0, "Boss Eusine", "Boss")
            add(game, const, 1, "Boss Eusine", "Boss")
        else:
            add(game, const, 0, plain_label(const, text[const]), "Other")
    return rows


HEADER = """# Who is who in Generation 2 (Gold, Silver, Crystal): the trainer class and party numbers the game stores, and how the
# fight reads in the ledger (2026-09-29).
#
# Sources: the pret disassemblies pokegold (Gold and Silver, one trainer table) and pokecrystal. Class numbers are the
# order of TrainerGroups (constants/trainer_constants.asm: FALKNER 1 ... CLAIR 8, RIVAL1 9, WILL 11, BRUNO 13, KAREN 14,
# KOGA 15, CHAMPION 16, BROCK 17 ... BLUE 64, GRUNTF 66, and MYSTICALMAN 67 in Crystal only); a party number is the
# 1-based position inside its class (RIVAL1_2_CYNDAQUIL is 5). These are the values the game keeps in
# wOtherTrainerClass and wOtherTrainerID during a battle. Every number was walked back out of the Gold (U) and
# Crystal (U) dumps (tools/nuzlocke/gen2_trainer_rom.py --check compares 1036 parties) and every boss row was
# matched with the trainer or loadtrainer line of the map script that starts the fight.
#
# The Team Rocket executives are all named EXECUTIVE by the games; Archer, Proton, Petrel and Ariana are the
# community's names (Bulbapedia, HeartGold and SoulSilver), matched to the fights by team and place. The two Petrel
# and the two Ariana fights are told apart by where they happen.
#
# game: * = Gold, Silver and Crystal; gs = Gold and Silver; c = Crystal. A row for one game wins over a * row.
# class: the trainer class number. no: the party number, or 0 = every party of the class; a row with a nonzero no wins
#   over the no 0 row of its class. Every class of both games has a no 0 row.
# label: how the fight reads in the ledger. group: Gym (Johto and Kanto leaders), Elite4 (the four and the Champion),
#   Boss (Rocket executives, Red, Eusine), Rival (Silver), Other.
#
# game	class	no	label	group
"""


def render(rows):
    body = "".join("\t".join(str(x) for x in r) + "\n" for r in rows)
    return HEADER + body


def generate():
    rows = build_rows()
    C.write_lf(OUT, render(rows))
    print("wrote %s: %d rows" % (OUT, len(rows)))


def check():
    probs = C.Problems()
    rows = build_rows()
    shipped = [tuple(r) for r in C.data_rows(OUT)]
    probs.check([tuple(str(x) for x in r) for r in rows] == shipped, "the shipped file differs from what the generator builds")
    probs.check(C.clean_crlf_ascii(OUT), "the shipped file is not clean CRLF ASCII (run to_crlf.py)")
    shipped_header = [l for l in C.lines_of(OUT) if l.startswith("#")]
    probs.check(shipped_header == [l for l in HEADER.split("\n") if l.startswith("#")], "header comment differs")

    roms = {g: C.load_rom(g) for g in ("gs", "c")}
    info = {}
    for g in ("gs", "c"):
        root = C.REPO[g]
        cls, ids = C.trainer_numbers(root)
        labels = C.parse_group_labels(root)
        groups = C.parse_parties(root)
        charmap = C.Charmap(root)
        info[g] = (root, cls, ids, labels, groups, charmap, C.script_trainers(root))

    def applies(game, g):
        return game == "*" or game == g

    seen_pairs = {}
    for game, cno, no, label, group in shipped:
        cno, no = int(cno), int(no)
        probs.check(group in GROUPS, "unknown group %r" % group)
        probs.check(label.isascii() and label == label.strip() and len(label) > 0, "bad label %r" % label)
        seen_pairs.setdefault((game, cno, no), []).append(label)
        for g in ("gs", "c"):
            if not applies(game, g):
                continue
            root, cls, ids, labels, groups, charmap, scripts = info[g]
            probs.check(1 <= cno <= len(labels), "%s: class %d does not exist in %s" % (game, cno, g))
            if no == 0 or not 1 <= cno <= len(labels):
                continue
            plist = groups[labels[cno - 1]]
            probs.check(no <= len(plist), "%s: class %d has no party %d in %s" % (game, cno, no, g))
            if no > len(plist):
                continue
            # the ROM holds the same party (name bytes, and a level on the team)
            rom = roms[g]
            rname, ptype, mons, _ = R.party(rom, g, cno, no)
            probs.check(rname == charmap.encode(plist[no - 1]["name"]), "%s: class %d party %d name bytes" % (g, cno, no))
            probs.check(max(m[0] for m in mons) == max(m["level"] for m in plist[no - 1]["mons"]),
                        "%s: class %d party %d level" % (g, cno, no))
            # a map script starts this fight (an unused party is not a boss row)
            const = next(cn for cn, n in cls.items() if n == cno)
            id_const = next(k[1] for k, n in ids.items() if k[0] == const and n == no)
            users = [s for s in scripts if s[0] == const and s[1] == id_const]
            probs.check(bool(users), "%s: no map script starts %s %s (class %d party %d)" % (g, const, id_const, cno, no))
            if const in RIVAL_STAGES:
                stage = (no - 1) // 3
                want = RIVAL_STAGES[const][stage][1]
                probs.check(any(u[2] == want for u in users), "%s: %s stage %d is not started from %s (%s)" % (
                    g, const, stage + 1, want, [u[2] for u in users]))
            for cn, party, lab, f in EXECUTIVES:
                if cn == const and party == no:
                    probs.check(any(u[2] == f for u in users), "%s: %s %d is not started from %s (%s)" % (
                        g, const, no, f, [u[2] for u in users]))
    for k, v in seen_pairs.items():
        probs.check(len(v) == 1, "duplicate row for %r" % (k,))

    # every class of each game resolves to a no 0 row, and the boss classes read as bosses
    for g in ("gs", "c"):
        root, cls, ids, labels, groups, charmap, scripts = info[g]
        for const, cno in cls.items():
            if cno == 0:
                continue
            has0 = (("*", cno, 0) in seen_pairs) or ((g, cno, 0) in seen_pairs) or (g == "gs" and ("gs", cno, 0) in seen_pairs)
            probs.check(has0, "%s: class %s (%d) has no no-0 row" % (g, const, cno))
    # Gold and Silver have no class 67 row
    probs.check(not any(int(r[1]) == 67 and r[0] != "c" for r in shipped), "class 67 must be Crystal only")
    # the boss classes hold exactly the parties the rows name
    for g in ("gs", "c"):
        root, cls, ids, labels, groups, charmap, scripts = info[g]
        for const, want in (("RIVAL1", 15), ("RIVAL2", 6), ("EXECUTIVEM", 4), ("EXECUTIVEF", 2), ("RED", 1), ("BLUE", 1),
                            ("CHAMPION", 1)):
            probs.check(len(groups[labels[cls[const] - 1]]) == want, "%s: %s has %d parties" % (g, const, want))
    # the research doc's other cap points: each fight's highest level, and that my label names the same fight
    doc = C.read_text(RESEARCH)
    doc_caps = {}
    tables = doc[doc.index("### 4.1 Level caps"):doc.index("### 4.2 Rulings")] + doc[doc.index("### 5.1 Level caps"):doc.index("### 5.2 Rulings")]
    for line in tables.split("\n"):
        m = re.match(r"^\|\s*([^|]+?)\s*\|\s*([^|]*?)\s*\|\s*(\d+)\s*\|", line)
        if m:
            doc_caps.setdefault(m.group(1), set()).add(int(m.group(3)))
    by_key = {(r[0], int(r[1]), int(r[2])): r[3] for r in shipped}
    matched = 0
    for doc_label, const, party, words in DOC_ROWS:
        found = [k for k in doc_caps if k.startswith(doc_label)]
        probs.check(len(found) == 1, "research doc has no unique row starting %r" % doc_label)
        if len(found) != 1:
            continue
        for g in ("gs", "c"):
            if const == "MYSTICALMAN" and g == "gs":
                continue
            root, cls, ids, labels, groups, charmap, scripts = info[g]
            cap = max(m["level"] for m in groups[labels[cls[const] - 1]][party - 1]["mons"])
            probs.check({cap} == doc_caps[found[0]], "%s: %s cap %d, research doc says %s" % (g, doc_label, cap, sorted(doc_caps[found[0]])))
            label = by_key.get(("*", cls[const], party)) or by_key.get((g, cls[const], party)) or ""
            probs.check(all(w in label for w in words), "%s: label %r does not name %s" % (g, label, words))
            matched += 1
    print("research doc other cap points compared: %d fights" % matched)
    probs.finish("gen2_trainers --check")


if __name__ == "__main__":
    if sys.argv[1:2] == ["--check"]:
        check()
    else:
        generate()
