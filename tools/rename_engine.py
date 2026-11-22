"""Rename a vendored copy of the Nat. Dex randomizer so it can ship beside another copy in one APK.

    python tools/rename_engine.py engine-natdex122 122

Moves com/dabomstew/pkrandom to com/dabomstew/pkrandom<suffix> and the five top-level sibling packages (compressors,
cuecompressors, launcher, pptxt, thenewpoketext) to nd<suffix><name>, and rewrites every reference, dotted and slashed
(package and import lines, resource paths such as "/com/dabomstew/pkrandom/config/" and the newgui Bundle), in src/ and
test/. The way engine-zx (pkrandomzx) and engine-maxdex (pkrandommd) were renamed. Refuses to run twice.
"""
import os
import re
import shutil
import sys

module, suffix = sys.argv[1], sys.argv[2]
SIBLINGS = ["compressors", "cuecompressors", "launcher", "pptxt", "thenewpoketext"]
src = os.path.join(module, "src")
assert os.path.isdir(os.path.join(src, "com", "dabomstew", "pkrandom")), "already renamed, or not a Nat. Dex engine copy"

new_pkg = "pkrandom" + suffix
moves = [(os.path.join(src, "com", "dabomstew", "pkrandom"), os.path.join(src, "com", "dabomstew", new_pkg))]
moves += [(os.path.join(src, s), os.path.join(src, "nd" + suffix + s)) for s in SIBLINGS if os.path.isdir(os.path.join(src, s))]
for a, b in moves:
    shutil.move(a, b)

subs = [
    (re.compile(r"com\.dabomstew\.pkrandom\b"), "com.dabomstew." + new_pkg),
    (re.compile(r"com/dabomstew/pkrandom/"), "com/dabomstew/" + new_pkg + "/"),
]
for s in SIBLINGS:
    # A package or an import of a sibling: "package compressors;", "import cuecompressors.BLZCoder;", and dotted uses.
    subs.append((re.compile(r"(?<![\w.])" + s + r"(?=[.;])"), "nd" + suffix + s))

changed = 0
for root in (src, os.path.join(module, "test")):
    for d, _, files in os.walk(root):
        for f in files:
            if not f.endswith((".java", ".kt", ".properties", ".ini", ".txt")):
                continue
            p = os.path.join(d, f)
            with open(p, "rb") as fh:
                raw = fh.read()
            try:
                text = raw.decode("utf-8")
            except UnicodeDecodeError:
                text = raw.decode("latin-1")
            out = text
            for pat, rep in subs:
                out = pat.sub(rep, out)
            if out != text:
                with open(p, "wb") as fh:
                    fh.write(out.encode("utf-8") if raw.decode("utf-8", "ignore") == text else out.encode("latin-1"))
                changed += 1
print("moved", len(moves), "packages, rewrote", changed, "files")
