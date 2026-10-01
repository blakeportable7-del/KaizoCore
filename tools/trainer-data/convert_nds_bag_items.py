"""The DS tracker's bag tables for the heals box, by RUNNING its Lua (NDS-Ironmon-Tracker), 2026-09-29.

    python -B tools/trainer-data/convert_nds_bag_items.py

Writes tracker-nds/src/main/resources/nds/bag-items.tsv, one row per item in the order the main
screen's hover lists them (HoverFrameFactory readItemDataIntoFrame walks the sort orders):

    heal    id  name  amount  CONSTANT|PERCENTAGE     ItemData.HEALING_ITEMS in HEALING_ID_SORT_ORDER
    status  id  name  what it cures                   ItemData.STATUS_ITEMS in STATUS_ID_SORT_ORDER
                                                      (MiscData.STATUS_TYPE: All, Burn, Sleep...)

Program.scanForHealingItems counts these in the bag, calculateHealPercent turns the heals into the
"Heals:" line and getStatusTotals the statuses into "Status items:" (Program.lua:334-411, 751-784).
"""
import sys
from pathlib import Path
from lupa import LuaRuntime, lua_type

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import extract_gen5_data as g5                      # TRACKER, write_rows

OUT = HERE.parent.parent / "tracker-nds/src/main/resources/nds/bag-items.tsv"


def main():
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(
        "STANDIN = {}\n"
        "local function proxy() return setmetatable({}, STANDIN) end\n"
        "STANDIN.__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end\n"
        "STANDIN.__call = function(self, a, ...) if a ~= nil then return a end return proxy() end\n"
        "setmetatable(_G, {__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end})\n")
    for f in ("constants/Chars.lua", "constants/MiscData.lua", "constants/ItemData.lua"):
        lua.execute((g5.TRACKER / f).read_text(encoding="utf-8"))
    I = lua.globals().ItemData
    constant = I.HEALING_TYPE.CONSTANT

    def num(v):
        v = float(v)
        return str(int(v)) if v.is_integer() else repr(v)

    rows = ["# kind\tid\tname\tamount and CONSTANT or PERCENTAGE, or what it cures (ItemData, NDS-Ironmon-Tracker)"]
    order = I.HEALING_ID_SORT_ORDER
    for k in range(1, len(order) + 1):
        i = int(order[k]); it = I.HEALING_ITEMS[i]
        rows.append("heal\t%d\t%s\t%s\t%s" % (i, it.name, num(it.amount), "CONSTANT" if it.type == constant else "PERCENTAGE"))
    order = I.STATUS_ID_SORT_ORDER
    for k in range(1, len(order) + 1):
        i = int(order[k]); it = I.STATUS_ITEMS[i]
        if lua_type(it.status) is not None:
            sys.exit("status item %d: its status is not a plain string" % i)
        rows.append("status\t%d\t%s\t%s" % (i, it.name, it.status))
    heal_n = sum(1 for _ in I.HEALING_ITEMS.items()); status_n = sum(1 for _ in I.STATUS_ITEMS.items())
    if len(rows) - 1 != heal_n + status_n:
        sys.exit("sort orders cover %d items, the tables hold %d" % (len(rows) - 1, heal_n + status_n))
    g5.write_rows(OUT, rows)
    print("bag items: %d heals, %d status items" % (heal_n, status_n))
    return 0


if __name__ == "__main__":
    sys.exit(main())
