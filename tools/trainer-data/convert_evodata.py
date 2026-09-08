"""EvoDataGen4.lua / EvoDataGen5.lua (NDS-Ironmon-Tracker) to evos.tsv, by RUNNING
the Lua so the table is exactly what the DS tracker uses.

    python tools/trainer-data/convert_evodata.py <NDS-Ironmon-Tracker/ironmon_tracker/constants> <out dir>

Rows: base id, target id, then "evo:percent" pairs in the reference's order.
"""
import sys, pathlib
from lupa import LuaRuntime
ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2])
tab = chr(9); nl = chr(10)
for gen in (4, 5):
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(open(ref / ("EvoDataGen%d.lua" % gen), encoding="utf-8").read())
    evos = lua.globals().EvoData.EVOLUTIONS
    rows = 0
    with open(out / ("gen%d" % gen) / "evos.tsv", "w", encoding="utf-8", newline=nl) as f:
        f.write("# base id" + tab + "target id" + tab + "evo:percent,...  (NDS-Ironmon-Tracker EvoDataGen%d.lua, converted by tools/trainer-data/convert_evodata.py)" % gen + nl)
        for base in sorted(int(k) for k in dict(evos).keys()):
            for target in sorted(int(k) for k in dict(evos[base]).keys()):
                lst = [(int(v["id"]), float(v["percent"])) for _, v in sorted(dict(evos[base][target]).items(), key=lambda kv: int(kv[0]))]
                f.write(str(base) + tab + str(target) + tab + ",".join("%d:%g" % p for p in lst) + nl); rows += 1
    print("gen", gen, "rows", rows)
