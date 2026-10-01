"""The DS tracker's move powers and species weights, by RUNNING its Lua (NDS-Ironmon-Tracker), 2026-09-29.

    python -B tools/trainer-data/convert_nds_move_power.py

Writes into tracker-nds/src/main/resources:

    gen4/moves.tsv     MoveData.MOVES as GameConfigurator.initMoveData(GEN = 4) builds it, in the
                       shape gen5/moves.tsv has (tools/extract_gen5_data.py move_row: power and
                       accuracy the tracker shows as "---" are 0, a text power such as WT or <HP
                       is 0 with the text in an eighth column). It held the ROM's numbers, so 34
                       variable or fixed powers read 1 where the tracker prints WT, <HP, VAR or
                       ---, Nightmare and Memento had an accuracy, and Curse and Hidden Power had
                       the ROM's type where the tracker has UNKNOWN (37 rows, parity audit
                       2026-09-28). Every row is read back and compared with the Lua.
    nds/weights.tsv    PokemonData.POKEMON_MASTER_LIST weight in kg per species id 1-649, which
                       MoveUtils.calculateVariableDamage reads for Low Kick, Grass Knot, Heat Crash
                       and Heavy Slam.

Hand entry is banned (docs/EMULATOR_PLAN.md risk 3); the tracker's own startup code is run.
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import extract_gen5_data as g5                      # TRACKER, reference_moves, move_row, write_rows
from lupa import LuaRuntime, lua_type

RES = HERE.parent.parent / "tracker-nds/src/main/resources"


def main():
    # ---- gen4/moves.tsv
    ref = g5.reference_moves(4)
    out = RES / "gen4" / "moves.tsv"
    old = {l.split("\t")[0]: l for l in out.read_text(encoding="utf-8").splitlines()} if out.exists() else {}
    rows = [g5.move_row(r) for r in ref]
    g5.write_rows(out, rows)
    back = out.read_text(encoding="utf-8").splitlines()
    if len(back) != len(ref):
        sys.exit("gen4/moves.tsv has %d rows, the reference %d" % (len(back), len(ref)))
    for line, r in zip(back, ref):
        p = line.split("\t")
        shown_power = p[7] if len(p) > 7 else (p[2] if p[2] != "0" else "---")
        shown = (p[0], p[1], shown_power, p[3] if p[3] != "0" else "---", p[4], p[5], p[6])
        if shown != r:
            sys.exit("gen4/moves.tsv row %s reads %s, the reference %s" % (p[0], shown, r))
    changed = [l for l in rows if old.get(l.split("\t")[0]) != l]
    print("gen4 moves %d match initMoveData(GEN = 4); %d rows changed" % (len(rows), len(changed)))

    # ---- nds/weights.tsv
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(
        "STANDIN = {}\n"
        "local function proxy() return setmetatable({}, STANDIN) end\n"
        "STANDIN.__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end\n"
        "STANDIN.__call = function(self, a, ...) if a ~= nil then return a end return proxy() end\n"
        "setmetatable(_G, {__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end})\n")
    for f in ("constants/Chars.lua", "constants/Graphics.lua", "constants/PokemonData.lua"):
        lua.execute((g5.TRACKER / f).read_text(encoding="utf-8"))
    P = lua.globals().PokemonData.POKEMON_MASTER_LIST
    wl = ["# species id\tweight kg (PokemonData.POKEMON_MASTER_LIST)"]
    for sid in range(1, 650):
        w = P[sid + 1]["weight"]                        # list index = id + 1; index 1 is the empty entry
        if w is None or lua_type(w) is not None:
            sys.exit("species %d has no plain weight" % sid)
        wl.append("%d\t%s" % (sid, repr(float(w))))
    g5.write_rows(RES / "nds" / "weights.tsv", wl)
    print("weights %d, species 1 %s kg, 649 %s kg" % (len(wl) - 1, wl[1].split("\t")[1], wl[-1].split("\t")[1]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
