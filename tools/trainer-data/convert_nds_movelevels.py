"""The DS tracker's level-up LEVEL tables, by RUNNING the reference's Lua (NDS-Ironmon-Tracker).

    python tools/trainer-data/convert_nds_movelevels.py <NDS-Ironmon-Tracker/ironmon_tracker> <tracker-nds resources root>

PokemonData.POKEMON_MASTER_LIST[id + 1].movelvls holds one list of levels per version group, and
Program.addAdditionalDataToPokemon keeps movelvls[gameInfo.VERSION_GROUP] (Program.lua:327):
1 Diamond and Pearl, 2 Platinum, 3 HeartGold and SoulSilver, 4 Black and White, 5 Black 2 and
White 2 (GameInfo.lua). The levels, not the moves: a randomizer changes the move at each slot and
leaves the levels alone, so one table per version group holds for every seed.

Writes one row per species, "id<TAB>levels":

    gen4/movelevels-dp.tsv     version group 1, species 1-493
    gen4/movelevels-pt.tsv     version group 2
    gen4/movelevels-hgss.tsv   version group 3

and checks, without writing, that gen5/movelevels-bw.tsv and gen5/movelevels-b2w2.tsv (written by
tools/extract_gen5_data.py) hold version groups 4 and 5 for species 1-649. Every written file is read
back and compared with the Lua lists; any difference stops the script with a non-zero exit.

Until 2026-09-28 every Gen 4 game used Platinum's table: 51 species wrong on Diamond and Pearl, 13 on
HeartGold and SoulSilver (parity audit).
"""
import sys, pathlib
from lupa import LuaRuntime, lua_type

ref = pathlib.Path(sys.argv[1]); res = pathlib.Path(sys.argv[2])
c = ref / "constants"

lua = LuaRuntime(unpack_returned_tuples=True)
# Globals the files touch but that are not loaded here resolve to stand-ins that can be called
# (handing back their first argument, so MiscUtils.readOnly(t) is t) and indexed to any depth,
# as in convert_nds_log_tables.py.
lua.execute(
    "STANDIN = {}\n"
    "local function proxy() return setmetatable({}, STANDIN) end\n"
    "STANDIN.__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end\n"
    "STANDIN.__call = function(self, a, ...) if a ~= nil then return a end return proxy() end\n"
    "setmetatable(_G, {__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end})\n")
for f in ("Chars.lua", "Graphics.lua", "PokemonData.lua"):
    lua.execute((c / f).read_text(encoding="utf-8"))
G = lua.globals()
master = G.PokemonData.POKEMON_MASTER_LIST


def levels(species, group):
    """movelvls[group] for a national dex id, as ints in the tracker's order."""
    mon = master[species + 1]
    lv = mon["movelvls"] if mon is not None else None
    if lv is None or lua_type(lv) != "table":
        sys.exit("species %d has no movelvls table" % species)
    g = lv[group]
    if g is None or lua_type(g) != "table":
        sys.exit("species %d has no movelvls[%d]" % (species, group))
    out = []
    for k in range(1, len(g) + 1):
        v = g[k]
        if isinstance(v, float) and v.is_integer():
            v = int(v)
        if not isinstance(v, int) or not 1 <= v <= 100:
            sys.exit("species %d group %d: level %r" % (species, group, v))
        out.append(v)
    return out


def rows(group, last):
    return ["%d\t%s" % (s, ",".join(str(x) for x in levels(s, group))) for s in range(1, last + 1)]


def ending_of(folder):
    """The line ending the tables in [folder] already use, so a new file matches its neighbours."""
    for p in sorted(folder.glob("*.tsv")):
        return "\r\n" if b"\r\n" in p.read_bytes() else "\n"
    return "\n"


def read_rows(path):
    return path.read_text(encoding="utf-8").splitlines()


tables = [
    ("gen4/movelevels-dp.tsv", 1, 493, True),
    ("gen4/movelevels-pt.tsv", 2, 493, True),
    ("gen4/movelevels-hgss.tsv", 3, 493, True),
    ("gen5/movelevels-bw.tsv", 4, 649, False),
    ("gen5/movelevels-b2w2.tsv", 5, 649, False),
]
for rel, group, last, write in tables:
    path = res / rel
    want = rows(group, last)
    if write:
        nl = ("\r\n" if b"\r\n" in path.read_bytes() else "\n") if path.exists() else ending_of(path.parent)
        path.write_bytes((nl.join(want) + nl).encode("utf-8"))
    have = read_rows(path)
    if have != want:
        bad = [(w, h) for w, h in zip(want, have) if w != h][:5]
        sys.exit("%s differs from movelvls[%d]: %d rows vs %d, first differences %s" % (rel, group, len(have), len(want), bad))
    print("%-26s version group %d, %d species, %s" % (rel, group, last, "written and read back" if write else "checked"))

# How far apart the Gen 4 groups are, for the log: a Platinum table on another game is this wrong.
pt = rows(2, 493)
for name, group in (("Diamond/Pearl", 1), ("HeartGold/SoulSilver", 3)):
    other = rows(group, 493)
    print("%s: %d species differ from Platinum" % (name, sum(1 for a, b in zip(pt, other) if a != b)))
