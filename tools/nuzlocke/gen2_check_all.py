"""Run every Generation 2 Nuzlocke data check (2026-09-29) and print the last line of each.

    python tools/nuzlocke/gen2_check_all.py

Checks the five shipped files (areas, trainers, levelcaps, families, statics) against the pret disassemblies and the Gold (U)
and Crystal (U) dumps, the trainer-table walk, and the memory facts. Exit status 1 if any check fails. The dumps and the
disassemblies are found through gen2_common.py (NZ_REFS, NZ_ROMS_VENDOR and NZ_ROMS_SCRATCH move them).
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPTS = ["gen2_trainer_rom.py", "gen2_areas.py", "gen2_trainers.py", "gen2_levelcaps.py", "gen2_families.py",
           "gen2_statics.py", "gen2_facts.py"]


def main():
    failed = 0
    for name in SCRIPTS:
        proc = subprocess.run([sys.executable, os.path.join(HERE, name), "--check"], capture_output=True, text=True)
        lines = [l for l in (proc.stdout + proc.stderr).strip().split("\n") if l.strip()]
        print("%-22s exit %d  %s" % (name, proc.returncode, lines[-1] if lines else ""))
        if proc.returncode != 0:
            failed += 1
            for l in lines[-15:]:
                print("    " + l)
    print("ALL OK" if not failed else "%d of %d checks FAILED" % (failed, len(SCRIPTS)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
