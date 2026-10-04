"""Move descriptions for the Nat. Dex builds' moves past Gen 3 (ids 355-847), from the references, 2026-10-03.

    python -B tools/trainer-data/convert_natdex_move_desc.py [NatDexExtension.lua]

Writes tracker-gba/src/main/resources/natdex/movedesc.tsv in gen3/movedesc.tsv's shape: move id, the name
natdex/moves.tsv gives it, the description, then which source the words are from. Ids 1-354 are not in it: they
keep gen3/movedesc.tsv's text on every build. The words are the source's own, with two changes: a British spelling
is respelled through build_rules.american() (the run stops if one is left), and an em dash becomes a comma,
KaizoCore's copy rule. Three sources, in this order, then KaizoCore's own lines:

  natdex    The Nat. Dex Extension's natDexMoveDescriptions (NatDexExtension.lua 1.2.1 by CyanSMP64, used with
            permission), RUN in Lua, not parsed by pattern. It has an entry for every id 355-847, but 482 of the 493
            read "Not implemented yet." That is the extension's placeholder, not a description, so those are left
            out and the next source is asked. The PC tracker shows the placeholder (MoveData.updateResources).
  nds       The DS tracker's MoveData (NDS-Ironmon-Tracker), its Gen 5 wording, for a move Black and White already
            had, matched by name: the same text tracker-nds/src/main/resources/nds/move-desc.tsv carries (checked
            here), which the DS tracker's move info shows.
  showdown  Pokemon Showdown's move text, MIT (sources/showdown-moves.ts, data/text/moves.ts pinned to one commit;
            sources/showdown-moves.PINNED.txt has the commit, the date, the SHA-256 and the license). Its shortDesc,
            matched by name, never its desc: the desc is a rules list (Doodle's ran to 837 characters), not what the
            game says (Blake, 2026-10-04). The file is READ as data, never run: a small reader below takes its
            object literal (names, strings, nested objects, comments) and stops on anything else.
  kaizocore KaizoCore's own line (OWN below) for a move whose shortDesc says nothing a player can use ("No
            competitive use.", "No additional effect.").

Every move must end up with a description: the run stops if one is left, on Nat. Dex or on MaxDex. Nothing from
Showdown or OWN may be longer than the longest description in gen3/movedesc.tsv (Magnitude's, 174 characters).

MaxDex has no file of its own: it numbers its moves its own way (Fairy Wind is 358 there, 584 here), so GbaTracker
finds a MaxDex move's text by its name in this file. The run checks every maxdex/moves.tsv name past 354 is here.
"""
import hashlib
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE.parent / "rules"))
import extract_gen5_data as g5                      # TRACKER (the DS reference), write_rows
from build_rules import american                    # the repo's one respelling step
from lupa import LuaRuntime, lua_type

EXTENSION = Path.home() / "ironmon-ref/NatDexExtension/NatDexExtension.lua"
SHOWDOWN = HERE / "sources/showdown-moves.ts"
SHOWDOWN_SHA256 = "811028921074d1fd8ea8798bc0f0e29602a59f639e58bdf53b0eedf3fdcf54e5"   # its LF bytes, PINNED.txt
RES = ROOT / "tracker-gba/src/main/resources"
OUT = RES / "natdex/movedesc.tsv"
NDS_TSV = ROOT / "tracker-nds/src/main/resources/nds/move-desc.tsv"
PLACEHOLDER = "Not implemented yet."
FIRST = 355                                         # the first move past Gen 3's 354
EM_DASH = chr(0x2014)
POKEMON = "Pok" + chr(0xE9) + "mon"

# Showdown's shortDesc that says nothing a player can use. Each move it is given has a line in OWN.
EMPTY = {"No competitive use.", "No additional effect."}

# KaizoCore's own lines, in the games' plain style (Blake, 2026-10-04, who gave the wording of three of them). The
# source column credits them as "kaizocore". Each must stay true to the move as Nat. Dex plays it.
OWN = {
    "Happy Hour": "Doubles the prize money from the battle.",
    "Celebrate": "The " + POKEMON + " congratulates you. It has no effect in battle.",
    "Hold Hands": "The user and an ally hold hands. It has no effect in battle.",
    "High Horsepower": "Inflicts regular damage with no additional effect.",
    "Leafage": "Inflicts regular damage with no additional effect.",
    "Dragon Hammer": "Inflicts regular damage with no additional effect.",
    "Dynamax Cannon": "Inflicts regular damage with no additional effect.",
    "Behemoth Blade": "Inflicts regular damage with no additional effect.",
    "Behemoth Bash": "Inflicts regular damage with no additional effect.",
    "Branch Poke": "Inflicts regular damage with no additional effect.",
}

# What AmericanSpellingTest fails, in short: a hit here means american() needs another line.
BRITISH = re.compile(r"\b(\w*colour\w*|\w*favour\w*|\w*behaviour\w*|\w*honour\w*|\w*neighbour\w*|\w*armour\w*|"
                     r"licences?|licenced|defences?|offences?|centre[sd]?|metres?|grey(s|ed|ing)?|\w*recognis\w*|"
                     r"\w*(initiali|normali|randomi|maximi|minimi|neutrali|immobili|utili)s(e|ed|es|ing|ation)|"
                     r"paralys(e|ed|es|ing)|analys(e|ed|ing)|learnt|levell\w*|travell\w*|cancell\w*|labell\w*|"
                     r"whilst|amongst|artefacts?|judgements?|ageing)\b", re.IGNORECASE)


def key(name):
    """A move name as a match key: case, spaces and punctuation ignored ("U-turn" and "U-Turn" are one move)."""
    return re.sub(r"[^a-z0-9]", "", name.lower())


def tsv(path):
    rows = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        p = line.split("\t")
        if p[0].isdigit():
            rows[int(p[0])] = p
    return rows


def extension_descriptions(path):
    """natDexMoveDescriptions as the extension builds it: {id: (NameKey, Description)}."""
    lines = path.read_text(encoding="utf-8").splitlines()
    start = next(i for i, l in enumerate(lines) if l.strip() == "self.Data.natDexMoveDescriptions = {")
    indent = lines[start][: len(lines[start]) - len(lines[start].lstrip())]
    end = next(i for i in range(start + 1, len(lines)) if lines[i] == indent + "}")
    lua = LuaRuntime(unpack_returned_tuples=True)
    table = lua.execute("return {\n" + "\n".join(lines[start + 1:end]) + "\n}")
    out = {}
    for mid, entry in table.items():
        out[int(mid)] = (str(entry["NameKey"]), str(entry["Description"]))
    return out


def ds_moves():
    """The DS tracker's moves with their Gen 5 wording, as convert_nds_move_desc.py runs them: {id: (name, text)}."""
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(
        "STANDIN = {}\n"
        "local function proxy() return setmetatable({}, STANDIN) end\n"
        "STANDIN.__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end\n"
        "STANDIN.__call = function(self, a, ...) if a ~= nil then return a end return proxy() end\n"
        "setmetatable(_G, {__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end})\n")
    for f in ("constants/Chars.lua", "constants/Graphics.lua", "constants/PokemonData.lua",
              "constants/MoveData.lua", "GameConfigurator.lua"):
        lua.execute((g5.TRACKER / f).read_text(encoding="utf-8"))
    G = lua.globals()
    G.GameConfigurator.initMoveData(lua.table_from({"GEN": 5}))
    out = {}
    for index, m in G.MoveData.MOVES.items():
        if index == 1:
            continue                                     # the empty entry for move id 0
        name, d = m["name"], m["description"]
        if lua_type(name) is not None or d is None or lua_type(d) is not None:
            sys.exit("DS move %d: name or description is not a plain string" % (index - 1))
        out[index - 1] = (str(name), str(d).replace("\t", " ").replace("\r", " ").replace("\n", " "))
    return out


class TsObject:
    """The object literal a Showdown data/text file holds, read as data: keys (bare or quoted), string values,
    nested objects, commas and comments. Anything else stops the run, so a change in the file's shape is never
    misread. Nothing in the file is run."""
    IDENT = re.compile(r"[A-Za-z_$][\w$]*")
    ESC = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f", "v": "\v", "0": "\0"}

    def __init__(self, src, at):
        self.s, self.i = src, at

    def fail(self, what):
        sys.exit("showdown-moves.ts line %d: %s" % (self.s.count("\n", 0, self.i) + 1, what))

    def skip(self):
        while True:
            while self.i < len(self.s) and self.s[self.i] in " \t\r\n":
                self.i += 1
            if self.s.startswith("//", self.i):
                self.i = self.s.index("\n", self.i)
            elif self.s.startswith("/*", self.i):
                self.i = self.s.index("*/", self.i) + 2
            else:
                return

    def string(self):
        quote, self.i, out = self.s[self.i], self.i + 1, []
        while True:
            c = self.s[self.i]
            if c == quote:
                self.i += 1
                return "".join(out)
            if c == "\n":
                self.fail("a string runs past its line")
            if c == "\\":
                n = self.s[self.i + 1]
                if n == "u":
                    out.append(chr(int(self.s[self.i + 2:self.i + 6], 16)))
                    self.i += 6
                elif n == "x":
                    out.append(chr(int(self.s[self.i + 2:self.i + 4], 16)))
                    self.i += 4
                else:
                    out.append(self.ESC.get(n, n))
                    self.i += 2
                continue
            out.append(c)
            self.i += 1

    def parse(self):
        self.skip()
        if self.s[self.i] != "{":
            self.fail("an object was expected")
        self.i += 1
        out = {}
        while True:
            self.skip()
            c = self.s[self.i]
            if c == "}":
                self.i += 1
                return out
            if c in "\"'":
                k = self.string()
            else:
                m = self.IDENT.match(self.s, self.i)
                if not m:
                    self.fail("a key was expected, not %r" % c)
                k, self.i = m.group(0), m.end()
            self.skip()
            if self.s[self.i] != ":":
                self.fail("':' was expected after %r" % k)
            self.i += 1
            self.skip()
            c = self.s[self.i]
            if c == "{":
                v = self.parse()
            elif c in "\"'":
                v = self.string()
            else:
                self.fail("a string or an object was expected for %r, not %r" % (k, c))
            if k in out:
                self.fail("%r twice" % k)
            out[k] = v
            self.skip()
            if self.s[self.i] == ",":
                self.i += 1
            elif self.s[self.i] != "}":
                self.fail("',' or '}' was expected after %r" % k)


def showdown_moves(path):
    """Showdown's MovesText: {key(name): (name, shortDesc)}, the current text (not a genN one), never the desc."""
    raw = path.read_bytes().replace(b"\r\n", b"\n")      # a Windows checkout may hand it over with CRLF
    if hashlib.sha256(raw).hexdigest() != SHOWDOWN_SHA256:
        sys.exit("%s is not the pinned file: see sources/showdown-moves.PINNED.txt" % path.name)
    src = raw.decode("utf-8")
    reader = TsObject(src, src.index("= {", src.index("export const MovesText")) + 2)
    entries = reader.parse()
    reader.skip()
    if not src.startswith(";", reader.i):
        reader.fail("the object should end the file")
    out = {}
    for sid, e in entries.items():
        if not isinstance(e, dict) or not isinstance(e.get("name"), str):
            sys.exit("Showdown entry %s has no name" % sid)
        short = e["shortDesc"].strip() if isinstance(e.get("shortDesc"), str) else ""
        out.setdefault(key(e["name"]), (e["name"], short))
    return out


def no_em_dash(text):
    """KaizoCore's copy rule is no em dashes: one between words becomes a comma."""
    return re.sub(r"\s*" + EM_DASH + r"\s*", ", ", text)


def main():
    ext_path = Path(sys.argv[1]) if len(sys.argv) > 1 else EXTENSION
    natdex = {mid: p[1] for mid, p in tsv(RES / "natdex/moves.tsv").items()}
    maxdex = {mid: p[1] for mid, p in tsv(RES / "maxdex/moves.tsv").items()}
    ext = extension_descriptions(ext_path)

    if sorted(ext) != list(range(FIRST, max(natdex) + 1)):
        sys.exit("natDexMoveDescriptions holds ids %d-%d, natdex/moves.tsv %d-%d" % (min(ext), max(ext), FIRST, max(natdex)))
    for mid, (name, _) in ext.items():
        if key(name) != key(natdex[mid]):
            sys.exit("move %d is %r in the extension's descriptions and %r in natdex/moves.tsv" % (mid, name, natdex[mid]))

    ds = ds_moves()
    shipped = tsv(NDS_TSV)
    for mid, (_, text) in ds.items():
        if mid in shipped and shipped[mid][2] != text:
            sys.exit("DS move %d: MoveData's Gen 5 text is not nds/move-desc.tsv's; rerun convert_nds_move_desc.py" % mid)
    ds_by_name = {}
    for mid, (name, text) in ds.items():
        ds_by_name.setdefault(key(name), (mid, text))
    showdown = showdown_moves(SHOWDOWN)
    cap = max(len(p[2]) for p in tsv(RES / "gen3/movedesc.tsv").values())
    own = {key(n): t for n, t in OWN.items()}

    rows = ["# move id\tname\tdescription\tsource (natdex: NatDexExtension.lua natDexMoveDescriptions; "
            "nds: NDS-Ironmon-Tracker MoveData, Gen 5; showdown: Pokemon Showdown data/text/moves.ts shortDesc, MIT; "
            "kaizocore: KaizoCore's own line). "
            "Made by tools/trainer-data/convert_natdex_move_desc.py"]
    counts = {"natdex": 0, "nds": 0, "showdown": 0, "kaizocore": 0}
    left, respelled, moved, dashed, used_own = [], [], [], [], set()
    for mid in range(FIRST, max(natdex) + 1):
        name = natdex[mid]
        text, source = ext[mid][1].strip(), "natdex"
        if text == PLACEHOLDER:
            text, source = "", ""
        if not text:
            hit = ds_by_name.get(key(name))
            if hit:
                text, source = hit[1].strip(), "nds"
                if hit[0] != mid:
                    moved.append("%d %s (DS id %d)" % (mid, name, hit[0]))
        if not text:
            hit = showdown.get(key(name))
            if hit and hit[1]:
                text, source = hit[1], "showdown"
                if text in EMPTY:
                    if key(name) not in own:
                        sys.exit("move %d %s: Showdown's shortDesc is %r; give it a line in OWN" % (mid, name, text))
                    text, source = own[key(name)], "kaizocore"
                    used_own.add(key(name))
                if len(text) > cap:
                    sys.exit("move %d %s: %d characters, longer than gen3/movedesc.tsv's longest (%d)" % (mid, name, len(text), cap))
        if not text:
            left.append("%d %s" % (mid, name))
            continue
        plain = no_em_dash(text)
        if plain != text:
            dashed.append("%d %s" % (mid, name))
        us = american(plain)
        if us != plain:
            respelled.append("%d %s: %r -> %r" % (mid, name, plain, us))
        if BRITISH.search(us):
            sys.exit("move %d %s keeps a British spelling (%s): add it to build_rules.AMERICAN" % (mid, name, BRITISH.search(us).group(0)))
        if "\t" in us or "\n" in us or EM_DASH in us or PLACEHOLDER.lower() in us.lower():
            sys.exit("move %d %s: a tab, a line break, an em dash or the placeholder in the text" % (mid, name))
        counts[source] += 1
        rows.append("%d\t%s\t%s\t%s" % (mid, name, us, source))

    if left:
        sys.exit("no source describes %d moves: %s" % (len(left), ", ".join(left)))
    if set(own) != used_own:
        sys.exit("OWN lines no move took: " + ", ".join(n for n in OWN if key(n) not in used_own))
    g5.write_rows(OUT, rows)
    back = tsv(OUT)
    if len(back) != len(rows) - 1:
        sys.exit("natdex/movedesc.tsv does not read back")

    described = {key(p[1]) for p in back.values()}
    natdex_keys = {key(n) for mid, n in natdex.items() if mid >= FIRST}
    unmatched = [("%d %s" % (mid, n)) for mid, n in maxdex.items() if mid >= FIRST and key(n) not in natdex_keys]
    if unmatched:
        sys.exit("MaxDex moves with no Nat. Dex name to match: " + ", ".join(unmatched))
    maxdex_left = [("%d %s" % (mid, n)) for mid, n in maxdex.items() if mid >= FIRST and key(n) not in described]
    if maxdex_left:
        sys.exit("MaxDex moves with no description: " + ", ".join(maxdex_left))

    print("Nat. Dex %d-%d: %d moves: %d from the extension, %d from the DS tracker, %d from Showdown, %d KaizoCore's "
          "own, none left" % (FIRST, max(natdex), max(natdex) - FIRST + 1, counts["natdex"], counts["nds"],
                              counts["showdown"], counts["kaizocore"]))
    longest = max((len(p[2]), p[1], p[3]) for p in back.values() if p[3] in ("showdown", "kaizocore"))
    print("cap %d (gen3/movedesc.tsv's longest); longest from Showdown or OWN: %s, %d (%s)" % (cap, longest[1], longest[0], longest[2]))
    print("MaxDex %d-%d: %d moves, every one described by name" % (FIRST, max(maxdex), max(maxdex) - FIRST + 1))
    for label, items in (("DS text matched at another id", moved), ("em dash made a comma", dashed),
                         ("respelled", respelled)):
        if items:
            print("%s (%d): %s" % (label, len(items), "; ".join(items)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
