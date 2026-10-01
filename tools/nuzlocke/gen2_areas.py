"""Generate (and check) tracker-gba/src/main/resources/nuzlocke/areas-gen2.tsv (2026-09-29).

    python tools/nuzlocke/gen2_areas.py            # write the file (LF; then run to_crlf.py)
    python tools/nuzlocke/gen2_areas.py --check    # rebuild, compare with the shipped file, and read the map headers
                                                   # and the landmark tables back out of the Gold and Crystal dumps
    python tools/nuzlocke/gen2_areas.py --report   # print the notes the Kotlin side needs (gates, special maps, regions)

Rows: game, id, place, detail.
  gs: id = wMapGroup * 256 + wMapNumber (both 1-based, as the game stores them); place = the LANDMARK the map's header
      names (data/maps/maps.asm, the 4th argument of the map macro), so every floor of a cave and every building of a
      town share one place; detail = the map itself from constants/map_constants.asm.
  c : id = wCurLandmark (the landmark number itself); place = the landmark name; detail = the same.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen2_common as C  # noqa: E402

OUT = os.path.join(C.NZ, "areas-gen2.tsv")

# ---------------------------------------------------------------------------------- name helpers
WORDS = {
    "POKECENTER": "Pokemon Center", "MOUNT": "Mt.", "DEPT": "Dept.", "OF": "of", "MR": "Mr.",
    "TIMS": "Tim's", "EMYS": "Emy's", "KURTS": "Kurt's", "ELMS": "Elm's", "REDS": "Red's", "BLUES": "Blue's",
    "OAKS": "Oak's", "WILLS": "Will's", "KOGAS": "Koga's", "BRUNOS": "Bruno's", "KARENS": "Karen's",
    "LANCES": "Lance's", "BILLS": "Bill's", "EARLS": "Earl's", "COPYCATS": "Copycat's", "FUJIS": "Fuji's",
    "POKEMONS": "Pokemon's", "PSYCHICS": "Psychic's", "MANIAS": "Mania's", "DELETERS": "Deleter's",
    "WARDENS": "Warden's", "PLAYERS": "Player's", "NEIGHBORS": "Neighbor's", "SISTERS": "Sister's",
    "CAPTAINS": "Captain's", "DIGLETTS": "Diglett's", "FAMILYS": "Family's", "PP": "PP",
    "DRAGONS": "Dragon's", "KYLES": "Kyle's", "GENTS": "Gent's",
    "SILPH": "Silph", "CO": "Co.", "TM": "TM", "NNW": "NNW", "NNE": "NNE", "NE": "NE", "SW": "SW", "SSW": "SSW",
    "NW": "NW", "SE": "SE", "SSE": "SSE", "NW": "NW",
}
FLOOR = re.compile(r"^B?\d+F$")
# Map constants whose plain rendering would be wrong or unclear.
DETAIL = {
    "NATIONAL_PARK_BUG_CONTEST": "National Park Bug-Catching Contest",
    "WHIRL_ISLAND_NW": "Whirl Islands NW", "WHIRL_ISLAND_NE": "Whirl Islands NE", "WHIRL_ISLAND_SW": "Whirl Islands SW",
    "WHIRL_ISLAND_CAVE": "Whirl Islands Cave", "WHIRL_ISLAND_SE": "Whirl Islands SE", "WHIRL_ISLAND_B1F": "Whirl Islands B1F",
    "WHIRL_ISLAND_B2F": "Whirl Islands B2F", "WHIRL_ISLAND_LUGIA_CHAMBER": "Whirl Islands Lugia Chamber",
    "RUINS_OF_ALPH_HO_OH_CHAMBER": "Ruins of Alph Ho-Oh Chamber",
    "GOLDENROD_DEPT_STORE_B1F": "Goldenrod Dept. Store B1F",
    "GOLDENROD_PP_SPEECH_HOUSE": "Goldenrod PP Speech House",
    "MOUNT_MORTAR_1F_OUTSIDE": "Mt. Mortar 1F Outside", "MOUNT_MORTAR_1F_INSIDE": "Mt. Mortar 1F Inside",
    "MOUNT_MORTAR_2F_INSIDE": "Mt. Mortar 2F Inside", "MOUNT_MORTAR_B1F": "Mt. Mortar B1F",
    "SAFARI_ZONE_MAIN_OFFICE": "Safari Zone Main Office", "SAFARI_ZONE_WARDENS_HOME": "Safari Zone Warden's Home",
    "BILLS_FAMILYS_HOUSE": "Bill's Family's House", "BILLS_OLDER_SISTERS_HOUSE": "Bill's Older Sister's House",
    "PLAYERS_NEIGHBORS_HOUSE": "Player's Neighbor's House", "DAY_OF_WEEK_SIBLINGS_HOUSE": "Day of Week Siblings House",
    "MR_POKEMONS_HOUSE": "Mr. Pokemon's House", "MR_PSYCHICS_HOUSE": "Mr. Psychic's House",
    "FAST_SHIP_CABINS_NNW_NNE_NE": "Fast Ship Cabins NNW NNE NE", "FAST_SHIP_CABINS_SW_SSW_NW": "Fast Ship Cabins SW SSW NW",
    "FAST_SHIP_CABINS_SE_SSE_CAPTAINS_CABIN": "Fast Ship Cabins SE SSE Captain's Cabin",
    "UNDERGROUND_PATH": "Underground Path",
}
# Landmark names as the game shows them, in the text a sign or the Pokegear map prints, made readable.
LANDMARK_FIX = {"MT.MORTAR": "Mt. Mortar", "MT.MOON": "Mt. Moon"}


def landmark_title(text):
    """A landmark name string from data/maps/landmarks.asm ('NEW BARK<BSP>TOWN') as Title Case ASCII."""
    t = text.replace("<BSP>", " ")
    if t in LANDMARK_FIX:
        return LANDMARK_FIX[t]
    words = []
    for w in t.split(" "):
        if w == "OF":
            words.append("of")
        else:
            words.append(w[:1] + w[1:].lower())
    return " ".join(words)


def pretty_map(const):
    if const in DETAIL:
        return DETAIL[const]
    out = []
    for tok in const.split("_"):
        if FLOOR.match(tok):
            out.append(tok)
        elif tok in WORDS:
            out.append(WORDS[tok])
        else:
            out.append(tok.capitalize())
    return " ".join(out)


# ---------------------------------------------------------------------------------- source parsers
def parse_map_constants(root):
    """[(group_no, map_no, MAP_CONST)] from constants/map_constants.asm (newgroup starts a group, map_const a map)."""
    out = []
    gno = 0
    mno = 0
    for raw in C.lines_of(root + "/constants/map_constants.asm"):
        line = C.strip_comment(raw)
        if re.match(r"^newgroup\s+\w+", line):
            gno += 1
            mno = 0
            continue
        m = re.match(r"^map_const\s+(\w+),", line)
        if m:
            mno += 1
            out.append((gno, mno, m.group(1)))
    return out


def parse_map_headers(root):
    """[(group_no, map_no, CamelName, environment, LANDMARK const, FISHGROUP const)] from data/maps/maps.asm, in constants order."""
    order = []
    text = C.lines_of(root + "/data/maps/maps.asm")
    for raw in text:
        m = re.match(r"^dw\s+MapGroup_(\w+)$", C.strip_comment(raw))
        if m:
            order.append(m.group(1))
    out = []
    cur = None
    mno = 0
    for raw in text:
        line = C.strip_comment(raw)
        m = re.match(r"^MapGroup_(\w+):$", line)
        if m:
            cur = order.index(m.group(1)) + 1
            mno = 0
            continue
        m = re.match(r"^map\s+(\w+),\s*(\w+),\s*(\w+),\s*(\w+),\s*[^,]+,\s*(\w+),\s*(\w+),\s*(\w+)$", line)
        if m and cur:
            mno += 1
            out.append((cur, mno, m.group(1), m.group(3), m.group(4), m.group(7)))
    return out


def parse_landmarks(root):
    """({LANDMARK const: id}, {id: title-case name}) for one game (ids from constants/landmark_constants.asm, names
    from data/maps/landmarks.asm in table order, which the game indexes by the same id)."""
    ids = {}
    n = 0
    for raw in C.lines_of(root + "/constants/landmark_constants.asm"):
        line = C.strip_comment(raw)
        if line.startswith("const_def"):
            if ids:
                break          # the second const_def block (CaughtData's LANDMARK_EVENT and LANDMARK_GIFT) is not a landmark
            n = 0
            continue
        m = re.match(r"^const\s+(LANDMARK_\w+)$", line)
        if m:
            ids[m.group(1)] = n
            n += 1
    labels = []
    strings = {}
    for raw in C.lines_of(root + "/data/maps/landmarks.asm"):
        m = re.match(r"^\s*landmark\s+-?\d+,\s*-?\d+,\s*(\w+)", raw)
        if m:
            labels.append(m.group(1))
        m = re.match(r'^(\w+Name):\s*db\s+"([^"]*)@"', raw)
        if m:
            strings[m.group(1)] = m.group(2)
    if len(labels) != len(ids):
        C.die("%s: %d landmark table rows but %d landmark constants" % (root, len(labels), len(ids)))
    raw_names = {i: strings[lab] for i, lab in enumerate(labels)}
    return ids, {i: landmark_title(t) for i, t in raw_names.items()}, raw_names


def game_maps(game):
    """Every map of a game: [dict(id, group, no, const, camel, env, landmark_const, landmark, place, detail, skip)]."""
    root = C.REPO[game]
    consts = parse_map_constants(root)
    heads = parse_map_headers(root)
    if len(consts) != len(heads):
        C.die("%s: %d map constants but %d map headers" % (game, len(consts), len(heads)))
    lm_ids, lm_names, _raw = parse_landmarks(root)
    out = []
    for (g, n, const), (g2, n2, camel, env, lm, fishgroup) in zip(consts, heads):
        if (g, n) != (g2, n2):
            C.die("%s: map constants and headers disagree at group %d map %d" % (game, g, n))
        skip = None
        if const.endswith("_BETA"):
            skip = "unused beta map"
        elif lm == "LANDMARK_SPECIAL":
            skip = "special map (link and mobile rooms)"
        lid = lm_ids[lm]
        out.append({"id": g * 256 + n, "group": g, "no": n, "const": const, "camel": camel, "env": env, "landmark_const": lm,
                    "landmark": lid, "place": lm_names[lid], "detail": pretty_map(const), "skip": skip, "fishgroup": fishgroup})
    return out


# ---------------------------------------------------------------------------------- rows
HEADER = """# Where the player is, for the Nuzlocke area a wild encounter belongs to, Generation 2 (2026-09-29).
#
# game: gs = Gold and Silver, c = Crystal.
#   gs: id = wMapGroup * 256 + wMapNumber (GbcTracker reads both bytes; group and map number are 1-based as the game
#       stores them, so Falkner's gym is group 10, map 7 = 2567). place = the landmark that map's header names
#       (pokegold data/maps/maps.asm, 4th argument of the map macro; names from data/maps/landmarks.asm), which is the
#       town or route a house, gate, cave floor or tower floor belongs to, exactly as the game itself files it: every floor
#       of Union Cave, Sprout Tower or Tin Tower and every building of a town share one place. detail = the map itself
#       (constants/map_constants.asm). Gold and Silver use one map table.
#   c : id = wCurLandmark, the landmark number (pokecrystal constants/landmark_constants.asm: Johto 1..46, Kanto 47..94,
#       Fast Ship 95). place = the landmark name (data/maps/landmarks.asm, the game's own text in Title Case: the sign
#       prints SILVER CAVE for Mt. Silver, RADIO TOWER for the Goldenrod one, POWER PLANT, UNDERGROUND and FAST SHIP for the
#       S.S. Aqua); detail is the same.
#       wCurLandmark is 0 (LANDMARK_SPECIAL) in the link and mobile rooms and 0xFF inside any map of the GATE environment
#       (gates, both Underground Path entrances, the Underground Path itself, Victory Road Gate, the Battle Tower gate) and
#       both National Park gates: those are not places, so a tracker keeps the last real landmark. There is no row for 0 or 255.
#       Crystal has one landmark Gold and Silver lack: Battle Tower (Crystal 29), so Crystal's numbers from 29 up are one
#       higher than Gold's landmark ids; the two tables are separate on purpose.
#   Maps without a gs row: the thirteen unused beta maps (ten Pokemon Center 2F betas that no warp leads to, Olivine House
#   Beta, Safari Zone Beta and its gate, whose only warps are marked inaccessible) and the four link rooms (Pokemon Center
#   2F, Trade Center, Colosseum, Time Capsule), whose landmark is SPECIAL: a tracker keeps the last real map for those.
# Kanto and Johto share these tables. Regions by landmark: Johto is landmark 1..46 (Gold 1..45), Kanto 47..94 (Gold 46..93),
#   and the Fast Ship 95 (Gold 94); the game's own RegionCheck counts Victory Road, Route 23, Indigo Plateau, Routes 26 to 28
#   and Tohjo Falls as Johto for music, which is not a geographic split.
#
# game	id	place	detail
"""


def build_rows():
    rows = []
    for m in game_maps("gs"):
        if not m["skip"]:
            rows.append(("gs", m["id"], m["place"], m["detail"]))
    rows.sort(key=lambda r: r[1])
    _ids, names, _raw = parse_landmarks(C.REPO["c"])
    for lid in sorted(names):
        if lid != 0:
            rows.append(("c", lid, names[lid], names[lid]))
    return rows


def render(rows):
    return HEADER + "".join("\t".join(str(x) for x in r) + "\n" for r in rows)


def generate():
    rows = build_rows()
    C.write_lf(OUT, render(rows))
    print("wrote %s: %d rows (%d gs, %d c)" % (OUT, len(rows), sum(1 for r in rows if r[0] == "gs"), sum(1 for r in rows if r[0] == "c")))


# ---------------------------------------------------------------------------------- ROM readers
ENV = {"TOWN": 1, "ROUTE": 2, "INDOOR": 3, "CAVE": 4, "ENVIRONMENT_5": 5, "GATE": 6, "DUNGEON": 7}


def rom_map_headers(rom, game, counts):
    """{(group, map): (environment byte, location byte)} read from the ROM: MapGroupPointers is a table of 26
    bank-local pointers at the randomizer's MapHeaders offset, each pointing at that group's array of 9-byte headers
    (bank, tileset, environment, attributes pointer, location, music, time and phone, fishing group)."""
    ini = C.ini_section("Gold (U)" if game == "gs" else "Crystal (U)")
    table = int(ini["MapHeaders"], 16)
    bank_base = (table // 0x4000) * 0x4000
    n_groups = len(counts)
    ptrs = [rom[table + 2 * i] | (rom[table + 2 * i + 1] << 8) for i in range(n_groups)]
    out = {}
    for g in range(1, n_groups + 1):
        start = bank_base + ptrs[g - 1] - 0x4000
        for n in range(1, counts[g] + 1):
            h = start + 9 * (n - 1)
            out[(g, n)] = (rom[h + 2], rom[h + 5])
    return out, ptrs, bank_base


def rom_landmark_names(rom, game):
    """{id: name bytes} from the ROM's Landmarks table (x, y, 16-bit pointer per landmark; the randomizer's
    LandmarkTableOffset and LandmarkCount), each name a string ending in 0x50."""
    ini = C.ini_section("Gold (U)" if game == "gs" else "Crystal (U)")
    table = int(ini["LandmarkTableOffset"], 16)
    count = int(ini["LandmarkCount"])
    bank_base = (table // 0x4000) * 0x4000
    out = {}
    for i in range(count):
        ptr = rom[table + 4 * i + 2] | (rom[table + 4 * i + 3] << 8)
        p = bank_base + ptr - 0x4000
        e = p
        while rom[e] != 0x50:
            e += 1
        out[i] = bytes(rom[p:e])
    return out


EXPECTED_DEVIATIONS = {
    "RADIO_TOWER_1F": "Radio Tower is its own landmark (Goldenrod's warps lead to Goldenrod City)",
    "LAV_RADIO_TOWER_1F": "Lav Radio Tower is its own landmark",
    "POWER_PLANT": "Power Plant is its own landmark",
    "SEAFOAM_GYM": "Blaine's gym is filed under Seafoam Islands, its warps lead to Route 20",
    "INDIGO_PLATEAU_POKECENTER_1F": "the League's Pokecenter is Indigo Plateau, its door opens on the Route 23 map",
    "ILEX_FOREST_AZALEA_GATE": "the game's header says Route 34 for this gate, which stands between Azalea Town and Ilex Forest",
}


def interior_deviations(root, maps):
    """{map const: note} for every INDOOR or GATE map whose header landmark is not the landmark of an outdoor map
    (town or route) that its own warps lead to."""
    by_const = {m["const"]: m for m in maps}
    out = set()
    for m in maps:
        if m["skip"] or m["env"] not in ("INDOOR", "GATE"):
            continue
        text = C.read_text("%s/maps/%s.asm" % (root, m["camel"]))
        warps = re.findall(r"^\s*warp_event\s+[^,]+,\s*[^,]+,\s*(\w+)\s*,", text, re.M)
        outdoor = [w for w in warps if w in by_const and by_const[w]["env"] in ("TOWN", "ROUTE")]
        if outdoor and m["landmark"] not in {by_const[w]["landmark"] for w in outdoor}:
            out.add(m["const"])
    return {k: EXPECTED_DEVIATIONS.get(k, "unexpected") for k in out}


def check():
    probs = C.Problems()
    rows = build_rows()
    shipped = [tuple(r) for r in C.data_rows(OUT)]
    probs.check([tuple(str(x) for x in r) for r in rows] == shipped, "the shipped file differs from what the generator builds")
    probs.check(C.clean_crlf_ascii(OUT), "the shipped file is not clean CRLF ASCII (run to_crlf.py)")
    probs.check([l for l in C.lines_of(OUT) if l.startswith("#")] == [l for l in HEADER.split("\n") if l.startswith("#")],
                "header comment differs")
    gs_ids = [int(r[1]) for r in shipped if r[0] == "gs"]
    c_ids = [int(r[1]) for r in shipped if r[0] == "c"]
    probs.check(len(gs_ids) == len(set(gs_ids)) and len(c_ids) == len(set(c_ids)), "duplicate ids")
    probs.check(c_ids == list(range(1, 96)), "Crystal rows are not landmarks 1..95")

    for game in ("gs", "c"):
        rom = C.load_rom(game)
        root = C.REPO[game]
        maps = game_maps(game)
        counts = {}
        for m in maps:
            counts[m["group"]] = counts.get(m["group"], 0) + 1
        rom_heads, ptrs, bank_base = rom_map_headers(rom, game, counts)
        # the ROM's group table agrees with the source's group sizes
        for g in range(1, len(counts)):
            probs.check((ptrs[g] - ptrs[g - 1]) == 9 * counts[g], "%s group %d: ROM holds %d bytes of headers, source has %d maps" % (
                game, g, ptrs[g] - ptrs[g - 1], counts[g]))
        lm_ids, _lm_names, raw = parse_landmarks(root)
        for m in maps:
            env, loc = rom_heads[(m["group"], m["no"])]
            probs.check(loc == m["landmark"], "%s %s: ROM header location %d, source %d" % (game, m["const"], loc, m["landmark"]))
            probs.check(env == ENV[m["env"]], "%s %s: ROM environment %d, source %s" % (game, m["const"], env, m["env"]))
        # the landmark names in the ROM are the disassembly's strings
        charmap = C.Charmap(root)
        rom_names = rom_landmark_names(rom, game)
        probs.check(len(rom_names) == len(raw), "%s: ROM has %d landmarks, source %d" % (game, len(rom_names), len(raw)))
        for i, text in raw.items():
            probs.check(rom_names.get(i) == charmap.encode(text), "%s landmark %d: name bytes differ from %r" % (game, i, text))
        probs.check(sum(1 for m in maps if m["env"] == "GATE") == sum(1 for v in rom_heads.values() if v[0] == 6),
                    "%s: GATE map count differs between source and ROM" % game)
        # shipped rows for this game equal the source (gs) or the landmark table (c)
        if game == "gs":
            want = {m["id"]: (m["place"], m["detail"]) for m in maps if not m["skip"]}
            got = {int(r[1]): (r[2], r[3]) for r in shipped if r[0] == "gs"}
            probs.check(want == got, "gs rows differ from the source maps")
            # every skipped map is skipped for a stated reason and nothing else is missing
            skipped = [m["const"] for m in maps if m["skip"]]
            probs.check(len(skipped) == 17, "expected 13 beta maps and 4 special maps to be skipped, got %d: %s" % (len(skipped), skipped))
        # A building, gate or cave floor belongs to the town or route its warps lead out to; the header agrees for every
        # interior map except the ones EXPECTED_DEVIATIONS names: five are places of their own (a header names the
        # tower, plant or plateau) and one is a mistake in the game's data. Any other disagreement fails the check.
        if game == "gs":
            probs.check(interior_deviations(root, maps) == EXPECTED_DEVIATIONS,
                        "interior maps whose header landmark differs from their outdoor warps: %s" % sorted(interior_deviations(root, maps)))
    probs.finish("gen2_areas --check")


def report():
    """The facts the notes need: which maps read 0xFF or 0 as Crystal's landmark, and the regions."""
    for game in ("gs", "c"):
        maps = game_maps(game)
        gates = [m["const"] for m in maps if m["env"] == "GATE"]
        special = [m["const"] for m in maps if m["landmark_const"] == "LANDMARK_SPECIAL"]
        print("%s: %d maps, GATE environment %d, SPECIAL landmark %d" % (game, len(maps), len(gates), len(special)))
        print("  GATE:", ", ".join(gates))
        print("  SPECIAL:", ", ".join(special))
        lm_ids, names, _raw = parse_landmarks(C.REPO[game])
        print("  landmarks: %d (0 = SPECIAL); Johto 1..%d, Kanto %d..%d, other %d" % (
            len(lm_ids), lm_ids["LANDMARK_SILVER_CAVE"], lm_ids["LANDMARK_PALLET_TOWN"], lm_ids["LANDMARK_ROUTE_28"],
            lm_ids["LANDMARK_FAST_SHIP"]))


if __name__ == "__main__":
    if sys.argv[1:2] == ["--check"]:
        check()
    elif sys.argv[1:2] == ["--report"]:
        report()
    else:
        generate()
