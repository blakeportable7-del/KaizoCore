"""Locate the compressed sprite (front pic) and palette tables in the ROMs.

A pic table is a long run of 8-byte entries {u32 romPtr, u16 size, u16 tag}
whose pointers land on GBA LZ77 headers (byte 0x10) with a 0x800 decompressed
size (64x64 4bpp). Palette tables look the same but decompress to 0x20-0x40.
The candidates are decoded to PNGs for a human eye to pick front vs back and
normal vs shiny - the addresses only get trusted after that look.
"""
import struct
import sys
from pathlib import Path

ROMS = Path(r"C:\Users\bepor\IronMonOne\.vendor\roms")
OUT = Path(r"C:\Users\bepor\IronMonOne\.vendor\sprites")
OUT.mkdir(exist_ok=True)


def lz77(rom: bytes, off: int) -> bytes | None:
    if off + 4 > len(rom) or rom[off] != 0x10:
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


def entry(rom, off):
    ptr, size, tag = struct.unpack_from("<IHH", rom, off)
    return ptr, size, tag


def is_pic(rom, off, want):
    ptr, _, _ = entry(rom, off)
    if not (0x08000000 <= ptr < 0x08000000 + len(rom) - 4):
        return False
    p = ptr - 0x08000000
    if p + 4 > len(rom) or rom[p] != 0x10:
        return False
    size = rom[p + 1] | (rom[p + 2] << 8) | (rom[p + 3] << 16)
    return size in want


def find_tables(rom, want, min_run):
    """Longest aligned runs of entries whose pointers hit LZ77 data of `want` size."""
    runs = []
    off = 0
    n = len(rom) - 8
    while off < n:
        if is_pic(rom, off, want):
            start = off
            while off < n and is_pic(rom, off, want):
                off += 8
            count = (off - start) // 8
            if count >= min_run:
                runs.append((start, count))
        else:
            off += 4
    return runs


def draw_png(pixels_rgba, w, h, path):
    import zlib
    raw = b"".join(
        b"\x00" + bytes(v for px in pixels_rgba[y * w:(y + 1) * w] for v in px)
        for y in range(h))
    def chunk(t, d):
        c = t + d
        return struct.pack(">I", len(d)) + c + struct.pack(">I", zlib.crc32(c))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw))
           + chunk(b"IEND", b""))
    path.write_bytes(png)


def decode_sprite(rom, pic_table, pal_table, species, path):
    ptr, _, _ = entry(rom, pic_table + species * 8)
    data = lz77(rom, ptr - 0x08000000)
    pptr, _, _ = entry(rom, pal_table + species * 8)
    pal_raw = lz77(rom, pptr - 0x08000000)
    if data is None or pal_raw is None or len(data) < 0x800:
        return False
    pal = []
    for i in range(16):
        c = struct.unpack_from("<H", pal_raw, i * 2)[0]
        r = (c & 31) << 3; g = ((c >> 5) & 31) << 3; b = ((c >> 10) & 31) << 3
        pal.append((r, g, b, 0 if i == 0 else 255))
    px = [(0, 0, 0, 0)] * (64 * 64)
    for t in range(64):                      # 8x8 tiles
        tx, ty = (t % 8) * 8, (t // 8) * 8
        for i in range(32):
            b = data[t * 32 + i]
            x = (i % 4) * 2; y = i // 4
            px[(ty + y) * 64 + tx + x] = pal[b & 0xF]
            px[(ty + y) * 64 + tx + x + 1] = pal[b >> 4]
    draw_png(px, 64, 64, path)
    return True


def main(name, probe_species):
    rom = (ROMS / name).read_bytes()
    pics = find_tables(rom, {0x800}, 200)
    pals = find_tables(rom, set(range(0x20, 0x48)), 200)
    print(f"== {name}")
    print("  pic runs:", [(hex(0x08000000 + s), c) for s, c in pics[:6]])
    print("  pal runs:", [(hex(0x08000000 + s), c) for s, c in pals[:6]])
    stem = name.split(".")[0]
    for pi, (ps, _) in enumerate(pics[:4]):
        for qi, (qs, _) in enumerate(pals[:4]):
            p = OUT / f"{stem}-pic{pi}-pal{qi}.png"
            if decode_sprite(rom, ps, qs, probe_species, p):
                print(f"  wrote {p.name}  (pic@{hex(0x08000000+ps)} pal@{hex(0x08000000+qs)})")


main("firered-u-v10.gba", 59)          # Arcanine
main("emerald-u.gba", 59)
main("emerald-natdex-121.gba", 59)
