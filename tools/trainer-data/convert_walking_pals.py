"""Ironmon-Tracker's Walking Pals icon set, by RUNNING its Lua (data/SpriteData.lua), and its shinies from PMD Sprite Collab.

    python tools/trainer-data/convert_walking_pals.py <Ironmon-Tracker/ironmon_tracker> <app assets root>
                                                      [--cache DIR] [--offline]

Writes into walkingpals/:

    walkingpals.tsv     SpriteData.WalkingPals: one row per species and animation - the Gen 3
                        pokemonID, the animation (idle, walk, sleep, faint), the frame's width and
                        height, the x/y offset the tracker draws it at, and each frame's duration
                        in game frames (60 a second).
    idle|walk|sleep|faint/<id>.png
                        The sprite sheets, copied as shipped: frames left to right, eight facing
                        directions top to bottom (Input.getSpriteFacingDirection's order).
    shiny-colors.tsv, shiny.tsv, shiny/<anim>/<id>.png, credits.tsv
                        The shinies (Blake, 2026-10-03: "If they are shiny you should be able to play
                        as shiny"). Ironmon-Tracker ships none, so they come from PMD Sprite Collab,
                        the collab its set was cut from, through convert_walking_pals_nat.py's cache
                        and its proof (THE SHINIES there): a shiny that is an exact recolor of
                        Ironmon-Tracker's own sheet ships as its color map, any other as a sheet of its
                        own, cut as Ironmon-Tracker cut the plain one and placed at the plain row (the
                        hand-placed offsets), and only when its frames are the plain sheet's. credits.tsv
                        names each shiny's own artists; the plain sheets are credited as NOTICE says.

It also checks what makes the recolor carry over: each of Ironmon-Tracker's sheets against the
same animation of Sprite Collab's plain sheet, cut to its size. They are pixel-identical cuts
for all but a few (the count is printed); a shiny is still tested against Ironmon-Tracker's own
pixels, never Sprite Collab's, so a sheet redrawn since gets a color map only if it still holds.

Globals the file touches but that are not loaded here resolve to stand-ins, as in
convert_nds_log_tables.py.
"""
import argparse, collections, os, pathlib, shutil, sys

import numpy as np
from lupa import LuaRuntime
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import convert_walking_pals_nat as cw  # noqa: E402

ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
ap.add_argument("ref", type=pathlib.Path, help="Ironmon-Tracker's ironmon_tracker folder")
ap.add_argument("assets", type=pathlib.Path, help="the app's assets folder")
ap.add_argument("--cache", default="C:/Users/bepor/walkingpals-cache", type=pathlib.Path, help="convert_walking_pals_nat.py's cache")
ap.add_argument("--offline", action="store_true", help="use only what the cache already holds")
args = ap.parse_args()
ref = args.ref; out = args.assets / "walkingpals"
out.mkdir(parents=True, exist_ok=True)
lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute(
    "STANDIN = {}\n"
    "local function proxy() return setmetatable({}, STANDIN) end\n"
    "STANDIN.__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end\n"
    "STANDIN.__call = function(self, a, ...) if a ~= nil then return a end return proxy() end\n"
    "setmetatable(_G, {__index = function(t, k) local v = proxy(); rawset(t, k, v); return v end})\n")
lua.execute((ref / "data" / "SpriteData.lua").read_text(encoding="utf-8"))
wp = lua.globals().SpriteData.WalkingPals
tab, nl = chr(9), chr(10)
rows = 0
table = collections.defaultdict(dict)
with open(out / "walkingpals.tsv", "w", encoding="utf-8", newline=nl) as f:
    f.write(tab.join(["# id", "animation", "w", "h", "x", "y", "durations"]) + nl)
    for sid in sorted(k for k in wp.keys() if isinstance(k, int)):
        for anim in ("idle", "walk", "sleep", "faint"):
            a = wp[sid][anim]
            if a is None:
                continue
            durs = ",".join(str(int(a["durations"][i])) for i in range(1, len(a["durations"]) + 1))
            f.write(tab.join([str(sid), anim] + [str(int(a[k] or 0)) for k in ("w", "h", "x", "y")] + [durs]) + nl)
            table[sid][anim] = dict(w=int(a["w"] or 0), h=int(a["h"] or 0), x=int(a["x"] or 0), y=int(a["y"] or 0),
                                    durs=[int(v) for v in durs.split(",")])
            rows += 1
print("walkingpals rows", rows)
n = 0
for anim in ("idle", "walk", "sleep", "faint"):
    src = ref / "images" / "spritesWalkingPals" / anim
    dst = out / anim
    dst.mkdir(exist_ok=True)
    for p in src.glob("*.png"):
        shutil.copyfile(p, dst / p.name); n += 1
print("sheets", n)

# ---------------------------------------------------------------- the shinies

col = cw.Collab(args.cache, update=False, offline=args.offline)
names = cw.natdex_names()
by_name = {cw.norm(v["name"]): int(k) for k, v in col.tracker.items()}
national = {}
for sid in table:
    nat = sid if sid <= 251 else by_name.get(cw.norm(names.get(sid, "")))
    if nat:  # Gen 3's unused slots 252-276 name no Pokemon
        national[sid] = nat
folders = {sid: "sprite/%04d" % nat for sid, nat in national.items()}
col.want(f + "/AnimData.xml" for f in folders.values() if col.has(f))
plain_sheets = set()
for f in folders.values():
    if col.has(f):
        ad = cw.anim_data(col.dir / f / "AnimData.xml")
        plain_sheets |= {"%s/%s-Anim.png" % (f, ad[cw.PMD[a]][0]) for a in cw.ANIMS if ad.get(cw.PMD[a])}
col.want(p for p in plain_sheets if p in col.files)
cw.want_shinies(col, list(folders.values()))
cuts = collections.Counter()
shinies = cw.Shinies(col, hand_placed=True)
for sid in sorted(national):
    folder = folders[sid]
    plains = {a: np.array(Image.open(out / a / ("%d.png" % sid)).convert("RGBA")) for a in table[sid]}
    ad = cw.anim_data(col.dir / folder / "AnimData.xml") if col.has(folder) else {}
    for anim, plain in plains.items():
        src = ad.get(cw.PMD[anim])
        p = col.dir / folder / (src[0] + "-Anim.png") if src else None
        sc = np.array(Image.open(p).convert("RGBA")) if p and p.exists() else None
        h, w = plain.shape[:2]
        cuts["a pixel-identical cut of Sprite Collab's plain sheet" if sc is not None and sc.shape[0] >= h and sc.shape[1] >= w
             and cw.same_pixels(sc[:h, :w], plain) else "not a cut of Sprite Collab's plain sheet now"] += 1

    def cut(anim, sheet, w, h, durs, plains=plains):
        """A Sprite Collab sheet cut to the size of Ironmon-Tracker's sheet of that animation."""
        ph, pw = plains[anim].shape[:2]
        return sheet[:ph, :pw] if sheet.shape[0] >= ph and sheet.shape[1] >= pw else None

    shinies.add(str(sid), national[sid], col.tracker["%04d" % national[sid]]["name"], folder, table[sid], plains, cut)
src = "PMD Sprite Collab %s (%s), github.com/PMDCollab/SpriteCollab" % (col.commit[:12], col.date)
shinies.write(out, src, "Gen 1-3 Walking Pals in this folder")
cw.write_tsv(out / "credits.tsv", ["key", "sprite", "SpriteCollab folder", "artists", "licenses"], [
    "Who drew each shiny in this folder (shiny-colors.tsv, shiny.tsv): " + src + ".",
    "The plain sheets are Ironmon-Tracker's own copies of the collab's, credited as NOTICE says. Artists: the current",
    "credits.txt entries for the animations used, then tracker.json's credit for the folder, by the names in",
    "credit_names.txt (a Discord id where none is registered). Licenses as each contribution was given: CC_BY-NC_4 =",
    "CC BY-NC 4.0; PMDCollab_1 = use with credit; PMDCollab_2 = use with credit, not for profit; Unspecified = Spike",
    "Chunsoft's own sprites from the PMD games. The collab's terms for the whole repository: CC BY-NC 4.0.",
], shinies.credits)
print("Ironmon-Tracker's sheets against Sprite Collab's plain ones:", ", ".join("%d %s" % (v, k) for k, v in sorted(cuts.items())))
shinies.report("walkingpals")
