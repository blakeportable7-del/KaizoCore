#!/usr/bin/env python3
"""Write the Heart & Soul tracker profile's generated parts from the build's layout export.

    python3 tools/hns/gen_tracker.py --layout app/src/main/assets/hns/layout-kaizo.json \
        --species app/src/main/assets/hns/species-kaizo.json --src /root/hns/pokehns-expansion

Writes, from those files only (nothing typed by hand):
  tracker-gba/src/main/kotlin/com/ironmonone/tracker/HnsLayout.kt   addresses, struct fields, constants, labels
  tracker-gba/src/main/resources/hns/packsprites.tsv                HnS species id -> Nat. Dex sprite pack id
  tracker-gba/src/main/resources/hns/maptrainers.tsv                (mapGroup << 8 | mapNum) -> trainer ids

--src is the HnS source tree (WSL), read only for the trainers each map's scripts battle (trainerbattle lines in
data/maps/*/scripts.inc); without it maptrainers.tsv is left as it is. The output is deterministic.
"""
import argparse
import json
import os
import re
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
KT_OUT = os.path.join(REPO, "tracker-gba/src/main/kotlin/com/ironmonone/tracker/HnsLayout.kt")
RES = os.path.join(REPO, "tracker-gba/src/main/resources/hns")
NATDEX_SPECIES = os.path.join(REPO, "tracker-gba/src/main/resources/natdex/species.tsv")
# The pack's forms, each with its National Dex number and form name (the Walking Pals map of the same ids).
NATDEX_FORMS = os.path.join(REPO, "app/src/main/assets/walkingpals-nat/natdex-map.tsv")

# Struct fields the tracker reads; None = every field.
STRUCTS = {
    "SpeciesInfo": None, "MoveInfo": None, "AbilityInfo": None, "ItemInfo": None, "TypeInfo": None,
    "LevelUpMove": None, "Evolution": None, "EvolutionParam": None, "BoxPokemon": None, "Pokemon": None,
    "PokemonSubstruct0": None, "PokemonSubstruct1": None, "PokemonSubstruct2": None, "PokemonSubstruct3": None,
    "BattlePokemon": None, "Volatiles": None, "Bag": None, "ItemSlot": None, "Trainer": None, "TrainerMon": None,
    "TrainerClass": None, "_TrainerBattleParameter": None, "WildPokemonHeader": None, "WildEncounterTypes": None,
    "WildPokemonInfo": None, "WildPokemon": None, "BattleResults": None, "MapHeader": None,
    "RegionMapLocation": None, "WarpData": None,
    "SaveBlock1": ["pos", "location", "playerPartyCount", "money", "bag", "flags", "vars", "gameStats",
                   "dexSeen", "dexCaught"],
    "SaveBlock2": ["playerName", "playerGender", "playerTrainerId", "optionsBattleStyle", "encryptionKey", "rivalName"],
    "BattleScripting": ["battler"],
    "BattleStruct": ["unableToUseMove", "futureSight", "wish", "weatherDuration", "safariCatchFactor"],
    # Battle Details and the last-attack line (2026-10-05).
    "SideTimer": None, "FieldTimer": None, "ProtectStruct": ["physicalDmg", "specialDmg"],
    # The Nuzlocke's statics (2026-10-05): a scripted wild battle returns through CB2_EndScriptedWildBattle.
    # Play as your Pokemon (Overworld.kt's table and the test that holds sprite_core.h's constants to this build).
    "Main": ["callback2", "oamBuffer", "savedCallback"],
    "Sprite": ["oam", "x", "y", "x2", "y2", "centerToCornerVecX", "centerToCornerVecY", "inUse", "coordOffsetEnabled", "invisible"],
    "ObjectEvent": ["facingDirection"],
    "PlayerAvatar": ["runningState", "tileTransitionState", "spriteId", "objectEventId"],
    # The Survival heal counter (2026-10-06, tracker-gba HnsHeals): the script running now and its call stack.
    "ScriptContext": ["stackDepth", "scriptPtr", "stack"],
    # The log viewer's trainer portraits (rc38.1).
    "TrainerSprite": ["frontPic", "palette"],
}

# Items the tracker's shared tables name by the vanilla Gen 3 id (HEAL_ITEMS, the catch-rate balls, EvoText's
# stones) or by the Nat. Dex ROM's id (EvoText's Nat. Dex methods): the HnS item with the same constant name maps
# onto it. Vanilla ids from pokeemerald include/constants/items.h; Nat. Dex ids as EvoText.NAT_DEX names them.
VANILLA_ITEMS = {
    "ITEM_MASTER_BALL": 1, "ITEM_ULTRA_BALL": 2, "ITEM_GREAT_BALL": 3, "ITEM_POKE_BALL": 4, "ITEM_SAFARI_BALL": 5,
    "ITEM_NET_BALL": 6, "ITEM_DIVE_BALL": 7, "ITEM_NEST_BALL": 8, "ITEM_REPEAT_BALL": 9, "ITEM_TIMER_BALL": 10,
    "ITEM_LUXURY_BALL": 11, "ITEM_PREMIER_BALL": 12,
    "ITEM_SUN_STONE": 93, "ITEM_MOON_STONE": 94, "ITEM_FIRE_STONE": 95, "ITEM_THUNDER_STONE": 96,
    "ITEM_WATER_STONE": 97, "ITEM_LEAF_STONE": 98,
    "ITEM_SHINY_STONE": 99, "ITEM_DUSK_STONE": 100, "ITEM_DAWN_STONE": 101, "ITEM_ICE_STONE": 102,
    "ITEM_DUBIOUS_DISC": 89, "ITEM_RAZOR_CLAW": 90, "ITEM_RAZOR_FANG": 91, "ITEM_LINKING_CORD": 92,
    "ITEM_KINGS_ROCK": 187, "ITEM_DEEP_SEA_TOOTH": 192, "ITEM_DEEP_SEA_SCALE": 193, "ITEM_METAL_COAT": 199,
    "ITEM_DRAGON_SCALE": 201, "ITEM_UPGRADE": 218,
}

# The evolution items EvoText knows, by the HnS constant: the method key it reads.
EVO_ITEM_METHOD = {
    "ITEM_THUNDER_STONE": "THUNDER", "ITEM_FIRE_STONE": "FIRE", "ITEM_WATER_STONE": "WATER", "ITEM_MOON_STONE": "MOON",
    "ITEM_LEAF_STONE": "LEAF", "ITEM_SUN_STONE": "SUN", "ITEM_SHINY_STONE": "SHINY", "ITEM_DUSK_STONE": "DUSK",
    "ITEM_DAWN_STONE": "DAWN", "ITEM_ICE_STONE": "ICE", "ITEM_METAL_COAT": "METAL_COAT", "ITEM_KINGS_ROCK": "KINGS_ROCK",
    "ITEM_DRAGON_SCALE": "DRAGON_SCALE", "ITEM_UPGRADE": "UPGRADE", "ITEM_DUBIOUS_DISC": "DUBIOUS_DISC",
    "ITEM_RAZOR_CLAW": "RAZOR_CLAW", "ITEM_RAZOR_FANG": "RAZOR_FANG", "ITEM_LINKING_CORD": "LINKING_CORD",
}

# Trainer classes the tracker groups (TrainerData's Gym / Elite4 / Rival / Boss), HnS's own classes.
CLASS_GROUPS = {
    "TRAINER_CLASS_LEADER_HNS": "Gym", "TRAINER_CLASS_LEADER_KANTO_HNS": "Gym",
    "TRAINER_CLASS_ELITE_FOUR_HNS": "Elite4", "TRAINER_CLASS_CHAMPION_HNS": "Elite4",
    "TRAINER_CLASS_RIVAL_HNS": "Rival", "TRAINER_CLASS_ROCKET_ADMIN_HNS": "Boss",
}

# GachaMon's prize cards (TrainerData.getCommonTrainers in the PC tracker): the story's rivals, gym leaders, Elite Four,
# Champion and Red, and the Rocket executives, each label to its trainers by constant name (every story variant of a
# fight, none of the post-game rematches named _POSTOBC_). Written to hns/commontrainers.tsv in this order.
COMMON_TRAINERS = (
    [("Rival %d" % n, r"TRAINER_RIVAL_(CHIKORITA|CYNDAQUIL|TOTODILE)_%d_HNS" % n) for n in range(1, 8)] +
    [(name, r"TRAINER_%s(_\d+)*_HNS" % const) for name, const in (
        ("Falkner", "FALKNER"), ("Bugsy", "BUGSY"), ("Whitney", "WHITNEY"), ("Morty", "MORTY"), ("Chuck", "CHUCK"),
        ("Jasmine", "JASMINE"), ("Pryce", "PRYCE"), ("Clair", "CLAIR"), ("Brock", "BROCK"), ("Misty", "MISTY"),
        ("Lt. Surge", "LTSURGE"), ("Erika", "ERIKA"), ("Janine", "JANINE"), ("Sabrina", "SABRINA"), ("Blaine", "BLAINE"),
        ("Blue", "BLUE"), ("Will", "WILL"), ("Koga", "KOGA"), ("Bruno", "BRUNO"), ("Karen", "KAREN"), ("Lance", "LANCE"),
        ("Red", "RED"), ("Proton", "PROTON"), ("Archer", "ARCHER"), ("Petrel", "PETREL"), ("Ariana", "ARIANA"),
        ("Giovanni", "GIOVANNI"))]
)

# The run is won by beating Red at the top of Mt. Silver (Blake, 2026-10-05: "red"), either of his two battles there;
# Lance stays an ordinary Champion fight.
FINAL_TRAINERS = ["TRAINER_RED_HNS", "TRAINER_RED_POSTOBC_HNS"]

# Elm's lab, where the three starter balls stand on the table.
LAB_MAP = "MAP_NEW_BARK_TOWN_LAB_HNS"


def starter_balls(src, L):
    """The lab's three balls, left to right by their x on the table (data/maps/NewBarkTown_Lab_hns/map.json), each with
    the address of the species its choice script sets (setvar PLAYER_STARTER_SPECIES, in the layout's script mons)."""
    m = json.load(open(os.path.join(src, "data/maps/NewBarkTown_Lab_hns/map.json"), encoding="utf-8"))
    balls = sorted((o["x"], o["script"]) for o in m["object_events"] if o.get("graphics_id") == "OBJ_EVENT_GFX_POKE_BALL")
    sets = {e["label"]: e for e in L["scripts"]["mons"] if e["kind"] == "setvar-species" and "NewBarkTown_Lab" in e["file"]}
    out = []
    for x, script in balls:
        e = sets.get(script + "_Choice")
        if e is None:
            raise SystemExit("no setvar-species for the lab ball %s" % script)
        out.append(int(e["valueAddr"], 16))
    if len(out) != 3:
        raise SystemExit("the lab has %d starter balls, not 3" % len(out))
    return out

KOTLIN_KEYWORDS = {"as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
                   "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
                   "typeof", "val", "var", "when", "while"}


def ident(n):
    return "`%s`" % n if n in KOTLIN_KEYWORDS else n


def hexl(v):
    return "0x%08XL" % v


def field_line(name, fd):
    off, size = fd["offset"], fd["size"]
    shift, width = fd.get("bits", [0, size * 8])
    count = fd.get("count", 1)
    signed = "true" if fd.get("signed") else "false"
    return "        val %s = HnsField(%d, %d, %d, %d, %s, %d)" % (ident(name), off, size, shift, width, signed, count)


def norm(name):
    s = name.replace("♀", "f").replace("♂", "m")
    s = unicodedata.normalize("NFKD", s)
    return "".join(c for c in s.lower() if c.isalnum())


FORM_SUFFIX = [("_MEGA_X", "X"), ("_MEGA_Y", "Y"), ("_MEGA", "M"), ("_PRIMAL", "P"), ("_ALOLA", "A"),
               ("_GALAR", "G"), ("_HISUI", "H")]


# A form's name as HnS spells it (the end of its constant) where the pack's map spells it otherwise.
FORM_ALIASES = {"SANDY": "sand", "BLUE_STRIPED": "blue", "WHITE_STRIPED": "white", "PAU": "pa-u", "LOW_KEY": "lowkey",
                "SPIKY_EARED": "spiky", "F": "female", "10_AURA_BREAK": "10", "10_POWER_CONSTRUCT": "10"}
# Forms the pack draws under a name of its own, by the HnS constant: Partner Pikachu and Eevee (Let's Go's, HnS's
# _STARTER), Battle Bond Greninja, and Zacian and Zamazenta Crowned.
FORM_PACK_NAMES = {"SPECIES_PIKACHU_STARTER": "Pikachu-P", "SPECIES_EEVEE_STARTER": "Eevee-P",
                   "SPECIES_GRENINJA_BOND": "Greninja-B", "SPECIES_ZACIAN_CROWNED": "Zacian-C",
                   "SPECIES_ZAMAZENTA_CROWNED": "Zamazenta-C"}


def pack_sprites(species):
    """HnS species id -> the Nat. Dex pack's id for the same Pokemon (its form where the pack has one).

    A form is matched to the pack's picture of that form by its National Dex number and its form name: the end of its
    HnS constant past its species' (SPECIES_ROTOM_HEAT is Rotom's "heat", SPECIES_DARMANITAN_GALAR_ZEN Darmanitan's
    "galar-zen"), as the pack's map (natdex-map.tsv) names its forms; every Minior core is the pack's one core picture.
    A form the pack has no picture of is drawn as its base species."""
    pack = {}
    for line in open(NATDEX_SPECIES, encoding="utf-8"):
        i, _, n = line.rstrip("\n").partition("\t")
        if i.isdigit() and n and n != "none":
            pack.setdefault(norm(n), int(i))
    # (national number, form name) -> pack id, from the map's rows: id, name, national, form, set, key, note.
    pack_forms = {}
    for line in open(NATDEX_FORMS, encoding="utf-8"):
        if line.startswith("#"):
            continue
        c = line.rstrip("\n").split("\t")
        if len(c) >= 4 and c[0].isdigit() and c[2].isdigit() and c[3]:
            pack_forms.setdefault((int(c[2]), c[3]), int(c[0]))
    base_of = {}
    for e in species:
        if e["natDexNum"] and e["natDexNum"] not in base_of:
            base_of[e["natDexNum"]] = e
    # Each species by its name, at its own National Dex number: a regional form is numbered past 1025 in HnS, so its
    # forms (Darmanitan's Galarian Zen) are found through the species it is a form of.
    root_of = {}
    for e in species:
        if 0 < e["natDexNum"] <= 1025:
            root_of.setdefault(norm(e["name"]), e)

    def form_pack(e, base):
        if e["const"] in FORM_PACK_NAMES:
            return pack.get(norm(FORM_PACK_NAMES[e["const"]]))
        root = root_of.get(norm(base["name"].split("-")[0])) or base
        const = e["const"][len("SPECIES_"):]
        if const.startswith("MINIOR_CORE_"):
            return pack_forms.get((root["natDexNum"], "red"))
        # Past the species' constant (SPECIES_ROTOM), or its name where its own constant names its form
        # (SPECIES_CASTFORM_NORMAL), shortest first: the Galarian Zen Darmanitan is Darmanitan's "galar-zen", not Galarian
        # Darmanitan's "zen".
        named = re.sub("[^A-Z0-9]+", "_", root["name"].upper()).strip("_") + "_"
        for prefix in sorted({root["const"][len("SPECIES_"):] + "_", base["const"][len("SPECIES_"):] + "_", named}, key=len):
            if const.startswith(prefix):
                tail = const[len(prefix):]
                key = FORM_ALIASES.get(tail, tail.lower().replace("_", "-"))
                if (root["natDexNum"], key) in pack_forms:
                    return pack_forms[(root["natDexNum"], key)]
        return None

    out, gaps, forms_as_base = {}, [], []
    for e in species:
        if not e["id"] or not e["natDexNum"] or e["const"] == "SPECIES_EGG":
            continue
        base = base_of[e["natDexNum"]]
        b = pack.get(norm(base["name"]))
        if b is None:
            gaps.append(e["const"])
            continue
        pid = b
        if e is not base:
            for suf, letter in FORM_SUFFIX:
                if e["const"].endswith(suf) or (suf + "_") in e["const"]:
                    pid = pack.get(norm(base["name"] + "-" + letter), b)
                    break
            if pid == b:
                pid = form_pack(e, base) or b
            if pid == b:
                forms_as_base.append(e["const"])
        out[e["id"]] = pid
    return out, gaps, forms_as_base


def map_trainers(src, maps, trainers):
    """(group << 8 | num) -> trainer ids, from each HnS map's trainerbattle lines, in file order."""
    out = {}
    root = os.path.join(src, "data", "maps")
    for d in sorted(os.listdir(root)):
        if not d.endswith("_hns"):
            continue
        mj, sc = os.path.join(root, d, "map.json"), os.path.join(root, d, "scripts.inc")
        if not (os.path.isfile(mj) and os.path.isfile(sc)):
            continue
        mid = json.load(open(mj, encoding="utf-8")).get("id")
        if mid not in maps:
            continue
        ids = []
        for line in open(sc, encoding="utf-8"):
            m = re.match(r"\s*trainerbattle\w*\s+(.*)", line)
            if not m:
                continue
            for t in re.findall(r"\bTRAINER_\w+", m.group(1)):
                if t in trainers and trainers[t] not in ids:
                    ids.append(trainers[t])
        if ids:
            out[maps[mid]] = ids
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--layout", required=True)
    ap.add_argument("--species", required=True)
    ap.add_argument("--src", help="HnS source tree, for the trainers on each map")
    a = ap.parse_args()
    L = json.load(open(a.layout, encoding="utf-8"))
    SP = json.load(open(a.species, encoding="utf-8"))
    if SP["buildCrc"] != L["buildCrc"]:
        raise SystemExit("species file is for build %s, layout for %s" % (SP["buildCrc"], L["buildCrc"]))
    E, C = L["enums"], L["constants"]
    o = []
    w = o.append
    w("package com.ironmonone.tracker")
    w("")
    w("// GENERATED by tools/hns/gen_tracker.py from app/src/main/assets/hns/layout-kaizo.json. Do not hand-edit:")
    w("// rebuild the layout (tools/hns/layout.py) and run the generator again.")
    w("")
    w("/** The Heart & Soul Kaizo comfort build's layout (CRC %s): every address, struct field and constant the" % L["buildCrc"])
    w(" *  tracker reads, exactly as the build's own symbols and compiled structs give them. */")
    w("@Suppress(\"ObjectPropertyName\", \"PropertyName\", \"unused\", \"SpellCheckingInspection\")")
    w("internal object HnsLayout {")
    w("    const val BUILD_CRC = 0x%sL" % L["buildCrc"])
    w("")
    w("    // Symbols (ELF st_value: a code address carries its thumb bit, as gBattleMainFunc holds it).")
    for n, s in sorted(L["symbols"].items()):
        w("    const val %s = %s" % (n, hexl(int(s["addr"], 16))))
    w("")
    w("    // Table record counts (the symbol's size over its element's).")
    for n, t in sorted(L["tables"].items()):
        w("    const val %s_COUNT = %d" % (n, t["count"]))
    w("")
    w("    // Script labels (battle and field), [addr, end): end is the next symbol's address.")
    for n, s in sorted(L["labels"].items()):
        w("    val %s = %s until %s" % (n, hexl(int(s["addr"], 16)), hexl(int(s["end"], 16))))
    w("")
    w("    // Scalar constants.")
    for n, v in sorted(C.items()):
        w("    const val %s = %d" % (n, v))
    w("")
    w("    // Types (TYPE_NONE is 0, so every type is one above Gen 3's own id, Fairy 19).")
    for n, v in sorted(E["TYPE"].items(), key=lambda kv: (kv[1], kv[0])):
        if n.startswith("TYPE_SIDE_HAZARD"):
            continue
        w("    const val %s = %d" % (n, v))
    for n, v in sorted(E["FLAG_BADGE"].items(), key=lambda kv: kv[1]):
        w("    const val %s = %d" % (n, v))
    sp = E["SPECIES"]
    w("    const val SPECIES_SHEDINJA = %d" % sp["SPECIES_SHEDINJA"])
    w("    const val SPECIES_UNOWN = %d" % sp["SPECIES_UNOWN"])
    it = E["ITEM"]
    for n in ("ITEM_OLD_ROD", "ITEM_GOOD_ROD", "ITEM_SUPER_ROD", "ITEM_X_SP_DEF", "ITEM_GUARD_SPEC", "ITEM_DIRE_HIT"):
        w("    const val %s = %d" % (n, it[n]))
    for n in ("EVO_LEVEL", "EVO_TRADE", "EVO_ITEM", "IF_MIN_FRIENDSHIP", "IF_GENDER", "IF_HOLD_ITEM"):
        w("    const val %s = %d" % (n, E["EVO" if n.startswith("EVO") else "IF"][n]))
    w("    const val MON_FEMALE_GENDER = %d" % C["MON_FEMALE"])
    # Battle Details (2026-10-05): the side and field status bits, and the battle environments by name.
    for fam in ("SIDE_STATUS", "STATUS_FIELD", "BATTLE_ENVIRONMENT"):
        for n, v in sorted(E.get(fam, {}).items(), key=lambda kv: (kv[1], kv[0])):
            w("    const val %s = %d" % (n, v))
    # Catch Rates (2026-10-06, tracker-gba HnsCatch): the balls by their BALL_ id (ItemInfo.secondaryId), and what
    # ComputeBallData and ComputeCaptureOdds compare against.
    for n, v in sorted(E["BALL"].items(), key=lambda kv: (kv[1], kv[0])):
        if not re.match(r"BALL_(ROTATE|AFFINE)", n):
            w("    const val %s = %d" % (n, v))
    for n in ("TIME_EVENING", "TIME_NIGHT"):
        w("    const val %s = %d" % (n, E["TIME"][n]))
    w("    const val BATTLE_TYPE_SAFARI = %d" % E["BATTLE_TYPE"]["BATTLE_TYPE_SAFARI"])
    w("    const val SPECIES_CELEBI = %d" % sp["SPECIES_CELEBI"])
    w("    const val ABILITY_COMATOSE = %d" % E["ABILITY"]["ABILITY_COMATOSE"])
    for n in ("ITEM_MOON_STONE", "ITEM_SAFARI_BALL"):
        w("    const val %s = %d" % (n, it[n]))
    w("")
    w("    /** HnS item id to the id the shared tables use for the same item (vanilla Gen 3, or Nat. Dex's for the newer evolution items). */")
    w("    val VANILLA_ITEM: Map<Int, Int> = mapOf(")
    w(",\n".join("        %d to %d" % (it[n], v) for n, v in sorted(VANILLA_ITEMS.items(), key=lambda kv: it.get(kv[0], 0)) if n in it))
    w("    )")
    w("    /** Evolution items EvoText names, by the HnS item id. */")
    w("    val EVO_ITEM_METHOD: Map<Int, String> = mapOf(")
    w(",\n".join("        %d to \"%s\"" % (it[n], v) for n, v in sorted(EVO_ITEM_METHOD.items(), key=lambda kv: it.get(kv[0], 0)) if n in it))
    w("    )")
    w("    /** The X items (battle items), by the HnS item id. */")
    w("    val X_ITEMS: Set<Int> = setOf(%s)" % ", ".join(str(v) for n, v in sorted(it.items(), key=lambda kv: kv[1]) if n.startswith("ITEM_X_")))
    tc = E["TRAINER_CLASS"]
    w("    /** Trainer classes the tracker groups, by the HnS class id. */")
    w("    val CLASS_GROUPS: Map<Int, String> = mapOf(%s)" % ", ".join('%d to "%s"' % (tc[n], g) for n, g in CLASS_GROUPS.items()))
    tr = E["TRAINER"]
    w("    /** The run is won by beating one of these (Red on Mt. Silver). */")
    w("    val FINAL_TRAINERS: Set<Int> = setOf(%s)" % ", ".join(str(tr[n]) for n in FINAL_TRAINERS))
    if not a.src:
        raise SystemExit("--src is needed for the lab's starter balls")
    safari = sorted(v for n, v in E["MAP"].items() if n.endswith("_HNS") and "SAFARI_ZONE" in n
                    and not re.search(r"GATE|ENTRANCE|ENTERANCE|POKEMON_CENTER|INDOOR", n))
    w("    /** The Safari Zones' own areas, (mapGroup << 8) | mapNum: not the gates, entrances or the indoor rooms. */")
    w("    val SAFARI_MAP_IDS: Set<Int> = setOf(%s)" % ", ".join(str(v) for v in safari))
    w("    /** Elm's lab, (mapGroup << 8) | mapNum. */")
    w("    const val LAB_MAP_ID = %d" % E["MAP"][LAB_MAP])
    w("    /** The species word each starter ball's choice script sets, the balls left to right on the table. */")
    w("    val STARTER_BALL_SPECIES: LongArray = longArrayOf(%s)" % ", ".join(hexl(v) for v in starter_balls(a.src, L)))
    rivals = []
    for n, v in sorted(tr.items(), key=lambda kv: kv[1]):
        m = re.match(r"TRAINER_RIVAL_(CHIKORITA|CYNDAQUIL|TOTODILE)_\d+_HNS$", n)
        if m:
            rivals.append('%d to "%s"' % (v, m.group(1)))
    w("    /** The rival's battles, by the starter the rival took (Tracker.Data.whichRival). */")
    w("    val RIVALS: Map<Int, String> = mapOf(%s)" % ", ".join(rivals))
    w("")
    w("    // Struct sizes and fields: HnsField(offset, size, shift, width, signed, count).")
    for sname, want in STRUCTS.items():
        st = L["structs"][sname]
        kname = sname.lstrip("_")
        w("    object %s {" % kname)
        w("        const val SIZE = %d" % st["size"])
        for f, fd in st["fields"].items():
            if want is None or f in want:
                w(field_line(f, fd))
        missing = [f for f in (want or []) if f not in st["fields"]]
        if missing:
            raise SystemExit("%s has no %s" % (sname, missing))
        w("    }")
    w("}")
    text = "\n".join(o) + "\n"
    open(KT_OUT, "w", encoding="utf-8", newline="\n").write(text)
    print("wrote %s (%d bytes)" % (KT_OUT, len(text)))

    os.makedirs(RES, exist_ok=True)
    sprites, gaps, forms = pack_sprites(SP["species"])
    p = os.path.join(RES, "packsprites.tsv")
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write("# HnS species id -> Nat. Dex sprite pack id (assets/gbasprites), by National Dex number and form. "
                "GENERATED by tools/hns/gen_tracker.py.\n")
        for k in sorted(sprites):
            f.write("%d\t%d\n" % (k, sprites[k]))
    print("wrote %s: %d species, %d without a pack picture %s, %d forms drawn as their base"
          % (p, len(sprites), len(gaps), gaps[:12], len(forms)))

    p = os.path.join(RES, "commontrainers.tsv")
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write("# GachaMon's common trainers (prize cards): label -> trainer ids. GENERATED by tools/hns/gen_tracker.py.\n")
        for label, rx in COMMON_TRAINERS:
            ids = sorted(v for n, v in tr.items() if re.fullmatch(rx, n))
            if not ids:
                raise SystemExit("no trainer for %s (%s)" % (label, rx))
            f.write("%s\t%s\n" % (label, ",".join(str(x) for x in ids)))
    print("wrote %s: %d labels" % (p, len(COMMON_TRAINERS)))

    if a.src:
        mt = map_trainers(a.src, E["MAP"], tr)
        p = os.path.join(RES, "maptrainers.tsv")
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write("# (mapGroup << 8 | mapNum) -> the trainers its scripts battle, from data/maps/*_hns/scripts.inc. "
                    "GENERATED by tools/hns/gen_tracker.py.\n")
            for k in sorted(mt):
                f.write("%d\t%s\n" % (k, ",".join(str(x) for x in mt[k])))
        print("wrote %s: %d maps, %d trainers" % (p, len(mt), sum(len(v) for v in mt.values())))


if __name__ == "__main__":
    main()
