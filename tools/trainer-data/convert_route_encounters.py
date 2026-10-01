"""RouteData.lua (Ironmon-Tracker, Gen 3) to routeenc-<version>.tsv, by RUNNING the Lua.

    python tools/trainer-data/convert_route_encounters.py <Ironmon-Tracker/ironmon_tracker> <tracker-gba resources root>

The route info screen (InfoScreen.drawRouteInfoScreen) needs, per map and encounter area, the
vanilla species with their rate and level range, exactly as RouteData.getEncounterAreaPokemon
returns them for the running game. That differs by version: FireRed and LeafGreen, Ruby and
Sapphire pick different species ({FR, LG} / {R, S, E} tables), Ruby and Sapphire shift every map
id above 107 by one, and the ids are national numbers that the reference converts to Gen 3
internal ones. The routes-frlg/rse.tsv files did none of that (2026-09-28), so this runs the
reference's own setup and lookup once per version instead.

Every map in RouteData.Info is written, with or without encounters, so the file also carries
the route names. Line format: map id, TAB, route name, TAB, areas joined by "|", each "Area=" then
"id:rate:min:max" entries joined by ",", in RouteData.OrderedEncounters order. Ids are internal.
"""
import sys, pathlib
from lupa import LuaRuntime

ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2]) / "gen3"
route_src = (ref / "data" / "RouteData.lua").read_text(encoding="utf-8")
pd = (ref / "data" / "PokemonData.lua").read_text(encoding="utf-8").splitlines()
start = next(i for i, l in enumerate(pd) if l.startswith("local idInternalToNat"))
stop = next(i for i, l in enumerate(pd) if l.startswith("function PokemonData.dexMapNationalToInternal"))
ids = "\n".join(pd[start:stop + 3])

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
PokemonData.isValid = function(id) return id ~= nil and id >= 1 and id <= 411 and not (id > 251 and id < 277) end
"""

tab, nl = chr(9), chr(10)
for version, game, color in (("firered", 3, "FireRed"), ("leafgreen", 3, "LeafGreen"),
                             ("ruby", 1, "Ruby"), ("sapphire", 1, "Sapphire"), ("emerald", 2, "Emerald")):
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(STUBS)
    lua.execute("GameSettings = { game = %d, versioncolor = '%s', versiongroup = %d }" % (game, color, 1 if game != 3 else 2))
    lua.execute(ids)
    lua.execute(route_src)
    lua.execute("if RouteData.setupRouteInfoAs%s then RouteData.maxId = RouteData.setupRouteInfoAs%s() end"
                % (("FRLG",) * 2 if game == 3 else ("RSE",) * 2))
    dump = lua.eval("""function()
      local rows = {}
      for mapId, route in pairs(RouteData.Info) do
        local areas = {}
        for _, area in ipairs(RouteData.OrderedEncounters) do
          if RouteData.hasRouteEncounterArea(mapId, area) then
            local mons = {}
            for _, m in ipairs(RouteData.getEncounterAreaPokemon(mapId, area)) do
              table.insert(mons, string.format("%d:%s:%d:%d", m.pokemonID, tostring(m.rate), m.minLv, m.maxLv))
            end
            if #mons > 0 then table.insert(areas, area .. "=" .. table.concat(mons, ",")) end
          end
        end
        table.insert(rows, { mapId, route.name or "", table.concat(areas, "|") })
      end
      return rows
    end""")()
    rows = [(int(r[1]), str(r[2]), str(r[3])) for r in dump.values()]
    # Keys exactly as the reference writes them. For Ruby/Sapphire that is NOT a uniform shift:
    # routes and caves are "[id + offset]" (+1 above 107) while the gyms and Elite Four rooms
    # keep Emerald's ids, and the reference looks them up with the raw map id the game reports.
    rows.sort()
    with open(out / ("routeenc-%s.tsv" % version), "w", encoding="utf-8", newline=nl) as f:
        f.write("# map id" + tab + "name" + tab + "Area=internal id:rate:min level:max level  (RouteData.lua via tools/trainer-data/convert_route_encounters.py)" + nl)
        for m, name, areas in rows:
            f.write(str(m) + tab + name + tab + areas + nl)
    print(version, sum(1 for r in rows if r[2]), "maps with encounters")
