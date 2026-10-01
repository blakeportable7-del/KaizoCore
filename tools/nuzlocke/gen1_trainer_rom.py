#!/usr/bin/env python
"""Read a Generation 1 trainer party out of a Red, Blue or Yellow ROM image (the walk the Kotlin side ports).

    python tools/nuzlocke/gen1_trainer_rom.py --check    # every party in parties.asm against the three ROM dumps

Layout, verified on Red, Blue and Yellow (US):

* TrainerDataPointers is a table of 47 little-endian 16 bit pointers, one per trainer class 1..47 (pointer k-1 is class k,
  the number wTrainerClass holds). It sits at file offset 0x39D3B in Red and Blue and 0x39DD1 in Yellow (the randomizer's
  TrainerDataTableOffset in gen1_offsets.ini). The pointers are inside bank 0x0E, so file offset = 0x38000 + pointer - 0x4000.
* A class's parties follow one another from its pointer; party number `no` (wTrainerNo, 1 based) is the no-th one. A party
  ends at the first 0 byte, so skipping (no - 1) parties is skipping to the next 0 byte that many times.
* A party is in one of two formats:
    fixed level:   level, species, species, ..., 0            (the first byte is not 0xFF; every Pokemon has that level)
    per Pokemon:   0xFF, level, species, level, species, ..., 0
  Species are the game's internal ids (not dex numbers).
"""
import sys
from pathlib import Path

sys.dont_write_bytecode = True     # never leave .pyc files behind in the repo

TABLE = {"rb": 0x39D3B, "y": 0x39DD1}      # file offset of TrainerDataPointers
BANK_BASE = 0x38000 - 0x4000               # bank 0x0E: file offset = BANK_BASE + pointer


def party(rom, game, class_no, no):
    """[(level, species id)] of party `no` of trainer class `class_no`; `game` is "rb" or "y"."""
    slot = TABLE[game] + 2 * (class_no - 1)
    p = BANK_BASE + (rom[slot] | rom[slot + 1] << 8)
    for _ in range(no - 1):                # skip the earlier parties: each ends at its first 0 byte
        while rom[p] != 0:
            p += 1
        p += 1
    mons = []
    if rom[p] == 0xFF:                     # a level in front of every species
        p += 1
        while rom[p] != 0:
            mons.append((rom[p], rom[p + 1]))
            p += 2
    else:                                  # one level, then the species
        level = rom[p]
        p += 1
        while rom[p] != 0:
            mons.append((level, rom[p]))
            p += 1
    return mons


def max_level(rom_bytes, game, class_no, no):
    """The highest level on the team of party `no` of class `class_no` (0 for a party with no Pokemon)."""
    return max((level for level, _ in party(rom_bytes, game, class_no, no)), default=0)


def check():
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import gen1_common as g
    bad = 0
    total = 0
    for build in ("red", "blue", "yellow"):
        game = g.GAME_OF[build]
        d = g.Disasm(build)
        rom = g.Rom(build)
        rom.verify_build()
        if g.ini_int(rom.ini, "TrainerDataTableOffset") != TABLE[game]:
            print("MISMATCH: %s: gen1_offsets.ini TrainerDataTableOffset is not %#x" % (build, TABLE[game]))
            bad += 1
        sp = d.species_consts()
        n = 0
        for cls, parties in d.parties().items():
            for no, pt in enumerate(parties, 1):
                want_mons = [(lv, sp[s]) for lv, s in pt]
                got_mons = party(rom.data, game, cls, no)
                got = max_level(rom.data, game, cls, no)
                want = max(lv for lv, _ in pt)
                n += 1
                if got != want or got_mons != want_mons:
                    bad += 1
                    print("MISMATCH: %s class %d party %d: ROM %s (max %d), parties.asm %s (max %d)" % (
                        build, cls, no, got_mons, got, want_mons, want))
        total += n
        print("%s: compared the highest level of %d parties in %d classes with parties.asm" % (build, n, len(d.parties())))
    print("total parties compared: %d, mismatches: %d" % (total, bad))
    return 1 if bad else 0


if __name__ == "__main__":
    if "--check" in sys.argv[1:]:
        sys.exit(check())
    print(__doc__)
