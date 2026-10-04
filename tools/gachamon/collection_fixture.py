"""Writes a PC tracker GachaMon collection file with the PC tracker's own writer, for KaizoCore's import test.

    python tools/gachamon/collection_fixture.py <Ironmon-Tracker clone> <repo root>

Needs the lupa package (Lua 5.1 from Python). The reference's StructEncoder.lua, data/GachaMonData.lua and
GachaMonFileManager.lua are loaded as they are. Cards come from reference-cases.tsv's share codes, read back into
IGachaMon objects by the reference's own GachaMonData.transformCodeIntoGachaMon, then written by
GachaMonFileManager.saveCollectionToFile, exactly as the PC tracker writes FullCollection.gccg. The file holds:

- the first 12 cases, three of them favorites (golden-charizard, case-4, case-7)
- case-2 a second time, now a favorite: the same card, which the import must count once and keep as a favorite
- then two records the reference cannot read: one with version byte 9 and 30 bytes more, then 7 stray bytes

Writes tracker-gba/src/test/resources/gachamon/FullCollection.gccg and collection-fixture.txt (what was written).
"""
import sys
from pathlib import Path

from lupa.lua51 import LuaRuntime

ref = Path(sys.argv[1]) / 'ironmon_tracker'
repo = Path(sys.argv[2])
lua = LuaRuntime(unpack_returned_tuples=True)
G = lua.globals()

lua.execute('''
Constants = { BLANKLINE = "---", HIDDEN_INFO = "?" }
Main = { IsOnBizhawk = function() return true end }
CustomCode = { RomHacks = { natdex = false, isPlayingNatDex = function() return false end, ExtensionKeys = {} } }
TrackerAPI = { getExtensionSelf = function() return nil end }
PokemonData = {
  Types = { NORMAL = "normal", FIGHTING = "fighting", FLYING = "flying", POISON = "poison", GROUND = "ground", ROCK = "rock",
    BUG = "bug", GHOST = "ghost", STEEL = "steel", FIRE = "fire", WATER = "water", GRASS = "grass", ELECTRIC = "electric",
    PSYCHIC = "psychic", ICE = "ice", DRAGON = "dragon", DARK = "dark", FAIRY = "fairy", UNKNOWN = "unknown", EMPTY = "" },
  Evolutions = { NONE = { abbreviation = "---" } },
  Pokemon = {},
}
Utils = {}
function Utils.bit_lshift(x, n) return x * (2 ^ n) end
function Utils.getbits(x, from, width) return math.floor(x / (2 ^ from)) % (2 ^ width) end
function Utils.isNilOrEmpty(s) return s == nil or s == "" end
FileManager = { fileExists = function() return true end, extractFileNameFromPath = function(p) return p end }
''')
lua.execute((ref / 'StructEncoder.lua').read_text(encoding='utf-8'))
lua.execute((ref / 'data' / 'MoveData.lua').read_text(encoding='utf-8'))
lua.execute((ref / 'data' / 'AbilityData.lua').read_text(encoding='utf-8'))
lua.execute((ref / 'data' / 'GachaMonData.lua').read_text(encoding='utf-8'))
lua.execute((ref / 'GachaMonFileManager.lua').read_text(encoding='utf-8'))

res = repo / 'tracker-gba' / 'src' / 'test' / 'resources' / 'gachamon'
lines = [l for l in (res / 'reference-cases.tsv').read_text(encoding='utf-8').splitlines() if l and not l.startswith('#')]
header = lines[0].split('\t')
cases = [dict(zip(header, l.split('\t'))) for l in lines[1:]]

collection = lua.table()
written = []
for i, case in enumerate(cases[:12]):
    g = G.GachaMonData.transformCodeIntoGachaMon(case['code'])
    assert g is not None, case['name']
    g.Favorite = 1 if i in (0, 4, 7) else 0
    collection[len(collection) + 1] = g
    written.append(f"{case['name']}\tfavorite={g.Favorite}")
dup = G.GachaMonData.transformCodeIntoGachaMon(cases[2]['code'])
dup.Favorite = 1
collection[len(collection) + 1] = dup
written.append(f"{cases[2]['name']} again\tfavorite=1")

out = res / 'FullCollection.gccg'
assert G.GachaMonFileManager.saveCollectionToFile(collection, str(out))
data = out.read_bytes()
assert len(data) == 13 * 31, len(data)
# What the reference's own reader cannot read: an unknown version, then a short tail.
data += bytes([9]) + bytes(range(30)) + bytes(range(7))
out.write_bytes(data)

# The reference reads the file back up to the first record it cannot read.
back = G.GachaMonFileManager.getCollectionFromFile(str(out))
assert len(back) == 13, len(back)

(res / 'collection-fixture.txt').write_text(
    '# FullCollection.gccg, written by GachaMonFileManager.saveCollectionToFile (tools/gachamon/collection_fixture.py)\n'
    + '\n'.join(written) + '\nversion 9 record (31 bytes)\nstray tail (7 bytes)\n', encoding='utf-8')
print(f'wrote {out} ({len(data)} bytes)')
