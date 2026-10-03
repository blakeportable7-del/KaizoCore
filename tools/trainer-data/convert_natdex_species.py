"""The Nat. Dex extension's species evolutions and weights (NatDexExtension.lua) to
gen3/species-extra-natdex.tsv, the table the GBA tracker uses on a Nat. Dex ROM.

    python tools/trainer-data/convert_natdex_species.py <NatDexExtension.lua> <repo root>

Rows 1-411 are gen3/species-extra.tsv with the extension's evolution changes to the
base game's species (its PokemonData update: Kadabra's Linking Cord, Primeape at 43,
Eevee's eight stones, and the rest). Rows 412-1283 are its natDexMons, in order: the
extension appends them to PokemonData.Pokemon, so the first is 412.

The evolution column keeps the reference's key, as gen3/species-extra.tsv does: a
bare level, or the PokemonData.Evolutions key (SHINY, LINKING_CORD, ...). natDexMons
is built before the extension adds its own Evolutions, so in the reference a new
method there reads nil until the "workaround" lines set it again; the Lua below
resolves every key by name, and the script checks the workaround names the same
method for each species it touches.

The table literal is RUN in Lua (lupa), not parsed by pattern, so it is exactly the
extension's.
"""
import io, re, sys, pathlib
from lupa import LuaRuntime

ext = pathlib.Path(sys.argv[1]); root = pathlib.Path(sys.argv[2])
tab = chr(9); nl = chr(10)
src = io.open(ext, encoding="utf-8").read()

# The natDexMons literal, from its opening brace to the matching close.
start = src.index("self.Data.natDexMons = {")
i = src.index("{", start); depth = 0
for j in range(i, len(src)):
    c = src[j]
    if c == "{": depth += 1
    elif c == "}":
        depth -= 1
        if depth == 0: end = j + 1; break
literal = src[i:end]

lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute("PokemonData = { Evolutions = setmetatable({}, { __index = function(_, k) return k end }) }")
mons = lua.eval(literal)
new_rows = []
for k in sorted(int(x) for x in dict(mons).keys()):
    m = mons[k]
    evo = m["evolution"]
    w = m["weight"]
    new_rows.append((m["name"], "" if evo in (None, "NONE") else str(evo), "" if w is None else ("%.1f" % float(w))))

# The extension's PokemonData update: mon[N].evolution = PE.KEY or "NN".
over = {}
for n, val in re.findall(r'mon\[\s*(\d+)\]\.evolution\s*=\s*(PE\.[A-Z0-9_]+|"[0-9]+")', src):
    over[int(n)] = val[3:] if val.startswith("PE.") else val.strip('"')

vanilla = {}
for line in io.open(root / "tracker-gba/src/main/resources/gen3/species-extra.tsv", encoding="utf-8"):
    p = line.rstrip("\r\n").split(tab)
    if len(p) >= 4 and p[0].isdigit(): vanilla[int(p[0])] = p[1:4]
assert sorted(vanilla) == list(range(1, 412)), "species-extra.tsv is not 411 rows"
assert len(new_rows) == 872, len(new_rows)

out = root / "tracker-gba/src/main/resources/gen3/species-extra-natdex.tsv"
changed = 0
with io.open(out, "w", encoding="utf-8", newline=nl) as f:
    for sid in range(1, 412):
        name, evo, weight = vanilla[sid]
        if sid in over:
            # Our vanilla rows carry a Lua comment on a few values (EvoText.clean strips it); the override replaces all of it.
            evo = over[sid]; changed += 1
        f.write(str(sid) + tab + name + tab + evo + tab + weight + nl)
    for k, (name, evo, weight) in enumerate(new_rows):
        sid = 412 + k
        if sid in over:
            assert over[sid] == evo, (sid, name, evo, over[sid])
        f.write(str(sid) + tab + name + tab + evo + tab + weight + nl)
print("rows", 411 + len(new_rows), "base species changed", changed, "->", out)
