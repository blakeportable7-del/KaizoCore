"""MaxDex 1.0's tracker data (Tripc423/Maxdex, MaxDexExtension.lua 1.0 and its maxdex/ folder) to the files
KaizoCore's GBA tracker reads on a MaxDex build.

    python tools/trainer-data/convert_maxdex.py <Maxdex clone> <repo root>

Every table literal is RUN in Lua (lupa), not parsed by pattern, so it is exactly the extension's; the FireRed
ability-script lines are the one exception, read line by line from its FireRed block. Writes:

  tracker-gba/src/main/resources/maxdex/species.tsv     ids 1-1280. 1-411 as natdex/species.tsv (Gen 3's own,
                                                        the same in both builds); 412-1280 from pokeNameList,
                                                        spelled as KaizoCore spells the same Pokemon on Nat. Dex
                                                        1.2.1 (the same ids to 1235). The extension names three of
                                                        the Legends Z-A megas like older megas ("Absol M" is 1085
                                                        and 1246); they are Absol-Z, Garchomp-Z and Lucario-Z, as
                                                        Nat. Dex 1.2.1 names them.
  tracker-gba/src/main/resources/maxdex/moves.tsv       1-841. 1-354 Gen 3's moves as natdex/moves.tsv has them
                                                        (the extension's renames, checked); 355-841 natDexMoves.
  tracker-gba/src/main/resources/maxdex/abilities.tsv   1-289, MaxDexAbilities.lua with the extension's two
                                                        renames (Compound Eyes, Lightning Rod).
  tracker-gba/src/main/resources/gen3/species-extra-maxdex.tsv
                                                        evolution and weight: 1-411 gen3/species-extra.tsv with the
                                                        extension's own evolution changes, 412-1280 natDexMons.
  tracker-gba/src/main/resources/gen3/abilityscripts-maxdex.tsv
                                                        the FireRed GS.ABILITIES lines, absolute addresses, in the
                                                        abilityscripts-firered11.tsv format.
  tracker-gba/src/main/resources/gen3/revos-maxdex.tsv  the extension's random-evolution table
                                                        (overrideTryLoadRevoData), in the revos.tsv format.
  app/src/main/assets/gbasprites-maxdex/412..1280.png   maxdex/sprites_mons, copied byte for byte.
"""
import io
import pathlib
import re
import shutil
import sys
from lupa import LuaRuntime

clone = pathlib.Path(sys.argv[1])
root = pathlib.Path(sys.argv[2])
tab = chr(9)
nl = chr(10)
src = io.open(clone / 'MaxDexExtension.lua', encoding='utf-8').read()
assert 'version = "1.0"' in src, 'not MaxDexExtension 1.0'


def literal(text, marker):
    """The table literal after [marker], from its opening brace to the matching close."""
    start = text.index(marker)
    i = text.index('{', start)
    depth = 0
    for j in range(i, len(text)):
        c = text[j]
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return text[i:j + 1]
    raise ValueError(marker)


lua = LuaRuntime(unpack_returned_tuples=True)
# Evolution keys read back as their own names, as convert_natdex_species.py does.
lua.execute("local function names() return setmetatable({}, { __index = function(_, k) return k end }) end"
            " PokemonData = { Evolutions = names(), Types = names() }"
            " MoveData = { Categories = names() } MiscData = { BagPocket = names() }")


def run(marker):
    return lua.eval(literal(src, marker))


def read_tsv(path):
    out = {}
    for line in io.open(path, encoding='utf-8'):
        p = line.rstrip('\r\n').split(tab)
        if len(p) >= 2 and p[0].isdigit():
            out[int(p[0])] = p[1:]
    return out


def write_tsv(path, rows, header=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    with io.open(path, 'w', encoding='utf-8', newline=nl) as f:
        if header:
            f.write(header + nl)
        for r in rows:
            f.write(tab.join(str(x) for x in r) + nl)
    print('wrote', path, len(rows), 'rows')


def key(s):
    return re.sub(r'[^a-z0-9]', '', s.lower())


res = root / 'tracker-gba/src/main/resources'

# ---------------------------------------------------------------- species
natdex_names = {i: v[0] for i, v in read_tsv(res / 'natdex/species.tsv').items()}
names = run('self.Data.pokeNameList = {')
ext_names = {int(k): names[k] for k in dict(names).keys()}
assert sorted(ext_names) == list(range(412, 1281)), (min(ext_names), max(ext_names), len(ext_names))
# Nat. Dex 1.2.1's spelling of each Pokemon, by its letters; the six forms both builds have under other spellings.
by_key = {}
for i, n in natdex_names.items():
    by_key.setdefault(key(n), []).append(n)
SAME_FORM = {'Deoxys Atk': 'Deoxys-A', 'Deoxys Def': 'Deoxys-D', 'Deoxys Spe': 'Deoxys-S',
             'Darmanitan Z G': 'Darmanitan-GZ', 'Pumpkaboo X': 'Pumpkaboo-J', 'Gourgeist X': 'Gourgeist-J'}
Z_MEGAS = {1246: 'Absol-Z', 1248: 'Garchomp-Z', 1249: 'Lucario-Z'}
species = {i: natdex_names[i] for i in range(1, 412)}
for i in range(412, 1281):
    n = ext_names[i]
    if i in Z_MEGAS:
        assert key(n) == key(Z_MEGAS[i].replace('-Z', ' M')), (i, n)
        species[i] = Z_MEGAS[i]
    elif n in SAME_FORM:
        assert natdex_names[i] == SAME_FORM[n], (i, n, natdex_names[i])
        species[i] = SAME_FORM[n]
    else:
        found = by_key.get(key(n), [])
        assert len(found) == 1, (i, n, found)
        species[i] = found[0]
    if i <= 1235:
        assert species[i] == natdex_names[i], (i, species[i], natdex_names[i])
real = [n for i, n in species.items() if not 252 <= i <= 276]   # Gen 3's 25 unused slots read "none" in both builds
assert len(set(real)) == len(real) == 1255, 'species names must be unique'
write_tsv(res / 'maxdex/species.tsv', [(i, species[i]) for i in range(1, 1281)])

# ---------------------------------------------------------------- moves
natdex_moves = {i: v[0] for i, v in read_tsv(res / 'natdex/moves.tsv').items()}
renames = dict((int(a), b) for a, b in re.findall(r'moveNames\[(\d+)\] = "([^"]+)"', src))
assert len(renames) == 21, len(renames)
for i, n in renames.items():
    assert natdex_moves[i] == n, (i, n, natdex_moves[i])
ext_moves = run('self.Data.natDexMoves = {')
moves = {i: natdex_moves[i] for i in range(1, 355)}
for _, m in sorted(dict(ext_moves).items(), key=lambda kv: int(kv[0])):
    moves[int(m['id'])] = m['name']
assert sorted(moves) == list(range(1, 842)), (min(moves), max(moves), len(moves))
known = set(natdex_moves.values())
missing = [n for n in moves.values() if n not in known]
assert not missing, missing[:10]
assert moves[361] == 'Roost' and moves[578] == 'Freeze-Dry' and moves[841] == 'Malignant Chain'
write_tsv(res / 'maxdex/moves.tsv', [(i, moves[i]) for i in range(1, 842)])

# ---------------------------------------------------------------- abilities
abil_src = io.open(clone / 'maxdex/MaxDexAbilities.lua', encoding='utf-8').read()
abil = lua.eval(literal(abil_src, 'MaxDexAbilities = {'))
abilities = {int(a['id']): a['name'] for _, a in dict(abil).items()}
assert sorted(abilities) == list(range(1, 290)), len(abilities)
for i, n in re.findall(r'abilNames\[(\d+)\] = "([^"]+)"', src):
    abilities[int(i)] = n
assert abilities[14] == 'Compound Eyes' and abilities[31] == 'Lightning Rod' and abilities[289] == 'Poison Puppeteer'
write_tsv(res / 'maxdex/abilities.tsv', [(i, abilities[i]) for i in range(1, 290)])

# ---------------------------------------------------------------- evolutions and weights
mons = run('self.Data.natDexMons = {')
new_rows = []
for k in sorted(int(x) for x in dict(mons).keys()):
    m = mons[k]
    evo = m['evolution']
    w = m['weight']
    new_rows.append(('' if evo in (None, 'NONE') else str(evo), '' if w is None else ('%.1f' % float(w))))
assert len(new_rows) == 869, len(new_rows)
over = {}
for n, val in re.findall(r'mon\[\s*(\d+)\]\.evolution\s*=\s*(PE\.[A-Z0-9_]+|"[0-9]+")', src):
    over[int(n)] = val[3:] if val.startswith('PE.') else val.strip('"')
vanilla = read_tsv(res / 'gen3/species-extra.tsv')
assert sorted(vanilla) == list(range(1, 412))
extra = []
for sid in range(1, 412):
    name, evo, weight = vanilla[sid][:3]
    if sid in over:
        evo = over[sid]
    extra.append((sid, name, evo, weight))
for k, (evo, weight) in enumerate(new_rows):
    sid = 412 + k
    if sid in over:
        assert over[sid] == evo, (sid, evo, over[sid])
    extra.append((sid, species[sid], evo, weight))
write_tsv(res / 'gen3/species-extra-maxdex.tsv', extra)

# ---------------------------------------------------------------- ability scripts (FireRed block)
fr = src[src.index('if GS.game == 3 then', src.index('function self.updateGameSettings()')):]
fr = fr[:fr.index('elseif GS.game == 2 then')]
scripts = []
for kind, base, off, body in re.findall(r'GS\.ABILITIES\.([A-Z_]+)\[0x([0-9a-fA-F]+) \+ 0x([0-9a-fA-F]+)\] = \{([^}]*)\}', fr):
    ids = [int(x) for x in re.findall(r'\[(\d+)\]\s*=\s*true', body)]
    scope = re.search(r'scope\s*=\s*"(\w+)"', body)
    scripts.append(('%X' % (int(base, 16) + int(off, 16)), kind, ','.join(str(i) for i in ids), scope.group(1) if scope else ''))
firered11 = [l.rstrip('\r\n').split(tab) for l in io.open(res / 'gen3/abilityscripts-firered11.tsv', encoding='utf-8') if l.strip()]
assert len(scripts) == len(firered11) == 42, (len(scripts), len(firered11))
for a, b in zip(scripts, firered11):
    assert a[1:] == tuple(b[1:4]), (a, b)   # the same triggers and abilities, at MaxDex's addresses
write_tsv(res / 'gen3/abilityscripts-maxdex.tsv', scripts)

# ---------------------------------------------------------------- random evolutions
revo = lua.eval(literal(src, 'PokemonRevoData.RevoData = {'))
rows = []
for base in sorted(int(k) for k in dict(revo).keys()):
    entry = revo[base]
    options = entry['options']
    if options is None:
        pairs = [(int(v['id']), float(v['perc'])) for _, v in sorted(dict(entry).items(), key=lambda kv: int(kv[0]))]
        rows.append((base, 0, ','.join('%d:%g' % p for p in pairs)))
    else:
        for _, target in sorted(dict(options).items(), key=lambda kv: int(kv[0])):
            table = entry[int(target)]
            pairs = [(int(v['id']), float(v['perc'])) for _, v in sorted(dict(table).items(), key=lambda kv: int(kv[0]))]
            rows.append((base, int(target), ','.join('%d:%g' % p for p in pairs)))
assert all(0 < r[0] <= 1280 for r in rows)
write_tsv(res / 'gen3/revos-maxdex.tsv', rows,
          header='# base id' + tab + 'target evo id (0 = single evolution)' + tab +
                 'evo:percent,...  (MaxDexExtension.lua 1.0, converted by tools/trainer-data/convert_maxdex.py)')

# ---------------------------------------------------------------- icons
dest = root / 'app/src/main/assets/gbasprites-maxdex'
if dest.exists():
    shutil.rmtree(dest)
dest.mkdir(parents=True)
icons = sorted((clone / 'maxdex/sprites_mons').glob('*.png'), key=lambda p: int(p.stem))
assert [int(p.stem) for p in icons] == list(range(412, 1281)), 'sprites_mons must be 412.png to 1280.png'
for p in icons:
    shutil.copyfile(p, dest / p.name)
print('copied', len(icons), 'icons to', dest)
