"""Shared readers for the Generation 1 Nuzlocke data scripts (gen1_*.py).

Read only: the pret disassemblies (pokered, pokeyellow) and the clean ROM dumps (red-u, blue-u, yellow-u).
Nothing here writes a file. Every reader that has a ROM twin lives in class Rom, so a script can ask the
disassembly and the ROM the same question and compare the answers.

The three ROM dumps are byte-identical builds of pokered, pokeblue and pokeyellow (their SHA-1 is the one
in each repository's roms.sha1; Rom.verify_build() checks it), so the two views must agree exactly.

Builds: "red", "blue", "yellow". Game keys used in the data files: "rb" (Red and Blue), "y" (Yellow).
"""
import hashlib
import os
import re
from pathlib import Path

REFS = Path(os.environ.get("NZ_REFS", "C:/Users/bepor/ironmon-ref"))
ROMS = Path(os.environ.get("NZ_ROMS", "C:/Users/bepor/IronMonOne/.vendor/roms"))
ROOT = Path(__file__).resolve().parents[2]
NZ_DIR = ROOT / "tracker-gba/src/main/resources/nuzlocke"
SPECIES_TSV = ROOT / "tracker-gba/src/main/resources/natdex/species.tsv"
INI_PATH = ROOT / "engine-zx/src/com/dabomstew/pkrandomzx/config/gen1_offsets.ini"
DATE = "2026-09-29"

# build -> (disassembly folder, ROM file, symbols the assembler defines for that build, sha1 of the ROM)
BUILDS = {
    "red": ("pokered", "red-u.gbc", {"_RED"}, "ea9bcae617fdf159b045185467ae58b2e4a48b9a"),
    "blue": ("pokered", "blue-u.gbc", {"_BLUE"}, "d7037c83e1ae5b39bde3c30787637ba1d4c48ce2"),
    "yellow": ("pokeyellow", "yellow-u.gbc", {"_YELLOW"}, "cc7d03262ebfaf2f06772c1a480c7d9d5f4a38e1"),
}
GAME_OF = {"red": "rb", "blue": "rb", "yellow": "y"}
INI_SECTION = {"red": "Red (U)", "blue": "Blue (U)", "yellow": "Yellow (U)"}

OPP_ID_OFFSET = 200


class Mismatch(Exception):
    pass


# --------------------------------------------------------------------------- asm text

def read_text(path):
    return Path(path).read_text(encoding="utf-8")


def _cond(expr, defs):
    e = re.sub(r"DEF\s*\(\s*(\w+)\s*\)", lambda m: "True" if m.group(1) in defs else "False", expr)
    e = e.replace("||", " or ").replace("&&", " and ").replace("!", " not ")
    if re.search(r"[A-Za-z_]", re.sub(r"\b(True|False|and|or|not)\b", "", e)):
        raise ValueError("cannot evaluate conditional: " + expr)
    return bool(eval(e, {"__builtins__": {}}, {}))


def asm_lines(path, defs=frozenset()):
    """Code lines of an asm file: comments stripped, IF/ELIF/ELSE/ENDC resolved for `defs`, MACRO bodies dropped."""
    out = []
    stack = []          # per open IF: [parent_active, taken, active]
    in_macro = False
    for raw in read_text(path).splitlines():
        line = raw.split(";")[0].strip()
        if not line:
            continue
        head = line.split(None, 1)[0].upper()
        if in_macro:
            if head == "ENDM":
                in_macro = False
            continue
        if head in ("MACRO", "MACRO?"):
            in_macro = True
            continue
        arg = line[len(head):].strip()
        if head == "IF":
            parent = all(s[2] for s in stack)
            v = _cond(arg, defs) if parent else False
            stack.append([parent, v, v])
            continue
        if head == "ELIF":
            s = stack[-1]
            v = (not s[1]) and s[0] and _cond(arg, defs)
            s[1] = s[1] or v
            s[2] = v
            continue
        if head == "ELSE":
            s = stack[-1]
            s[2] = s[0] and not s[1]
            s[1] = True
            continue
        if head == "ENDC":
            stack.pop()
            continue
        if all(s[2] for s in stack):
            out.append(line)
    if stack:
        raise ValueError("unbalanced IF in " + str(path))
    return out


def _num(tok):
    tok = tok.strip()
    if tok.startswith("$"):
        return int(tok[1:], 16)
    if tok.startswith("%"):
        return int(tok[1:], 2)
    return int(tok)


def eval_expr(expr, env):
    """Evaluate a small RGBDS integer expression against `env` (a name -> int dict)."""
    e = re.sub(r"\$([0-9A-Fa-f]+)", lambda m: str(int(m.group(1), 16)), expr)
    e = re.sub(r"%([01]+)\b", lambda m: str(int(m.group(1), 2)), e)
    e = e.replace("/", "//")
    return int(eval(e, {"__builtins__": {}}, env))


def parse_consts(path, defs=frozenset(), seed=None):
    """Names defined by const_def/const/const_skip/const_next/DEF ... EQU in one constants file."""
    env = dict(seed or {})
    val, inc = 0, 1
    for line in asm_lines(path, defs):
        m = re.match(r"const_def\b\s*(.*)", line)
        if m:
            args = [a.strip() for a in m.group(1).split(",") if a.strip()]
            val = eval_expr(args[0], env) if args else 0
            inc = eval_expr(args[1], env) if len(args) > 1 else 1
            continue
        m = re.match(r"(?:const|const_export)\s+(\w+)", line)
        if m:
            env[m.group(1)] = val
            val += inc
            continue
        m = re.match(r"trainer_const\s+(\w+)", line)
        if m:
            env[m.group(1)] = val
            env["OPP_" + m.group(1)] = OPP_ID_OFFSET + val
            val += inc
            continue
        m = re.match(r"map_const\s+(\w+)\s*,\s*(\d+)\s*,\s*(\d+)", line)
        if m:
            env[m.group(1)] = val
            env[m.group(1) + "_WIDTH"] = int(m.group(2))
            env[m.group(1) + "_HEIGHT"] = int(m.group(3))
            val += inc
            continue
        m = re.match(r"const_skip\b\s*(.*)", line)
        if m:
            val += inc * (eval_expr(m.group(1), env) if m.group(1).strip() else 1)
            continue
        m = re.match(r"const_next\s+(.*)", line)
        if m:
            val = eval_expr(m.group(1), env)
            continue
        m = re.match(r"DEF\s+(\w+)\s+(EQU|=|\+=)\s*(.*)", line)
        if m:
            e = dict(env)
            e["const_value"] = val
            try:
                v = eval_expr(m.group(3), e)
            except Exception:
                continue        # string equates and macros we do not need
            env[m.group(1)] = env.get(m.group(1), 0) + v if m.group(2) == "+=" else v
            continue
    env["const_value"] = val
    return env


# --------------------------------------------------------------------------- the randomizer's offsets

def parse_ini(build):
    """The Game Boy randomizer's offsets for one US build, honouring CopyFrom (a child's list replaces the parent's)."""
    sections = {}
    cur = None
    for raw in read_text(INI_PATH).splitlines():
        line = raw.strip()
        if not line or line.startswith("//"):
            continue
        m = re.match(r"\[(.*)\]$", line)
        if m:
            cur = m.group(1)
            sections[cur] = {}
            continue
        if cur is None or "=" not in line:
            continue
        k, v = line.split("=", 1)
        k = k.strip()
        v = v.strip()                     # StaticPokemon lines keep their `// Name` tail, see static_entries()
        sections[cur].setdefault(k, []).append(v)

    def resolve(name):
        s = dict(sections[name])
        parent = s.get("CopyFrom", [None])[0]
        if parent:
            p = resolve(parent)
            p.update(s)
            return p
        return s

    return resolve(INI_SECTION[build])


def static_entries(ini):
    """{name: (species file offsets, level file offsets)} from the ini's StaticPokemon{} lines (`// Snorlax 1` names them).

    The ghost Marowak line has no name and is returned as "Ghost Marowak"."""
    out = {}
    for key, default in (("StaticPokemon{}", None), ("StaticPokemonGhostMarowak{}", "Ghost Marowak")):
        for raw in ini.get(key, []):
            body, _, name = raw.partition("//")
            m = re.match(r"\{Species=\[(.*?)\],\s*Level=\[(.*?)\]\}", body.strip())
            if not m:
                continue
            sp = [int(x.strip(), 0) for x in m.group(1).split(",")]
            lv = [int(x.strip(), 0) for x in m.group(2).split(",")]
            out[name.strip() or default] = (sp, lv)
    return out


def ini_int(ini, key):
    return int(ini[key][0], 0)


def ini_list(ini, key):
    return [int(x.strip(), 0) for x in ini[key][0].strip("[]").split(",")]


# --------------------------------------------------------------------------- the disassembly

def norm_place(s):
    """A Town Map name as the game spells it -> ASCII Title Case ("MT.MOON@" -> "Mt. Moon")."""
    s = s.rstrip("@")
    fixed = {"MT.MOON": "Mt. Moon", "#MON LEAGUE": "Pokemon League", "#MON TOWER": "Pokemon Tower",
             "<PKMN> MANSION": "Pokemon Mansion", "S.S.ANNE": "S.S. Anne", "DIGLETT's CAVE": "Diglett's Cave",
             "ROCKET HQ": "Rocket HQ", "SILPH CO.": "Silph Co."}
    if s in fixed:
        return fixed[s]
    return " ".join(w.capitalize() for w in s.split(" "))


def norm_class(s):
    """A trainer class name as the game spells it -> ASCII Title Case ("JR.TRAINER" + male sign -> "Jr. Trainer M")."""
    s = s.replace("\u2642", " M").replace("\u2640", " F").replace("\u00e9", "e")
    fixed = {"JR.TRAINER M": "Jr. Trainer M", "JR.TRAINER F": "Jr. Trainer F", "PROF.OAK": "Prof. Oak",
             "LT.SURGE": "Lt. Surge", "COOLTRAINER M": "Cooltrainer M", "COOLTRAINER F": "Cooltrainer F"}
    if s in fixed:
        return fixed[s]
    return " ".join(w.capitalize() for w in s.split(" "))


class Disasm:
    """One pret disassembly, read for one build (red, blue or yellow) so IF DEF(_RED) blocks resolve right."""

    def __init__(self, build):
        self.build = build
        self.game = GAME_OF[build]
        self.dirname, self.rom_name, self.defs, self.sha1 = BUILDS[build]
        self.root = REFS / self.dirname
        self._cache = {}

    # -- plumbing
    def lines(self, rel):
        return asm_lines(self.root / rel, self.defs)

    def cached(self, key, fn):
        if key not in self._cache:
            self._cache[key] = fn()
        return self._cache[key]

    # -- maps
    def map_consts(self):
        return self.cached("mapconsts", lambda: parse_consts(self.root / "constants/map_constants.asm", self.defs))

    def maps(self):
        """[{id, const, w, h, group, indoor, unused}] in id order; group is the Town Map indoor group token."""
        def build():
            out, pending, first_indoor = [], [], None
            cur = 0
            for line in self.lines("constants/map_constants.asm"):
                m = re.match(r"map_const\s+(\w+)\s*,\s*(\d+)\s*,\s*(\d+)", line)
                if m:
                    d = dict(id=cur, const=m.group(1), w=int(m.group(2)), h=int(m.group(3)), group=None)
                    out.append(d)
                    pending.append(d)
                    cur += 1
                    continue
                m = re.match(r"end_indoor_group\s+(\w+)", line)
                if m:
                    for d in pending:
                        d["group"] = m.group(1)
                    pending = []
                    continue
                if re.match(r"DEF FIRST_INDOOR_MAP EQU const_value", line):
                    first_indoor = cur
                    pending = []
            for d in out:
                d["indoor"] = d["id"] >= first_indoor
                d["unused"] = d["const"].startswith("UNUSED_MAP")
            self._cache["first_indoor"] = first_indoor
            return out
        return self.cached("maps", build)

    def first_indoor(self):
        self.maps()
        return self._cache["first_indoor"]

    def map_by_const(self):
        return {m["const"]: m for m in self.maps()}

    def town_names(self):
        """(outdoor names by map id, indoor group -> name), Title Case, from data/maps/town_map_entries.asm and names.asm."""
        def build():
            names = {}
            for line in self.lines("data/maps/names.asm"):
                m = re.match(r"(\w+):\s*db\s+\"(.*)\"", line)
                if m:
                    names[m.group(1)] = norm_place(m.group(2))
            outdoor, groups = [], {}
            sect = None
            for line in self.lines("data/maps/town_map_entries.asm"):
                if line.startswith("ExternalMapEntries:"):
                    sect = "ext"
                elif line.startswith("InternalMapEntries:"):
                    sect = "int"
                m = re.match(r"outdoor_map\s+\d+\s*,\s*\d+\s*,\s*(\w+)", line)
                if m and sect == "ext":
                    outdoor.append(names[m.group(1)])
                m = re.match(r"indoor_map\s+(\w+)\s*,\s*\d+\s*,\s*\d+\s*,\s*(\w+)", line)
                if m and sect == "int":
                    groups[m.group(1)] = names[m.group(2)]
            return outdoor, groups
        return self.cached("townnames", build)

    def objects(self):
        """Per map constant: {'warps': [(x, y, dest const or LAST_MAP, dest warp id)], 'bg': [...], 'objs': [dict]}."""
        def build():
            out = {}
            d = self.root / "data/maps/objects"
            for f in sorted(d.glob("*.asm")):
                lines = asm_lines(f, self.defs)
                const = None
                for line in lines:
                    m = re.match(r"def_warps_to\s+(\w+)", line)
                    if m:
                        const = m.group(1)
                if const is None:
                    raise ValueError("no def_warps_to in " + f.name)
                rec = dict(file=f.name, warps=[], bg=[], objs=[])
                for line in lines:
                    m = re.match(r"warp_event\s+(\d+)\s*,\s*(\d+)\s*,\s*(\w+)\s*,\s*(\d+)", line)
                    if m:
                        rec["warps"].append((int(m.group(1)), int(m.group(2)), m.group(3), int(m.group(4))))
                        continue
                    m = re.match(r"bg_event\s+(\d+)\s*,\s*(\d+)\s*,\s*(\w+)", line)
                    if m:
                        rec["bg"].append((int(m.group(1)), int(m.group(2)), m.group(3)))
                        continue
                    m = re.match(r"object_event\s+(.*)", line)
                    if m:
                        a = [x.strip() for x in m.group(1).split(",")]
                        o = dict(x=int(a[0]), y=int(a[1]), sprite=a[2], move=a[3], dir=a[4], text=a[5])
                        if len(a) == 8:
                            o["kind"], o["arg1"], o["arg2"] = "mon_or_trainer", a[6], int(a[7])
                        elif len(a) == 7:
                            o["kind"], o["arg1"], o["arg2"] = "item", a[6], None
                        else:
                            o["kind"], o["arg1"], o["arg2"] = "npc", None, None
                        rec["objs"].append(o)
                out[const] = rec
            return out
        return self.cached("objects", build)

    # -- wild tables
    def wild_labels(self):
        """{label: {'grass': (rate, [(level, species const)]), 'water': (...)}} from data/wild/maps/*.asm."""
        def build():
            out = {}
            d = self.root / "data/wild/maps"
            for f in sorted(d.glob("*.asm")):
                cur, kind = None, None
                for line in asm_lines(f, self.defs):
                    m = re.match(r"(\w+WildMons):", line)
                    if m:
                        cur = m.group(1)
                        out[cur] = {"grass": (0, []), "water": (0, [])}
                        continue
                    m = re.match(r"def_(grass|water)_wildmons\s+(\d+)", line)
                    if m:
                        kind = m.group(1)
                        out[cur][kind] = (int(m.group(2)), [])
                        continue
                    m = re.match(r"db\s+(\d+)\s*,\s*(\w+)$", line)
                    if m and kind:
                        out[cur][kind][1].append((int(m.group(1)), m.group(2)))
            return out
        return self.cached("wildlabels", build)

    def wild_by_map(self):
        """{map id: {'grass': ..., 'water': ...}} for the maps whose pointer is not NothingWildMons."""
        def build():
            labels = self.wild_labels()
            out = {}
            i = 0
            for line in self.lines("data/wild/grass_water.asm"):
                m = re.match(r"dw\s+(\w+)", line)
                if m:
                    lab = m.group(1)
                    if lab != "NothingWildMons":
                        out[i] = labels[lab]
                    i += 1
            self._cache["wild_pointer_count"] = i
            return out
        return self.cached("wildbymap", build)

    def super_rod(self):
        """{map const: [(level, species const)]}: the Super Rod fish of each map that has any.

        Red and Blue keep a map -> group pointer list and shared groups; Yellow lists four (species, level) pairs
        after each map (data/wild/super_rod.asm has both layouts)."""
        def build():
            consts = self.map_consts()
            by_map, groups, cur = {}, {}, None
            for line in self.lines("data/wild/super_rod.asm"):
                m = re.match(r"dbw\s+(\w+)\s*,\s*\.(\w+)", line)
                if m:
                    by_map[m.group(1)] = m.group(2)
                    continue
                m = re.match(r"\.(Group\d+):", line)
                if m:
                    cur = m.group(1)
                    groups[cur] = []
                    continue
                m = re.match(r"db\s+(\d+)\s*,\s*(\w+)$", line)
                if m and cur:
                    groups[cur].append((int(m.group(1)), m.group(2)))
                    continue
                m = re.match(r"db\s+(\w+)\s*,\s*(.*)$", line)
                if m and m.group(1) in consts:                       # Yellow: map, then (species, level) x 4
                    a = [x.strip() for x in m.group(2).split(",")]
                    by_map[m.group(1)] = [(int(a[i + 1]), a[i]) for i in range(0, len(a), 2)]
            return {k: (groups[v] if isinstance(v, str) else v) for k, v in by_map.items()}
        return self.cached("superrod", build)

    def good_rod(self):
        return [(int(m.group(1)), m.group(2)) for line in self.lines("data/wild/good_rod.asm")
                for m in [re.match(r"db\s+(\d+)\s*,\s*(\w+)$", line)] if m]

    # -- species
    def species_consts(self):
        """{name: internal id} for pokemon_constants.asm (NO_MON = 0)."""
        return self.cached("species", lambda: parse_consts(self.root / "constants/pokemon_constants.asm", self.defs))

    def dex_consts(self):
        return self.cached("dexc", lambda: parse_consts(self.root / "constants/pokedex_constants.asm", self.defs))

    def dex_of_internal(self):
        """{internal species id: dex number} from data/pokemon/dex_order.asm (PokedexOrder, one byte per internal id)."""
        def build():
            dexc = self.dex_consts()
            out, i = {}, 1
            for line in self.lines("data/pokemon/dex_order.asm"):
                m = re.match(r"db\s+(\w+)", line)
                if m:
                    out[i] = dexc.get(m.group(1), 0)      # DEX_MISSINGNO-style padding is 0
                    i += 1
            return out
        return self.cached("dexorder", build)

    def evos(self):
        """{internal id: [(method, level or item, target internal id)]} from data/pokemon/evos_moves.asm."""
        def build():
            sp = self.species_consts()
            order, blocks, cur = [], {}, None
            state = None
            for line in self.lines("data/pokemon/evos_moves.asm"):
                m = re.match(r"dw\s+(\w+EvosMoves)", line)
                if m:
                    order.append(m.group(1))
                    continue
                m = re.match(r"(\w+EvosMoves):", line)
                if m:
                    cur = m.group(1)
                    blocks[cur] = []
                    state = "evo"
                    continue
                if cur is None:
                    continue
                if state == "evo":
                    if line == "db 0":
                        state = "moves"
                        continue
                    m = re.match(r"db\s+EVOLVE_(LEVEL|ITEM|TRADE)\s*,\s*(.*)", line)
                    if m:
                        a = [x.strip() for x in m.group(2).split(",")]
                        if m.group(1) == "LEVEL":
                            blocks[cur].append(("level", int(a[0]), sp[a[1]]))
                        elif m.group(1) == "ITEM":
                            blocks[cur].append(("item", a[0], sp[a[2]]))
                        else:
                            blocks[cur].append(("trade", int(a[0]), sp[a[1]]))
                elif state == "moves" and line == "db 0":
                    state = None
            return {i + 1: blocks[lab] for i, lab in enumerate(order)}
        return self.cached("evos", build)

    # -- trainers
    def class_consts(self):
        """{'YOUNGSTER': 1, ...} (the class number wTrainerClass holds)."""
        def build():
            env = parse_consts(self.root / "constants/trainer_constants.asm", self.defs)
            return {k: v for k, v in env.items() if not k.startswith("OPP_") and k not in ("OPP_ID_OFFSET", "NUM_TRAINERS", "const_value")}
        return self.cached("classes", build)

    def class_names(self):
        """{class number: Title Case name} from data/trainers/names.asm (the order is the class number)."""
        def build():
            out, i = {}, 1
            for raw in read_text(self.root / "data/trainers/names.asm").splitlines():
                m = re.match(r'\s*li\s+"(.*)"', raw)
                if m:
                    out[i] = norm_class(m.group(1))
                    i += 1
            return out
        return self.cached("classnames", build)

    def parties(self):
        """{class number: [party, ...]} party = [(level, species const), ...], no. N is index N-1."""
        def build():
            classes = self.class_consts()
            order, cur, data = [], None, {}
            for line in self.lines("data/trainers/parties.asm"):
                m = re.match(r"dw\s+(\w+Data)$", line)
                if m:
                    order.append(m.group(1))
                    continue
                m = re.match(r"(\w+Data):", line)
                if m:
                    cur = m.group(1)
                    data[cur] = []
                    continue
                m = re.match(r"db\s+(.*)", line)
                if m and cur:
                    a = [x.strip() for x in m.group(1).split(",")]
                    if a[0] == "$FF":
                        pairs = [(int(a[i]), a[i + 1]) for i in range(1, len(a) - 1, 2)]
                    else:
                        pairs = [(int(a[0]), s) for s in a[1:-1]]
                    if a[-1] != "0":
                        raise ValueError("party does not end in 0: " + line)
                    data[cur].append(pairs)
            if len(order) != len(classes) - 1:   # NOBODY (class 0) has no pointer
                raise ValueError("class pointer count mismatch")
            return {i + 1: data[lab] for i, lab in enumerate(order)}
        return self.cached("parties", build)

    def party_comments(self):
        """{class number: [comment text before each party]} - the disassembly's own labels (`; Route 22`, `; Unused`)."""
        def build():
            order, cur, out, pending = [], None, {}, ""
            raw = read_text(self.root / "data/trainers/parties.asm").splitlines()
            for line in raw:
                code = line.split(";")[0].strip()
                com = line.split(";", 1)[1].strip() if ";" in line else ""
                m = re.match(r"dw\s+(\w+Data)$", code)
                if m:
                    order.append(m.group(1))
                    continue
                m = re.match(r"(\w+Data):", code)
                if m:
                    cur = m.group(1)
                    out[cur] = []
                    pending = ""
                    continue
                if cur is None:
                    continue
                if code.startswith("db"):
                    out[cur].append(com or pending)
                    if com:
                        pending = com
                elif com and not code:
                    pending = com
            return {i + 1: out[lab] for i, lab in enumerate(order)}
        return self.cached("partycomments", build)

    # -- misc constants
    def ram_consts(self):
        return self.cached("ram", lambda: parse_consts(self.root / "constants/ram_constants.asm", self.defs))

    def event_consts(self):
        return self.cached("events", lambda: parse_consts(self.root / "constants/event_constants.asm", self.defs))

    def item_consts(self):
        return self.cached("items", lambda: parse_consts(self.root / "constants/item_constants.asm", self.defs))


# --------------------------------------------------------------------------- the ROM

class Rom:
    """A clean ROM dump plus the randomizer's offsets for its build."""

    def __init__(self, build):
        self.build = build
        self.game = GAME_OF[build]
        _, name, _, self.sha1 = BUILDS[build]
        self.path = ROMS / name
        self.data = self.path.read_bytes()
        self.ini = parse_ini(build)

    def verify_build(self):
        got = hashlib.sha1(self.data).hexdigest()
        if got != self.sha1:
            raise Mismatch("%s: sha1 %s is not the reference build %s" % (self.path.name, got, self.sha1))
        return got

    def u8(self, off):
        return self.data[off]

    def u16(self, off):
        return self.data[off] | (self.data[off + 1] << 8)

    @staticmethod
    def bank_off(bank, ptr):
        """File offset of a banked pointer (0x4000..0x7FFF in `bank`); a home-bank pointer is its own offset."""
        return ptr if ptr < 0x4000 else bank * 0x4000 + (ptr - 0x4000)

    # -- maps
    def map_header_ptr(self, map_id):
        a = ini_int(self.ini, "MapAddresses")
        b = ini_int(self.ini, "MapBanks")
        return self.data[b + map_id], self.u16(a + 2 * map_id)

    def map_header(self, map_id):
        bank, ptr = self.map_header_ptr(map_id)
        o = self.bank_off(bank, ptr)
        flags = self.data[o + 9]
        p = o + 10 + 11 * bin(flags & 0xF).count("1")
        return dict(bank=bank, tileset=self.data[o], h=self.data[o + 1], w=self.data[o + 2],
                    conn=flags & 0xF, obj_ptr=self.u16(p))

    def map_objects(self, map_id):
        """{'warps': [(x, y, dest map id or 255, dest warp id)], 'objs': [dict]} straight from the map's object data."""
        hd = self.map_header(map_id)
        o = self.bank_off(hd["bank"], hd["obj_ptr"]) + 1            # skip the border block
        n = self.data[o]
        o += 1
        warps = []
        for _ in range(n):
            y, x, dwarp, dmap = self.data[o:o + 4]
            warps.append((x, y, dmap, dwarp + 1))
            o += 4
        o += 1 + 3 * self.data[o]                                    # bg events
        n = self.data[o]
        o += 1
        objs = []
        for _ in range(n):
            sprite, y, x, move, rng, text = self.data[o:o + 6]
            o += 6
            rec = dict(sprite=sprite, x=x - 4, y=y - 4, text=text, kind="npc", arg1=None, arg2=None)
            if text & 0x40:
                rec.update(kind="mon_or_trainer", arg1=self.data[o], arg2=self.data[o + 1])
                o += 2
            elif text & 0x80:
                rec.update(kind="item", arg1=self.data[o])
                o += 1
            objs.append(rec)
        return dict(warps=warps, objs=objs)

    # -- wild tables
    def wild_tables(self, count):
        """{map id: {'grass': (rate, [(level, species id)]), 'water': (...)}} for the maps with a non-empty table."""
        base = ini_int(self.ini, "WildPokemonTableOffset")
        bank = base // 0x4000
        out = {}
        for i in range(count):
            p = self.bank_off(bank, self.u16(base + 2 * i))
            rec = {}
            for kind in ("grass", "water"):
                rate = self.data[p]
                p += 1
                slots = []
                if rate:
                    slots = [(self.data[p + 2 * k], self.data[p + 2 * k + 1]) for k in range(10)]
                    p += 20
                rec[kind] = (rate, slots)
            if rec["grass"][0] or rec["water"][0]:
                out[i] = rec
        return out

    def super_rod(self):
        """{map id: [(level, species id)]} from SuperRodTableOffset: Red and Blue store (map, pointer) entries ending
        in 0xFF and shared groups (a count, then level/species pairs); Yellow stores map + four (species, level) pairs."""
        base = ini_int(self.ini, "SuperRodTableOffset")
        out = {}
        if self.build == "yellow":
            p = base
            while self.data[p] != 0xFF:
                out[self.data[p]] = [(self.data[p + 2 + 2 * k], self.data[p + 1 + 2 * k]) for k in range(4)]
                p += 9
            return out
        bank = base // 0x4000
        p = base
        while self.data[p] != 0xFF:
            q = self.bank_off(bank, self.u16(p + 1))
            n = self.data[q]
            out[self.data[p]] = [(self.data[q + 1 + 2 * k], self.data[q + 2 + 2 * k]) for k in range(n)]
            p += 3
        return out

    # -- species tables
    def dex_of_internal(self):
        base = ini_int(self.ini, "PokedexOrder")
        n = ini_int(self.ini, "InternalPokemonCount")
        return {i: self.data[base + i - 1] for i in range(1, n + 1)}

    def evos(self):
        """{internal id: [(method, arg, target internal id)]} from the ROM's evolution and learnset table."""
        base = ini_int(self.ini, "PokemonMovesetsTableOffset")
        bank = base // 0x4000
        n = ini_int(self.ini, "InternalPokemonCount")
        out = {}
        for i in range(1, n + 1):
            p = self.bank_off(bank, self.u16(base + 2 * (i - 1)))
            evs = []
            while self.data[p]:
                t = self.data[p]
                if t == 1:
                    evs.append(("level", self.data[p + 1], self.data[p + 2]))
                    p += 3
                elif t == 2:
                    evs.append(("item", self.data[p + 1], self.data[p + 3]))
                    p += 4
                elif t == 3:
                    evs.append(("trade", self.data[p + 1], self.data[p + 2]))
                    p += 3
                else:
                    raise Mismatch("unknown evolution type %d for internal id %d" % (t, i))
            out[i] = evs
        return out

    # -- trainers
    def trainer_pointers(self):
        """File offsets of each class's party list, classes 1..47 (list index class-1).

        TrainerDataTableOffset (gen1_offsets.ini) is the file offset of TrainerDataPointers: 47 little-endian
        pointers into bank 0x0E, one per class, in class order. The first party list follows the table."""
        base = ini_int(self.ini, "TrainerDataTableOffset")
        bank = base // 0x4000
        return [self.bank_off(bank, self.u16(base + 2 * k)) for k in range(47)]

    def read_party(self, p):
        """(party, offset after it): [(level, species id)]. A party starts with its level and the species follow
        until a 0 byte, or it starts with 0xFF and holds (level, species) pairs until a 0 byte."""
        party = []
        if self.data[p] == 0xFF:
            p += 1
            while self.data[p]:
                party.append((self.data[p], self.data[p + 1]))
                p += 2
        else:
            lvl = self.data[p]
            p += 1
            while self.data[p]:
                party.append((lvl, self.data[p]))
                p += 1
        return party, p + 1

    def trainer_table(self):
        """{class: [party]} by walking every class from its pointer with the ini's TrainerDataClassCounts.

        Raises Mismatch when a class's last party does not end exactly where the next class's list starts."""
        counts = ini_list(self.ini, "TrainerDataClassCounts")
        ptrs = self.trainer_pointers()
        table = {}
        for cls in range(1, 48):
            p = ptrs[cls - 1]
            parties = []
            for _ in range(counts[cls]):
                party, p = self.read_party(p)
                parties.append(party)
            if cls < 47 and p != ptrs[cls]:
                raise Mismatch("class %d parties end at %#x, next class starts at %#x" % (cls, p, ptrs[cls]))
            if parties:
                table[cls] = parties
        return table

    # -- text
    def text_at(self, off, charmap, limit=32):
        out = []
        for i in range(limit):
            b = self.data[off + i]
            if b == 0x50:
                break
            out.append(charmap.get(b, "?"))
        return "".join(out)


def charmap(build):
    """{byte: character} from constants/charmap.asm, control codes left out (upper case, digits and the few marks)."""
    d = Disasm(build)
    out = {}
    for raw in read_text(d.root / "constants/charmap.asm").splitlines():
        m = re.match(r'\s*charmap\s+"(.*)"\s*,\s*\$([0-9A-Fa-f]+)', raw)
        if m and not m.group(1).startswith("<") and len(m.group(1)) == 1:
            out.setdefault(int(m.group(2), 16), m.group(1))
    return out


# --------------------------------------------------------------------------- species names

def natdex_names(limit=151):
    """{dex number: name} for 1..limit from natdex/species.tsv (the spelling the reader resolves)."""
    out = {}
    for line in read_text(SPECIES_TSV).splitlines():
        if "\t" in line:
            k, v = line.split("\t", 1)
            if k.isdigit() and int(k) <= limit:
                out[int(k)] = v.strip()
    return out


def species_name(d, const, names=None):
    """Species constant (RHYDON) -> the natdex spelling, through the dex number."""
    names = names or natdex_names()
    return names[d.dex_of_internal()[d.species_consts()[const]]]


# --------------------------------------------------------------------------- output helpers

def header_block(lines):
    return "\n".join("# " + l if l else "#" for l in lines) + "\n"


def compare_text(path, expected):
    """True when the file's text equals `expected` once line endings are ignored."""
    got = Path(path).read_bytes().decode("ascii").replace("\r\n", "\n")
    return got == expected


def write_lf(path, text):
    Path(path).write_bytes(text.encode("ascii"))
