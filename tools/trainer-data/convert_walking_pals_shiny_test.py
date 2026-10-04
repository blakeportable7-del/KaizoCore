#!/usr/bin/env python3
"""The shiny Walking Pals (Blake, 2026-10-03: "If they are shiny you should be able to play as shiny"): the converters'
proof that a shiny is an exact recolor of its plain sheet, checked three ways.

    python tools/trainer-data/convert_walking_pals_shiny_test.py [--quick]

1. The proof itself (recolor, apply_colors, color_rows, shiny_folder in convert_walking_pals_nat.py) on sheets built
   here: a recolor is found and reproduces the shiny; a color split in two, other pixels clear, a half-clear pixel and
   other frames are each refused.
2. Every shiny as shipped, against Sprite Collab's own sheet in the converter's cache, read here without the converter:
   each color map applied to the shipped plain sheet IS the shiny pixel for pixel, and each sheet of its own IS the
   shiny, cut as the plain one is, and is NOT an exact recolor (else it would have shipped as a map).
3. Both converters run offline into a scratch folder write every shipped Walking Pals file again byte for byte
   (line endings aside, which git keeps as LF). Skipped with --quick; takes about four minutes.

Parts 2 and 3 need the cache (convert_walking_pals_nat.py's --cache default) and part 3 Ironmon-Tracker's checkout;
each is skipped, saying so, where it is not on this machine.
"""
import os
import pathlib
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image

HERE = pathlib.Path(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, str(HERE))
import convert_walking_pals_nat as cw  # noqa: E402

REPO = HERE.parents[1]
ASSETS = REPO / "app/src/main/assets"
CACHE = pathlib.Path("C:/Users/bepor/walkingpals-cache")
IRONMON_TRACKER = pathlib.Path("C:/Users/bepor/ironmon-ref/Ironmon-Tracker/ironmon_tracker")


def expect(failures, name, ok):
    if not ok:
        failures.append(name)


# ---------------------------------------------------------------- 1. the proof on sheets built here

def sheet(colors, alpha=None):
    """A 2x3 RGBA sheet from RRGGBB numbers, None for a clear pixel."""
    a = np.zeros((2, 3, 4), np.uint8)
    for i, c in enumerate(colors):
        y, x = divmod(i, 3)
        if c is not None:
            a[y, x] = [(c >> 16) & 255, (c >> 8) & 255, c & 255, 255 if alpha is None else alpha]
    return a


def proof(failures):
    plain = sheet([0x000000, 0x6fb46a, 0x6fb46a, None, 0xffffff, 0x000000])
    shiny = sheet([0x000000, 0x9cc390, 0x9cc390, None, 0xf0f0f0, 0x000000])
    m, why = cw.recolor(plain, shiny)
    expect(failures, "an exact recolor is found: %r %r" % (m, why), m == {0x000000: 0x000000, 0x6fb46a: 0x9cc390, 0xffffff: 0xf0f0f0})
    expect(failures, "and its map makes the shiny", cw.same_pixels(cw.apply_colors(plain, m), shiny))
    split = sheet([0x000000, 0x9cc390, 0x7fa95c, None, 0xf0f0f0, 0x000000])
    expect(failures, "one plain color split in two is not a recolor", cw.recolor(plain, split) == (None, "a color split in two"))
    holes = sheet([0x000000, 0x9cc390, 0x9cc390, 0x123456, 0xf0f0f0, 0x000000])
    expect(failures, "other pixels clear is not a recolor", cw.recolor(plain, holes) == (None, "other pixels clear"))
    soft = sheet([0x000000, 0x9cc390, 0x9cc390, None, 0xf0f0f0, 0x000000], alpha=128)
    expect(failures, "a half-clear pixel is not a recolor", cw.recolor(plain, soft) == (None, "partly clear pixels"))
    expect(failures, "other frames are not a recolor", cw.recolor(plain, np.zeros((2, 4, 4), np.uint8)) == (None, "frames"))
    many = sheet([0x000000, 0x6fb46a, 0x7fa95c, None, 0xffffff, 0x000000])
    to_one = sheet([0x000000, 0x9cc390, 0x9cc390, None, 0xffffff, 0x000000])
    m2, _ = cw.recolor(many, to_one)
    expect(failures, "two plain colors may become one, and still make the shiny", m2 is not None and cw.same_pixels(cw.apply_colors(many, m2), to_one))
    rows = cw.color_rows("25", {"idle": {1: 2, 3: 4}, "walk": {1: 2, 5: 6}, "sleep": {1: 9}})
    expect(failures, "maps that agree share a row, one that does not has its own: %r" % rows, rows == [
        ["25", "idle,walk", "000001=000002 000003=000004 000005=000006"], ["25", "sleep", "000001=000009"]])
    for folder, want in [("sprite/0025", "sprite/0025/0000/0001"), ("sprite/0006/0001", "sprite/0006/0001/0001"),
                         ("sprite/0668/0000/0000/0002", "sprite/0668/0000/0001/0002"), ("sprite/0201/0026", "sprite/0201/0026/0001")]:
        expect(failures, "the shiny of %s is %s" % (folder, want), cw.shiny_folder(folder) == want)


# ---------------------------------------------------------------- 2. every shipped shiny against Sprite Collab's

def table(path, colors=False):
    out = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or not line.strip():
            continue
        c = line.split("\t")
        if colors:
            m = {int(a, 16): int(b, 16) for a, b in (w.split("=") for w in c[2].split())}
            for anim in c[1].split(","):
                out[(c[0], anim)] = m
        else:
            out[(c[0], c[1])] = (int(c[2]), int(c[3]), [int(v) for v in c[6].split(",")])
    return out


def rgba(p):
    return np.array(Image.open(p).convert("RGBA"))


def shipped(failures):
    if not (CACHE / "SpriteCollab" / ".git").is_dir():
        print("no SpriteCollab cache at %s: the check against it is skipped" % CACHE)
        return
    col = cw.Collab(CACHE, update=False, offline=True)
    names = cw.natdex_names()
    by_name = {cw.norm(v["name"]): int(k) for k, v in col.tracker.items()}
    folders = {"walkingpals-nat": {}, "walkingpals": {}}
    for line in (ASSETS / "walkingpals-nat/credits.tsv").read_text(encoding="utf-8").splitlines():
        c = line.split("\t")
        if not line.startswith("#") and not c[1].endswith(" shiny"):
            folders["walkingpals-nat"][c[0]] = c[2]
    for line in (ASSETS / "walkingpals/walkingpals.tsv").read_text(encoding="utf-8").splitlines():
        if not line.startswith("#"):
            i = int(line.split("\t")[0])
            nat = i if i <= 251 else by_name.get(cw.norm(names.get(i, "")))
            if nat:
                folders["walkingpals"][str(i)] = "sprite/%04d" % nat
    counts = {}
    for pack in folders:
        plain = table(ASSETS / pack / (pack + ".tsv"))
        maps = table(ASSETS / pack / "shiny-colors.tsv", colors=True)
        own = table(ASSETS / pack / "shiny.tsv")

        def sc_shiny(key, anim):
            """Sprite Collab's shiny sheet of [anim], cut as the shipped plain one is cut."""
            s = cw.shiny_folder(folders[pack][key])
            src = cw.anim_data(col.dir / s / "AnimData.xml")[cw.PMD[anim]]
            whole = rgba(col.dir / s / (src[0] + "-Anim.png"))
            if pack == "walkingpals":  # Ironmon-Tracker's sheet's own size
                h, w = rgba(ASSETS / pack / anim / (key + ".png")).shape[:2]
                return whole[:h, :w]
            return cw.nat_cut(anim, whole, src[1], src[2], src[3])

        for (key, anim), m in maps.items():
            p = rgba(ASSETS / pack / anim / (key + ".png"))
            expect(failures, "%s %s %s: the map makes Sprite Collab's shiny" % (pack, key, anim), cw.same_pixels(cw.apply_colors(p, m), sc_shiny(key, anim)))
            expect(failures, "%s %s %s: a map for a plain sheet that ships" % (pack, key, anim), (key, anim) in plain)
        for (key, anim) in own:
            s = rgba(ASSETS / pack / "shiny" / anim / (key + ".png"))
            expect(failures, "%s %s %s: the sheet is Sprite Collab's shiny" % (pack, key, anim), cw.same_pixels(s, sc_shiny(key, anim)))
            p = rgba(ASSETS / pack / anim / (key + ".png"))
            same = plain[(key, anim)] == own[(key, anim)]
            expect(failures, "%s %s %s: a sheet of its own that a map could have said" % (pack, key, anim),
                   not same or cw.recolor(p, s)[0] is None)
        expect(failures, "%s: no animation is both a map and a sheet" % pack, not set(maps) & set(own))
        counts[pack] = (len(maps), len(own))
    print("checked against Sprite Collab:", ", ".join("%s %d maps and %d sheets" % (k, *v) for k, v in counts.items()))


# ---------------------------------------------------------------- 3. the converters, offline, write the same files

def same_files(failures, made, pack):
    a, b = made / pack, ASSETS / pack
    fa = {p.relative_to(a).as_posix() for p in a.rglob("*") if p.is_file()}
    fb = {p.relative_to(b).as_posix() for p in b.rglob("*") if p.is_file()}
    expect(failures, "%s: files only the rerun wrote: %s" % (pack, sorted(fa - fb)[:5]), not fa - fb)
    expect(failures, "%s: shipped files the rerun did not write: %s" % (pack, sorted(fb - fa)[:5]), not fb - fa)
    def shipped_bytes(f):  # a table as git keeps it (LF); a picture as it is (a PNG's own signature holds CR LF)
        raw = (b / f).read_bytes()
        return raw.replace(b"\r\n", b"\n") if f.endswith(".tsv") else raw
    differ = [f for f in sorted(fa & fb) if (a / f).read_bytes() != shipped_bytes(f)]
    expect(failures, "%s: files that differ: %s" % (pack, differ[:5]), not differ)
    print("%s: %d files written again, %d differ" % (pack, len(fa & fb), len(differ)))


def rerun(failures):
    if not (CACHE / "SpriteCollab" / ".git").is_dir() or not IRONMON_TRACKER.is_dir():
        print("no cache or no Ironmon-Tracker checkout: the offline rerun is skipped")
        return
    with tempfile.TemporaryDirectory() as tmp:
        made = pathlib.Path(tmp)
        for args in ([str(HERE / "convert_walking_pals_nat.py"), "--offline", "--assets", tmp],
                     [str(HERE / "convert_walking_pals.py"), str(IRONMON_TRACKER), tmp, "--offline"]):
            r = subprocess.run([sys.executable] + args, capture_output=True, text=True, encoding="utf-8")
            if r.returncode:
                failures.append("%s failed:\n%s" % (args[0], r.stderr[-2000:]))
                return
        for pack in ("walkingpals-nat", "walkingpals", "walkingpals-darkus"):
            same_files(failures, made, pack)


def main():
    failures = []
    proof(failures)
    shipped(failures)
    if "--quick" not in sys.argv:
        rerun(failures)
    for f in failures[:40]:
        print("FAIL", f)
    print("%d failed" % len(failures) if failures else "all passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
