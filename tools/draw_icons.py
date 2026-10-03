"""
Draw the app's own type and status icons, replacing the ones copied from the
tracker repositories (which are Nintendo's art). Same file names, same sizes,
so nothing in the app changes: types/<name>.png 30x12, status/<CODE>.png 16x8.
The badges are the trackers' own badge art (NOTICE, BUNDLED IN THE APK): this
script does not draw them, so it cannot write over them.

    python tools/draw_icons.py

Everything here is drawn from primitives and a 3x5 pixel alphabet defined
below. No source image is read.
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")

# 3x5 pixel alphabet, rows top to bottom, 1 = lit.
GLYPHS = {
    "A": ["010", "101", "111", "101", "101"], "B": ["110", "101", "110", "101", "110"],
    "C": ["011", "100", "100", "100", "011"], "D": ["110", "101", "101", "101", "110"],
    "E": ["111", "100", "110", "100", "111"], "F": ["111", "100", "110", "100", "100"],
    "G": ["011", "100", "101", "101", "011"], "H": ["101", "101", "111", "101", "101"],
    "I": ["111", "010", "010", "010", "111"], "J": ["001", "001", "001", "101", "010"],
    "K": ["101", "101", "110", "101", "101"], "L": ["100", "100", "100", "100", "111"],
    "M": ["101", "111", "111", "101", "101"], "N": ["110", "101", "101", "101", "101"],
    "O": ["010", "101", "101", "101", "010"], "P": ["110", "101", "110", "100", "100"],
    "Q": ["010", "101", "101", "110", "011"], "R": ["110", "101", "110", "101", "101"],
    "S": ["011", "100", "010", "001", "110"], "T": ["111", "010", "010", "010", "010"],
    "U": ["101", "101", "101", "101", "011"], "V": ["101", "101", "101", "101", "010"],
    "W": ["101", "101", "111", "111", "101"], "X": ["101", "101", "010", "101", "101"],
    "Y": ["101", "101", "010", "010", "010"], "Z": ["111", "001", "010", "100", "111"],
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"], "3": ["111", "001", "011", "001", "111"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "111"],
    "?": ["111", "001", "011", "000", "010"], " ": ["000", "000", "000", "000", "000"],
}

def text(draw, x, y, s, color):
    for ch in s:
        g = GLYPHS.get(ch, GLYPHS["?"])
        for r, row in enumerate(g):
            for c, bit in enumerate(row):
                if bit == "1":
                    draw.point((x + c, y + r), color)
        x += 4

def text_width(s):
    return len(s) * 4 - 1

# ---- types: the tracker's colour per type, a darker rim, the name in white.
TYPES = {
    "normal": ("NORMAL", (168, 168, 120)), "fighting": ("FIGHT", (192, 48, 40)), "flying": ("FLYING", (168, 144, 240)),
    "poison": ("POISON", (160, 64, 160)), "ground": ("GROUND", (224, 192, 104)), "rock": ("ROCK", (184, 160, 56)),
    "bug": ("BUG", (168, 184, 32)), "ghost": ("GHOST", (112, 88, 152)), "steel": ("STEEL", (184, 184, 208)),
    "fire": ("FIRE", (240, 128, 48)), "water": ("WATER", (104, 144, 240)), "grass": ("GRASS", (120, 200, 80)),
    "electric": ("ELECTR", (248, 208, 48)), "psychic": ("PSYCHC", (248, 88, 136)), "ice": ("ICE", (152, 216, 216)),
    "dragon": ("DRAGON", (112, 56, 248)), "dark": ("DARK", (112, 88, 72)), "fairy": ("FAIRY", (238, 153, 172)),
    "unknown": ("???", (104, 160, 144)),
}

def darker(c, f=0.55):
    return tuple(int(v * f) for v in c)

def type_icon(label, color):
    im = Image.new("RGBA", (30, 12), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 29, 11), fill=color + (255,), outline=darker(color) + (255,))
    w = text_width(label)
    x = (30 - w) // 2
    ink = (255, 255, 255, 255) if sum(color) < 560 else (30, 30, 30, 255)
    text(d, x, 3, label, ink)
    return im

# ---- status: 16x8 chips.
STATUS = {"BRN": (240, 128, 48), "FRZ": (152, 216, 216), "PAR": (248, 208, 48), "PSN": (160, 64, 160), "SLP": (140, 136, 120), "FNT": (200, 64, 64)}

def status_icon(code, color):
    im = Image.new("RGBA", (16, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 15, 7), fill=color + (255,), outline=darker(color) + (255,))
    ink = (255, 255, 255, 255) if sum(color) < 560 else (30, 30, 30, 255)
    text(d, 3, 1, code, ink)
    return im

def main():
    for name, (label, color) in TYPES.items():
        type_icon(label, color).save(os.path.join(ROOT, "types", name + ".png"))
    for code, color in STATUS.items():
        status_icon(code, color).save(os.path.join(ROOT, "status", code + ".png"))
    print("types", len(TYPES), "status", len(STATUS))

if __name__ == "__main__":
    main()
