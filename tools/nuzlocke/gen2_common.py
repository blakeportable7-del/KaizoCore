"""Shared readers for the Generation 2 (Gold, Silver, Crystal) Nuzlocke data generators (2026-09-29).

Everything reads two things and never copies bytes from either into the repo:
  * the pret disassemblies (pokegold for Gold and Silver, pokecrystal for Crystal), and
  * clean ROM dumps (Gold (U) and Crystal (U)); there is no Silver dump, and Silver shares every table used here
    with Gold (the two are one pokegold build with -D_GOLD or -D_SILVER).

Game keys used in the data files: gs = Gold and Silver, c = Crystal (g and s only where a value differs).

The environment variable NZ_REFS moves the disassemblies, NZ_ROMS_VENDOR and NZ_ROMS_SCRATCH move the dumps, NZ_DATA the
folder holding the shipped data files (a tampered copy proves a check can fail).
"""
import os
import re
import sys

REFS = os.environ.get("NZ_REFS", "C:/Users/bepor/ironmon-ref")
ROMS_VENDOR = os.environ.get("NZ_ROMS_VENDOR", "C:/Users/bepor/IronMonOne/.vendor/roms")
ROMS_SCRATCH = os.environ.get(
    "NZ_ROMS_SCRATCH",
    "C:/Users/bepor/AppData/Local/Temp/claude/C--Users-bepor-willowcreek-v2-deploy-zip/"
    "c84daccc-781f-450a-becb-7ad5c6ff979d/scratchpad/roms")

REPO = {"gs": REFS + "/pokegold", "c": REFS + "/pokecrystal"}
ROM_PATH = {"gs": ROMS_SCRATCH + "/gold-u.gbc", "c": ROMS_VENDOR + "/crystal-u.gbc"}
# The SHA-1 of a clean dump, from each disassembly's roms.sha1 (pokegold.gbc and pokecrystal.gbc).
ROM_SHA1 = {"gs": "d8b8a3600a465308c9953dfa04f0081c05bdcb94", "c": "f4cd194bdee0d04ca4eac29e09b8e4e9d818c133"}
GAME_NAME = {"gs": "Gold and Silver", "c": "Crystal"}

HERE = os.path.dirname(os.path.abspath(__file__))
WORKTREE = os.path.dirname(os.path.dirname(HERE))
RES = os.path.join(WORKTREE, "tracker-gba", "src", "main", "resources")
NZ = os.environ.get("NZ_DATA", os.path.join(RES, "nuzlocke"))     # NZ_DATA points the checks at a copy (for breakage tests)
NATDEX_SPECIES = os.path.join(RES, "natdex", "species.tsv")
LANDMARKS_TSV = os.path.join(RES, "gen2", "landmarks.tsv")
INI = os.path.join(WORKTREE, "engine-zx", "src", "com", "dabomstew", "pkrandomzx", "config", "gen2_offsets.ini")

DATE = "2026-09-29"


# ------------------------------------------------------------------------------------------ text helpers
def read_text(path):
    """A file as text with universal newlines (the repo's working tree is CRLF, a fresh write is LF)."""
    with open(path, "r", encoding="utf-8", newline=None) as f:
        return f.read()


def lines_of(path):
    return read_text(path).split("\n")


def strip_comment(line):
    """Drop a ; comment (the pret sources never put a semicolon inside a string that matters here)."""
    i = line.find(";")
    return (line if i < 0 else line[:i]).strip()


def die(msg):
    print("FAIL: " + msg, file=sys.stderr)
    sys.exit(1)


class Problems:
    """Collects check failures so a --check run prints every one and exits non-zero once."""

    def __init__(self):
        self.items = []
        self.checks = 0

    def check(self, ok, msg):
        self.checks += 1
        if not ok:
            self.items.append(msg)
        return ok

    def finish(self, label):
        if self.items:
            for m in self.items[:60]:
                print("MISMATCH: " + m)
            if len(self.items) > 60:
                print("... and %d more" % (len(self.items) - 60))
            print("%s: FAILED, %d of %d checks mismatched" % (label, len(self.items), self.checks))
            sys.exit(1)
        print("%s: OK, %d checks, no mismatch" % (label, self.checks))


# ------------------------------------------------------------------------------------------ ROM helpers
def load_rom(game, required=True):
    """The clean dump for a game key as bytes, or None (with a note) if it is missing and not required."""
    import hashlib
    path = ROM_PATH[game]
    if not os.path.exists(path):
        if required:
            die("ROM dump missing: " + path)
        print("NOTE: no ROM dump for %s at %s, ROM checks skipped" % (GAME_NAME[game], path))
        return None
    with open(path, "rb") as f:
        data = f.read()
    sha = hashlib.sha1(data).hexdigest()
    if sha != ROM_SHA1[game] and not os.environ.get("NZ_ALLOW_DIRTY_ROM"):
        die("%s is not the clean %s (U) dump: sha1 %s, expected %s" % (path, GAME_NAME[game], sha, ROM_SHA1[game]))
    return data


def ini_section(name):
    """The key=value pairs of one [section] of gen2_offsets.ini (values as raw strings), following CopyFrom."""
    text = read_text(INI)
    sections = {}
    cur = None
    for raw in text.split("\n"):
        line = raw.strip()
        m = re.match(r"^\[(.+)\]$", line)
        if m:
            cur = m.group(1)
            sections[cur] = {}
            continue
        if cur and "=" in line and not line.startswith("//"):
            k, v = line.split("=", 1)
            sections[cur].setdefault(k.strip(), v.strip())
    out = {}
    chain = [name]
    while "CopyFrom" in sections.get(chain[-1], {}):
        chain.append(sections[chain[-1]]["CopyFrom"])
    for n in reversed(chain):
        out.update(sections.get(n, {}))
    return out


# ------------------------------------------------------------------------------------------ constants
def parse_species_ids(root):
    """{CONSTANT: number} for constants/pokemon_constants.asm's first const_def 1 block (1..NUM_POKEMON, then EGG)."""
    ids = {}
    n = 0
    started = False
    for raw in lines_of(root + "/constants/pokemon_constants.asm"):
        line = strip_comment(raw)
        if line.startswith("const_def"):
            if started:
                break
            started = True
            n = 1 if line.replace("const_def", "").strip() == "1" else 0
            continue
        m = re.match(r"^const\s+(\w+)$", line)
        if started and m:
            ids[m.group(1)] = n
            n += 1
        elif started and line.startswith("DEF NUM_POKEMON"):
            break
    # EGG is 0xFD in both games (constants/pokemon_constants.asm: const_skip, then EGG); not needed for parties
    return ids


def parse_trainer_classes(root):
    """[(class_no, CLASSCONST, [party constants in order])], from constants/trainer_constants.asm.

    trainerclass X sets X to the running class number and restarts the party numbering at 1 (const_def 1),
    so the Nth const under a class is party N: exactly what wOtherTrainerID holds.
    """
    classes = []
    cls_no = -1
    for raw in lines_of(root + "/constants/trainer_constants.asm"):
        line = strip_comment(raw)
        m = re.match(r"^trainerclass\s+(\w+)$", line)
        if m:
            cls_no += 1
            classes.append([cls_no, m.group(1), []])
            continue
        m = re.match(r"^const\s+(\w+)$", line)
        if m and classes:
            classes[-1][2].append(m.group(1))
    return [tuple(c) for c in classes]


def parse_class_names(root):
    """The trainer class names in class order (class 1 first), as the game text spells them."""
    names = []
    for raw in lines_of(root + "/data/trainers/class_names.asm"):
        m = re.match(r'^\s*li\s+"([^"]*)"', raw)
        if m:
            names.append(m.group(1))
    return names


def parse_group_labels(root):
    """The Group label for class 1, 2, ... in TrainerGroups order."""
    labels = []
    for raw in lines_of(root + "/data/trainers/party_pointers.asm"):
        m = re.match(r"^\s*dw\s+(\w+Group)\s*$", raw)
        if m:
            labels.append(m.group(1))
    return labels


TRAINERTYPE = {"TRAINERTYPE_NORMAL": 0, "TRAINERTYPE_MOVES": 1, "TRAINERTYPE_ITEM": 2, "TRAINERTYPE_ITEM_MOVES": 3}


def parse_parties(root):
    """{GroupLabel: [party, ...]} where a party is {name, type, mons: [{level, species, item, moves}]}.

    The party number (wOtherTrainerID) is the 1-based position in the group, which is how ReadTrainerParty
    counts them (it skips ID-1 parties by their $FF terminators).
    """
    species = parse_species_ids(root)
    groups = {}
    cur = None
    party = None
    for raw in lines_of(root + "/data/trainers/parties.asm"):
        line = strip_comment(raw)
        if not line:
            continue
        m = re.match(r"^(\w+Group):$", line)
        if m:
            cur = m.group(1)
            groups[cur] = []
            party = None
            continue
        if cur is None:
            continue
        m = re.match(r'^db\s+"([^"]*)@"\s*,\s*(TRAINERTYPE_\w+)$', line)
        if m:
            party = {"name": m.group(1), "type": TRAINERTYPE[m.group(2)], "mons": []}
            groups[cur].append(party)
            continue
        if re.match(r"^db\s+-1$", line):
            party = None
            continue
        m = re.match(r"^db\s+(.+)$", line)
        if m and party is not None:
            f = [x.strip() for x in m.group(1).split(",")]
            t = party["type"]
            level = int(f[0])
            sp = species[f[1]]
            rest = f[2:]
            item = None
            moves = []
            if t in (2, 3):
                item = rest[0]
                rest = rest[1:]
            if t in (1, 3):
                moves = rest[:4]
                rest = rest[4:]
            if rest:
                die("trainer entry with leftover fields in %s: %s" % (root, raw))
            party["mons"].append({"level": level, "species": sp, "species_const": f[1], "item": item, "moves": moves})
    return groups


def script_trainers(root):
    """Every trainer fight a map script starts: [(class_const, id_const, map_file, line_no, command)].

    `trainer CLASS, ID, event, ...` is a walk-up trainer (the class and id are copied into wOtherTrainerClass and
    wOtherTrainerID when it sees you); `loadtrainer CLASS, ID` is a scripted fight (home/trainers.asm and
    engine/overworld/scripting.asm Script_loadtrainer write the same two bytes).
    """
    out = []
    mapdir = root + "/maps"
    for name in sorted(os.listdir(mapdir)):
        if not name.endswith(".asm"):
            continue
        for i, raw in enumerate(lines_of(mapdir + "/" + name), 1):
            m = re.match(r"^\s*(trainer|loadtrainer)\s+(\w+)\s*,\s*(\w+)", strip_comment(raw))
            if m:
                out.append((m.group(2), m.group(3), name, i, m.group(1)))
    return out


def trainer_numbers(root):
    """({class const: class number}, {(class const, id const): party number}) from constants/trainer_constants.asm."""
    cls = {}
    ids = {}
    for no, const, parts in parse_trainer_classes(root):
        cls[const] = no
        for n, p in enumerate(parts, 1):
            ids[(const, p)] = n
    return cls, ids


def species_constant_names(root):
    """{number: CONSTANT} the other way round."""
    return {v: k for k, v in parse_species_ids(root).items()}


def title_species(const):
    """A species constant (NIDORAN_F, MR__MIME, HO_OH ...) as a natdex/species.tsv name is not needed here: the
    generators map numbers through natdex/species.tsv instead. Kept for messages only."""
    return const.replace("_", " ").title()


def natdex_names():
    """{dex number: name} from natdex/species.tsv (1..251 are the Gen 2 species)."""
    names = {}
    for raw in lines_of(NATDEX_SPECIES):
        if raw.strip() and "\t" in raw:
            a, b = raw.split("\t", 1)
            names[int(a)] = b.strip()
    return names


class Charmap:
    """constants/charmap.asm: text token -> byte. Later definitions win, and the longest token matches first,
    which is how rgbasm reads a string (so "'s" is one byte, $D4)."""

    def __init__(self, root):
        self.map = {}
        for raw in lines_of(root + "/constants/charmap.asm"):
            m = re.match(r'^\s*charmap\s+"([^"]+)"\s*,\s*\$([0-9A-Fa-f]{1,2})', raw)
            if m:
                self.map[m.group(1)] = int(m.group(2), 16)
        self.tokens = sorted(self.map, key=len, reverse=True)

    def encode(self, text):
        out = bytearray()
        i = 0
        while i < len(text):
            for t in self.tokens:
                if text.startswith(t, i):
                    out.append(self.map[t])
                    i += len(t)
                    break
            else:
                die("no charmap entry for %r in %r" % (text[i], text))
        return bytes(out)


# ------------------------------------------------------------------------------------------ output helpers
def write_lf(path, text):
    """Write with LF endings (to_crlf.py converts them after the run, as the spec says). ASCII only."""
    bad = [c for c in text if ord(c) > 126 or (ord(c) < 32 and c not in "\n\t")]
    if bad:
        die("non-ASCII or control characters in output for " + path + ": " + repr(bad[:5]))
    with open(path, "w", encoding="ascii", newline="\n") as f:
        f.write(text)


def data_rows(path):
    """Rows of a shipped .tsv as lists of fields; blank lines and # lines skipped."""
    rows = []
    for raw in lines_of(path):
        if raw.strip() and not raw.lstrip().startswith("#"):
            rows.append(raw.rstrip("\r").split("\t"))
    return rows


def header_lines(path):
    return [l for l in lines_of(path) if l.startswith("#")]


def clean_crlf_ascii(path):
    """True when a shipped file is all CRLF (no bare LF, no bare CR) and plain printable ASCII plus tabs."""
    with open(path, "rb") as f:
        b = f.read()
    if b.count(b"\n") != b.count(b"\r\n") or b.count(b"\r") != b.count(b"\r\n"):
        return False
    return all(x < 127 and (x >= 32 or x in (9, 10, 13)) for x in b)
