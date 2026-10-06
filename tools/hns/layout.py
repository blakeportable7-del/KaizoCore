#!/usr/bin/env python3
"""Export the memory layout of a Pokemon Heart & Soul build (pokehns-expansion) for KaizoCore.

Run inside WSL, where the arm-none-eabi toolchain lives, on a build directory that has
been built with `make hns` (pokehns.elf, pokehns.gba and the generated headers present):

    python3 tools/hns/layout.py --src /root/hns/layout-src \
        --out app/src/main/assets/hns/layout-plain.json \
        --species-out app/src/main/assets/hns/species-plain.json

Nothing here is hand-kept. Symbols come from the ELF symbol table. Struct offsets and sizes
come from compiling a probe C file against the project's own headers with the exact
cpp/preproc/cc1/as command line `make -n` prints for a real source file, so every #if in
the headers resolves the way it did for the game. Bitfields (which offsetof cannot take)
are measured by compiling `static const struct S x = { .field = ~0 }` and reading which bits
came out set. Constants (enums and #defines) are evaluated by the compiler too.

The build directory is only read. The probe is compiled in a temporary directory.
"""
import argparse
import json
import os
import re
import shlex
import struct
import subprocess
import sys
import tempfile
import zlib

ROM_BASE = 0x08000000

# Structs to export: (name, source file holding its definition or None for the headers).
STRUCTS = [
    ("SpeciesInfo", None), ("LevelUpMove", None), ("Evolution", None), ("EvolutionParam", None),
    ("FormChange", None), ("MoveInfo", None), ("AbilityInfo", None), ("ItemInfo", None),
    ("TmHmIndexKey", None), ("TypeInfo", None), ("Trainer", None), ("TrainerMon", None),
    ("TrainerClass", None), ("WildPokemon", None), ("WildPokemonInfo", None),
    ("WildEncounterTypes", None), ("WildPokemonHeader", None), ("BoxPokemon", None),
    ("Pokemon", None), ("PokemonSubstruct0", None), ("PokemonSubstruct1", None),
    ("PokemonSubstruct2", None), ("PokemonSubstruct3", None), ("BattlePokemon", None),
    ("Volatiles", None), ("SaveBlock1", None), ("SaveBlock2", None), ("SaveBlock3", None),
    ("ChallengeSettings", None), ("WarpData", None), ("ItemSlot", None), ("Bag", None),
    ("_TrainerBattleParameter", None), ("InGameTrade", "src/trade.c"),
    # The tracker's reads (2026-10-05): battle results and scripting, the map header and its region map entry, and
    # BattleStruct for unableToUseMove (what HITMARKER_UNABLE_TO_USE_MOVE was before the expansion).
    ("BattleResults", None), ("BattleScripting", None), ("MapHeader", None), ("RegionMapLocation", None),
    ("BattleStruct", None),
    # Battle Details and the last-attack line (2026-10-05): the side and field timers, and the per-turn damage taken.
    ("SideTimer", None), ("FieldTimer", None), ("ProtectStruct", None),
    # Hidden items (map bg_events) and the Pickup table, which the presets randomize (2026-10-05).
    ("MapEvents", None), ("BgEvent", None), ("PickupItem", None),
    # The Nuzlocke's statics (2026-10-05): gMain.savedCallback tells a scripted wild battle from a walking one.
    # Play as your Pokemon (2026-10-05): the overworld structs libretrodroid's sprite_core.h reads (kSpriteBytes,
    # kObjectEventBytes, gMain.oamBuffer, gPlayerAvatar's spriteId and objectEventId), so a test can hold its
    # constants to this build's.
    ("Main", None), ("Sprite", None), ("ObjectEvent", None), ("PlayerAvatar", None),
    # The Survival heal counter (2026-10-06): the field script running now and its call stack (tracker-gba HnsHeals).
    ("ScriptContext", None),
]

HEADERS = [
    "global.h", "pokemon.h", "move.h", "item.h", "data.h", "wild_encounter.h", "battle.h",
    "battle_setup.h", "randomizer.h", "constants/items.h", "constants/moves.h",
    "constants/abilities.h", "constants/species.h", "constants/opponents.h",
    "constants/map_groups.h", "constants/flags.h", "constants/vars.h", "constants/trainers.h",
    "constants/pokedex.h", "constants/hold_effects.h", "constants/battle.h", "constants/rtc.h",
    "constants/difficulty.h", "constants/pokemon.h", "constants/pokeball.h",
    "constants/region_map_sections.h", "constants/trainer_types.h", "constants/tms_hms.h",
    "constants/form_change_types.h", "constants/item_effects.h", "pokemon_storage_system.h",
    "region_map.h", "constants/battle_script_commands.h", "battle_script_commands.h", "constants/game_stat.h", "constants/map_types.h", "constants/event_bg.h", "main.h", "sprite.h", "kaizocore_tables.h",
    # The script engine (2026-10-06): the heals outside a Pokemon Center (tracker-gba HnsHeals).
    "script.h",
]

SYMBOLS = [
    # ROM tables
    "gSpeciesInfo", "gMovesInfo", "gAbilitiesInfo", "gItemsInfo", "gTypesInfo",
    "gTypeEffectivenessTable", "gExperienceTables", "gTMHMItemMoveIds", "gTrainers",
    "gBattlePartners", "gTrainerClasses", "gWildMonHeaders", "sStarterMon",
    "gStarterAndGiftMonTable", "gEggMonTable", "sIngameTrades", "sObtainableToNationalOrder",
    "gNaturesInfo", "sSubstructOffsets", "sOddEggSpecies",
    # KaizoCore comfort build only (tools/hns/patches, src/kaizocore_tables.c); absent from the plain build
    "gHnsRoamerSpecies", "gHnsNamedGiftSpecies", "gHnsLabTrashItem", "gHnsChallengePreset",
    # The Pickup table (src/battle_script_commands.c, static)
    "sPickupTable",
    # RAM
    "gPlayerParty", "gPlayerPartyCount", "gEnemyParty", "gEnemyPartyCount", "gBattleMons",
    "gBattleTypeFlags", "gBattlersCount", "gBattlerPartyIndexes", "gBattlerPositions",
    "gBattleOutcome", "gBattleWeather", "gSideStatuses", "gFieldStatuses", "gBattleStruct",
    "gBattleResults", "gBattlerAttacker", "gBattlerTarget", "gCurrentMove", "gLastUsedMove",
    "gChosenMoveByBattler", "gTrainerBattleParameter", "gSaveBlock1Ptr", "gSaveBlock2Ptr",
    "gSaveBlock3Ptr", "gPokemonStoragePtr", "gMapHeader", "gMain", "gSpecialVar_Result",
    "gRngValue", "gFollowerSteps",
    # The time of day the wild encounters use (src/overworld.c, updated every minute; the tracker's wild list, rc37).
    "gTimeOfDay",
    # The tracker's reads (2026-10-05): the battle engine's state, the map tables, and the code addresses
    # gBattleMainFunc holds (raw st_value, thumb bit included).
    "gBattlescriptCurrInstr", "gBattleScripting", "gBattlerAbility", "gCurrentTurnActionNumber",
    "gActionsByTurnOrder", "gHitMarker", "gBattleCommunication", "gBattleMainFunc", "gBattleTextBuff1",
    "gSpecialVar_ItemId", "gBattleEnvironment", "gPaydayMoney", "gSideTimers", "gFieldTimers",
    "sMonSummaryScreen", "gTasks", "gRegionMapEntries", "gMapGroups",
    "HandleTurnActionSelectionState", "ReturnFromBattleToOverworld", "TryDoEventsBeforeFirstTurn",
    "DoBattleIntro",
    # Battle Details and the last-attack line (2026-10-05).
    "gProtectStructs", "gBattleTurnCounter",
    # A battle's way back (raw st_value, thumb bit included): a script's wild battle returns through
    # CB2_EndScriptedWildBattle, a walking one through CB2_EndWildBattle (src/battle_setup.c); the expansion leaves
    # gBattleTypeFlags 0 for both, so gMain.savedCallback is what tells a static from a first encounter.
    "CB2_EndScriptedWildBattle", "CB2_EndWildBattle",
    # Play as your Pokemon (2026-10-05): the overworld table (tracker-gba Overworld.kt, OverworldAddresses), the two
    # callbacks as raw st_value (thumb bit included), as the native side compares gMain.callback2 with them.
    "CB2_Overworld", "CB2_OverworldBasic", "gPlayerAvatar", "gSprites", "gSpriteCoordOffsetX",
    "gSpriteCoordOffsetY", "gPlttBufferUnfaded", "gPlttBufferFaded", "gObjectEvents",
    # The Survival heal counter (2026-10-06): the field script engine's context (src/script.c, static).
    "sGlobalScriptContext",
]

# Battle script labels: plain asm labels carry no size, so each gets "end", the next symbol's address. The tracker
# watches gBattlescriptCurrInstr inside these (the ability pop-up, and the pauses during which a chosen move may not
# happen).
LABELS = [
    "BattleScript_AbilityPopUp", "BattleScript_AbilityPopUpTarget", "BattleScript_AbilityPopUpScripting",
    "BattleScript_AbilityPopUpOverwriteThenNormal", "BattleScript_FocusPunchSetUp", "BattleScript_SnatchedMove",
    "BattleScript_MoveUsedIsConfused", "BattleScript_MoveUsedIsConfusedRet", "BattleScript_MoveUsedIsConfusedNoMore",
    "BattleScript_MoveUsedWokeUp", "BattleScript_MoveUsedIsInLove", "BattleScript_MoveUsedIsInLoveCantAttack",
    "BattleScript_MoveUsedIsFrozen", "BattleScript_BattlerDefrosted",
    # Field script labels (2026-10-06): the free heals outside a Pokemon Center that Survival counts as one (tracker-gba
    # HnsHeals): the shared heal every bed and healer calls, the callers of it that count, and the Alola benches' own heal.
    "Common_EventScript_OutOfCenterPartyHeal", "NewBarkTown_Lab_EventScript_HealingMachine2",
    "NationalPark_Normal_EventScript_Teacher2", "Route26_House1_EventScript_HealWoman", "SSAquaRooms_EventScript_Bed",
    "Route111_OldLadysRestStop_EventScript_Rest", "SSTidalRooms_EventScript_Bed", "Alola_Akala_Bench_Heal_2",
    "Alola_Poni_Bench_Heal_2", "Alola_Ulaula_Bench_Heal_2",
]

# Tables: symbol -> struct name, or uN for plain integer arrays.
TABLES = {
    "gSpeciesInfo": "SpeciesInfo", "gMovesInfo": "MoveInfo", "gAbilitiesInfo": "AbilityInfo",
    "gItemsInfo": "ItemInfo", "gTypesInfo": "TypeInfo", "gTrainers": "Trainer",
    "gBattlePartners": "Trainer", "gTrainerClasses": "TrainerClass",
    "gWildMonHeaders": "WildPokemonHeader", "gTMHMItemMoveIds": "TmHmIndexKey",
    "sIngameTrades": "InGameTrade", "gTypeEffectivenessTable": "u32", "gExperienceTables": "u32",
    "sStarterMon": "u16", "gStarterAndGiftMonTable": "u16", "gEggMonTable": "u16",
    "sObtainableToNationalOrder": "u16", "sSubstructOffsets": "u8", "sOddEggSpecies": "u16", "gHnsRoamerSpecies": "u16", "gHnsNamedGiftSpecies": "u16", "gHnsLabTrashItem": "u16", "gHnsChallengePreset": "u8",
    "sPickupTable": "PickupItem", "gPlayerParty": "Pokemon",
    "gEnemyParty": "Pokemon", "gBattleMons": "BattlePokemon",
}

# Scalar constants (macros or enumerators) the engine and tracker need.
SCALARS = [
    "NUM_SPECIES", "SPECIES_EGG", "MOVES_COUNT", "MOVES_COUNT_ALL", "ABILITIES_COUNT",
    "ITEMS_COUNT", "NUMBER_OF_MON_TYPES", "NUM_TECHNICAL_MACHINES", "NUM_HIDDEN_MACHINES",
    "NUM_ALL_MACHINES", "TRAINERS_COUNT", "MAX_TRAINERS_COUNT", "PARTNER_COUNT",
    "DIFFICULTY_COUNT", "DIFFICULTY_NORMAL", "TIMES_OF_DAY_COUNT", "PARTY_SIZE",
    "MAX_MON_MOVES", "NUM_ABILITY_SLOTS", "POKEMON_NAME_LENGTH", "PLAYER_NAME_LENGTH",
    "TRAINER_NAME_LENGTH", "ABILITY_NAME_LENGTH", "ITEM_NAME_LENGTH", "MOVE_NAME_LENGTH",
    "TYPE_NAME_LENGTH", "TRAINER_ID_LENGTH", "LEVEL_UP_MOVE_END", "EVOLUTIONS_END",
    "MOVE_UNAVAILABLE", "FORM_SPECIES_END", "NATIONAL_DEX_NONE", "NATIONAL_DEX_DEOXYS",
    "NATIONAL_DEX_COUNT", "OBTAINABLE_DEX_COUNT", "SEPARATE_OBTAINABLE_DEX",
    "RANDOMIZER_SPECIES_COUNT", "RANDOMIZER_MAX_EVO_STAGES", 
    "STARTER_AND_GIFT_MON_COUNT", "EGG_MON_COUNT", "MAX_TRAINER_ITEMS", "LAND_WILD_COUNT",
    "WATER_WILD_COUNT", "ROCK_WILD_COUNT", "FISH_WILD_COUNT", "HIDDEN_WILD_COUNT",
    "NUM_FLAG_BYTES", "FLAGS_COUNT", "VARS_START", "VARS_COUNT",
    "NUM_BADGES", "NUM_BATTLE_STATS", "NUM_STATS", "NUM_NATURES", "MAX_LEVEL",
    "MAX_BATTLERS_COUNT", "BAG_ITEMS_COUNT", "BAG_KEYITEMS_COUNT", "BAG_POKEBALLS_COUNT",
    "BAG_TMHM_COUNT", "BAG_BERRIES_COUNT", "BAG_MEDICINE_COUNT", "BAG_TREASURES_COUNT",
    "BAG_BATTLE_ITEMS_COUNT", "I_COMBINE_BAG_POCKETS", "PC_ITEMS_COUNT", "TOTAL_BOXES_COUNT",
    "IN_BOX_COUNT", "MON_RANDOMIZER_INVALID", "P_GENDER_DIFFERENCES", "P_FOOTPRINTS",
    "OW_POKEMON_OBJECT_EVENTS", "P_SEPARATE_REGIONAL_FORMS", "B_PHYSICAL_SPECIAL_SPLIT",
    "MON_MALE", "MON_FEMALE", "MON_GENDERLESS", "SYS_FLAGS",
    "VAR_STARTER_MON", "VAR_TEMP_2", "MAP_GROUPS_COUNT", "OW_TIME_OF_DAY_ENCOUNTERS",
    "OW_TIME_OF_DAY_FALLBACK", "OW_TIME_OF_DAY_DISABLE_FALLBACK", "OW_TIMES_OF_DAY", "GEN_LATEST",
    # The tracker's (2026-10-05).
    "TRAINER_FLAGS_START", "VAR_REPEL_STEP_COUNT", "ACTIONS_CONFIRMED_COUNT", "B_ACTION_USE_MOVE",
    "GAME_STAT_STEPS", "GAME_STAT_FISHING_ENCOUNTERS", "GAME_STAT_USED_ROCK_SMASH", "GAME_STAT_USED_POKECENTER",
    "GAME_STAT_RESTED_AT_HOME", "B_WEATHER_RAIN", "B_WEATHER_SUN", "B_WEATHER_SANDSTORM", "B_WEATHER_HAIL",
    "B_WEATHER_SNOW", "B_WEATHER_FOG", "B_WEATHER_STRONG_WINDS", "B_WEATHER_RAIN_PRIMAL", "B_WEATHER_SUN_PRIMAL", "SHINY_ODDS", "MAPSEC_NONE", "MAP_TYPE_INDOOR",
    "ITEM3_CONFUSION", "ITEM3_PARALYSIS", "ITEM3_FREEZE", "ITEM3_BURN", "ITEM3_POISON", "ITEM3_SLEEP",
    "ITEM3_STATUS_ALL", "ITEM4_HEAL_HP", "ITEM4_HEAL_PP", "ITEM4_HEAL_PP_ONE", "ITEM4_REVIVE", "ITEM4_EVO_STONE",
    "ITEM6_HEAL_HP_FULL", "ITEM6_HEAL_HP_HALF", "ITEM6_HEAL_HP_LVL_UP", "ITEM6_HEAL_HP_QUARTER",
    "STATUS1_SLEEP", "STATUS1_POISON", "STATUS1_BURN", "STATUS1_FREEZE", "STATUS1_PARALYSIS",
    "STATUS1_TOXIC_POISON", "STATUS1_FROSTBITE", "CONDITIONS_END",
    # Hidden items and the lab trash can (2026-10-05).
    "BG_EVENT_HIDDEN_ITEM", "FLAG_HIDDEN_ITEMS_START", "FLAG_HNS_LAB_TRASH_ITEM_TAKEN",
    # The Safari Zone (2026-10-05): the flag the game sets while the player is in it.
    "FLAG_SYS_SAFARI_MODE",
    "FLAG_END_NUZLOCKE",  # set by beating Lance (patch 0038) in every mode: the League is beaten
    # Play as your Pokemon (2026-10-05).
    "MAX_SPRITES", "OBJECT_EVENTS_COUNT",
    # The challenge menu's preset per KaizoCore mode (kaizocore_tables.h, 2026-10-05).
    "HNS_CHALLENGE_ROWS", "HNS_PRESET_MODE", "HNS_PRESET_SKIP_MENU", "HNS_PRESET_VALUES", "HNS_PRESET_LOCKS",
    "HNS_PRESET_SIZE", "HNS_PRESET_MODE_NONE", "HNS_PRESET_MODE_KAIZO", "HNS_PRESET_MODE_NUZLOCKE",
]

# Families exported whole, grouped by prefix. Enumerators and #defines both count.
FAMILIES = [
    "SPECIES_", "MOVE_", "ITEM_", "ABILITY_", "TYPE_", "NATURE_", "EVO_", "IF_", "GROWTH_",
    "SIDE_STATUS_", "STATUS_FIELD_", "BATTLE_ENVIRONMENT_",
    "EGG_GROUP_", "POCKET_", "HOLD_EFFECT_", "DAMAGE_CATEGORY_", "TIME_", "DIFFICULTY_",
    "BALL_", "TRAINER_CLASS_", "MAP_", "MAPSEC_", "FLAG_BADGE", "TRAINER_BATTLE_TYPE_",
    "BATTLE_TYPE_", "STATUS1_", "FORM_CHANGE_", "TRAINER_",
]
# Prefix families that would otherwise swallow constants that are not ids.
FAMILY_EXCLUDE = re.compile(
    r"^(MOVE_(EFFECT|TARGET|NAME|CATEGORY)_|ITEM_(NAME|DESC|USE|EFFECT|TYPE|TO|ID)_|"
    r"SPECIES_(FLAG|NAME)|MAP_(OFFSET|BIT|GROUPS_COUNT|TYPE_|BATTLE_SCENE|LAYOUT|NUM|GROUP)|"
    r"TYPE_(NAME|EFFECTIVENESS|BOX|ICON|ENHANCE)|ABILITY_(NAME|DESCRIPTION)|IF_\w*_H$)")


# The tree builds Emerald, FRLG and HnS from one source, so trainer and map constants of all
# three coexist with colliding values. Keep only this game's.
def family_filter(src):
    hns_trainers = set(re.findall(r"#define\s+(TRAINER_\w+)\s",
                                  open(os.path.join(src, "include/constants/opponents_hns.h")).read()))
    return {
        "TRAINER_": lambda n: n in hns_trainers,
        "MAP_": lambda n: n.endswith("_HNS") or n in ("MAP_UNDEFINED", "MAP_NONE", "MAP_DYNAMIC"),
    }


def die(msg):
    sys.stderr.write("layout.py: " + msg + "\n")
    sys.exit(1)


# ---------------------------------------------------------------- ELF ----

class Elf:
    """Minimal little-endian ELF32 reader: sections and the symbol table."""

    def __init__(self, path):
        self.data = data = open(path, "rb").read()
        if data[:4] != b"\x7fELF" or data[4] != 1 or data[5] != 1:
            die(path + " is not a little-endian ELF32 file")
        shoff, = struct.unpack_from("<I", data, 0x20)
        shentsize, shnum, shstrndx = struct.unpack_from("<HHH", data, 0x2E)
        secs = []
        for i in range(shnum):
            o = shoff + i * shentsize
            name, typ, flags, addr, off, size, link, info, align, entsize = struct.unpack_from("<10I", data, o)
            secs.append(dict(name_off=name, type=typ, addr=addr, off=off, size=size, link=link, entsize=entsize))
        strtab = secs[shstrndx]
        for s in secs:
            s["name"] = self._cstr(strtab["off"] + s["name_off"])
        self.sections = secs
        self.symbols = {}
        for s in secs:
            if s["type"] != 2:  # SHT_SYMTAB
                continue
            names = secs[s["link"]]
            for j in range(s["size"] // 16):
                st_name, value, size, info, other, shndx = struct.unpack_from("<IIIBBH", data, s["off"] + j * 16)
                if not st_name:
                    continue
                name = self._cstr(names["off"] + st_name)
                rec = dict(addr=value, size=size, bind=info >> 4, type=info & 15, shndx=shndx)
                old = self.symbols.get(name)
                # Prefer a global over a local of the same name, and a sized symbol over an unsized one.
                if old is None or (old["bind"] == 0 and rec["bind"] != 0) or (old["size"] == 0 and size):
                    self.symbols[name] = rec

    def _cstr(self, o):
        return self.data[o:self.data.index(b"\0", o)].decode("latin-1")

    def section_bytes(self, name):
        for s in self.sections:
            if s["name"] == name:
                return self.data[s["off"]:s["off"] + s["size"]], s
        return None, None


# ------------------------------------------------------- compile line ----

def compile_pipeline(src):
    """The cpp | preproc | cc1 | as pipeline make uses for src/main.c, as argument lists."""
    out = subprocess.run(["make", "-n", "hns", "-W", "src/main.c"], cwd=src, capture_output=True, text=True)
    line = None
    for l in out.stdout.splitlines():
        if "arm-none-eabi-cpp" in l and " src/main.c" in l and "cc1" in l:
            line = l
            break
    if line is None:
        die("could not find the compile command for src/main.c in `make -n hns` output")
    parts = [p.strip() for p in line.split(" | ")]
    cpp = shlex.split(parts[0])
    pre = shlex.split(parts[1])
    cc1 = shlex.split(parts[2])
    asm = shlex.split(parts[-1])
    if "cpp" not in cpp[0] or "preproc" not in pre[0] or "cc1" not in cc1[0] or not asm[0].endswith("as"):
        die("unexpected pipeline shape: " + line)
    cpp = [a for a in cpp if a != "src/main.c"]
    # Warnings are noise for a probe; errors are handled by dropping the failing entries.
    cc1 = [a for a in cc1 if a not in ("-Werror", "-Wall") and not a.startswith("-W")] + ["-w"]
    cc1 = [a for a in cc1 if a not in ("-o", "-")]
    asm = [a for a in asm if a != "-"]
    i = asm.index("-o")
    del asm[i:i + 2]
    return dict(cpp=cpp, pre=pre[0], cc1=cc1, asm=asm, line=line)


def run_cpp(pipe, src, cfile, extra=()):
    r = subprocess.run(pipe["cpp"] + list(extra) + [cfile], cwd=src, capture_output=True, text=True)
    if r.returncode:
        die("cpp failed:\n" + r.stderr[-3000:])
    return r.stdout


def compile_obj(pipe, src, cfile, ofile):
    """Returns (ok, stderr)."""
    cpp = subprocess.run(pipe["cpp"] + [cfile], cwd=src, capture_output=True)
    if cpp.returncode:
        return False, cpp.stderr.decode("utf-8", "replace")
    pre = subprocess.run([pipe["pre"], "-i", cfile, "charmap.txt"], cwd=src, input=cpp.stdout, capture_output=True)
    if pre.returncode:
        return False, pre.stderr.decode("utf-8", "replace")
    cc = subprocess.run(pipe["cc1"] + ["-o", "-", "-"], cwd=src, input=pre.stdout, capture_output=True)
    if cc.returncode:
        return False, cc.stderr.decode("utf-8", "replace")
    asm = subprocess.run(pipe["asm"] + ["-o", ofile, "-"], cwd=src, input=cc.stdout, capture_output=True)
    if asm.returncode:
        return False, asm.stderr.decode("utf-8", "replace")
    return True, ""


# ---------------------------------------------------------- C parsing ----

def strip_markers(text):
    return "\n".join(l for l in text.splitlines() if not l.startswith("#"))


def match_brace(text, i):
    """text[i] == '{'; returns the index of the matching '}'."""
    depth = 0
    for j in range(i, len(text)):
        c = text[j]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return j
    raise ValueError("unbalanced braces")


def split_top(text, sep):
    out, depth, cur = [], 0, []
    for c in text:
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        if c == sep and depth == 0:
            out.append("".join(cur))
            cur = []
        else:
            cur.append(c)
    out.append("".join(cur))
    return out


ATTR = re.compile(r"__attribute__\s*\(\(")


def strip_attrs(s):
    while True:
        m = ATTR.search(s)
        if not m:
            return s
        depth, j = 0, m.start() + len("__attribute__")
        while True:
            if s[j] == "(":
                depth += 1
            elif s[j] == ")":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        s = s[:m.start()] + " " + s[j + 1:]


def find_struct(text, name):
    m = re.search(r"\bstruct\s+(?:__attribute__\s*\(\([^{;]*?\)\)\s*)*" + re.escape(name) + r"\s*\{", text)
    if not m:
        return None
    start = m.end() - 1
    return text[start + 1:match_brace(text, start)]


def declarator_info(d):
    """Returns (name, is_bitfield, is_array) for one declarator text (type may be attached)."""
    d = d.strip()
    m = re.search(r"\(\s*\*\s*(?:const\s+)?(\w+)\s*\)", d)
    if m:
        return m.group(1), False, False
    parts = split_top(d, ":")
    bit = len(parts) > 1
    base = parts[0].strip()
    arr = False
    while base.endswith("]"):
        depth = 0
        for j in range(len(base) - 1, -1, -1):
            if base[j] == "]":
                depth += 1
            elif base[j] == "[":
                depth -= 1
                if depth == 0:
                    break
        base = base[:j].rstrip()
        arr = True
    m = re.search(r"(\w+)$", base)
    if not m:
        raise ValueError("no name in declarator: " + d)
    return m.group(1), bit, arr


def parse_members(body):
    """Flattened member list: dicts with name, bitfield, array. Anonymous aggregates are inlined."""
    out = []
    body = strip_attrs(body)
    i, decls, cur = 0, [], []
    # split on ';' at brace depth 0, but keep nested aggregate bodies intact
    depth = 0
    for c in body:
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        if c == ";" and depth == 0:
            decls.append("".join(cur))
            cur = []
        else:
            cur.append(c)
    for d in decls:
        d = d.strip()
        if not d:
            continue
        if "{" in d:
            a = d.index("{")
            b = match_brace(d, a)
            after = d[b + 1:].strip()
            if not after:
                out.extend(parse_members(d[a + 1:b]))
            else:
                for decl in split_top(after, ","):
                    name, bit, arr = declarator_info(decl)
                    out.append(dict(name=name, bitfield=False, array=arr, aggregate=True))
            continue
        pieces = split_top(d, ",")
        type_tokens = set(re.findall(r"\w+", pieces[0].split(declarator_info(pieces[0])[0])[0]))
        signed = bool(type_tokens & {"s8", "s16", "s32", "s64", "int", "signed", "short", "long"}) \
            and "unsigned" not in type_tokens
        for k, decl in enumerate(pieces):
            name, bit, arr = declarator_info(decl)
            out.append(dict(name=name, bitfield=bit, array=arr, aggregate=False, signed=signed))
    return out


def parse_enumerators(text):
    names = []
    for m in re.finditer(r"\benum\b(?:\s*__attribute__\s*\(\(\s*\w+\s*\)\))?\s*\w*\s*\{", text):
        a = m.end() - 1
        try:
            b = match_brace(text, a)
        except ValueError:
            continue
        for item in split_top(text[a + 1:b], ","):
            item = item.strip()
            mm = re.match(r"([A-Za-z_]\w*)", item)
            if mm:
                names.append(mm.group(1))
    return names


def raw_struct_source(path, name):
    text = open(path, encoding="utf-8", errors="replace").read()
    m = re.search(r"\bstruct\s+" + re.escape(name) + r"\s*\{", text)
    if not m:
        return None
    b = match_brace(text, m.end() - 1)
    end = text.index(";", b)
    return text[m.start():end + 1]


# ------------------------------------------------------------ charmap ----

def load_charmap(src):
    table = {}
    for line in open(os.path.join(src, "charmap.txt"), encoding="utf-8"):
        m = re.match(r"^'(.|\\.)'\s*=\s*([0-9A-Fa-f]{2})\s*$", line.rstrip("\n"))
        if not m:
            continue
        ch = m.group(1)
        if ch.startswith("\\"):
            ch = {"\\n": "\n", "\\'": "'", "\\\\": "\\"}.get(ch, ch[1:])
        b = int(m.group(2), 16)
        table.setdefault(b, ch)
    return table


def decode(buf, charmap):
    out = []
    for b in buf:
        if b == 0xFF:
            break
        out.append(charmap.get(b, "{%02X}" % b))
    return "".join(out)


# ------------------------------------------------------------- probe ----

def build_probe(src, pipe, workdir):
    extra_defs = []
    for name, where in STRUCTS:
        if where:
            raw = raw_struct_source(os.path.join(src, where), name)
            if raw is None:
                die("struct %s not found in %s" % (name, where))
            extra_defs.append(raw)
    head = "".join('#include "%s"\n' % h for h in HEADERS if os.path.exists(os.path.join(src, "include", h)))
    head += "\n".join(extra_defs) + "\n"

    p1 = os.path.join(workdir, "kc_probe1.c")
    open(p1, "w").write(head)
    pre_text = strip_markers(run_cpp(pipe, src, p1))
    macro_text = run_cpp(pipe, src, p1, extra=["-dM"])

    # Structs and their members.
    structs = {}
    missing_structs = []
    for name, _ in STRUCTS:
        body = find_struct(pre_text, name)
        if body is None:
            missing_structs.append(name)
            continue
        structs[name] = parse_members(body)

    # Candidate constants: enumerators plus object-like integer-looking macros.
    macros = {}
    for line in macro_text.splitlines():
        m = re.match(r"#define ([A-Za-z_]\w*)(?:\s+(.*))?$", line)
        if m and m.group(2) is not None:
            v = m.group(2).strip()
            if v and '"' not in v and "{" not in v and "'" not in v:
                macros[m.group(1)] = v
    enumerators = parse_enumerators(pre_text)
    cands = set(macros) | set(enumerators)
    wanted = set(n for n in SCALARS if n in cands)
    # The flags the maps' hidden items name (many prefixes: FLAG_HIDDEN_ITEM_, FLAG_ITEM_, FLAG_ROUTE50_...).
    wanted |= set(n for n in hidden_item_flag_names(src) if n in cands)
    family_names = {}
    keep = family_filter(src)
    for n in cands:
        if FAMILY_EXCLUDE.match(n):
            continue
        for p in FAMILIES:
            if n.startswith(p):
                if p in keep and not keep[p](n):
                    break
                family_names.setdefault(p, set()).add(n)
                wanted.add(n)
                break

    # The probe: one long long per value, one line each, so a failing line maps to one key.
    lines = [head, "#define KC_SECTION __attribute__((section(\".kcprobe\"), used))\n"]
    lines.append("#define KC_TM_MOVE(x) MOVE_##x,\n")
    keys = []

    def add(key, expr):
        keys.append((key, expr))

    for name, members in structs.items():
        add(("size", name), "sizeof(struct %s)" % name)
        add(("align", name), "__alignof__(struct %s)" % name)
        for mem in members:
            if mem["bitfield"]:
                continue
            f = mem["name"]
            add(("off", name, f), "__builtin_offsetof(struct %s, %s)" % (name, f))
            add(("fsize", name, f), "sizeof(((struct %s *)0)->%s)" % (name, f))
            if mem["array"]:
                add(("count", name, f), "sizeof(((struct %s *)0)->%s) / sizeof(((struct %s *)0)->%s[0])" % (name, f, name, f))
    for n in sorted(wanted):
        add(("const", n), "(long long)(%s)" % n)

    bitfield_probes = []
    for name, members in structs.items():
        for mem in members:
            if mem["bitfield"]:
                bitfield_probes.append((name, mem["name"]))

    dropped = []
    for attempt in range(40):
        body = list(lines)
        body.append("const long long kc_vals[] KC_SECTION = {\n")
        first_line = sum(l.count("\n") for l in body) + 1
        for key, expr in keys:
            body.append("    %s,\n" % expr)
        body.append("};\n")
        body.append("const unsigned short kc_tm_moves[] KC_SECTION = { FOREACH_TM(KC_TM_MOVE) 0 };\n")
        body.append("const unsigned short kc_hm_moves[] KC_SECTION = { FOREACH_HM(KC_TM_MOVE) 0 };\n")
        bf_first = sum(l.count("\n") for l in body) + 1
        for i, (sname, f) in enumerate(bitfield_probes):
            body.append("const struct %s kc_bf_%d KC_SECTION = { .%s = ~0 };\n" % (sname, i, f))
        p2 = os.path.join(workdir, "kc_probe2.c")
        open(p2, "w").write("".join(body))
        ofile = os.path.join(workdir, "kc_probe2.o")
        ok, err = compile_obj(pipe, src, p2, ofile)
        if ok:
            break
        bad_vals, bad_bf = set(), set()
        for m in re.finditer(r"kc_probe2\.c:(\d+):(?:\d+:)? error", err):
            ln = int(m.group(1))
            if first_line <= ln < first_line + len(keys):
                bad_vals.add(ln - first_line)
            elif bf_first <= ln < bf_first + len(bitfield_probes):
                bad_bf.add(ln - bf_first)
        if not bad_vals and not bad_bf:
            die("probe failed to compile:\n" + err[-4000:])
        dropped += [k for i, k in enumerate(keys) if i in bad_vals]
        dropped += [("bitfield",) + b for i, b in enumerate(bitfield_probes) if i in bad_bf]
        keys = [k for i, k in enumerate(keys) if i not in bad_vals]
        bitfield_probes = [b for i, b in enumerate(bitfield_probes) if i not in bad_bf]
    else:
        die("probe kept failing")

    # Only constants may be dropped; a struct field that does not compile is a parser bug.
    bad = [k for k in dropped if k[0] != "const"]
    if bad:
        die("probe entries failed to compile: %r" % bad[:20])
    obj = Elf(ofile)
    sec, _ = obj.section_bytes(".kcprobe")
    if sec is None:
        die("probe object has no .kcprobe section")

    def sym_bytes(name, size=None):
        s = obj.symbols[name]
        return sec[s["addr"]:s["addr"] + (size if size is not None else s["size"])]

    raw = sym_bytes("kc_vals")
    vals = {}
    for i, (key, _) in enumerate(keys):
        vals[key] = struct.unpack_from("<q", raw, 8 * i)[0]
    tm_moves = list(struct.unpack("<%dH" % (obj.symbols["kc_tm_moves"]["size"] // 2), sym_bytes("kc_tm_moves")))[:-1]
    hm_moves = list(struct.unpack("<%dH" % (obj.symbols["kc_hm_moves"]["size"] // 2), sym_bytes("kc_hm_moves")))[:-1]

    bitfields = {}
    for i, (sname, f) in enumerate(bitfield_probes):
        b = sym_bytes("kc_bf_%d" % i)
        v = int.from_bytes(b, "little")
        if v == 0:
            die("bitfield probe %s.%s set no bits" % (sname, f))
        lo = (v & -v).bit_length() - 1
        hi = v.bit_length() - 1
        width = hi - lo + 1
        if bin(v).count("1") != width:
            die("bitfield %s.%s is not contiguous" % (sname, f))
        # Smallest naturally aligned 1/2/4-byte unit holding the whole field.
        for unit in (1, 2, 4, 8):
            start = (lo // 8) // unit * unit
            if (hi // 8) < start + unit:
                break
        bitfields[(sname, f)] = dict(offset=start, size=unit, bits=[lo - start * 8, width])

    return dict(structs=structs, missing_structs=missing_structs, vals=vals, bitfields=bitfields,
                family_names=family_names, tm_moves=tm_moves, hm_moves=hm_moves,
                macros=macros, dropped=sorted(k[1] for k in dropped))


# ------------------------------------------------------------- export ----

def build_layout(src, crc_name):
    elf_path = os.path.join(src, "pokehns.elf")
    gba_path = os.path.join(src, "pokehns.gba")
    if not os.path.exists(elf_path) or not os.path.exists(gba_path):
        die("build %s has no pokehns.elf / pokehns.gba; run `make hns` first" % src)
    rom = open(gba_path, "rb").read()
    crc = "%08X" % (zlib.crc32(rom) & 0xFFFFFFFF)
    elf = Elf(elf_path)
    pipe = compile_pipeline(src)
    with tempfile.TemporaryDirectory(prefix="kc_layout_") as wd:
        probe = build_probe(src, pipe, wd)
        vals = probe["vals"]
        nspecies = vals[("const", "NUM_SPECIES")]
        species_values = {n: vals[("const", n)] for n in probe["family_names"].get("SPECIES_", ())
                          if ("const", n) in vals}
        scripts = scan_scripts(src, elf, rom, wd, nspecies, species_values)

    structs_out = {}
    for name, members in probe["structs"].items():
        fields = {}
        for mem in members:
            f = mem["name"]
            if mem["bitfield"]:
                bf = probe["bitfields"].get((name, f))
                if bf:
                    fields[f] = dict(bf, signed=True) if mem.get("signed") else bf
                continue
            if ("off", name, f) not in vals:
                continue
            d = dict(offset=vals[("off", name, f)], size=vals[("fsize", name, f)])
            if mem.get("signed"):
                d["signed"] = True
            if ("count", name, f) in vals:
                d["count"] = vals[("count", name, f)]
            fields[f] = d
        structs_out[name] = dict(size=vals[("size", name)], align=vals[("align", name)],
                                 fields=dict(sorted(fields.items(), key=lambda kv: (kv[1]["offset"], kv[0]))))

    symbols_out, missing_symbols = {}, []
    for n in SYMBOLS:
        s = elf.symbols.get(n)
        if s is None:
            missing_symbols.append(n)
            continue
        symbols_out[n] = dict(addr="0x%08X" % s["addr"], size=s["size"])
    rom_addrs = sorted(set(v["addr"] for v in elf.symbols.values() if ROM_BASE <= v["addr"] < ROM_BASE + 0x2000000))
    labels_out = {}
    for n in LABELS:
        s = elf.symbols.get(n)
        if s is None:
            missing_symbols.append(n)
            continue
        nxt = next((a for a in rom_addrs if a > s["addr"]), None)
        labels_out[n] = dict(addr="0x%08X" % s["addr"], end="0x%08X" % nxt if nxt else None)

    constants = {}
    for (kind, *rest), v in vals.items():
        if kind == "const" and rest[0] in SCALARS:
            constants[rest[0]] = v
    enums = {}
    for prefix, names in probe["family_names"].items():
        grp = {n: vals[("const", n)] for n in names if ("const", n) in vals}
        if grp:
            enums[prefix.rstrip("_")] = dict(sorted(grp.items(), key=lambda kv: (kv[1], kv[0])))

    # TM/HM list from the build's own macro lists, joined with the ROM's index table.
    move_names = {}
    for n, v in enums.get("MOVE", {}).items():
        move_names.setdefault(v, n)
    machines = []
    for kind, lst in (("TM", probe["tm_moves"]), ("HM", probe["hm_moves"])):
        for i, mv in enumerate(lst):
            machines.append(dict(kind=kind, num=i + 1, move=mv, moveName=move_names.get(mv)))
    key = probe["structs"].get("TmHmIndexKey") and structs_out["TmHmIndexKey"]
    if key and "gTMHMItemMoveIds" in symbols_out:
        base = int(symbols_out["gTMHMItemMoveIds"]["addr"], 16) - ROM_BASE
        io, isz = key["fields"]["itemId"]["offset"], key["fields"]["itemId"]["size"]
        mo, msz = key["fields"]["moveId"]["offset"], key["fields"]["moveId"]["size"]
        for i, m in enumerate(machines):
            rec = base + (i + 1) * key["size"]
            item = int.from_bytes(rom[rec + io:rec + io + isz], "little")
            move = int.from_bytes(rom[rec + mo:rec + mo + msz], "little")
            if move != m["move"]:
                die("gTMHMItemMoveIds[%d] has move %d, macro list says %d" % (i + 1, move, m["move"]))
            m["item"] = item

    # Element type and count of each table, checked against the symbol size.
    tables = {}
    for sym, kind in TABLES.items():
        if sym not in symbols_out:
            continue
        size = symbols_out[sym]["size"]
        if kind in structs_out:
            esz, d = structs_out[kind]["size"], dict(struct=kind)
        else:
            esz, d = int(kind[1:]) // 8, dict(elem=kind)
        if size % esz:
            die("%s is %d bytes, not a whole number of %s (%d)" % (sym, size, kind, esz))
        d["count"] = size // esz
        tables[sym] = d

    # Substructure order straight from the ROM table GetSubstruct uses.
    so = symbols_out.get("sSubstructOffsets")
    if so is None or so["size"] != 96:
        die("sSubstructOffsets missing or not u8[4][24]")
    a = int(so["addr"], 16) - ROM_BASE
    order = [list(rom[a + 24 * t:a + 24 * t + 24]) for t in range(4)]

    hidden_flags = {n: vals[("const", n)] for n in hidden_item_flag_names(src) if ("const", n) in vals}
    hidden = scan_hidden_items(src, rom, symbols_out, structs_out, constants, enums, hidden_flags)

    layout = dict(
        buildCrc=crc,
        buildName=crc_name,
        generator="tools/hns/layout.py",
        compile=" ".join(pipe["cc1"][1:]),
        romBase="0x08000000",
        symbols=dict(sorted(symbols_out.items())),
        labels=dict(sorted(labels_out.items())),
        structs=dict(sorted(structs_out.items())),
        constants=dict(sorted(constants.items())),
        enums=dict(sorted(enums.items())),
        machines=machines,
        charmap={"%02X" % b: c for b, c in sorted(load_charmap(src).items())},
        tables=dict(sorted(tables.items())),
        scripts=scripts,
        hiddenItems=hidden,
        terminators=dict(levelUpLearnset="move == LEVEL_UP_MOVE_END", evolutions="method == EVOLUTIONS_END",
                         teachableLearnset="u16 == MOVE_UNAVAILABLE", formSpeciesIdTable="u16 == FORM_SPECIES_END",
                         wildMonHeaders="mapGroup == MAP_GROUP(MAP_UNDEFINED) (0xFF)"),
        boxMonEncryption=dict(
            key="otId ^ personality, XORed into each u32 of BoxPokemon.secure.raw",
            checksum="u16 sum of the 24 decrypted u16 halves of secure.raw",
            substructOrder="index = personality % 24; substruct type t sits at secure.substructs[order[t][index]]"
                           " (12 bytes each); from sSubstructOffsets",
            order=order,
            nickname="10 bytes in BoxPokemon.nickname + nickname11 and nickname12 in substruct 0",
            money="SaveBlock1.money ^ SaveBlock2.encryptionKey",
            bagQuantity="ItemSlot.quantity ^ (SaveBlock2.encryptionKey & 0xFFFF) (bag only, not PC items)",
        ),
        missing=dict(symbols=missing_symbols, structs=probe["missing_structs"],
                     scalars=sorted(n for n in SCALARS if n not in constants),
                     constantsThatDidNotCompile=probe["dropped"]),
    )
    return layout, rom, elf, probe


# -------------------------------------------------------- hidden items ----

def hidden_item_maps(src):
    """(map.json "id", its hidden_item bg_events) for every map folder of the tree."""
    maps_dir = os.path.join(src, "data", "maps")
    for d in sorted(os.listdir(maps_dir)):
        mj = os.path.join(maps_dir, d, "map.json")
        if os.path.isfile(mj):
            m = json.load(open(mj, encoding="utf-8"))
            yield m["id"], [ev for ev in (m.get("bg_events") or []) if ev.get("type") == "hidden_item"]


def hidden_item_flag_names(src):
    return sorted({ev["flag"] for _, evs in hidden_item_maps(src) for ev in evs})


def scan_hidden_items(src, rom, symbols, structs, constants, enums, flags):
    """Every hidden item of the game's maps, read from the ROM: gMapGroups -> MapHeader.events -> MapEvents.bgEvents,
    the BgEvents of kind BG_EVENT_HIDDEN_ITEM. bgUnion.hiddenItem is a bitfield struct inside a union, which the
    probe cannot name, so its bits (item 11, hiddenItemId 13, quantity 7, underfoot 1 from bit 0, include/global.fieldmap.h
    and the bg_hidden_item_event macro) are proved here instead: every hidden item a map.json lists must read back with
    its item, its flag and its quantity at its x and y, and every one in the ROM must be listed."""
    BITS = dict(item=[0, 11], hiddenItemId=[11, 13], quantity=[24, 7], underfoot=[31, 1])
    kind_hidden = constants.get("BG_EVENT_HIDDEN_ITEM")
    flag_start = constants.get("FLAG_HIDDEN_ITEMS_START")
    if kind_hidden is None or flag_start is None or "gMapGroups" not in symbols:
        die("hidden items: BG_EVENT_HIDDEN_ITEM, FLAG_HIDDEN_ITEMS_START or gMapGroups missing")
    mh, me, be = structs["MapHeader"], structs["MapEvents"], structs["BgEvent"]
    groups = int(symbols["gMapGroups"]["addr"], 16)
    items = enums.get("ITEM", {})
    flag_name = {}
    for n, v in sorted(flags.items()):
        flag_name.setdefault(v, n)

    def bits(word, k):
        lo, w = BITS[k]
        return (word >> lo) & ((1 << w) - 1)

    def fld(st, base, name):
        f = st["fields"][name]
        return rd(rom, base + f["offset"], f["size"])

    out, seen, scanned = [], set(), set()
    for map_const, v in sorted(enums.get("MAP", {}).items(), key=lambda kv: kv[1]):
        if not map_const.endswith("_HNS"):
            continue
        g, n = v >> 8, v & 0xFF
        header = rd(rom, rd(rom, groups + 4 * g, 4) + 4 * n, 4)
        if (header, map_const) in seen:
            continue
        seen.add((header, map_const))
        scanned.add(map_const)
        events = fld(mh, header, "events")
        if not events:
            continue
        count, bgs = fld(me, events, "bgEventCount"), fld(me, events, "bgEvents")
        for i in range(count):
            b = bgs + i * be["size"]
            if fld(be, b, "kind") != kind_hidden:
                continue
            addr = b + be["fields"]["bgUnion"]["offset"]
            word = rd(rom, addr, 4)
            fid = bits(word, "hiddenItemId") + flag_start
            out.append(dict(map=map_const, x=fld(be, b, "x"), y=fld(be, b, "y"), addr="0x%08X" % addr,
                            item=bits(word, "item"), quantity=bits(word, "quantity"),
                            underfoot=bool(bits(word, "underfoot")), flag=fid, flagName=flag_name.get(fid)))

    # The proof against the sources: data/maps/*/map.json.
    listed = {}
    for map_id, evs in hidden_item_maps(src):
        for ev in evs:
            listed[(map_id, ev["x"], ev["y"])] = ev
    got = {(h["map"], h["x"], h["y"]): h for h in out}
    for key, h in got.items():
        ev = listed.get(key)
        if ev is None:
            die("hidden item %r is in the ROM but in no map.json" % (key,))
        want_q = int(ev.get("quantity", 1) or 1)
        if items.get(ev["item"]) != h["item"] or flags.get(ev["flag"]) != h["flag"] or want_q != h["quantity"]:
            die("hidden item %r: map.json says %s %s x%d, the ROM reads item %d flag %d x%d"
                % (key, ev["item"], ev["flag"], want_q, h["item"], h["flag"], h["quantity"]))
    missing = [k for k in listed if k[0] in scanned and k not in got]
    if missing:
        die("hidden items in map.json but not in the ROM: %r" % missing[:5])
    return dict(bits=BITS, count=len(out), items=out)


# ------------------------------------------------------- event scripts ----

# Script commands that carry a species, as positions in the list of data directives the
# macro emits (callnative counts as two: the opcode byte and the function pointer).
SCRIPT_MONS = {
    "givemon": dict(species=4, level=5),
    "createmon": dict(side=2, slot=3, species=4, level=5),
    "setwildbattle": dict(species=1, level=2, item=3, species2=4, level2=5, item2=6),
    "setwildbattleshiny": dict(species=1, level=2, item=3),
    "setwildbossbattle": dict(species=2, level=3, item=4, species2=9, level2=10, item2=11),
    "giveegg": dict(species=1),
    "seteventmon": dict(species=2, level=5, item=8),
    "setvar": dict(var=1, value=2),
}
LST_DATA = re.compile(r"^\s*\d+\s+([0-9a-f]{4,8})\s+([0-9A-F]+)\s*\t(>*)\s*(\.\w+)")
LST_STMT = re.compile(r"^\s*\d+\s+\t (\w+)\b(.*)$")
LST_LABEL = re.compile(r"^\s*\d+\s+\t(?:\.ifdef \w+; \.size [^;]*; \.endif; )?(\w+):+\s*$")
LST_FILE = re.compile(r'\.linefile (\d+)\s*(?:"([^"]+)")?')
LST_NUM = re.compile(r"^\s*(\d+)\s")
# preproc closes the previous label's .size before each label. For `Label:` it does so on the
# label's own line; for `Label::` it adds a line ending in `.global Label` before `Label:`; at
# the end of a file it adds a bare `.ifdef ...; .endif` line. Those added lines have no source
# line of their own, but cpp numbers them (its line markers count them), so the source line of
# a statement is the marker's number minus every added line seen so far in that file.
LST_INJECTED = re.compile(r"^\s*\d+\s+\t(\.ifdef \w+; \.size [^;]*; \.endif(; \.global \w+)?\s*$|\.global \w+\s*$)")


def scan_scripts(src, elf, rom, workdir, num_species, species_values):
    """Every species-carrying script command in data/event_scripts.s, with the ROM address
    of each field, from an assembler listing of the exact command make uses. Also every other
    command whose source line names a SPECIES_ constant (showmonpic, case, checkspecies...),
    with the address of each u16 that holds that species, so a randomizer can keep them in step."""
    out = subprocess.run(["make", "-n", "hns", "-W", "data/event_scripts.s"], cwd=src, capture_output=True, text=True)
    line = next((l for l in out.stdout.splitlines()
                 if "data/event_scripts.s" in l and "-o build/" in l and "preproc" in l), None)
    if line is None:
        die("no assemble command for data/event_scripts.s in `make -n hns`")
    m = re.search(r"-o (\S+event_scripts\.o)", line)
    built = os.path.join(src, m.group(1))
    lst = os.path.join(workdir, "ev.lst")
    obj = os.path.join(workdir, "ev.o")
    cmd = line.replace(m.group(0), "-almn=%s -o %s" % (shlex.quote(lst), shlex.quote(obj)))
    r = subprocess.run(["bash", "-c", "set -o pipefail; " + cmd], cwd=src, capture_output=True, text=True)
    if r.returncode:
        die("assembling the event scripts failed:\n" + r.stderr[-3000:])
    if open(obj, "rb").read() != open(built, "rb").read():
        die("re-assembled event_scripts.o differs from %s; rebuild first" % built)
    ev = Elf(obj)
    sec = next(s for s in ev.sections if s["name"] == "script_data")
    sec_index = ev.sections.index(sec)
    bases = set()
    for name, s in ev.symbols.items():
        if s["bind"] == 1 and s["shndx"] == sec_index and name in elf.symbols:
            bases.add(elf.symbols[name]["addr"] - s["addr"])
    if len(bases) != 1:
        die("script_data base is not unique: %r" % sorted(bases)[:5])
    base = bases.pop()

    mons, setvars, refs = [], [], []
    cur_file, label = None, None
    anchor = (None, 0, 0)  # (file, line N, listing line of the .linefile directive)
    injected = {}  # file -> added lines seen so far in it
    stmt = None
    src_cache = {}

    def source_line(fil, n):
        if fil not in src_cache:
            try:
                src_cache[fil] = open(os.path.join(src, fil), encoding="utf-8", errors="replace").read().split("\n")
            except OSError:
                src_cache[fil] = []
        lines = src_cache[fil]
        return lines[n - 1] if 0 < n <= len(lines) else ""

    def finish(st):
        if st is None:
            return
        kind, lab, fil, lineno, data = st
        if not data:
            return  # inside a false .if: nothing was emitted
        text = source_line(fil, lineno).strip() if lineno else ""
        if kind not in SCRIPT_MONS:
            names = re.findall(r"\bSPECIES_\w+", text.split("@")[0])
            if names:
                want = {species_values[n] for n in names if n in species_values}
                hits = []
                for off, size, hexbytes in data:
                    if size == 2:
                        v = rd(rom, base + off, 2)
                        if v in want:
                            hits.append(dict(species=v, addr="0x%08X" % (base + off)))
                refs.append(dict(kind=kind, label=lab, file=fil, line=lineno, text=text, refs=hits))
            return
        lay = SCRIPT_MONS[kind]
        if max(lay.values()) >= len(data):
            die("%s at %s emitted %d directives, expected more" % (kind, lab, len(data)))
        e = dict(kind=kind, label=lab, file=fil, line=lineno, text=text)
        for k, i in lay.items():
            off, size, hexbytes = data[i]
            addr = base + off
            v = rd(rom, addr, size)
            listed = int.from_bytes(bytes.fromhex(hexbytes), "little")
            if v != listed:
                die("%s %s field %s: ROM has %d at 0x%08X, listing says %d" % (kind, lab, k, v, addr, listed))
            e[k] = v
            e[k + "Addr"] = "0x%08X" % addr
            if k in ("species", "level", "item", "species2", "level2", "item2", "value"):
                e[k + "Size"] = size
        # givemon's held item: after the u32 of flags (directive 6), present only when bit 0 says an item was given
        # (asm/macros/event.inc). The lab's starter givemon carries one (ITEM_NONE), which KaizoCore randomizes.
        if kind == "givemon" and len(data) > 7 and data[6][1] == 4:
            if rd(rom, base + data[6][0], 4) & 1:
                off, size, hexbytes = data[7]
                v = rd(rom, base + off, size)
                if size != 2 or v != int.from_bytes(bytes.fromhex(hexbytes), "little"):
                    die("givemon %s item operand: ROM has %d, listing says %s" % (lab, v, hexbytes))
                e["item"], e["itemAddr"], e["itemSize"] = v, "0x%08X" % (base + off), size
        if kind == "setvar":
            setvars.append(e)
        else:
            mons.append(e)

    with open(lst, encoding="utf-8", errors="replace") as f:
        for ln in f:
            if ".linefile" in ln:
                fm = LST_FILE.search(ln)
                nm = LST_NUM.match(ln)
                if fm and nm:
                    anchor = (fm.group(2) or anchor[0], int(fm.group(1)), int(nm.group(1)))
                    if fm.group(2) and not fm.group(2).startswith("<") and not fm.group(2).startswith("include/"):
                        cur_file = fm.group(2)
                continue
            dm = LST_DATA.match(ln)
            if dm:
                if stmt is not None and dm.group(3):
                    stmt[4].append((int(dm.group(1), 16), len(dm.group(2)) // 2, dm.group(2)))
                continue
            if "\t>" in ln:
                continue
            if LST_INJECTED.match(ln):
                injected[cur_file] = injected.get(cur_file, 0) + 1
                continue
            lm = LST_LABEL.match(ln)
            if lm:
                label = lm.group(1)
                continue
            sm = LST_STMT.match(ln)
            if sm:
                finish(stmt)
                listnum = int(LST_NUM.match(ln).group(1))
                lineno = (anchor[1] + (listnum - anchor[2] - 1) - injected.get(cur_file, 0)
                          if anchor[0] == cur_file else None)
                stmt = (sm.group(1), label, cur_file, lineno, [])
        finish(stmt)

    # A species given through a variable (the starters: setvar VAR_TEMP_2, SPECIES_X, then
    # givemon VAR_TEMP_2) is set by setvar commands in the same file.
    var_uses = {}
    for e in mons:
        for k in ("species", "species2"):
            if e.get(k, 0) >= 0x4000:
                e[k + "IsVar"] = True
                var_uses.setdefault((e["file"], e[k]), []).append(e["label"])
    for e in setvars:
        # Only a setvar whose source line names a species (checked in the original file).
        if (e["file"], e["var"]) in var_uses and "SPECIES_" in e["text"]:
            e["kind"] = "setvar-species"
            e["usedBy"] = sorted(set(var_uses[(e["file"], e["var"])]))
            mons.append(e)
        elif "SPECIES_" in e["text"].split("@")[0]:
            v = e["value"]
            refs.append(dict(kind="setvar", label=e["label"], file=e["file"], line=e["line"], text=e["text"],
                             refs=[dict(species=v, addr=e["valueAddr"])] if 0 < v < num_species else []))
    for e in mons:
        for k in ("species", "species2", "value"):
            if k in e and not e.get(k + "IsVar") and not (0 <= e[k] < num_species):
                die("%s %s has species %d" % (e["kind"], e["label"], e[k]))
        # The source line must agree with what the ROM holds (proves the line mapping).
        if e["kind"] in ("givemon", "setwildbattle", "seteventmon", "giveegg", "setwildbossbattle",
                         "setwildbattleshiny", "setvar-species") and not e["text"].split()[0] == e["kind"].split("-")[0]:
            die("%s %s maps to %s:%s which reads %r" % (e["kind"], e["label"], e["file"], e["line"], e["text"]))
    mons.sort(key=lambda e: (e.get("speciesAddr") or e.get("valueAddr")))
    refs.sort(key=lambda e: (e["file"], e["line"]))
    return dict(scriptDataBase="0x%08X" % base, mons=mons, speciesRefs=refs)


def rd(rom, addr, size):
    o = addr - ROM_BASE
    return int.from_bytes(rom[o:o + size], "little")


def field(rom, base, f):
    v = rd(rom, base + f["offset"], f["size"])
    if "bits" in f:
        v = (v >> f["bits"][0]) & ((1 << f["bits"][1]) - 1)
    return v


def build_species(layout, rom, src):
    charmap = load_charmap(src)
    S = layout["structs"]["SpeciesInfo"]
    F = S["fields"]
    C = layout["constants"]
    base = int(layout["symbols"]["gSpeciesInfo"]["addr"], 16)
    n = layout["symbols"]["gSpeciesInfo"]["size"] // S["size"]
    if n != C["NUM_SPECIES"] + 1 and n != C["NUM_SPECIES"]:
        die("gSpeciesInfo holds %d records, NUM_SPECIES is %d" % (n, C["NUM_SPECIES"]))
    types = {v: k for k, v in layout["enums"]["TYPE"].items()}
    growth = {v: k for k, v in layout["enums"].get("GROWTH", {}).items()}
    sp_names = {}
    for k, v in sorted(layout["enums"]["SPECIES"].items()):
        sp_names.setdefault(v, []).append(k)
    ab = layout["structs"]["AbilityInfo"]
    ab_base = int(layout["symbols"]["gAbilitiesInfo"]["addr"], 16)

    def ability_name(a):
        o = ab_base + a * ab["size"] + ab["fields"]["name"]["offset"] - ROM_BASE
        return decode(rom[o:o + ab["fields"]["name"]["size"]], charmap)

    def rec(i):
        return base + i * S["size"]

    def ptr(i, f):
        return rd(rom, rec(i) + F[f]["offset"], 4)

    species_count = C["NUM_SPECIES"]
    egg = C["SPECIES_EGG"]

    def enabled(i):
        return field(rom, rec(i), F["baseHP"]) > 0 or i == egg

    def evolutions(i):
        p = ptr(i, "evolutions") if 0 <= i <= species_count and enabled(i) else ptr(0, "evolutions")
        if p == 0:
            p = ptr(0, "evolutions")
        out = []
        E = layout["structs"]["Evolution"]
        while p:
            m = field(rom, p, E["fields"]["method"])
            if m == C["EVOLUTIONS_END"]:
                break
            out.append(field(rom, p, E["fields"]["targetSpecies"]))
            p += E["size"]
        return out

    def forms(i):
        p = ptr(i, "formSpeciesIdTable")
        out = []
        while p:
            v = rd(rom, p, 2)
            if v == C["FORM_SPECIES_END"]:
                break
            out.append(v)
            p += 2
        return out

    # IsSpeciesInGenScope (src/randomizer.c BuildGenScopeMask), replayed in the same order.
    rcount = C["RANDOMIZER_SPECIES_COUNT"]
    max_stages = C.get("RANDOMIZER_MAX_EVO_STAGES", 5)
    obt = layout["symbols"].get("sObtainableToNationalOrder")
    obt_list = []
    if C.get("SEPARATE_OBTAINABLE_DEX") and obt:
        esz = obt["size"] // C["OBTAINABLE_DEX_COUNT"]
        a = int(obt["addr"], 16)
        obt_list = [rd(rom, a + k * esz, esz) for k in range(C["OBTAINABLE_DEX_COUNT"])]
    obt_set = set(obt_list)

    def obtainable(i):
        nat = field(rom, rec(i), F["natDexNum"])
        if not C.get("SEPARATE_OBTAINABLE_DEX"):
            return nat
        return nat != 0 and nat in obt_set

    def regional(i):
        r = rec(i)
        return any(field(rom, r, F[f]) for f in ("isAlolanForm", "isGalarianForm", "isHisuianForm", "isPaldeanForm"))

    mask = set()
    sys.setrecursionlimit(10000)

    def mark(sp, stage):
        if sp == 0 or sp >= rcount or stage > max_stages or sp in mask or not enabled(sp):
            return
        mask.add(sp)
        for f in forms(sp):
            mark(f, stage)
        for t in evolutions(sp):
            mark(t, stage + 1)

    for i in range(1, rcount):
        nat = field(rom, rec(i), F["natDexNum"])
        if nat != C["NATIONAL_DEX_NONE"] and nat <= C["NATIONAL_DEX_DEOXYS"]:
            mark(i, 0)
        elif regional(i) and obtainable(i):
            mark(i, 0)
    changed = True
    while changed:
        changed = False
        for i in range(1, rcount):
            if i in mask or not enabled(i):
                continue
            for t in evolutions(i):
                if t < rcount and t in mask:
                    mark(i, 0)
                    changed = True
                    break

    out = []
    for i in range(species_count):
        r = rec(i)
        names = sp_names.get(i, [])
        # Prefer the constant defined as a plain number over its aliases.
        const = None
        for nm in names:
            if re.fullmatch(r"\d+", layout["_macros"].get(nm, "") or ""):
                const = nm
                break
        if const is None and names:
            const = min(names, key=len)
        nm_f = F["speciesName"]
        o = r + nm_f["offset"] - ROM_BASE
        abilities = []
        af = F["abilities"]
        asz = af["size"] // af["count"]
        for k in range(af["count"]):
            abilities.append(rd(rom, r + af["offset"] + k * asz, asz))
        tf = F["types"]
        tsz = tf["size"] // tf["count"]
        tps = [rd(rom, r + tf["offset"] + k * tsz, tsz) for k in range(tf["count"])]
        entry = dict(
            id=i,
            const=const,
            name=decode(rom[o:o + nm_f["size"]], charmap).rstrip(),
            natDexNum=field(rom, r, F["natDexNum"]),
            baseStats={"hp": field(rom, r, F["baseHP"]), "atk": field(rom, r, F["baseAttack"]),
                       "def": field(rom, r, F["baseDefense"]), "spe": field(rom, r, F["baseSpeed"]),
                       "spa": field(rom, r, F["baseSpAttack"]), "spd": field(rom, r, F["baseSpDefense"])},
            types=[types.get(t, t) for t in tps],
            abilities=[dict(id=a, name=ability_name(a)) for a in abilities],
            growthRate=growth.get(field(rom, r, F["growthRate"]), field(rom, r, F["growthRate"])),
            enabled=enabled(i),
            randomizerMode=field(rom, r, F["randomizerMode"]),
            gen13Scope=i in mask,
            legendary=dict(restricted=field(rom, r, F["isRestrictedLegendary"]),
                           sub=field(rom, r, F["isSubLegendary"]), mythical=field(rom, r, F["isMythical"]),
                           ultraBeast=field(rom, r, F["isUltraBeast"]), paradox=field(rom, r, F["isParadox"])),
        )
        entry["bst"] = sum(entry["baseStats"].values())
        out.append(entry)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--src", required=True, help="built pokehns-expansion directory")
    ap.add_argument("--out", required=True, help="layout JSON to write")
    ap.add_argument("--species-out", help="per-species JSON to write")
    ap.add_argument("--name", default="plain", help="build name recorded in the file")
    a = ap.parse_args()
    src = os.path.abspath(a.src)
    layout, rom, elf, probe = build_layout(src, a.name)
    layout["_macros"] = probe["macros"]
    species = build_species(layout, rom, src) if a.species_out else None
    del layout["_macros"]
    for p, obj in ((a.out, layout), (a.species_out, species)):
        if p is None:
            continue
        os.makedirs(os.path.dirname(os.path.abspath(p)), exist_ok=True)
        with open(p, "w", newline="\n") as f:
            if obj is species:
                f.write("{\n")
                f.write('"buildCrc": "%s",\n"genScope": "src/randomizer.c IsSpeciesInGenScope with tx_Random_GenScope on",\n"species": [\n' % layout["buildCrc"])
                f.write(",\n".join(json.dumps(e, separators=(",", ":"), ensure_ascii=False) for e in obj))
                f.write("\n]}\n")
            else:
                json.dump(obj, f, separators=(",", ":"), ensure_ascii=False)
                f.write("\n")
        print("wrote %s (%d bytes)" % (p, os.path.getsize(p)))
    m = layout["missing"]
    print("buildCrc %s, %d symbols, %d structs, %d scalars, %d enum families"
          % (layout["buildCrc"], len(layout["symbols"]), len(layout["structs"]), len(layout["constants"]), len(layout["enums"])))
    if m["symbols"] or m["structs"] or m["scalars"]:
        print("missing:", json.dumps(m))


if __name__ == "__main__":
    main()
