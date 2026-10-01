"""The DS tracker's move descriptions, by RUNNING its Lua (NDS-Ironmon-Tracker), 2026-09-29.

    python -B tools/trainer-data/convert_nds_move_desc.py

Writes tracker-nds/src/main/resources/nds/move-desc.tsv: move id, then MoveData's description as
GameConfigurator.initMoveData(GEN = 4) and (GEN = 5) leave it (initMoveData keeps attribute[GEN]
where the master list holds a table; Gen 4 stops at move 467). MainScreen puts it in each move's
hover text (readMovesIntoUI, MainScreen.lua:518-520); a phone shows it on a tap.
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import extract_gen5_data as g5                      # TRACKER, write_rows
from lupa import LuaRuntime, lua_type

OUT = HERE.parent.parent / "tracker-nds/src/main/resources/nds/move-desc.tsv"


def moves_for(gen):
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(
        "STANDIN = {}\n"
        "local function proxy() return setmetatable({}, STANDIN) end\n"
        "STANDIN.__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end\n"
        "STANDIN.__call = function(self, a, ...) if a ~= nil then return a end return proxy() end\n"
        "setmetatable(_G, {__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end})\n")
    for f in ("constants/Chars.lua", "constants/Graphics.lua", "constants/PokemonData.lua",
              "constants/MoveData.lua", "GameConfigurator.lua"):
        lua.execute((g5.TRACKER / f).read_text(encoding="utf-8"))
    G = lua.globals()
    G.GameConfigurator.initMoveData(lua.table_from({"GEN": gen}))
    out = {}
    for index, m in G.MoveData.MOVES.items():
        if index == 1:
            continue                                     # the empty entry for move id 0
        d = m["description"]
        if d is None or lua_type(d) is not None:
            sys.exit("Gen %d move %d: description is not a plain string" % (gen, index - 1))
        if str(m["id"]) != str(index - 1):
            sys.exit("Gen %d MoveData.MOVES[%d] carries id %s" % (gen, index, m["id"]))
        out[index - 1] = str(d).replace("\t", " ").replace("\r", " ").replace("\n", " ")
    return out


def main():
    g4, gen5 = moves_for(4), moves_for(5)
    rows = ["# move id\tGen 4 description\tGen 5 description"]
    for mid in sorted(gen5):
        rows.append("%d\t%s\t%s" % (mid, g4.get(mid, ""), gen5[mid]))
    g5.write_rows(OUT, rows)
    back = OUT.read_text(encoding="utf-8").splitlines()[1:]
    for line in back:
        p = line.split("\t")
        mid = int(p[0])
        if p[1] != g4.get(mid, "") or p[2] != gen5[mid]:
            sys.exit("move-desc.tsv row %d does not read back" % mid)
    print("move descriptions: Gen 4 %d, Gen 5 %d, differing %d" % (
        len(g4), len(gen5), sum(1 for k in g4 if g4[k] != gen5[k])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
