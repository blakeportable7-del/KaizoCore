"""Auto Pokemon theme tables, by RUNNING the reference Lua (2026-09-28).

    python tools/trainer-data/convert_autothemes.py <Ironmon-Tracker-AutoPokemonThemes> <Ironmon-Tracker/ironmon_tracker> <NDS-Ironmon-Tracker/ironmon_tracker> <out dir>

Writes into <out dir>/gen3 and <out dir>/nds; point it at a scratch folder, then copy
gen3/autothemes.tsv into tracker-gba/src/main/resources/gen3 and nds/autothemes.tsv into
tracker-nds/src/main/resources/nds (or pass each module's resources root and move the other file).

gen3/autothemes.tsv   id<TAB>theme code, from Fellshadow's AutoThemes.lua loadThemeSets() run over its own
                      AutoThemeSets.txt with the Gen 3 tracker's FileManager/Theme/PokemonData loaded.
nds/autothemes.tsv    gen<TAB>species<TAB>form<TAB>theme, from NDS PokemonData + GameConfigurator, with
                      Program.checkForAlternateForm's resolution precomputed (cosmetic forms keep the base).
"""
import sys, pathlib, re
from lupa.lua54 import LuaRuntime, lua_type  # BizHawk runs Lua 5.4; lua55 rejects assigning a for-loop variable

ext, gba, nds, out = (pathlib.Path(a) for a in sys.argv[1:5])

STANDIN = (
    "STANDIN = {}\n"
    "local function proxy() return setmetatable({}, STANDIN) end\n"
    "STANDIN.__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end\n"
    "STANDIN.__call = function(self, a, ...) if a ~= nil then return a end return proxy() end\n"
    "STANDIN.__concat = function(a, b) return tostring(type(a)=='table' and '' or a) .. tostring(type(b)=='table' and '' or b) end\n"
    "for _, mm in ipairs({'__add','__sub','__mul','__div','__mod','__pow','__unm','__idiv'}) do STANDIN[mm] = function() return 0 end end\n"
    "STANDIN.__lt = function() return false end; STANDIN.__le = function() return false end; STANDIN.__len = function() return 0 end\n"
    "function isStandIn(v) return type(v) == 'table' and getmetatable(v) == STANDIN end\n"
    "setmetatable(_G, {__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end})\n")

# ---------------------------------------------------------------- Gen 3 (Fellshadow)
L = LuaRuntime(unpack_returned_tuples=True)
L.execute(STANDIN)
for f in ("Utils.lua", "FileManager.lua", "Theme.lua", "data/PokemonData.lua"):
    L.execute((gba / f).read_text(encoding="utf-8"))
G = L.globals()
folder = str(ext).replace("\\", "/") + "/"
L.execute("function FileManager.getCustomFolderPath() return %r end" % folder)
L.execute("function FileManager.getPathIfExists(p) local f = io.open(p, 'r'); if f then f:close(); return p end return nil end")
L.execute("LOG = {}; print = function(m) table.insert(LOG, m) end")
make = L.execute((ext / "AutoThemes.lua").read_text(encoding="utf-8"))
self = make()
self.setupMappings()
themes, ok = self.loadThemeSets()
logs = [G.LOG[i] for i in range(1, len(G.LOG) + 1)]
g3 = {int(k): str(v) for k, v in themes.items()}
print("gen3: loaded", len(g3), "ok", ok, "log", logs)
(out / "gen3").mkdir(parents=True, exist_ok=True)
with open(out / "gen3" / "autothemes.tsv", "w", encoding="utf-8", newline="\n") as fh:
    for k in sorted(g3):
        fh.write(f"{k}\t{g3[k]}\n")

# ---------------------------------------------------------------- NDS (built in)
N = LuaRuntime(unpack_returned_tuples=True)
N.execute(STANDIN)
for f in ("constants/PokemonData.lua",):
    N.execute((nds / f).read_text(encoding="utf-8"))
N.execute((nds / "GameConfigurator.lua").read_text(encoding="utf-8"))
NG = N.globals()
rows = []
nds_themes = {}
for gen in (4, 5):
    N.execute("GameConfigurator.initPokemon({GEN=%d}); GameConfigurator.initAlternateForms({GEN=%d})" % (gen, gen))
    P = NG.PokemonData.POKEMON
    count = len(P)
    base_last = 493 if gen == 4 else 649
    by_id = {}
    for i in range(1, count + 1):
        e = P[i]
        t = e.theme
        by_id[i - 1] = (str(e.name), str(t) if t is not None and lua_type(t) != "table" else None, e)
    # Base species
    for sp in range(1, base_last + 1):
        name, theme, _ = by_id[sp]
        if theme: rows.append((gen, sp, 0, theme))
        nds_themes[(gen, sp, 0)] = theme
    # Forms, as Program.checkForAlternateForm resolves them: form k>=1 -> formTable.index + (k - 2) as a 0-based id
    AF = NG.PokemonData.ALTERNATE_FORMS
    order = NG.GameConfigurator["ALTERNATE_FORM_ORDER_GEN%d" % gen]
    for j in range(1, len(order) + 1):
        base = str(order[j]); ft = AF[base]
        base_sp = int(ft.baseIndex)
        cosmetic = bool(ft.cosmetic)
        for k in range(1, len(ft.forms) + 1):
            alt_id = int(ft.index) + (k - 2)
            fname, ftheme, _ = by_id.get(alt_id, (None, None, None))
            resolved = nds_themes[(gen, base_sp, 0)] if cosmetic else ftheme
            if resolved and resolved != nds_themes[(gen, base_sp, 0)]:
                rows.append((gen, base_sp, k, resolved))
            nds_themes[(gen, base_sp, k)] = resolved
    print(f"gen{gen}: entries {count}, base with theme {sum(1 for s in range(1, base_last+1) if nds_themes[(gen,s,0)])}/{base_last}")
(out / "nds").mkdir(parents=True, exist_ok=True)
with open(out / "nds" / "autothemes.tsv", "w", encoding="utf-8", newline="\n") as fh:
    for r in rows:
        fh.write("\t".join(str(x) for x in r) + "\n")
print("nds rows", len(rows), "form rows", sum(1 for r in rows if r[2] > 0))

