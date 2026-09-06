"""Lock the NatDex expansion palette base.

The pic table at 0x0835EB38 is indexed by (species - 411): index 1 decoded as
Turtwig, which is species 412 in the extension's own name list. This scores
candidate palette bases against fifteen species whose body colors are not in
dispute, so the winner is the one that renders known Pokemon in known colors.
"""
import struct
from pathlib import Path

rom = Path(r"C:\Users\bepor\IronMonOne\.vendor\roms\emerald-natdex-121.gba").read_bytes()
PIC = 0x35EB38
BASE_SPECIES = 411          # index = species - BASE_SPECIES

# species id -> (expected dominant hue test, human name)
ANCHORS = [
    (412, "green", "Turtwig"), (415, "orange", "Chimchar"),
    (418, "blue", "Piplup"), (417, "orange", "Infernape"),
    (430, "blue", "Luxray"), (470, "blue", "Garchomp"),
    (473, "blue", "Lucario"), (487, "grey", "Magnezone"),
    (489, "orange", "Rhyperior"), (493, "white", "Togekiss"),
    (495, "green", "Leafeon"), (496, "blue", "Glaceon"),
    (468, "blue", "Gible"), (484, "white", "Snover"),
    (513, "purple", "Cresselia"),
]


def lz77(off):
    if off < 0 or off + 4 > len(rom) or rom[off] != 0x10:
        return None
    size = rom[off + 1] | (rom[off + 2] << 8) | (rom[off + 3] << 16)
    if not (0x10 <= size <= 0x4000):
        return None
    out = bytearray(); i = off + 4
    try:
        while len(out) < size:
            flags = rom[i]; i += 1
            for bit in range(8):
                if len(out) >= size:
                    break
                if flags & (0x80 >> bit):
                    b1, b2 = rom[i], rom[i + 1]; i += 2
                    for _ in range((b1 >> 4) + 3):
                        out.append(out[-(((b1 & 0xF) << 8) | b2) - 1])
                else:
                    out.append(rom[i]); i += 1
    except IndexError:
        return None
    return bytes(out[:size])


def entry_off(table, idx):
    p = struct.unpack_from("<I", rom, table + idx * 8)[0]
    return p - 0x08000000 if 0x08000000 <= p < 0x08000000 + len(rom) else -1


def hist(idx):
    d = lz77(entry_off(PIC, idx))
    if d is None or len(d) < 0x800:
        return None
    h = [0] * 16
    for b in d[:0x800]:
        h[b & 0xF] += 1; h[b >> 4] += 1
    return h


def pal(base, idx):
    raw = lz77(entry_off(base, idx))
    if raw is None or len(raw) < 32:
        return None
    return [(((c := struct.unpack_from("<H", raw, i * 2)[0]) & 31) << 3,
             ((c >> 5) & 31) << 3, ((c >> 10) & 31) << 3) for i in range(16)]


def mean_rgb(base, idx):
    h, p = hist(idx), pal(base, idx)
    if h is None or p is None:
        return None
    tot = r = g = b = 0
    for c in range(1, 16):
        w = h[c]; tot += w
        r += p[c][0] * w; g += p[c][1] * w; b += p[c][2] * w
    return None if tot == 0 else (r / tot, g / tot, b / tot)


def hue_score(rgb, want):
    if rgb is None:
        return -500.0
    r, g, b = rgb
    mx, mn = max(r, g, b), min(r, g, b)
    sat, val = mx - mn, (r + g + b) / 3
    return {
        "green": g - max(r, b),
        "orange": (r - b) * 0.7 + (r - g) * 0.3,
        "blue": b - max(r, g),
        "yellow": min(r, g) - b,
        "purple": min(r, b) - g,
        "white": val - sat * 2,
        "grey": 100 - sat * 2,
    }[want]


CANDIDATES = ([0x361BB8 + k * 8 for k in range(400)] +
              [0x364D00 + k * 8 for k in range(400)])

scored = []
for base in CANDIDATES:
    s = sum(hue_score(mean_rgb(base, sp - BASE_SPECIES), want)
            for sp, want, _ in ANCHORS)
    scored.append((s, base))
scored.sort(reverse=True)

print("top palette bases:")
for s, b in scored[:5]:
    print(f"  score {s:8.1f}  0x{0x08000000 + b:08X}")

best = scored[0][1]
print(f"\nper-anchor detail at 0x{0x08000000 + best:08X}:")
for sp, want, name in ANCHORS:
    rgb = mean_rgb(best, sp - BASE_SPECIES)
    got = "  ".join(f"{v:5.0f}" for v in rgb) if rgb else "none"
    print(f"  {name:10s} want {want:7s} rgb {got}  score {hue_score(rgb, want):6.1f}")
