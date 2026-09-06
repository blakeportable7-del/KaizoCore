"""Crack the NatDex sprite tables: find the palette-table base that makes known
species decode in their signature colors (Bulbasaur green, Charizard orange,
Pikachu yellow, Arcanine orange). The pic tables were found earlier; the open
question is which palette base pairs with them and whether the index is the
species id."""
import struct
from pathlib import Path

ROMS = Path(r"C:\Users\bepor\IronMonOne\.vendor\roms")
OUT = Path(r"C:\Users\bepor\IronMonOne\.vendor\sprites")
rom = (ROMS / "emerald-natdex-121.gba").read_bytes()


def lz77(off):
    if off < 0 or off + 4 > len(rom) or rom[off] != 0x10:
        return None
    size = rom[off + 1] | (rom[off + 2] << 8) | (rom[off + 3] << 16)
    if not (0x10 <= size <= 0x4000):
        return None
    out = bytearray()
    i = off + 4
    try:
        while len(out) < size:
            flags = rom[i]; i += 1
            for bit in range(8):
                if len(out) >= size:
                    break
                if flags & (0x80 >> bit):
                    b1, b2 = rom[i], rom[i + 1]; i += 2
                    ln = (b1 >> 4) + 3
                    disp = ((b1 & 0xF) << 8) | b2
                    for _ in range(ln):
                        out.append(out[-disp - 1])
                else:
                    out.append(rom[i]); i += 1
    except IndexError:
        return None
    return bytes(out[:size])


def table_ptr(table, idx):
    p = struct.unpack_from("<I", rom, table + idx * 8)[0]
    return p - 0x08000000 if 0x08000000 <= p < 0x08000000 + len(rom) else -1


def palette(pal_base, idx):
    raw = lz77(table_ptr(pal_base, idx))
    if raw is None or len(raw) < 32:
        return None
    cols = []
    for i in range(16):
        c = struct.unpack_from("<H", raw, i * 2)[0]
        cols.append(((c & 31) << 3, ((c >> 5) & 31) << 3, ((c >> 10) & 31) << 3))
    return cols


def pixel_hist(pic_base, idx):
    """Palette-index histogram of the sprite (which colors dominate)."""
    data = lz77(table_ptr(pic_base, idx))
    if data is None or len(data) < 0x800:
        return None
    hist = [0] * 16
    for b in data[:0x800]:
        hist[b & 0xF] += 1
        hist[b >> 4] += 1
    return hist


def dominant_rgb(pic_base, pal_base, idx):
    """Weighted average RGB of non-background pixels."""
    hist = pixel_hist(pic_base, idx)
    pal = palette(pal_base, idx)
    if hist is None or pal is None:
        return None
    tot = r = g = b = 0
    for c in range(1, 16):
        w = hist[c]
        tot += w
        r += pal[c][0] * w; g += pal[c][1] * w; b += pal[c][2] * w
    if tot == 0:
        return None
    return (r / tot, g / tot, b / tot)


def score(rgb, want):
    if rgb is None:
        return -1e9
    r, g, b = rgb
    if want == "green":
        return g - max(r, b)
    if want == "yellow":
        return min(r, g) - b
    if want == "orange":
        return r - b + (g - b) * 0.3
    return 0


# Known anchors: internal id -> signature body color
ANCHORS = {1: "green", 6: "orange", 25: "yellow", 59: "orange"}
PIC_TABLES = {"pic1": 0x35eb38, "pic3": 0x36a904}
# Candidate palette bases: slide through both big palette runs entry by entry.
CAND = [0x361bb8 + k * 8 for k in range(0, 1576 - 1283)] + \
       [0x364d00 + k * 8 for k in range(0, 200)]

best = []
for pic_name, pic in PIC_TABLES.items():
    for base in CAND:
        s = sum(score(dominant_rgb(pic, base, i), w) for i, w in ANCHORS.items())
        best.append((s, pic_name, base))
best.sort(reverse=True)
for s, pn, b in best[:6]:
    print(f"score {s:8.1f}  {pn}  pal_base=0x{0x08000000 + b:08X}")
