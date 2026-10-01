"""Generation 5 (Black, White, Black 2, White 2) ROM reader shared by the gen5_*.py generators and checks.

An NDS file system reader, a NARC reader and decoders for the records the Nuzlocke data files come from:
trainer data and trainer Pokemon (levels), personal data (gender ratio, types), evolutions and baby forms.

The layouts are the ones the Universal Pokemon Randomizer ZX uses (engine-zx/src/com/dabomstew/pkrandomzx/
romhandlers/Gen5RomHandler.java and config/gen5_offsets.ini). Nothing read out of a ROM is copied into the
repository: only facts derived from it (a level, a species, a type) reach the data files.

Run as a script for a quick look at a ROM:  python gen5_rom.py black2
"""
import mmap
import struct
import sys
from pathlib import Path

ROM_DIR = Path("C:/Users/bepor/IronMonOne/.vendor/roms")
# Black 2 and White 2 are the dumps the spec names. There is no Black dump anywhere; a White (US) dump sits in
# the user's Downloads folder (outside the spec's list, so it is only ever an optional extra check).
ROMS = {
    "black2": [ROM_DIR / "black2-u.nds"],
    "white2": [ROM_DIR / "white2-u.nds"],
    "white": [ROM_DIR / "white-u.nds", Path("C:/Users/bepor/Downloads/white.nds")],
}
GAME_CODES = {"black2": "IREO", "white2": "IRDO", "white": "IRAO"}

# NARC paths per game family, from gen5_offsets.ini (File<...>=<a/x/y/z, crc>).
NARC_PATHS = {
    "bw": {
        "TextStrings": "a/0/0/2",
        "MapTableFile": "a/0/1/2",
        "PokemonStats": "a/0/1/6",
        "PokemonEvolutions": "a/0/1/9",
        "BabyPokemon": "a/0/2/0",
        "Scripts": "a/0/5/7",
        "MapFiles": "a/1/2/5",
        "WildPokemon": "a/1/2/6",
        "TrainerData": "a/0/9/2",
        "TrainerPokemon": "a/0/9/3",
    },
    "b2w2": {
        "TextStrings": "a/0/0/2",
        "MapTableFile": "a/0/1/2",
        "PokemonStats": "a/0/1/6",
        "PokemonEvolutions": "a/0/1/9",
        "BabyPokemon": "a/0/2/0",
        "Scripts": "a/0/5/6",
        "MapFiles": "a/1/2/6",
        "WildPokemon": "a/1/2/7",
        "TrainerData": "a/0/9/1",
        "TrainerPokemon": "a/0/9/2",
    },
}

# Text archive entries (index into a/0/0/2), from gen5_offsets.ini.
TEXT_INDEX = {
    "bw": {"PokemonNames": 70, "TrainerNames": 190, "TrainerClasses": 191, "ItemNames": 54, "MapNames": 89},
    "b2w2": {"PokemonNames": 90, "TrainerNames": 382, "TrainerClasses": 383, "ItemNames": 64, "MapNames": 109},
}


class NdsRom:
    """An NDS ROM opened read-only. Files are found by path through the file name table."""

    def __init__(self, path):
        self.path = Path(path)
        self._fh = open(self.path, "rb")
        self.data = mmap.mmap(self._fh.fileno(), 0, access=mmap.ACCESS_READ)
        self.game_code = bytes(self.data[0x0C:0x10]).decode("ascii")
        self.title = bytes(self.data[0x00:0x0C]).decode("ascii", "replace").rstrip("\x00")
        fnt_off, fnt_size, fat_off, fat_size = struct.unpack_from("<IIII", self.data, 0x40)
        self._fnt_off = fnt_off
        self._fat_off = fat_off
        self._fat_count = fat_size // 8
        self.paths = {}
        self._walk(0xF000, "")

    def close(self):
        self.data.close()
        self._fh.close()

    def _walk(self, dir_id, prefix):
        idx = dir_id & 0xFFF
        sub_off, first_id, _parent = struct.unpack_from("<IHH", self.data, self._fnt_off + 8 * idx)
        pos = self._fnt_off + sub_off
        fid = first_id
        while True:
            b = self.data[pos]
            pos += 1
            if b == 0:
                break
            n = b & 0x7F
            name = bytes(self.data[pos:pos + n]).decode("latin-1")
            pos += n
            if b & 0x80:
                child = struct.unpack_from("<H", self.data, pos)[0]
                pos += 2
                self._walk(child, prefix + name + "/")
            else:
                self.paths[prefix + name] = fid
                fid += 1

    def file_bytes(self, path):
        fid = self.paths[path]
        start, end = struct.unpack_from("<II", self.data, self._fat_off + 8 * fid)
        return bytes(self.data[start:end])

    def narc(self, path):
        return read_narc(self.file_bytes(path))


def read_narc(blob):
    """The files of a NARC archive, as a list of bytes objects."""
    if blob[:4] != b"NARC":
        raise ValueError("not a NARC")
    header_size = struct.unpack_from("<H", blob, 0x0C)[0]
    pos = header_size
    if blob[pos:pos + 4] != b"BTAF":
        raise ValueError("no BTAF")
    btaf_size = struct.unpack_from("<I", blob, pos + 4)[0]
    count = struct.unpack_from("<H", blob, pos + 8)[0]
    entries = [struct.unpack_from("<II", blob, pos + 12 + 8 * i) for i in range(count)]
    pos += btaf_size
    if blob[pos:pos + 4] != b"BTNF":
        raise ValueError("no BTNF")
    pos += struct.unpack_from("<I", blob, pos + 4)[0]
    if blob[pos:pos + 4] != b"GMIF":
        raise ValueError("no GMIF")
    base = pos + 8
    return [blob[base + s:base + e] for s, e in entries]


def open_rom(key):
    """Open 'black2', 'white2' or 'white' (or a path). Returns None when no file is there, and refuses (None)
    a file whose header game code is not the one the key names."""
    candidates = ROMS.get(key, [Path(key)])
    for path in candidates:
        if not Path(path).exists():
            continue
        rom = NdsRom(path)
        want = GAME_CODES.get(key)
        if want and rom.game_code != want:
            rom.close()
            continue
        return rom
    return None


# ---------------------------------------------------------------- trainers

class TrainerMon:
    __slots__ = ("level", "species", "form", "item", "moves", "difficulty", "ability_slot", "gender_flag")

    def __init__(self, level, species, form, item, moves, difficulty, ability_slot, gender_flag):
        self.level = level
        self.species = species
        self.form = form
        self.item = item
        self.moves = moves
        self.difficulty = difficulty
        self.ability_slot = ability_slot
        self.gender_flag = gender_flag


class Trainer:
    """One trainer record. `index` is the NARC file index: the id the DS tracker reads as enemyTrainerId."""

    def __init__(self, index, poketype, trainer_class, battle_mode, mons, items, ai, healer, money):
        self.index = index
        self.poketype = poketype
        self.trainer_class = trainer_class
        self.battle_mode = battle_mode
        self.mons = mons
        self.items = items
        self.ai = ai
        self.healer = healer
        self.money = money

    @property
    def top_level(self):
        return max((m.level for m in self.mons), default=0)


def read_trainers(rom, family="b2w2"):
    """Every trainer of the ROM, keyed by NARC index (index 0 is an empty placeholder and is skipped)."""
    paths = NARC_PATHS[family]
    tdata = rom.narc(paths["TrainerData"])
    tpoke = rom.narc(paths["TrainerPokemon"])
    out = {}
    for i in range(1, len(tdata)):
        t = tdata[i]
        p = tpoke[i]
        poketype, tclass, mode, count = t[0], t[1], t[2], t[3]
        items = struct.unpack_from("<4H", t, 4)
        ai = struct.unpack_from("<I", t, 12)[0]
        healer = t[16]
        money = t[17]
        pos = 0
        mons = []
        for _ in range(count):
            difficulty = p[pos]
            sb = p[pos + 1]
            level = struct.unpack_from("<H", p, pos + 2)[0]
            species = struct.unpack_from("<H", p, pos + 4)[0]
            form = struct.unpack_from("<H", p, pos + 6)[0]
            pos += 8
            item = None
            moves = None
            if poketype & 2:
                item = struct.unpack_from("<H", p, pos)[0]
                pos += 2
            if poketype & 1:
                moves = struct.unpack_from("<4H", p, pos)
                pos += 8
            mons.append(TrainerMon(level, species, form, item, moves, difficulty, (sb >> 4) & 0xF, sb & 0xF))
        if pos != len(p):
            raise ValueError("trainer %d: party record is %d bytes, parsed %d" % (i, len(p), pos))
        out[i] = Trainer(i, poketype, tclass, mode, mons, items, ai, healer, money)
    return out


# ---------------------------------------------------------------- personal data

PERSONAL_GENDER = 0x12
PERSONAL_TYPE1 = 6
PERSONAL_TYPE2 = 7


def read_personal(rom, family="b2w2"):
    """The personal records (76 bytes each) as a list; index = national dex number for 1..649."""
    recs = rom.narc(NARC_PATHS[family]["PokemonStats"])
    return recs


# ---------------------------------------------------------------- evolutions

def read_evolutions(rom, family="b2w2"):
    """{species: [(method, param, target), ...]} from the evolution NARC (7 slots of 6 bytes per species)."""
    recs = rom.narc(NARC_PATHS[family]["PokemonEvolutions"])
    out = {}
    for sp, rec in enumerate(recs):
        edges = []
        for k in range(7):
            method, param, target = struct.unpack_from("<HHH", rec, 6 * k)
            if method != 0 and target != 0:
                edges.append((method, param, target))
        out[sp] = edges
    return out


def read_babies(rom, family="b2w2"):
    """The baby-form NARC as raw u16 per species (the species a hatched egg of it becomes)."""
    recs = rom.narc(NARC_PATHS[family]["BabyPokemon"])
    out = {}
    for sp, rec in enumerate(recs):
        if len(rec) >= 2:
            out[sp] = struct.unpack_from("<H", rec, 0)[0]
    return out


# ---------------------------------------------------------------- text and zone headers

def _decompress_text(words):
    """The 9 bits per character packing of a text entry that starts with 0xF100 (port of PPTxtHandler.decompress)."""
    out = []
    j = 1
    shift1 = 0
    trans = 0
    while True:
        tmp1 = 0
        if shift1 >= 0x10:
            shift1 -= 0x10
            if shift1 > 0:
                tmp1 = trans | ((words[j] << (9 - shift1)) & 0x1FF)
                if (tmp1 & 0xFF) == 0xFF:
                    break
                if tmp1 not in (0, 1):
                    out.append(tmp1)
        else:
            tmp1 = (words[j] >> shift1) & 0x1FF
            if (tmp1 & 0xFF) == 0xFF:
                break
            if tmp1 not in (0, 1):
                out.append(tmp1)
            shift1 += 9
            if shift1 < 0x10:
                trans = (words[j] >> shift1) & 0x1FF
                shift1 += 9
            j += 1
    return out


def read_text_file(blob):
    """The strings of one message file (port of PPTxtHandler.readTexts): a list of str, one per entry."""
    num_sections, num_entries = struct.unpack_from("<HH", blob, 0)
    pos = 12
    section_offsets = list(struct.unpack_from("<%dI" % num_sections, blob, pos))
    base = section_offsets[0]
    pos = base + 4
    entries = []
    for _ in range(num_entries):
        off, count, _unk = struct.unpack_from("<IHH", blob, pos)
        pos += 8
        entries.append((off, count))
    strings = []
    for off, count in entries:
        words = list(struct.unpack_from("<%dH" % count, blob, base + off))
        key = words[count - 1] ^ 0xFFFF
        for k in range(count - 1, -1, -1):
            words[k] ^= key
            key = ((key >> 3) | (key << 13)) & 0xFFFF
        if words and words[0] == 0xF100:
            words = _decompress_text(words)
        chars = []
        for w in words:
            if w == 0xFFFF:
                break
            if 20 < w <= 0xFFF0:
                chars.append(chr(w))
            else:
                chars.append("{%04X}" % w)
        strings.append("".join(chars))
    return strings


def read_text(rom, family, kind):
    """A named text list ('PokemonNames', 'TrainerNames', 'TrainerClasses', 'ItemNames', 'MapNames')."""
    narc = rom.narc(NARC_PATHS[family]["TextStrings"])
    return read_text_file(narc[TEXT_INDEX[family][kind]])


def read_zone_headers(rom, family):
    """The zone (map header) table, 48 bytes each, as a list of bytes objects."""
    blob = rom.narc(NARC_PATHS[family]["MapTableFile"])[0]
    return [blob[i:i + 48] for i in range(0, len(blob) - 47, 48)]


def read_scripts(rom, family):
    """The script NARC: one bytes object per script file."""
    return rom.narc(NARC_PATHS[family]["Scripts"])


# Zone header fields (48 bytes per zone; see the notes in gen5_statics.py): u16 at +6 is the zone's script file,
# u16 at +22 its map file, u16 at +24 the parent zone (the zone itself for an outdoor map), u8 at +26 the name index
# into the map name text, and the wild encounter set at +20 (u8 in Black 2 / White 2, u16 in Black / White).
ZONE_SCRIPT = 6
ZONE_MAPFILE = 22
ZONE_PARENT = 24
ZONE_NAME = 26


def zone_u16(header, offset):
    return struct.unpack_from("<H", header, offset)[0]


def zone_wild_set(header, family):
    """The wild encounter set index of a zone, or None when it has none."""
    if family == "b2w2":
        v = header[20]
        return None if v == 255 else v
    v = zone_u16(header, 20)
    return None if v == 65535 else v


ENCOUNTER_TYPES = [
    ("grass", 12), ("doubles", 12), ("shaking", 12), ("surf", 5), ("surf ripple", 5), ("fish", 5), ("fish ripple", 5),
]


def read_wild_set(blob):
    """Every (species, minimum level, maximum level, type) of one wild set file, over all four seasons."""
    out = []
    season_size = 232
    for start in range(0, len(blob) - season_size + 1, season_size):
        offset = 8
        for i, (label, count) in enumerate(ENCOUNTER_TYPES):
            rate = blob[start + i]
            if rate:
                for e in range(count):
                    pos = start + offset + e * 4
                    species = struct.unpack_from("<H", blob, pos)[0] & 0x3FF
                    out.append((species, blob[pos + 2], blob[pos + 3], label))
            offset += count * 4
    return out


def badge_order(rom, family):
    """The badge names in the order of the game's own list of badge award messages ("Basic Badge / Received:").

    The message file that holds them has one entry per badge, spaced three apart, in the game's badge index order
    (the order of the bits of the badge byte the DS tracker reads). Returns [] when no such file is found.
    """
    import re
    pattern = re.compile(r"^([A-Za-z]+) Badge\{FFFE\}Received")
    narc = rom.narc(NARC_PATHS[family]["TextStrings"])
    for blob in narc:
        try:
            strings = read_text_file(blob)
        except Exception:  # not every file in the archive is a message file
            continue
        found = []
        for text in strings:
            m = pattern.match(text)
            if m:
                found.append(m.group(1))
        if len(found) >= 8:
            return found
    return []


if __name__ == "__main__":
    key = sys.argv[1] if len(sys.argv) > 1 else "black2"
    rom = open_rom(key)
    if rom is None:
        print("missing ROM:", key)
        sys.exit(1)
    print(rom.title, rom.game_code, "files:", len(rom.paths))
    for name, p in NARC_PATHS["b2w2"].items():
        recs = rom.narc(p)
        print("  %-18s %-8s %d records, first sizes %s" % (name, p, len(recs), [len(r) for r in recs[:3]]))
