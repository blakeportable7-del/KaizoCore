"""TrainerData.Trainers (Ironmon-Tracker, Gen 3) to trainers-<table>.tsv, by RUNNING the Lua.

    python tools/trainer-data/convert_trainers.py <Ironmon-Tracker/ironmon_tracker> <tracker-gba resources root>

Every trainer's class and group, exactly as TrainerData.buildData leaves them for the running game
(TrainerData.lua:131-144): setupTrainersAsFRLG for FireRed and LeafGreen, setupTrainersAsEmerald
for Emerald and setupTrainersAsRubySapphire for Ruby and Sapphire. The three tables disagree on
hundreds of ids (R/S has Archie at 1/34/35, Maxie at 566/601/602, no ids above 693), so Ruby and
Sapphire get their own file instead of borrowing Emerald's (parity audit, 2026-09-28).

    trainers-frlg.tsv   FireRed, LeafGreen (and Nat. Dex FireRed)
    trainers-rse.tsv    Emerald (and Nat. Dex Emerald); the name predates the R/S split
    trainers-rs.tsv     Ruby, Sapphire

Line format: trainer id, TAB, class key (the TrainerData.Classes key, e.g. "GymLeader8"), TAB,
group key (the TrainerData.TrainerGroups key, e.g. "Elite4"; "Other" where the reference leaves
the group nil, as it does for generic classes and FRLG's rematch Elite Four 735-741).

One id is claimed by two classes: FRLG 666 is in BirdKeeper's range {662, 668} and listed on its own
under Beauty (TrainerData.lua:826, 828). mapClassesToTrainers walks the classes with pairs(), whose
order Lua leaves undefined, so the PC tracker itself may land on either. This keeps the class that
names the id outright over the one whose range covers it (Beauty, which is also what the FireRed
ROM's own trainer table says: BEAUTY GRACE), and refuses to guess at any other collision. To see
the claims, mapClassesToTrainers is made to record its input: the one line of the source changed.
"""
import sys, pathlib
from lupa import LuaRuntime

ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2]) / "gen3"

STUBS = r"""
FileManager = { buildImagePath = function(...) return "" end, Folders = {}, Extensions = {} }
Constants = { BLANKLINE = "---", HIDDEN_INFO = "?", }
Utils = {
  getbits = function(v, s, n) return math.floor(v / 2^s) % 2^n end,
  isNilOrEmpty = function(s) return s == nil or s == "" end,
  formatSpecialCharacters = function(s) return s end,
  inlineIf = function(c, a, b) if c then return a else return b end end,
}
GameSettings = { game = 3, versioncolor = "" }
Tracker = { getWhichRival = function() return nil end }
Program = {}
RouteData = { Info = {} }
"""

HOOK_FROM = "local function mapClassesToTrainers(classMap, trainerList)"
HOOK_TO = "local function mapClassesToTrainers(classMap, trainerList)\n\tCLASS_MAP = classMap"

src = (ref / "data" / "TrainerData.lua").read_text(encoding="utf-8")
assert src.count(HOOK_FROM) == 1, "mapClassesToTrainers moved; update the hook"
src = src.replace(HOOK_FROM, HOOK_TO)

tab, nl = chr(9), chr(10)
for name, game, setup in (("frlg", 3, "setupTrainersAsFRLG"), ("rse", 2, "setupTrainersAsEmerald"),
                          ("rs", 1, "setupTrainersAsRubySapphire")):
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(STUBS)
    lua.execute("GameSettings.game = %d" % game)
    lua.execute(src)
    # The route half of setup (mapRoutesToTrainers) only fills trainer.routeId, which this file does not carry.
    lua.execute("TrainerData.Trainers = {}; TrainerData.%s()" % setup)
    rows, claims = lua.eval("""function()
      local classKey, groupKey = {}, {}
      for k, v in pairs(TrainerData.Classes) do classKey[v] = k end
      for k, v in pairs(TrainerData.TrainerGroups) do groupKey[v] = k end
      local rows, claims = {}, {}
      for id, t in pairs(TrainerData.Trainers) do
        table.insert(rows, { id, classKey[t.class] or "Unknown", t.group and groupKey[t.group] or "Other" })
      end
      for class, items in pairs(CLASS_MAP) do
        for _, item in pairs(items) do
          if type(item) == "number" then
            table.insert(claims, { item, classKey[class], true, class.group and groupKey[class.group] or "Other" })
          else
            for i = item[1], item[2] do
              table.insert(claims, { i, classKey[class], false, class.group and groupKey[class.group] or "Other" })
            end
          end
        end
      end
      return rows, claims
    end""")()
    table = {int(r[1]): [str(r[2]), str(r[3])] for r in rows.values()}
    by_id = {}
    for c in claims.values():
        by_id.setdefault(int(c[1]), []).append((str(c[2]), bool(c[3]), str(c[4])))
    for tid, cs in sorted(by_id.items()):
        if len(cs) < 2:
            continue
        named = [c for c in cs if c[1]]
        assert len(named) == 1, "trainer %d is claimed by %s; decide which the tracker should use" % (tid, cs)
        assert len({c[2] for c in cs}) == 1, "trainer %d's claims disagree on the group: %s" % (tid, cs)
        table[tid][0] = named[0][0]
        print(name, "trainer", tid, "claimed by", [c[0] for c in cs], "-> kept", named[0][0])
    with open(out / ("trainers-%s.tsv" % name), "w", encoding="utf-8", newline=nl) as f:
        for tid in sorted(table):
            f.write(str(tid) + tab + table[tid][0] + tab + table[tid][1] + nl)
    print(name, len(table), "trainers")
