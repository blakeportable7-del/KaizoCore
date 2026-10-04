"""Runs the PC tracker's own GachaMon code on generated Pokemon and writes what it answers, for KaizoCore's tests.

    python tools/gachamon/reference_fixtures.py <Ironmon-Tracker clone> <repo root>

Needs the lupa package (Lua 5.1 from Python). The reference's files are loaded as they are: data/GachaMonData.lua,
GachaMonFileManager.lua, StructEncoder.lua, data/MoveData.lua and data/AbilityData.lua, with the reference's own
GachaMonRatingSystem.json, Utils.isSTAB and Utils.getNatureMultiplier, PokemonData.getEffectiveness and its type chart.
Only the game is stood in for: each case's species (types, base stats, listed BST, evolution) and, for a randomized
case, the ROM's move power, type and accuracy as MoveData.buildData would have read them.

Writes tracker-gba/src/test/resources/gachamon/reference-cases.tsv: one case a line, the inputs as the rating reads
them and what calculateRatingScore, calculateStars, calculateBattlePower and getShareablyCode answered.
"""
import json
import random
import re
import sys
from pathlib import Path

from lupa.lua51 import LuaRuntime

ref = Path(sys.argv[1]) / 'ironmon_tracker'
repo = Path(sys.argv[2])
lua = LuaRuntime(unpack_returned_tuples=True)
G = lua.globals()

def lua_func(src_path, name):
    """The text of `function name(...) ... end` from a reference file, by matching its end at column 0."""
    src = src_path.read_text(encoding='utf-8')
    start = src.index(f'function {name}(')
    end = src.index('\nend', start) + 4
    return src[start:end]

# The environment the reference files expect at load time.
lua.execute('''
Constants = { BLANKLINE = "---", HIDDEN_INFO = "?" }
Main = { IsOnBizhawk = function() return true end }
CustomCode = { RomHacks = { natdex = false, isPlayingNatDex = function() return CustomCode.RomHacks.natdex end, ExtensionKeys = {} } }
TrackerAPI = { getExtensionSelf = function() return nil end }
PokemonData = {
  Types = { NORMAL = "normal", FIGHTING = "fighting", FLYING = "flying", POISON = "poison", GROUND = "ground", ROCK = "rock",
    BUG = "bug", GHOST = "ghost", STEEL = "steel", FIRE = "fire", WATER = "water", GRASS = "grass", ELECTRIC = "electric",
    PSYCHIC = "psychic", ICE = "ice", DRAGON = "dragon", DARK = "dark", FAIRY = "fairy", UNKNOWN = "unknown", EMPTY = "" },
  Evolutions = { NONE = { abbreviation = "---" } },
  Pokemon = {},
}
local T = PokemonData.Types
PokemonData.TypeIndexMap = { [0]=T.NORMAL, T.FIGHTING, T.FLYING, T.POISON, T.GROUND, T.ROCK, T.BUG, T.GHOST, T.STEEL,
  T.UNKNOWN, T.FIRE, T.WATER, T.GRASS, T.ELECTRIC, T.PSYCHIC, T.ICE, T.DRAGON, T.DARK, T.FAIRY }
PokemonData.TypeNameToIndexMap = {}
for i = 0, 18 do PokemonData.TypeNameToIndexMap[PokemonData.TypeIndexMap[i]] = i end
PokemonData.BlankPokemon = { pokemonID = 0, types = { T.UNKNOWN, T.EMPTY }, bst = "---", evolution = PokemonData.Evolutions.NONE }
function PokemonData.isValid(id) return id ~= nil and PokemonData.Pokemon[id] ~= nil end
function PokemonData.getNatDexCompatible(id) return PokemonData.Pokemon[id or false] or PokemonData.BlankPokemon end
Utils = {}
function Utils.bit_lshift(x, n) return x * (2 ^ n) end
function Utils.getbits(x, from, width) return math.floor(x / (2 ^ from)) % (2 ^ width) end
FileManager = { fileExists = function() return true end }
''')
lua.execute((ref / 'StructEncoder.lua').read_text(encoding='utf-8'))
lua.execute((ref / 'data' / 'MoveData.lua').read_text(encoding='utf-8'))
lua.execute((ref / 'data' / 'AbilityData.lua').read_text(encoding='utf-8'))
lua.execute((ref / 'data' / 'GachaMonData.lua').read_text(encoding='utf-8'))
lua.execute((ref / 'GachaMonFileManager.lua').read_text(encoding='utf-8'))
lua.execute(lua_func(ref / 'Utils.lua', 'Utils.isSTAB'))
lua.execute(lua_func(ref / 'Utils.lua', 'Utils.getNatureMultiplier'))
lua.execute(lua_func(ref / 'data' / 'PokemonData.lua', 'PokemonData.getEffectiveness'))

def to_lua(v):
    if isinstance(v, dict):
        t = lua.table()
        for k, x in v.items():
            t[k] = to_lua(x)
        return t
    if isinstance(v, list):
        t = lua.table()
        for i, x in enumerate(v, start=1):
            t[i] = to_lua(x)
        return t
    return v

rating_json = json.loads((ref / 'data' / 'GachaMonRatingSystem.json').read_text(encoding='utf-8'))
G.FileManager.decodeJsonFile = lambda path: to_lua(rating_json)
assert G.GachaMonFileManager.importRatingSystem('GachaMonRatingSystem.json')

TYPE_NAMES = ['normal', 'fighting', 'flying', 'poison', 'ground', 'rock', 'bug', 'ghost', 'steel', 'unknown', 'fire',
              'water', 'grass', 'electric', 'psychic', 'ice', 'dragon', 'dark', 'fairy']
TYPE_IDS = {n: i for i, n in enumerate(TYPE_NAMES)}
REAL_TYPES = [i for i in range(18) if i != 9]

# The reference's static move list, as loaded, before any case changes it.
static_moves = {}
for mid in range(1, 355):
    m = G.MoveData.Moves[mid]
    static_moves[mid] = (m.type, m.power, m.accuracy, m.category, bool(m.variablepower))

def restore_moves():
    for mid, (t, p, a, c, _) in static_moves.items():
        m = G.MoveData.Moves[mid]
        m.type, m.power, m.accuracy, m.category = t, p, a, c

def randomize_move(mid, rnd):
    """MoveData.buildData(forced) with a ROM whose move has a new power, type and accuracy (not the split patch)."""
    m = G.MoveData.Moves[mid]
    new_type = TYPE_NAMES[rnd.choice(REAL_TYPES)]
    rom_power = str(rnd.choice([0, 0, 10, 20, 35, 40, 50, 60, 70, 75, 80, 90, 95, 100, 110, 120, 140, 150, 1]))
    rom_acc = str(rnd.choice([0, 30, 50, 55, 70, 75, 80, 85, 90, 95, 100, 100, 100]))
    if not m.variablepower:
        m.power = rom_power
    if m.power != '0' and new_type != m.type and m.category != G.MoveData.Categories.STATUS:
        m.category = G.MoveData.TypeToCategory[new_type]
    m.type = new_type
    m.accuracy = rom_acc

RULESETS = ['Standard', 'Ultimate', 'Kaizo', 'Survival', 'SurvivalRevival', 'SuperKaizo', 'Subpar',
            'Ascension1', 'Ascension2', 'Ascension3']
CATEGORY = {'Physical': 'PHYSICAL', 'Special': 'SPECIAL', 'Status': 'STATUS', 'None': 'NONE'}

def run_case(rnd, case):
    """One Pokemon through convertPokemonToGachaMon's rating calls; returns the fixture line's fields."""
    restore_moves()
    for mid in case['randomized_moves']:
        randomize_move(mid, rnd)
    species = case['species']
    types = [TYPE_NAMES[t] for t in case['types']]
    G.PokemonData.Pokemon = to_lua({})
    mon = to_lua({
        'types': [types[0], types[1] if len(types) > 1 else ''],
        'bst': case['listed_bst'],
        'baseStats': dict(zip(['hp', 'atk', 'def', 'spa', 'spd', 'spe'], case['base'])),
    })
    mon.evolution = '16' if case['evolves'] else G.PokemonData.Evolutions.NONE
    G.PokemonData.Pokemon[species] = mon
    G.CustomCode.RomHacks.natdex = case['natdex']
    G.GachaMonData.rulesetKey = case['ruleset']
    stats = dict(zip(['hp', 'atk', 'def', 'spa', 'spd', 'spe'], case['stats']))
    g = G.GachaMonData.IGachaMon.new(G.GachaMonData.IGachaMon, to_lua({
        'Version': 2, 'Personality': case['personality'], 'PokemonId': species, 'Level': case['level'],
        'AbilityId': case['ability'], 'SeedNumber': case['seed'],
        'Temp': {'Stats': stats, 'MoveIds': case['moves'], 'GameVersion': case['game'], 'IsShiny': case['shiny'],
                 'Gender': case['gender'], 'Nature': case['nature'], 'Keep': case['keep']},
    }))
    g.Type1 = case['types'][0]
    g.Type2 = case['types'][1] if len(case['types']) > 1 else case['types'][0]
    rating = G.GachaMonData.calculateRatingScore(g, mon.baseStats)
    g.RatingScore = rating
    power = G.GachaMonData.calculateBattlePower(g)
    g.BattlePower = power
    stars = G.GachaMonData.calculateStars(g)
    g.Favorite = case['favorite']
    g.GameWinner = case['winner']
    g.Badges = case['badges']
    g.Temp.DateTimeObtained = None
    g.compressStatsHpAtkDef(g, True)
    g.compressStatsSpaSpdSpe(g, True)
    g.compressMoveIdsGameVersionKeep(g, True)
    g.compressShinyGenderNature(g, True)
    # compressDateObtained reads os.date; set the bits for the case's date directly, as it would
    y, mo, d = case['date']
    g.C_DateObtained = d + mo * 32 + (y - 2000) * 512
    code = G.GachaMonData.getShareablyCode(g)
    moves = []
    for mid in case['moves']:
        m = G.MoveData.Moves[mid]
        moves.append(f"{mid}:{TYPE_IDS[m.type]}:{m.power}:{int(m.accuracy) if re.fullmatch(r'[0-9]+', str(m.accuracy)) else 0}:{CATEGORY[m.category]}")
    return [
        case['name'], species, ','.join(map(str, case['types'])), ','.join(map(str, case['base'])), case['listed_bst'],
        int(case['evolves']), int(case['natdex']), case['ruleset'], case['ability'], ' '.join(moves),
        ','.join(map(str, case['stats'])), case['nature'],
        case['personality'], case['level'], case['seed'], case['game'], case['shiny'], case['gender'], case['keep'],
        case['favorite'], case['winner'], case['badges'], '-'.join(map(str, case['date'])),
        int(rating), int(stars), int(power), code,
    ]

def golden():
    # Blake's own PC card, 2026-07-22: Charizard, Impish, Lv. 5, Compoundeyes, Crabhammer, Bonemerang, Eruption,
    # Dragon Claw, HP 17 ATK 20 DEF 11 SPA 16 SPD 21 SPE 9, Emerald, seed 1,220: 69 points (5 stars), 8000 BP, v2.
    # The seed randomized base stats: at level 5 those stats need Attack and Sp. Atk of 110 or more, HP + Def + Sp. Def
    # under 240 and Speed under 60 (HP base about 5-29, Atk 135-159, Def 35-59, SpA 115-139, SpD 145-169, Spe 25-49).
    return dict(name='golden-charizard', species=6, types=[10, 2], base=[20, 140, 45, 125, 155, 35], listed_bst=534,
                evolves=False, natdex=False, ruleset='Kaizo', ability=14, moves=[152, 155, 284, 337],
                stats=[17, 20, 11, 16, 21, 9], nature=8, personality=0x9E3779B1, level=5, seed=1220, game=2,
                shiny=1, gender=1, keep=1, favorite=0, winner=0, badges=0, date=(2026, 7, 22), randomized_moves=[])

def random_case(rnd, n):
    types = [rnd.choice(REAL_TYPES)]
    natdex = rnd.random() < 0.15
    if rnd.random() < 0.55:
        t2 = rnd.choice(REAL_TYPES + ([18] if natdex else []))
        if t2 != types[0]:
            types.append(t2)
    ability = rnd.choice(list(range(1, 78)) + [0, 2, 14, 17, 26, 37, 40, 41, 45, 55, 69, 70, 74, 4, 75, 53, 47, 10, 11, 18])
    move_pool = list(range(1, 355))
    favored = [311, 284, 323, 67, 175, 179, 216, 218, 167, 155, 24, 41, 3, 4, 31, 129, 185, 325, 332, 345, 351, 36, 38,
               66, 344, 12, 32, 90, 329, 237, 248, 251, 353, 69, 49, 82, 217, 222, 255, 15, 19, 57, 70, 127, 148, 249, 291]
    count = rnd.choice([1, 2, 3, 4, 4, 4, 4])
    moves = []
    while len(moves) < count:
        mid = rnd.choice(favored) if rnd.random() < 0.35 else rnd.choice(move_pool)
        if mid not in moves:
            moves.append(mid)
    base = [rnd.choice([rnd.randint(1, 255), rnd.randint(20, 130), rnd.choice([29, 30, 49, 50, 59, 60, 89, 90, 109, 110])])
            for _ in range(6)]
    level = rnd.randint(1, 100)
    stats = [rnd.randint(10, 700)] + [rnd.randint(5, 500) for _ in range(5)]
    randomized = [m for m in moves if rnd.random() < 0.3]
    return dict(name=f'case-{n}', species=rnd.randint(1, 411), types=types, base=base,
                listed_bst=rnd.choice([rnd.randint(180, 680), 399, 400, 410, 411, 420, 421]), evolves=rnd.random() < 0.5,
                natdex=natdex, ruleset=rnd.choice(RULESETS + ['Kaizo', 'SuperKaizo', 'Standard']), ability=ability,
                moves=moves, stats=stats, nature=rnd.randint(0, 24), personality=rnd.randint(0, 0xFFFFFFFF),
                level=level, seed=rnd.randint(1, 65535), game=rnd.randint(1, 5), shiny=rnd.randint(0, 1),
                gender=rnd.randint(0, 2), keep=rnd.randint(0, 1), favorite=rnd.randint(0, 1), winner=rnd.randint(0, 1),
                badges=rnd.randint(0, 255), date=(rnd.randint(2025, 2030), rnd.randint(1, 12), rnd.randint(1, 28)),
                randomized_moves=randomized)

TOP_MOVES = [int(k) for k, v in sorted(rating_json['Moves'].items(), key=lambda kv: -kv[1]) if int(k) <= 354][:60]
TOP_ABILITIES = [int(k) for k, v in sorted(rating_json['Abilities'].items(), key=lambda kv: -kv[1])[:15]]

def strong_case(rnd, n):
    """A Pokemon built to rate high: top moves, matching types, high base stats, so the 5+ thresholds are reached."""
    c = random_case(rnd, n)
    moves = rnd.sample(TOP_MOVES, 4)
    c['moves'] = moves
    c['randomized_moves'] = []
    move_types = [TYPE_IDS[G.MoveData.Moves[m].type] for m in moves]
    real = [t for t in move_types if t != 9]
    c['types'] = sorted(set(rnd.sample(real, min(2, len(real))))) if real else [0]
    c['ability'] = rnd.choice(TOP_ABILITIES)
    c['base'] = [rnd.randint(60, 140), rnd.randint(90, 160), rnd.randint(60, 140), rnd.randint(90, 160), rnd.randint(60, 140), rnd.randint(80, 150)]
    c['nature'] = rnd.choice([1, 2, 3, 4, 15, 16, 17, 19])
    c['natdex'] = False
    c['ruleset'] = rnd.choice(['Standard', 'Kaizo', 'Ultimate', 'SuperKaizo'])
    return c

def weak_case(rnd, n):
    """A Pokemon that rates nothing: no ability points, banned moves, poor offense, so the rating clamps at 0."""
    c = random_case(rnd, n)
    c['ability'] = 0
    c['moves'] = rnd.sample([15, 19, 57, 70, 127, 148, 249, 291], rnd.randint(1, 4))
    c['randomized_moves'] = []
    c['ruleset'] = 'Kaizo'
    c['base'] = [rnd.randint(1, 29), rnd.randint(1, 49), rnd.randint(1, 29), rnd.randint(1, 49), rnd.randint(1, 29), rnd.randint(1, 59)]
    c['nature'] = rnd.choice([5, 10, 20, 3, 13, 23])
    return c

rnd = random.Random(20261003)
rows = [run_case(rnd, golden())]
for n in range(1, 401):
    rows.append(run_case(rnd, random_case(rnd, n)))
for n in range(401, 461):
    rows.append(run_case(rnd, strong_case(rnd, n)))
for n in range(461, 466):
    rows.append(run_case(rnd, weak_case(rnd, n)))

out = repo / 'tracker-gba' / 'src' / 'test' / 'resources' / 'gachamon'
out.mkdir(parents=True, exist_ok=True)
header = ['name', 'species', 'types', 'base', 'listedBst', 'evolves', 'natDex', 'ruleset', 'ability', 'moves',
          'stats', 'nature', 'personality', 'level', 'seed', 'game', 'shiny', 'gender', 'keep', 'favorite', 'winner',
          'badges', 'date', 'rating', 'stars', 'battlePower', 'code']
with open(out / 'reference-cases.tsv', 'w', encoding='utf-8', newline='\n') as f:
    f.write('# Answers of the PC tracker\'s own GachaMon code (tools/gachamon/reference_fixtures.py). moves: id:type:power:accuracy:category\n')
    f.write('\t'.join(header) + '\n')
    for r in rows:
        f.write('\t'.join(str(x) for x in r) + '\n')
print('golden', rows[0][-4:])
from collections import Counter
print('stars', sorted(Counter(r[-3] for r in rows).items()))
