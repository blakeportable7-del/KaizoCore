"""Generates and checks tracker-gba/src/main/resources/nuzlocke/statics-gen4.tsv (2026-09-29).

    python tools/nuzlocke/gen4_statics.py            # writes the file (LF; then run to_crlf.py on it)
    python tools/nuzlocke/gen4_statics.py --check    # re-reads the ROM dump and pret, compares with the shipped file

A row says: a wild battle at PLACE at exactly LEVEL is a set battle (a static), not the area's first encounter.
PLACE is the area name the DS tracker reports for the map the battle happens in: the row of the tracker's location
table (gen4/locations-pt.tsv for Diamond, Pearl and Platinum, gen4/locations-hgss.tsv for HeartGold and SoulSilver)
for that map's header id, spelled exactly as the table spells it (typos included).

Every row is tied to the game's own data and --check re-reads that data:
  * Platinum (`pt`): the script line that starts the battle in pret pokeplatinum (the dump is byte-identical to what
    that commit builds), the map header whose scriptsArchiveID is that script file, and the header's id in
    generated/map_headers.txt looked up in the tracker table.
  * HeartGold and SoulSilver (`hgss`, `hg`, `ss`): the script line in pret pokeheartgold (the game sets
    FLAG_ENGAGING_STATIC_POKEMON right before each static WildBattle), the map header whose scriptsBank is that script
    file, its id in include/constants/maps.h looked up in the tracker table. `hg` and `ss` rows exist only where the two
    versions use different levels for the same place.
  * Diamond and Pearl (`dp`): pret has no script source for them, so the Diamond dump is read directly: the script
    archive fielddata/script/scr_seq_release.narc, the wild-battle command (opcode 0x124 WildBattle, 0x2BD
    LegendaryBattle, numbers taken from the comments of pokediamond's arm9/src/scrcmd.c) with its species and level
    words at the recorded offset, and the map header table in arm9 whose scripts_bank names that script file. There is
    no Pearl dump: pret builds Pearl from the same script archive, and a species that differs between the versions
    (Dialga, Palkia) is read from a script variable set by GetGameVersion.

A row whose level an ordinary encounter of the area can also be (WALKING: grass, cave floor, swarm, radio, Poke Radar,
dual-slot, Rock Smash, Headbutt; or surf and fishing), recomputed by gen4_encounters.py from the same sources, carries the
national dex number of its species in a sixth column, so the reader (NuzlockeStatics) does not take an ordinary encounter of
another species for the static. EXCLUDED lists the rows with a walking collision, ROWS the rest, and --check proves each
collision still exists. No row shares its own species with an ordinary encounter at its level.
"""
import os
import re
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen4_common as C  # noqa: E402
import gen4_encounters as E  # noqa: E402
import gen4_nds as N  # noqa: E402

import struct  # noqa: E402
import textwrap  # noqa: E402

FILENAME = "statics-gen4.tsv"

DP_SCRIPT_NARC = "fielddata/script/scr_seq_release.narc"
LEG, WILD = 0x2BD, 0x124   # pokediamond arm9/src/scrcmd.c: ScrCmd_LegendaryBattle // 02BD, ScrCmd_WildBattle // 0124
DP_FRIDAY = (0x234, 0x4000, 0x11, 0x4000, 5)   # GetDayOfWeek var; Compare var, 5
DP_SPIRITOMB = (0x214, 0x800C, 0x11, 0x800C, 32)   # GetSpiritombTalkCounter var; Compare var, 32
HG_SCR = "files/fielddata/script/scr_seq/"
STATIC_FLAG = r"SetFlag\s+FLAG_ENGAGING_STATIC_POKEMON\s+"
VERSION_HG = r"GetGameVersion\s+\w+\s+Compare\s+\w+,\s*7\s+GoToIfNe\s+\w+\s+"   # 7 = VERSION_HEARTGOLD (config.h)


def _cmd(cmd, *args):
    """A regex for one script line: the command and its comma separated arguments."""
    return r"\b%s\s+%s" % (cmd, r",\s*".join(re.escape(a) for a in args))


def PT(script, cmd, species, level, hdr, extra=()):
    return dict(kind="pt", path="res/field/scripts/" + script, hdr=hdr, level=level, sp=["SPECIES_" + species],
                re=[_cmd(cmd, "SPECIES_" + species, str(level))] + list(extra))


def HG(script, species, level, hdr, extra=()):
    return dict(kind="hg", path=HG_SCR + script, hdr=hdr, level=level, sp=["SPECIES_" + species],
                re=[STATIC_FLAG + _cmd("WildBattle", "SPECIES_" + species, str(level), "0")] + list(extra))


def HGX(script, hdr, level, *rx):
    """A HeartGold/SoulSilver script check with hand written regexes; `level` is what the row must say."""
    return dict(kind="hg", path=HG_SCR + script, hdr=hdr, level=level, re=list(rx))


def DP(fid, off, op, species, level, hdr, also=()):
    return dict(kind="dp", file=fid, off=off, words=(op, species, level), hdr=hdr, level=level, also=list(also),
                sp=[species] if species < 0x4000 else [])


def TEXT(repo, path, *rx):
    return dict(kind="text", repo=repo, path=path, re=list(rx))


def _hg_split(script, hdr, species_id, first, second):
    """Ho-Oh and Lugia: the script sets the species, then the level by GetGameVersion (7 = HeartGold)."""
    return dict(kind="hg", path=HG_SCR + script, hdr=hdr, level={"hg": first, "ss": second}, sp=[species_id],
                re=[r"SetVar\s+VAR_TEMP_x400A,\s*%d\s" % species_id,
                    VERSION_HG + r"SetVar\s+VAR_SPECIAL_x8004,\s*%d\s+GoTo\s+\w+\s+\w+:\s+SetVar\s+VAR_SPECIAL_x8004,\s*%d\s"
                    % (first, second),
                    STATIC_FLAG + _cmd("WildBattle", "VAR_TEMP_x400A", "VAR_SPECIAL_x8004", "0")])


# ROWS: (game, place, level, species, note, checks). The note carries no collision sentence: build() adds it from the
# recomputed ordinary slots.
ROWS = [
    # ---- Diamond and Pearl (Diamond dump)
    ("dp", "Acuity Cavern", 50, "Uxie", "fixed battle in the cave under Lake Acuity",
     [DP(352, 70, LEG, 480, 50, 319)]),
    ("dp", "Flower Paradise", 30, "Shaymin", "event Pokemon (the research doc: the Oak's Letter event)",
     [DP(302, 70, LEG, 492, 30, 274)]),
    ("dp", "Hall of Origin", 80, "Arceus", "event Pokemon (the research doc: the Azure Flute event)",
     [DP(232, 96, LEG, 493, 80, 510)]),
    ("dp", "Newmoon Island", 40, "Darkrai",
     "event Pokemon (the research doc: the Member Card event); level 40 here, 50 in Platinum",
     [DP(354, 62, LEG, 491, 40, 321)]),
    ("dp", "Route 209", 25, "Spiritomb",
     "scripted battle at the Hallowed Tower once the Spiritomb counter of Underground talks reaches 32",
     [DP(406, 350, WILD, 442, 25, 356, also=[(258, DP_SPIRITOMB)])]),
    ("dp", "Snowpoint Temple", 70, "Regigigas", "fixed battle at the bottom of the temple; level 70 here, 1 in Platinum",
     [DP(309, 146, LEG, 486, 70, 283)]),
    ("dp", "Spear Pillar", 47, "Dialga (Diamond) or Palkia (Pearl)",
     "story battle; the script picks Dialga in Diamond and Palkia in Pearl",
     [DP(230, 3808, LEG, 0x8004, 47, 220, also=[(4679, (LEG, 0x8004, 47))])]),
    ("dp", "Stark Mountain", 70, "Heatran", "fixed battle in the last room; level 70 here, 50 in Platinum",
     [DP(278, 380, LEG, 485, 70, 265)]),
    ("dp", "Turnback Cave", 70, "Giratina", "fixed battle in the Giratina room; level 70 here, 47 in Platinum",
     [DP(283, 93, LEG, 487, 70, 270)]),
    ("dp", "Valley Windworks", 22, "Drifloon",
     "fixed battle, Fridays only (the script compares the day of week with 5); level 22 here, 15 in Platinum",
     [DP(210, 468, LEG, 425, 22, 200, also=[(91, DP_FRIDAY)])]),
    ("dp", "Verity Cavern", 50, "Azelf",
     "fixed battle in Valor Cavern; the tracker's table names that map Verity Cavern by mistake",
     [DP(348, 142, LEG, 482, 50, 316)]),
    # ---- Platinum
    ("pt", "Acuity Cavern", 50, "Uxie", "fixed battle in the cave under Lake Acuity",
     [PT("scripts_acuity_cavern.s", "StartLegendaryBattle", "UXIE", 50, "MAP_HEADER_ACUITY_CAVERN")]),
    ("pt", "Distortion World", 47, "Giratina (Origin Forme)",
     "story battle in the Giratina room",
     [PT("scripts_distortion_world_giratina_room.s", "StartGiratinaOriginBattle", "GIRATINA", 47,
         "MAP_HEADER_DISTORTION_WORLD_GIRATINA_ROOM")]),
    ("pt", "Flower Paradise", 30, "Shaymin", "event Pokemon, needs the Oak's Letter and the Shaymin event",
     [PT("scripts_flower_paradise.s", "StartFatefulEncounter", "SHAYMIN", 30, "MAP_HEADER_FLOWER_PARADISE",
         extra=[r"CheckItem\s+ITEM_OAKS_LETTER", r"CheckDistributionEvent\s+DISTRIBUTION_EVENT_SHAYMIN"])]),
    ("pt", "Hall of Origin", 80, "Arceus", "event Pokemon, needs the Arceus event",
     [PT("scripts_hall_of_origin.s", "StartLegendaryBattle", "ARCEUS", 80, "MAP_HEADER_HALL_OF_ORIGIN",
         extra=[r"CheckDistributionEvent\s+DISTRIBUTION_EVENT_ARCEUS"])]),
    ("pt", "Iceberg Ruins", 30, "Regice", "fixed battle; needs the game cleared and an event Regigigas in the party",
     [PT("scripts_iceberg_ruins.s", "StartLegendaryBattle", "REGICE", 30, "MAP_HEADER_ICEBERG_RUINS",
         extra=[r"FLAG_GAME_COMPLETED", r"CheckPartyHasFatefulEncounterRegigigas"])]),
    ("pt", "Iron Ruins", 30, "Registeel", "fixed battle; needs the game cleared and an event Regigigas in the party",
     [PT("scripts_iron_ruins.s", "StartLegendaryBattle", "REGISTEEL", 30, "MAP_HEADER_IRON_RUINS",
         extra=[r"FLAG_GAME_COMPLETED", r"CheckPartyHasFatefulEncounterRegigigas"])]),
    ("pt", "Newmoon Island", 50, "Darkrai",
     "event Pokemon, needs the Member Card and the Darkrai event; level 50 here, 40 in Diamond and Pearl",
     [PT("scripts_newmoon_island_forest.s", "StartLegendaryBattle", "DARKRAI", 50, "MAP_HEADER_NEWMOON_ISLAND_FOREST",
         extra=[r"CheckItem\s+ITEM_MEMBER_CARD", r"CheckDistributionEvent\s+DISTRIBUTION_EVENT_DARKRAI"])]),
    ("pt", "Old Chateau", 20, "Rotom",
     "scripted battle at the TV, night only and once a day; level 20 here, 15 in Diamond and Pearl",
     [PT("scripts_old_chateau_back_middle_west_room.s", "StartWildBattle", "ROTOM", 20,
         "MAP_HEADER_OLD_CHATEAU_BACK_MIDDLE_WEST_ROOM",
         extra=[r"TIMEOFDAY_NIGHT", r"FLAG_DAILY_BATTLED_OLD_CHATEAU_ROTOM"])]),
    ("pt", "Route 209", 25, "Spiritomb",
     "scripted battle at the Hallowed Tower after the Odd Keystone and 32 Underground talks",
     [PT("scripts_route_209.s", "StartWildBattle", "SPIRITOMB", 25, "MAP_HEADER_ROUTE_209",
         extra=[r"ITEM_ODD_KEYSTONE", r"GoToIfGe\s+VAR_RESULT,\s*32,\s*Route209_EncounterSpiritomb"])]),
    ("pt", "Rpck Peak Ruins", 30, "Regirock",
     "fixed battle; needs the game cleared and an event Regigigas in the party; the tracker's table spells this map Rpck Peak Ruins",
     [PT("scripts_rock_peak_ruins.s", "StartLegendaryBattle", "REGIROCK", 30, "MAP_HEADER_ROCK_PEAK_RUINS",
         extra=[r"FLAG_GAME_COMPLETED", r"CheckPartyHasFatefulEncounterRegigigas"])]),
    ("pt", "Snowpoint Temple", 1, "Regigigas", "fixed battle at level 1; needs Regirock, Regice and Registeel in the party",
     [PT("scripts_snowpoint_temple_b5f.s", "StartLegendaryBattle", "REGIGIGAS", 1, "MAP_HEADER_SNOWPOINT_TEMPLE_B5F",
         extra=[r"CheckHasAllLegendaryTitansInParty"])]),
    ("pt", "Spear Pillar", 70, "Dialga or Palkia", "two battles, Platinum has both in every copy",
     [PT("scripts_spear_pillar_dialga.s", "StartLegendaryBattle", "DIALGA", 70, "MAP_HEADER_SPEAR_PILLAR_DIALGA"),
      PT("scripts_spear_pillar_palkia.s", "StartLegendaryBattle", "PALKIA", 70, "MAP_HEADER_SPEAR_PILLAR_PALKIA")]),
    ("pt", "Stark Mountain", 50, "Heatran",
     "fixed battle, shown after the game is cleared and Buck has been talked to; level 50 here, 70 in Diamond and Pearl",
     [PT("scripts_stark_mountain_room_3.s", "StartLegendaryBattle", "HEATRAN", 50, "MAP_HEADER_STARK_MOUNTAIN_ROOM_3",
         extra=[r"CheckGameCompleted", r"FLAG_TALKED_TO_BATTLEGROUND_BUCK"])]),
    ("pt", "Turnback Cave", 47, "Giratina (Altered Forme)", "fixed battle in the Giratina room, only while Giratina is not caught",
     [PT("scripts_turnback_cave_giratina_room.s", "StartLegendaryBattle", "GIRATINA", 47,
         "MAP_HEADER_TURNBACK_CAVE_GIRATINA_ROOM",
         extra=[r"SetFlag\s+FLAG_CAUGHT_GIRATINA\s+SetFlag\s+FLAG_HIDE_TURNBACK_CAVE_GIRATINA_ROOM_GIRATINA"])]),
    ("pt", "Valley Windworks", 15, "Drifloon",
     "fixed battle, Fridays only and gone for the day once beaten; level 15 here, 22 in Diamond and Pearl; honey-tree battles on this map roll levels 5 to 15 and can match too",
     [PT("scripts_valley_windworks_outside.s", "StartLegendaryBattle", "DRIFLOON", 15,
         "MAP_HEADER_VALLEY_WINDWORKS_OUTSIDE",
         extra=[r"DAY_OF_WEEK_FRIDAY", r"FLAG_DAILY_WON_AGAINST_VALLEY_WINDWORKS_OUTSIDE_DRIFLOON"]),
      TEXT("pokeplatinum", "src/overlay005/honey_tree.c", r"MAP_HEADER_VALLEY_WINDWORKS_OUTSIDE,"),
      TEXT("pokeplatinum", "src/overlay006/wild_encounters.c", r"u8 levelVariance = 15 - 5 \+ 1;",
           r"u8 level = 5 \+ LCRNG_RandMod\(levelVariance\);")]),
    ("pt", "Verity Cavern", 50, "Azelf",
     "fixed battle in Valor Cavern; the tracker's table names that map Verity Cavern by mistake",
     [PT("scripts_valor_cavern.s", "StartLegendaryBattle", "AZELF", 50, "MAP_HEADER_VALOR_CAVERN")]),
    # ---- HeartGold and SoulSilver
    ("hgss", "Burned Tower", 40, "Suicune", "fixed battle in the basement, opened by the Hall of Fame script; the Route 25 battle comes first",
     [HG("scr_seq_0024_D18R0102.s", "SUICUNE", 40, "MAP_BURNED_TOWER_B1F"),
      TEXT("pokeheartgold", HG_SCR + "scr_seq_0825_T10R0701.s",
           r"Compare\s+VAR_SCENE_ROUTE_25,\s*3\s+GoToIfNe\s+\w+\s+ClearFlag\s+FLAG_HIDE_BURNED_TOWER_STATIC_SUICUNE")]),
    ("hgss", "Cerulean Cave", 70, "Mewtwo", "fixed battle at the end of the cave",
     [HG("scr_seq_0011_D03R0103.s", "MEWTWO", 70, "MAP_CERULEAN_CAVE_B1F")]),
    ("hgss", "Embedded Tower", 50, "Rayquaza, Kyogre (HeartGold) or Groudon (SoulSilver)",
     "three room battles at level 50 in both versions; Kyogre only in HeartGold, Groudon only in SoulSilver",
     [HG("scr_seq_0135_D52R0103.s", "RAYQUAZA", 50, "MAP_EMBEDDED_TOWER_RAYQUAZA_ROOM"),
      # Kyogre: version 7 (HeartGold) reaches the Blue Orb check, any other version hides it
      HG("scr_seq_0134_D52R0102.s", "KYOGRE", 50, "MAP_EMBEDDED_TOWER_KYOGRE_ROOM",
         extra=[r"Compare\s+VAR_TEMP_x4000,\s*7\s+GoToIfNe\s+_0038\s+GoTo\s+_004A",
                r"_0038:\s+GoTo\s+_0040", r"_0040:\s+SetFlag\s+FLAG_HIDE_EMBEDDED_TOWER_KYOGRE\s",
                r"_004A:\s+GoToIfSet\s+\w+,\s*\w+\s+HasItem\s+ITEM_BLUE_ORB"]),
      # Groudon: version 7 (HeartGold) hides it, any other version reaches the Red Orb check
      HG("scr_seq_0133_D52R0101.s", "GROUDON", 50, "MAP_EMBEDDED_TOWER_GROUDON_ROOM",
         extra=[r"Compare\s+VAR_TEMP_x4000,\s*7\s+GoToIfNe\s+_0038\s+GoTo\s+_0040",
                r"_0038:\s+GoTo\s+_004A", r"_0040:\s+SetFlag\s+FLAG_HIDE_EMBEDDED_TOWER_GROUDON\s",
                r"_004A:\s+GoToIfSet\s+\w+,\s*\w+\s+HasItem\s+ITEM_RED_ORB"])]),
    ("hgss", "Lake of Rage", 30, "Gyarados (the red one)", "fixed battle; the script forces it to be shiny",
     [dict(kind="hg", path=HG_SCR + "scr_seq_0938_T29.s", hdr="MAP_LAKE_OF_RAGE", level=30, sp=["SPECIES_GYARADOS"],
           re=[STATIC_FLAG + _cmd("WildBattle", "SPECIES_GYARADOS", "30", "1")]),
      TEXT("pokeheartgold", "src/scrcmd_c.c",
           r"BOOL ScrCmd_WildBattle\(ScriptContext \*ctx\) \{[^}]*u8 shiny = ScriptReadByte\(ctx\);")]),
    ("hgss", "Pewter City", 40, "Latios (HeartGold) or Latias (SoulSilver)",
     "event Pokemon behind a special gate (the research doc: the Enigma Stone event)",
     [dict(kind="hg", path=HG_SCR + "scr_seq_0750_T03.s", hdr="MAP_PEWTER", level=40, sp=["SPECIES_LATIOS", "SPECIES_LATIAS"],
           re=[VERSION_HG + r"SetVar\s+VAR_TEMP_x400A,\s*SPECIES_LATIOS", r"SetVar\s+VAR_TEMP_x400A,\s*SPECIES_LATIAS",
               STATIC_FLAG + _cmd("WildBattle", "VAR_TEMP_x400A", "40", "0")])]),
    ("hgss", "Route 10", 50, "Zapdos", "fixed battle outside the Power Plant, unlocked by the Earth Badge",
     [HG("scr_seq_0191_R10.s", "ZAPDOS", 50, "MAP_ROUTE_10", extra=[r"CheckBadge\s+BADGE_EARTH"]),
      TEXT("pokeheartgold", HG_SCR + "scr_seq_0743_T02GYM0101.s", r"ClearFlag\s+FLAG_HIDE_ROUTE_10_ZAPDOS")]),
    ("hgss", "Route 11", 50, "Snorlax", "fixed battle on the road; the script starts it only while a radio tune is playing (the Poke Flute channel)",
     [HG("scr_seq_0197_R11.s", "SNORLAX", 50, "MAP_ROUTE_11", extra=[r"RadioMusicIsPlaying\s+5,"])]),
    ("hgss", "Route 12", 50, "Snorlax", "fixed battle, a second Snorlax; same radio condition as the one on Route 11",
     [HG("scr_seq_0199_R12.s", "SNORLAX", 50, "MAP_ROUTE_12", extra=[r"RadioMusicIsPlaying\s+5,"])]),
    ("hgss", "Route 25", 40, "Suicune", "fixed battle on Route 25",
     [HG("scr_seq_0216_R25.s", "SUICUNE", 40, "MAP_ROUTE_25")]),
    ("hgss", "Route 36", 20, "Sudowoodo", "fixed battle at the tree",
     [HG("scr_seq_0243_R36.s", "SUDOWOODO", 20, "MAP_ROUTE_36")]),
    ("hgss", "Seafoam Islands", 50, "Articuno", "fixed battle on the lowest floor",
     [HG("scr_seq_0014_D11R0105.s", "ARTICUNO", 50, "MAP_SEAFOAM_ISLANDS_B4F")]),
    ("hgss", "Team Rocket HQ", 21, "Koffing or Geodude",
     "forced trap-tile battles on B1F that cannot be fled (RocketTrapBattle; the game does not flag them static)",
     [dict(kind="hg", path=HG_SCR + "scr_seq_0089_D35R0102.s", hdr="MAP_TEAM_ROCKET_HEADQUARTERS_B1F", level=21,
           sp=["SPECIES_KOFFING", "SPECIES_GEODUDE"],
           re=[_cmd("RocketTrapBattle", "SPECIES_KOFFING", "21"), _cmd("RocketTrapBattle", "SPECIES_GEODUDE", "21")]),
      TEXT("pokeheartgold", "src/scrcmd_c.c", r"(?s)BOOL ScrCmd_RocketTrapBattle\(ScriptContext \*ctx\) \{.*?"
           r"SetupAndStartWildBattle\(ctx->taskman, species, level, winFlag, FALSE, FALSE\);")]),
    ("hgss", "Team Rocket HQ", 23, "Electrode (three) or Voltorb (trap tiles)",
     "the three Electrodes on B2F and the Voltorb trap-tile battles on B1F",
     [HG("scr_seq_0090_D35R0103.s", "ELECTRODE", 23, "MAP_TEAM_ROCKET_HEADQUARTERS_B2F"),
      dict(kind="hg", path=HG_SCR + "scr_seq_0089_D35R0102.s", hdr="MAP_TEAM_ROCKET_HEADQUARTERS_B1F", level=23,
           sp=["SPECIES_VOLTORB"],
           re=[_cmd("RocketTrapBattle", "SPECIES_VOLTORB", "23")])]),
    # ---- HeartGold or SoulSilver only: the two versions use different levels for the same battle
    ("hg", "Bell Tower", 45, "Ho-Oh", "fixed battle on the roof of the tower",
     [_hg_split("scr_seq_0021_D17R0110.s", "MAP_BELL_TOWER_ROOF", 250, 45, 70)]),
    ("hg", "Whirl Islands", 70, "Lugia", "fixed battle in the cave on the lowest floor",
     [_hg_split("scr_seq_0104_D40R0107.s", "MAP_WHIRL_ISLANDS_B3F_LUGIA_CAVE", 249, 70, 45)]),
    ("ss", "Bell Tower", 70, "Ho-Oh", "fixed battle on the roof of the tower",
     [_hg_split("scr_seq_0021_D17R0110.s", "MAP_BELL_TOWER_ROOF", 250, 45, 70)]),
    ("ss", "Whirl Islands", 45, "Lugia", "fixed battle in the cave on the lowest floor",
     [_hg_split("scr_seq_0104_D40R0107.s", "MAP_WHIRL_ISLANDS_B3F_LUGIA_CAVE", 249, 70, 45)]),
]

# Real statics LEFT OUT because an ordinary walking encounter of the same area can also be their level.
EXCLUDED = [
    ("dp", "Old Chateau", 15, "Rotom", "scripted battle at the TV; the Gastly of the house are level 15 too",
     [DP(329, 126, WILD, 479, 15, 300)]),
    ("hgss", "Mt. Silver Cave", 50, "Moltres", "fixed battle in the Moltres chamber; the wild Pokemon of the mountain are level 50 too",
     [HG("scr_seq_0106_D41R0105.s", "MOLTRES", 50, "MAP_MOUNT_SILVER_CAVE_MOLTRES_CHAMBER")]),
    ("hgss", "Union Cave", 20, "Lapras", "fixed battle on B2F, Fridays only; the wild Pokemon of the cave are level 20 too",
     [HG("scr_seq_0058_D25R0103.s", "LAPRAS", 20, "MAP_UNION_CAVE_B2F")]),
]


# ---------------------------------------------------------------------------------------------------------------
# sources
# ---------------------------------------------------------------------------------------------------------------
class Sources:
    def __init__(self):
        self.tables = {"pt": C.location_names("pt"), "hgss": C.location_names("hgss")}
        self.by_const, _rev = C.species_by_const()
        self.names = C.species_names()
        self._pt = None
        self._hg = None
        self._dp = None

    def pt(self):
        if self._pt is None:
            ids = [l.strip() for l in C.pret_text("pokeplatinum", "generated/map_headers.txt").split("\n") if l.strip()]
            text = C.pret_text("pokeplatinum", "include/data/map_headers.h")
            blocks = dict(re.findall(r"\[(MAP_HEADER_\w+)\]\s*=\s*\{(.*?)\n    \},", text, re.S))
            self._pt = ({c: i for i, c in enumerate(ids)}, blocks)
        return self._pt

    def hg(self):
        if self._hg is None:
            ids = {}
            for line in C.pret_text("pokeheartgold", "include/constants/maps.h").split("\n"):
                m = re.match(r"#define\s+(MAP_\w+)\s+(\d+)", line)
                if m:
                    ids.setdefault(m.group(1), int(m.group(2)))
            text = C.pret_text("pokeheartgold", "src/data/map_headers.h")
            blocks = dict(re.findall(r"\[(MAP_\w+)\]\s*=\s*\{(.*?)\n\s*\},", text, re.S))
            self._hg = (ids, blocks)
        return self._hg

    def dp(self):
        if self._dp is None:
            rom = N.NdsRom(C.rom_path("NZ_ROM_DIAMOND", N.DIAMOND_ROM))
            sha = C.pret_text("pokediamond", "pokediamond.us.sha1").split()[0]
            headers = N.dp_map_headers(rom)
            narc = N.Narc(rom.read(DP_SCRIPT_NARC))
            maps = {}
            for line in C.pret_text("pokediamond", "include/constants/maps.h").split("\n"):
                m = re.match(r"#define\s+MAP_(\w+)\s+(\d+)", line)
                if m:
                    maps[int(m.group(2))] = m.group(1)
            self._dp = (rom, rom.sha1 == sha, headers, narc, maps)
        return self._dp


def _match_all(label, text, patterns, where, problems):
    for rx in patterns:
        if not re.search(rx, text):
            problems.append("%s: %s has no match for %r" % (label, where, rx))


def verify_row(src, row, problems):
    game, place, level, species, note, checks = row
    label = "%s %s L%d %s" % (game, place, level, species)
    if not (1 <= level <= 100):
        problems.append("%s: level out of range" % label)
    table = src.tables["pt" if game in ("dp", "pt") else "hgss"]
    if place not in table.values():
        problems.append("%s: not a name in the tracker's location table" % label)
    if not checks:
        problems.append("%s: no evidence recorded" % label)
    for ck in checks:
        kind = ck["kind"]
        lv = ck.get("level")
        if lv is not None:
            expected = lv[game] if isinstance(lv, dict) else lv
            if expected != level:
                problems.append("%s: the evidence is for level %s, the row says %d" % (label, expected, level))
        for sp in ck.get("sp", []):
            sid = src.by_const[sp] if isinstance(sp, str) else sp
            if C.pretty_species(src.names[sid]) not in species:
                problems.append("%s: the evidence is for %s, which the species text does not name" % (
                    label, C.pretty_species(src.names[sid])))
        if kind == "text":
            _match_all(label, C.pret_text(ck["repo"], ck["path"]), ck["re"], ck["path"], problems)
        elif kind == "pt":
            ids, blocks = src.pt()
            _match_all(label, C.pret_text("pokeplatinum", ck["path"]), ck["re"], ck["path"], problems)
            hdr = ck["hdr"]
            if hdr not in ids:
                problems.append("%s: %s is not a map header" % (label, hdr))
                continue
            stem = os.path.basename(ck["path"])[:-2]
            m = re.search(r"\.scriptsArchiveID\s*=\s*(\w+)", blocks.get(hdr, ""))
            if not m or m.group(1) != stem:
                problems.append("%s: header %s runs script %s, not %s" % (label, hdr, m.group(1) if m else None, stem))
            name = table.get(ids[hdr])
            if name != place:
                problems.append("%s: header %s (id %d) is %r in the tracker table, not %r" % (label, hdr, ids[hdr], name,
                                                                                              place))
        elif kind == "hg":
            ids, blocks = src.hg()
            _match_all(label, C.pret_text("pokeheartgold", ck["path"]), ck["re"], ck["path"], problems)
            hdr = ck["hdr"]
            if hdr not in ids:
                problems.append("%s: %s is not a map" % (label, hdr))
                continue
            stem = os.path.basename(ck["path"])[:-2]
            m = re.search(r"\.scriptsBank\s*=\s*NARC_scr_seq_(\w+?)_bin", blocks.get(hdr, ""))
            if not m or m.group(1) != stem:
                problems.append("%s: map %s runs script %s, not %s" % (label, hdr, m.group(1) if m else None, stem))
            name = table.get(ids[hdr])
            if name != place:
                problems.append("%s: map %s (id %d) is %r in the tracker table, not %r" % (label, hdr, ids[hdr], name,
                                                                                          place))
        elif kind == "dp":
            rom, sha_ok, headers, narc, maps = src.dp()
            if not sha_ok:
                problems.append("%s: the Diamond dump is not the build pret publishes" % label)
            f = narc.files[ck["file"]]
            words = struct.unpack_from("<HHH", f, ck["off"])
            if words != ck["words"]:
                problems.append("%s: script file %d offset %d reads %s, expected %s" % (label, ck["file"], ck["off"],
                                                                                        words, ck["words"]))
            for off, exp in ck.get("also", []):
                got = struct.unpack_from("<%dH" % len(exp), f, off)
                if got != tuple(exp):
                    problems.append("%s: script file %d offset %d reads %s, expected %s" % (label, ck["file"], off, got,
                                                                                            tuple(exp)))
            hid = ck["hdr"]
            if headers[hid][3] != ck["file"]:
                problems.append("%s: header %d runs script bank %d, not %d" % (label, hid, headers[hid][3], ck["file"]))
            name = table.get(hid)
            if name != place:
                problems.append("%s: header %d (%s) is %r in the tracker table, not %r" % (label, hid, maps.get(hid),
                                                                                         name, place))
        else:
            problems.append("%s: unknown check kind %s" % (label, kind))


def verify_opcodes(problems):
    """The Diamond opcodes come from the comments of pokediamond's own scrcmd.c."""
    text = C.pret_text("pokediamond", "arm9/src/scrcmd.c")
    for name, code in (("WildBattle", "0124"), ("LegendaryBattle", "02BD"), ("GetDayOfWeek", "0234"),
                       ("GetSpiritombTalkCounter", "0214"), ("CompareVarToValue", "0011")):
        if not re.search(r"BOOL ScrCmd_%s\(ScriptContext \*ctx\)\s*\{?\s*// %s" % (name, code), text):
            problems.append("pokediamond scrcmd.c: ScrCmd_%s is not opcode %s" % (name, code))


class Analysis:
    """Everything --check and build() need: verified rows and the collision info per row."""

    def __init__(self, problems):
        self.src = Sources()
        verify_opcodes(problems)
        seen = {}
        for row in ROWS + EXCLUDED:
            key = (row[0], row[1], row[2])
            if key in seen:
                problems.append("two rows for %s" % (key,))
            seen[key] = row
            verify_row(self.src, row, problems)
        for (game, place, level), row in seen.items():
            if game in ("hg", "ss"):
                other = "ss" if game == "hg" else "hg"
                twin = [k for k in seen if k[0] == other and k[1] == place and k[2] != level]
                if not twin:
                    problems.append("%s %s L%d: an %s row with a different level is missing (hg and ss rows are only "
                                    "for a level that differs)" % (game, place, level, other))
                if any(k[0] == "hgss" and k[1] == place and k[2] == level for k in seen):
                    problems.append("%s %s L%d duplicates an hgss row" % (game, place, level))
        rom = self.src.dp()[0] if any(r[0] == "dp" for r in ROWS + EXCLUDED) else None
        self.enc = E.Encounters(dp_rom=rom)
        self.names = C.species_names()
        self.info = {}
        for row in ROWS:
            walk, water = self.collide(row)
            if walk:
                problems.append("%s %s L%d %s: %d ordinary walking slot(s) share this level (first: %s): move the row "
                                "to EXCLUDED" % (row[0], row[1], row[2], row[3], len(walk), self._describe(walk[:1])))
            self.info[(row[0], row[1], row[2])] = water
        for row in EXCLUDED:
            walk, _water = self.collide(row)
            if not walk:
                problems.append("%s %s L%d %s is EXCLUDED but no ordinary walking slot shares its level any more" % (
                    row[0], row[1], row[2], row[3]))

    def collide(self, row):
        ents = self.enc.collisions(row[0], row[1], row[2])
        return [e for e in ents if e[1] == "walk"], [e for e in ents if e[1] == "water"]

    def _describe(self, ents):
        return ", ".join("%s %s L%d-%d" % (C.pretty_species(self.names[e[2]]), e[5], e[3], e[4]) for e in ents)

    def water_species(self, game, place, level):
        names = sorted({C.pretty_species(self.names[e[2]]) for e in self.info.get((game, place, level), [])})
        return names


def header_lines(dex_rows):
    excl_lines = textwrap.wrap("Rows with a species check (each collision is re-proved by --check): " + "; ".join(dex_rows) + ".",
                               width=116)
    return [
        "# Battles that are not a wild first encounter, Generation 4 (2026-09-29): a wild battle at PLACE at exactly LEVEL is",
        "# a set battle (a static), not the area's first encounter.",
        "#",
        "# Columns: game, place, level, species, note, and for a few rows the national dex number of the species.",
        "#   game: dp = Diamond and Pearl, pt = Platinum, hgss = HeartGold and SoulSilver. hg or ss only for a place whose",
        "#   level differs between the two versions (Ho-Oh, Lugia). A species that differs at the same level (Dialga or",
        "#   Palkia, Kyogre or Groudon, Latios or Latias) stays one row of the shared key.",
        "#   place: the area name the DS tracker reports for the map the battle happens in, spelled exactly as the tracker's",
        "#   location table spells it for that map's header id (gen4/locations-pt.tsv for dp and pt, gen4/locations-hgss.tsv",
        "#   for hgss), typos included. Two are the table's mistakes and are kept as they read: Valor Cavern (Azelf) is",
        "#   \"Verity Cavern\" (id 316) and Platinum's Rock Peak Ruins (Regirock) is \"Rpck Peak Ruins\" (id 592). If the table",
        "#   is fixed, change those rows.",
        "#   level: the level the game's script gives the battle. species: the vanilla species, a note for people (a",
        "#   randomized game changes it and keeps the place and level).",
        "#",
        "# Sources, all re-read by tools/nuzlocke/gen4_statics.py --check:",
        "#   pt: the script line that starts the battle in pret pokeplatinum c248fb3 (the Platinum dump is byte-identical to",
        "#       that build), the map header whose scriptsArchiveID is that script, and the header's id in",
        "#       generated/map_headers.txt looked up in the tracker table.",
        "#   hgss, hg, ss: the script line in pret pokeheartgold 9d8b759 (the game sets FLAG_ENGAGING_STATIC_POKEMON right",
        "#       before each static WildBattle; the trap-tile battles of Team Rocket HQ are RocketTrapBattle), the map whose",
        "#       scriptsBank is that script, its id in include/constants/maps.h looked up in the tracker table. The levels of",
        "#       Ho-Oh and Lugia come from the script's own GetGameVersion split (7 = HeartGold).",
        "#   dp: pret has no script source for Diamond and Pearl, so the Diamond dump is read: script archive",
        "#       fielddata/script/scr_seq_release.narc, the command words at a recorded offset (0x124 WildBattle, 0x2BD",
        "#       LegendaryBattle, numbers from pokediamond's scrcmd.c), and the arm9 map header table whose scripts_bank names",
        "#       that script file. No Pearl dump exists; the two versions share the script archive except where a script",
        "#       reads the version (Dialga or Palkia at Spear Pillar).",
        "#",
        "# Not listed: roamers (Mesprit and Cresselia only start roaming in these games, Raikou, Entei, the Kanto Latias or",
        "# Latios, Platinum's three birds), gifts, eggs, trades, honey trees, the catching tutorials and trainer battles.",
        "# A row whose level an ordinary encounter of the area can also be (walking: grass, cave floor, swarm, radio, Poke",
        "# Radar, Rock Smash, Headbutt; or surf and fishing) has the species' national dex number as its sixth column: the",
        "# reader takes a wild battle for the static only when it is that species, so the ordinary encounter of another",
        "# species at that level is not taken for it and the area's first encounter is still counted. A randomized game",
        "# changes the species and does not match those rows.",
    ] + ["# " + l for l in excl_lines] + [
        "#",
        "# game\tplace\tlevel\tspecies\tnote\tdex (optional)",
    ]


def dex_of(species):
    """The national dex number of the one species a row names. A row that names two has no one number to check."""
    ids = {C.pretty_species(n).lower(): i for i, n in C.species_names().items()}
    parts = [p.strip() for p in re.split(r",| or ", re.sub(r"\(.*?\)", "", species)) if p.strip()]
    if len(parts) != 1 or parts[0].lower() not in ids:
        raise ValueError("cannot give %r one national dex number" % species)
    return ids[parts[0].lower()]


ORDER = {"dp": 0, "pt": 1, "hgss": 2, "hg": 3, "ss": 4}


def sorted_rows():
    return sorted(ROWS + EXCLUDED, key=lambda r: (ORDER[r[0]], r[1], r[2]))


def collides(analysis, row):
    walk, water = analysis.collide(row)
    return bool(walk or water)


def build(analysis):
    dex_rows = ["%s %s L%d %s" % (g, p, l, sp) for (g, p, l, sp, _n, _c) in sorted_rows() if collides(analysis, (g, p, l))]
    lines = header_lines(dex_rows)
    for game, place, level, species, note, _checks in sorted_rows():
        water = analysis.water_species(game, place, level)
        if water:
            note += "; ordinary surf or fishing encounters at this level also match (%s)" % ", ".join(water)
        line = "%s\t%s\t%d\t%s\t%s" % (game, place, level, species, note)
        if collides(analysis, (game, place, level)):
            line += "\t%d" % dex_of(species)
        lines.append(line)
    return C.render(lines)


def main(argv):
    check = "--check" in argv
    problems = []
    try:
        analysis = Analysis(problems)
    except C.MissingSource as e:
        return C.finish(FILENAME, [], missing=[str(e)])
    text = build(analysis)
    print("  %d rows (%s), %d of them with a walking collision and %d more with a water collision, all with a species check" % (
        len(ROWS) + len(EXCLUDED),
        ", ".join("%s %d" % (g, sum(1 for r in ROWS + EXCLUDED if r[0] == g)) for g in ("dp", "pt", "hgss", "hg", "ss")),
        len(EXCLUDED), sum(1 for v in analysis.info.values() if v)))
    if check:
        C.compare_with_shipped(FILENAME, text, problems)
        return C.finish(FILENAME, problems)
    if problems:
        return C.finish(FILENAME, problems)
    C.write_out(FILENAME, text)
    return C.EXIT_OK


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
