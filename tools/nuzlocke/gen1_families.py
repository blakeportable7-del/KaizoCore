#!/usr/bin/env python
"""Generate (default) or verify (--check) tracker-gba/src/main/resources/nuzlocke/families-gen1.tsv.

    python tools/nuzlocke/gen1_families.py            # rewrite the file (LF endings; run to_crlf.py afterwards)
    python tools/nuzlocke/gen1_families.py --check    # re-read the disassemblies and the Red, Blue and Yellow ROMs, compare

An evolution line is every species joined by an evolution edge, from the game's own evolution table (pokered and
pokeyellow data/pokemon/evos_moves.asm; in the ROM the table the randomizer calls PokemonMovesetsTableOffset, one
pointer per internal species id, each followed by that species's evolutions). Internal ids become dex numbers through
PokedexOrder, and dex numbers become names through natdex/species.tsv.
"""
import argparse
import sys
from pathlib import Path

sys.dont_write_bytecode = True     # never leave .pyc files behind in the repo
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen1_common as g

OUT = g.NZ_DIR / "families-gen1.tsv"


def edges_disasm(d):
    dex = d.dex_of_internal()
    out = set()
    for src, evs in d.evos().items():
        for _, _, target in evs:
            out.add((dex[src], dex[target]))
    return out


def edges_rom(rom):
    dex = rom.dex_of_internal()
    out = set()
    for src, evs in rom.evos().items():
        for _, _, target in evs:
            out.add((dex[src], dex[target]))
    return out


def families(edges):
    """Connected groups of dex numbers 1..151, each sorted; only groups of two or more, ordered by their lowest number."""
    parent = {i: i for i in range(1, 152)}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for a, b in edges:
        parent[find(a)] = find(b)
    groups = {}
    for i in range(1, 152):
        groups.setdefault(find(i), []).append(i)
    return sorted((sorted(v) for v in groups.values() if len(v) > 1), key=lambda v: v[0])


def render(fams):
    names = g.natdex_names()
    lines = [
        "Evolution lines for the dupes clause, Generation 1 (2026-09-29): one line per line, names as natdex/species.tsv spells them.",
        "A species on no line is its own line. Generation 1 has no baby forms, so every line starts at a wild species.",
        "",
        "These are the VANILLA lines, from the games' own evolution tables: pokered and pokeyellow data/pokemon/evos_moves.asm,",
        "and the same table read out of the Red, Blue and Yellow ROM dumps (`python tools/nuzlocke/gen1_families.py --check`",
        "does both; the three games have identical lines). A branching line keeps all its branches on one row (Poliwag,",
        "Eevee). Nidoran F and Nidoran M are two lines. Only species 1 to 151. A randomizer that shuffles evolutions changes",
        "who belongs together and this file cannot know: the dupes clause is then a suggestion the player corrects by hand.",
        "",
        "names, comma separated, lowest dex number first",
    ]
    out = g.header_block(lines)
    for fam in fams:
        out += ",".join(names[i] for i in fam) + "\n"
    return out


def generate():
    d = g.Disasm("red")
    fams = families(edges_disasm(d))
    return render(fams), fams


def check():
    errors = []

    def err(msg):
        errors.append(msg)
        print("MISMATCH:", msg)

    text, fams = generate()
    if not OUT.exists():
        err("%s does not exist" % OUT)
    elif not g.compare_text(OUT, text):
        err("%s differs from what the disassembly derives (run the script without --check to rewrite it)" % OUT.name)
    names = g.natdex_names()
    if sorted(names) != list(range(1, 152)):
        err("natdex/species.tsv does not name dex numbers 1..151")
    # the shipped file: every name resolves through species.tsv, no species twice
    by_name = {v: k for k, v in names.items()}
    seen = set()
    if OUT.exists():
        for line in g.read_text(OUT).splitlines():
            if not line or line.startswith("#"):
                continue
            for n in line.split(","):
                if n not in by_name:
                    err("name does not resolve through natdex/species.tsv: %r" % n)
                elif by_name[n] in seen:
                    err("species on two lines: %s" % n)
                else:
                    seen.add(by_name[n])
    summary = []
    ref = None
    for build in ("red", "blue", "yellow"):
        d = g.Disasm(build)
        rom = g.Rom(build)
        rom.verify_build()
        # the internal id -> dex number order: every dex number 1..151 appears once, the ROM table equals the disassembly's
        dex_d, dex_r = d.dex_of_internal(), rom.dex_of_internal()
        if dex_d != dex_r:
            err("%s: PokedexOrder differs between the ROM and the disassembly" % build)
        if sorted(v for v in dex_d.values() if v) != list(range(1, 152)):
            err("%s: PokedexOrder does not hold dex numbers 1..151 once each" % build)
        # the evolution edges: ROM against disassembly, and the same for all three games
        e_d, e_r = edges_disasm(d), edges_rom(rom)
        if e_d != e_r:
            err("%s: evolution edges differ between the ROM and evos_moves.asm: %s" % (build, sorted(e_d ^ e_r)))
        # the disassembly's per-species records (method and level or item id) equal the ROM's too
        it = d.item_consts()
        rom_evos = rom.evos()
        for internal, evs in d.evos().items():
            want = []
            for m, a, t in evs:
                want.append((m, it[a] if m == "item" else a, t))
            if want != rom_evos[internal]:
                err("%s: evolution record of internal id %d differs (%s vs %s)" % (build, internal, want, rom_evos[internal]))
        f = families(e_r)
        if f != fams:
            err("%s: families from the ROM differ from the shipped ones" % build)
        if ref is None:
            ref = e_r
        elif e_r != ref:
            err("%s: evolution edges differ from Red's" % build)
        summary.append("%s: %d evolution edges, %d lines, PokedexOrder ok" % (build, len(e_r), len(f)))
    print("\n".join(summary))
    if errors:
        print("FAILED: %d mismatch(es)" % len(errors))
        return 1
    print("OK: families-gen1.tsv matches the disassemblies and the Red, Blue and Yellow ROM dumps (%d lines, %d species on a line)" % (
        len(fams), sum(len(f) for f in fams)))
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true", help="verify the shipped file against the sources, do not write")
    ap.add_argument("--stdout", action="store_true", help="print the file instead of writing it")
    a = ap.parse_args()
    if a.check:
        return check()
    text, _ = generate()
    if a.stdout:
        sys.stdout.write(text)
    else:
        g.write_lf(OUT, text)
        print("wrote", OUT, "(%d lines)" % text.count("\n"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
