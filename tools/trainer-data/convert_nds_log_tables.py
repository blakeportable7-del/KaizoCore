"""The DS tracker's log-viewer tables, by RUNNING its Lua (NDS-Ironmon-Tracker).

    python tools/trainer-data/convert_nds_log_tables.py <NDS-Ironmon-Tracker/ironmon_tracker> <tracker-nds resources root>

Writes into nds/:

    trainer-groups.tsv  TrainerData.TRAINERS[code].IMPORTANT_GROUPS, one battle per row. Black and
                        White's first gym is three battles picked by the starter (alt 1-3), as
                        LogViewerScreen.formatTrainerGroups picks them.
    game-info.tsv       GameInfo.GAME_INFO: name, generation, version group, badge prefix, gym TMs
                        (-1 is HeartGold and SoulSilver's spacer before Kanto), pivot types.
    pivot-areas.tsv     LocationData.LOCATION_DATA[code].encounterAreaOrder: the Pivots tab's areas.
    evo-methods.tsv     PokemonData.POKEMON_MASTER_LIST: each species' evolution, a level or a type
                        code, as PokemonStatScreen labels the evolution it shows. Empty for none.
    evo-names.tsv       PokemonData.EVO_LONGER_NAMES: a type code's long names, one per evolution.

Globals the files touch but that are not loaded here resolve to stand-ins that can be called
(handing back their first argument, so MiscUtils.readOnly(t) is t) and indexed to any depth.
A value that is still a stand-in after loading is something the tracker leaves undefined, and is
written empty.
"""
import sys, pathlib
from lupa import LuaRuntime, lua_type

ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2]) / "nds"
out.mkdir(parents=True, exist_ok=True)
c = ref / "constants"

lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute(
    "STANDIN = {}\n"
    "local function proxy() return setmetatable({}, STANDIN) end\n"
    "STANDIN.__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end\n"
    "STANDIN.__call = function(self, a, ...) if a ~= nil then return a end return proxy() end\n"
    "function isStandIn(v) return type(v) == 'table' and getmetatable(v) == STANDIN end\n"
    "setmetatable(_G, {__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end})\n")
for f in ("Chars.lua", "LocationData.lua", "TrainerData.lua", "PokemonData.lua", "GameInfo.lua"):
    lua.execute((c / f).read_text(encoding="utf-8"))
G = lua.globals()
standin = G.isStandIn

def plain(v):
    """A Lua scalar as text; a stand-in (undefined in the tracker) as empty."""
    if v is None or (lua_type(v) == "table" and standin(v)):
        return ""
    if isinstance(v, float) and v.is_integer():
        return str(int(v))
    return str(v)

def ipairs(t):
    """Array entries in index order (integer keys only)."""
    if t is None or lua_type(t) != "table":
        return []
    return sorted(((k, v) for k, v in t.items() if isinstance(k, int)), key=lambda kv: kv[0])

def spairs(t):
    """String-keyed entries, sorted by key (stand-in keys skipped)."""
    if t is None or lua_type(t) != "table":
        return []
    return sorted(((k, v) for k, v in t.items() if isinstance(k, str)), key=lambda kv: kv[0])

tab, nl = chr(9), chr(10)

# trainer groups
rows = 0
with open(out / "trainer-groups.tsv", "w", encoding="utf-8", newline=nl) as f:
    f.write(tab.join(["# game code", "group", "group name", "type (0 standard, 1 rival, 2 gym leaders)", "battle",
                      "alt (1-3 by starter, 0 none)", "name or location", "ids (by starter when three)", "badge", "iv"]) + nl)
    for code, g in sorted(((k, v) for k, v in G.TrainerData.TRAINERS.items() if isinstance(k, int)), key=lambda kv: kv[0]):
        for gi, grp in ipairs(g["IMPORTANT_GROUPS"]):
            for bi, b in ipairs(grp["battles"]):
                subs = [(0, b)] if (b["ids"] is not None and not standin(b["ids"])) else [(ai, sb) for ai, sb in ipairs(b)]
                for ai, sb in subs:
                    label = plain(sb["name"]) or plain(sb["location"])
                    ids = ",".join(plain(x) for _, x in ipairs(sb["ids"]))
                    f.write(tab.join(["%08X" % code, str(gi), plain(grp["groupName"]), plain(grp["trainerType"]), str(bi),
                                      str(ai), label, ids, plain(sb["badgeNumber"]), plain(sb["iv"])]) + nl)
                    rows += 1
print("trainer-groups", rows)

# game info
vn = {int(v): k for k, v in spairs(G.GameInfo.VERSION_NUMBER)}
with open(out / "game-info.tsv", "w", encoding="utf-8", newline=nl) as f:
    f.write(tab.join(["# game code", "version", "name", "gen", "version group", "badge prefix", "gym TMs", "pivot types",
                      "trainer table code", "location table code"]) + nl)
    # Which TrainerData.TRAINERS and LocationData.LOCATION_DATA entry each game points at (Pearl uses
    # Diamond's, SoulSilver HeartGold's...), found by identity against the loaded tables.
    same = lua.eval("function(a, b) return rawequal(a, b) end")
    def table_code(tables, t):
        for k, v in tables.items():
            if isinstance(k, int) and same(v, t):
                return "%08X" % k
        return ""
    for code, info in sorted(((k, v) for k, v in G.GameInfo.GAME_INFO.items() if isinstance(k, int)), key=lambda kv: kv[0]):
        gym = ",".join(plain(x) for _, x in ipairs(info["GYM_TMS"]))
        piv = ",".join(k for k, _ in spairs(info["PIVOT_TYPES"]))
        f.write(tab.join(["%08X" % code, vn.get(code, "?"), plain(info["NAME"]), plain(info["GEN"]), plain(info["VERSION_GROUP"]),
                          plain(info["BADGE_PREFIX"]), gym, piv,
                          table_code(G.TrainerData.TRAINERS, info["TRAINERS"]),
                          table_code(G.LocationData.LOCATION_DATA, info["LOCATION_DATA"])]) + nl)
print("game-info", len(vn))

# pivot areas
n = 0
with open(out / "pivot-areas.tsv", "w", encoding="utf-8", newline=nl) as f:
    f.write(tab.join(["# location table code", "order", "area"]) + nl)
    for code, table in sorted(((k, v) for k, v in G.LocationData.LOCATION_DATA.items() if isinstance(k, int)), key=lambda kv: kv[0]):
        for i, name in ipairs(table["encounterAreaOrder"]):
            f.write(tab.join(["%08X" % code, str(i), plain(name)]) + nl); n += 1
print("pivot-areas", n)

# evolutions
n = 0
with open(out / "evo-methods.tsv", "w", encoding="utf-8", newline=nl) as f:
    f.write(tab.join(["# national id", "name", "evolution (a level, or a type code; empty for none)"]) + nl)
    for i, p in ipairs(G.PokemonData.POKEMON_MASTER_LIST):
        if i < 2:
            continue   # entry 1 is the empty slot for id 0
        f.write(tab.join([str(i - 1), plain(p["name"]), plain(p["evolution"])]) + nl); n += 1
print("evo-methods", n)
n = 0
with open(out / "evo-names.tsv", "w", encoding="utf-8", newline=nl) as f:
    f.write(tab.join(["# evolution type code", "long names, one per evolution (PokemonData.EVO_LONGER_NAMES)"]) + nl)
    for code, names in spairs(G.PokemonData.EVO_LONGER_NAMES):
        f.write(tab.join([code, " | ".join(plain(x) for _, x in ipairs(names))]) + nl); n += 1
print("evo-names", n)
