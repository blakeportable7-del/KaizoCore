"""Generate (and check) tracker-gba/src/main/resources/nuzlocke/statics-gen2.tsv (2026-09-29).

    python tools/nuzlocke/gen2_statics.py            # write the file (LF; then run to_crlf.py)
    python tools/nuzlocke/gen2_statics.py --check    # rebuild, compare with the shipped file, read every level and species
                                                     # back out of the ROM dumps and re-run the collision test

Every row is a `loadwildmon SPECIES, LEVEL` followed by `startbattle` in a map script of pokegold or pokecrystal.
The reader (NuzlockeStatics) matches a wild battle on its place and level alone, so a row whose level an ordinary wild
slot of the same place shares (see gen2_wild.py) gets a sixth column, the national dex number the wild Pokemon must be,
unless that very species is also an ordinary slot at that level, when the row is left out.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen2_common as C  # noqa: E402
import gen2_areas as A  # noqa: E402
import gen2_wild as W  # noqa: E402

OUT = os.path.join(C.NZ, "statics-gen2.tsv")
RESEARCH = os.path.join(C.WORKTREE, "docs", "research", "nuzlocke-gen1-3.md")
GAME_ORDER = {"gs": 0, "g": 1, "s": 2, "c": 3}
VARIANTS = {"gs": ("gold", "silver"), "g": ("gold",), "s": ("silver",), "c": ("crystal",)}
REPO_OF = {"gs": "gs", "g": "gs", "s": "gs", "c": "c"}

# What each script battle is, in plain words, for the note column. A row that holds several species uses the set's note.
NOTES = {
    "SUDOWOODO": "the tree on the way to Ecruteak, needs the Squirtbottle, ordinary battle type",
    "GYARADOS": "the Red Gyarados, forced shiny (battle type FORCESHINY)",
    "SNORLAX": "wakes to the Poke Flute radio, holds an item (battle type FORCEITEM)",
    "LUGIA": "Silver Wing, holds an item (battle type FORCEITEM)",
    "HO_OH": "Rainbow Wing, holds an item (battle type FORCEITEM)",
    "SUICUNE": "Tin Tower 1F after the Clear Bell, cannot run (battle type SUICUNE)",
    "CELEBI": "Ilex Forest shrine with the GS Ball, not obtainable on a US cartridge (battle type CELEBI)",
    "LAPRAS": "Union Cave B2F on Fridays only, ordinary battle type",
    frozenset({"GEODUDE", "KOFFING"}):
        "Team Rocket HQ B1F floor-tile traps, 7 Geodude and 7 Koffing, forced battles that cannot be run from (battle type TRAP)",
    frozenset({"VOLTORB", "ELECTRODE"}):
        "8 Voltorb floor-tile traps in Team Rocket HQ B1F (battle type TRAP) and 3 Electrode in the B2F generator room "
        "(ordinary battle type)",
}
# The battle type a script battle sets in code rather than with a loadvar line.
TYPE_IN_CODE = {"CELEBI": "BATTLETYPE_CELEBI"}
# Script files that hold a battle that is not a wild first encounter to be listed, and why not.
NOT_STATICS = {
    "Route29.asm": "the Dude's catching tutorial (battle type TUTORIAL): his Rattata, not the player's",
    "BurnedTowerB1F.asm": "UnusedEnteiScript is labelled unreferenced: nothing ever runs it",
}


def pretty_species(const):
    """A species constant as natdex/species.tsv spells it (SNORLAX -> Snorlax, HO_OH -> Ho-Oh)."""
    names = C.natdex_names()
    by = {names[i].upper().replace(" ", "_").replace("-", "_").replace(".", "_").replace("'", ""): names[i] for i in range(1, 252)}
    return by[const]


def species_number(const):
    return C.parse_species_ids(C.REPO["c"])[const]


def script_battles(game):
    """Every `loadwildmon` of a game's map scripts: dicts with file, line, species, level, kind, type, version, place."""
    root = C.REPO[game]
    maps = {m["camel"]: m for m in A.game_maps(game)}
    out = []
    mapdir = root + "/maps"
    for name in sorted(os.listdir(mapdir)):
        if not name.endswith(".asm"):
            continue
        lines = C.lines_of(mapdir + "/" + name)
        clean = [C.strip_comment(l) for l in lines]
        silver_at = None
        for i, s in enumerate(clean):
            if s == ".Silver:":
                silver_at = i
        has_checkver = any(s == "checkver" for s in clean)
        for i, s in enumerate(clean):
            m = re.match(r"^loadwildmon\s+(\w+)\s*,\s*(\d+)$", s)
            if not m:
                continue
            species, level = m.group(1), int(m.group(2))
            window = clean[i + 1:i + 9]
            kind = "static" if "startbattle" in window else "tutorial" if any(w.startswith("catchtutorial") for w in window) else "other"
            end = i + 1 + (window.index("startbattle") if "startbattle" in window else 0)
            btype = None
            for t in clean[max(0, i - 3):end + 1]:
                mt = re.match(r"^loadvar\s+VAR_BATTLETYPE\s*,\s*(\w+)$", t)
                if mt:
                    btype = mt.group(1)
            btype = btype or TYPE_IN_CODE.get(species) or "BATTLETYPE_NORMAL"
            label = None
            for j in range(i, -1, -1):
                if re.match(r"^[A-Za-z_]\w*:", lines[j]) and not lines[j].startswith("."):
                    label = lines[j]
                    break
            version = "gs"
            if has_checkver:
                if silver_at is None or "iftrue .Silver" not in clean:
                    C.die("%s: checkver without an iftrue .Silver branch" % name)
                version = "s" if i > silver_at else "g"
            camel = name[:-4]
            out.append({"file": name, "line": i + 1, "species": species, "level": level, "kind": kind, "type": btype,
                        "version": version if game == "gs" else "c", "unreferenced": bool(label and "unreferenced" in label),
                        "place": maps[camel]["place"], "map": maps[camel]["const"]})
    return out


_CACHE = {}


def collisions(key, place, level):
    """[(variant + source, map const, [SPECIES at that level])] of the ordinary wild slots of a place that share a level,
    over every variant the game key covers."""
    out = []
    for variant in VARIANTS[key]:
        game = REPO_OF[key]
        if (game, variant) not in _CACHE:
            _CACHE[(game, variant)] = W.place_levels(game, variant)
        for (src, const), pairs in sorted(_CACHE[(game, variant)].get(place, {}).items()):
            species = sorted({sp for lv, sp in pairs if lv == level})
            if species:
                out.append((variant + " " + src, const, species))
    return out


def build():
    """(rows, skipped, dex_notes): rows = [(game, place, level, species text, note, dex or "")], skipped = [(reason, key, place,
    level, species)], dex_notes = one header line per row that carries a dex number."""
    groups = {}
    skipped = []
    for game in ("gs", "c"):
        for b in script_battles(game):
            if b["file"] in NOT_STATICS or b["kind"] != "static":
                if b["file"] not in NOT_STATICS:
                    C.die("%s:%d: a loadwildmon that is neither a static nor a known exception" % (b["file"], b["line"]))
                skipped.append((NOT_STATICS[b["file"]], b["version"], b["place"], b["level"], b["species"]))
                continue
            groups.setdefault((b["version"], b["place"], b["level"]), []).append(b)
    rows = []
    dex_notes = []
    for (key, place, level), bs in groups.items():
        hits = collisions(key, place, level)
        consts = list(dict.fromkeys(b["species"] for b in bs))
        species = " or ".join(pretty_species(c) for c in consts)
        dex = ""
        if hits:
            same_species = [h for h in hits if set(h[2]) & set(consts)]
            if same_species or len(consts) != 1:
                skipped.append(("an ordinary slot of the same place has this species at this level, so nothing tells them apart",
                                key, place, level, species))
                continue
            dex = str(species_number(consts[0]))
            slots = sorted({pretty_species(sp) for h in hits for sp in h[2]})
            shown = ", ".join(slots[:8]) + (" and %d more" % (len(slots) - 8) if len(slots) > 8 else "")
            dex_notes.append("#   %s, %s at %s, level %d (ordinary level %d slots there: %s)" % (key, species, place, level, level, shown))
        kinds = frozenset(b["species"] for b in bs)
        note = NOTES[kinds] if kinds in NOTES else "; ".join(dict.fromkeys(NOTES[k] for k in sorted(kinds)))
        rows.append((key, place, level, species, note, dex))
    rows.sort(key=lambda r: (GAME_ORDER[r[0]], r[1], r[2]))
    dex_notes.sort()
    return rows, skipped, dex_notes


def header(dex_notes):
    lines = """# Battles that are not a wild first encounter, Generation 2 (2026-09-29): a wild battle at this place at exactly this
# level is a set battle (a static), not the area's first encounter.
#
# game: gs = Gold and Silver, g = Gold only, s = Silver only (only where a level differs between them), c = Crystal.
# place: a place of areas-gen2.tsv (the landmark the map belongs to). level: the level in the script.
# species: the vanilla species, a note for humans; a randomized game changes the species and keeps the place and level.
# dex (optional sixth column): the national dex number the wild Pokemon must be. Only the rows listed below carry it.
#
# Every row is a loadwildmon followed by startbattle in a map script of pokegold or pokecrystal. Every startbattle in both
# trees was read (15 wild battles in pokegold, 14 in pokecrystal) and each level and species was read back out of the Gold
# (U) and Crystal (U) dumps through the randomizer's StaticPokemon offsets (gen2_offsets.ini); Silver's own levels are the
# other branch of the same checkver script, which is in the Gold dump too. gen2_statics.py --check redoes all of it.
#
# A row without a dex number exists only when no ordinary wild slot of that place has the same level (grass, surf, fishing
# wherever a map has water, Headbutt trees, Rock Smash, swarms; in every version the row covers), because the reader matches
# on place and level alone and would call that encounter a static. The wild tables are data/wild/*.asm. These rows have a
# dex number because an ordinary slot of the same place has that level too; they count only when the wild Pokemon is that
# species, so a randomized game (which changes the species) does not see them as statics:""".split("\n")
    lines += dex_notes
    lines += """# Left out on purpose (found by the same scan):
# - Raikou and Entei (and Suicune in Gold and Silver), level 40: they roam and turn up on any route. BATTLETYPE_ROAMING (5).
# - The Bug-Catching Contest: BATTLETYPE_CONTEST (6). A contest battle is recognised by its battle type and needs no row.
# - The Dude's catching tutorial on Route 29 (a level 5 Rattata): BATTLETYPE_TUTORIAL (3); it is the Dude's catch.
# - The unused Entei script in Burned Tower B1F (pokegold): labelled unreferenced, nothing runs it.
# Battle types also identify statics: FORCEITEM (10) is Snorlax, Lugia and Ho-Oh, TRAP (9) the Rocket HQ floor tiles,
# FORCESHINY (7) Red Gyarados, and in Crystal SUICUNE (12) and CELEBI (11). Sudowoodo, the Electrode and Lapras are NORMAL.
#
# game	place	level	species	note	dex (optional)""".split("\n")
    return "\n".join(lines) + "\n"


def render(rows, dex_notes):
    return header(dex_notes) + "".join("\t".join(str(x) for x in (r if r[5] else r[:5])) + "\n" for r in rows)


def generate():
    rows, skipped, dex_notes = build()
    C.write_lf(OUT, render(rows, dex_notes))
    print("wrote %s: %d rows (%d with a dex number)" % (OUT, len(rows), sum(1 for r in rows if r[5])))
    for s in skipped:
        print("  left out: %s %s %s %s (%s)" % (s[1], s[2], s[3], s[4], s[0]))


# ---------------------------------------------------------------------------------------- checks
def ini_statics(name):
    """[(comment name, [species offsets], [level offsets])] from a gen2_offsets.ini section (Silver copies Gold's)."""
    text = C.read_text(C.INI)
    out = []
    cur = None
    for line in text.split("\n"):
        line = line.strip()
        m = re.match(r"^\[(.+)\]$", line)
        if m:
            cur = m.group(1)
            continue
        if cur != name:
            continue
        m = re.match(r"^StaticPokemon\{\}=\{Species=\[([^\]]*)\](?:,\s*Level=\[([^\]]*)\])?\}\s*//\s*(.+)$", line)
        if m:
            sp = [int(x, 16) for x in m.group(1).split(",")]
            lv = [int(x, 16) for x in m.group(2).split(",")] if m.group(2) else []
            out.append((m.group(3).strip(), sp, lv))
    return out


def check():
    probs = C.Problems()
    rows, skipped, dex_notes = build()
    shipped = [tuple(r) for r in C.data_rows(OUT)]
    probs.check([tuple(str(x) for x in (r if r[5] else r[:5])) for r in rows] == shipped, "the shipped file differs from what the generator builds")
    probs.check(C.clean_crlf_ascii(OUT), "the shipped file is not clean CRLF ASCII (run to_crlf.py)")
    probs.check([l for l in C.lines_of(OUT) if l.startswith("#")] == [l for l in header(dex_notes).split("\n") if l.startswith("#")],
                "header comment differs")
    probs.check(all(len(r) in (5, 6) for r in shipped), "every row has five or six columns")
    places = {r[2] for r in C.data_rows(os.path.join(C.NZ, "areas-gen2.tsv"))}
    probs.check(all(r[1] in places for r in shipped), "a place is not in areas-gen2.tsv")

    # 1. every scripted wild battle in both trees is accounted for: a row, or a stated reason
    counts = {g: len(script_battles(g)) for g in ("gs", "c")}
    probs.check(counts == {"gs": 18, "c": 17}, "loadwildmon count changed: %s" % counts)
    static_lines = {g: sum(1 for b in script_battles(g) if b["kind"] == "static") for g in ("gs", "c")}
    probs.check(static_lines == {"gs": 15, "c": 14}, "startbattle-fed loadwildmon count: %s" % static_lines)

    # 2. ROM: the randomizer's StaticPokemon offsets hold the species and level of each row
    names = C.natdex_names()
    by_name = {v: k for k, v in names.items()}
    rom_gold = C.load_rom("gs")
    rom_crystal = C.load_rom("c")
    wanted = {  # ini comment -> (place, level) it must appear as
        "gs": {"Sudowoodo": ("Route 36", 20), "RedGyarados": ("Lake of Rage", 30), "Snorlax": ("Vermilion City", 50),
               "Lapras": ("Union Cave", 20), "Voltorb": ("Mahogany Town", 23), "Geodude": ("Mahogany Town", 21),
               "Koffing": ("Mahogany Town", 21), "Electrode1": ("Mahogany Town", 23), "Electrode2": ("Mahogany Town", 23),
               "Electrode3": ("Mahogany Town", 23), "Lugia": ("Whirl Islands", 70), "Ho-Oh": ("Tin Tower", 40)},
        "c": {"Sudowoodo": ("Route 36", 20), "RedGyarados": ("Lake of Rage", 30), "Snorlax": ("Vermilion City", 50),
              "Lapras": ("Union Cave", 20), "Voltorb": ("Mahogany Town", 23), "Geodude": ("Mahogany Town", 21),
              "Koffing": ("Mahogany Town", 21), "Electrode1": ("Mahogany Town", 23), "Electrode2": ("Mahogany Town", 23),
              "Electrode3": ("Mahogany Town", 23), "Lugia": ("Whirl Islands", 60), "Ho-Oh": ("Tin Tower", 60),
              "Suicune": ("Tin Tower", 40)},
    }
    species_of_ini = {"Sudowoodo": "Sudowoodo", "RedGyarados": "Gyarados", "Snorlax": "Snorlax", "Lapras": "Lapras",
                      "Voltorb": "Voltorb", "Geodude": "Geodude", "Koffing": "Koffing", "Electrode1": "Electrode",
                      "Electrode2": "Electrode", "Electrode3": "Electrode", "Lugia": "Lugia", "Ho-Oh": "Ho-Oh",
                      "Suicune": "Suicune"}
    rom_hits = 0
    for game, rom, section in (("gs", rom_gold, "Gold (U)"), ("c", rom_crystal, "Crystal (U)")):
        entries = {n: (sp, lv) for n, sp, lv in ini_statics(section)}
        for n, (place, level) in wanted[game].items():
            sp, lv = entries[n]
            probs.check(all(rom[o] == by_name[species_of_ini[n]] for o in sp[-1:]),
                        "%s ROM %s: species byte at %s is not %s" % (game, n, [hex(o) for o in sp[-1:]], species_of_ini[n]))
            probs.check(rom[lv[0]] == level, "%s ROM %s: level byte %X is %d, expected %d" % (game, n, lv[0], rom[lv[0]], level))
            rom_hits += 1
    # Silver's branch of the checkver scripts sits in the same Gold dump, at the [Silver (U)] offsets
    for n, (place, level) in (("Lugia", ("Whirl Islands", 40)), ("Ho-Oh", ("Tin Tower", 70))):
        sp, lv = {a: (b, c) for a, b, c in ini_statics("Silver (U)")}[n]
        probs.check(rom_gold[sp[-1]] == by_name[species_of_ini[n]] and rom_gold[lv[0]] == level,
                    "Silver branch of %s in the Gold dump: %d at %X" % (n, rom_gold[lv[0]], lv[0]))
        rom_hits += 1
    # 3. every shipped row: a row without a dex number does not collide; a row with one collides, carries the right number and
    #    no ordinary slot of that species has that level
    for r in shipped:
        game, place, level, species, note = r[:5]
        dex = r[5] if len(r) > 5 else ""
        level = int(level)
        hits = collisions(game, place, level)
        probs.check(all(s in names.values() for s in species.split(" or ")), "%s: species name does not resolve" % species)
        if dex == "":
            probs.check(not hits, "%s %s %d collides with an ordinary encounter and has no dex number" % (game, place, level))
        else:
            probs.check(bool(hits), "%s %s %d has a dex number but nothing collides" % (game, place, level))
            probs.check(names[int(dex)] == species, "%s %s %d: dex number %s is not %s" % (game, place, level, dex, species))
            slot_species = {sp for h in hits for sp in h[2]}
            probs.check(species_number_const(species) not in slot_species,
                        "%s %s %d: the species itself is an ordinary slot at that level" % (game, place, level))
    # 4. the skipped rows are the two named exceptions or a same-species collision
    for reason, key, place, level, species in skipped:
        if reason.startswith("an ordinary slot"):
            probs.check(any(species_number_const(species) in h[2] for h in collisions(key, place, level)),
                        "%s %s %d was skipped for a collision that is not there" % (key, place, level))
    reasons = sorted({s[0].split(" (")[0] for s in skipped})
    print("skipped: %d candidates (%s)" % (len(skipped), "; ".join(reasons)))
    # 5. the roamers: InitRoamMons in both disassemblies
    for game, expect in (("gs", ("RAIKOU", "ENTEI", "SUICUNE")), ("c", ("RAIKOU", "ENTEI"))):
        text = C.read_text(C.REPO[game] + "/engine/overworld/wildmons.asm")
        start = text.index("InitRoamMons:")
        body = text[start:text.index("CheckEncounterRoamMon:", start)]
        species = re.findall(r"ld a, (\w+)\n\s*ld \[wRoamMon\dSpecies\], a", body)
        levels = re.findall(r"ld a, (\d+)\n\s*ld \[wRoamMon1Level\], a", body)
        probs.check(tuple(species) == expect and levels == ["40"], "%s InitRoamMons: %s %s" % (game, species, levels))
    # 6. the trap counts (22 floor tiles: 7 Geodude, 7 Koffing, 8 Voltorb) in both trees
    for game in ("gs", "c"):
        t = C.read_text(C.REPO[game] + "/maps/TeamRocketBaseB1F.asm")
        found = re.findall(r"^ExplodingTrap\d+:\n(?:.*\n){0,4}?\s*scall (\w+)ExplodingTrap", t, re.M)
        tally = {k: found.count(k) for k in set(found)}
        probs.check(tally == {"Geodude": 7, "Koffing": 7, "Voltorb": 8}, "%s trap counts: %s" % (game, tally))
    # 7. the research doc's static tables (sections 4.2.3 and 5.2.3) against the script levels
    text = C.read_text(RESEARCH)
    for label, a, b in (("4.2.3", "#### 4.2.3", "#### 4.2.4"), ("5.2.3", "#### 5.2.3", "#### 5.2.4")):
        sect = text[text.index(a):text.index(b)]
        for line in sect.split("\n"):
            m = re.match(r"^\|\s*(Sudowoodo|Red Gyarados|Snorlax|Lapras|Suicune|Lugia|Ho-Oh|Celebi)\s*\|[^|]*\|\s*([^|]+?)\s*\|", line)
            if m:
                doc_level = m.group(2)
                game = "gs" if label == "4.2.3" else "c"
                sp = m.group(1).upper().replace("-", "_").replace(" ", "_")
                sp = "GYARADOS" if sp == "RED_GYARADOS" else sp
                lv = {b["level"] for b in script_battles(game) if b["species"] == sp}
                ok = all(str(x) in doc_level for x in lv)
                if not ok:
                    print("RESEARCH DOC DISAGREES %s: %s is level %s in the doc, scripts say %s" % (label, m.group(1), doc_level, sorted(lv)))
                probs.check(ok, "research doc %s %s level" % (label, m.group(1)))
    print("ROM offsets read: %d rows checked against the dumps" % rom_hits)
    probs.finish("gen2_statics --check")


def species_number_const(species_text):
    """The species constant for a species name as natdex spells it."""
    names = C.natdex_names()
    by = {names[i]: names[i].upper().replace(" ", "_").replace("-", "_").replace(".", "_").replace("'", "") for i in range(1, 252)}
    return by[species_text]


if __name__ == "__main__":
    if sys.argv[1:2] == ["--check"]:
        check()
    else:
        generate()
