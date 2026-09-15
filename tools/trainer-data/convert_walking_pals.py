"""Ironmon-Tracker's Walking Pals icon set, by RUNNING its Lua (data/SpriteData.lua).

    python tools/trainer-data/convert_walking_pals.py <Ironmon-Tracker/ironmon_tracker> <app assets root>

Writes into walkingpals/:

    walkingpals.tsv     SpriteData.WalkingPals: one row per species and animation - the Gen 3
                        pokemonID, the animation (idle, walk, sleep, faint), the frame's width and
                        height, the x/y offset the tracker draws it at, and each frame's duration
                        in game frames (60 a second).
    idle|walk|sleep|faint/<id>.png
                        The sprite sheets, copied as shipped: frames left to right, eight facing
                        directions top to bottom (Input.getSpriteFacingDirection's order).

Globals the file touches but that are not loaded here resolve to stand-ins, as in
convert_nds_log_tables.py.
"""
import os, sys, shutil, pathlib
from lupa import LuaRuntime

ref = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2]) / "walkingpals"
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
with open(out / "walkingpals.tsv", "w", encoding="utf-8", newline=nl) as f:
    f.write(tab.join(["# id", "animation", "w", "h", "x", "y", "durations"]) + nl)
    for sid in sorted(k for k in wp.keys() if isinstance(k, int)):
        for anim in ("idle", "walk", "sleep", "faint"):
            a = wp[sid][anim]
            if a is None:
                continue
            durs = ",".join(str(int(a["durations"][i])) for i in range(1, len(a["durations"]) + 1))
            f.write(tab.join([str(sid), anim] + [str(int(a[k] or 0)) for k in ("w", "h", "x", "y")] + [durs]) + nl)
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
