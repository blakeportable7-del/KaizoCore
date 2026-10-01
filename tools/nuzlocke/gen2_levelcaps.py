"""Generate (and check) tracker-gba/src/main/resources/nuzlocke/levelcaps-gen2.tsv (2026-09-29).

    python tools/nuzlocke/gen2_levelcaps.py            # write the file (LF; then run to_crlf.py)
    python tools/nuzlocke/gen2_levelcaps.py --check    # rebuild from parties.asm, compare with the shipped file, the
                                                       # ROM dumps, the research doc and the badge constants

Every cap is the highest level on the boss's team in data/trainers/parties.asm of the game's own disassembly
(pokegold for Gold and Silver, pokecrystal for Crystal); the first-run (only) party of each boss is used.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen2_common as C  # noqa: E402
import gen2_trainer_rom as R  # noqa: E402

OUT = os.path.join(C.NZ, "levelcaps-gen2.tsv")
RESEARCH = os.path.join(C.WORKTREE, "docs", "research", "nuzlocke-gen1-3.md")

# (key, kind, label, class constant, group). Order = the order the fights normally come in.
# A Johto gym leader's class number is not its place in the League: WHITNEY is class 2 and BUGSY class 3.
JOHTO = [("gym1", "FALKNER", "Falkner"), ("gym2", "BUGSY", "Bugsy"), ("gym3", "WHITNEY", "Whitney"),
         ("gym4", "MORTY", "Morty"), ("gym5", "CHUCK", "Chuck"), ("gym6", "JASMINE", "Jasmine"),
         ("gym7", "PRYCE", "Pryce"), ("gym8", "CLAIR", "Clair")]
ELITE4 = [("e4-1", "WILL", "Will"), ("e4-2", "KOGA", "Koga"), ("e4-3", "BRUNO", "Bruno"), ("e4-4", "KAREN", "Karen")]
KANTO = [("brock", "BROCK", "Brock"), ("misty", "MISTY", "Misty"), ("surge", "LT_SURGE", "Lt. Surge"),
         ("erika", "ERIKA", "Erika"), ("janine", "JANINE", "Janine"), ("sabrina", "SABRINA", "Sabrina"),
         ("blaine", "BLAINE", "Blaine"), ("blue", "BLUE", "Blue")]
# The badge each Johto and Kanto leader hands over (the map script's setflag ENGINE_*BADGE, proved by --check)
BADGE_OF = {"FALKNER": "ZEPHYRBADGE", "BUGSY": "HIVEBADGE", "WHITNEY": "PLAINBADGE", "MORTY": "FOGBADGE",
            "CHUCK": "STORMBADGE", "JASMINE": "MINERALBADGE", "PRYCE": "GLACIERBADGE", "CLAIR": "RISINGBADGE",
            "BROCK": "BOULDERBADGE", "MISTY": "CASCADEBADGE", "LT_SURGE": "THUNDERBADGE", "ERIKA": "RAINBOWBADGE",
            "JANINE": "SOULBADGE", "SABRINA": "MARSHBADGE", "BLAINE": "VOLCANOBADGE", "BLUE": "EARTHBADGE"}
# Where the badge is handed over, when it is not in the leader's own gym: Clair sends you to the Dragon's Den first.
BADGE_SCRIPT = {"CLAIR": {"gs": "DragonsDenB1F.asm", "c": "DragonShrine.asm"}}
GYM_SCRIPT = {"FALKNER": "VioletGym.asm", "BUGSY": "AzaleaGym.asm", "WHITNEY": "GoldenrodGym.asm",
              "MORTY": "EcruteakGym.asm", "CHUCK": "CianwoodGym.asm", "JASMINE": "OlivineGym.asm",
              "PRYCE": "MahoganyGym.asm", "BROCK": "PewterGym.asm", "MISTY": "CeruleanGym.asm",
              "LT_SURGE": "VermilionGym.asm", "ERIKA": "CeladonGym.asm", "JANINE": "FuchsiaGym.asm",
              "SABRINA": "SaffronGym.asm", "BLAINE": "SeafoamGym.asm", "BLUE": "ViridianGym.asm"}


def johto_in_art_order(bits):
    """GbcTracker.johtoInArtOrder in Python: swap raw bits 4 (Mineral) and 5 (Storm), so badge N is bit N-1 in fight order."""
    return (bits & ~0x30) | ((bits >> 1) & 0x10) | ((bits << 1) & 0x20)


def badge_bits(root):
    """{BADGECONST: (region, raw bit)} from constants/ram_constants.asm (wJohtoBadges then wKantoBadges)."""
    out = {}
    region = None
    n = 0
    for raw in C.lines_of(root + "/constants/ram_constants.asm"):
        s = raw.strip()
        if s.startswith("; wJohtoBadges::"):
            region, n = "johto", 0
        elif s.startswith("; wKantoBadges::"):
            region, n = "kanto", 0
        elif region and s.startswith("const_def"):
            n = 0
        elif region:
            m = re.match(r"^const\s+(\w+BADGE)\b", s)
            if m:
                out[m.group(1)] = (region, n)
                n += 1
            elif s.startswith("DEF NUM_KANTO_BADGES"):
                break
    return out


def tracker_bit(root, leader):
    """The bit of the tracker's badge word (Johto art order 0..7, Kanto 8..15) the leader's badge sets."""
    region, raw = badge_bits(root)[BADGE_OF[leader]]
    if region == "johto":
        return johto_in_art_order(1 << raw).bit_length() - 1
    return 8 + raw


def boss_info(game):
    """{class const: (class no, [party dicts])} for one game."""
    root = C.REPO[game]
    cls, _ids = C.trainer_numbers(root)
    labels = C.parse_group_labels(root)
    groups = C.parse_parties(root)
    return cls, {const: groups[labels[no - 1]] for const, no in cls.items() if 1 <= no <= len(labels)}


def top_species(party, names):
    """(cap, 'A or B'): the highest level on the team and the species at it, in team order."""
    cap = max(m["level"] for m in party["mons"])
    seen = []
    for m in party["mons"]:
        if m["level"] == cap and names[m["species"]] not in seen:
            seen.append(names[m["species"]])
    return cap, " or ".join(seen)


def build_rows():
    names = C.natdex_names()
    rows = []
    for game in ("gs", "c"):
        cls, parties = boss_info(game)
        root = C.REPO[game]
        seq = 0

        def add(key, kind, label, const, group, badge):
            nonlocal seq
            seq += 1
            cap, ace = top_species(parties[const][0], names)
            rows.append((game, seq, key, kind, label, cap, ace, "%d:1" % cls[const],
                         "" if badge is None else badge, group))

        for key, const, label in JOHTO:
            add(key, "gym", label, const, "", tracker_bit(root, const))
        for key, const, label in ELITE4:
            add(key, "e4", label, const, "", None)
        add("champion", "champion", "Lance", "CHAMPION", "", None)
        for key, const, label in KANTO:
            add(key, "post", label, const, "Kanto gyms", tracker_bit(root, const))
        add("red", "post", "Red", "RED", "Mt. Silver", None)
    return rows


HEADER = """# Hardcore Nuzlocke level caps for the vanilla Generation 2 games, Gold, Silver and Crystal (2026-09-29).
#
# A cap is the level of the highest-level Pokemon on the boss's team in the first-run fight. Each of these bosses has
# exactly one party in the trainer table (Generation 2 has no rematch for a gym leader, the Elite Four, Lance, Blue or
# Red; its phone rematches are ordinary trainers). That is the definition Bulbapedia gives for the Hardcore "Level Cap" rule.
#
# Source: docs/research/nuzlocke-gen1-3.md sections 4.1 (Gold and Silver) and 5.1 (Crystal). Every number was
# recomputed from data/trainers/parties.asm of pokegold and pokecrystal and read back out of the Gold (U) and Crystal (U)
# dumps (tools/nuzlocke/gen2_trainer_rom.py); tools/nuzlocke/gen2_levelcaps.py --check redoes all three. The two games
# agree on every boss here. Gold and Silver share one trainer table and are one key, gs. Lt. Surge is 46 (Electabuzz),
# not the 45 that Nuzlocke University and Serebii give.
#
# game: gs = Gold and Silver, c = Crystal. seq: the order the fights normally come in, restarting at 1 for each game.
# key: gym1..gym8 are the Johto gyms in fight order (Falkner, Bugsy, Whitney, Morty, Chuck, Jasmine, Pryce, Clair; Pryce's
# 31 is lower than Jasmine's 35, that is the game's own data), e4-1..e4-4 (Will, Koga, Bruno, Karen), champion (Lance, the
# fight that ends the run),
# then post fights: the eight Kanto gym leaders (any order, group "Kanto gyms") and Red on Mt. Silver (group "Mt. Silver").
# Eusine (Crystal) is not a cap point; he is in trainers-gen2.tsv as a boss.
# ids: class:no, the trainer class number and party number the game keeps in wOtherTrainerClass and wOtherTrainerID
# during the fight (see trainers-gen2.tsv). Each boss has one party, so no is 1.
#
# badge: the bit of the badge word GbcTracker reports (johtoInArtOrder(wJohtoBadges) or (wKantoBadges shl 8)).
#   Johto: the game keeps Zephyr 0, Hive 1, Plain 2, Fog 3, Mineral 4, Storm 5, Glacier 6, Rising 7 in wJohtoBadges
#   (constants/ram_constants.asm). Mineral is Jasmine's and Storm is Chuck's, and Chuck is fought first, so the tracker
#   swaps bits 4 and 5: in the tracker's word badge N (the Nth gym in fight order) is bit N-1. Falkner 0, Bugsy 1,
#   Whitney 2, Morty 3, Chuck 4, Jasmine 5, Pryce 6, Clair 7.
#   Kanto: the game's own order in wKantoBadges is Boulder 0 (Brock), Cascade 1 (Misty), Thunder 2 (Surge), Rainbow 3
#   (Erika), Soul 4 (Janine), Marsh 5 (Sabrina), Volcano 6 (Blaine), Earth 7 (Blue), shifted up by 8: Brock 8 ... Blue 15.
#   Each pairing of leader and badge is the setflag ENGINE_*BADGE in that leader's map script. Clair's Rising Badge is
#   NOT handed over at the gym: she sends you to the Dragon's Den (Dragon Shrine in Crystal) and the badge comes there.
#   Elite Four, Champion and Red earn no badge.
#
# game	seq	key	kind	label	cap	ace	ids	badge	group
"""


def render(rows):
    return HEADER + "".join("\t".join(str(x) for x in r) + "\n" for r in rows)


def generate():
    rows = build_rows()
    C.write_lf(OUT, render(rows))
    print("wrote %s: %d rows" % (OUT, len(rows)))


def research_caps():
    """{label: (cap, ace text)} from the research doc's Johto, Kanto and Red rows (sections 4.1 and 5.1 hold the same numbers)."""
    text = C.read_text(RESEARCH)
    out = {}
    sect = text[text.index("### 4.1 Level caps"):text.index("### 4.2 Rulings")]
    for line in sect.split("\n"):
        m = re.match(r"^\|\s*(?:\d|E4-\d|C|K\d)\s*\|\s*([^|]+?)\s*\|[^|]*\|\s*(\d+)[^|]*\|\s*([^|]+?)\s*\|", line)
        if m:
            label = m.group(1).replace("Champion ", "")
            out[label] = (int(m.group(2)), m.group(3))
    return out


def check():
    probs = C.Problems()
    rows = build_rows()
    shipped = [tuple(r) for r in C.data_rows(OUT)]
    probs.check([tuple(str(x) for x in r) for r in rows] == shipped, "the shipped file differs from what the generator builds")
    probs.check(C.clean_crlf_ascii(OUT), "the shipped file is not clean CRLF ASCII (run to_crlf.py)")
    probs.check([l for l in C.lines_of(OUT) if l.startswith("#")] == [l for l in HEADER.split("\n") if l.startswith("#")],
                "header comment differs")
    probs.check(all(len(r) == 10 for r in shipped), "every row has all ten columns")

    doc = research_caps()
    probs.check(len(doc) >= 21, "the research doc's cap tables were not all found (%d rows)" % len(doc))
    roms = {g: C.load_rom(g) for g in ("gs", "c")}
    disagreements = []
    for game, seq, key, kind, label, cap, ace, ids, badge, group in shipped:
        cap = int(cap)
        cls_no, no = [int(x) for x in ids.split(":")]
        # 1. the disassembly, recomputed from scratch (not through build_rows)
        root = C.REPO[game]
        cls, ids_map = C.trainer_numbers(root)
        labels = C.parse_group_labels(root)
        groups = C.parse_parties(root)
        const = next(k for k, v in cls.items() if v == cls_no)
        party = groups[labels[cls_no - 1]][no - 1]
        probs.check(cap == max(m["level"] for m in party["mons"]), "%s %s: cap %d vs parties.asm" % (game, key, cap))
        # 2. the ROM dump
        probs.check(R.max_level(roms[game], game, cls_no, no) == cap, "%s %s: cap %d vs the ROM dump" % (game, key, cap))
        # 3. a map script starts the fight (a gym leader's walk-up trainer line, a loadtrainer for the rest)
        users = [s for s in C.script_trainers(root) if s[0] == const]
        probs.check(bool(users), "%s %s: no map script starts class %s" % (game, key, const))
        # 4. the research doc
        d = doc.get(label) or doc.get("Lt. Surge" if label == "Lt. Surge" else label)
        if d is None:
            probs.check(False, "%s %s: not in the research doc's tables" % (game, key))
        elif d[0] != cap:
            disagreements.append("%s %s: cap %d, research doc %d" % (game, key, cap, d[0]))
        # 5. the badge bit
        if kind == "gym" or key in [k for k, _, _ in KANTO]:
            leader = const
            if leader not in BADGE_OF:
                probs.check(False, "%s %s: class %s (%d) is not a gym leader class" % (game, key, const, cls_no))
                continue
            bit = tracker_bit(root, leader)
            probs.check(str(bit) == badge, "%s %s: badge bit %s, expected %d" % (game, key, badge, bit))
            if kind == "gym":
                probs.check(bit == int(key[3:]) - 1, "%s %s: Johto badge N must be bit N-1 in fight order" % (game, key))
            else:
                probs.check(8 <= bit <= 15, "%s %s: Kanto bit out of 8..15" % (game, key))
            # the badge is set by a script of that leader's map
            script = BADGE_SCRIPT.get(leader, {}).get(game) or GYM_SCRIPT[leader]
            text = C.read_text("%s/maps/%s" % (root, script))
            probs.check(("setflag ENGINE_" + BADGE_OF[leader]) in text, "%s %s: %s does not set %s" % (game, key, script, BADGE_OF[leader]))
            if leader in GYM_SCRIPT:
                gym_text = C.read_text("%s/maps/%s" % (root, GYM_SCRIPT[leader]))
                probs.check(re.search(r"\btrainer\s+%s\b|\bloadtrainer\s+%s\b" % (leader, leader), gym_text) is not None,
                            "%s %s: %s does not start the %s fight" % (game, key, GYM_SCRIPT[leader], leader))
        else:
            probs.check(badge == "", "%s %s: no badge expected" % (game, key))
    # Gold and Silver and Crystal agree, boss by boss
    by = {}
    for game, seq, key, kind, label, cap, ace, ids, badge, group in shipped:
        by.setdefault(key, {})[game] = (cap, ace, ids, badge, group, kind, label)
    for key, v in by.items():
        probs.check(len(v) == 2 and v["gs"] == v["c"], "%s differs between gs and c: %s" % (key, v))
    # the badge word: every bit 0..15 is used exactly once
    bits = sorted(int(r[8]) for r in shipped if r[0] == "gs" and r[8] != "")
    probs.check(bits == list(range(16)), "badge bits are not exactly 0..15: %s" % bits)
    seqs = {g: [int(r[1]) for r in shipped if r[0] == g] for g in ("gs", "c")}
    probs.check(all(s == list(range(1, len(s) + 1)) for s in seqs.values()), "seq is not 1..N per game")
    for d in disagreements:
        print("RESEARCH DOC DISAGREES: " + d)
    probs.check(not disagreements, "research doc disagreements: %d" % len(disagreements))
    probs.finish("gen2_levelcaps --check")


if __name__ == "__main__":
    if sys.argv[1:2] == ["--check"]:
        check()
    else:
        generate()
