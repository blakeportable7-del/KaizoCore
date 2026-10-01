"""Writes tracker-gba/src/main/resources/nuzlocke/levelcaps-gen5.tsv (Generation 5: Black, White, Black 2, White 2).

    python tools/nuzlocke/gen5_levelcaps.py           write the file (LF endings; run to_crlf.py after)
    python tools/nuzlocke/gen5_levelcaps.py --check   re-read every source and compare with the shipped file

What is in the file, per game key:
  bw    Black and White: gyms 1 to 8, the Elite Four (any order), N at N's Castle (post), Ghetsis (champion), and the
        rival and N fights as extra cap points.
  b2w2  Black 2 and White 2, NORMAL mode: gyms 1 to 8 in fight order, the Elite Four (any order), Iris (champion),
        and Hugh, Colress, Rood, Zinzolin and Ghetsis as extra cap points.

Sources, all re-read by --check:
  * the trainer data (archives TrainerData and TrainerPokemon) of clean ROMs: Black 2 and White 2 for b2w2 (they must
    agree with each other), a White (US) dump for bw when one is on the machine (Black has no dump anywhere);
  * the names of those trainers from the game's own text archive (a person's id is only trusted when the ROM calls it
    by the same name);
  * tracker-nds nds/trainer-groups.tsv (ids and the badge number each gym leader gives) and NdsGameMap.kt
    (labTrainerIds, finalTrainerId);
  * the Universal Pokemon Randomizer ZX (engine-zx Gen5Constants trainer tags and gen5_offsets.ini
    EliteFourIndices), which tag the same ids independently;
  * docs/research/nuzlocke-gen4-5.md sections 4.1 and 5.1 (caps, all rows);
  * the games' own badge award messages (order of the badges in the badge byte).
When a ROM is not on the machine the numbers written come from the table below (which was filled from those ROMs) and
--check says so and exits 2.
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen5_common as C
import gen5_rom as R

OUT = C.OUT_DIR / "levelcaps-gen5.tsv"
E4G = "Elite Four, any order"
NG = "N's Castle"

# key, kind, label, ids (normal), alt ids (Challenge Mode records), badge bit, group, names the ROM must give,
# cap, ace (the numbers the ROMs gave on 2026-09-29; the ROMs are consulted first), UPR tag the ids must carry.
# Rows are in the order the fights normally come in. seq is the row number within the game.
RIVAL = "rival"
BOSS = "boss"


def row(key, kind, label, ids, cap, ace, names, upr, badge=None, group="", alt=()):
    return dict(key=key, kind=kind, label=label, ids=list(ids), alt=list(alt), badge=badge, group=group,
                names=tuple(names), cap=cap, ace=ace, upr=upr)


BW_ROWS = [
    row("cheren1", RIVAL, "Cheren", [53, 54, 55], 5, "Tepig, Oshawott or Snivy", ["Cheren"], r"RIVAL1-\d"),
    row("bianca1", RIVAL, "Bianca", [59, 60, 61], 5, "Oshawott, Snivy or Tepig", ["Bianca"], r"FRIEND1-\d"),
    row("n1", RIVAL, "N", [64], 7, "Purrloin", ["N"], r"NOTSTRONG"),
    row("bianca2", RIVAL, "Bianca", [498, 499, 500], 7, "Oshawott, Snivy or Tepig", ["Bianca"], r"FRIEND2-\d"),
    row("cheren2", RIVAL, "Cheren", [287, 288, 289], 8, "Tepig, Oshawott or Snivy", ["Cheren"], r"RIVAL2-\d"),
    row("gym1", "gym", "Cilan, Chili or Cress", [12, 11, 13], 14, "Pansage, Pansear or Panpour", ["Cilan", "Chili", "Cress"], r"GYM\d+-LEADER", badge=0),
    row("cheren3", RIVAL, "Cheren", [56, 57, 58], 14, "Tepig, Oshawott or Snivy", ["Cheren"], r"RIVAL3-\d"),
    row("n2", RIVAL, "N", [65], 13, "Pidove", ["N"], r"STRONG"),
    row("gym2", "gym", "Lenora", [21], 20, "Watchog", ["Lenora"], r"GYM\d+-LEADER", badge=1),
    row("gym3", "gym", "Burgh", [22], 23, "Leavanny", ["Burgh"], r"GYM\d+-LEADER", badge=2),
    row("bianca3", RIVAL, "Bianca", [507, 508, 509], 20, "Dewott, Servine or Pignite", ["Bianca"], r"FRIEND3-\d"),
    row("cheren4", RIVAL, "Cheren", [403, 404, 405], 22, "Pignite, Dewott or Servine", ["Cheren"], r"RIVAL4-\d"),
    row("n3", RIVAL, "N", [89], 22, "Sandile", ["N"], r"STRONG"),
    row("gym4", "gym", "Elesa", [23], 27, "Zebstrika", ["Elesa"], r"GYM\d+-LEADER", badge=3),
    row("cheren5", RIVAL, "Cheren", [90, 91, 92], 26, "Pignite, Dewott or Servine", ["Cheren"], r"RIVAL5-\d"),
    row("gym5", "gym", "Clay", [24], 31, "Excadrill", ["Clay"], r"GYM\d+-LEADER", badge=4),
    row("bianca4", RIVAL, "Bianca", [491, 492, 493], 28, "Dewott, Servine or Pignite", ["Bianca"], r"FRIEND4-\d"),
    row("n4", RIVAL, "N", [218], 28, "Boldore", ["N"], r"STRONG"),
    row("gym6", "gym", "Skyla", [25], 35, "Swanna", ["Skyla"], r"GYM\d+-LEADER", badge=5),
    row("cheren6", RIVAL, "Cheren", [539, 540, 541], 35, "Pignite, Dewott or Servine", ["Cheren"], r"RIVAL6-\d"),
    row("gym7", "gym", "Brycen", [131], 39, "Beartic", ["Brycen"], r"GYM\d+-LEADER", badge=6),
    row("bianca5", RIVAL, "Bianca", [494, 495, 496], 40, "Samurott, Serperior or Emboar", ["Bianca"], r"FRIEND5-\d"),
    row("gym8", "gym", "Drayden or Iris", [133, 132], 43, "Haxorus", ["Drayden", "Iris"], r"GYM\d+-LEADER", badge=7),
    row("cheren7", RIVAL, "Cheren", [588, 589, 590], 45, "Emboar, Samurott or Serperior", ["Cheren"], r"RIVAL7-\d"),
    row("e4-1", "e4", "Shauntal", [228], 50, "Chandelure", ["Shauntal"], r"ELITE\d", group=E4G),
    row("e4-2", "e4", "Marshal", [229], 50, "Mienshao", ["Marshal"], r"ELITE\d", group=E4G),
    row("e4-3", "e4", "Grimsley", [230], 50, "Bisharp", ["Grimsley"], r"ELITE\d", group=E4G),
    row("e4-4", "e4", "Caitlin", [231], 50, "Gothitelle", ["Caitlin"], r"ELITE\d", group=E4G),
    row("n", "post", "N", [587, 586], 52, "Zekrom or Reshiram", ["N"], r"UBER", group=NG),
    row("champion", "champion", "Ghetsis", [232], 54, "Hydreigon", ["Ghetsis"], r"UBER"),
]

B2W2_ROWS = [
    row("hugh1", RIVAL, "Hugh", [161, 162, 163], 5, "Tepig, Oshawott or Snivy", ["Rival"], r"RIVAL1-\d"),
    row("hugh2", RIVAL, "Hugh", [166, 167, 168], 8, "Tepig, Oshawott or Snivy", ["Rival"], r"RIVAL2-\d"),
    row("gym1", "gym", "Cheren", [156], 13, "Lillipup", ["Cheren"], r"GYM\d+-LEADER", badge=0, alt=[764]),
    row("gym2", "gym", "Roxie", [157], 18, "Whirlipede", ["Roxie"], r"GYM\d+-LEADER", badge=1, alt=[765]),
    row("gym3", "gym", "Burgh", [154], 24, "Leavanny", ["Burgh"], r"GYM\d+-LEADER", badge=2, alt=[766]),
    row("colress1", BOSS, "Colress", [358], 23, "Klink", ["Colress"], r"THEMED:COLRESS-STRONG"),
    row("gym4", "gym", "Elesa", [153], 30, "Zebstrika", ["Elesa"], r"GYM\d+-LEADER", badge=3, alt=[767]),
    row("rood", BOSS, "Rood", [346], 27, "Herdier", ["Rood"], None),
    row("gym5", "gym", "Clay", [158], 33, "Excadrill", ["Clay"], r"GYM\d+-LEADER", badge=4, alt=[768]),
    row("gym6", "gym", "Skyla", [155], 39, "Swanna", ["Skyla"], r"GYM\d+-LEADER", badge=5, alt=[769]),
    row("hugh4", RIVAL, "Hugh", [378, 379, 380], 41, "Emboar, Samurott or Serperior", ["Rival"], r"RIVAL5-\d"),
    row("gym7", "gym", "Drayden", [159], 48, "Haxorus", ["Drayden"], r"GYM\d+-LEADER", badge=6, alt=[770]),
    row("zinzolin2", BOSS, "Zinzolin", [584], 48, "Weavile", ["Zinzolin"], r"THEMED:ZINZOLIN-STRONG"),
    row("gym8", "gym", "Marlon", [160], 51, "Jellicent", ["Marlon"], r"GYM\d+-LEADER", badge=7, alt=[771]),
    row("colress3", BOSS, "Colress", [344], 52, "Klinklang", ["Colress"], r"THEMED:COLRESS-STRONG"),
    row("ghetsis", BOSS, "Ghetsis", [345], 52, "Hydreigon", ["Ghetsis"], r"UBER"),
    row("hugh5", RIVAL, "Hugh", [684, 685, 686], 57, "Emboar, Samurott or Serperior", ["Rival"], r"RIVAL8-\d"),
    row("e4-1", "e4", "Shauntal", [38], 58, "Chandelure", ["Shauntal"], r"ELITE\d", group=E4G, alt=[772]),
    row("e4-2", "e4", "Marshal", [39], 58, "Conkeldurr", ["Marshal"], r"ELITE\d", group=E4G, alt=[774]),
    row("e4-3", "e4", "Grimsley", [40], 58, "Bisharp", ["Grimsley"], r"ELITE\d", group=E4G, alt=[773]),
    row("e4-4", "e4", "Caitlin", [41], 58, "Gothitelle", ["Caitlin"], r"ELITE\d", group=E4G, alt=[775]),
    row("champion", "champion", "Iris", [341], 59, "Haxorus", ["Iris"], r"CHAMPION", alt=[776]),
]

GAMES = [("bw", BW_ROWS), ("b2w2", B2W2_ROWS)]

# The game codes of trainer-groups.tsv that carry each game key's trainer tables.
TG_CODES = {"bw": {"black": "4F425249", "white": "4F415249"}, "b2w2": {"black2": "4F455249"}}

# The badge order of each game: (Bulbapedia's badge pages and the games' own award messages) badge N is bit N-1.
BADGES = {
    "bw": ["Trio", "Basic", "Insect", "Bolt", "Quake", "Jet", "Freeze", "Legend"],
    "b2w2": ["Basic", "Toxic", "Insect", "Bolt", "Quake", "Jet", "Legend", "Wave"],
}
GYM_TOWNS = {
    "bw": ["Striaton", "Nacrene", "Castelia", "Nimbasa", "Driftveil", "Mistralton", "Icirrus", "Opelucid"],
    "b2w2": ["Aspertia", "Virbank", "Castelia", "Nimbasa", "Driftveil", "Mistralton", "Opelucid", "Humilau"],
}


# ------------------------------------------------------------------ ROM side

class Sources:
    """The ROMs that are on the machine, each with its trainers, names and species read once."""

    def __init__(self):
        self.missing = []
        self.species = C.species_names()
        self.roms = {}
        self.trainers = {}
        self.names = {}
        for key, family in (("black2", "b2w2"), ("white2", "b2w2"), ("white", "bw")):
            rom = R.open_rom(key)
            if rom is None:
                if key != "white2" and key != "black2":
                    self.missing.append("White (US) ROM for the Black and White numbers (%s)" % ", ".join(str(p) for p in R.ROMS["white"]))
                else:
                    self.missing.append("%s ROM (%s)" % (key, R.ROMS[key][0]))
                continue
            self.roms[key] = rom
            self.trainers[key] = R.read_trainers(rom, family)
            self.names[key] = R.read_text(rom, family, "TrainerNames")

    def rom_for(self, game):
        return "white" if game == "bw" else "black2"

    def close(self):
        for rom in self.roms.values():
            rom.close()


def species_label(sources, species):
    return C.pretty_species(sources.species.get(species, "?%d" % species))


def team_ace(sources, trainer):
    """The species of the first Pokemon at the team's highest level."""
    top = trainer.top_level
    for m in trainer.mons:
        if m.level == top:
            return species_label(sources, m.species)
    return "?"


def join_or(names):
    seen = []
    for n in names:
        if n not in seen:
            seen.append(n)
    if len(seen) <= 1:
        return "".join(seen)
    return ", ".join(seen[:-1]) + " or " + seen[-1]


def rom_values(sources, game, r, problems):
    """(cap, ace) of a row read from its game's ROM, or None when that ROM is not here. Also checks names."""
    key = sources.rom_for(game)
    trainers = sources.trainers.get(key)
    if trainers is None:
        return None
    names = sources.names[key]
    caps = []
    aces = []
    for tid in r["ids"]:
        t = trainers.get(tid)
        if t is None:
            problems.append("%s %s: trainer %d is not in the %s trainer data" % (game, r["key"], tid, key))
            return None
        if names[tid] not in r["names"]:
            problems.append("%s %s: trainer %d is called %r in the ROM, expected one of %s" % (game, r["key"], tid, names[tid], list(r["names"])))
        caps.append(t.top_level)
        aces.append(team_ace(sources, t))
    for tid in r["alt"]:
        t = trainers.get(tid)
        if t is None:
            problems.append("%s %s: Challenge Mode trainer %d is not in the %s trainer data" % (game, r["key"], tid, key))
            continue
        if names[tid] not in r["names"]:
            problems.append("%s %s: Challenge Mode trainer %d is called %r in the ROM, expected %s" % (game, r["key"], tid, names[tid], list(r["names"])))
        if t.top_level != max(caps):
            problems.append("%s %s: Challenge Mode record %d tops out at %d, the Normal record at %d" % (game, r["key"], tid, t.top_level, max(caps)))
    if len(set(caps)) != 1:
        problems.append("%s %s: the starter versions disagree on the cap %s" % (game, r["key"], caps))
    return max(caps), join_or(aces)


def compare_versions(sources, game, r, problems):
    """Black 2 and White 2 must carry identical teams for every id of the row."""
    if game != "b2w2" or "white2" not in sources.trainers or "black2" not in sources.trainers:
        return
    for tid in r["ids"] + r["alt"]:
        a = sources.trainers["black2"][tid]
        b = sources.trainers["white2"][tid]
        ta = [(m.species, m.level, m.form) for m in a.mons]
        tb = [(m.species, m.level, m.form) for m in b.mons]
        if ta != tb:
            problems.append("b2w2 %s: trainer %d differs between Black 2 and White 2" % (r["key"], tid))


def build_rows(sources, problems):
    """All rows as final dicts (cap and ace from the ROM when it is on hand, else from the table)."""
    out = []
    for game, rows in GAMES:
        for seq, r in enumerate(rows, 1):
            got = rom_values(sources, game, r, problems)
            compare_versions(sources, game, r, problems)
            cap, ace = r["cap"], r["ace"]
            if got is not None:
                if got != (cap, ace):
                    problems.append("%s %s: the ROM says cap %d, ace %r but the table in gen5_levelcaps.py says %d, %r" % (game, r["key"], got[0], got[1], cap, ace))
                cap, ace = got
            final = dict(r)
            final.update(game=game, seq=seq, cap=cap, ace=ace)
            out.append(final)
    return out


# ------------------------------------------------------------------ the file

def header(rows):
    n_bw = sum(1 for r in rows if r["game"] == "bw")
    n_b2 = sum(1 for r in rows if r["game"] == "b2w2")
    lines = [
        "# Hardcore Nuzlocke level caps for the vanilla Generation 5 games (2026-09-29).",
        "#",
        "# A cap is the level of the highest-level Pokemon on the boss's team in the first-run fight (Bulbapedia's",
        "# definition of the Hardcore \"Level Cap\" rule). Rematches are left out.",
        "#",
        "# Games: bw = Black and White (%d rows), b2w2 = Black 2 and White 2 in NORMAL mode (%d rows)." % (n_bw, n_b2),
        "#",
        "# Sources. docs/research/nuzlocke-gen4-5.md sections 4.1 and 5.1 (Bulbapedia and Serebii agree on every number),",
        "# recomputed from the games' own trainer data: for b2w2 from clean Black 2 and White 2 (US) ROMs (the two games hold",
        "# the same trainer table and the same levels), for bw from a clean White (US) ROM (Black has no dump on this machine;",
        "# Bulbapedia and Serebii give Black the same numbers apart from the two records named below). Every id was matched",
        "# to its trainer's name in the ROM's own text, and to the ids of nds/trainer-groups.tsv and of the Universal Pokemon",
        "# Randomizer ZX's trainer tags (Gen5Constants tagTrainersBW and tagTrainersBW2, gen5_offsets.ini EliteFourIndices).",
        "# gen5_levelcaps.py --check re-reads all of them.",
        "#",
        "# ids: the trainer's index in the game's trainer archive, which is the number the DS tracker reads as",
        "# enemyTrainerId (the Black 2 RAM dump reads 163 in the first Hugh fight, the ROM's record of that fight; the same kind",
        "# of number as NdsGameMap labTrainerIds and finalTrainerId, which agree with these rows: bw lab 64 = n1 and final",
        "# 232 = Ghetsis, b2w2 lab 161 to 163 = hugh1 and final 341 = Iris). A row with several ids is one fight with",
        "# several versions: one per starter (the rivals), or one per game or leader (see below).",
        "#",
        "# Black versus White. The two games use the same trainer records and differ only in which of two the story fights",
        "# (the White ROM holds both of each pair; Black has no dump, and the randomizer's tags and EliteFourIndices say the",
        "# same of Black):",
        "#   gym8: Drayden is 133 (Black), Iris is 132 (White), both with the same team of top level 43.",
        "#   n (N's Castle): 587 is the Zekrom team (Black), 586 the Reshiram team (White). Same levels.",
        "#   gym1: Chili 11, Cilan 12, Cress 13, chosen by your starter; all three top out at 14.",
        "# Both ids are listed on those rows. trainer-groups.tsv lists 587 for White's N's Castle in its N group but 586 in",
        "# its Elite 4 group; 586 is right (it is the Reshiram team in the White ROM, and the randomizer's EliteFourIndices",
        "# for White ends in 586).",
        "#",
        "# Black 2 and White 2 difficulty. Only Normal Mode is in this file: Easy Mode and Challenge Mode cannot be read from",
        "# memory. Challenge Mode fights the gym leaders, the Elite Four and Iris with their own trainer records (764 to 771",
        "# and 772 to 776); their levels in the ROM equal Normal's and the game adds 1 to 5 levels as the battle loads",
        "# (Bulbapedia: Challenge caps 14 19 26 32 36 42 52 55, Elite Four 62, Iris 63; Easy Mode is 1 to 4 lower). Those",
        "# ids are on the same rows so the fight is still recognized as that boss; the tracker should take the level of the",
        "# team in the battle, as it does for every cap when it can. Note NdsGameMap.finalTrainerId (341) is the Normal Mode",
        "# Iris only; the Challenge Mode Iris is 776.",
        "#",
        "# Badges. badge is the bit of the badge byte the DS tracker reads (NdsTracker.readBadges: one byte, badge N in bit",
        "# N-1). Bit order, from the games' own badge award messages (the game lists them in badge index order) and from the",
        "# reference tracker (its BW_badge1..8 and BW2_badge1..8 icons are drawn in bit order; trainer-groups.tsv gives each",
        "# gym leader the badge number they award):",
        "#   bw:   0 Trio (Striaton), 1 Basic (Nacrene), 2 Insect (Castelia), 3 Bolt (Nimbasa), 4 Quake (Driftveil),",
        "#         5 Jet (Mistralton), 6 Freeze (Icirrus), 7 Legend (Opelucid).",
        "#   b2w2: 0 Basic (Aspertia), 1 Toxic (Virbank), 2 Insect (Castelia), 3 Bolt (Nimbasa), 4 Quake (Driftveil),",
        "#         5 Jet (Mistralton), 6 Legend (Opelucid), 7 Wave (Humilau).",
        "# In both games the Nth gym in the fight order is bit N-1. rival and boss rows carry no badge.",
        "#",
        "# Extras (kind rival and boss) are cap points only when the rules ask for them; they are in the order the fights",
        "# come in (research doc story positions), and seq counts every row, so a gym's number is in its key (gym3), not in",
        "# seq. Left out: the partner fights where Cheren or Hugh fights beside you (Wellspring Cave, Route 5; Castelia",
        "# Sewers, Plasma Frigate 368 to 370, Lacunosa Town; trainer-groups.tsv lists the Plasma Frigate one as Hugh's third),",
        "# the Shadow Triad fights of Black 2 and White 2 (48 and 51, both under the next cap), N's post-game fights, and",
        "# every rematch. Story positions of two rows are uncertain: N's second fight (n2, Nacrene City) is at the Gym 2",
        "# door in Bulbapedia's walkthrough and after Lenora in Serebii's (13 either way); Hugh's Undella Town fight (hugh4)",
        "# is placed after Gym 6 as in the walkthrough. Both keep their levels, only the order could move.",
        "#",
        "# Regenerate: python tools/nuzlocke/gen5_levelcaps.py    Check: python tools/nuzlocke/gen5_levelcaps.py --check",
        "#",
        "# game\tseq\tkey\tkind\tlabel\tcap\tace\tids\tbadge\tgroup",
    ]
    return lines


def render_rows(rows):
    body = []
    for r in rows:
        ids = ",".join(str(i) for i in r["ids"] + r["alt"])
        badge = "" if r["badge"] is None else str(r["badge"])
        body.append("\t".join([r["game"], str(r["seq"]), r["key"], r["kind"], r["label"], str(r["cap"]), r["ace"], ids, badge, r["group"]]))
    return C.render(header(rows) + body)


# ------------------------------------------------------------------ cross-checks

def read_trainer_groups():
    """{game code: {id: (group name, battle, badge number or None, name or place)}} from nds/trainer-groups.tsv."""
    out = {}
    for p in C.read_tsv_rows(C.NDS_RES / "nds" / "trainer-groups.tsv"):
        if len(p) < 8:
            continue
        code = p[0]
        badge = int(p[8]) if len(p) > 8 and p[8].strip().isdigit() else None
        for tid in p[7].split(","):
            tid = tid.strip()
            if tid.isdigit():
                out.setdefault(code, {})[int(tid)] = (p[2], p[4], badge, p[6])
    return out


def read_upr_tags():
    """{'bw' or 'b2w2': {trainer index: tag}} parsed from Gen5Constants.tagTrainersBW and tagTrainersBW2."""
    path = C.ROOT / "engine-zx" / "src" / "com" / "dabomstew" / "pkrandomzx" / "constants" / "Gen5Constants.java"
    text = path.read_text(encoding="utf-8")
    out = {}
    for game, fn in (("bw", "tagTrainersBW"), ("b2w2", "tagTrainersBW2")):
        m = re.search(r"public static void %s\(List<Trainer> trs\) \{(.*?)\n    \}\n" % fn, text, re.S)
        body = m.group(1)
        tags = {}
        for line in body.splitlines():
            line = line.split("//")[0]
            a = re.search(r'\btag\(trs,\s*"([^"]+)"\s*,\s*([^)]*)\)', line)
            b = re.search(r'\btag\(trs,\s*(0x[0-9a-fA-F]+|\d+)\s*,\s*"([^"]+)"\s*\)', line)
            c = re.search(r'tagRivalBW\(trs,\s*"([^"]+)"\s*,\s*(0x[0-9a-fA-F]+|\d+)\)', line)
            if a:
                for n in a.group(2).split(","):
                    n = n.strip()
                    if n:
                        tags[int(n, 0)] = a.group(1)
            elif b:
                tags[int(b.group(1), 0)] = b.group(2)
            elif c:
                base = int(c.group(2), 0)
                for k in range(3):
                    tags[base + k] = "%s-%d" % (c.group(1), k)
        out[game] = tags
    return out


def read_upr_ini():
    """EliteFourIndices per game section of gen5_offsets.ini, and the Challenge Mode list of Black 2."""
    path = C.ROOT / "engine-zx" / "src" / "com" / "dabomstew" / "pkrandomzx" / "config" / "gen5_offsets.ini"
    sections = {}
    current = None
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\[(.*)\]\s*$", line)
        if m:
            current = m.group(1)
            sections[current] = {}
            continue
        if current and "=" in line:
            k, v = line.split("=", 1)
            sections[current][k.strip()] = v.split("//")[0].strip()
    def ints(text):
        return [int(x) for x in re.findall(r"\d+", text)]
    return {
        "black": ints(sections["Black (U)"]["EliteFourIndices"]),
        "white": ints(sections["White (U)"]["EliteFourIndices"]),
        "black2": ints(sections["Black 2 (U)"]["EliteFourIndices"]),
        "black2_challenge": ints(sections["Black 2 (U)"]["ChallengeModeEliteFourIndices"]),
    }


def read_game_map():
    """{'Pokemon Black': (lab ids, final id), ...} from NdsGameMap.kt."""
    text = (C.ROOT / "tracker-nds" / "src" / "main" / "kotlin" / "com" / "ironmonone" / "tracker" / "nds" / "NdsGameMap.kt").read_text(encoding="utf-8")
    out = {}
    for m in re.finditer(r'name = "(Pokemon [^"]+)",\s*labTrainerIds = setOf\(([^)]*)\), finalTrainerId = (\d+)', text):
        out[m.group(1)] = ([int(x) for x in re.findall(r"\d+", m.group(2))], int(m.group(3)))
    return out


def read_doc_caps():
    """{'bw': {key: cap}, 'b2w2': {key: cap}} from the tables of sections 4.1 and 5.1 of the research doc."""
    text = (C.DOCS / "nuzlocke-gen4-5.md").read_text(encoding="utf-8")
    ordinal = re.compile(r"(\d)(?:st|nd|rd|th)")
    caps = {"bw": {}, "b2w2": {}}
    sections = {"bw": ("### 4.1 Level caps", "### 4.2"), "b2w2": ("### 5.1 Level caps", "### 5.2")}
    for game, (start, end) in sections.items():
        chunk = text[text.index(start):text.index(end)]
        for line in chunk.splitlines():
            if not line.startswith("|") or "**" not in line:
                continue
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) < 4:
                continue
            first = re.search(r"\*\*(\d+)\*\*", line)
            battle = cells[1]
            cap = int(first.group(1))
            key = None
            n = ordinal.search(battle)
            num = n.group(1) if n else None
            if game == "bw":
                if battle.startswith("Cilan"):
                    key = "gym1"
                elif battle in ("Lenora", "Burgh", "Elesa", "Clay", "Skyla", "Brycen"):
                    key = "gym%d" % (["Cheren/Cilan", "Lenora", "Burgh", "Elesa", "Clay", "Skyla", "Brycen"].index(battle) + 1)
                elif battle.startswith("Drayden"):
                    key = "gym8"
                elif battle in ("Shauntal", "Marshal", "Grimsley", "Caitlin"):
                    key = "e4-%d" % (["Shauntal", "Marshal", "Grimsley", "Caitlin"].index(battle) + 1)
                elif battle.startswith("N (Black)") or battle.startswith("N (White)"):
                    key = "n"
                elif battle == "Ghetsis":
                    key = "champion"
                elif battle.startswith("Rival Cheren"):
                    key = "cheren" + num
                elif battle.startswith("Bianca,"):
                    key = "bianca" + num
                elif battle.startswith("N,"):
                    key = "n" + num
            else:
                # the Normal column is the bold one
                if battle in ("Cheren", "Roxie", "Burgh", "Elesa", "Clay", "Skyla", "Drayden", "Marlon"):
                    key = "gym%d" % (["Cheren", "Roxie", "Burgh", "Elesa", "Clay", "Skyla", "Drayden", "Marlon"].index(battle) + 1)
                elif battle in ("Shauntal", "Marshal", "Grimsley", "Caitlin"):
                    key = "e4-%d" % (["Shauntal", "Marshal", "Grimsley", "Caitlin"].index(battle) + 1)
                elif battle == "Iris":
                    key = "champion"
                elif battle.startswith("Rival Hugh"):
                    key = "hugh" + num
                elif battle.startswith("Colress"):
                    key = "colress" + num
                elif battle.startswith("Sage Rood"):
                    key = "rood"
                elif battle.startswith("Sage Zinzolin"):
                    key = "zinzolin" + num
                elif battle == "Ghetsis":
                    key = "ghetsis"
            if key is not None:
                if key in caps[game] and caps[game][key] != cap:
                    caps[game][key] = (caps[game][key], cap)  # two versions with different numbers (none expected)
                else:
                    caps[game][key] = cap
    return caps


def cross_checks(rows, sources, problems, notes):
    groups = read_trainer_groups()
    upr = read_upr_tags()
    ini = read_upr_ini()
    gmap = read_game_map()
    doc = read_doc_caps()

    # 1. the research doc, every row that has a cap in it
    matched = 0
    differ = 0
    for r in rows:
        want = doc[r["game"]].get(r["key"])
        if want is None:
            notes.append("not in the research doc's tables: %s %s" % (r["game"], r["key"]))
            continue
        matched += 1
        if want != r["cap"]:
            differ += 1
            problems.append("%s %s: cap %s but the research doc has %s" % (r["game"], r["key"], r["cap"], want))
    notes.append("research doc: %d of %d rows have a cap there, %d differ" % (matched, len(rows), differ))

    # 2. trainer-groups.tsv: ids present for the game, and the badge numbers of the gym leaders
    in_tg = 0
    only_rom = []
    for r in rows:
        codes = TG_CODES[r["game"]]
        for tid in r["ids"]:
            found = [name for name, code in codes.items() if tid in groups.get(code, {})]
            if found:
                in_tg += 1
            else:
                only_rom.append((r["game"], r["key"], tid))
            if r["badge"] is not None:
                for name in found:
                    badge = groups[codes[name]][tid][2]
                    if badge != r["badge"] + 1:
                        problems.append("%s %s: trainer-groups.tsv gives id %d badge %s (%s), expected %d" % (r["game"], r["key"], tid, badge, name, r["badge"] + 1))
    notes.append("trainer-groups.tsv lists %d of the normal ids; not listed there (ROM and randomizer tags only): %s" % (in_tg, ["%s:%s:%d" % x for x in only_rom]))

    # 3. randomizer trainer tags
    upr_ok = 0
    for r in rows:
        if r["upr"] is None:
            continue
        tags = upr[r["game"]]
        for tid in r["ids"] + r["alt"]:
            tag = tags.get(tid)
            if tag is None or not re.fullmatch(r["upr"], tag):
                problems.append("%s %s: randomizer tag of trainer %d is %r, expected %s" % (r["game"], r["key"], tid, tag, r["upr"]))
            else:
                upr_ok += 1
    notes.append("randomizer trainer tags agree for %d ids" % upr_ok)

    # 3b. how many independent sources stand behind each id
    upr_lists = {"bw": set(ini["black"]) | set(ini["white"]), "b2w2": set(ini["black2"]) | set(ini["black2_challenge"])}
    kt_ids = {}
    for name, game in (("Pokemon Black", "bw"), ("Pokemon Black 2", "b2w2")):
        lab, final = gmap[name]
        kt_ids[game] = set(lab) | {final}
    counts = {}
    single = []
    for r in rows:
        key = sources.rom_for(r["game"])
        for tid in r["ids"] + r["alt"]:
            found = set()
            if key in sources.names and sources.names[key][tid] in r["names"]:
                found.add("ROM")
            if any(tid in groups.get(code, {}) for code in TG_CODES[r["game"]].values()):
                found.add("trainer-groups")
            if r["upr"] is not None and re.fullmatch(r["upr"], upr[r["game"]].get(tid, "")):
                found.add("randomizer tags")
            if tid in upr_lists[r["game"]]:
                found.add("randomizer ini")
            if tid in kt_ids[r["game"]]:
                found.add("NdsGameMap")
            counts[len(found)] = counts.get(len(found), 0) + 1
            if len(found) < 2:
                single.append("%s:%s:%d %s" % (r["game"], r["key"], tid, sorted(found)))
    notes.append("ids by number of independent sources (ROM name, trainer-groups, randomizer tags, randomizer ini, NdsGameMap): %s; fewer than two: %s" % (sorted(counts.items(), reverse=True), single))

    # 4. EliteFourIndices of the randomizer's ini
    by = {(r["game"], r["key"]): r for r in rows}
    n_row = by[("bw", "n")]["ids"]
    if sorted(n_row) != sorted([ini["black"][5], ini["white"][5]]) or by[("bw", "champion")]["ids"] != [ini["black"][4]] or ini["black"][4] != ini["white"][4]:
        problems.append("bw n/champion ids %s %s disagree with EliteFourIndices black %s white %s" % (n_row, by[("bw", "champion")]["ids"], ini["black"], ini["white"]))
    if [by[("bw", "e4-%d" % k)]["ids"][0] for k in (1, 2, 3, 4)] != ini["black"][:4] or ini["black"][:4] != ini["white"][:4]:
        problems.append("bw Elite Four ids disagree with EliteFourIndices")
    b2e4 = [by[("b2w2", "e4-%d" % k)]["ids"][0] for k in (1, 2, 3, 4)] + by[("b2w2", "champion")]["ids"]
    if b2e4 != ini["black2"]:
        problems.append("b2w2 Elite Four and Iris ids %s disagree with EliteFourIndices %s" % (b2e4, ini["black2"]))
    ch = [by[("b2w2", "e4-%d" % k)]["alt"][0] for k in (1, 2, 3, 4)] + by[("b2w2", "champion")]["alt"]
    # (the ini lists them Shauntal, Grimsley, Marshal, Caitlin, Iris; the names of the ROM decide who is who)
    if sorted(ch) != sorted(ini["black2_challenge"]):
        problems.append("b2w2 Challenge Mode ids %s disagree with ChallengeModeEliteFourIndices %s" % (ch, ini["black2_challenge"]))
    notes.append("EliteFourIndices agree (Black %s, White last id %d, Black 2 %s, Challenge %s)" % (ini["black"], ini["white"][5], ini["black2"], ini["black2_challenge"]))

    # 5. NdsGameMap.kt
    for name, game in (("Pokemon Black", "bw"), ("Pokemon Black 2", "b2w2")):
        lab, final = gmap[name]
        champ = by[(game, "champion")]["ids"]
        if final not in champ:
            problems.append("NdsGameMap %s finalTrainerId %d is not the champion row's id %s" % (name, final, champ))
        rows_with_lab = [r["key"] for r in rows if r["game"] == game and set(lab) <= set(r["ids"])]
        if not rows_with_lab:
            problems.append("NdsGameMap %s labTrainerIds %s are not the ids of any row" % (name, lab))
        notes.append("NdsGameMap %s: labTrainerIds %s = row %s, finalTrainerId %d = champion row" % (name, lab, rows_with_lab, final))

    # 6. badge order, from the ROMs' own award messages
    for key, family, game in (("white", "bw", "bw"), ("black2", "b2w2", "b2w2"), ("white2", "b2w2", "b2w2")):
        rom = sources.roms.get(key)
        if rom is None:
            continue
        order = R.badge_order(rom, family)
        if order != BADGES[game]:
            problems.append("%s: the game's badge message order is %s, expected %s" % (key, order, BADGES[game]))
        else:
            notes.append("%s badge award messages in bit order: %s" % (key, ", ".join(order)))
    for r in rows:
        if r["kind"] == "gym" and r["badge"] != int(r["key"][3:]) - 1:
            problems.append("%s %s: badge bit %s is not gym number minus one" % (r["game"], r["key"], r["badge"]))


def main():
    flags = C.main_flags()
    problems, notes = [], []
    sources = Sources()
    rows = build_rows(sources, problems)
    text = render_rows(rows)
    if not flags["check"]:
        if problems:
            print("refusing to write, problems:")
            for p in problems:
                print("  - " + p)
            sources.close()
            return C.EXIT_MISMATCH
        OUT.write_text(text, encoding="ascii", newline="\n")
        print("wrote %s (%d rows)" % (OUT, len(rows)))
    shipped = C.normalized(OUT)
    if shipped is None:
        problems.append("%s does not exist" % OUT)
    else:
        problems.extend(C.diff_lines(text, shipped))
    cross_checks(rows, sources, problems, notes)
    missing = list(sources.missing)
    code = C.finish("levelcaps-gen5.tsv", problems, missing)
    for n in notes:
        print("  " + n)
    if code == C.EXIT_OK:
        per_game = {}
        for r in rows:
            per_game[r["game"]] = per_game.get(r["game"], 0) + 1
        print("  rows: %s, every cap read from a ROM" % per_game)
    sources.close()
    return code


if __name__ == "__main__":
    sys.exit(main())
