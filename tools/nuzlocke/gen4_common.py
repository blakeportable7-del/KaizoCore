"""Shared paths and helpers for the gen4_*.py generators and their --check modes (2026-09-29).

The Generation 4 Nuzlocke data files (levelcaps-gen4.tsv, families-gen4.tsv, species-gen4.tsv, statics-gen4.tsv) are
written by the gen4_*.py scripts in this folder. Every script writes plain ASCII with LF line endings (the repo's
working tree wants CRLF: run scratchpad/nzgbds/to_crlf.py on the result) and re-reads its sources in --check mode,
comparing the regenerated text with the shipped file with line endings ignored.

Exit codes of every script: 0 = fine, 1 = the shipped file does not match its sources, 2 = a source is missing so
nothing could be checked (a missing source is never reported as a pass).

Sources
  * the ROM dumps (never copied into the repo): Platinum and Diamond, see gen4_nds.py. Override the paths with the
    environment variables NZ_ROM_PLATINUM and NZ_ROM_DIAMOND.
  * the pret disassemblies at PINNED commits (PINS below). A file is looked up in this order: a local checkout under
    $NZ_PRET_DIR/<repo> whose HEAD is the pinned commit, the download cache ($NZ_PRET_CACHE, default
    <temp>/kaizocore-nuzlocke-pret), then https://raw.githubusercontent.com/pret/<repo>/<commit>/<path>.
"""
import io
import os
import re
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "tracker-gba" / "src" / "main" / "resources" / "nuzlocke"
NDS_RES = ROOT / "tracker-nds" / "src" / "main" / "resources"
DOCS = ROOT / "docs" / "research"
DATE = "2026-09-29"

EXIT_OK = 0
EXIT_MISMATCH = 1
EXIT_MISSING = 2

# The pinned pret commits every fact below was read from (the same commits the research doc used).
PINS = {
    "pokediamond": "5bc4b1a3d8f100f77a4c64e59a0d544a0e29b3ec",
    "pokeplatinum": "c248fb3f8cc9934ded800e489567c5c0eeee92eb",
    "pokeheartgold": "9d8b7591f09b65804da2fb2dfd56f320633e0d36",
}


# Characters of the tracker's species names that are not ASCII (built with chr() so this file stays ASCII).
FEMALE_SIGN = chr(0x2640)
MALE_SIGN = chr(0x2642)
RIGHT_QUOTE = chr(0x2019)


class MissingSource(Exception):
    """A ROM dump or a disassembly file that this run needs is not available."""


# ---------------------------------------------------------------------------------------------------------------
# pret access
# ---------------------------------------------------------------------------------------------------------------
_local_ok = {}


def _local_dir(repo):
    base = os.environ.get("NZ_PRET_DIR")
    if not base:
        return None
    d = Path(base) / repo
    if not d.is_dir():
        return None
    if repo not in _local_ok:
        ok = False
        try:
            head = subprocess.run(["git", "-C", str(d), "rev-parse", "HEAD"], capture_output=True, text=True,
                                  timeout=30).stdout.strip()
            ok = head == PINS[repo]
        except Exception:
            ok = False
        _local_ok[repo] = ok
        if not ok:
            print("note: %s is not at the pinned commit %s, ignoring it" % (d, PINS[repo][:10]), file=sys.stderr)
    return d if _local_ok[repo] else None


def _cache_dir():
    return Path(os.environ.get("NZ_PRET_CACHE") or (Path(tempfile.gettempdir()) / "kaizocore-nuzlocke-pret"))


def pret_text(repo, path):
    """The text of one file of a pret repository at the pinned commit (UTF-8, LF)."""
    local = _local_dir(repo)
    if local is not None and (local / path).is_file():
        return (local / path).read_bytes().decode("utf-8", "replace").replace("\r\n", "\n")
    cached = _cache_dir() / repo / PINS[repo] / path
    if cached.is_file():
        return cached.read_bytes().decode("utf-8", "replace").replace("\r\n", "\n")
    url = "https://raw.githubusercontent.com/pret/%s/%s/%s" % (repo, PINS[repo], path)
    try:
        with urllib.request.urlopen(url, timeout=90) as r:
            data = r.read()
    except Exception as e:  # network down, 404 ...
        raise MissingSource("%s (%s)" % (url, e))
    cached.parent.mkdir(parents=True, exist_ok=True)
    cached.write_bytes(data)
    return data.decode("utf-8", "replace").replace("\r\n", "\n")


def pret_json(repo, path):
    import json
    return json.loads(pret_text(repo, path))


# ---------------------------------------------------------------------------------------------------------------
# tracker resources
# ---------------------------------------------------------------------------------------------------------------
def read_tsv_rows(path):
    """The data rows (comment and blank lines skipped) of a tab separated file, as lists of str."""
    rows = []
    for line in io.open(str(path), encoding="utf-8", newline=None).read().split("\n"):
        line = line.rstrip("\r")
        if not line.strip() or line.startswith("#"):
            continue
        rows.append(line.split("\t"))
    return rows


def species_names():
    """{national dex number: NAME} of tracker-nds gen4/species.tsv, made plain ASCII: the two Nidoran signs
    become ' F' and ' M' and the typographic apostrophe of Farfetch'd becomes a plain one."""
    names = {}
    for p in read_tsv_rows(NDS_RES / "gen4" / "species.tsv"):
        if len(p) >= 2 and p[0].isdigit():
            n = p[1].strip().replace(FEMALE_SIGN, " F").replace(MALE_SIGN, " M").replace(RIGHT_QUOTE, "'")
            names[int(p[0])] = n
    return names


def raw_species_names():
    """Same table exactly as the tracker spells it (with the signs), for the name-difference note."""
    names = {}
    for p in read_tsv_rows(NDS_RES / "gen4" / "species.tsv"):
        if len(p) >= 2 and p[0].isdigit():
            names[int(p[0])] = p[1].strip()
    return names


def location_names(kind):
    """{map id: area name} of gen4/locations-pt.tsv (kind 'pt') or locations-hgss.tsv (kind 'hgss'), exactly as
    the tracker spells them (typos and accents included)."""
    names = {}
    for p in read_tsv_rows(NDS_RES / "gen4" / ("locations-%s.tsv" % kind)):
        if len(p) >= 2 and p[0].isdigit():
            names[int(p[0])] = p[1].strip()
    return names


def const_name(tracker_name):
    """The pret constant body of a tracker species name: 'MR. MIME' -> 'MR_MIME', 'NIDORAN<female sign>' ->
    'NIDORAN_F', "FARFETCH'D" -> 'FARFETCHD', 'HO-OH' -> 'HO_OH'. Add 'SPECIES_' for the disassembly's constant."""
    n = tracker_name.replace(FEMALE_SIGN, "_F").replace(MALE_SIGN, "_M")
    n = n.replace("'", "").replace(RIGHT_QUOTE, "").replace(".", "")
    n = re.sub(r"[ \-]+", "_", n)
    return n.upper().strip("_")


def species_by_const():
    """{'SPECIES_BULBASAUR': 1, ...} for National Dex 1..493, and {1: 'SPECIES_BULBASAUR'}."""
    by = {}
    rev = {}
    for i, n in raw_species_names().items():
        c = "SPECIES_" + const_name(n)
        by[c] = i
        rev[i] = c
    return by, rev


def title_case(name):
    """'BULBASAUR' -> 'Bulbasaur', 'MR. MIME' -> 'Mr. Mime', 'HO-OH' -> 'Ho-Oh', "FARFETCH'D" -> "Farfetch'd"."""
    out = []
    up = True
    for ch in name.lower():
        out.append(ch.upper() if up else ch)
        up = ch in " .-"
    return "".join(out)


def pretty_species(name):
    return title_case(name)


# ---------------------------------------------------------------------------------------------------------------
# output helpers
# ---------------------------------------------------------------------------------------------------------------
def render(lines):
    """Lines to file text: LF endings, one final newline. Refuses anything that is not plain ASCII."""
    text = "\n".join(lines) + "\n"
    bad = [c for c in text if ord(c) > 126 or (ord(c) < 32 and c not in "\t\n")]
    if bad:
        raise ValueError("non-ASCII or control characters in output: %r" % sorted(set(bad))[:10])
    return text


def normalized(path):
    """A file's text with CRLF turned into LF, or None when it does not exist."""
    p = Path(path)
    if not p.exists():
        return None
    return p.read_bytes().decode("ascii").replace("\r\n", "\n")


def diff_lines(expected, actual, limit=12):
    """A short list of human readable differences between two texts (empty when equal)."""
    if expected == actual:
        return []
    exp = expected.split("\n")
    act = (actual or "").split("\n")
    out = []
    for i in range(max(len(exp), len(act))):
        e = exp[i] if i < len(exp) else "<missing>"
        a = act[i] if i < len(act) else "<missing>"
        if e != a:
            out.append("line %d: generated %r, shipped %r" % (i + 1, e[:110], a[:110]))
            if len(out) >= limit:
                out.append("... more differences")
                break
    return out


def finish(name, problems, missing=()):
    """Print the summary of a --check run and return the exit code."""
    if missing:
        for m in missing:
            print("%s: SKIPPED, source missing: %s" % (name, m))
        return EXIT_MISSING
    if problems:
        print("%s: MISMATCH (%d)" % (name, len(problems)))
        for p in problems[:40]:
            print("  - " + p)
        return EXIT_MISMATCH
    print("%s: OK" % name)
    return EXIT_OK


def compare_with_shipped(filename, text, problems):
    """Add the differences between the regenerated text and the shipped file (line endings ignored)."""
    shipped = normalized(OUT_DIR / filename)
    if shipped is None:
        problems.append("%s is not shipped yet" % filename)
        return
    for d in diff_lines(text, shipped):
        problems.append("%s: %s" % (filename, d))


def write_out(filename, text):
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / filename).write_bytes(text.encode("ascii"))
    print("wrote %s (%d lines, LF; run to_crlf.py on it)" % (OUT_DIR / filename, text.count("\n")))


def rom_path(env, default):
    p = os.environ.get(env) or default
    if not os.path.exists(p):
        raise MissingSource("ROM dump %s (set %s)" % (p, env))
    return p
