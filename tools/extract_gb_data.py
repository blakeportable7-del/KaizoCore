"""Gen 1 and Gen 2 species and move facts for the Game Boy trackers' info screens.

The PC trackers keep these as tables, not ROM reads, and the panel's lookups
(Pokemon info: weight, evolution; move info: the summary) show them. So they
are taken from the references' own data, the way gen3/species-extra.tsv and
gen3/movedesc.tsv were for the GBA tracker:

  Gen 1: ~/ironmon-ref/Ironmon-gen-tracker/ironmon_tracker/data   (v1.2.2, Red/Blue/Yellow)
  Gen 2: ~/ironmon-ref/Ironmon-gen-2-tracker/ironmon_tracker/data (v0.4, Crystal)

  PokemonData.lua  PokemonData.Pokemon, one entry per species in dex order:
                   `evolution` and `weight` (kg). Gen 1 reads 1..151, Gen 2 1..251.
  MoveData.lua     MoveData.Moves, entries with an explicit id: `summary`.
                   Gen 1 reads 1..165, Gen 2 1..251.

Types, base stats, move power/type/accuracy/PP and the learn levels are NOT
taken: the trackers read those out of the randomized ROM (Gen1Tracker,
GbcTracker), because a randomizer changes them and a table would not know.

Evolution is written in the vocabulary the app's EvoText already reads
(gen3/species-extra.tsv): a bare level, or the PokemonData.Evolutions key
(FRIEND, THUNDER, MOON, WATER37 ...), empty when it does not evolve. The Lua
comments some rows carry ("37", -- Level 37 replaces trade evolution) are
dropped, and STONES (Eevee's "STONE") is written EEVEE_STONES, the key the
Gen 3 table uses for the same abbreviation.

Output: tracker-gba/src/main/resources/gen1/{species-extra,movedesc}.tsv and
gen2/{species-extra,movedesc}.tsv:

  species-extra.tsv  id TAB Name TAB evolution TAB weight
  movedesc.tsv       id TAB Name TAB summary
"""
import re
import sys
from pathlib import Path

REFS = Path.home() / "ironmon-ref"
OUT = Path(__file__).resolve().parent.parent / "tracker-gba/src/main/resources"

# Constants.lua: Constants.Words.POKEMON = "Pok" .. Constants.getC("é") .. "mon"
WORDS = {"Constants.Words.POKEMON": "Pokémon"}

SPECIES = re.compile(r"\{\s*name\s*=\s*\"([^\"]*)\"(.*?)\n\s*\},?", re.S)
MOVE = re.compile(r"\{\s*(?:--[^\n]*\n\s*)?id\s*=\s*\"(\d+)\",(.*?)\n\s*\},?", re.S)


def lua_string(expr):
    """A Lua string expression of quoted parts joined by `..` with the Words constants."""
    parts = []
    for tok in re.findall(r"\"((?:[^\"\\]|\\.)*)\"|(Constants\.Words\.[A-Z_]+)", expr):
        if tok[0] or not tok[1]:
            parts.append(tok[0])
        else:
            parts.append(WORDS[tok[1]])
    return "".join(parts)


def evolution(raw):
    v = raw.split("--")[0].strip().rstrip(",").strip()
    if v.startswith("\""):
        return v.strip("\"")
    key = v.replace("PokemonData.Evolutions.", "")
    if key == "NONE":
        return ""
    return "EEVEE_STONES" if key == "STONES" else key


def species(ref, count):
    text = (REFS / ref / "ironmon_tracker/data/PokemonData.lua").read_text(encoding="utf-8", errors="replace")
    body = text[text.index("PokemonData.Pokemon = {"):]
    rows = []
    for i, m in enumerate(SPECIES.finditer(body)):
        if i >= count:
            break
        name, rest = m.group(1), m.group(2)
        evo = re.search(r"evolution\s*=\s*([^\n]+)", rest).group(1)
        weight = re.search(r"weight\s*=\s*([0-9.]+)", rest).group(1)
        rows.append("%d\t%s\t%s\t%s" % (i + 1, name, evolution(evo), weight))
    assert len(rows) == count, (ref, len(rows))
    return rows


def moves(ref, count):
    text = (REFS / ref / "ironmon_tracker/data/MoveData.lua").read_text(encoding="utf-8", errors="replace")
    body = text[text.index("MoveData.Moves = {"):]
    got = {}
    for m in MOVE.finditer(body):
        mid = int(m.group(1))
        if mid > count:
            continue
        name = re.search(r"name\s*=\s*\"([^\"]*)\"", m.group(2)).group(1)
        summary = re.search(r"summary\s*=\s*([^\n]*?),?\s*$", m.group(2), re.M)
        got[mid] = (name, lua_string(summary.group(1)).strip() if summary else "")
    assert sorted(got) == list(range(1, count + 1)), (ref, sorted(set(range(1, count + 1)) - set(got)))
    return ["%d\t%s\t%s" % (i, got[i][0], got[i][1]) for i in range(1, count + 1)]


def write(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(("\r\n".join(rows) + "\r\n").encode("utf-8"))
    print("%-28s %3d rows   first: %s" % (path.relative_to(OUT), len(rows), rows[0]))


def main():
    write(OUT / "gen1/species-extra.tsv", species("Ironmon-gen-tracker", 151))
    write(OUT / "gen1/movedesc.tsv", moves("Ironmon-gen-tracker", 165))
    write(OUT / "gen2/species-extra.tsv", species("Ironmon-gen-2-tracker", 251))
    write(OUT / "gen2/movedesc.tsv", moves("Ironmon-gen-2-tracker", 251))
    return 0


if __name__ == "__main__":
    sys.exit(main())
