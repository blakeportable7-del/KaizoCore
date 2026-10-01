"""Small NDS file-system and NARC reader shared by the Gen 4 Nuzlocke data generators (2026-09-29).

Reads a clean ROM dump (never copies bytes into the repo): the NDS header, the file name table (FNT) and the file
allocation table (FAT) give every file by path, and a NARC archive splits into its member files.

Also holds the small Gen 4 decoders the generators share: personal data, evolution data and trainer data
(trdata.narc / trpoke.narc), and the DS ROM locations of the dumps on Blake's machine.

Nothing here needs a third-party package.
"""
import hashlib
import os
import struct
import sys

sys.dont_write_bytecode = True

ROMS_VENDOR = "C:/Users/bepor/IronMonOne/.vendor/roms"
ROMS_SCRATCH = ("C:/Users/bepor/AppData/Local/Temp/claude/C--Users-bepor-willowcreek-v2-deploy-zip/"
                "c84daccc-781f-450a-becb-7ad5c6ff979d/scratchpad/roms")
PLATINUM_ROM = ROMS_VENDOR + "/platinum-u.nds"
DIAMOND_ROM = ROMS_SCRATCH + "/diamond-u.nds"


class NdsRom:
    """An NDS ROM image: header fields plus a path -> bytes file reader."""

    def __init__(self, path):
        self.path = path
        with open(path, "rb") as f:
            self.data = f.read()
        d = self.data
        self.sha1 = hashlib.sha1(d).hexdigest()
        self.title = d[0:12].rstrip(b"\x00").decode("ascii", "replace")
        self.game_code = d[12:16].decode("ascii", "replace")
        self.revision = d[0x1E]
        fnt_off, fnt_size, fat_off, fat_size = struct.unpack_from("<IIII", d, 0x40)
        self._fnt = d[fnt_off:fnt_off + fnt_size]
        n_files = fat_size // 8
        self._fat = [struct.unpack_from("<II", d, fat_off + 8 * i) for i in range(n_files)]
        self.paths = {}
        self._walk(0xF000, "")

    def _walk(self, dir_id, prefix):
        idx = dir_id & 0xFFF
        sub_off, first_id, _parent = struct.unpack_from("<IHH", self._fnt, 8 * idx)
        pos = sub_off
        fid = first_id
        while True:
            b = self._fnt[pos]
            pos += 1
            if b == 0:
                break
            n = b & 0x7F
            name = self._fnt[pos:pos + n].decode("ascii", "replace")
            pos += n
            if b & 0x80:
                (child,) = struct.unpack_from("<H", self._fnt, pos)
                pos += 2
                self._walk(child, prefix + name + "/")
            else:
                self.paths[prefix + name] = fid
                fid += 1

    def read(self, path):
        fid = self.paths[path]
        start, end = self._fat[fid]
        return self.data[start:end]

    def find(self, suffix):
        return sorted(p for p in self.paths if p.endswith(suffix))


class Narc:
    """A NARC archive: .files is the list of member byte strings."""

    def __init__(self, blob):
        if blob[:4] != b"NARC":
            raise ValueError("not a NARC")
        hdr_size = struct.unpack_from("<H", blob, 12)[0]
        pos = hdr_size
        if blob[pos:pos + 4] != b"BTAF":
            raise ValueError("BTAF missing")
        btaf_size, count = struct.unpack_from("<IH", blob, pos + 4)
        entries = [struct.unpack_from("<II", blob, pos + 12 + 8 * i) for i in range(count)]
        pos += btaf_size
        if blob[pos:pos + 4] != b"BTNF":
            raise ValueError("BTNF missing")
        btnf_size = struct.unpack_from("<I", blob, pos + 4)[0]
        pos += btnf_size
        if blob[pos:pos + 4] != b"GMIF":
            raise ValueError("GMIF missing")
        base = pos + 8
        self.files = [blob[base + s:base + e] for s, e in entries]

    def __len__(self):
        return len(self.files)


# ---------------------------------------------------------------------------------------------------------------
# Gen 4 personal data (poketool/personal/personal.narc in Diamond and Pearl, pl_personal.narc in Platinum).
# 44-byte records: base stats 0x00..0x05, types 0x06 and 0x07, gender ratio 0x10.
# ---------------------------------------------------------------------------------------------------------------
PERSONAL_SIZE = 44


def personal_path(rom):
    hits = rom.find("poketool/personal/pl_personal.narc") or rom.find("poketool/personal/personal.narc")
    if not hits:
        raise KeyError("no personal narc in " + rom.path)
    return hits[0]


def read_personal(rom):
    narc = Narc(rom.read(personal_path(rom)))
    out = []
    for f in narc.files:
        if len(f) != PERSONAL_SIZE:
            raise ValueError("personal record is %d bytes, expected %d" % (len(f), PERSONAL_SIZE))
        out.append(f)
    return out


# Evolution data (poketool/personal/evo.narc): one 44-byte file per species: 7 slots of (method u16, param u16,
# target u16); a slot with method 0 is empty.
EVO_SLOTS = 7


def read_evo(rom):
    hits = rom.find("poketool/personal/evo.narc")
    if not hits:
        raise KeyError("no evo narc in " + rom.path)
    narc = Narc(rom.read(hits[0]))
    out = []
    for f in narc.files:
        slots = []
        for i in range(EVO_SLOTS):
            method, param, target = struct.unpack_from("<HHH", f, 6 * i)
            if method != 0:
                slots.append((method, param, target))
        out.append(slots)
    return out


# ---------------------------------------------------------------------------------------------------------------
# Gen 4 trainer data. trdata.narc: 20-byte header per trainer (type u8, class u8, sprite? u8, party size u8,
# 4 items u16, ai flags u32, double battle u32). trpoke.narc: the party, whose entry size depends on the type:
# bit 0 of the type = custom moves, bit 1 = held item. Diamond/Pearl and Platinum both use 6 bytes per member
# (dv u8, ability/difficulty u8, level u16, species u16 with the form in the top bits), plus 2 for a held item and
# 8 for four moves; Platinum has a trailing u16 (ball capsule) per member.
# ---------------------------------------------------------------------------------------------------------------
def read_trainers(rom, member_extra):
    """Returns a list indexed by trainer id: dict(type, cls, size, party=[(species, level, item, moves)])."""
    trdata = Narc(rom.read(rom.find("poketool/trainer/trdata.narc")[0]))
    trpoke = Narc(rom.read(rom.find("poketool/trainer/trpoke.narc")[0]))
    if len(trdata) != len(trpoke):
        raise ValueError("trdata and trpoke differ in length")
    out = []
    for hdr, body in zip(trdata.files, trpoke.files):
        ttype, cls, _spr, size = struct.unpack_from("<BBBB", hdr, 0)
        has_moves = bool(ttype & 1)
        has_item = bool(ttype & 2)
        pos = 0
        party = []
        for _ in range(size):
            _dv, _abil, level, sp = struct.unpack_from("<BBHH", body, pos)
            pos += 6
            item = 0
            moves = []
            if has_item:
                (item,) = struct.unpack_from("<H", body, pos)
                pos += 2
            if has_moves:
                moves = list(struct.unpack_from("<4H", body, pos))
                pos += 8
            pos += member_extra
            party.append((sp & 0x3FF, level, item, moves))
        out.append({"type": ttype, "cls": cls, "size": size, "party": party, "hdr": hdr})
    return out


# Diamond's map header table lives in arm9 at RAM 0x020EEDBC, 559 entries of 24 bytes (pret pokediamond
# map_header_resolve_fields.py and include/map_header.h): area_data_bank u8, move_model_bank u8, matrix_id u16,
# scripts_bank u16, level_scripts_bank u16, msg_bank u16, day_music u16, night_music u16, wild_encounter_bank u16,
# events_bank u16, mapsec u16, weather u8, camera u8, map_type u8, flags u8. Its index is the map header id the DS
# tracker reports (childMapHeader).
DP_HEADER_TABLE_RAM = 0x020EEDBC
DP_HEADER_COUNT = 0x3468 // 24


def dp_map_headers(rom):
    """The Diamond dump's map headers as tuples of the 15 fields above, indexed by header id."""
    arm9_off, _entry, arm9_ram, _size = struct.unpack_from("<IIII", rom.data, 0x20)
    tbl = arm9_off + (DP_HEADER_TABLE_RAM - arm9_ram)
    return [struct.unpack_from("<BBHHHHHHHHHBBBB", rom.data, tbl + 24 * i) for i in range(DP_HEADER_COUNT)]


def rom_or_none(path):
    return NdsRom(path) if os.path.exists(path) else None
