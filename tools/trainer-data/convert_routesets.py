"""RandomizerLog.RouteSetNumToIdMap (Ironmon-Tracker, Gen 3) to routesets-<game>.tsv, by RUNNING the Lua.

    python tools/trainer-data/convert_routesets.py <Ironmon-Tracker/ironmon_tracker> <tracker-gba resources root>

The reference maps each "Set #N" of the randomizer log's wild encounters to a map id with one
hand-written table per game (setupRubySappRouteMappings, setupEmeraldRouteMappings,
setupFRLGRouteMappings), because the log's route names are not unique. Executing those three
functions is the only way to get exactly the table the PC tracker uses.
"""
import sys, pathlib
from lupa import LuaRuntime

ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2]) / "gen3"
src = (ref / "data" / "RandomizerLog.lua").read_text(encoding="utf-8").splitlines()
start = next(i for i, l in enumerate(src) if l.startswith("function RandomizerLog.setupRubySappRouteMappings"))
stop = next(i for i, l in enumerate(src) if l.startswith("function RandomizerLog.removeMappings"))
body = chr(10).join(src[start:stop])

tab = chr(9); nl = chr(10)
for tag, fn, color in (("rs", "setupRubySappRouteMappings", "Ruby"), ("e", "setupEmeraldRouteMappings", "Emerald"), ("frlg", "setupFRLGRouteMappings", "FireRed")):
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute("RandomizerLog = {}; GameSettings = { versioncolor = '%s' }; "
                "Utils = { inlineIf = function(c, a, b) if c then return a else return b end end }" % color)
    lua.execute(body)
    lua.execute("RandomizerLog.%s()" % fn)
    table = {int(k): int(v) for k, v in dict(lua.globals().RandomizerLog.RouteSetNumToIdMap).items()}
    with open(out / ("routesets-%s.tsv" % tag), "w", encoding="utf-8", newline=nl) as f:
        f.write("# set number" + tab + "map id  (RandomizerLog.RouteSetNumToIdMap, converted by tools/trainer-data/convert_routesets.py)" + nl)
        for k in sorted(table):
            f.write(str(k) + tab + str(table[k]) + nl)
    print(tag, len(table), "sets ->", len(set(table.values())), "maps")
