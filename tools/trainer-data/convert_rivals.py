"""Which rival each rival trainer id belongs to, taken by RUNNING the PC tracker's
TrainerData.lua (Ironmon-Tracker, Gen 3) with a stub environment: the tables
are built by code, and executing that code is the only way to get exactly what
the PC tracker gets.

    python tools/trainer-data/convert_rivals.py <ironmon_tracker dir> <out dir>

The app already carries routes/trainers/trainerroutes TSVs from an earlier
port. This adds:

    rivals-frlg.tsv   trainer id, which rival ("Middle" and friends for FRLG)
    rivals-rse.tsv    the same for Ruby, Sapphire and Emerald (their ids never disagree)
"""
import sys, pathlib
from lupa import LuaRuntime

ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)

STUBS = r"""
FileManager = { buildImagePath = function(...) return "" end, Folders = {}, }
Constants = { BLANKLINE = "---", HIDDEN_INFO = "?", }
Utils = {
  getbits = function(v, s, n) return math.floor(v / 2^s) % 2^n end,
  isNilOrEmpty = function(s) return s == nil or s == "" end,
  formatSpecialCharacters = function(s) return s end,
  shortenText = function(s) return s end,
  inlineIf = function(c, a, b) if c then return a else return b end end,
  tableContains = function(t, v) for _, x in pairs(t or {}) do if x == v then return true end end return false end,
}
GameSettings = { game = 3, versioncolor = "", }
Tracker = { Data = {}, getWhichRival = function() return nil end }
Program = { GameData = {}, }
Resources = {}
Main = {}
Drawing = {}
MoveData = {}
PokemonData = {}
"""

def run(game_number, route_setup, trainer_setup):
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(STUBS)
    lua.execute("GameSettings.game = %d" % game_number)
    lua.execute(open(ref / "data" / "RouteData.lua", encoding="utf-8").read())
    lua.execute(open(ref / "data" / "TrainerData.lua", encoding="utf-8").read())
    lua.execute("RouteData.Info = {}; RouteData.%s()" % route_setup)
    lua.execute("TrainerData.Trainers = {}; TrainerData.%s()" % trainer_setup)
    return lua.globals()

def rivals(g, acc):
    for tid, t in dict(g.TrainerData.Trainers).items():
        if t.whichRival:
            acc[int(tid)] = str(t.whichRival)

tab = chr(9); nl = chr(10)
tables = {
    "frlg": [(3, "setupRouteInfoAsFRLG", "setupTrainersAsFRLG")],
    "rse": [(2, "setupRouteInfoAsRSE", "setupTrainersAsEmerald"), (1, "setupRouteInfoAsRSE", "setupTrainersAsRubySapphire")],
}
for name, setups in tables.items():
    acc = {}
    for game_number, route_setup, trainer_setup in setups:
        rivals(run(game_number, route_setup, trainer_setup), acc)
    with open(out / ("rivals-%s.tsv" % name), "w", encoding="utf-8", newline=nl) as f:
        f.write("# trainer id" + tab + "which rival  (TrainerData.Trainers[id].whichRival, converted by tools/trainer-data/convert_rivals.py)" + nl)
        for tid in sorted(acc):
            f.write(str(tid) + tab + acc[tid] + nl)
    print(name, "rivals", len(acc))
