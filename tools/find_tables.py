"""Locate ability-name, item, and battle-move tables in the prepared ROMs.

Same method as the species/move name tables: anchor on known strings/values,
derive stride from a second anchor, then verify with a third before trusting.
"""
import sys
from pathlib import Path

ROMS = Path(r"C:\Users\bepor\IronMonOne\.vendor\roms")


def enc(text: str) -> bytes:
    out = bytearray()
    for ch in text:
        if "A" <= ch <= "Z":
            out.append(0xBB + ord(ch) - 65)
        elif "a" <= ch <= "z":
            out.append(0xD5 + ord(ch) - 97)
        elif ch == " ":
            out.append(0x00)
        elif "0" <= ch <= "9":
            out.append(0xA1 + ord(ch) - 48)
        else:
            raise ValueError(ch)
    return bytes(out)


def find_all(rom: bytes, needle: bytes):
    hits, i = [], rom.find(needle)
    while i != -1:
        hits.append(i)
        i = rom.find(needle, i + 1)
    return hits


def ability_table(rom: bytes):
    """base such that entry(id) = base + id*stride starts with the name."""
    stench = enc("STENCH") + b"\xff"
    drizzle = enc("DRIZZLE") + b"\xff"
    for s in find_all(rom, stench):
        for d in find_all(rom, drizzle):
            stride = d - s
            if not (8 <= stride <= 64):
                continue
            base = s - stride
            checks = {22: "INTIMIDATE", 53: "PICKUP", 66: "BLAZE", 26: "LEVITATE"}
            if all(rom[base + i * stride: base + i * stride + len(enc(n)) + 1]
                   == enc(n) + b"\xff" for i, n in checks.items()):
                return base, stride
    return None


def item_table(rom: bytes):
    master = enc("MASTER BALL") + b"\xff"
    ultra = enc("ULTRA BALL") + b"\xff"
    for m in find_all(rom, master):
        for u in find_all(rom, ultra):
            stride = u - m
            if not (16 <= stride <= 64):
                continue
            base = m - stride  # item 0 comes before item 1
            if rom[base + 13 * stride: base + 13 * stride + 7] == enc("POTION") + b"\xff":
                # vanilla has itemId mirrored at +14; report whether that holds
                mirrored = all(
                    int.from_bytes(rom[base + i * stride + 14: base + i * stride + 16],
                                   "little") == i for i in (1, 2, 13))
                return base, stride, mirrored
    return None


def battle_moves(rom: bytes):
    """stride-12 gBattleMoves: entry = effect,power,type,accuracy,pp,..."""
    # anchors: POUND(1)=40/Normal/100/35, FLAMETHROWER(53)=95/Fire/100/15,
    #          PSYCHIC(94)=90/Psychic/100/10
    for i in range(0, len(rom) - 12 * 100):
        if (rom[i + 12 + 1] == 40 and rom[i + 12 + 2] == 0
                and rom[i + 12 + 3] == 100 and rom[i + 12 + 4] == 35
                and rom[i + 12 * 53 + 1] == 95 and rom[i + 12 * 53 + 2] == 10
                and rom[i + 12 * 53 + 3] == 100 and rom[i + 12 * 53 + 4] == 15
                and rom[i + 12 * 94 + 1] == 90 and rom[i + 12 * 94 + 2] == 14
                and rom[i + 12 * 94 + 3] == 100 and rom[i + 12 * 94 + 4] == 10):
            return i
    return None


for name in ("firered-u-v10.gba", "emerald-u.gba", "emerald-natdex-121.gba"):
    rom = (ROMS / name).read_bytes()
    print(f"== {name}")
    a = ability_table(rom)
    print(f"  abilities: " + (f"base=0x{0x08000000 + a[0]:08X} stride={a[1]}" if a else "NOT FOUND"))
    it = item_table(rom)
    print(f"  items:     " + (f"base=0x{0x08000000 + it[0]:08X} stride={it[1]} idMirror={it[2]}" if it else "NOT FOUND"))
    bm = battle_moves(rom)
    print(f"  moves:     " + (f"base=0x{0x08000000 + bm:08X} stride=12" if bm is not None else "NOT FOUND"))
