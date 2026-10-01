#!/usr/bin/env python
"""Generate (default) or verify (--check) tracker-gba/src/main/resources/nuzlocke/areas-gen1.tsv.

    python tools/nuzlocke/gen1_areas.py            # rewrite the file (LF endings; run to_crlf.py afterwards)
    python tools/nuzlocke/gen1_areas.py --check    # re-read the disassemblies and the three ROM dumps, compare, exit 1 on a mismatch

The place of every map is derived, not typed: outdoor maps are their own place (their Town Map name); an indoor map
belongs to the outdoor map its warps lead out to (the first layer of reverse warps that contains an outdoor map; a
tie goes to the game's own Town Map group). The named multi-floor places and the Safari Zone sectors are the only
hand-made part (COMPLEXES below). The ROM check derives the places a second time from the warps stored in the ROM.
"""
import argparse
import sys
from collections import defaultdict
from pathlib import Path

sys.dont_write_bytecode = True     # never leave .pyc files behind in the repo
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen1_common as g

OUT = g.NZ_DIR / "areas-gen1.tsv"


def floors(prefix, labels):
    return {prefix + k: v for k, v in labels.items()}


N_F = {"%dF" % i: "%dF" % i for i in range(1, 12)}
COMPLEXES = {
    "Viridian Forest": {"VIRIDIAN_FOREST": ""},
    "Mt. Moon": {"MT_MOON_1F": "1F", "MT_MOON_B1F": "B1F", "MT_MOON_B2F": "B2F"},
    "Rock Tunnel": {"ROCK_TUNNEL_1F": "1F", "ROCK_TUNNEL_B1F": "B1F"},
    "Power Plant": {"POWER_PLANT": ""},
    "Pokemon Tower": {"POKEMON_TOWER_%dF" % i: "%dF" % i for i in range(1, 8)},
    "Pokemon Mansion": {"POKEMON_MANSION_1F": "1F", "POKEMON_MANSION_2F": "2F", "POKEMON_MANSION_3F": "3F",
                        "POKEMON_MANSION_B1F": "B1F"},
    "Seafoam Islands": {"SEAFOAM_ISLANDS_1F": "1F", "SEAFOAM_ISLANDS_B1F": "B1F", "SEAFOAM_ISLANDS_B2F": "B2F",
                        "SEAFOAM_ISLANDS_B3F": "B3F", "SEAFOAM_ISLANDS_B4F": "B4F"},
    "Victory Road": {"VICTORY_ROAD_1F": "1F", "VICTORY_ROAD_2F": "2F", "VICTORY_ROAD_3F": "3F"},
    "Rocket Hideout": {"ROCKET_HIDEOUT_B1F": "B1F", "ROCKET_HIDEOUT_B2F": "B2F", "ROCKET_HIDEOUT_B3F": "B3F",
                       "ROCKET_HIDEOUT_B4F": "B4F", "ROCKET_HIDEOUT_ELEVATOR": "Elevator"},
    "Silph Co.": dict({"SILPH_CO_%dF" % i: "%dF" % i for i in range(1, 12)}, SILPH_CO_ELEVATOR="Elevator"),
    "Cerulean Cave": {"CERULEAN_CAVE_1F": "1F", "CERULEAN_CAVE_2F": "2F", "CERULEAN_CAVE_B1F": "B1F"},
    "Diglett's Cave": {"DIGLETTS_CAVE": ""},
    "S.S. Anne": {"SS_ANNE_1F": "1F", "SS_ANNE_2F": "2F", "SS_ANNE_3F": "3F", "SS_ANNE_B1F": "B1F",
                  "SS_ANNE_BOW": "Bow", "SS_ANNE_KITCHEN": "Kitchen", "SS_ANNE_CAPTAINS_ROOM": "Captain's Room",
                  "SS_ANNE_1F_ROOMS": "1F Rooms", "SS_ANNE_2F_ROOMS": "2F Rooms", "SS_ANNE_B1F_ROOMS": "B1F Rooms"},
    "Celadon Mansion": {"CELADON_MANSION_1F": "1F", "CELADON_MANSION_2F": "2F", "CELADON_MANSION_3F": "3F",
                        "CELADON_MANSION_ROOF": "Roof", "CELADON_MANSION_ROOF_HOUSE": "Roof House"},
    "Underground Path": {"UNDERGROUND_PATH_NORTH_SOUTH": "North-South", "UNDERGROUND_PATH_WEST_EAST": "West-East"},
    "Safari Zone Center": {"SAFARI_ZONE_CENTER": "", "SAFARI_ZONE_CENTER_REST_HOUSE": "Rest House"},
    "Safari Zone East": {"SAFARI_ZONE_EAST": "", "SAFARI_ZONE_EAST_REST_HOUSE": "Rest House"},
    "Safari Zone North": {"SAFARI_ZONE_NORTH": "", "SAFARI_ZONE_NORTH_REST_HOUSE": "Rest House"},
    "Safari Zone West": {"SAFARI_ZONE_WEST": "", "SAFARI_ZONE_WEST_REST_HOUSE": "Rest House",
                         "SAFARI_ZONE_SECRET_HOUSE": "Secret House"},
}
# The two link rooms are reached by the Cable Club receptionist, never by a warp.
LINK_ROOMS = {"TRADE_CENTER": ("Cable Club", "Trade Center"), "COLOSSEUM": ("Cable Club", "Colosseum")}
# Indoor maps outside COMPLEXES whose place differs from the game's own Town Map group name, and what they resolve to.
GROUP_DIFFS = {"BILLS_HOUSE": "Route 25", "ROCK_TUNNEL_POKECENTER": "Route 10", "SAFARI_ZONE_GATE": "Fuchsia City",
               "LORELEIS_ROOM": "Indigo Plateau", "BRUNOS_ROOM": "Indigo Plateau", "AGATHAS_ROOM": "Indigo Plateau",
               "LANCES_ROOM": "Indigo Plateau", "CHAMPIONS_ROOM": "Indigo Plateau", "HALL_OF_FAME": "Indigo Plateau"}
# Ids that are left out, and why (the generator proves each list against the data).
GAME_KEYS = (("rb", "red"), ("y", "yellow"))


def derive(maps, edges, outdoor_names, group_names):
    """{map id: (place, detail)} and a report dict, from `edges` = {map const: [destination const or LAST_MAP]}."""
    byc = {m["const"]: m for m in maps}
    rev = defaultdict(set)
    for src, dests in edges.items():
        for d in dests:
            if d != "LAST_MAP":
                rev[d].add(src)
    in_complex = {c: (place, label) for place, members in COMPLEXES.items() for c, label in members.items()}

    def outdoor_parents(const):
        frontier, seen = {const}, {const}
        while frontier:
            found = {s for m in frontier for s in rev[m] if not byc[s]["indoor"]}
            if found:
                return sorted(found, key=lambda k: byc[k]["id"])
            frontier = {s for m in frontier for s in rev[m]} - seen
            seen |= frontier
        return []

    out, report = {}, dict(unused=[], copies=[], ties=[], group_diffs={}, unresolved=[])
    for m in maps:
        const = m["const"]
        if m["unused"]:
            report["unused"].append(m["id"])
            continue
        if not m["indoor"]:
            name = outdoor_names[m["id"]]
            out[m["id"]] = (name, name)
            continue
        if const in LINK_ROOMS:
            out[m["id"]] = LINK_ROOMS[const]
            continue
        if not rev[const]:
            report["copies"].append(const)          # no warp leads in: an unused copy of another map
            continue
        if const in in_complex:
            place, label = in_complex[const]
            out[m["id"]] = (place, place if not label else place + " " + label)
            continue
        parents = outdoor_parents(const)
        names = [outdoor_names[byc[p]["id"]] for p in parents]
        if not names:
            report["unresolved"].append(const)
            continue
        gname = group_names.get(m["group"])
        if len(names) == 1:
            place = names[0]
        else:
            place = gname if gname in names else names[0]
            report["ties"].append((const, names, place))
        if place != gname:
            report["group_diffs"][const] = place
        out[m["id"]] = (place, place)
    return out, report


def edges_from_disasm(d):
    return {const: [w[2] for w in rec["warps"]] for const, rec in d.objects().items()}


def edges_from_rom(d, rom):
    """The same edges, read from the map object data stored in the ROM (destination ids turned back into constants)."""
    const_of = {m["id"]: m["const"] for m in d.maps()}
    const_of[255] = "LAST_MAP"
    byc = d.map_by_const()
    return {const: [const_of[w[2]] for w in rom.map_objects(byc[const]["id"])["warps"]] for const in d.objects()}


def build_places(build):
    d = g.Disasm(build)
    outdoor, groups = d.town_names()
    return derive(d.maps(), edges_from_disasm(d), outdoor, groups), d


def render(rows_by_game, reports):
    r = reports["red"]
    unused = ", ".join(str(i) for i in r["unused"])
    lines = [
        "Nuzlocke areas for Generation 1, Red, Blue and Yellow (2026-09-29): which Nuzlocke area a map belongs to.",
        "",
        "One row per real map and game. rb is Red and Blue (they share every map id, name and warp), y is Yellow.",
        "Source: the pret disassemblies pokered and pokeyellow. constants/map_constants.asm gives the map ids, data/maps/",
        "names.asm and town_map_entries.asm the place names as the game spells them, data/maps/objects/*.asm each map's",
        "warps. The clean ROM dumps (red-u, blue-u, yellow-u) hold the same warps and wild tables: `python tools/nuzlocke/",
        "gen1_areas.py --check` reads them back, derives the places a second time from the ROM and compares.",
        "",
        "id: wCurMap in decimal (0..247; Yellow also has 248, the Summer Beach House, and its map 63 is Melanie's house",
        "instead of the Cerulean trade house).",
        "place: the Nuzlocke area a wild encounter or a gift on that map belongs to. A town, city or route is its own place",
        "(Sea Route 19..21 keep the game's name). An indoor map belongs to the outdoor map its warps lead out to: a",
        "house, a Pokemon Center, a Mart, a Gym or a gate is part of its town or route (a gate with two sides goes to the",
        "side the game's Town Map names). Bill's House is Route 25, the Rock Tunnel Pokemon Center is Route 10, the",
        "Safari Zone gate is Fuchsia City, the Game Corner rooms are Celadon City, the League rooms are Indigo Plateau.",
        "Every floor of a cave, tower, mansion or building complex shares one place: Viridian Forest, Mt. Moon, Rock",
        "Tunnel, Power Plant, Pokemon Tower, Pokemon Mansion, Seafoam Islands, Victory Road, Rocket Hideout, Silph Co.,",
        "Cerulean Cave, Diglett's Cave, S.S. Anne, Celadon Mansion and Underground Path (the two tunnels only; the four",
        "entrance houses are part of their routes). The Safari Zone is four places, Safari Zone Center, East, North and",
        "West, each with its rest house (the Secret House is in the West sector); merge them by the prefix if a ruleset",
        "wants one Safari Zone. The two link rooms are the place Cable Club.",
        "detail: the specific map for the every-floor-is-its-own-area switch (Mt. Moon B1F, Silph Co. 5F). It is the same",
        "as place for a map that is not a floor of a multi-floor place.",
        "Left out: the %d unused ids (the ROM's pointer for each is another map's header)," % len(r["unused"]),
        "  " + unused + ",",
        "and the maps nothing can warp into (unreachable copies of other maps):",
        "  " + ", ".join(r["copies"]) + ".",
        "Yellow leaves out the same ids and the same copies.",
        "",
        "game\tid\tplace\tdetail",
    ]
    out = g.header_block(lines[:-1]) + "# " + lines[-1] + "\n"
    for game, _ in GAME_KEYS:
        for mid in sorted(rows_by_game[game]):
            place, detail = rows_by_game[game][mid]
            out += "%s\t%d\t%s\t%s\n" % (game, mid, place, detail)
    return out


def generate():
    rows, reports = {}, {}
    for game, build in GAME_KEYS:
        (places, rep), _ = build_places(build)
        rows[game] = places
        reports[build] = rep
    return render(rows, reports), rows, reports


def check():
    errors = []

    def err(msg):
        errors.append(msg)
        print("MISMATCH:", msg)

    text, rows, reports = generate()
    if not OUT.exists():
        err("%s does not exist" % OUT)
    elif not g.compare_text(OUT, text):
        err("%s differs from what the disassembly derives (run the script without --check to rewrite it)" % OUT.name)
    # every derived place is plain ASCII with no tab
    for game in rows:
        for mid, (p, dt) in rows[game].items():
            for s in (p, dt):
                if not s.isascii() or "\t" in s:
                    err("bad characters in %s %d: %r" % (game, mid, s))

    summary = []
    for build in ("red", "blue", "yellow"):
        d = g.Disasm(build)
        rom = g.Rom(build)
        rom.verify_build()
        maps = d.maps()
        byc = d.map_by_const()
        outdoor, groups = d.town_names()
        # 1. the map table: every real map's ROM header has the size the constants file gives
        n_size = 0
        for m in maps:
            if m["unused"]:
                continue
            h = rom.map_header(m["id"])
            if (h["h"], h["w"]) != (m["h"], m["w"]):
                err("%s map %d %s: ROM size %dx%d, constants %dx%d" % (build, m["id"], m["const"], h["w"], h["h"], m["w"], m["h"]))
            n_size += 1
        real_ptrs = {rom.map_header_ptr(m["id"])[1] for m in maps if not m["unused"]}
        for m in maps:
            if m["unused"] and rom.map_header_ptr(m["id"])[1] not in real_ptrs:
                err("%s unused map %d has a header pointer of its own in the ROM" % (build, m["id"]))
        # 2. every warp of every map is the same in the ROM as in the disassembly
        n_warp = 0
        idmap = {m["const"]: m["id"] for m in maps}
        idmap["LAST_MAP"] = 255
        for const, rec in d.objects().items():
            want = [(x, y, idmap[dest], wid) for x, y, dest, wid in rec["warps"]]
            got = rom.map_objects(byc[const]["id"])["warps"]
            if want != got:
                err("%s %s: warps differ between disassembly and ROM" % (build, const))
            n_warp += len(want)
        # 3. the places derived from the ROM's warps equal the ones derived from the disassembly's
        (places_d, rep_d), _ = build_places(build)
        places_r, rep_r = derive(maps, edges_from_rom(d, rom), outdoor, groups)
        if places_d != places_r or rep_d != rep_r:
            err("%s: places derived from the ROM differ from those derived from the disassembly" % build)
        # 4. wild tables: identical maps, rates and slots in the ROM and in the disassembly, and every one has a place
        sp = d.species_consts()
        wild_d = {}
        for mid, rec in d.wild_by_map().items():
            t = {k: (rate, [(lvl, sp[s]) for lvl, s in slots]) for k, (rate, slots) in rec.items()}
            if t["grass"][0] or t["water"][0]:
                wild_d[mid] = t
        wild_r = rom.wild_tables(len(maps))
        if wild_d != wild_r:
            err("%s: wild tables differ between the disassembly and the ROM" % build)
        for mid in wild_d:
            if mid not in places_d:
                err("%s: map %d has a wild table but no place" % (build, mid))
        # the fishing places too: the Super Rod maps, and the fish of each, are the same in the ROM
        rod_d = {byc[c]["id"]: [(lvl, sp[s]) for lvl, s in fish] for c, fish in d.super_rod().items()}
        if rod_d != rom.super_rod():
            err("%s: Super Rod tables differ between the disassembly and the ROM" % build)
        for mid in rod_d:
            if mid not in places_d:
                err("%s: Super Rod map %d has no place" % (build, mid))
        # 5. the shipped file holds exactly these places for this build's game key
        game = g.GAME_OF[build]
        shipped = {}
        if OUT.exists():
            for line in g.read_text(OUT).splitlines():
                if line and not line.startswith("#"):
                    gm, i, p, dt = line.split("\t")
                    if gm == game:
                        shipped[int(i)] = (p, dt)
        if shipped != places_r:
            err("%s: shipped rows for %s differ from the places derived from the ROM" % (build, game))
        # 6. the group-name differences are exactly the documented ones
        if rep_d["group_diffs"] != GROUP_DIFFS:
            err("%s: maps whose place differs from the Town Map group are %s, expected %s" % (build, rep_d["group_diffs"], GROUP_DIFFS))
        if rep_d["unresolved"]:
            err("%s: maps with no outdoor parent: %s" % (build, rep_d["unresolved"]))
        summary.append("%s: %d map headers sized, %d warps, %d wild-table maps, %d Super Rod maps, %d rows, %d ties %s" % (
            build, n_size, n_warp, len(wild_d), len(rod_d), len(places_r), len(rep_d["ties"]),
            [t[0] + "->" + t[2] for t in rep_d["ties"]]))
    # Red and Blue share every row; Yellow leaves out the same unused ids and copies
    if reports["red"]["unused"] != reports["yellow"]["unused"] or reports["red"]["copies"] != reports["yellow"]["copies"]:
        err("Yellow leaves out different maps than Red")
    pb = build_places("blue")[0][0]
    if pb != rows["rb"]:
        err("Blue derives different places than Red")
    print("\n".join(summary))
    print("ROM copies left out:", reports["red"]["copies"], "unused ids:", len(reports["red"]["unused"]))
    if errors:
        print("FAILED: %d mismatch(es)" % len(errors))
        return 1
    print("OK: areas-gen1.tsv matches the disassemblies and the Red, Blue and Yellow ROM dumps")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="verify the shipped file against the sources, do not write")
    ap.add_argument("--stdout", action="store_true", help="print the file instead of writing it")
    a = ap.parse_args()
    if a.check:
        return check()
    text, _, _ = generate()
    if a.stdout:
        sys.stdout.write(text)
    else:
        g.write_lf(OUT, text)
        print("wrote", OUT, "(%d lines)" % text.count("\n"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
