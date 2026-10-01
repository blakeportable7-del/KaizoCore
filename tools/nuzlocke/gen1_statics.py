#!/usr/bin/env python
"""Generate (default) or verify (--check) tracker-gba/src/main/resources/nuzlocke/statics-gen1.tsv.

    python tools/nuzlocke/gen1_statics.py            # rewrite the file (LF endings; run to_crlf.py afterwards)
    python tools/nuzlocke/gen1_statics.py --check    # re-read the disassemblies and the Red, Blue and Yellow ROMs, compare

A static is a wild battle that a map object or a script starts with a fixed species and level. Two kinds exist in
Generation 1: Pokemon standing on a map as an object (the Power Plant Voltorb, Electrode and Zapdos, Articuno, Moltres,
Mewtwo: their species and level are in the object data) and battles a script starts (the two Snorlax, and the ghost
Marowak of Pokemon Tower 6F: `ld a, SPECIES / ld [wCurOpponent], a / ld a, LEVEL / ld [wCurEnemyLevel], a`).

The reader matches a row by (game, place, level) only, so a static whose place and level equal a slot of the place's
ordinary wild tables (grass, surf, Super Rod, and the Old and Good Rod levels 5 and 10 that any water gives) cannot be
told from that slot; such a static is left out of the file and named in its header instead. For rb the tables of Red
and of Blue both count.
"""
import argparse
import re
import sys
from collections import OrderedDict
from pathlib import Path

sys.dont_write_bytecode = True     # never leave .pyc files behind in the repo
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen1_areas as A
import gen1_common as g

OUT = g.NZ_DIR / "statics-gen1.tsv"
GAME_KEYS = ("rb", "y")
BUILDS_OF = {"rb": ("red", "blue"), "y": ("yellow",)}

# script-started battles: (script file, map const it runs on, species constant the script loads, note)
SCRIPTS = [
    ("Route12", "ROUTE_12", "SNORLAX", "Snorlax asleep in the road; the Poke Flute wakes it and the script starts the battle"),
    ("Route16", "ROUTE_16", "SNORLAX", "Snorlax asleep in the road; the Poke Flute wakes it and the script starts the battle"),
    ("PokemonTower6F", "POKEMON_TOWER_6F", "RESTLESS_SOUL", "the ghost Marowak on 6F; it cannot be caught even with the Silph Scope"),
]
OBJECT_NOTES = {
    "VOLTORB": "a Voltorb disguised as an item ball (six of them); each touch is a battle",
    "ELECTRODE": "an Electrode disguised as an item ball (two of them)",
    "ZAPDOS": "legendary bird, one battle",
    "ARTICUNO": "legendary bird, one battle",
    "MOLTRES": "legendary bird, one battle",
    "MEWTWO": "legendary, one battle",
}
ORDER = ["Route 12", "Route 16", "Pokemon Tower", "Power Plant", "Seafoam Islands", "Victory Road", "Cerulean Cave"]
# (game, place, level) -> (note, national dex number) of a static whose place and level an ordinary wild slot shares
CHECKED = {}


def script_static(d, script, species_const):
    """(species const, level) from the script's `ld a, X / ld [wCurOpponent], a / ld a, N / ld [wCurEnemyLevel], a`."""
    lines = d.lines("scripts/%s.asm" % script)
    for i in range(len(lines) - 3):
        m1 = re.match(r"ld a,\s*(\w+)$", lines[i])
        m2 = re.match(r"ld a,\s*(\d+)$", lines[i + 2])
        if (m1 and m2 and lines[i + 1] == "ld [wCurOpponent], a" and lines[i + 3] == "ld [wCurEnemyLevel], a"
                and m1.group(1) == species_const):
            return m1.group(1), int(m2.group(1))
    raise ValueError("no fixed battle for %s in scripts/%s.asm" % (species_const, script))


def candidates(build):
    """[(place, level, species const, note, map const)] for one build, before the collision test."""
    d = g.Disasm(build)
    (places, _), _ = A.build_places(build)
    byc = d.map_by_const()
    out = []
    for const, rec in d.objects().items():
        for o in rec["objs"]:
            if o["kind"] == "mon_or_trainer" and not o["arg1"].startswith("OPP_"):
                out.append((places[byc[const]["id"]][0], o["arg2"], o["arg1"], OBJECT_NOTES[o["arg1"]], const))
    for script, const, species, note in SCRIPTS:
        real, level = script_static(d, script, species)
        out.append((places[byc[const]["id"]][0], level, real, note, const))
    return out


def ordinary_slots(build):
    """{(place, level): set of species names} of every ordinary wild slot: grass, surf, Super Rod, and the Old Rod (5)
    and Good Rod (10) that any water gives (listed as "Old Rod or Good Rod")."""
    d = g.Disasm(build)
    (places, _), _ = A.build_places(build)
    byc = d.map_by_const()
    names = g.natdex_names()
    dex, sp = d.dex_of_internal(), d.species_consts()
    out = {}

    def add(place, lv, const):
        out.setdefault((place, lv), set()).add(names[dex[sp[const]]] if const else "Old Rod or Good Rod")

    for mid, rec in d.wild_by_map().items():
        for kind in ("grass", "water"):
            for lv, s_ in rec[kind][1]:
                add(places[mid][0], lv, s_)
    for const, fish in d.super_rod().items():
        for lv, s_ in fish:
            add(places[byc[const]["id"]][0], lv, s_)
    for place in {p for p, _ in places.values()}:
        add(place, 5, None)
        add(place, 10, None)
    return out


def rows_for(game):
    """(kept rows [(place, level, species name, note)], left out [(place, level, species name, {build: ordinary species})])."""
    builds = BUILDS_OF[game]
    d = g.Disasm(builds[0])
    names = g.natdex_names()
    dex, sp = d.dex_of_internal(), d.species_consts()
    ordinary = {b: ordinary_slots(b) for b in builds}
    grouped = OrderedDict()
    for place, level, const, note, _ in candidates(builds[0]):
        grouped.setdefault((place, level), (names[dex[sp[const]]], note, dex[sp[const]]))
    kept, left = [], []
    for (place, level), (sname, note, dexno) in grouped.items():
        hit = {b: sorted(ordinary[b][(place, level)]) for b in builds if (place, level) in ordinary[b]}
        if hit:
            # An ordinary wild slot has this place and level: the row is kept, with the national dex number the
            # Pokemon must be (the sixth column), so it does not swallow that slot.
            left.append((place, level, sname, hit))
            CHECKED[(game, place, level)] = (note, dexno)
        else:
            kept.append((place, level, sname, note))
    key = lambda r: (ORDER.index(r[0]), r[1])
    return sorted(kept, key=key), sorted(left, key=key)


def render(by_game):
    lines = [
        "Static battles for Generation 1 (2026-09-29): wild battles a map object or a script starts, not the area's first encounter.",
        "",
        "A wild battle at `place` at exactly `level` counts as a static (a set battle). The place is a place string of",
        "areas-gen1.tsv; the species is the vanilla one and only a note for people (a randomized game changes the species and",
        "keeps the place and level). game: rb = Red and Blue, y = Yellow.",
        "",
        "Source: the map object files of pokered and pokeyellow (data/maps/objects/*.asm: an object whose last two fields are a",
        "species and a level is a Pokemon standing on the map) and the fight-start scripts (scripts/Route12.asm, Route16.asm,",
        "PokemonTower6F.asm). The ROM dumps hold the same objects and script bytes, and the randomizer's gen1_offsets.ini names",
        "the same species and level offsets; `python tools/nuzlocke/gen1_statics.py --check` reads them all back.",
        "",
        "Not rows, on purpose:",
        "- Gifts, fossils, the Magikarp salesman, Game Corner prizes and in-game trades are not battles.",
        "- The Viridian City old man's tutorial catch (Weedle in Red and Blue, Rattata in Yellow) and Yellow's opening Pikachu",
        "  (Pallet Town) are level 5 scripted battles. Level 5 is also the Old Rod's Magikarp, which any town with water has, so",
        "  a (place, level) row would swallow real encounters. The battle type flags them instead: wBattleType 1 is the old man,",
        "  4 (Yellow only) is the Pikachu battle.",
    ]
    left = [(game, r) for game in GAME_KEYS for r in by_game[game][1]]
    if left:
        lines += ["- Rows with a sixth column carry the national dex number the wild Pokemon must be, because an ordinary wild slot of the",
                  "  same place has that level too and a row of place and level alone would swallow that real encounter:"]
        for game, (place, level, sname, hit) in left:
            who = "; ".join("%s: %s" % (b.capitalize(), " or ".join(v)) for b, v in hit.items())
            lines.append("  %s, %s at %s, level %d (ordinary slot there: %s)" % (game, sname, place, level, who))
        lines += ["  The species check means a randomized game, which changes the species, does not see these rows as statics. The other",
                  "  ghost battles are told by the game's own flags (a wild battle on Pokemon Tower maps 142 to 148 without the Silph",
                  "  Scope is the ghost: IsGhostBattle, engine/battle/core.asm)."]
    lines += ["", "game\tplace\tlevel\tspecies\tnote\tdex (optional)"]
    out = g.header_block(lines[:-1]) + "# " + lines[-1] + "\n"
    for game in GAME_KEYS:
        rows = [(place, level, sname, note, None) for place, level, sname, note in by_game[game][0]]
        for place, level, sname, _ in by_game[game][1]:
            note, dexno = CHECKED[(game, place, level)]
            rows.append((place, level, sname, note, dexno))
        for place, level, sname, note, dexno in sorted(rows, key=lambda r: (ORDER.index(r[0]), r[1])):
            out += "%s\t%s\t%d\t%s\t%s%s\n" % (game, place, level, sname, note, "" if dexno is None else "\t%d" % dexno)
    return out


def generate():
    by_game = {game: rows_for(game) for game in GAME_KEYS}
    return render(by_game), by_game


def find_all(data, pat):
    out, i = [], data.find(pat)
    while i >= 0:
        out.append(i)
        i = data.find(pat, i + 1)
    return out


_WRAM = {}


def wram(build):
    folder = g.BUILDS[build][0]
    if folder not in _WRAM:
        sys.path.insert(0, str(g.ROOT / "tools"))
        import contextlib
        import io
        import wram_layout
        with contextlib.redirect_stderr(io.StringIO()):
            _WRAM[folder] = wram_layout.layout(g.REFS / folder)[0]
    return _WRAM[folder]


def check():
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
    shipped_checked = {"rb": [], "y": []}      # the rows with a species check (a sixth column)
    if OUT.exists():
        for line in g.read_text(OUT).splitlines():
            if line and not line.startswith("#"):
                p = line.split("\t")
                if len(p) > 5:
                    shipped_checked[p[0]].append((p[1], int(p[2]), p[3], int(p[5])))
                else:
                    shipped[p[0]].append((p[1], int(p[2]), p[3], p[4]))
    for game in shipped:
        if shipped[game] != by_game[game][0]:
            err("shipped rows for %s differ from the derived ones" % game)
        derived_checked = [(p, lv, s_, CHECKED[(game, p, lv)][1]) for p, lv, s_, _ in by_game[game][1]]
        if sorted(shipped_checked[game]) != sorted(derived_checked):
            err("shipped species-checked rows for %s differ from the derived ones" % game)
        keys = [(p, lv) for p, lv, _, _ in shipped[game]] + [(p, lv) for p, lv, _, _ in shipped_checked[game]]
        if len(keys) != len(set(keys)):
            err("%s has two rows for one (place, level)" % game)
    summary = []
    rom_collisions = {"rb": set(), "y": set()}
    cand0 = {}
    for build in ("red", "blue", "yellow"):
        game = g.GAME_OF[build]
        d = g.Disasm(build)
        rom = g.Rom(build)
        rom.verify_build()
        sp = d.species_consts()
        byc = d.map_by_const()
        w = wram(build)
        # 0. Red and Blue share every static
        c = sorted(candidates(build))
        if game in cand0 and cand0[game] != c:
            err("%s: statics differ from the other build of %s" % (build, game))
        cand0.setdefault(game, c)
        # 1. objects: the ROM's static Pokemon objects are the disassembly's, with the same species and level
        obj_asm = sorted((c_, sp[o["arg1"]], o["arg2"]) for c_, rec in d.objects().items() for o in rec["objs"]
                         if o["kind"] == "mon_or_trainer" and not o["arg1"].startswith("OPP_"))
        obj_rom = sorted((m["const"], o["arg1"], o["arg2"]) for m in d.maps() if not m["unused"] and m["const"] in d.objects()
                         for o in rom.map_objects(m["id"])["objs"] if o["kind"] == "mon_or_trainer" and o["arg1"] < g.OPP_ID_OFFSET)
        if obj_asm != obj_rom:
            err("%s: static Pokemon objects differ between the ROM and the map object files" % build)
        # 2. scripts: the ROM holds each fight-start sequence once per map that uses it, in that map's bank, and
        #    the randomizer's offsets (below) point at the species and level operands of exactly those sequences
        n_script = 0
        entries = g.static_entries(rom.ini)
        by_seq = {}
        for script, const, species, _ in SCRIPTS:
            by_seq.setdefault(script_static(d, script, species), []).append(const)
        for (real, level), consts in by_seq.items():
            pat = bytes([0x3E, sp[real], 0xEA, w["wCurOpponent"] & 255, w["wCurOpponent"] >> 8,
                         0x3E, level, 0xEA, w["wCurEnemyLevel"] & 255, w["wCurEnemyLevel"] >> 8])
            hits = find_all(rom.data, pat)
            banks = sorted(rom.map_header_ptr(byc[c_]["id"])[0] for c_ in consts)
            if sorted(h // 0x4000 for h in hits) != banks:
                err("%s: the %s battle of %s: ROM hits in banks %s, expected banks %s" % (
                    build, real, consts, sorted(h // 0x4000 for h in hits), banks))
            names_ = ["Ghost Marowak"] if real == "RESTLESS_SOUL" else ["Snorlax 1", "Snorlax 2"]
            ini_sp = {o for n in names_ if n in entries for o in entries[n][0]}
            ini_lv = {o for n in names_ if n in entries for o in entries[n][1]}
            if not all(h + 1 in ini_sp and h + 6 in ini_lv for h in hits):
                err("%s: the ini's offsets for %s are not the operands of the ROM's `ld a, %s` / `ld a, %d` sequences" % (
                    build, names_, real, level))
            n_script += len(hits)
        # 3. the randomizer's own offsets (gen1_offsets.ini StaticPokemon) name the same species and level bytes
        want = {"Snorlax 1": ("SNORLAX", 30), "Snorlax 2": ("SNORLAX", 30), "Articuno": ("ARTICUNO", 50), "Zapdos": ("ZAPDOS", 50),
                "Moltres": ("MOLTRES", 50), "Mewtwo": ("MEWTWO", 70), "Ghost Marowak": ("MAROWAK", 30)}
        for i in range(1, 7):
            want["Voltorb %d" % i] = ("VOLTORB", 40)
        for i in range(1, 3):
            want["Electrode %d" % i] = ("ELECTRODE", 43)
        for name, (species, level) in want.items():
            if name not in entries:
                err("%s: gen1_offsets.ini has no StaticPokemon entry %s" % (build, name))
                continue
            offs_sp, offs_lv = entries[name]
            if any(rom.data[o] != sp[species] for o in offs_sp) or any(rom.data[o] != level for o in offs_lv):
                err("%s: ROM bytes at the ini's offsets for %s are not %s level %d" % (build, name, species, level))
        # 4. the collision test from the ROM's own wild tables
        (places, _), _ = A.build_places(build)
        rom_levels = {}
        for mid, rec in rom.wild_tables(len(d.maps())).items():
            for kind in ("grass", "water"):
                for lv, _ in rec[kind][1]:
                    rom_levels.setdefault(places[mid][0], set()).add(lv)
        for mid, fish in rom.super_rod().items():
            for lv, _ in fish:
                rom_levels.setdefault(places[mid][0], set()).add(lv)
        for place, level, _, _ in shipped[game]:
            if level in rom_levels.get(place, set()) or level in (5, 10):
                err("%s: shipped static (%s, %d) collides with an ordinary slot in the ROM's tables" % (build, place, level))
        for (place, level, _, _, _) in cand0[game]:
            if level in rom_levels.get(place, set()) or level in (5, 10):
                rom_collisions[game].add((place, level))
        # every place string is one of the areas file's places
        area_places = {p for p, _ in places.values()}
        for p, _, _, _ in shipped[game] + shipped_checked[game]:
            if p not in area_places:
                err("static place %r is not a place of areas-gen1.tsv" % p)
        summary.append("%s: %d static Pokemon objects and %d script battles found in the ROM, %d ini offsets read back" % (
            build, len(obj_rom), n_script, sum(len(v[0]) + len(v[1]) for k, v in entries.items() if k in want)))
    for game in GAME_KEYS:
        left = {(p, lv) for p, lv, _, _ in by_game[game][1]}
        if left != rom_collisions[game]:
            err("%s: left-out statics %s are not the collisions the ROM tables show, %s" % (game, sorted(left), sorted(rom_collisions[game])))
    print("\n".join(summary))
    for game in GAME_KEYS:
        print("%s: %d rows, %d more with a species check %s" % (game, len(by_game[game][0]), len(by_game[game][1]),
                                                              [(p, lv, s) for p, lv, s, _ in by_game[game][1]]))
    if errors:
        print("FAILED: %d mismatch(es)" % len(errors))
        return 1
    print("OK: statics-gen1.tsv matches the disassemblies and the Red, Blue and Yellow ROM dumps")
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
