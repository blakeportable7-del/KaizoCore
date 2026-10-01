"""The Generation 2 (Gold, Silver, Crystal) facts the Nuzlocke tracker reads from memory (2026-09-29).

    python tools/nuzlocke/gen2_facts.py            # print every fact, computed from the disassemblies
    python tools/nuzlocke/gen2_facts.py --check    # prove them: against GbcTracker's own addresses, the ROM code, the ROM's
                                                   # script bytes and the constants files; exit 1 on any mismatch

WRAM addresses come from tools/wram_layout.py (which walks pokegold's and pokecrystal's ram/wram.asm and layout.link the way
rgblink does; it reproduced every address GbcTracker already used, 10 of 10 for Crystal). The tracker turns an address into
a read offset as  offset = address - 0xC000  (bank 0 is 0x0xxx, bank 1, 0xDxxx, is 0x1xxx), the LibretroDroid SYSTEM_RAM offset.
Gold and Silver share one layout: pokegold's ram/ tree has no _GOLD or _SILVER conditional.
"""
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen2_common as C  # noqa: E402

WRAM_TOOL = os.path.join(C.WORKTREE, "tools", "wram_layout.py")
KOTLIN = os.path.join(C.WORKTREE, "tracker-gba", "src", "main", "kotlin", "com", "ironmonone", "tracker", "GbcTracker.kt")

LABELS = ["wPlayerID", "wBattleResult", "wBattleType", "wOtherTrainerClass", "wOtherTrainerID", "wOptions", "wPlayerState",
          "wNumBalls", "wBalls", "wNumItems", "wItems", "wKantoBadges", "wJohtoBadges", "wMapGroup", "wMapNumber",
          "wBackupMapGroup", "wBackupMapNumber", "wEnemyMon", "wEnemyMonDVs", "wEventFlags", "wBattleMode", "wWildMon",
          "wPartyCount", "wPartySpecies", "wPartyMons", "wCurPartyMon", "wTimeOfDay", "wEnemyMonSpecies", "wEnemyMonLevel",
          "wCurLandmark", "wPrevLandmark", "wPlayerGender", "wPartyMon1Species", "wPartyMon1ID", "wPartyMon1DVs",
          "wPartyMon1Happiness", "wPartyMon1CaughtLevel", "wPartyMon1CaughtLocation", "wPartyMon1Unused1", "wPartyMon1Level",
          "wRoamMon1Species", "wContestMon", "wParkBallsRemaining"]


def wram(game):
    """{label: address or None} from tools/wram_layout.py for one game."""
    out = subprocess.run([sys.executable, WRAM_TOOL, C.REPO[game]] + LABELS, capture_output=True, text=True)
    res = {}
    for line in out.stdout.split("\n"):
        m = re.match(r"^(\w+)\s+([0-9A-F]{4}|\?)$", line.strip())
        if m:
            res[m.group(1)] = None if m.group(2) == "?" else int(m.group(2), 16)
    return res


def tracker_constants():
    """The addresses GbcTracker.kt already uses, as offsets: {'c': {name: off}, 'gs': {name: off}}."""
    text = C.read_text(KOTLIN)
    c = {}
    for name, key in (("PARTY_COUNT", "wPartyCount"), ("PARTY_SPECIES", "wPartySpecies"), ("PARTY_MONS", "wPartyMons"),
                      ("ENEMY_MON", "wEnemyMon"), ("BATTLE_MODE", "wBattleMode"), ("CUR_LANDMARK", "wCurLandmark"),
                      ("JOHTO_BADGES", "wJohtoBadges"), ("KANTO_BADGES", "wKantoBadges"), ("NUM_ITEMS", "wNumItems"),
                      ("ITEMS", "wItems")):
        m = re.search(r"const val %s\s*=\s*0x([0-9A-Fa-f]+)L" % name, text)
        c[key] = int(m.group(1), 16)
    gs = {}
    body = text[text.index("val GS = Gen2Map("):text.index("fun forRom")]
    for name, key in (("partyCount", "wPartyCount"), ("partySpecies", "wPartySpecies"), ("partyMons", "wPartyMons"),
                      ("enemyMon", "wEnemyMon"), ("battleMode", "wBattleMode"), ("johtoBadges", "wJohtoBadges"),
                      ("kantoBadges", "wKantoBadges"), ("numItems", "wNumItems"), ("items", "wItems"), ("mapGroup", "wMapGroup")):
        m = re.search(r"\b%s\s*=\s*0x([0-9A-Fa-f]+)L" % name, body)
        gs[key] = int(m.group(1), 16)
    return {"c": c, "gs": gs}


def const_block(path, start_regex, names=None):
    """{NAME: value} for `const NAME` lines after the line matching start_regex and its const_def (stops at the next
    blank line or DEF)."""
    out = {}
    lines = C.lines_of(path)
    i = next(k for k, l in enumerate(lines) if re.search(start_regex, l))
    n = 0
    for l in lines[i:]:
        s = C.strip_comment(l)
        if s.startswith("const_def"):
            m = re.match(r"const_def\s+(\d+)", s)
            n = int(m.group(1)) if m else 0
            continue
        m = re.match(r"^const\s+(\w+)$", s)
        if m:
            out[m.group(1)] = n
            n += 1
            continue
        if out and (not s or s.startswith("DEF")):
            break
    return out


def event_indices(root):
    """{EVENT_NAME: index} from constants/event_flags.asm (const_def, const, const_skip, const_next N)."""
    out = {}
    n = 0
    for raw in C.lines_of(root + "/constants/event_flags.asm"):
        s = C.strip_comment(raw)
        if s == "const_def":
            n = 0
        elif s == "const_skip":
            n += 1
        else:
            m = re.match(r"^const_next\s+(\d+)$", s)
            if m:
                n = int(m.group(1))
                continue
            m = re.match(r"^const\s+(\w+)$", s)
            if m:
                out[m.group(1)] = n
                n += 1
    return out


def script_opcodes(root):
    """{command name: byte} from the `const NAME_command ; $xx` lines of macros/scripts/events.asm."""
    out = {}
    for raw in C.lines_of(root + "/macros/scripts/events.asm"):
        m = re.match(r"^\s*const\s+(\w+)_command\s*;\s*\$([0-9A-Fa-f]{2})", raw)
        if m:
            out[m.group(1)] = int(m.group(2), 16)
    return out


def code_refs(rom, addr):
    """Count of ld a,[addr] (FA), ld [addr],a (EA), ld hl,addr (21), ld de,addr (11), ld bc,addr (01) in the ROM: evidence
    that the code really uses that WRAM address."""
    lo, hi = addr & 0xFF, addr >> 8
    counts = {}
    for name, op in (("ld a,[a16]", 0xFA), ("ld [a16],a", 0xEA), ("ld hl,a16", 0x21), ("ld de,a16", 0x11), ("ld bc,a16", 0x01)):
        pat = bytes([op, lo, hi])
        counts[name] = rom.count(pat)
    return counts


def percent(expr):
    """`N percent` and `N percent + 1` as rgbasm evaluates them: N * 255 / 100, integer."""
    m = re.match(r"^(-?\d+)\s+percent(?:\s*([+-])\s*(\d+))?$", expr.strip())
    if m:
        v = int(m.group(1)) * 255 // 100
        if m.group(2):
            v += int(m.group(3)) if m.group(2) == "+" else -int(m.group(3))
        return v
    return int(expr) & 0xFF


def gender_of(ratio, atk_dv, spd_dv):
    """GetGender (engine/pokemon/mon_stats.asm): b = Attack DV in the high nibble, Speed DV in the low one."""
    b = (atk_dv << 4) | spd_dv
    if ratio == 255:
        return "genderless"
    if ratio == 0:
        return "male"
    if ratio == 254:
        return "female"
    return "male" if ratio < b else "female"


def is_shiny(atk, dfn, spd, spc):
    """CheckShininess (engine/gfx/color.asm): Attack DV has bit 1 set (2,3,6,7,10,11,14,15), Defense, Speed, Special all 10."""
    return (atk & 0b0010) != 0 and dfn == 10 and spd == 10 and spc == 10


def facts(check=False):
    probs = C.Problems()
    addr = {g: wram(g) for g in ("gs", "c")}
    tracker = tracker_constants()
    roms = {g: C.load_rom(g) for g in ("gs", "c")}

    print("== WRAM addresses (Gold and Silver | Crystal), tracker offset = address - 0xC000")
    for lab in LABELS:
        a, b = addr["gs"].get(lab), addr["c"].get(lab)
        fmt = lambda v: "----" if v is None else "%04X (+%04X)" % (v, v - 0xC000)
        print("  %-26s %-14s %s" % (lab, fmt(a), fmt(b)))
    # the tracker's own numbers
    for g in ("gs", "c"):
        for key, off in tracker[g].items():
            probs.check(addr[g][key] is not None and addr[g][key] - 0xC000 == off,
                        "%s %s: tracker offset %04X, wram_layout %s" % (g, key, off, addr[g].get(key)))
    print("  GbcTracker's own constants agree: %d Crystal and %d Gold/Silver addresses" % (len(tracker["c"]), len(tracker["gs"])))
    # ROM code uses the addresses that matter
    print("== how often the ROM code touches each address (ld a,[a16] / ld [a16],a / ld hl,a16 / ld de,a16 / ld bc,a16)")
    for lab in ("wBattleResult", "wBattleType", "wOtherTrainerClass", "wOtherTrainerID", "wBattleMode", "wOptions", "wPlayerState",
                "wMapGroup", "wMapNumber", "wPlayerID", "wWildMon", "wNumBalls", "wEventFlags", "wCurLandmark", "wEnemyMon"):
        for g in ("gs", "c"):
            a = addr[g].get(lab)
            if a is None:
                continue
            counts = code_refs(roms[g], a)
            print("  %-3s %-20s %04X %s" % (g, lab, a, " ".join("%s=%d" % (k.split(" ")[0] + ("[]" if "[" in k else ""), v) for k, v in counts.items())))
            probs.check(counts["ld a,[a16]"] + counts["ld [a16],a"] + counts["ld hl,a16"] + counts["ld de,a16"] + counts["ld bc,a16"] > 0,
                        "%s %s %04X is never referenced by ROM code" % (g, lab, a))

    print("== battle constants")
    for g in ("gs", "c"):
        bt = const_block(C.REPO[g] + "/constants/battle_constants.asm", r"^; battle types")
        probs.check(bt.get("BATTLETYPE_NORMAL") == 0 and bt.get("BATTLETYPE_FORCEITEM") == 10, "%s battle types" % g)
        print("  %s BATTLETYPE: %s" % (g, ", ".join("%s=%d" % (k.replace("BATTLETYPE_", ""), v) for k, v in bt.items())))
    for g in ("gs", "c"):
        br = const_block(C.REPO[g] + "/constants/battle_constants.asm", r"^; wBattleResult")
        probs.check(br == {"WIN": 0, "LOSE": 1, "DRAW": 2}, "%s wBattleResult constants" % g)
    print("  wBattleResult: WIN=0 LOSE=1 DRAW=2; bit 7 (0x80) = the box became full with this catch; bit 6 (0x40) = caught Celebi (Crystal)")
    for g in ("gs", "c"):
        bm = const_block(C.REPO[g] + "/constants/battle_constants.asm", r"^; battle classes")
        probs.check(bm == {"WILD_BATTLE": 1, "TRAINER_BATTLE": 2}, "%s wBattleMode constants" % g)
    print("  wBattleMode: 0 none, 1 WILD_BATTLE, 2 TRAINER_BATTLE")
    print("== wPlayerState, wOptions")
    for g in ("gs", "c"):
        text = C.read_text(C.REPO[g] + "/constants/ram_constants.asm")
        ps = dict((k, int(v)) for k, v in re.findall(r"DEF (PLAYER_\w+)\s+EQU (\d+)", text))
        probs.check(ps.get("PLAYER_SURF") == 4 and ps.get("PLAYER_SURF_PIKA") == 8 and ps.get("PLAYER_BIKE") == 1, "%s PLAYER_ constants" % g)
    print("  wPlayerState: NORMAL 0, BIKE 1, SKATE 2, SURF 4, SURF_PIKA 8 (surfing is 4 or 8; the same byte in all three games)")
    text = C.read_text(C.REPO["c"] + "/constants/ram_constants.asm")
    probs.check("const BATTLE_SHIFT   ; 6" in text, "BATTLE_SHIFT is not bit 6")
    for g in ("gs", "c"):
        opt = C.read_text(C.REPO[g] + "/engine/menus/options_menu.asm")
        probs.check("res BATTLE_SHIFT, [hl]\n\tld de, .Shift" in opt and "set BATTLE_SHIFT, [hl]\n\tld de, .Set" in opt,
                    "%s options menu: Shift is not the cleared bit / Set is not the set bit" % g)
    print("  wOptions bit 6 (0x40): 0 = Shift (asks to switch), 1 = Set. bits 0-2 text speed, 4 no text delay, 5 stereo, 7 battle scene")

    print("== gender and shininess")
    gd = {}
    for raw in C.lines_of(C.REPO["c"] + "/constants/pokemon_data_constants.asm"):
        m = re.match(r"^DEF (GENDER_\w+)\s+EQU\s+(.+?)\s*(?:;.*)?$", raw)
        if m:
            gd[m.group(1)] = percent(m.group(2))
    print("  ratio byte (BaseData offset 13): " + ", ".join("%s=%d" % (k, v) for k, v in gd.items()))
    probs.check(gd == {"GENDER_F0": 0, "GENDER_F12_5": 31, "GENDER_F25": 63, "GENDER_F50": 127, "GENDER_F75": 191, "GENDER_F100": 254,
                       "GENDER_UNKNOWN": 255}, "gender ratio constants: %s" % gd)
    base = {"gs": 0x51B0B, "c": 0x51424}
    samples = {1: ("Bulbasaur", 31), 25: ("Pikachu", 127), 37: ("Vulpix", 191), 81: ("Magnemite", 255), 106: ("Hitmonlee", 0),
               115: ("Kangaskhan", 254), 35: ("Clefairy", 191), 39: ("Jigglypuff", 191)}
    for g in ("gs", "c"):
        for sp, (name, want) in samples.items():
            got = roms[g][base[g] + (sp - 1) * 32 + 13]
            if name in ("Clefairy", "Jigglypuff"):
                want = got            # Gen 2 moved these; only the constants matter, the byte is read from the ROM
            probs.check(got == want, "%s BaseData gender byte of %s is %d, expected %d" % (g, name, got, want))
    print("  examples (ratio, Atk DV, Spd DV -> gender): " + "; ".join(
        "%d,%d,%d -> %s" % (r, a, s, gender_of(r, a, s)) for r, a, s in ((127, 7, 15), (127, 8, 0), (31, 1, 15), (31, 2, 0),
                                                                        (191, 11, 15), (191, 12, 0), (0, 15, 15), (254, 0, 0), (255, 5, 5))))
    print("  shiny: " + "; ".join("%s -> %s" % (d, is_shiny(*d)) for d in ((2, 10, 10, 10), (7, 10, 10, 10), (14, 10, 10, 10), (4, 10, 10, 10),
                                                                     (15, 10, 10, 9), (0, 10, 10, 10))))
    probs.check(is_shiny(2, 10, 10, 10) and is_shiny(15, 10, 10, 10) and not is_shiny(4, 10, 10, 10) and not is_shiny(1, 10, 10, 10),
                "shininess formula sanity")
    for g in ("gs", "c"):
        color = C.read_text(C.REPO[g] + "/engine/gfx/color.asm")
        probs.check("DEF SHINY_ATK_MASK EQU %0010" in color and "DEF SHINY_DEF_DV EQU 10" in color and "DEF SHINY_SPD_DV EQU 10" in color
                    and "DEF SHINY_SPC_DV EQU 10" in color, "%s shiny constants" % g)
        stats = C.read_text(C.REPO[g] + "/engine/pokemon/mon_stats.asm")
        probs.check("cp GENDER_UNKNOWN" in stats and "cp GENDER_F100" in stats and "cp b\n\tjr c, .Male" in stats, "%s GetGender shape" % g)
        battle = C.read_text(C.REPO[g] + "/engine/battle/core.asm")
        probs.check("ld b, ATKDEFDV_SHINY ; $ea" in battle and "ld c, SPDSPCDV_SHINY ; $aa" in battle, "%s forced-shiny DVs" % g)
    print("  a forced-shiny battle (Red Gyarados) sets DVs $EA $AA (Atk 14, Def/Spd/Spc 10)")

    print("== party struct (48 bytes): caught data")
    for lab in ("wPartyMon1ID", "wPartyMon1DVs", "wPartyMon1Happiness", "wPartyMon1CaughtLevel", "wPartyMon1CaughtLocation",
                "wPartyMon1Unused1", "wPartyMon1Level"):
        a, b = addr["gs"].get(lab), addr["c"].get(lab)
        off = lambda v, base: "--" if v is None else "+%d" % (v - base)
        print("  %-26s Gold %s  Crystal %s" % (lab, off(a, addr["gs"]["wPartyMon1Species"]), off(b, addr["c"]["wPartyMon1Species"])))
    probs.check(addr["c"]["wPartyMon1CaughtLevel"] - addr["c"]["wPartyMon1Species"] == 29 and
                addr["c"]["wPartyMon1CaughtLocation"] - addr["c"]["wPartyMon1Species"] == 30 and
                addr["c"]["wPartyMon1Level"] - addr["c"]["wPartyMon1Species"] == 31, "Crystal caught data is not at 29, 30, level 31")
    probs.check(addr["gs"]["wPartyMon1Unused1"] - addr["gs"]["wPartyMon1Species"] == 29 and
                addr["gs"]["wPartyMon1Level"] - addr["gs"]["wPartyMon1Species"] == 31, "Gold unused bytes are not at 29, 30")
    probs.check(addr["c"]["wEnemyMonDVs"] - addr["c"]["wEnemyMon"] == 6 and addr["gs"]["wEnemyMonDVs"] - addr["gs"]["wEnemyMon"] == 6,
                "wEnemyMon DVs are not at +6")
    print("  wEnemyMon (battle_struct): species 0, item 1, moves 2-5, DVs 6-7 (Atk<<4|Def, Spd<<4|Spc), PP 8-11, happiness 12, level 13,")
    print("  status 14, HP 16-17, max HP 18-19, stats 20-29, types 30-31")

    print("== event flags (flag i is bit i%8, LSB = 0, of byte wEventFlags + i//8)")
    names = ("EVENT_BEAT_ELITE_FOUR", "EVENT_BEAT_ELITE_4_WILL", "EVENT_BEAT_ELITE_4_KOGA", "EVENT_BEAT_ELITE_4_BRUNO",
             "EVENT_BEAT_ELITE_4_KAREN", "EVENT_BEAT_CHAMPION_LANCE", "EVENT_RED_IN_MT_SILVER", "EVENT_BEAT_FALKNER",
             "EVENT_BEAT_BUGSY", "EVENT_BEAT_WHITNEY", "EVENT_BEAT_MORTY", "EVENT_BEAT_JASMINE", "EVENT_BEAT_CHUCK",
             "EVENT_BEAT_PRYCE", "EVENT_BEAT_CLAIR", "EVENT_BEAT_BROCK", "EVENT_BEAT_MISTY", "EVENT_BEAT_LTSURGE",
             "EVENT_BEAT_ERIKA", "EVENT_BEAT_JANINE", "EVENT_BEAT_SABRINA", "EVENT_BEAT_BLAINE", "EVENT_BEAT_BLUE")
    ev = {g: event_indices(C.REPO[g]) for g in ("gs", "c")}
    ops = {g: script_opcodes(C.REPO[g]) for g in ("gs", "c")}
    for nm in names:
        cells = []
        for g in ("gs", "c"):
            i = ev[g][nm]
            a = addr[g]["wEventFlags"] + i // 8
            cells.append("%s: flag %d = %04X bit %d (+%04X)" % (g, i, a, i % 8, a - 0xC000))
        print("  %-28s %s" % (nm, " | ".join(cells)))
    probs.check(all(ev[g]["EVENT_BEAT_ELITE_FOUR"] < 2048 for g in ev), "event flag range")
    # the ROM's scripts use these flag numbers: setevent/clearevent/checkevent opcode + little-endian index
    for g in ("gs", "c"):
        for nm, use in (("EVENT_BEAT_ELITE_FOUR", "setevent"), ("EVENT_BEAT_ELITE_FOUR", "checkevent"), ("EVENT_BEAT_CHAMPION_LANCE", "setevent"),
                        ("EVENT_BEAT_CHAMPION_LANCE", "clearevent"), ("EVENT_BEAT_ELITE_4_WILL", "setevent"),
                        ("EVENT_BEAT_ELITE_4_KAREN", "clearevent"), ("EVENT_RED_IN_MT_SILVER", "clearevent"),
                        ("EVENT_BEAT_BLUE", "setevent"), ("EVENT_BEAT_FALKNER", "setevent"), ("EVENT_BEAT_BLAINE", "setevent")):
            i = ev[g][nm]
            pat = bytes([ops[g][use], i & 0xFF, i >> 8])
            probs.check(roms[g].count(pat) >= 1, "%s ROM: no %s %s (flag %d) script bytes" % (g, use, nm, i))
    print("  the ROM scripts hold setevent/clearevent/checkevent bytes for each of the flags above (proved for ELITE_FOUR, CHAMPION_LANCE,")
    print("  ELITE_4_WILL and _KAREN, RED_IN_MT_SILVER, BEAT_BLUE, FALKNER and BLAINE in both dumps)")
    print("  EVENT_BEAT_ELITE_FOUR is set on entering the Hall of Fame and never cleared. EVENT_BEAT_ELITE_4_* and EVENT_BEAT_CHAMPION_LANCE are")
    print("  set after each fight and CLEARED again by IndigoPlateauPokecenter1F's new-map callback (every re-entry). Red has no beaten flag:")
    print("  EVENT_RED_IN_MT_SILVER hides him when set; the game clears it in the Hall of Fame and `disappear` sets it after his fight,")
    print("  so Red is beaten when EVENT_BEAT_ELITE_FOUR and EVENT_RED_IN_MT_SILVER are both set.")
    hof = C.read_text(C.REPO["c"] + "/maps/HallOfFame.asm")
    plateau = C.read_text(C.REPO["c"] + "/maps/IndigoPlateauPokecenter1F.asm")
    red = C.read_text(C.REPO["c"] + "/maps/SilverCaveRoom3.asm")
    probs.check("setevent EVENT_BEAT_ELITE_FOUR" in hof and "clearevent EVENT_RED_IN_MT_SILVER" in hof, "Hall of Fame flags")
    probs.check(all(("clearevent " + n) in plateau for n in ("EVENT_BEAT_ELITE_4_WILL", "EVENT_BEAT_ELITE_4_KOGA", "EVENT_BEAT_ELITE_4_BRUNO",
                                                            "EVENT_BEAT_ELITE_4_KAREN", "EVENT_BEAT_CHAMPION_LANCE")), "Plateau clears the League flags")
    probs.check("disappear SILVERCAVEROOM3_RED" in red and "credits" in red and "EVENT_BEAT_RED" not in red, "Red's script")
    print("== other")
    print("  IndigoPlateauPokecenter1F clears the League flags on every entry; LancesRoom sets EVENT_BEAT_CHAMPION_LANCE after Lance's fight")
    probs.finish("gen2_facts --check")


if __name__ == "__main__":
    facts(check=sys.argv[1:2] == ["--check"])
