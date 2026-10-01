"""RouteData.Info names and trainers (Ironmon-Tracker, Gen 3) to routeinfo-<version>.tsv, by RUNNING the Lua.

    python tools/trainer-data/convert_route_info.py <Ironmon-Tracker/ironmon_tracker> <tracker-gba resources root>

RouteData.Info[mapId].trainers is where the PC tracker takes a map's trainers from, for Trainers On
Route (TrainersOnRouteScreen.lua:155-163), the notebook and the carousel
(Program.getDefeatedTrainersByLocation, Program.lua:1545-1561) and the log viewer's route tab
(RandomizerLog.parseRoutes, RandomizerLog.lua:606-651). The app used to carry one table per game
family, FRLG's taken from FRLGTrainerRouteData.lua (the tile-click map, which misses Oak's Lab, Route
22, the Elite Four and more) and Emerald's shared with Ruby and Sapphire. This runs
setupRouteInfoAsFRLG / setupRouteInfoAsRSE once per version instead, so each version gets its own
trainers in the reference's order, and its own names (Ruby's Magma Hideout comes from
RouteData.swapRubySapphireTeamTrainers, RouteData.lua:5909-5947).

Line format: map id, TAB, the reference's key, TAB, name, TAB, trainer ids joined by ",".

The map id is the tracker's own (GbaTracker.mapId): the id the game reports, except that Ruby and
Sapphire ids above 108 come down one (GameMap.rsMapShift), since R/S carries an empty Lilycove layout
at 108 that Emerald dropped. The reference keys R/S maps by the raw id instead, writing most of them
"[id + offset]" with Emerald's id. Most, not all: the maps in RS_KEYED_AS_EMERALD below are written
without the offset, so the reference files them one map early (its own RSTrainerRouteData.lua, made
from the R/S game, has Mossdeep Gym's trainers at 109 and Sidney at 112; Ruby's gWildMonHeaders puts
Route 124's underwater encounters on layout 275). Those are filed here under the map they name, and
listed as corrections. The Cave of Origin floors R/S alone has are also written without the offset,
but in R/S's own numbering (Ruby's wild headers put B1F-B3F on layouts 160-162), so they are right.
"""
import sys, pathlib
from lupa import LuaRuntime

ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2]) / "gen3"
route_src = (ref / "data" / "RouteData.lua").read_text(encoding="utf-8")

# Ruby/Sapphire keys above 107 that the reference writes without "+ offset"
# (RouteData.lua:4467-4529 gyms and Elite Four, 4103 Route 124 Water): Emerald-numbered, one too low
# for the game. Filed under the map they name.
RS_KEYED_AS_EMERALD = {108, 109, 110, 111, 112, 113, 114, 115, 274}
# Written without the offset too (RouteData.lua:5003-5047, the R/S-only branch), but already in R/S's
# own numbering: correct, so they come down one like every other R/S id above 108.
RS_KEYED_AS_RS = {160, 161, 162, 163}

STUBS = """
local function anything()
  local t = {}
  setmetatable(t, { __index = function(tbl, k) return anything() end, __call = function() return nil end })
  return t
end
Constants = anything(); Resources = anything(); TrainerData = anything(); Program = anything(); TrackerAPI = anything()
Utils = anything()
Utils.inlineIf = function(c, a, b) if c then return a else return b end end
Utils.formatSpecialCharacters = function(s) return s end
PokemonData = {}
"""

def run(game, color, probe=False):
    """RouteData.Info for one version: {key: (name, [trainer ids])}. A probe run replaces R/S's
    offset of 1 with 1000, which moves every "[id + offset]" key and leaves the literal ones."""
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(STUBS)
    if probe:
        lua.execute("Utils.inlineIf = function(c, a, b) if c then return a elseif a == 0 and b == 1 then return 1000 else return b end end")
    lua.execute("GameSettings = { game = %d, versioncolor = '%s', versiongroup = %d }" % (game, color, 1 if game != 3 else 2))
    lua.execute(route_src)
    lua.execute("RouteData.setupRouteInfoAs%s()" % ("FRLG" if game == 3 else "RSE"))
    rows = lua.eval("""function()
      local rows = {}
      for mapId, route in pairs(RouteData.Info) do
        local ids = {}
        for _, id in ipairs(route.trainers or {}) do table.insert(ids, tostring(id)) end
        table.insert(rows, { mapId, route.name or "", table.concat(ids, ",") })
      end
      return rows
    end""")()
    return {int(r[1]): (str(r[2]), str(r[3])) for r in rows.values()}

probe = run(1, "Sapphire", probe=True)
literal = {k for k in probe if 108 <= k < 1000}
assert literal == RS_KEYED_AS_EMERALD | RS_KEYED_AS_RS, \
    "the reference's un-offset R/S keys changed: %s; check each against the game before shipping" % sorted(literal)

tab, nl = chr(9), chr(10)
for version, game, color in (("firered", 3, "FireRed"), ("leafgreen", 3, "LeafGreen"),
                             ("ruby", 1, "Ruby"), ("sapphire", 1, "Sapphire"), ("emerald", 2, "Emerald")):
    info = run(game, color)
    rows = {}
    for key, (name, trainers) in info.items():
        if game != 1 or key < 108 or key in RS_KEYED_AS_EMERALD:
            ours = key
        else:
            ours = key - 1
        assert ours not in rows, "%s: two maps land on %d" % (version, ours)
        rows[ours] = (key, name, trainers)
    with open(out / ("routeinfo-%s.tsv" % version), "w", encoding="utf-8", newline=nl) as f:
        f.write("# map id" + tab + "reference key" + tab + "name" + tab + "trainers  (RouteData.Info[key].name and .trainers via tools/trainer-data/convert_route_info.py)" + nl)
        for ours in sorted(rows):
            key, name, trainers = rows[ours]
            f.write(str(ours) + tab + str(key) + tab + name + tab + trainers + nl)
    moved = sorted(k for k, (key, _, _) in rows.items() if game == 1 and key in RS_KEYED_AS_EMERALD)
    print(version, len(rows), "maps,", sum(1 for r in rows.values() if r[2]), "with trainers",
          ("; reference keys corrected: %s" % moved) if moved else "")
