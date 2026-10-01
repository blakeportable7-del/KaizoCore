"""Walk the trainer tables of a Gold, Silver or Crystal ROM (2026-09-29).

    python tools/nuzlocke/gen2_trainer_rom.py --check         # every party of both dumps against parties.asm
    python tools/nuzlocke/gen2_trainer_rom.py gs 1 1          # Falkner: class 1, party 1 (highest level, then the team)
    python tools/nuzlocke/gen2_trainer_rom.py c 67 1          # Eusine

The layout, from pokegold and pokecrystal data/trainers/party_pointers.asm and parties.asm and read back out of
both clean dumps:

  * TrainerGroups is a table of 16-bit little-endian pointers, one per trainer class from class 1 (class 0 is
    TRAINER_NONE and has no entry): 66 classes in Gold and Silver, 67 in Crystal (Mysticalman, Eusine's class).
    It sits at ROM offset TABLE (the randomizer's TrainerDataTableOffset in gen2_offsets.ini).
  * A pointer is bank-local (0x4000..0x7FFF) and points into the same bank as the table itself, so
        rom offset = bank * 0x4000 + (pointer - 0x4000)      with   bank = TABLE // 0x4000.
  * A class is a run of parties, one after another, and the class's pointer is the first. A party is
        name bytes ending in 0x50 ('@'), one type byte, the Pokemon, and a 0xFF terminator.
    The type byte is a bit set: bit 0 = every Pokemon has four moves, bit 1 = every Pokemon holds an item.
    One Pokemon is  level, species, [item], [move1 move2 move3 move4]  (2 to 7 bytes).
  * wOtherTrainerID is 1-based: party `no` of a class is reached by skipping no - 1 whole parties
    (ReadTrainerParty in engine/battle/read_trainer_party.asm does it by counting 0xFF bytes).
  * wOtherTrainerClass is the class number, the 1-based index into that pointer table (FALKNER = 1).

Gold and Silver share every one of these offsets (the randomizer's [Silver (U)] copies [Gold (U)]); only the
Gold dump is on hand. Nothing from a ROM is copied into the repo.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen2_common as C  # noqa: E402

# game key -> (ROM offset of TrainerGroups, number of trainer classes)
GAMES = {"gs": (0x3993E, 66), "c": (0x39999, 67)}
ALIASES = {"gold": "gs", "silver": "gs", "g": "gs", "s": "gs", "gs": "gs", "crystal": "c", "c": "c"}


def game_key(game):
    return ALIASES[game.lower()]


def party_at(rom, pos):
    """One party at ROM offset pos: (name bytes, type byte, [Pokemon records], offset after the party)."""
    start = pos
    while rom[pos] != 0x50:            # the name ends with '@'
        pos += 1
    name = bytes(rom[start:pos])
    ptype = rom[pos + 1]
    pos += 2
    size = 2 + (4 if ptype & 1 else 0) + (1 if ptype & 2 else 0)
    mons = []
    while rom[pos] != 0xFF:
        mons.append(bytes(rom[pos:pos + size]))
        pos += size
    return name, ptype, mons, pos + 1


def class_start(rom, game, class_no):
    """ROM offset of the first party of a trainer class."""
    table, n_classes = GAMES[game_key(game)]
    if not 1 <= class_no <= n_classes:
        raise ValueError("class %d out of range 1..%d" % (class_no, n_classes))
    at = table + 2 * (class_no - 1)
    ptr = rom[at] | (rom[at + 1] << 8)
    if not 0x4000 <= ptr < 0x8000:
        raise ValueError("class %d: pointer %04X is not a bank-local pointer" % (class_no, ptr))
    return (table // 0x4000) * 0x4000 + (ptr - 0x4000)


def party(rom, game, class_no, no):
    """Party number `no` (1-based, wOtherTrainerID) of a class: (name, type, mons, end)."""
    pos = class_start(rom, game, class_no)
    for _ in range(no - 1):
        pos = party_at(rom, pos)[3]
    return party_at(rom, pos)


def max_level(rom_bytes, game, class_no, no):
    """The highest level on the team of trainer class `class_no`, party `no`: the level cap of that fight.
    A record's first byte is the level."""
    return max(m[0] for m in party(rom_bytes, game, class_no, no)[2])


# ------------------------------------------------------------------------------------------------- check
def check():
    probs = C.Problems()
    compared = 0
    for game, ini_name in (("gs", "Gold (U)"), ("c", "Crystal (U)")):
        rom = C.load_rom(game)
        root = C.REPO[game]
        table, n_classes = GAMES[game]
        ini = C.ini_section(ini_name)
        probs.check(int(ini["TrainerDataTableOffset"], 16) == table, "%s table offset differs from the ini" % game)
        counts = eval(ini["TrainerDataClassCounts"])
        probs.check(int(ini["TrainerClassAmount"], 16) == n_classes and len(counts) == n_classes, "%s class amount" % game)
        labels = C.parse_group_labels(root)
        groups = C.parse_parties(root)
        charmap = C.Charmap(root)
        probs.check(len(labels) == n_classes, "%s: TrainerGroups lists %d classes" % (game, len(labels)))
        n_parties = 0
        last = n_classes
        for cls in range(1, n_classes + 1):
            src = groups[labels[cls - 1]]
            # the ROM walk, class by class; the last class is bounded by the ini count
            pos = class_start(rom, game, cls)
            rom_parties = []
            want = counts[cls - 1]
            while len(rom_parties) < want:
                name, ptype, mons, pos = party_at(rom, pos)
                rom_parties.append((name, ptype, mons))
            if cls < last:
                probs.check(pos == class_start(rom, game, cls + 1),
                            "%s class %d: parties end at %X but the next class starts at %X" % (
                                game, cls, pos, class_start(rom, game, cls + 1)))
            probs.check(len(src) == want == len(rom_parties), "%s class %d: %d parties in parties.asm, %d in the ini" % (
                game, cls, len(src), want))
            for no in range(1, len(src) + 1):
                p = src[no - 1]
                rname, ptype, mons = rom_parties[no - 1]
                probs.check(rname == charmap.encode(p["name"]), "%s class %d party %d: name bytes differ from %r" % (
                    game, cls, no, p["name"]))
                # the function under test, not the loop above
                got = max_level(rom, game, cls, no)
                exp = max(m["level"] for m in p["mons"])
                compared += 1
                n_parties += 1
                probs.check(got == exp, "%s class %d party %d: ROM highest level %d, parties.asm %d" % (game, cls, no, got, exp))
                probs.check(ptype == p["type"], "%s class %d party %d: type %d vs %d" % (game, cls, no, ptype, p["type"]))
                probs.check([(m[0], m[1]) for m in mons] == [(m["level"], m["species"]) for m in p["mons"]],
                            "%s class %d party %d: level/species list differs" % (game, cls, no))
        print("%s: %d classes, %d parties compared (highest level, type, name, level and species of every Pokemon)" % (
            C.GAME_NAME[game], n_classes, n_parties))
    print("total parties compared: %d" % compared)
    probs.finish("gen2_trainer_rom --check")


def main(argv):
    if argv[1:2] == ["--check"]:
        check()
        return
    if len(argv) == 4:
        game = game_key(argv[1])
        rom = C.load_rom(game)
        cls, no = int(argv[2]), int(argv[3])
        name, ptype, mons, _ = party(rom, game, cls, no)
        print("max level", max_level(rom, game, cls, no), "type", ptype, "mons", [(m[0], m[1]) for m in mons])
        return
    print(__doc__)


if __name__ == "__main__":
    main(sys.argv)
