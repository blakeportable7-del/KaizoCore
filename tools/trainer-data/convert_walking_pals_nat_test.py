#!/usr/bin/env python3
"""convert_walking_pals_nat.py's stand-ins (Blake, 2026-10-02): a Mega or form with no walking sprite of its own walks
as its base species, "only if we don't have the correct sprites".

    python tools/trainer-data/convert_walking_pals_nat_test.py

The rule is checked on rows built here, then end to end on the SpriteCollab cache when it is on this machine (the
converter's own --cache default; skipped without it): a Mega whose slot is empty takes its base species' sheet, and the
same run with a sheet in that slot takes the Mega's own, with no change to the converter.
"""
import os
import pathlib
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import convert_walking_pals_nat as cw  # noqa: E402

CACHE = pathlib.Path("C:/Users/bepor/walkingpals-cache")


def rules(failures):
    def expect(name, got, want):
        if got != want:
            failures.append("%s: got %r, want %r" % (name, got, want))

    names = {6: "Charizard", 280: "Torchic", 282: "Blaziken", 693: "Pyroar", 895: "Falinks", 683: "Greninja",
             1052: "Charizard-X", 1053: "Charizard-Y", 1073: "Blaziken-M", 1192: "Greninja-A", 1254: "Greninja-M",
             1255: "Pyroar-M", 1263: "Falinks-M"}
    by_name = {"charizard": 6, "torchic": 255, "blaziken": 257, "pyroar": 668, "falinks": 870, "greninja": 658}
    empty = "no sheet: SpriteCollab's %s slot is empty"
    rows = {
        683: (658, "", "walkingpals-nat", "658", ""),                         # Greninja, drawn
        693: (668, "female", "walkingpals-nat", "668-female", "no male Pyroar sheet in SpriteCollab; the female one stands in"),
        895: (870, "", "", "", "no sheet: SpriteCollab has no Falinks sprite yet"),
        1052: (6, "mega-x", "walkingpals-nat", "6-mega-x", ""),              # a form with a sheet of its own
        1053: (6, "mega-y", "", "", empty % "Charizard Mega Y"),
        1073: (257, "mega", "", "", empty % "Blaziken Mega"),
        1192: (658, "ash", "", "", empty % "Greninja Ash"),
        1254: (658, "mega", "", "", empty % "Greninja Mega"),
        1255: (668, "mega", "", "", empty % "Pyroar Mega"),
        1263: (870, "mega", "", "", empty % "Falinks Mega"),
    }
    out = cw.stand_in(dict(rows), names, by_name)
    expect("a form with a sheet of its own keeps it", out[1052], rows[1052])
    expect("a base species keeps what it has", out[683], rows[683])
    expect("a base species with no sheet stays blank: no species is given another's", out[895], rows[895])
    expect("a Gen 1-3 base: walkingpals/, by Gen 3's id", out[1053][2:4], ("walkingpals", "6"))
    expect("a Hoenn base: Gen 3's own order", out[1073][2:4], ("walkingpals", "282"))
    expect("a later base: its own row's sheet", out[1254][2:4], ("walkingpals-nat", "658"))
    expect("the base's own stand-in carries over", out[1255][2:4], ("walkingpals-nat", "668-female"))
    expect("a base with no sheet leaves its Mega blank", out[1263], rows[1263])
    expect("the form column still says what it is", (out[1053][1], out[1192][1]), ("mega-y", "ash"))
    expect("the note says why, and that the base stands in", out[1192][4],
           "SpriteCollab's Greninja Ash slot is empty; Greninja's sheet stands in until its own is drawn")


class DrawnLater(cw.Collab):
    """The cache as it is, plus a sheet in one slot that is empty today: what the next run sees once someone draws it."""

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
    _, today = cw.plan(cw.Collab(CACHE, update=False, offline=True), names)
    venusaur = today[1051]
    if venusaur[2:4] != ("walkingpals", "3") or cw.STANDS_IN not in venusaur[4]:
        failures.append("Venusaur-M today walks as Venusaur, the note saying so: %r" % (venusaur,))
    if today[1052][2:4] != ("walkingpals-nat", "6-mega-x"):
        failures.append("Charizard-X keeps the sheet of its own: %r" % (today[1052],))
    for i, r in today.items():
        if cw.STANDS_IN in r[4] and i <= 1050:
            failures.append("a base species stands in for another: %d %r" % (i, r))
    _, later = cw.plan(DrawnLater(CACHE, "sprite/0003/0001"), names)
    if later[1051][2:4] != ("walkingpals-nat", "3-mega") or cw.STANDS_IN in later[1051][4]:
        failures.append("once its slot is drawn, Venusaur-M takes its own: %r" % (later[1051],))
    changed = [i for i in today if today[i] != later[i] and i != 1051]
    if changed:
        failures.append("drawing one sheet moved other rows: %r" % changed[:5])


def main():
    failures = []
    rules(failures)
    end_to_end(failures)
    for f in failures:
        print("FAIL", f)
    print("%d failed" % len(failures) if failures else "all passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
