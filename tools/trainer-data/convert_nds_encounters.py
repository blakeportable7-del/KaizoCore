"""LocationData.lua encounters (NDS-Ironmon-Tracker) to encounters-<game>.tsv, by RUNNING the Lua.

    python tools/trainer-data/convert_nds_encounters.py <NDS-Ironmon-Tracker/ironmon_tracker/constants> <resources root>

The DS tracker's encounter frame (MainScreen.lua:176-224, HoverFrameFactory.createTracked/
VanillaEncountersHoverFrame) reads LOCATION_DATA[game].encounters[areaName]: how many species the
area holds (totalPokemon) and, per slot, the vanilla level (or level range) and percent. It shows
no species on purpose, so a randomized seed is not spoiled. One file per table: pt (Diamond, Pearl,
Platinum), hgss, bw, b2w2, keyed by the same area names the tracker reads.

Row: area name, TAB, totalPokemon, TAB, slots joined by ";", each slot's entries joined by ",",
an entry "level:percent" or "min-max:percent".
"""
import sys, pathlib
from lupa import LuaRuntime
ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2])
lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute(open(ref / "Chars.lua", encoding="utf-8").read()); lua.execute(open(ref / "LocationData.lua", encoding="utf-8").read())
data = lua.globals().LocationData.LOCATION_DATA
tab = chr(9); nl = chr(10)

def entry(e):
    pct = e["percent"]
    pct = int(pct) if float(pct) == int(pct) else pct
    if e["level"] is not None:
        return "%d:%s" % (int(e["level"]), pct)
    r = e["levelRange"]
    return "%d-%d:%s" % (int(r[1]), int(r[2]), pct)

for code, tag, gen in ((0x45555043, "pt", 4), (0x454B5049, "hgss", 4), (0x4F425249, "bw", 5), (0x4F455249, "b2w2", 5)):
    enc = data[code]["encounters"]
    rows = []
    for area in dict(enc).keys():
        a = enc[area]
        slots = []
        vd = a["vanillaData"]
        for i in range(1, len(vd) + 1):
            slot = vd[i]
            slots.append(",".join(entry(slot[j]) for j in range(1, len(slot) + 1)))
        rows.append((str(area), int(a["totalPokemon"]), ";".join(slots)))
    rows.sort()
    with open(out / "nds" / ("encounters-%s.tsv" % tag), "w", encoding="utf-8", newline=nl) as f:
        f.write("# area" + tab + "totalPokemon" + tab + "slots  (NDS-Ironmon-Tracker LocationData.lua encounters, converted by tools/trainer-data/convert_nds_encounters.py)" + nl)
        for area, total, slots in rows:
            f.write(area + tab + str(total) + tab + slots + nl)
    print(tag, len(rows), "areas")
