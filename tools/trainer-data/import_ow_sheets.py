"""Cut the overworld sheets we have into walkingpals-ow and point natdex-map at them.

Sources:
  Willøw's Eevee Expo 1475 pack (the two zips in Downloads): the 26 megas whose own sheet is in that pack.
  JinGuinsoO00's 256x256 sheets for Feraligatr-M and Garchomp-Z.
  The Heart & Soul source's follower overworlds for the forms that have one there.
  Mega Delphox is not included: the only file on disk is a 265x258 preview of the whole line.
"""
import hashlib
import io
import os
import subprocess
import sys
import zipfile

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import convert_walking_pals_nat as cw

REPO = cw.REPO
ASSETS = REPO / "app/src/main/assets"
SET = "walkingpals-ow"
OUT = ASSETS / SET
SRC = REPO / "tools/trainer-data/sources/ow"
HNS = "/root/hns/pokehns-expansion/graphics/pokemon"
MEGA_ZIP = r"C:\Users\bepor\Downloads\MEGA sprites-20261009T030126Z-1-001.zip"
ZA_ZIP = r"C:\Users\bepor\Downloads\ZA Megas-20261009T030102Z-1-001.zip"
POST_WILLOW = "https://eeveeexpo.com/resources/1475/"
POST_JIN = "https://www.deviantart.com/jinguinsoo00"
ART_CLASSIC = "Kidkatt, Larryturbo, Princess-Phoenix. Resized and ported by Willøw"
ART_ZA = "DarkusShadow, BluebirdDxD. Resized and ported by Willøw"
TERMS_WILLOW = "Credit the artists. Willøw ported the sheets for Essentials (Eevee Expo 1475)."
TERMS_JIN = "Credit JinGuinsoO00."
TERMS_HNS = "Heart & Soul follower overworld, from the pokeemerald-expansion graphics in the Heart & Soul source. Credit the sheet's artist."
NOTE = "own overworld sheet"

# natdex id, zip entry name, which zip, artists, terms, post
WILLOW = [
    (1051, "VENUSAUR_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1053, "CHARIZARD_2.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1054, "BLASTOISE_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1055, "BEEDRILL_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1056, "PIDGEOTTO_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1061, "PINSIR_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1062, "GYARADOS_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1064, "MEWTWO_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1066, "AMPHAROS_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1068, "SCIZOR_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1069, "HERACROSS_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1073, "BLAZIKEN_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1074, "SWAMPERT_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1078, "AGGRON_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1087, "SALAMENCE_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1088, "METAGROSS_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1092, "GARCHOMP_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1094, "ABOMASNOW_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1096, "AUDINO_1.png", "mega", ART_CLASSIC, TERMS_WILLOW, POST_WILLOW),
    (1238, "CLEFABLE_1.png", "za", ART_ZA, TERMS_WILLOW, POST_WILLOW),
    (1239, "VICTREEBEL_1.png", "za", ART_ZA, TERMS_WILLOW, POST_WILLOW),
    (1240, "STARMIE_1.png", "za", ART_ZA, TERMS_WILLOW, POST_WILLOW),
    (1241, "DRAGONITE_1.png", "za", ART_ZA, TERMS_WILLOW, POST_WILLOW),
    (1264, "RAICHU_3.png", "za", ART_ZA, TERMS_WILLOW, POST_WILLOW),
    (1265, "RAICHU_4.png", "za", ART_ZA, TERMS_WILLOW, POST_WILLOW),
    (1270, "LUCARIO_2.png", "za", ART_ZA, TERMS_WILLOW, POST_WILLOW),
]

# natdex id, path under graphics/pokemon, artists
HNS_ROWS = [
    (693, "pyroar/overworld.png", "Heart & Soul source, pyroar/overworld.png (the male sheet)"),
    (1137, "stunfisk/galar/overworld.png", "Heart & Soul source"),
    (1186, "tornadus/therian/overworld.png", "Heart & Soul source"),
    (1187, "thundurus/therian/overworld.png", "Heart & Soul source"),
    (1192, "greninja/ash/overworld.png", "Heart & Soul source"),
    (1199, "gourgeist/small/overworld.png", "Heart & Soul source"),
    (1200, "gourgeist/large/overworld.png", "Heart & Soul source"),
    (1207, "oricorio/sensu/overworld.png", "Heart & Soul source"),
    (1212, "necrozma/dusk_mane/overworld.png", "Heart & Soul source"),
    (1213, "necrozma/dawn_wings/overworld.png", "Heart & Soul source"),
    (1223, "calyrex/ice/overworld.png", "Heart & Soul source"),
    (1224, "calyrex/shadow/overworld.png", "Heart & Soul source"),
    (1228, "oinkologne/f/overworld.png", "Heart & Soul source"),
    (1237, "squawkabilly/white/overworld.png", "Heart & Soul source"),
]


def chat_png(fragment):
    root = r"C:\Users\bepor\.cursor\projects\c-Users-bepor\assets"
    hits = [n for n in os.listdir(root) if fragment in n.lower()]
    if len(hits) != 1:
        sys.exit("chat image %r matched %s" % (fragment, hits))
    return os.path.join(root, hits[0])


def png_bytes(path):
    data = open(path, "rb").read()
    return data


def knock(a):
    """Flat background becomes clear. A corner that is already clear is left, and soft edges become solid."""
    if int(a[0, 0, 3]) == 255:
        bg = a[0, 0].copy()
        a[np.all(a == bg, axis=-1)] = 0
    a[..., 3] = np.where(a[..., 3] >= 128, 255, 0).astype(np.uint8)
    a[a[..., 3] == 0] = 0
    return a


def load_rgba(data):
    im = Image.open(io.BytesIO(data)).convert("RGBA")
    return knock(np.array(im))


def to_png(a):
    buf = io.BytesIO()
    Image.fromarray(a, "RGBA").save(buf, "png")
    return buf.getvalue()


def square_sheets(data, name):
    return cw.expo_sheets(to_png(load_rgba(data)), name)


def strip_sheets(data, name):
    """One row of square frames (the Heart & Soul follower strip) copied into the eight facings."""
    a = load_rgba(data)
    h, w = a.shape[:2]
    if h not in (32, 64) or w % h:
        sys.exit("%s: %d x %d is not a row of square frames" % (name, w, h))
    f = h
    n = w // f
    frames = [a[:, i * f:(i + 1) * f] for i in range(n)]
    if any(not fr[..., 3].any() for fr in frames):
        sys.exit("%s: an empty frame" % name)
    idle_i = [0, 2 if n > 2 else 0]
    walk_i = [1, 2, 3, 0] if n > 3 else [i % n for i in range(4)]
    out = {}
    for anim, cols, length in (("idle", idle_i, 32), ("walk", walk_i, 8)):
        row = np.hstack([frames[i] for i in cols])
        sheet = np.vstack([row] * 8)
        out[anim] = (sheet, [length] * len(cols))
    x, y = cw.place("idle", out["idle"][0], f, f, len(idle_i))
    return f, x, y, out


def write_one(key, f, x, y, sheets, table):
    for anim, (sheet, durs) in sheets.items():
        png = cw.encode_png(sheet)
        if not cw.same_pixels(sheet, np.array(Image.open(io.BytesIO(png)).convert("RGBA"))):
            sys.exit("re-encoding changed %s %s" % (key, anim))
        folder = OUT / anim
        folder.mkdir(parents=True, exist_ok=True)
        (folder / (key + ".png")).write_bytes(png)
        table.append([key, anim, f, f, x, y, ",".join(map(str, durs))])


def map_rows():
    path = ASSETS / "walkingpals-nat/natdex-map.tsv"
    lines = path.read_text(encoding="utf-8").splitlines()
    rows = {}
    order = []
    header = []
    for line in lines:
        if line.startswith("#") or not line.strip():
            header.append(line)
            continue
        c = line.split("\t")
        rows[int(c[0])] = c
        order.append(int(c[0]))
    return path, header, rows, order


def main():
    SRC.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        for anim in ("idle", "walk", "sleep", "faint"):
            d = OUT / anim
            if d.is_dir():
                for p in d.glob("*.png"):
                    p.unlink()
    path, header, rows, order = map_rows()
    table, credits = [], []
    mega = zipfile.ZipFile(MEGA_ZIP)
    za = zipfile.ZipFile(ZA_ZIP)

    def take(natdex, data, name, artists, terms, post, key_override=None, form_override=None):
        c = rows[natdex]
        national, form = c[2], c[3]
        if key_override is None:
            key = national + ("-" + form if form else "")
        else:
            key = key_override
        if form_override is not None:
            c[3] = form_override
        h, w = load_rgba(data).shape[:2]
        if h == w and h % 4 == 0:
            f, x, y, sheets = square_sheets(data, name)
            kind = "square"
        else:
            f, x, y, sheets = strip_sheets(data, name)
            kind = "strip"
        digest = hashlib.sha256(data).hexdigest()
        stored = SRC / (key + ".png")
        stored.write_bytes(data)
        write_one(key, f, x, y, sheets, table)
        c[4] = SET
        c[5] = key
        c[6] = NOTE
        credits.append([key, c[1], post, artists, terms])
        print("%s %s %s frame %d  %s" % (natdex, c[1], kind, f, key))
        return digest

    for natdex, entry, which, artists, terms, post in WILLOW:
        z = mega if which == "mega" else za
        prefix = "MEGA sprites/OW Mega Sprites/" if which == "mega" else "ZA Megas/"
        data = z.read(prefix + entry)
        take(natdex, data, entry, artists, terms, post)

    for natdex, rel, artists in HNS_ROWS:
        dest = SRC / ("hns-" + rel.replace("/", "-"))
        wsl_dest = "/mnt/c/" + str(dest).replace("\\", "/").split(":", 1)[1].lstrip("/")
        subprocess.check_call(["wsl", "-e", "cp", "-f", HNS + "/" + rel, wsl_dest])
        data = dest.read_bytes()
        override = ("668", "") if natdex == 693 else (None, None)
        take(natdex, data, rel, artists, TERMS_HNS, "the Heart & Soul source, graphics/pokemon/" + rel,
             key_override=override[0], form_override=override[1])

    for natdex, fragment, title in (
        (1243, "dmbjk0p", "Feraligatr-M"),
        (1269, "dmbjj24", "Garchomp-Z"),
    ):
        data = png_bytes(chat_png(fragment))
        take(natdex, data, title, "JinGuinsoO00", TERMS_JIN, POST_JIN)

    # Pyroar's row was the female stand-in. The male sheet is the species sheet now.
    text = []
    for line in header:
        text.append(line)
    # Mention the new set once, on the set-list comment if it is not there yet.
    body = []
    for i in order:
        body.append("\t".join(rows[i]))
    path.write_text("\n".join(text + body) + "\n", encoding="utf-8", newline="\n")

    cw.write_tsv(OUT / (SET + ".tsv"), ["key", "animation", "w", "h", "x", "y", "durations"], [
        "Walking Pals for forms that were still using another Pokemon's sheet (2026-10-08).",
        "Willøw's Eevee Expo 1475 mega overworlds, JinGuinsoO00's Feraligatr-M and Garchomp-Z,",
        "and the Heart & Soul follower strips. Keyed as walkingpals-nat is.",
        "A 4x4 sheet is cut like the other follower sets (eight facings, diagonals use the side).",
        "A Heart & Soul strip is one facing, copied into each of the eight rows. Idle two frames, walk four.",
        "No sleep or faint: the app shows idle. Credit the artists (credits.tsv).",
        "x,y: offset from a 32x32 icon's corner; durations in game frames (60 a second).",
    ], table)
    cw.write_tsv(OUT / "credits.tsv", ["key", "sprite", "post", "artists", "terms"], [
        "Who drew each sheet in this folder. The post is the pack page, the artist gallery, or the Heart & Soul source path.",
    ], credits)
    cw.write_tsv(OUT / "shiny-colors.tsv", ["key", "animations", "colors"], [
        "No shinies ship in this folder. A shiny walks in plain colors.",
    ], [])
    cw.write_tsv(OUT / "shiny.tsv", ["key", "animation", "w", "h", "x", "y", "durations"], [
        "No shinies ship in this folder.",
    ], [])
    print("sheets", len(table), "keys", len(credits))


if __name__ == "__main__":
    main()
