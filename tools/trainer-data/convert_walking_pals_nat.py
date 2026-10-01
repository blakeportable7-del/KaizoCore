"""Walking Pals for every Pokemon after Gen 3, and the forms the Nat. Dex games use.

    python tools/trainer-data/convert_walking_pals_nat.py [--cache DIR] [--assets DIR]
                                                          [--update] [--offline] [--check-rule] [--preview]

The Gen 1-3 set (walkingpals/, convert_walking_pals.py) came from Ironmon-Tracker, keyed by the
Gen 3 INTERNAL species id. This writes a second set, keyed by NATIONAL dex number so the two never
share a key, straight from PMD Sprite Collab (github.com/PMDCollab/SpriteCollab, CC BY-NC 4.0):

    <assets>/walkingpals-nat/walkingpals-nat.tsv
        One row per sheet and animation: key, animation (idle, walk, sleep, faint), frame width and
        height, the x/y offset the frame is drawn at from a 32x32 icon's corner, and each frame's
        length in game frames (60 a second). The same columns as walkingpals.tsv; the key is the
        national number, or "<national>-<form>" for a form (6-mega-x, 19-alola, 678-female).
    <assets>/walkingpals-nat/{idle,walk,sleep,faint}/<key>.png
        The sheets as walkingpals/ has them: frames left to right; idle and walk keep the eight
        facing directions top to bottom (down, down-right, right, up-right, up, up-left, left,
        down-left), sleep and faint keep only the facing-down row. Re-encoded losslessly as
        indexed PNGs (every PMD sheet is at most 16 colours plus clear), about 60% smaller.
    <assets>/walkingpals-nat/natdex-map.tsv
        The Nat. Dex Extension's species ids 412-1283 (tracker-gba/.../natdex/species.tsv) to the
        sheet each one uses: the national number, the form key, the set and the key in it, blank
        with the reason when PMD Sprite Collab has no sheet for it.
    <assets>/walkingpals-nat/credits.tsv
        Who drew each sheet: the artists credits.txt (and tracker.json) name for the folder and the
        animations used, with the licence each contribution was given under.

Where it reads from: a cache folder OUTSIDE the repo (default C:/Users/bepor/walkingpals-cache)
holding a partial clone of SpriteCollab: no blobs, depth 1, sparse. Only the files this needs are
ever downloaded: tracker.json and the credit files, then each used folder's AnimData.xml and
credits.txt, then the Idle/Walk/Sleep/Faint sheets those name. The whole repo is gigabytes; from
an empty cache this fetched 25 MB and took two minutes, and wrote the same bytes as the run before
it. Later runs reuse the cached commit (written into the TSV headers) unless --update fetches the
newest. --offline never touches the network.

THE OFFSET RULE. Ironmon-Tracker's x/y were placed by hand (PR #409: "Adding animation offsets for
1-42", "...through 164", ...), so there is no formula to copy. PMD's own anchors (the frame centre,
the shadow, the Offsets markers) do not predict them either; the visible sprite's box does. This is
the simple rule closest to the hand placements, fitted on the 1,210 Gen 1-3 sheets (--check-rule
measures it again):

    Take the opaque pixels of the animation's facing-down row: for idle and walk only the first
    frame (the standing pose), for sleep and faint every frame. Put the centre of their bounding box
    (L, T, R, B; R and B exclusive) at (CX, CY) in the 32x32 icon:
        x = floor(CX - (L + R) / 2 + 0.5)      y = floor(CY - (T + B) / 2 + 0.5)
    with (CX, CY) = idle and walk (16, 20), sleep (16, 20.5), faint (17, 19).
    Then walk keeps idle's ground point (PMD's anchor, the frame centre: x + w/2, y + h/2) when the
    rule put it within 1 px of it, so the sprite does not hop when it starts walking.

Against the hand-placed table that is within 1 px for 82% of idle sheets (94% within 2), 78% of
walk (91%), 81% of sleep (94%) and 67% of faint (80%). The misses are mostly giants (Gyarados,
Lugia, Rayquaza), placed by eye with no pattern a clamp on how far they rise or sink could match
(tried). Small and mid-size sprites sit centred a little low in the box; big ones spill past it on
every side, as on PC. Idle and walk share a ground point for 88% of the new sheets (52% of the
hand-placed ones).

--check-rule  also checks out the Gen 1-3 base folders and prints that agreement.
--preview     writes <cache>/contact.png (Gen 4-9 idle frames beside shipped Gen 1-3 ones, each in a
              32x32 box at its offset, and the Gen 1-3 ones again as the rule places them) and
              <cache>/walk-<key>.gif.
Needs: git, Python 3 with Pillow and numpy.
"""
import argparse, collections, io, json, math, pathlib, re, struct, subprocess, sys, unicodedata, zlib
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image

REPO = pathlib.Path(__file__).resolve().parents[2]
URL = "https://github.com/PMDCollab/SpriteCollab.git"
ANIMS = ("idle", "walk", "sleep", "faint")
PMD = {"idle": "Idle", "walk": "Walk", "sleep": "Sleep", "faint": "Faint"}
CENTRE = {"idle": (16, 20), "walk": (16, 20), "sleep": (16, 20.5), "faint": (17, 19)}
FIRST_FRAME_ONLY = {"idle", "walk"}  # the standing pose; sleep and faint use every frame
ROWS_KEPT = {"idle": None, "walk": None, "sleep": 1, "faint": 1}  # None = every row
FEMALE = "0000/0000/0002"  # SpriteCollab: <form>/<shiny 0000>/<gender 0002 = female>
TAB, NL = "\t", "\n"

# The Nat. Dex names a form "<Species>-<suffix>". The same letter means different forms on
# different species (-M is Mega, but Midnight on Lycanroc; -S is Sky, Sandy, Small, School...), so
# each suffix lists the SpriteCollab form names it can mean and the first one that species has wins.
SUFFIX = {
    "M": ["Mega", "Midnight"], "X": ["Mega_X"], "Y": ["Mega_Y"], "Z": ["Mega_Z", "Zen"],
    "P": ["Primal", "Paldea", "Pirouette", "Pa_U"], "PF": ["Paldea_Blaze"], "PW": ["Paldea_Aqua"],
    "A": ["Alola", "Attack", "Ash"], "G": ["Galar", "Sensu"], "GZ": ["Galar_Zen"],
    "H": ["Hisui", "Hangry", "Hero"], "C": ["Cosplay", "Complete", "Crowned_Sword", "Crowned_Shield"],
    "S": ["Spiky", "Speed", "Sand", "Sunshine", "Sky", "Small", "School", "Shadow_Rider", "Stellar"],
    "F": ["Sunny", "Hearthflame", "female"], "W": ["Rainy", "White", "Wellspring"],
    "I": ["Snowy", "Ice_Rider"], "D": ["Defense", "Dusk"], "T": ["Trash", "Therian", "Terastal"],
    "O": ["Origin"], "B": ["Blue", "Black", "Blade", "Bloodmoon"], "E": ["Eternal", "Pom_Pom", "Eternamax"],
    "L": ["Large", "Lowkey"], "J": ["Super"], "10": ["10"], "U": ["Unbound", "Ultra"], "N": ["Noice"],
    "R": ["Rapid_Strike", "Roaming", "Cornerstone"], "DM": ["Dusk_Mane"], "DW": ["Dawn_Wings"],
    "Heat": ["Heat"], "Wash": ["Wash"], "Frost": ["Frost"], "Fan": ["Fan"], "Mow": ["Mow"],
    "F-M": ["Mega"],  # the Nat. Dex changelog: female Mega Meowstic has the male's sprites
}
# Forms that look like the base species, so they use its sheet.
LOOKS_LIKE_BASE = {
    "Pikachu-P": "Partner Pikachu looks like Pikachu",
    "Eevee-P": "Partner Eevee looks like Eevee",
    "Greninja-B": "Battle Bond Greninja looks like Greninja until it transforms",
}
# A form name the SpriteCollab slot spells differently.
SPECIAL_FORM = {"Minior-C": ("Red", "the Red Core stands in for every core colour")}
# A base species with no sheet of its own, standing in with another of the same form.
STAND_IN = {668: (FEMALE, "female", "no male Pyroar sheet in SpriteCollab; the female one stands in")}


def norm(s):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", s.lower())


def form_key(name):
    return name.lower().replace("_", "-")


def git(sc, *args):
    r = subprocess.run(["git", *args], cwd=sc, capture_output=True, text=True, encoding="utf-8")
    if r.returncode:
        sys.exit("git %s failed:\n%s" % (" ".join(args), r.stderr))
    return r.stdout


class Collab:
    """The sparse, blobless SpriteCollab clone, and what its tree holds."""

    META = ["/tracker.json", "/credit_names.txt", "/README.md", "/LICENSE.md", "/license_history/"]

    def __init__(self, cache, update, offline):
        self.dir = cache / "SpriteCollab"
        self.offline = offline
        if not (self.dir / ".git").is_dir():
            if offline:
                sys.exit("no clone at %s and --offline" % self.dir)
            cache.mkdir(parents=True, exist_ok=True)
            print("cloning", URL, "(trees only, no file contents yet)")
            subprocess.run(["git", "clone", "--filter=blob:none", "--no-checkout", "--depth", "1", URL, str(self.dir)], check=True)
            git(self.dir, "config", "core.sparseCheckout", "true")
            git(self.dir, "config", "core.sparseCheckoutCone", "false")
        elif update and not offline:
            print("fetching the newest SpriteCollab commit")
            git(self.dir, "fetch", "--depth", "1", "--filter=blob:none", "origin", "master")
            git(self.dir, "checkout", "--detach", "FETCH_HEAD")
        sp = self.dir / ".git" / "info" / "sparse-checkout"
        kept = set(sp.read_text(encoding="utf-8").split()) if sp.exists() else set()
        self.patterns = kept | set(self.META)  # what earlier runs checked out stays checked out
        self.apply()
        self.commit = git(self.dir, "rev-parse", "HEAD").strip()
        self.date = git(self.dir, "log", "-1", "--format=%cs").strip()
        self.files = set(git(self.dir, "ls-tree", "-r", "--name-only", "HEAD", "sprite").split(NL))
        self.tracker = json.loads((self.dir / "tracker.json").read_text(encoding="utf-8"))

    def want(self, paths):
        new = {"/" + p for p in paths} - self.patterns
        if new:
            self.patterns |= new
            self.apply()

    def apply(self):
        sp = self.dir / ".git" / "info" / "sparse-checkout"
        sp.parent.mkdir(parents=True, exist_ok=True)
        sp.write_text(NL.join(sorted(self.patterns)) + NL, encoding="utf-8")
        if self.offline:
            missing = [p for p in self.patterns if not p.endswith("/") and not any(ch in p for ch in "*?[!")
                       and not (self.dir / p.lstrip("/")).exists()]
            if missing:
                sys.exit("--offline but %d files are not in the cache, e.g. %s" % (len(missing), missing[0]))
            return
        git(self.dir, "read-tree", "-mu", "HEAD")  # downloads just the blobs the patterns now include

    def has(self, folder, name="AnimData.xml"):
        return "%s/%s" % (folder, name) in self.files


def anim_data(path):
    """AnimData.xml: name -> (source sheet name, w, h, durations), CopyOf followed."""
    raw = {a.findtext("Name"): a for a in ET.parse(path).getroot().find("Anims")}

    def resolve(name, seen=()):
        a = raw.get(name)
        if a is None or name in seen:
            return None
        if a.findtext("CopyOf"):
            return resolve(a.findtext("CopyOf"), seen + (name,))
        return name, int(a.findtext("FrameWidth")), int(a.findtext("FrameHeight")), [int(d.text) for d in a.find("Durations")]

    return {n: resolve(n) for n in raw}


# ---------------------------------------------------------------- lossless indexed PNG writer

def _chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def _pack(idx, bits):
    if bits == 8:
        return idx
    per = 8 // bits
    a = np.pad(idx, ((0, 0), (0, (-idx.shape[1]) % per))).reshape(idx.shape[0], -1, per)
    out = np.zeros(a.shape[:2], np.uint8)
    for k in range(per):
        out |= (a[:, :, k] << (8 - bits * (k + 1))).astype(np.uint8)
    return out


def encode_png(rgba):
    """RGBA array -> the smaller indexed PNG of two bit depths and two zlib strategies.
    Clear pixels become palette entry 0; every other colour is kept exactly. Rows are left
    unfiltered: on 150 sampled sheets no PNG filter (Sub, Up, Average, Paeth, or the best per
    row) ever beat none, so trying them only cost time."""
    a = rgba.copy()
    a[a[..., 3] == 0] = 0
    flat = a.reshape(-1, 4).copy().view(np.uint32).ravel()
    cols, inv = np.unique(flat, return_inverse=True)
    pal = cols.view(np.uint8).reshape(-1, 4)
    if len(pal) > 256:
        raise ValueError("%d colours" % len(pal))
    order = np.argsort(pal[:, 3] != 0, kind="stable")
    remap = np.empty_like(order)
    remap[order] = np.arange(len(order))
    idx = remap[inv].reshape(a.shape[:2]).astype(np.uint8)
    pal = pal[order]
    best = None
    depths = [b for b in (1, 2, 4) if len(pal) <= (1 << b)][:1] + [8]
    for bits in depths:
        packed = _pack(idx, bits)
        raw = np.hstack([np.zeros((packed.shape[0], 1), np.uint8), packed]).tobytes()  # filter byte 0
        for strategy in (zlib.Z_DEFAULT_STRATEGY, zlib.Z_FILTERED):
            c = zlib.compressobj(9, zlib.DEFLATED, 15, 9, strategy)
            z = c.compress(raw) + c.flush()
            if best is None or len(z) < len(best[0]):
                best = (z, bits)
    z, bits = best
    alpha = [int(v) for v in pal[:, 3]]
    while alpha and alpha[-1] == 255:
        alpha.pop()
    png = b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", struct.pack(">IIBBBBB", a.shape[1], a.shape[0], bits, 3, 0, 0, 0))
    png += _chunk(b"PLTE", pal[:, :3].tobytes())
    if alpha:
        png += _chunk(b"tRNS", bytes(alpha))
    png += _chunk(b"IDAT", z) + _chunk(b"IEND", b"")
    return png


def same_pixels(a, b):
    ma, mb = a[..., 3] > 0, b[..., 3] > 0
    return a.shape == b.shape and (ma == mb).all() and (a[ma] == b[mb]).all()


# ---------------------------------------------------------------- the offset rule

def row0_box(sheet, w, h, frames):
    """Bounding box of every opaque pixel in the facing-down row, over its first `frames` frames."""
    row = sheet[:h, : w * frames, 3] > 0
    box = None
    for c in range(frames):
        ys, xs = np.nonzero(row[:, c * w:(c + 1) * w])
        if len(xs):
            b = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
            box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    return box


def place(anim, sheet, w, h, frames):
    box = row0_box(sheet, w, h, 1 if anim in FIRST_FRAME_ONLY else frames)
    cx, cy = CENTRE[anim]
    if box is None:  # nothing drawn facing down: centre the frame instead
        return int(math.floor(cx - w / 2 + 0.5)), int(math.floor(cy - h / 2 + 0.5))
    l, t, r, b = box
    return int(math.floor(cx - (l + r) / 2 + 0.5)), int(math.floor(cy - (t + b) / 2 + 0.5))


def snap_walk(rows):
    """Walk keeps idle's ground point when the rule put it within 1 px of it."""
    i, w = rows.get("idle"), rows.get("walk")
    if not i or not w:
        return
    sx, sy = i["x"] + i["w"] // 2 - w["w"] // 2, i["y"] + i["h"] // 2 - w["h"] // 2
    if max(abs(sx - w["x"]), abs(sy - w["y"])) <= 1:
        w["x"], w["y"] = sx, sy


# ---------------------------------------------------------------- what to convert

def natdex_names():
    out = {}
    for line in (REPO / "tracker-gba/src/main/resources/natdex/species.tsv").read_text(encoding="utf-8").splitlines():
        if line.strip():
            a, b = line.split(TAB)
            out[int(a)] = b
    return out


class Target:
    def __init__(self, national, form, folder, label, note=""):
        self.national, self.form, self.folder, self.label, self.note = national, form, folder, label, note
        self.key = str(national) + ("-" + form if form else "")


def plan(col, names):
    """Every sheet to write, and the Nat. Dex map rows (id -> (national, form, set, key, note))."""
    by_name = {norm(v["name"]): int(k) for k, v in col.tracker.items()}
    targets, rows = {}, {}

    def base_target(nat):
        folder = "sprite/%04d" % nat
        if col.has(folder):
            return Target(nat, "", folder, col.tracker["%04d" % nat]["name"])
        if nat in STAND_IN:
            sub, fk, note = STAND_IN[nat]
            if col.has(folder + "/" + sub):
                return Target(nat, fk, folder + "/" + sub, col.tracker["%04d" % nat]["name"] + " " + fk, note)
        return None

    for nat in range(387, 1026):
        t = base_target(nat)
        if t:
            targets[t.key] = t

    for i in range(412, 1284):
        name = names[i]
        if i <= 1050:  # the expansion's base species: Gen 3's internal order carried on, 25 past national
            nat = by_name.get(norm(name))
            if nat is None or nat != i - 25:
                sys.exit("species.tsv %d %s does not match national %s" % (i, name, nat))
            t = targets.get(str(nat)) or next((x for x in targets.values() if x.national == nat), None)
            if t:
                rows[i] = (nat, t.form, "walkingpals-nat", t.key, t.note)
            else:
                rows[i] = (nat, "", "", "", "no sheet: SpriteCollab has no %s sprite yet" % name)
            continue
        base, suffix = (name[:-4], "F-M") if name.endswith("-F-M") else name.rsplit("-", 1)
        nat = by_name.get(norm(base))
        if nat is None:
            sys.exit("species.tsv %d %s: no SpriteCollab species %s" % (i, name, base))
        if name in LOOKS_LIKE_BASE:
            note = LOOKS_LIKE_BASE[name]
            if nat <= 386:
                rows[i] = (nat, "", "walkingpals", str(gen3_internal(nat, names, by_name)), note)
            else:
                t = base_target(nat)
                rows[i] = (nat, "", "walkingpals-nat", t.key, note) if t else (nat, "", "", "", "no sheet")
            continue
        groups = col.tracker["%04d" % nat].get("subgroups", {})
        forms = {norm(g.get("name", "")): (fid, g.get("name")) for fid, g in groups.items() if fid != "0000"}
        if name in SPECIAL_FORM:
            want, note = SPECIAL_FORM[name]
            candidates = [want]
        else:
            note = ""
            candidates = SUFFIX.get(suffix)
            if candidates is None:
                sys.exit("species.tsv %d %s: unknown form suffix -%s" % (i, name, suffix))
        hit = None
        for c in candidates:
            if c == "female":
                hit = ("female", FEMALE, "female")
                break
            if norm(c) in forms:
                fid, real = forms[norm(c)]
                hit = (form_key(real), fid, real)
                break
        if hit is None:
            rows[i] = (nat, "", "", "", "no sheet: SpriteCollab has no %s form of %s" % ("/".join(candidates), base))
            continue
        fk, fid, real = hit
        folder = "sprite/%04d/%s" % (nat, fid if fk != "female" else FEMALE)
        if not col.has(folder):
            rows[i] = (nat, fk, "", "", "no sheet: SpriteCollab's %s %s slot is empty" % (base, real.replace("_", " ")))
            continue
        t = Target(nat, fk, folder, "%s %s" % (base, real.replace("_", " ")), note)
        targets.setdefault(t.key, t)
        rows[i] = (nat, fk, "walkingpals-nat", t.key, note)
    return targets, rows


def gen3_internal(nat, names, by_name):
    """National 1-386 -> Gen 3's internal id: 1-251 the same, Hoenn at 277-411 in internal order
    (species.tsv names those ids, as the Nat. Dex keeps Gen 3's numbering)."""
    if nat <= 251:
        return nat
    for i in range(277, 412):
        if by_name.get(norm(names.get(i, ""))) == nat:
            return i
    raise ValueError("no Gen 3 internal id for national %d" % nat)


# ---------------------------------------------------------------- credits

def credit_names(col):
    out = {}
    for line in (col.dir / "credit_names.txt").read_text(encoding="utf-8").splitlines()[1:]:
        c = line.split(TAB)
        if len(c) >= 2 and c[1]:
            out[c[1]] = c[0].strip() or c[1]
    return out


def credits_for(col, t, used, who):
    """The artists of the animations used from this folder: credits.txt's current entries that
    touched one of them, then tracker.json's primary and secondary credit for the folder."""
    people, licences = [], []
    p = col.dir / t.folder / "credits.txt"
    if p.exists():
        for line in p.read_text(encoding="utf-8").splitlines():
            c = line.split(TAB)
            if len(c) < 5 or c[2] != "CUR" or not used & set(c[4].split(",")):
                continue
            if c[1] not in people:
                people.append(c[1])
            if c[3] not in licences:
                licences.append(c[3])
    node = col.tracker["%04d" % t.national]
    for part in t.folder.split("/")[2:]:
        node = node.get("subgroups", {}).get(part, {})
    cr = node.get("sprite_credit") or {}
    for pid in [cr.get("primary")] + list(cr.get("secondary") or []):
        if pid and pid not in people:
            people.append(pid)
    return [who.get(pid, pid) for pid in people], licences


# ---------------------------------------------------------------- convert

def convert(col, targets, out):
    for anim in ANIMS:
        (out / anim).mkdir(parents=True, exist_ok=True)
    need = set()
    for t in targets.values():
        need |= {t.folder + "/AnimData.xml", t.folder + "/credits.txt"}
    col.want(p for p in need if p in col.files)
    datas = {}
    need = set()
    for key, t in targets.items():
        ad = anim_data(col.dir / t.folder / "AnimData.xml")
        datas[key] = ad
        for anim in ANIMS:
            src = ad.get(PMD[anim])
            if src and col.has(t.folder, src[0] + "-Anim.png"):
                need.add("%s/%s-Anim.png" % (t.folder, src[0]))
    col.want(need)
    who = credit_names(col)
    table, credits, missing, stats = [], [], collections.defaultdict(list), collections.Counter()
    for key in sorted(targets, key=lambda k: (int(k.split("-")[0]), k)):
        t, ad = targets[key], datas[key]
        rows, used = {}, set()
        for anim in ANIMS:
            src = ad.get(PMD[anim])
            if not src or not col.has(t.folder, src[0] + "-Anim.png"):
                missing[anim].append(key)
                continue
            name, w, h, durs = src
            sheet = np.array(Image.open(col.dir / t.folder / (name + "-Anim.png")).convert("RGBA"))
            if sheet.shape[1] < w * len(durs) or sheet.shape[0] < h:
                print("  skip %s %s: sheet %s smaller than %d frames of %dx%d" % (key, anim, sheet.shape[:2], len(durs), w, h))
                missing[anim].append(key)
                continue
            keep = ROWS_KEPT[anim]
            sheet = sheet[: (keep * h if keep else sheet.shape[0] // h * h), : w * len(durs)]
            x, y = place(anim, sheet, w, h, len(durs))
            png = encode_png(sheet)
            back = np.array(Image.open(io.BytesIO(png)).convert("RGBA"))
            if not same_pixels(sheet, back):
                sys.exit("re-encoding changed %s %s" % (key, anim))
            (out / anim / (key + ".png")).write_bytes(png)
            stats["bytes"] += len(png)
            stats["sheets"] += 1
            rows[anim] = dict(w=w, h=h, x=x, y=y, durs=durs)
            used.add(name)
        snap_walk(rows)
        for anim in ANIMS:
            if anim in rows:
                r = rows[anim]
                table.append([key, anim, r["w"], r["h"], r["x"], r["y"], ",".join(map(str, r["durs"]))])
        people, licences = credits_for(col, t, used, who)
        credits.append([key, t.label.replace("_", " "), t.folder, ", ".join(people), ", ".join(licences)])
    return table, credits, missing, stats


def write_tsv(path, header, comment, rows):
    with open(path, "w", encoding="utf-8", newline=NL) as f:
        for c in comment:
            f.write("# " + c + NL)
        f.write("# " + TAB.join(header) + NL)
        for r in rows:
            f.write(TAB.join(str(v) for v in r) + NL)


# ---------------------------------------------------------------- --check-rule

def check_rule(col, names):
    """How close the rule comes to Ironmon-Tracker's hand placements for Gen 1-3."""
    by_name = {norm(v["name"]): int(k) for k, v in col.tracker.items()}
    shipped = collections.defaultdict(dict)
    for line in (REPO / "app/src/main/assets/walkingpals/walkingpals.tsv").read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or not line.strip():
            continue
        c = line.split(TAB)
        shipped[int(c[0])][c[1]] = dict(w=int(c[2]), h=int(c[3]), x=int(c[4]), y=int(c[5]), n=len(c[6].split(",")))
    nat_of = {}
    for i in shipped:
        nat = i if i <= 251 else by_name.get(norm(names.get(i, "")))
        if nat:
            nat_of[i] = nat
    col.want(p for i, nat in nat_of.items() for p in ["sprite/%04d/AnimData.xml" % nat] +
             ["sprite/%04d/%s-Anim.png" % (nat, PMD[a]) for a in ANIMS] if p in col.files)
    err = collections.defaultdict(list)
    for i, nat in sorted(nat_of.items()):
        folder = col.dir / ("sprite/%04d" % nat)
        if not (folder / "AnimData.xml").exists():
            continue
        ad = anim_data(folder / "AnimData.xml")
        rows = {}
        for anim, s in shipped[i].items():
            src = ad.get(PMD[anim])
            p = folder / (src[0] + "-Anim.png") if src else None
            if not src or not p.exists() or (src[1], src[2], len(src[3])) != (s["w"], s["h"], s["n"]):
                continue  # SpriteCollab has redrawn it since: not comparable
            sheet = np.array(Image.open(p).convert("RGBA"))
            x, y = place(anim, sheet, s["w"], s["h"], s["n"])
            rows[anim] = dict(w=s["w"], h=s["h"], x=x, y=y)
        snap_walk(rows)
        for anim, r in rows.items():
            s = shipped[i][anim]
            err[anim].append(max(abs(r["x"] - s["x"]), abs(r["y"] - s["y"])))
    print("offset rule against the hand-placed Gen 1-3 table:")
    for anim in ANIMS:
        e = np.array(err[anim])
        if len(e):
            print("  %-5s %4d sheets: exact %3.0f%%, within 1 px %3.0f%%, within 2 px %3.0f%%" % (
                anim, len(e), 100 * np.mean(e == 0), 100 * np.mean(e <= 1), 100 * np.mean(e <= 2)))


# ---------------------------------------------------------------- --preview

def preview(cache, assets):
    """contact.png: idle frame 0, facing down, of Gen 4-9 sheets and forms, each in a 32x32 box (the
    yellow square) at its x/y; then shipped Gen 1-3 sheets as Ironmon-Tracker placed them by hand, and
    the same Gen 1-3 sheets placed by the rule. walk-<key>.gif: one walk sheet, every facing in turn."""
    from PIL import ImageDraw
    WORLD, BOX, SCALE, PAD, COLS = 80, 24, 2, 12, 10
    cell = WORLD * SCALE

    def load(folder):
        out = {}
        for line in (assets / folder / (folder + ".tsv")).read_text(encoding="utf-8").splitlines():
            if line.startswith("#") or not line.strip():
                continue
            c = line.split(TAB)
            out[(c[0], c[1])] = [int(v) for v in c[2:6]] + [[int(v) for v in c[6].split(",")]]
        return out

    def stage(fr, x, y):
        world = Image.new("RGBA", (WORLD, WORLD), (52, 56, 66, 255))
        world.alpha_composite(Image.new("RGBA", (32, 32), (88, 104, 132, 255)), (BOX, BOX))
        world.paste(fr, (BOX + x, BOX + y), fr)  # binary alpha; paste takes negative offsets
        ImageDraw.Draw(world).rectangle((BOX - 1, BOX - 1, BOX + 32, BOX + 32), outline=(255, 214, 0, 255))
        return world.resize((cell, cell), Image.NEAREST)

    nat, old = load("walkingpals-nat"), load("walkingpals")
    new_keys = [k for k in (
        "387", "390", "393", "399", "403", "417", "425", "426", "428", "442", "445", "448", "479", "483", "484",
        "487", "493", "495", "498", "501", "530", "571", "609", "635", "643", "644", "646", "650", "653", "656",
        "658", "700", "718", "722", "778", "800", "807", "887", "890", "906", "909", "912", "1007", "1025",
        "6-mega-x", "19-alola", "128-paldea", "382-primal", "670-eternal", "678-female", "745-midnight",
        "888-crowned-sword", "668-female", "414") if (k, "idle") in nat]
    old_keys = ["1", "4", "6", "25", "94", "130", "143", "150", "249", "250", "277", "406"]
    sections = []
    tiles = []
    for key in new_keys:
        w, h, x, y, _ = nat[(key, "idle")]
        fr = Image.open(assets / "walkingpals-nat" / "idle" / (key + ".png")).convert("RGBA").crop((0, 0, w, h))
        tiles.append((key, stage(fr, x, y)))
    sections.append(("Gen 4-9 and Nat. Dex forms (walkingpals-nat), placed by the rule", tiles))
    hand, rule = [], []
    for key in old_keys:
        w, h, x, y, durs = old[(key, "idle")]
        sheet = Image.open(assets / "walkingpals" / "idle" / (key + ".png")).convert("RGBA")
        fr = sheet.crop((0, 0, w, h))
        hand.append((key, stage(fr, x, y)))
        rx, ry = place("idle", np.array(sheet), w, h, len(durs))
        rule.append(("%s (%+d,%+d)" % (key, rx - x, ry - y), stage(fr, rx, ry)))
    sections.append(("Gen 1-3 as shipped (walkingpals, Gen 3 ids), placed by hand in Ironmon-Tracker", hand))
    sections.append(("the same Gen 1-3 sheets placed by the rule (difference from the hand placement)", rule))
    height = PAD + sum(18 + math.ceil(len(t) / COLS) * (cell + 16) + PAD for _, t in sections)
    img = Image.new("RGBA", (PAD + COLS * (cell + PAD), height), (30, 32, 38, 255))
    d = ImageDraw.Draw(img)
    yy = PAD
    for title, tiles in sections:
        d.text((PAD, yy), title, fill=(235, 235, 235, 255))
        yy += 18
        for n, (label, tile) in enumerate(tiles):
            cx, cy = PAD + (n % COLS) * (cell + PAD), yy + (n // COLS) * (cell + 16)
            img.alpha_composite(tile, (cx, cy))
            d.text((cx, cy + cell + 2), label, fill=(235, 235, 235, 255))
        yy += math.ceil(len(tiles) / COLS) * (cell + 16) + PAD
    img.save(cache / "contact.png")
    key = next(k for k in ("445", "387") if (k, "walk") in nat)
    w, h, x, y, durs = nat[(key, "walk")]
    sheet = Image.open(assets / "walkingpals-nat" / "walk" / (key + ".png")).convert("RGBA")
    frames, times = [], []
    for row in range(sheet.height // h):
        for _ in range(2):
            for c, dur in enumerate(durs):
                fr = sheet.crop((c * w, row * h, (c + 1) * w, (row + 1) * h))
                frames.append(stage(fr, x, y).resize((cell * 2, cell * 2), Image.NEAREST).convert("RGB"))
                times.append(max(20, round(dur * 1000 / 60)))
    frames[0].save(cache / ("walk-%s.gif" % key), save_all=True, append_images=frames[1:], duration=times, loop=0)
    print("preview:", cache / "contact.png", "and", cache / ("walk-%s.gif" % key))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--cache", default="C:/Users/bepor/walkingpals-cache", type=pathlib.Path)
    ap.add_argument("--assets", default=REPO / "app/src/main/assets", type=pathlib.Path)
    ap.add_argument("--update", action="store_true", help="fetch the newest SpriteCollab commit first")
    ap.add_argument("--offline", action="store_true", help="use only what the cache already holds")
    ap.add_argument("--check-rule", action="store_true", help="measure the offset rule on the Gen 1-3 table")
    ap.add_argument("--preview", action="store_true", help="write contact.png and a walk GIF into the cache")
    args = ap.parse_args()
    col = Collab(args.cache, args.update, args.offline)
    names = natdex_names()
    print("SpriteCollab", col.commit[:12], col.date)
    targets, rows = plan(col, names)
    out = args.assets / "walkingpals-nat"
    for anim in ANIMS:  # a rerun replaces the set, so a sheet SpriteCollab dropped does not linger
        for p in (out / anim).glob("*.png") if (out / anim).is_dir() else []:
            p.unlink()
    table, credits, missing, stats = convert(col, targets, out)
    src = "PMD Sprite Collab %s (%s), github.com/PMDCollab/SpriteCollab" % (col.commit[:12], col.date)
    write_tsv(out / "walkingpals-nat.tsv", ["key", "animation", "w", "h", "x", "y", "durations"], [
        "Walking Pals after Gen 3, keyed by NATIONAL dex number (<national> or <national>-<form>),",
        "never by the Gen 3 internal id walkingpals/ uses. Written by tools/trainer-data/convert_walking_pals_nat.py",
        "from " + src + ". Sprites CC BY-NC 4.0 (see credits.tsv).",
        "x,y: offset from a 32x32 icon's corner; durations in game frames (60 a second).",
    ], table)
    write_tsv(out / "credits.tsv", ["key", "sprite", "SpriteCollab folder", "artists", "licences"], [
        "Who drew each Walking Pals sheet in this folder: " + src + ".",
        "Artists: the current credits.txt entries for the animations used (Idle, Walk, Sleep, Faint), then",
        "tracker.json's credit for the folder, by the names in credit_names.txt (a Discord id where none is registered).",
        "Licences as each contribution was given: CC_BY-NC_4 = CC BY-NC 4.0; PMDCollab_1 = use with credit;",
        "PMDCollab_2 = use with credit, not for profit; Unspecified = Spike Chunsoft's own sprites from the PMD games.",
        "The collab's terms for the whole repository: CC BY-NC 4.0 (non-commercial, credit the artists).",
    ], credits)
    map_rows = [[i, names[i]] + list(rows[i]) for i in sorted(rows)]
    write_tsv(out / "natdex-map.tsv", ["natdex", "name", "national", "form", "set", "key", "note"], [
        "The Nat. Dex Extension's species ids 412-1283 (tracker-gba/src/main/resources/natdex/species.tsv) to a",
        "Walking Pals sheet. set walkingpals-nat: key is <national>[-<form>] in this folder; set walkingpals: key is",
        "the Gen 3 internal id in walkingpals/. Blank set and key: no sheet, and the note says why.",
        "Ids 1-411 are Gen 3's own internal ids and walkingpals/ already covers them.",
    ], map_rows)
    per = collections.Counter(r[1] for r in table)
    print("sheets: %d for %d keys (%s), %.2f MB" % (stats["sheets"], len(targets), ", ".join("%s %d" % (a, per[a]) for a in ANIMS), stats["bytes"] / 1e6))
    for anim in ANIMS:
        if anim != "faint":
            print("  no %s: %s" % (anim, " ".join(missing[anim]) or "-"))
    print("  no faint: %d keys" % len(missing["faint"]))
    unmapped = [r for r in map_rows if not r[5]]
    print("nat. dex ids 412-1283: %d mapped, %d without a sheet" % (len(map_rows) - len(unmapped), len(unmapped)))
    for r in unmapped:
        print("  %d %s: %s" % (r[0], r[1], r[6]))
    if args.check_rule:
        check_rule(col, names)
    if args.preview:
        preview(args.cache, args.assets)


if __name__ == "__main__":
    main()
