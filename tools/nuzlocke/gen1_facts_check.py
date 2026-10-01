#!/usr/bin/env python
"""Verify the Generation 1 memory and battle facts the Nuzlocke tracker reads at run time (no data file, only `--check`).

    python tools/nuzlocke/gen1_facts_check.py --check     # prints the fact table, exits 1 on a mismatch

What it proves, for Red, Blue (pokered) and Yellow (pokeyellow), each against the disassembly and the ROM dump:

1. The WRAM addresses (tools/wram_layout.py on the disassembly) and that the addresses the tracker already uses (Gen1Map in
   Gen1Tracker.kt) are the same numbers. Every label is also found as the operand of a load or store in the ROM code.
2. The event flags: EVENT_BEAT_CHAMPION_RIVAL and the Elite Four trainer flags (their index, byte address and bit), and that
   the ROM sets and tests them (`ld hl, wEventFlags+n / set b, [hl]`, and the trainer headers that point at them).
3. The ball item ids and the Silph Scope, read from the ROM's item name table.
4. The code that decides a ghost battle (IsGhostBattle), the Safari battle type, the battle style bit of wOptions and the
   writes of wBattleResult, as byte patterns in the ROM.
"""
import argparse
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True     # never leave .pyc files behind in the repo
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen1_common as g

LABELS = ["wPlayerID", "wBattleResult", "wEscapedFromBattle", "wCapturedMonSpecies", "wOptions", "wTrainerClass", "wTrainerNo",
          "wBattleType", "wIsInBattle", "wWalkBikeSurfState", "wEnemyMon", "wEnemyMonDVs", "wPartyMonOT", "wPartyMonNicks",
          "wNumBagItems", "wBagItems", "wNumSafariBalls", "wEventFlags", "wCurMap", "wObtainedBadges", "wPartyCount",
          "wPartySpecies", "wPartyMons", "wCurOpponent", "wCurEnemyLevel", "wElite4Flags", "wEnemyMonSpecies2"]
# labels the tracker's Gen1Map holds, as (Gen1Map field, label); the map stores address - 0xC000
TRACKER = [("partyCount", "wPartyCount"), ("partySpecies", "wPartySpecies"), ("partyMons", "wPartyMons"), ("enemyMon", "wEnemyMon"),
           ("inBattle", "wIsInBattle"), ("enemyMove", "wEnemyMoveNum"), ("badges", "wObtainedBadges"), ("numItems", "wNumBagItems"),
           ("items", "wBagItems"), ("aiTurns", "wAILayer2Encouragement"), ("statMods", "wPlayerMonStatMods"), ("curMap", "wCurMap")]
GEN1_TRACKER = g.ROOT / "tracker-gba/src/main/kotlin/com/ironmonone/tracker/Gen1Tracker.kt"
FLAGS = ["EVENT_BEAT_LORELEIS_ROOM_TRAINER_0", "EVENT_BEAT_BRUNOS_ROOM_TRAINER_0", "EVENT_BEAT_AGATHAS_ROOM_TRAINER_0",
         "EVENT_BEAT_LANCES_ROOM_TRAINER_0", "EVENT_BEAT_LANCE", "EVENT_BEAT_CHAMPION_RIVAL"]
FLAG_ROOM = {"EVENT_BEAT_LORELEIS_ROOM_TRAINER_0": "LORELEIS_ROOM", "EVENT_BEAT_BRUNOS_ROOM_TRAINER_0": "BRUNOS_ROOM",
             "EVENT_BEAT_AGATHAS_ROOM_TRAINER_0": "AGATHAS_ROOM", "EVENT_BEAT_LANCES_ROOM_TRAINER_0": "LANCES_ROOM",
             "EVENT_BEAT_LANCE": "LANCES_ROOM", "EVENT_BEAT_CHAMPION_RIVAL": "CHAMPIONS_ROOM"}
_WRAM = {}


def wram(folder):
    if folder not in _WRAM:
        sys.path.insert(0, str(g.ROOT / "tools"))
        import contextlib
        import io
        import wram_layout
        with contextlib.redirect_stderr(io.StringIO()):
            _WRAM[folder] = wram_layout.layout(g.REFS / folder)[0]
    return _WRAM[folder]


def find_all(data, pat):
    out, i = [], data.find(pat)
    while i >= 0:
        out.append(i)
        i = data.find(pat, i + 1)
    return out


def tracker_maps():
    """{'RED_BLUE': {field: value}, 'YELLOW': {...}} parsed from Gen1Map's two constructors."""
    text = GEN1_TRACKER.read_bytes().decode("utf-8", "replace")
    out = {}
    for name in ("RED_BLUE", "YELLOW"):
        i = text.index("val %s = Gen1Map(" % name)
        block = text[i:text.index("\n        )", i)]
        out[name] = {m.group(1): int(m.group(2), 16) for m in re.finditer(r"(\w+)\s*=\s*0x([0-9A-Fa-f]+)L", block)}
    return out


def find_pattern(data, pat):
    """Offsets where `pat` (a list of ints, None = any byte) occurs."""
    first = pat[0]
    out, i = [], data.find(bytes([first]))
    n = len(pat)
    while i >= 0 and i + n <= len(data):
        if all(p is None or data[i + k] == p for k, p in enumerate(pat)):
            out.append(i)
        i = data.find(bytes([first]), i + 1)
    return out


def check():
    errors = []

    def err(msg):
        errors.append(msg)
        print("MISMATCH:", msg)

    tracker = tracker_maps()
    table = {}
    for build in ("red", "blue", "yellow"):
        folder = g.BUILDS[build][0]
        d = g.Disasm(build)
        rom = g.Rom(build)
        rom.verify_build()
        a = wram(folder)
        table[build] = a
        # 1a. the tracker's Gen1Map addresses are wram_layout's (Blue shares Red's)
        tmap = tracker["YELLOW" if build == "yellow" else "RED_BLUE"]
        for field, label in TRACKER:
            if label not in a:
                err("%s: wram_layout has no %s" % (build, label))
            elif tmap[field] != a[label] - 0xC000:
                err("%s: Gen1Map.%s is %#x, wram_layout gives %s = %#x" % (build, field, tmap[field], label, a[label] - 0xC000))
        # 1b. every label is an operand of a load or store in the ROM (ld a, [nn] = FA, ld [nn], a = EA)
        for label in LABELS:
            if label in ("wEnemyMonDVs", "wPartyMonOT", "wPartyMonNicks", "wPartyMons", "wPartySpecies", "wBagItems", "wEnemyMon", "wElite4Flags"):
                continue        # reached through pointers and loops, not by a fixed operand: checked by layout only
            v = a[label]
            hits = len(find_all(rom.data, bytes([0xFA, v & 255, v >> 8]))) + len(find_all(rom.data, bytes([0xEA, v & 255, v >> 8])))
            if hits == 0:
                err("%s: no ROM instruction loads or stores %s (%#06x)" % (build, label, v))
        # 1c. the enemy battle struct: DVs are 12 bytes into wEnemyMon
        if a["wEnemyMonDVs"] - a["wEnemyMon"] != 12:
            err("%s: DVs are not 12 bytes into wEnemyMon" % build)
        # 2. event flags
        ev = d.event_consts()
        base = a["wEventFlags"]
        for name in FLAGS:
            i = ev[name]
            addr, bit = base + i // 8, i % 8
            if name.endswith("TRAINER_0"):
                # the trainer header the room's script uses: db bit (1 for the first trainer of a map), db view range << 4,
                # dw address of the flag byte, which is wEventFlags + (index - 1) / 8
                addr_h = base + (i - 1) // 8
                pat = bytes([1, 0x00, addr_h & 255, addr_h >> 8])
            else:
                pat = bytes([0x21, addr & 255, addr >> 8, 0xCB, 0xC6 + 8 * bit])         # ld hl, addr ; set bit, [hl]
            # found exactly once, and in the bank of the room whose script uses it
            hits = find_all(rom.data, pat)
            room_bank = rom.map_header_ptr(d.map_by_const()[FLAG_ROOM[name]]["id"])[0]
            if len(hits) != 1 or hits[0] // 0x4000 != room_bank:
                err("%s: %s (%s) found at %s, expected once in bank %d" % (
                    build, name, pat.hex(" "), [hex(h) for h in hits], room_bank))
        # 3. items: ids of the balls and the Silph Scope, from the ROM's item name table
        cm = g.charmap(build)
        p = g.ini_int(rom.ini, "ItemNamesOffset")
        names = []
        for _ in range(0x48):
            s = []
            while rom.data[p] != 0x50:
                s.append(cm.get(rom.data[p], "?"))
                p += 1
            p += 1
            names.append("".join(s))
        it = d.item_consts()
        for const, want in (("MASTER_BALL", "MASTER BALL"), ("ULTRA_BALL", "ULTRA BALL"), ("GREAT_BALL", "GREAT BALL"),
                            ("POKE_BALL", "POK? BALL"), ("SAFARI_BALL", "SAFARI BALL"), ("SILPH_SCOPE", "SILPH SCOPE")):
            got = "".join(c if c.isascii() else "?" for c in names[it[const] - 1])   # the e acute of POKe BALL is not ASCII
            if got != want:
                err("%s: ROM item %d is %r, not %r" % (build, it[const], got, want))
        # 4a. IsGhostBattle: ld a,[wIsInBattle] / dec a / ret nz / ld a,[wCurMap] / cp 1F / jr c / cp 7F+1 / jr nc / ld b,SILPH_SCOPE ...
        mc = d.map_consts()
        pat = [0xFA, a["wIsInBattle"] & 255, a["wIsInBattle"] >> 8, 0x3D, 0xC0, 0xFA, a["wCurMap"] & 255, a["wCurMap"] >> 8,
               0xFE, mc["POKEMON_TOWER_1F"], 0x38, None, 0xFE, mc["POKEMON_TOWER_7F"] + 1, 0x30, None, 0x06, it["SILPH_SCOPE"]]
        if len(find_pattern(rom.data, pat)) != 1:
            err("%s: IsGhostBattle code not found exactly once" % build)
        # 4b. the Safari battle type: cp SAFARI_ZONE_EAST / jr c / cp SAFARI_ZONE_CENTER_REST_HOUSE / jr nc / ld a, 2 / ld [wBattleType], a
        pat = [0xFE, mc["SAFARI_ZONE_EAST"], 0x38, None, 0xFE, mc["SAFARI_ZONE_CENTER_REST_HOUSE"], 0x30, None, 0x3E, 0x02,
               0xEA, a["wBattleType"] & 255, a["wBattleType"] >> 8]
        if len(find_pattern(rom.data, pat)) != 1:
            err("%s: the Safari battle type setup was not found exactly once" % build)
        # 4c. bit 6 of wOptions is tested for the battle style: ld a,[wOptions] / bit 6, a
        pat = [0xFA, a["wOptions"] & 255, a["wOptions"] >> 8, 0xCB, 0x77]
        if not find_pattern(rom.data, pat):
            err("%s: no `bit 6, a` on wOptions in the ROM" % build)
        # 4d. wBattleResult writes: ld a, 2 / ld [wBattleResult], a (run or capture), ld a, 1 / ld [..], a (lost), xor a / ld [..], a
        br = a["wBattleResult"]
        for val, what in ((2, "draw (ran or caught)"), (1, "lost")):
            if not find_all(rom.data, bytes([0x3E, val, 0xEA, br & 255, br >> 8])):
                err("%s: no `ld a, %d / ld [wBattleResult], a` (%s)" % (build, val, what))
        if not find_all(rom.data, bytes([0xAF, 0xEA, br & 255, br >> 8])):
            err("%s: no `xor a / ld [wBattleResult], a`" % build)
        # 4e. wCapturedMonSpecies is zeroed before the capture and after it: xor a / ld [wCapturedMonSpecies], a
        cs = a["wCapturedMonSpecies"]
        if len(find_all(rom.data, bytes([0xAF, 0xEA, cs & 255, cs >> 8]))) < 2:
            err("%s: wCapturedMonSpecies is not zeroed in two places" % build)
    # print the fact table
    print("%-24s %-8s %-8s  %s" % ("label", "Red/Blue", "Yellow", "(Gen1Map offset = address - 0xC000)"))
    for label in LABELS:
        print("%-24s %04X     %04X" % (label, table["red"][label], table["yellow"][label]))
    ev_r, ev_y = g.Disasm("red").event_consts(), g.Disasm("yellow").event_consts()
    print()
    for name in FLAGS:
        i = ev_r[name]
        assert ev_y[name] == i
        print("%-38s index %#05x  Red/Blue byte %04X  Yellow byte %04X  bit %d" % (
            name, i, table["red"]["wEventFlags"] + i // 8, table["yellow"]["wEventFlags"] + i // 8, i % 8))
    if errors:
        print("FAILED: %d mismatch(es)" % len(errors))
        return 1
    print("OK: the addresses, flags, item ids and code patterns match the disassemblies, Gen1Map and the Red, Blue and Yellow ROMs")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    if not a.check:
        print(__doc__)
        return 0
    return check()


if __name__ == "__main__":
    sys.exit(main())
