"""LocationData.lua (NDS-Ironmon-Tracker) to locations-<game>.tsv, by RUNNING the Lua.

    python tools/trainer-data/convert_locations.py <NDS-Ironmon-Tracker/ironmon_tracker/constants> <resources root>

One file per table: pt (Diamond, Pearl, Platinum), hgss, bw, b2w2. Rows: map id, name.
"""
import sys, pathlib
from lupa import LuaRuntime
ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2])
lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute(open(ref / "Chars.lua", encoding="utf-8").read()); lua.execute(open(ref / "LocationData.lua", encoding="utf-8").read())
data = lua.globals().LocationData.LOCATION_DATA
tab = chr(9); nl = chr(10)
for code, tag, gen in ((0x45555043, "pt", 4), (0x454B5049, "hgss", 4), (0x4F425249, "bw", 5), (0x4F455249, "b2w2", 5)):
    locs = data[code]["locations"]; n = 0
    with open(out / ("gen%d" % gen) / ("locations-%s.tsv" % tag), "w", encoding="utf-8", newline=nl) as f:
        f.write("# map id" + tab + "name  (NDS-Ironmon-Tracker LocationData.lua, converted by tools/trainer-data/convert_locations.py)" + nl)
        for k in sorted(int(x) for x in dict(locs).keys()):
            f.write(str(k) + tab + str(locs[k]["name"]) + nl); n += 1
    print(tag, n)
