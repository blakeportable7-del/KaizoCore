#!/usr/bin/env python3
"""convert_walking_pals_nat.py's DARKUSSHADOW: DarkusShadow's overworld sprites fill the gaps Sprite Collab leaves
(Blake, 2026-10-04: "use the ones i gave you if they fill in sprite collabs gap").

    python tools/trainer-data/convert_walking_pals_darkus_test.py

A made-up sheet checks the cut (his pixels, eight facings, the walk from its first step, the idle's two poses, the
anchor); the shipped sources check that each is the file fetched and cuts cleanly; rows built here check the gap rule
(Sprite Collab's own sheet wins); then end to end on the SpriteCollab cache when it is on this machine: Iron Boulder and
Iron Crown take his sheets today, and the run after Sprite Collab draws Iron Boulder takes theirs, with no change here.
"""
import hashlib
import io
import os
import pathlib
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import convert_walking_pals_nat as cw  # noqa: E402

CACHE = pathlib.Path("C:/Users/bepor/walkingpals-cache")


def png(a):
    b = io.BytesIO()
    Image.fromarray(a).save(b, "PNG")
    return b.getvalue()


def made_up(f=32):
    """His format at twice its pixels: frame (row r, column c) a block of color (r, c), a pixel higher on the lifted
    frames 1 and 3."""
    n = np.zeros((4 * f, 4 * f, 4), np.uint8)
    for r in range(4):
        for c in range(4):
            lift = c % 2
            n[r * f + 8 - lift:r * f + f - 2 - lift, c * f + 8:c * f + 24] = (40 * r + 10, 40 * c + 10, 7, 255)
    return n, n.repeat(2, 0).repeat(2, 1)


def frame_color(sheet, f, row, col):
    px = sheet[row * f:(row + 1) * f, col * f:(col + 1) * f].reshape(-1, 4)
    px = px[px[:, 3] == 255]
    return (int(px[0][0]) - 10) // 40, (int(px[0][1]) - 10) // 40


def cut(failures):
    def expect(name, ok):
        if not ok:
            failures.append(name)

    native, doubled = made_up()
    f, x, y, sheets = cw.darkus_sheets(png(doubled), "made-up")
    expect("the frame is his own pixels: 32, not the posted 64 (got %d)" % f, f == 32)
    idle, idurs = sheets["idle"]
    walk, wdurs = sheets["walk"]
    expect("idle: 2 frames by 8 facings, 32 frames each (got %s %s)" % (idle.shape, idurs),
           idle.shape[:2] == (8 * 32, 2 * 32) and idurs == [32, 32])
    expect("walk: 4 frames by 8 facings, 8 frames each (got %s %s)" % (walk.shape, wdurs),
           walk.shape[:2] == (8 * 32, 4 * 32) and wdurs == [8, 8, 8, 8])
    # Facings: down, down-right, right, up-right, up, up-left, left, down-left from his down, left, right, up.
    expect("each facing is his row for it, a diagonal the side view",
           [frame_color(walk, 32, r, 0)[0] for r in range(8)] == [0, 2, 2, 2, 3, 1, 1, 1])
    expect("the walk starts on a step: his frames 1, 2, 3, 0",
           [frame_color(walk, 32, 0, c)[1] for c in range(4)] == [1, 2, 3, 0])
    expect("the idle is his grounded poses 0 and 2", [frame_color(idle, 32, 2, c)[1] for c in range(2)] == [0, 2])
    expect("pixels are his, not resampled", (idle[:32, :32] == native[:32, :32]).all())
    # THE OFFSET RULE on the standing pose: its box (8..24 across, 8..30 down) centred at (16, 20).
    expect("anchor: the standing pose's box centred at (16, 20), got (%d, %d)" % (x, y), (x, y) == (0, 1))
    # Anything that is not an exact doubling is refused, never resampled.
    bad = doubled.copy()
    bad[17, 17] = (1, 2, 3, 255)
    try:
        cw.darkus_sheets(png(bad), "a block of two colors")
        failures.append("a sheet that is not an exact doubling was cut")
    except SystemExit:
        pass


def shipped(failures):
    sources = cw.darkus_sources()
    if len(sources) < 19:
        failures.append("only %d sources" % len(sources))
    for s in sources:
        data = (cw.DARKUS_DIR / s.file).read_bytes()
        if hashlib.sha256(data).hexdigest() != s.sha256:
            failures.append("%s is not the file fetched" % s.file)
            continue
        f, _, _, _ = cw.darkus_sheets(data, s.file)
        if f not in (32, 64):
            failures.append("%s: frame %d" % (s.file, f))
        if not s.post.startswith("https://www.deviantart.com/darkusshadow/art/") or "DarkusShadow" not in s.artists:
            failures.append("%s: post %s, artists %s" % (s.file, s.post, s.artists))
    boulder = next(s for s in sources if s.natdex == 1047)
    f, x, y, sheets = cw.darkus_sheets((cw.DARKUS_DIR / boulder.file).read_bytes(), boulder.file)
    if (f, x, y) != (32, 1, 2) or sheets["walk"][0].shape[:2] != (256, 128):
        failures.append("Iron Boulder: frame %d at (%d, %d), walk %s" % (f, x, y, sheets["walk"][0].shape))


def gaps(failures):
    names = {1047: "Iron Boulder", 1030: "Roaring Moon", 1257: "Malamar-M"}

    def src(natdex, name, key):
        return cw.DarkusSource(["x.png", str(natdex), name, key, "t", "u", "", "", "", "DarkusShadow", ""])

    rows = {
        1047: (1022, "", "", "", "no sheet: SpriteCollab has no Iron Boulder sprite yet"),
        1030: (1005, "", "walkingpals-nat", "1005", ""),
        1257: (687, "mega", "", "", "no sheet: SpriteCollab's Malamar Mega slot is empty"),
    }
    out, unneeded = cw.fill_gaps(dict(rows), names, [
        src(1047, "Iron Boulder", "1022"), src(1030, "Roaring Moon", "1005"), src(1257, "Malamar-M", "687-mega")])
    if out[1047][2:4] != (cw.DARKUS_SET, "1022"):
        failures.append("a species nobody drew takes his sheet: %r" % (out[1047],))
    if out[1030] != rows[1030] or [u[0].natdex for u in unneeded] != [1030]:
        failures.append("Sprite Collab's own sheet wins: %r, unneeded %r" % (out[1030], unneeded))
    if out[1257][2:4] != (cw.DARKUS_SET, "687-mega"):
        failures.append("an empty Mega slot takes his Mega: %r" % (out[1257],))
    for bad in (src(1047, "Iron Crown", "1022"), src(1047, "Iron Boulder", "1023"), src(1257, "Malamar-M", "687")):
        try:
            cw.fill_gaps(dict(rows), names, [bad])
            failures.append("a source for the wrong Pokemon was taken: %s %s" % (bad.name, bad.key))
        except SystemExit:
            pass


class DrawnLater(cw.Collab):
    def __init__(self, cache, drawn):
        super().__init__(cache, update=False, offline=True)
        self.drawn = drawn

    def has(self, folder, name="AnimData.xml"):
        return folder == self.drawn or super().has(folder, name)


def end_to_end(failures):
    if not (CACHE / "SpriteCollab" / ".git").is_dir():
        print("no SpriteCollab cache at %s: the end-to-end half is skipped" % CACHE)
        return
    names = cw.natdex_names()
    unneeded = []
    _, today = cw.plan(cw.Collab(CACHE, update=False, offline=True), names, skipped=unneeded)
    for i, key in ((1047, "1022"), (1048, "1023"), (1027, "1002"), (1257, "687-mega")):
        if today[i][2:4] != (cw.DARKUS_SET, key):
            failures.append("%d takes his sheet today: %r" % (i, today[i]))
    if today[1237][2:4] != (cw.DARKUS_SET, "931") or cw.STANDS_IN not in today[1237][4]:
        failures.append("White Squawkabilly walks as his Squawkabilly: %r" % (today[1237],))
    if today[1030][2:4] != ("walkingpals-nat", "1005"):
        failures.append("Roaring Moon keeps Sprite Collab's: %r" % (today[1030],))
    if unneeded:
        failures.append("sources Sprite Collab already covers: %r" % [u[0].file for u in unneeded])
    later_unneeded = []
    _, later = cw.plan(DrawnLater(CACHE, "sprite/1022"), names, skipped=later_unneeded)
    if later[1047][2:4] != ("walkingpals-nat", "1022"):
        failures.append("once Sprite Collab draws Iron Boulder, theirs wins: %r" % (later[1047],))
    if [u[0].natdex for u in later_unneeded] != [1047]:
        failures.append("and the run says his is no longer needed: %r" % later_unneeded)


def main():
    failures = []
    cut(failures)
    shipped(failures)
    gaps(failures)
    end_to_end(failures)
    for f in failures:
        print("FAIL", f)
    print("%d failed" % len(failures) if failures else "all passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
