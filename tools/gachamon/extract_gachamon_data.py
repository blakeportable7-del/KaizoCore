"""GachaMon's reference data, from a clone of besteon/Ironmon-Tracker (9.3.x) and the Nat. Dex extension.

    python tools/gachamon/extract_gachamon_data.py <ironmon-ref dir> <repo root>

<ironmon-ref dir> holds Ironmon-Tracker/ and NatDexExtension/ (C:/Users/bepor/ironmon-ref here). Writes:

  tracker-gba/src/main/resources/gachamon/GachaMonRatingSystem.json
      ironmon_tracker/data/GachaMonRatingSystem.json, copied byte for byte: every rating, threshold and ruleset the
      cards are scored with (GachaMonFileManager.importRatingSystem reads the same file).
  tracker-gba/src/main/resources/gachamon/bst.tsv
      id, the base stat total PokemonData.lua lists for it (the five games), and the Nat. Dex extension's (its
      updatePokeData changes to the base game's, then natDexMons from 412). The ruleset's banned-ability exceptions
      read this static total, not the ROM's (GachaMonData.calculateRatingScore: pokemonInternal.bst).
  tracker-gba/src/main/resources/gachamon/variable-power.tsv
      MoveData.lua's variablepower moves and the power text the reference keeps for them (buildData never overwrites
      it from the ROM): what getExpectedPower and Utils.isSTAB read for those moves.
"""
import re
import shutil
import sys
from pathlib import Path

ref = Path(sys.argv[1])
repo = Path(sys.argv[2])
tracker = ref / 'Ironmon-Tracker' / 'ironmon_tracker'
out = repo / 'tracker-gba' / 'src' / 'main' / 'resources' / 'gachamon'
out.mkdir(parents=True, exist_ok=True)

shutil.copyfile(tracker / 'data' / 'GachaMonRatingSystem.json', out / 'GachaMonRatingSystem.json')

def entries(block):
    """The top-level { ... } entries of a Lua list, in order."""
    items, depth, start = [], 0, None
    for i, ch in enumerate(block):
        if ch == '{':
            if depth == 0:
                start = i
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0 and start is not None:
                items.append(block[start:i + 1])
                start = None
    return items

def list_after(src, marker):
    at = src.index(marker) + len(marker)
    depth, i = 1, at
    while depth:
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
        i += 1
    return src[at:i - 1]

def bst_of(entry):
    m = re.search(r'\bbst\s*=\s*(\d+)', entry)
    return int(m.group(1)) if m else None

pokemon_src = (tracker / 'data' / 'PokemonData.lua').read_text(encoding='utf-8')
vanilla = [bst_of(e) for e in entries(list_after(pokemon_src, 'PokemonData.Pokemon = {'))]
assert len(vanilla) == 411, len(vanilla)
assert vanilla[0] == 318 and vanilla[5] == 534 and vanilla[409] == 600 and vanilla[410] == 425, (vanilla[0], vanilla[5], vanilla[409], vanilla[410])  # Deoxys, then Chimecho

natdex_src = (ref / 'NatDexExtension' / 'NatDexExtension.lua').read_text(encoding='utf-8')
natdex = list(vanilla)
update = natdex_src[natdex_src.index('function self.updatePokeData()'):natdex_src.index('function self.updateMoveData()')]
for m in re.finditer(r'mon\[\s*(\d+)\]\.bst\s*=\s*(\d+)', update):
    natdex[int(m.group(1)) - 1] = int(m.group(2))
added = [bst_of(e) for e in entries(list_after(natdex_src, 'self.Data.natDexMons = {'))]
natdex += added
assert natdex[24] == 320, natdex[24]   # Pikachu, raised in Gen 6
assert natdex[411] == 318, natdex[411]  # 412 is Turtwig

with open(out / 'bst.tsv', 'w', encoding='utf-8', newline='\n') as f:
    f.write('# id\tvanilla\tnatdex  (tools/gachamon/extract_gachamon_data.py; blank where the list has none)\n')
    for i, nd in enumerate(natdex, start=1):
        v = vanilla[i - 1] if i <= len(vanilla) else None
        f.write(f"{i}\t{'' if v is None else v}\t{'' if nd is None else nd}\n")

move_src = (tracker / 'data' / 'MoveData.lua').read_text(encoding='utf-8')
rows = []
for e in entries(list_after(move_src, 'MoveData.Moves = {')):
    if 'variablepower = true' not in e:
        continue
    mid = int(re.search(r'\bid\s*=\s*"(\d+)"', e).group(1))
    power = re.search(r'\bpower\s*=\s*"([^"]*)"', e).group(1)
    rows.append((mid, power))
assert len(rows) == 25, len(rows)
with open(out / 'variable-power.tsv', 'w', encoding='utf-8', newline='\n') as f:
    f.write('# move id\tpower text  (MoveData.lua variablepower moves; tools/gachamon/extract_gachamon_data.py)\n')
    for mid, power in rows:
        f.write(f'{mid}\t{power}\n')
print('species', len(natdex), 'variable-power', len(rows))
