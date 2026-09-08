"""PokemonRevoData.lua (Ironmon-Tracker) to revos.tsv, by RUNNING the Lua so the
table is exactly what the PC tracker uses.

    python convert_revos.py <ironmon_tracker dir> <out file>

Rows: base id, target id (0 when the species has one regular evolution), then
"evo:percent" pairs in the reference's order. A species with several regular
evolutions gets one row per option, options in the reference's order.
"""
import sys, pathlib
from lupa import LuaRuntime
ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2])
lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute("PokemonData = { isValid = function(id) return id ~= nil and id > 0 end }")
lua.execute(open(ref / "data" / "PokemonRevoData.lua", encoding="utf-8").read())
lua.execute("PokemonRevoData.tryLoadData()")
revo = lua.globals().PokemonRevoData.RevoData
tab = chr(9); nl = chr(10); rows = 0
with open(out, "w", encoding="utf-8", newline=nl) as f:
    f.write("# base id" + tab + "target evo id (0 = single evolution)" + tab + "evo:percent,...  (PokemonRevoData.lua, converted by tools/trainer-data/convert_revos.py)" + nl)
    for base in sorted(int(k) for k in dict(revo).keys()):
        entry = revo[base]
        options = entry["options"]
        if options is None:
            pairs = [(int(v["id"]), float(v["perc"])) for _, v in sorted(dict(entry).items(), key=lambda kv: int(kv[0]))]
            f.write(str(base) + tab + "0" + tab + ",".join("%d:%g" % p for p in pairs) + nl); rows += 1
        else:
            for _, target in sorted(dict(options).items(), key=lambda kv: int(kv[0])):
                table = entry[int(target)]
                pairs = [(int(v["id"]), float(v["perc"])) for _, v in sorted(dict(table).items(), key=lambda kv: int(kv[0]))]
                f.write(str(base) + tab + str(int(target)) + tab + ",".join("%d:%g" % p for p in pairs) + nl); rows += 1
print("rows", rows)
