"""Shared paths and helpers for the gen5_*.py generators and their --check modes.

The Generation 5 Nuzlocke data files (levelcaps, families, species, statics) are written by the gen5_*.py scripts in
this folder. Every script writes plain ASCII with LF line endings (the repo's working tree wants CRLF: run
scratchpad/nzgbds/to_crlf.py on the result) and re-reads its sources in --check mode, comparing the result with the
shipped file with line endings ignored.

Exit codes of every script: 0 = fine, 1 = the shipped file does not match its sources, 2 = a source is missing so
nothing could be checked.
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "tracker-gba" / "src" / "main" / "resources" / "nuzlocke"
NDS_RES = ROOT / "tracker-nds" / "src" / "main" / "resources"
DOCS = ROOT / "docs" / "research"
DATE = "2026-09-29"

EXIT_OK = 0
EXIT_MISMATCH = 1
EXIT_MISSING = 2


def read_tsv_rows(path):
    """The data rows (comment and blank lines skipped) of a tab separated file, as lists of str."""
    rows = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        rows.append(line.split("\t"))
    return rows


def species_names():
    """{national dex number: NAME} exactly as tracker-nds gen5/species.tsv spells it."""
    names = {}
    for p in read_tsv_rows(NDS_RES / "gen5" / "species.tsv"):
        if len(p) >= 2 and p[0].isdigit():
            names[int(p[0])] = p[1].strip()
    return names


def location_names(family):
    """{map id: area name} of gen5/locations-bw.tsv (family 'bw') or locations-b2w2.tsv (family 'b2w2')."""
    names = {}
    for p in read_tsv_rows(NDS_RES / "gen5" / ("locations-%s.tsv" % family)):
        if len(p) >= 2 and p[0].isdigit():
            names[int(p[0])] = p[1].strip()
    return names


def title_case(name):
    """'BULBASAUR' -> 'Bulbasaur', 'MR. MIME' -> 'Mr. Mime', 'HO-OH' -> 'Ho-Oh' (for comment lines only)."""
    out = []
    up = True
    for ch in name.lower():
        out.append(ch.upper() if up else ch)
        up = ch in " .-'"
    return "".join(out)


# The tracker's species names carry a form letter for a few species; text meant for people reads better without it.
FORM_NAMES = {
    "BURMY P": "Burmy", "WORMADAM P": "Wormadam", "CHERRIM O": "Cherrim", "SHELLOS W": "Shellos",
    "GASTRODON W": "Gastrodon", "GIRATINA A": "Giratina", "SHAYMIN L": "Shaymin", "UNFEZANT M": "Unfezant",
    "BASCULIN R": "Basculin", "FRILLISH M": "Frillish", "JELLICENT M": "Jellicent", "MELOETTA A": "Meloetta",
}


def pretty_species(name):
    """'JELLICENT M' -> 'Jellicent', 'MR. MIME' -> 'Mr. Mime' (comment lines and the ace column)."""
    return FORM_NAMES.get(name, title_case(name))


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
    if problems:
        print("%s: MISMATCH (%d)" % (name, len(problems)))
        for p in problems[:40]:
            print("  - " + p)
        return EXIT_MISMATCH
    if missing:
        return EXIT_MISSING
    print("%s: OK" % name)
    return EXIT_OK


def main_flags(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    return {"check": "--check" in argv, "quiet": "--quiet" in argv}
