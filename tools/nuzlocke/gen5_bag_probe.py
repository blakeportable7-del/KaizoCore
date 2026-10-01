"""Where the Generation 5 bag lives in DS main RAM, and where a Poke Ball count can be read (research probe, no data file).

    python tools/nuzlocke/gen5_bag_probe.py

The DS tracker reads the Medicine pocket at NdsGameMap.itemStartNoBattle and the Berries pocket at berryBagStart
(NdsTracker scans a pocket slot by slot, u16 item id then u16 quantity, until an empty slot). The bag block of a
Gen 5 save holds five pockets one after the other; PKHeX's PlayerBag5BW and PlayerBag5B2W2 (identical) give their
offsets from the start of the block:

    Items 0x000   Key Items 0x4D8   TMs and HMs 0x624   Medicine 0x7D8   Berries 0x898

So the Items pocket, where the Poke Balls are (item ids 1 to 16: Master Ball 1, Ultra Ball 2, Great Ball 3,
Poke Ball 4 ...), starts 0x7D8 bytes below the Medicine address: itemStartNoBattle - 0x7D8. It holds 310 slots (the
0x4D8 bytes up to the Key Items pocket), 4 bytes each.

This script proves the layout on the two Black 2 RAM dumps in .vendor/dumps: relative to the tracker's addresses the
whole heap of those dumps sits 0x40 lower (NdsDumpReplayTest documents the shift), and with that shift the Key Items
pocket, which the game fills with the Xtransceiver (621) and the Pal Pad (437) at the very start, is found exactly
0x300 below the Medicine address, i.e. at bag block + 0x4D8. Both dumps were taken before the first Poke Ball, so
their Items pocket is empty: that the balls are in it comes from PKHeX's item lists and from the game's own text
(text archive a/0/0/2, file 73: the Bag tutorial says the Poke Balls are kept in the ITEMS case).
"""
import struct
import sys
from pathlib import Path

DUMPS = Path("C:/Users/bepor/IronMonOne/.vendor/dumps")
SHIFT = -0x40                      # the dumps' heap versus the tracker's addresses
MEDICINE_BLACK2 = 0x21E1FC         # NdsGameMap.B2W2.itemStartNoBattle
POCKETS = [("Items", 0x000, 310), ("Key Items", 0x4D8, 83), ("TMs and HMs", 0x624, 109), ("Medicine", 0x7D8, 48), ("Berries", 0x898, 64)]
GAMES = {  # itemStartNoBattle of each game (NdsGameMap; White is Black + 0x20, White 2 is Black 2 + 0x80)
    "Black": 0x234784, "White": 0x234784 + 0x20, "Black 2": 0x21E1FC, "White 2": 0x21E1FC + 0x80,
}


def main():
    problems = []
    for name in ("b2-clean-intro.bin", "b2-rand-rival-battle.bin"):
        path = DUMPS / name
        if not path.exists():
            print("missing dump", path)
            return 2
        ram = path.read_bytes()
        bag = MEDICINE_BLACK2 - 0x7D8 + SHIFT
        print("%s: bag block starts at 0x%X (main RAM offset)" % (name, bag))
        for pocket, off, slots in POCKETS:
            at = bag + off
            used = [(i, struct.unpack_from("<HH", ram, at + 4 * i)) for i in range(slots)]
            used = [(i, s) for i, s in used if s != (0, 0)]
            print("  %-11s at 0x%X, %3d slots, non-empty: %s" % (pocket, at, slots, used))
            if pocket == "Key Items" and [s for _i, s in used] != [(621, 1), (437, 1)]:
                problems.append("%s: Key Items pocket is not the Xtransceiver and the Pal Pad" % name)
            if pocket != "Key Items" and used:
                problems.append("%s: %s is not empty" % (name, pocket))
        key = bag + 0x4D8
        print("  key pocket bytes:", " ".join("%02x" % b for b in ram[key:key + 16]))
    print("first Items pocket slot (tracker address = itemStartNoBattle - 0x7D8, 310 slots of u16 id, u16 quantity):")
    for game, medicine in GAMES.items():
        print("  %-8s 0x%X  (Medicine at 0x%X, Berries at 0x%X)" % (game, medicine - 0x7D8, medicine, medicine + 0xC0))
    for p in problems:
        print("PROBLEM:", p)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
