"""Prove that the Generation 2 checks can fail (2026-09-29): tamper with a COPY of the shipped data or of a ROM dump, run the
check that should notice, and expect a non-zero exit. A check that stays green over a broken input is worse than no check.

    python tools/nuzlocke/gen2_check_can_fail.py

Nothing in the repo or in the dumps is touched: the copies live in a temporary folder (TMPDIR or TEMP moves it). The ROM cases
set NZ_ALLOW_DIRTY_ROM so a changed dump gets past the SHA-1 test and reaches the comparison being tested.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen2_common as C  # noqa: E402

CASES = []


def case(name, script, mutate_data=None, mutate_rom=None):
    CASES.append((name, script, mutate_data, mutate_rom))


def edit_line(path, contains, old, new):
    text = open(path, "rb").read().decode("ascii")
    lines = text.split("\r\n")
    hits = [i for i, l in enumerate(lines) if contains in l and not l.startswith("#")]
    assert hits, "no line contains %r in %s" % (contains, path)
    before = lines[hits[0]]
    lines[hits[0]] = before.replace(old, new, 1)
    assert lines[hits[0]] != before, "%r not found in %r" % (old, before)
    open(path, "wb").write("\r\n".join(lines).encode("ascii"))


def patch_rom(path, offset, value):
    b = bytearray(open(path, "rb").read())
    b[offset] = value
    open(path, "wb").write(bytes(b))


# ---- shipped data tampered
case("levelcaps: Falkner's cap 9 -> 10", "gen2_levelcaps.py",
     lambda d: edit_line(d + "/levelcaps-gen2.tsv", "\tFalkner\t", "\t9\tPidgeotto", "\t10\tPidgeotto"))
case("levelcaps: Chuck and Jasmine badge bits swapped back to the game's raw order", "gen2_levelcaps.py",
     lambda d: edit_line(d + "/levelcaps-gen2.tsv", "\tChuck\t", "\t7:1\t4", "\t7:1\t5"))
case("levelcaps: Clair's class number wrong", "gen2_levelcaps.py",
     lambda d: edit_line(d + "/levelcaps-gen2.tsv", "\tClair\t", "\t8:1\t", "\t9:1\t"))
case("trainers: Petrel's fight relabelled as Ariana", "gen2_trainers.py",
     lambda d: edit_line(d + "/trainers-gen2.tsv", "Executive Petrel, Radio Tower", "Petrel", "Ariana"))
case("trainers: a class loses its no 0 row", "gen2_trainers.py",
     lambda d: edit_line(d + "/trainers-gen2.tsv", "\tSailor\t", "\t40\t0\tSailor", "\t40\t1\tSailor"))
case("areas: Union Cave B2F moved to Route 33", "gen2_areas.py",
     lambda d: edit_line(d + "/areas-gen2.tsv", "Union Cave B2F", "Union Cave", "Route 33"))
case("areas: a Crystal landmark renamed", "gen2_areas.py",
     lambda d: edit_line(d + "/areas-gen2.tsv", "\tSilver Cave\t", "Silver Cave", "Mt. Silver"))
case("families: Slowking dropped from the Slowpoke line", "gen2_families.py",
     lambda d: edit_line(d + "/families-gen2.tsv", "Slowpoke,Slowbro", ",Slowking", ""))
case("statics: Sudowoodo's level 20 -> 21", "gen2_statics.py",
     lambda d: edit_line(d + "/statics-gen2.tsv", "Sudowoodo", "\t20\t", "\t21\t"))
case("statics: Lapras loses its dex number (its level 20 collides with Union Cave's ordinary slots)", "gen2_statics.py",
     lambda d: edit_line(d + "/statics-gen2.tsv", "\tUnion Cave\t20\tLapras\t", "ordinary battle type\t131", "ordinary battle type"))
case("statics: a row gets a wrong dex number", "gen2_statics.py",
     lambda d: edit_line(d + "/statics-gen2.tsv", "\tWhirl Islands\t40\tLugia\t", "\t249", "\t250"))
# ---- ROM dumps tampered
GOLD, CRYSTAL = "gs", "c"
case("Gold ROM: Falkner's Pidgeotto level 9 -> 10 (trainer walk)", "gen2_trainer_rom.py", None, (GOLD, "falkner", None))
case("Gold ROM: a map header's landmark byte changed", "gen2_areas.py", None, (GOLD, "maphdr", None))
case("Crystal ROM: Sudowoodo's level byte 20 -> 21", "gen2_statics.py", None, (CRYSTAL, 0x194069, 21))
case("Crystal ROM: Bulbasaur's evolution target changed", "gen2_families.py", None, (CRYSTAL, "evo", None))
case("Gold ROM: Falkner's top level changed (levelcaps ROM comparison)", "gen2_levelcaps.py", None, (GOLD, "falkner", None))


def run(script, env):
    """(exit code, the check's own complaint or None): a crash (a traceback) is not a check noticing anything."""
    proc = subprocess.run([sys.executable, os.path.join(HERE, script), "--check"], capture_output=True, text=True, env=env)
    out = proc.stdout + proc.stderr
    if "Traceback" in out:
        return proc.returncode, None
    lines = [l for l in out.split("\n") if l.startswith("MISMATCH:") or l.startswith("FAIL:")]
    return proc.returncode, (lines[0][:110] if lines else None)


def main():
    failures = 0
    for name, script, mutate_data, mutate_rom in CASES:
        tmp = tempfile.mkdtemp(prefix="nzg2_")
        try:
            env = dict(os.environ)
            data = tmp + "/nuzlocke"
            shutil.copytree(C.NZ, data)
            env["NZ_DATA"] = data
            if mutate_data:
                mutate_data(data)
            if mutate_rom:
                game, where, value = mutate_rom
                rom_dir = tmp + "/roms"
                os.makedirs(rom_dir)
                rom = rom_dir + ("/gold-u.gbc" if game == GOLD else "/crystal-u.gbc")
                shutil.copy(C.ROM_PATH[game], rom)
                env["NZ_ALLOW_DIRTY_ROM"] = "1"
                env["NZ_ROMS_SCRATCH" if game == GOLD else "NZ_ROMS_VENDOR"] = rom_dir
                if where == "maphdr":
                    ini = C.ini_section("Gold (U)")
                    table = int(ini["MapHeaders"], 16)
                    ptr = open(rom, "rb").read()[table + 2] | (open(rom, "rb").read()[table + 3] << 8)
                    where = (table // 0x4000) * 0x4000 + ptr - 0x4000 + 5          # the first map of group 2: its location byte
                    value = 200
                elif where == "evo":
                    ini = C.ini_section("Crystal (U)")
                    table = int(ini["PokemonMovesetsTableOffset"], 16)
                    data_rom = open(rom, "rb").read()
                    ptr = data_rom[table] | (data_rom[table + 1] << 8)
                    where = (table // 0x4000) * 0x4000 + ptr - 0x4000 + 2          # Bulbasaur's record: EVOLVE_LEVEL, 16, IVYSAUR
                    value = 4                                                       # ... now Charmander
                elif where == "falkner":
                    import gen2_trainer_rom as R
                    blob = open(rom, "rb").read()
                    start = R.class_start(blob, "gs", 1)
                    party_name = R.party_at(blob, start)[0]
                    where = start + len(party_name) + 2 + 6                           # the second Pokemon's level byte (9)
                    value = blob[where] + 1
                elif value is None:
                    # a trainer level: the byte at where is a Pokemon's level in Falkner's party (level 7 Pidgey, then level 9)
                    value = open(rom, "rb").read()[where] + 1
                patch_rom(rom, where, value)
            code, complaint = run(script, env)
            ok = code != 0 and complaint is not None
            print("%s  %s (%s exit %d)%s" % ("red as expected" if ok else "NOT NOTICED   ", name, script, code,
                                               "" if complaint is None else "\n                   -> " + complaint))
            failures += 0 if ok else 1
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    # the collision test itself, not through a file comparison
    import gen2_statics as S
    ok = bool(S.collisions("gs", "Union Cave", 20)) and not S.collisions("gs", "Route 36", 20) and \
        bool(S.collisions("s", "Whirl Islands", 40)) and not S.collisions("g", "Whirl Islands", 70)
    print("%s  the collision test: Union Cave 20 and Silver Whirl Islands 40 collide, Route 36 20 and Gold Whirl Islands 70 do not" % (
        "red as expected" if ok else "BROKEN       "))
    failures += 0 if ok else 1
    print("every check can fail" if not failures else "%d checks did not notice" % failures)
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
