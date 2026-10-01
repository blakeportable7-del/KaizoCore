"""Writes tracker-gba/src/main/resources/nuzlocke/statics-gen5.tsv (Generation 5: Black, White, Black 2, White 2).

    python tools/nuzlocke/gen5_statics.py           write the file (LF endings; run to_crlf.py after)
    python tools/nuzlocke/gen5_statics.py --check   re-read the ROMs and compare with the shipped file

A static is a wild battle that is not the first encounter of its area: a legendary or a weekday Pokemon standing in the
overworld, a Zen Mode statue, an item ball that turns out to be a Pokemon. Each row is one (game, place, level).

How a row was made, and what --check re-reads:
  * The Universal Pokemon Randomizer ZX lists where every static of these games keeps its species and level
    (engine-zx config/gen5_offsets.ini, StaticPokemon and StaticPokemonFakeBall lines, the sections Black (U) and
    Black 2 (U); White and White 2 copy them). The species is a u16 and the level a byte inside a script file
    (Scripts archive) or, for the item balls that are Pokemon, a map file (MapFiles archive).
  * The place is the area name the DS tracker shows. The tracker looks up the map it is in, and then that map's
    parent, in gen5/locations-bw.tsv or locations-b2w2.tsv. The ROM's zone headers (48 bytes, MapTableFile archive)
    give both: the u16 at +6 is the zone's script file, the u16 at +22 its map file, the u16 at +24 its parent zone
    (the zone itself for an outdoor map). So a static in script S belongs to the zone whose +6 field is S, and its
    area is table[zone] or, if the zone is not in the table, table[parent(zone)]. Item balls use +22 with the map file.
  * The reader (NuzlockeStatics) matches a wild battle to a row by place and level, and by species when the row has a
    sixth column. A row is dropped when an ordinary encounter of that area could be taken for it: reason "same"
    when the same species stands in the area's ordinary wild tables at that level (nothing tells the static from
    the ordinary encounter). Reason "land" is when any other species does in a land table (grass, dark grass or
    shaking grass): such a row is kept with the species' national dex number as its sixth column, so an ordinary
    encounter of another species is not taken for it. Only water tables (surf and fishing, which have wide level
    ranges) may share a level with a kept row. The tables are the WildPokemon archive, all four seasons of every
    zone the tracker calls that area.
Black has no dump on the machine: the Black and White numbers are read from a White (US) ROM, with the offsets the
randomizer gives for Black; both games carry the same static levels in the research doc (section 4.3).
Black 2 and White 2 are both read, and must agree.
Gifts, eggs, fossils, trades and roamers are not battles or have no fixed place and are not here.
"""
import re
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen5_common as C
import gen5_rom as R

OUT = C.OUT_DIR / "statics-gen5.tsv"
INI = C.ROOT / "engine-zx" / "src" / "com" / "dabomstew" / "pkrandomzx" / "config" / "gen5_offsets.ini"

# Each row: game, place, level, species text, note, and the (ini entry name, level index) pairs that stand behind it.
# The place, level and species are what --check finds in the ROM; the note is the only part written by hand.
S = "StaticPokemon"


def row(game, place, level, species, note, *src, skip=""):
    """skip: "" keeps the row; "same" drops a real static whose species also stands in the area's ordinary wild
    tables at that level; "land" drops one whose level an ordinary land encounter (grass, dark grass, shaking grass)
    of another species also has."""
    return dict(game=game, place=place, level=level, species=species, note=note, src=list(src), skip=skip)


BW_ROWS = [
    row("bw", "Desert Resort", 35, "Darmanitan",
        "Five Zen Mode statues at the Desert Resort end of Relic Castle; each is a battle once given a Rage Candy Bar",
        *[("Darmanitan %d" % k, 0) for k in range(1, 6)]),
    row("bw", "Dreamyard", 50, "Musharna",
        "Fridays only, in the basement, after the credits",
        ("Musharna", 0), skip="land"),
    row("bw", "Relic Castle", 70, "Volcarona", "Deepest floor, after the credits", ("Volcarona", 0)),
    row("bw", "Liberty Garden", 15, "Victini", "Needs the Liberty Pass event item, so not reachable in a normal game", ("Victini", 0)),
    row("bw", "N's Castle", 50, "Reshiram or Zekrom",
        "The legendary you must catch before N's battle: Reshiram in Black, Zekrom in White",
        ("Reshiram", 1), ("Zekrom", 1)),
    row("bw", "Dragonspiral Tower", 50, "Reshiram or Zekrom",
        "The same legendary waits at the top of the tower if it was not caught in N's Castle",
        ("Reshiram", 0), ("Zekrom", 0)),
    row("bw", "Mistralton Cave", 42, "Cobalion", "Guidance Chamber", ("Cobalion", 0)),
    row("bw", "Victory Road", 42, "Terrakion", "Trial Chamber, after Cobalion", ("Terrakion", 0), skip="land"),
    row("bw", "Pinwheel Exterior", 42, "Virizion",
        "Rumination Field; the tracker shows this area as Pinwheel Exterior", ("Virizion", 0)),
    row("bw", "Abundant Shrine", 70, "Landorus", "After the credits; needs Tornadus and Thundurus in the party", ("Landorus", 0)),
    row("bw", "Giant Chasm", 75, "Kyurem", "After the credits", ("Kyurem", 0)),
    row("bw", "Route 6", 20, "Foongus", "Item balls that are Foongus, a wild battle when picked up", ("Foongus", 0), ("Foongus", 1)),
    row("bw", "Route 10", 30, "Foongus", "Item balls that are Foongus", ("Foongus", 2), ("Foongus", 3)),
    row("bw", "Route 10", 40, "Amoonguss", "Item balls that are Amoonguss", ("Amoonguss", 0), ("Amoonguss", 1), skip="same"),
]

B2W2_ROWS = [
    row("b2w2", "Route 13", 45, "Cobalion", "Story", ("Cobalion", 0)),
    row("b2w2", "Route 13", 65, "Cobalion", "Comes back after the Hall of Fame at a higher level", ("Cobalion", 1)),
    row("b2w2", "Route 11", 45, "Virizion", "Story", ("Virizion", 0)),
    row("b2w2", "Route 11", 65, "Virizion", "Comes back after the Hall of Fame at a higher level", ("Virizion", 1)),
    row("b2w2", "Route 22", 45, "Terrakion", "Story; after the Wave Badge and a Colress scene", ("Terrakion", 0), skip="land"),
    row("b2w2", "Route 22", 65, "Terrakion", "Comes back after the Hall of Fame at a higher level", ("Terrakion", 1)),
    row("b2w2", "Dragonspiral Tower", 70, "Reshiram or Zekrom",
        "After the credits, with N in the ruins of his castle: Zekrom in Black 2, Reshiram in White 2",
        ("Reshiram", 0), ("Zekrom", 0)),
    row("b2w2", "Giant Chasm", 70, "Kyurem", "After the credits, once N's dragon is caught", ("Kyurem", 0)),
    row("b2w2", "Giant Chasm", 55, "Kyurem", "The fused Kyurem of the story (Black Kyurem or White Kyurem); it cannot be caught",
        ("Kyurem-Black", 0), ("Kyurem-White", 0)),
    row("b2w2", "Dreamyard", 68, "Latios or Latias", "After the credits; Latios in Black 2, Latias in White 2; it flees and must be chased",
        ("Latias", 0), ("Latios", 0)),
    row("b2w2", "Nacrene City", 65, "Uxie", "After the Cave of Being scene; in front of the museum", ("Uxie", 0)),
    row("b2w2", "Celestial Tower", 65, "Mesprit", "After the Cave of Being scene; on the roof", ("Mesprit", 0)),
    row("b2w2", "Route 23", 65, "Azelf", "After the Cave of Being scene", ("Azelf", 0)),
    row("b2w2", "Clay Tunnel", 65, "Regirock, Regice or Registeel",
        "The three chambers of the Underground Ruins, which the tracker shows as Clay Tunnel; Regice and Registeel need a key from the other version",
        ("Regirock", 0), ("Regice", 0), ("Registeel", 0)),
    row("b2w2", "Twist Mountain", 68, "Regigigas", "Needs Regirock, Regice and Registeel in the party", ("Regigigas", 0)),
    row("b2w2", "Marvelous Bridge", 68, "Cresselia", "Needs the Lunar Wing from the Strange House; the bridge is post-game", ("Cresselia", 0)),
    row("b2w2", "Reversal Mountain", 68, "Heatran", "Needs the Magma Stone from a cliff on Route 18", ("Heatran", 0), ("Heatran", 1)),
    row("b2w2", "Route 4", 25, "Mandibuzz or Braviary",
        "Behind a house, on one weekday only: Thursday in Black 2 (Mandibuzz), Monday in White 2 (Braviary)",
        ("Mandibuzz", 0), ("Braviary", 0)),
    row("b2w2", "Relic Castle", 35, "Volcarona", "Lowest floor, after the Quake Badge", ("Volcarona", 0)),
    row("b2w2", "Relic Castle", 65, "Volcarona", "Comes back after the Hall of Fame at a higher level", ("Volcarona", 1)),
    row("b2w2", "Seaside Cave", 42, "Crustle", "Blocks the way to the Plasma Frigate until Colress wakes it", ("Crustle", 0), skip="land"),
    row("b2w2", "Undella Bay", 40, "Jellicent", "One weekday only: Monday (male) in Black 2, Thursday (female) in White 2", ("Jellicent", 0), ("Jellicent", 1), skip="same"),
    row("b2w2", "Nature Preserve", 60, "Haxorus", "Shiny; needs the Permit from Professor Juniper, given after every Unova Pokemon has been seen", ("Shiny Haxorus", 0)),
    row("b2w2", "Route 6", 29, "Foongus", "Item balls that are Foongus", ("Foongus", 0), ("Foongus", 1), ("Foongus", 2), skip="same"),
    row("b2w2", "Route 7", 36, "Foongus", "Item balls that are Foongus", ("Foongus", 3), ("Foongus", 4), skip="same"),
    row("b2w2", "Route 11", 43, "Amoonguss", "Item balls that are Amoonguss", ("Amoonguss", 0), ("Amoonguss", 1), skip="land"),
    row("b2w2", "Route 22", 47, "Amoonguss", "Item balls that are Amoonguss", ("Amoonguss", 2), ("Amoonguss", 3), skip="land"),
    row("b2w2", "Route 23", 56, "Amoonguss", "Item balls that are Amoonguss", ("Amoonguss", 4), ("Amoonguss", 5), ("Amoonguss", 6), skip="land"),
]

GAMES = {
    # game key: (ini section, family, ROM keys to read (all must agree))
    "bw": ("Black (U)", "bw", ["white"]),
    "b2w2": ("Black 2 (U)", "b2w2", ["black2", "white2"]),
}
ROWS = BW_ROWS + B2W2_ROWS
LAND_TABLES = ("grass", "doubles", "shaking")


# ------------------------------------------------------------------ the ini

def parse_list(body, key):
    m = re.search(key + r"=\[([^\]]*)\]", body)
    out = []
    if m:
        for tok in m.group(1).split(","):
            tok = tok.strip()
            if tok:
                a, b = tok.split(":")
                out.append((int(a, 0), int(b, 0)))
    return out


def read_ini_statics(section):
    text = INI.read_text(encoding="utf-8")
    body = None
    for s in re.split(r"\n(?=\[)", text):
        if s.startswith("[" + section + "]"):
            body = s
    entries = {}
    for line in body.splitlines():
        m = re.match(r"^(StaticPokemon|StaticPokemonFakeBall)\{\}=\{(.*)\}\s*//\s*(.*)$", line)
        if m:
            name = m.group(3).strip()
            entries.setdefault(name, dict(kind=m.group(1), species=parse_list(m.group(2), "Species"), level=parse_list(m.group(2), "Level")))
    return entries


# ------------------------------------------------------------------ the ROM side

class GameData:
    """One ROM of a game key with the tables the statics need."""

    def __init__(self, rom_key, family):
        self.key = rom_key
        self.family = family
        self.rom = R.open_rom(rom_key)
        self.ok = self.rom is not None
        if not self.ok:
            return
        self.scripts = R.read_scripts(self.rom, family)
        self.mapfiles = self.rom.narc(R.NARC_PATHS[family]["MapFiles"])
        self.zones = R.read_zone_headers(self.rom, family)
        self.wild = self.rom.narc(R.NARC_PATHS[family]["WildPokemon"])
        self.loc = C.location_names(family)
        self.names = C.species_names()

    def close(self):
        if self.ok:
            self.rom.close()

    def area_of(self, zone):
        """The area name the tracker shows in a zone: the table entry of the zone, else of its parent."""
        h = self.zones[zone]
        return self.loc.get(zone) or self.loc.get(R.zone_u16(h, R.ZONE_PARENT))

    def zones_of(self, field, value):
        return [z for z, h in enumerate(self.zones) if R.zone_u16(h, field) == value]

    def read_entry(self, entry, idx):
        """(species id, level, zones, area names) of one level slot of an ini entry."""
        f0, o0 = entry["species"][0]
        species = struct.unpack_from("<H", self.scripts[f0], o0)[0]
        f, o = entry["level"][idx]
        if entry["kind"] == "StaticPokemonFakeBall":
            level = self.mapfiles[f][o]
            zones = self.zones_of(R.ZONE_MAPFILE, f)
        else:
            level = self.scripts[f][o]
            zones = self.zones_of(R.ZONE_SCRIPT, f)
        areas = sorted({self.area_of(z) for z in zones if self.area_of(z)})
        return species, level, zones, areas

    def area_zones(self, area):
        return [z for z in range(len(self.zones)) if self.area_of(z) == area]

    def ordinary_encounters(self, area):
        """{(species, minimum, maximum, table)} of every ordinary wild table of the zones the tracker calls this area."""
        out = set()
        seen = set()
        for z in self.area_zones(area):
            ws = R.zone_wild_set(self.zones[z], self.family)
            if ws is None or ws in seen or ws >= len(self.wild):
                continue
            seen.add(ws)
            for species, lo, hi, label in R.read_wild_set(self.wild[ws]):
                out.add((species, lo, hi, label))
        return out


def pretty_join(names):
    seen = []
    for n in names:
        if n not in seen:
            seen.append(n)
    if len(seen) <= 1:
        return "".join(seen)
    return ", ".join(seen[:-1]) + " or " + seen[-1]


def verify_rows(problems, missing):
    """Check every row against the ROMs; returns the number of rows verified."""
    verified = 0
    ini = {g: read_ini_statics(sec) for g, (sec, _fam, _roms) in GAMES.items()}
    data = {}
    for game, (_sec, family, roms) in GAMES.items():
        data[game] = []
        for k in roms:
            d = GameData(k, family)
            if d.ok:
                data[game].append(d)
            else:
                missing.append("%s ROM (%s)" % (k, R.ROMS[k][0]))
    for r in ROWS:
        entries = ini[r["game"]]
        seen_species = []
        for d in data[r["game"]]:
            names = []
            for name, idx in r["src"]:
                if name not in entries:
                    problems.append("%s %s L%d: no static called %r in the randomizer's ini" % (r["game"], r["place"], r["level"], name))
                    continue
                species, level, zones, areas = d.read_entry(entries[name], idx)
                sname = C.pretty_species(d.names.get(species, "?%d" % species))
                names.append(sname)
                if level != r["level"]:
                    problems.append("%s %s: %s (%s) has level %d in %s, the row says %d" % (r["game"], r["place"], name, idx, level, d.key, r["level"]))
                if areas != [r["place"]]:
                    problems.append("%s %s L%d: %s slot %d is in zones %s, tracker areas %s, not %r (%s)" % (r["game"], r["place"], r["level"], name, idx, zones, areas, r["place"], d.key))
                if sname.lower() not in [x.lower() for x in _species_of(r)]:
                    problems.append("%s %s L%d: the ROM has %s for %s, the row says %r" % (r["game"], r["place"], r["level"], sname, name, r["species"]))
            seen_species.append(pretty_join(names))
            # what the area's ordinary wild tables hold at that level decides whether the row is kept
            ordinary = d.ordinary_encounters(r["place"])
            same_ids = {sid for sid, sname in d.names.items() if C.pretty_species(sname) in names}
            same = sorted({"%s at levels %d to %d" % (C.pretty_species(d.names[esp]), lo, hi)
                           for (esp, lo, hi, _t) in ordinary if esp in same_ids and lo <= r["level"] <= hi})
            land = sorted({"%s at levels %d to %d (%s)" % (C.pretty_species(d.names[esp]), lo, hi, t)
                           for (esp, lo, hi, t) in ordinary if t in LAND_TABLES and lo <= r["level"] <= hi})
            reason = "same" if same else ("land" if land else "")
            if reason != r["skip"]:
                problems.append("%s %s L%d: the ordinary wild tables say %r (%s), the row says %r (%s)" % (
                    r["game"], r["place"], r["level"], reason, "; ".join(same or land) or "no overlap", r["skip"], d.key))
        if len(set(seen_species)) > 1:
            problems.append("%s %s L%d: the ROMs disagree on the species %s" % (r["game"], r["place"], r["level"], seen_species))
        if data[r["game"]]:
            verified += 1
    for game, lst in data.items():
        for d in lst:
            census_check(d, ini[game], problems)
    for lst in data.values():
        for d in lst:
            d.close()
    return verified


# Foongus and Amoonguss item balls are NPC records (36 bytes) in a map file whose script id is a fixed number; counting
# every such record in every map file shows the randomizer's list is complete (and the research doc's counts are not).
FAKE_BALL_SCRIPTS = {"bw": (b"\x0e", b"\x0f"), "b2w2": (b"\x18", b"\x19")}


def census_check(d, entries, problems):
    """Every fake item ball of the ROM against the ini's Foongus and Amoonguss level lists."""
    foongus, amoonguss = FAKE_BALL_SCRIPTS[d.family]
    pattern = re.compile(rb"(?s)..\x00\x00.{2}([" + foongus + amoonguss + rb"])\x29\x01\x00(.)\x00" + b"\x00" * 12)
    census = {}
    for fid, blob in enumerate(d.mapfiles):
        for m in pattern.finditer(blob):
            name = "Foongus" if m.group(1) == foongus else "Amoonguss"
            key = (fid, name, m.group(2)[0])
            census[key] = census.get(key, 0) + 1
    expected = {}
    for name in ("Foongus", "Amoonguss"):
        entry = entries.get(name)
        if entry is None:
            continue
        for f, o in entry["level"]:
            key = (f, name, d.mapfiles[f][o])
            expected[key] = expected.get(key, 0) + 1
    if census != expected:
        problems.append("%s: the fake item balls found in the map files %s differ from the randomizer's list %s" % (d.key, sorted(census.items()), sorted(expected.items())))
    return census


def _species_of(r):
    """The species names a row lists, split out of its text."""
    return [x.strip() for x in re.split(r",| or ", r["species"]) if x.strip()]


# ------------------------------------------------------------------ the file

def header():
    return [
        "# Statics for the Generation 5 games (2026-09-29): wild battles that are NOT the first encounter of their area.",
        "#",
        "# A wild battle at that place at exactly that level counts as a static (a set battle), not the area's first",
        "# encounter: legendaries and weekday Pokemon standing in the overworld, the Zen Mode statues, item balls that",
        "# are Pokemon. One row per game, place and level. species is the vanilla species (a randomized game changes the",
        "# species, the place and level stay). Gifts, eggs, fossils, trades and roamers are not here: they are not",
        "# battles or have no fixed place (the research doc's Tornadus and Thundurus roam, Zoroark and Zorua are gifts).",
        "#",
        "# game: bw = Black and White, b2w2 = Black 2 and White 2. Black 2 and White 2 were both read and no level differs;",
        "# Black could not be read and the research doc gives no version difference for these levels, so no row uses a",
        "# single-game key. place: spelled exactly as the DS tracker names the area (gen5/locations-bw.tsv,",
        "# gen5/locations-b2w2.tsv); a room inside a bigger area is shown by the tracker under the parent's name.",
        "#",
        "# Sources. Where each static keeps its species and level: the Universal Pokemon Randomizer ZX ini",
        "# (engine-zx gen5_offsets.ini, StaticPokemon and StaticPokemonFakeBall). Every level, every species and every",
        "# place was read out of clean ROMs: Black 2 and White 2 (they agree) and, for bw, a White (US) ROM, since Black",
        "# has no dump on this machine. The place is the zone the script or map file belongs to, turned into a tracker",
        "# area the way the tracker does it (the zone's own table entry, else its parent's). The research doc",
        "# (docs/research/nuzlocke-gen4-5.md 4.3 and 5.3) agrees on every level it gives; the fused Kyurem's level 55 and",
        "# the Foongus and Amoonguss item ball levels are not in it. Statics a game leaves out of its scripts (Tornadus,",
        "# Thundurus) or that are not battles are left out here.",
        "# A census of every map file finds exactly the item balls the randomizer lists: Black and White have 2 Foongus at",
        "# level 20 on Route 6 and 2 Foongus at 30 plus 2 Amoonguss at 40 on Route 10 (the research doc says 3 on Route 6",
        "# and 2 plus 1 on Route 10); Black 2 and White 2 have 3 at 29 on Route 6, 2 at 36 on Route 7, 2 at 43 on Route 11,",
        "# 2 at 47 on Route 22 and 3 at 56 on Route 23.",
        "#",
        "# Rows dropped on purpose, and rows kept with a species check. A wild battle is matched to a row by place and level (and",
        "# by species when the row has a sixth column), so a row whose level an ordinary encounter of that area also has would",
        "# turn ordinary encounters into free statics. Checked in the ROM (all seasons, every zone of the area): the same species",
        "# at that level in the ordinary tables (dropped: nothing tells the static from the ordinary encounter), or another",
        "# species at that level in a land table (grass, dark grass, shaking grass; kept, with the species' national dex number",
        "# as the sixth column, so an ordinary encounter of another species is not taken for it. A randomized game changes the",
        "# species and does not match those rows).",
        "#   same species, dropped: " + "; ".join("%s %s L%d %s" % (r["game"], r["place"], r["level"], r["species"]) for r in ROWS if r["skip"] == "same") + ".",
        "#   land table, kept with a dex number: " + "; ".join("%s %s L%d %s" % (r["game"], r["place"], r["level"], r["species"]) for r in ROWS if r["skip"] == "land") + ".",
        "# Rows kept can still share their level with a surf or fishing encounter, whose tables span wide ranges (fishing",
        "# is post-game in these games); that residue is accepted.",
        "#",
        "# Regenerate: python tools/nuzlocke/gen5_statics.py    Check: python tools/nuzlocke/gen5_statics.py --check",
        "#",
        "# game\tplace\tlevel\tspecies\tnote\tdex (optional)",
    ]


def dex_of(species):
    """The national dex number of a single species a row names ("Amoonguss" is 591)."""
    found = [sid for sid, name in C.species_names().items() if C.pretty_species(name).lower() == species.lower()]
    if len(found) != 1:
        raise ValueError("cannot tell which species %r is: %s" % (species, found))
    return found[0]


def written(r):
    """A row is written unless its species also stands in the ordinary tables at that level."""
    return r["skip"] != "same"


def render_rows():
    lines = []
    for r in ROWS:
        if written(r):
            cols = [r["game"], r["place"], str(r["level"]), r["species"], r["note"]]
            if r["skip"] == "land":
                cols.append(str(dex_of(r["species"])))
            lines.append("\t".join(cols))
    return C.render(header() + lines)


def main():
    flags = C.main_flags()
    problems, missing = [], []
    verified = verify_rows(problems, missing)
    text = render_rows()
    kept = [r for r in ROWS if written(r)]
    keys = [(r["game"], r["place"], r["level"]) for r in ROWS]
    if len(keys) != len(set(keys)):
        problems.append("two rows share a game, place and level")
    if not flags["check"]:
        if problems:
            print("refusing to write, problems:")
            for p in problems:
                print("  - " + p)
            return C.EXIT_MISMATCH
        OUT.write_text(text, encoding="ascii", newline="\n")
        print("wrote %s (%d rows)" % (OUT, len(kept)))
    shipped = C.normalized(OUT)
    if shipped is None:
        problems.append("%s does not exist" % OUT)
    else:
        problems.extend(C.diff_lines(text, shipped))
    # the places must be real tracker names
    for game, (_sec, family, _roms) in GAMES.items():
        names = set(C.location_names(family).values())
        for r in ROWS:
            if r["game"] == game and r["place"] not in names:
                problems.append("%s: place %r is not a name in gen5/locations-%s.tsv" % (game, r["place"], family))
    code = C.finish("statics-gen5.tsv", problems, sorted(set(missing)))
    if code == C.EXIT_OK:
        same = sum(1 for r in ROWS if r["skip"] == "same")
        land = sum(1 for r in ROWS if r["skip"] == "land")
        print("  %d rows in the file (%d of them with a species check); %d candidates verified against a ROM, %d dropped (same species); every place a tracker area name" % (len(kept), land, verified, same))
    return code


if __name__ == "__main__":
    sys.exit(main())
