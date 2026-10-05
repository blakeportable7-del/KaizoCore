#!/usr/bin/env python3
"""Write the item names of vanilla Emerald and of Emerald Nat. Dex 1.2.1, read from the real ROMs' item tables, as the
two small assets HnsEngine restricts Heart & Soul's item pools with (docs/HNS-KAIZO.md, "Item pools per pool choice"):

    python tools/hns/item_names.py --layout app/src/main/assets/hns/layout-kaizo.json \
        --emerald .vendor/roms/emerald-u.gba --natdex .vendor/roms/emerald-natdex-121.gba \
        --out app/src/main/assets/hns

The VANILLA pool rolls only items vanilla Emerald has, the NATDEX pool only items Nat. Dex Emerald 1.2.1 has. The
ROMs never leave the PC; only the names are written. Where each table is:
  - vanilla Emerald (USA, CRC 1F1C08FB): gItems at 0x085839A0, 44 bytes a record, the name in the first 14 bytes, the
    item id at 14 (the tracker's EMERALD map, tracker-gba GbaTracker.kt, itemNames);
  - Emerald Nat. Dex 1.2.1: the table the ROM's own pointer at 0x080001C8 names (the tracker's Nat. Dex map), 52 bytes
    a record, the name in the first 20 bytes, the item id at 20.
A record is an item when its id field is its own index; the game's unused slots ("????????") carry 0 and are left out.
Names are decoded with the Gen 3 character map the layout carries (the same encoding in all three games).
"""
import argparse
import json
import os
import sys
import zlib

ROM_BASE = 0x08000000

TABLES = {
    "emerald": dict(crc="1F1C08FB", file="items-emerald.tsv", game="Pokemon Emerald (USA)",
                    table=lambda rom: 0x085839A0, stride=44, name_len=14, id_off=14),
    "natdex": dict(crc=None, file="items-natdex-121.tsv", game="Pokemon Emerald Nat. Dex 1.2.1",
                   table=lambda rom: int.from_bytes(rom[0x1C8:0x1CC], "little"), stride=52, name_len=20, id_off=20),
}


def names(rom, spec, charmap):
    base = spec["table"](rom) - ROM_BASE
    if not 0 < base < len(rom):
        sys.exit("item_names.py: the item table pointer 0x%08X is outside the ROM" % (base + ROM_BASE))
    out = []
    for i in range(1, 2048):
        o = base + i * spec["stride"]
        if o + spec["stride"] > len(rom):
            break
        if int.from_bytes(rom[o + spec["id_off"]:o + spec["id_off"] + 2], "little") != i:
            continue
        raw = rom[o:o + spec["name_len"]]
        s = "".join(charmap.get(b, "?") for b in raw.split(b"\xff")[0]).strip()
        if s:
            out.append((i, s))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--layout", required=True)
    ap.add_argument("--emerald", required=True)
    ap.add_argument("--natdex", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    charmap = {int(k, 16): v for k, v in json.load(open(a.layout, encoding="utf-8"))["charmap"].items()}
    for key, path in (("emerald", a.emerald), ("natdex", a.natdex)):
        spec = TABLES[key]
        rom = open(path, "rb").read()
        crc = "%08X" % (zlib.crc32(rom) & 0xFFFFFFFF)
        if spec["crc"] and crc != spec["crc"]:
            sys.exit("item_names.py: %s is CRC %s, not %s" % (path, crc, spec["crc"]))
        rows = names(rom, spec, charmap)
        dest = os.path.join(a.out, spec["file"])
        with open(dest, "w", encoding="utf-8", newline="\n") as f:
            f.write("# %s, CRC %s: the item table's records whose id is their index (tools/hns/item_names.py)\n"
                    % (spec["game"], crc))
            for i, s in rows:
                f.write("%d\t%s\n" % (i, s))
        print("%s: %d items" % (dest, len(rows)))


if __name__ == "__main__":
    main()
