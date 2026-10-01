"""Generates and checks tracker-gba/src/main/resources/nuzlocke/levelcaps-gen4.tsv (2026-09-29).

    python tools/nuzlocke/gen4_levelcaps.py            # writes the file (LF; then run to_crlf.py on it)
    python tools/nuzlocke/gen4_levelcaps.py --check    # re-reads ROM dumps and pret, compares with the shipped file

Every number in the file is COMPUTED here, none is typed: the cap of a fight is the highest level on the team in the
first-run fight (the highest over every starter variant of a rival fight), read from

  * Diamond and Pearl (`dp`): pret pokediamond files/poketool/trainer/trdata.json, entry = trainer id, checked party for
    party against the clean Diamond dump (trdata.narc + trpoke.narc); Pearl has no dump, pret builds it from the same
    trdata.json;
  * Platinum (`pt`): pret pokeplatinum res/trainers/data/<trainer>.json, the file picked by the trainer constant that
    generated/trainers.txt gives for the id, checked party for party against the clean Platinum dump (which is
    byte-identical to what pret builds: SHA1 in platinum.us/rom_rev0.sha1);
  * HeartGold and SoulSilver (`hgss`): pret pokeheartgold files/poketool/trainer/trainers.json, entry = trainer id, ids
    named by include/constants/trainers.h. No HG/SS dump exists here.

The trainer ids are the ids the DS tracker reports (NdsTrackerState.enemyTrainerId = the u16 at the game map's
enemyTrainerId offset): trainer-groups.tsv, the reference tracker's LAB_IDS and FINAL_FIGHT_ID and this file all use the
trainer table index (Roark is 246 in Diamond and in Platinum, Cynthia 267, HGSS Red 260, HGSS Falkner 20).

The badge bits are derived from the disassembly, see badge_bits(): the badge byte(s) the tracker reads are the
TrainerInfo / PlayerProfile badge fields, the bit of a badge is its constant's number, and the badge each leader hands
over is the GiveBadge in that leader's gym script.
"""
import os
import re
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen4_common as C  # noqa: E402
import gen4_nds as N  # noqa: E402

FILENAME = "levelcaps-gen4.tsv"

# ---------------------------------------------------------------------------------------------------------------
# The fights. Fields: key, kind, label, ids-or-constants, badge constant (gym rows), group, tracker counterpart
# (group name, name or location in nds/trainer-groups.tsv), doc row (the research doc's "Battle" cell).
# The order is the order the fights normally come in (the research doc's story order), extras interleaved.
# ---------------------------------------------------------------------------------------------------------------
DP = [
    # key, kind, label, ids, expected pret class, badge, group, tracker, doc
    ("barry1", "rival", "Barry (Route 203)", [247, 248, 249], "TRAINER_CLASS_PKMN_TRAINER_BARRY", None, "",
     ("Barry", "Route 203"), "Rival Barry, 1st"),
    ("gym1", "gym", "Roark", [246], "TRAINER_CLASS_LEADER_ROARK", "BADGE_COAL", "", ("Gym Leaders", "Roark"), "Roark"),
    ("mars1", "boss", "Mars (Valley Windworks)", [295], "TRAINER_CLASS_COMMANDER_MARS", None, "", None,
     "Commander Mars, 1st"),
    ("gym2", "gym", "Gardenia", [315], "TRAINER_CLASS_LEADER_GARDENIA", "BADGE_FOREST", "",
     ("Gym Leaders", "Gardenia"), "Gardenia"),
    ("jupiter1", "boss", "Jupiter (Galactic Eterna Building)", [406], "TRAINER_CLASS_COMMANDER_JUPITER", None, "",
     None, "Commander Jupiter, 1st"),
    ("barry2", "rival", "Barry (Hearthome City)", [470, 471, 472], "TRAINER_CLASS_PKMN_TRAINER_BARRY", None, "",
     ("Barry", "Hearthorne City"), "Rival Barry, 2nd"),
    ("gym3", "gym", "Maylene", [317], "TRAINER_CLASS_LEADER_MAYLENE", "BADGE_COBBLE", "", ("Gym Leaders", "Maylene"),
     "Maylene"),
    ("gym4", "gym", "Crasher Wake", [316], "TRAINER_CLASS_LEADER_WAKE", "BADGE_FEN", "", ("Gym Leaders", "Wake"),
     "Crasher Wake"),
    ("barry3", "rival", "Barry (Pastoria City)", [473, 474, 475], "TRAINER_CLASS_PKMN_TRAINER_BARRY", None, "",
     ("Barry", "Pastoria City"), "Rival Barry, 3rd"),
    ("gym5", "gym", "Fantina", [318], "TRAINER_CLASS_LEADER_FANTINA", "BADGE_RELIC", "", ("Gym Leaders", "Fantina"),
     "Fantina"),
    ("barry4", "rival", "Barry (Canalave City)", [476, 477, 478], "TRAINER_CLASS_PKMN_TRAINER_BARRY", None, "",
     ("Barry", "Canalave City"), "Rival Barry, 4th"),
    ("gym6", "gym", "Byron", [250], "TRAINER_CLASS_LEADER_BYRON", "BADGE_MINE", "", ("Gym Leaders", "Byron"), "Byron"),
    ("saturn1", "boss", "Saturn (Lake Valor)", [408], "TRAINER_CLASS_COMMANDER_SATURN", None, "", None,
     "Commander Saturn, 1st"),
    ("mars2", "boss", "Mars (Lake Verity)", [405], "TRAINER_CLASS_COMMANDER_MARS", None, "", None,
     "Commander Mars, 2nd"),
    ("gym7", "gym", "Candice", [319], "TRAINER_CLASS_LEADER_CANDICE", "BADGE_ICICLE", "", ("Gym Leaders", "Candice"),
     "Candice"),
    ("saturn2", "boss", "Saturn (Galactic HQ)", [409], "TRAINER_CLASS_COMMANDER_SATURN", None, "", None,
     "Commander Saturn, 2nd"),
    ("cyrus1", "boss", "Cyrus (Galactic HQ)", [403], "TRAINER_CLASS_GALACTIC_BOSS", None, "", None, "Boss Cyrus, 1st"),
    ("cyrus2", "boss", "Cyrus (Spear Pillar)", [404], "TRAINER_CLASS_GALACTIC_BOSS", None, "", None,
     "Boss Cyrus, 2nd"),
    ("gym8", "gym", "Volkner", [320], "TRAINER_CLASS_LEADER_VOLKNER", "BADGE_BEACON", "", ("Gym Leaders", "Volkner"),
     "Volkner"),
    ("barry5", "rival", "Barry (Pokemon League)", [479, 480, 481], "TRAINER_CLASS_PKMN_TRAINER_BARRY", None, "",
     ("Barry", "Victory Road"), "Rival Barry, 5th"),
    ("e4-1", "e4", "Aaron", [261], "TRAINER_CLASS_ELITE_FOUR_AARON", None, "", ("Elite 4", "Aaron"), "Aaron"),
    ("e4-2", "e4", "Bertha", [262], "TRAINER_CLASS_ELITE_FOUR_BERTHA", None, "", ("Elite 4", "Bertha"), "Bertha"),
    ("e4-3", "e4", "Flint", [263], "TRAINER_CLASS_ELITE_FOUR_FLINT", None, "", ("Elite 4", "Flint"), "Flint"),
    ("e4-4", "e4", "Lucian", [264], "TRAINER_CLASS_ELITE_FOUR_LUCIEN", None, "", ("Elite 4", "Lucian"), "Lucian"),
    ("champion", "champion", "Cynthia", [267], "TRAINER_CLASS_CHAMPION", None, "", ("Elite 4", "Cynthia"), "Cynthia"),
]

BARRY_PT = lambda place: ["TRAINER_RIVAL_%s_%s" % (place, s) for s in ("PIPLUP", "TURTWIG", "CHIMCHAR")]  # noqa: E731

PT = [
    # key, kind, label, constants, badge, group, tracker, doc
    ("barry1", "rival", "Barry (Route 201)", BARRY_PT("ROUTE_201"), None, "", ("Barry", "Lab"), "Rival Barry, 1st"),
    ("barry2", "rival", "Barry (Route 203)", BARRY_PT("ROUTE_203"), None, "", ("Barry", "Route 203"),
     "Rival Barry, 2nd"),
    ("gym1", "gym", "Roark", ["TRAINER_LEADER_ROARK"], "BADGE_ID_COAL", "", ("Gym Leaders", "Roark"), "Roark"),
    ("mars1", "boss", "Mars (Valley Windworks)", ["TRAINER_COMMANDER_MARS_VALLEY_WINDWORKS"], None, "", None,
     "Commander Mars, 1st"),
    ("gym2", "gym", "Gardenia", ["TRAINER_LEADER_GARDENIA"], "BADGE_ID_FOREST", "", ("Gym Leaders", "Gardenia"),
     "Gardenia"),
    ("jupiter1", "boss", "Jupiter (Galactic Eterna Building)",
     ["TRAINER_COMMANDER_JUPITER_TEAM_GALACTIC_ETERNA_BUILDING"], None, "", None, "Commander Jupiter, 1st"),
    ("gym3", "gym", "Fantina", ["TRAINER_LEADER_FANTINA"], "BADGE_ID_RELIC", "", ("Gym Leaders", "Fantina"), "Fantina"),
    ("barry3", "rival", "Barry (Route 209)", BARRY_PT("ROUTE_209"), None, "", ("Barry", "Hearthorne City"),
     "Rival Barry, 3rd"),
    ("gym4", "gym", "Maylene", ["TRAINER_LEADER_MAYLENE"], "BADGE_ID_COBBLE", "", ("Gym Leaders", "Maylene"),
     "Maylene"),
    ("barry4", "rival", "Barry (Pastoria City)", BARRY_PT("PASTORIA_CITY"), None, "", ("Barry", "Pastoria City"),
     "Rival Barry, 4th"),
    ("gym5", "gym", "Crasher Wake", ["TRAINER_LEADER_WAKE"], "BADGE_ID_FEN", "", ("Gym Leaders", "Wake"),
     "Crasher Wake"),
    ("cyrus1", "boss", "Cyrus (Celestic Town)", ["TRAINER_GALACTIC_BOSS_CYRUS_CELESTIC_TOWN_RUINS"], None, "", None,
     "Boss Cyrus, 1st"),
    ("barry5", "rival", "Barry (Canalave City)", BARRY_PT("CANALAVE_CITY"), None, "", ("Barry", "Canalave City"),
     "Rival Barry, 5th"),
    ("gym6", "gym", "Byron", ["TRAINER_LEADER_BYRON"], "BADGE_ID_MINE", "", ("Gym Leaders", "Byron"), "Byron"),
    ("saturn1", "boss", "Saturn (Valor Cavern)", ["TRAINER_COMMANDER_SATURN_VALOR_CAVERN"], None, "", None,
     "Commander Saturn, 1st"),
    ("mars2", "boss", "Mars (Lake Verity)", ["TRAINER_COMMANDER_MARS_LAKE_VERITY"], None, "", None,
     "Commander Mars, 2nd"),
    ("gym7", "gym", "Candice", ["TRAINER_LEADER_CANDICE"], "BADGE_ID_ICICLE", "", ("Gym Leaders", "Candice"),
     "Candice"),
    ("saturn2", "boss", "Saturn (Galactic HQ)", ["TRAINER_COMMANDER_SATURN_GALACTIC_HQ"], None, "", None,
     "Commander Saturn, 2nd"),
    ("cyrus2", "boss", "Cyrus (Galactic HQ)", ["TRAINER_GALACTIC_BOSS_CYRUS_GALACTIC_HQ"], None, "", None,
     "Boss Cyrus, 2nd"),
    ("cyrus3", "boss", "Cyrus (Distortion World)", ["TRAINER_GALACTIC_BOSS_CYRUS_DISTORTION_WORLD"], None, "", None,
     "Boss Cyrus, 3rd"),
    ("gym8", "gym", "Volkner", ["TRAINER_LEADER_VOLKNER"], "BADGE_ID_BEACON", "", ("Gym Leaders", "Volkner"),
     "Volkner"),
    ("barry6", "rival", "Barry (Pokemon League)", BARRY_PT("POKEMON_LEAGUE"), None, "", ("Barry", "Victory Road"),
     "Rival Barry, 6th"),
    ("e4-1", "e4", "Aaron", ["TRAINER_ELITE_FOUR_AARON"], None, "", ("Elite 4", "Aaron"), "Aaron"),
    ("e4-2", "e4", "Bertha", ["TRAINER_ELITE_FOUR_BERTHA"], None, "", ("Elite 4", "Bertha"), "Bertha"),
    ("e4-3", "e4", "Flint", ["TRAINER_ELITE_FOUR_FLINT"], None, "", ("Elite 4", "Flint"), "Flint"),
    ("e4-4", "e4", "Lucian", ["TRAINER_ELITE_FOUR_LUCIAN"], None, "", ("Elite 4", "Lucian"), "Lucian"),
    ("champion", "champion", "Cynthia", ["TRAINER_CHAMPION_CYNTHIA"], None, "", ("Elite 4", "Cynthia"), "Cynthia"),
]

SILVER = lambda *n: ["TRAINER_RIVAL_SILVER" + s for s in n]  # noqa: E731

HGSS = [
    ("silver1", "rival", "Silver (Cherrygrove City)",
     ["TRAINER_PASSERBY_BOY", "TRAINER_PASSERBY_BOY_2", "TRAINER_PASSERBY_BOY_3"], None, "", ("Silver", "Lab"),
     "Rival Silver, 1st"),
    ("gym1", "gym", "Falkner", ["TRAINER_LEADER_FALKNER_FALKNER"], "BADGE_ZEPHYR", "", ("Johto Gyms", "Falkner"),
     "Falkner"),
    ("proton1", "boss", "Proton (Slowpoke Well)", ["TRAINER_EXECUTIVE_PROTON_PROTON"], None, "", None,
     "Executive Proton, 1st"),
    ("silver2", "rival", "Silver (Azalea Town)", SILVER("", "_7", "_10"), None, "", ("Silver", "Azalea Town"),
     "Rival Silver, 2nd"),
    ("gym2", "gym", "Bugsy", ["TRAINER_LEADER_BUGSY_BUGSY"], "BADGE_HIVE", "", ("Johto Gyms", "Bugsy"), "Bugsy"),
    ("gym3", "gym", "Whitney", ["TRAINER_LEADER_WHITNEY"], "BADGE_PLAIN", "", ("Johto Gyms", "Whitney"), "Whitney"),
    ("silver3", "rival", "Silver (Burned Tower)", SILVER("_4", "_8", "_11"), None, "", ("Silver", "Burned Tower"),
     "Rival Silver, 3rd"),
    ("gym4", "gym", "Morty", ["TRAINER_LEADER_MORTY_MORTY"], "BADGE_FOG", "", ("Johto Gyms", "Morty"), "Morty"),
    ("gym5", "gym", "Chuck", ["TRAINER_LEADER_CHUCK_CHUCK"], "BADGE_STORM", "", ("Johto Gyms", "Chuck"), "Chuck"),
    ("gym6", "gym", "Jasmine", ["TRAINER_LEADER_JASMINE_JASMINE"], "BADGE_MINERAL", "", ("Johto Gyms", "Jasmine"),
     "Jasmine"),
    ("petrel1", "boss", "Petrel (Team Rocket HQ)", ["TRAINER_EXECUTIVE_PETREL_PETREL_2"], None, "", None,
     "Executive Petrel, 1st"),
    ("ariana1", "boss", "Ariana (Team Rocket HQ)", ["TRAINER_EXECUTIVE_ARIANA_ARIANA_2"], None, "", None,
     "Executive Ariana, HQ battle*"),
    ("gym7", "gym", "Pryce", ["TRAINER_LEADER_PRYCE_PRYCE"], "BADGE_GLACIER", "", ("Johto Gyms", "Pryce"), "Pryce"),
    ("proton2", "boss", "Proton (Radio Tower)", ["TRAINER_EXECUTIVE_PROTON_PROTON_2"], None, "", None,
     "Executive Proton, 2nd"),
    ("petrel2", "boss", "Petrel (Radio Tower)", ["TRAINER_EXECUTIVE_PETREL_PETREL"], None, "",
     ("Elite 4 / Bosses", "Petrel"), "Executive Petrel, 2nd"),
    ("ariana2", "boss", "Ariana (Radio Tower)", ["TRAINER_EXECUTIVE_ARIANA_ARIANA"], None, "", None,
     "Executive Ariana, 2nd"),
    ("archer", "boss", "Archer (Radio Tower)", ["TRAINER_EXECUTIVE_ARCHER_ARCHER"], None, "",
     ("Elite 4 / Bosses", "Archer"), "Executive Archer"),
    ("silver4", "rival", "Silver (Goldenrod Tunnel)", SILVER("_12", "_17", "_18"), None, "", ("Silver", "Goldenrod"),
     "Rival Silver, 4th"),
    ("gym8", "gym", "Clair", ["TRAINER_LEADER_CLAIR_CLAIR"], "BADGE_RISING", "", ("Johto Gyms", "Clair"), "Clair"),
    ("silver5", "rival", "Silver (Victory Road)", SILVER("_5", "_9", "_13"), None, "", ("Silver", "Victory Road"),
     "Rival Silver, 5th"),
    ("e4-1", "e4", "Will", ["TRAINER_ELITE_FOUR_WILL_WILL"], None, "", ("Elite 4 / Bosses", "Will"), "Will"),
    ("e4-2", "e4", "Koga", ["TRAINER_ELITE_FOUR_KOGA_KOGA"], None, "", ("Elite 4 / Bosses", "Koga"), "Koga"),
    ("e4-3", "e4", "Bruno", ["TRAINER_ELITE_FOUR_BRUNO_BRUNO"], None, "", ("Elite 4 / Bosses", "Bruno"), "Bruno"),
    ("e4-4", "e4", "Karen", ["TRAINER_ELITE_FOUR_KAREN_KAREN"], None, "", ("Elite 4 / Bosses", "Karen"), "Karen"),
    ("champion", "champion", "Lance", ["TRAINER_CHAMPION_LANCE"], None, "", ("Elite 4 / Bosses", "Lance"), "Lance"),
    ("silver6", "rival", "Silver (Mt. Moon)", SILVER("_14", "_15", "_16"), None, "", ("Silver", "Mt. Moon"),
     "Rival Silver, 6th"),
    ("surge", "post", "Lt. Surge", ["TRAINER_LEADER_LT_SURGE_LT__SURGE"], "BADGE_THUNDER", "Kanto gyms",
     ("Kanto Gyms", "Lt. Surge"), "Lt. Surge"),
    ("sabrina", "post", "Sabrina", ["TRAINER_LEADER_SABRINA_SABRINA"], "BADGE_MARSH", "Kanto gyms",
     ("Kanto Gyms", "Sabrina"), "Sabrina"),
    ("misty", "post", "Misty", ["TRAINER_LEADER_MISTY_MISTY"], "BADGE_CASCADE", "Kanto gyms",
     ("Kanto Gyms", "Misty"), "Misty"),
    ("erika", "post", "Erika", ["TRAINER_LEADER_ERIKA_ERIKA"], "BADGE_RAINBOW", "Kanto gyms",
     ("Kanto Gyms", "Erika"), "Erika"),
    ("janine", "post", "Janine", ["TRAINER_LEADER_JANINE_JANINE"], "BADGE_SOUL", "Kanto gyms",
     ("Kanto Gyms", "Janine"), "Janine"),
    ("brock", "post", "Brock", ["TRAINER_LEADER_BROCK_BROCK"], "BADGE_BOULDER", "Kanto gyms",
     ("Kanto Gyms", "Brock"), "Brock"),
    ("blaine", "post", "Blaine", ["TRAINER_LEADER_BLAINE_BLAINE"], "BADGE_VOLCANO", "Kanto gyms",
     ("Kanto Gyms", "Blaine"), "Blaine"),
    ("blue", "post", "Blue", ["TRAINER_LEADER_BLUE_BLUE"], "BADGE_EARTH", "Blue", ("Kanto Gyms", "Blue"), "Blue"),
    ("red", "post", "Red", ["TRAINER_PKMN_TRAINER_RED_RED"], None, "Mt. Silver", ("Elite 4 / Bosses", "Red"), "Red"),
]

# Where each trainer constant is used in the game (the file that starts the battle), proving no id is a dead entry
# (pret pokeheartgold carries unused Silver entries 2, 3 and 265 that a name search would pick for the first fight).
PT_USED_IN = {
    "TRAINER_RIVAL_ROUTE_201_PIPLUP": "res/field/scripts/scripts_route_201.s",
    "TRAINER_RIVAL_ROUTE_201_TURTWIG": "res/field/scripts/scripts_route_201.s",
    "TRAINER_RIVAL_ROUTE_201_CHIMCHAR": "res/field/scripts/scripts_route_201.s",
    "TRAINER_RIVAL_ROUTE_203_PIPLUP": "res/field/scripts/scripts_route_203.s",
    "TRAINER_RIVAL_ROUTE_203_TURTWIG": "res/field/scripts/scripts_route_203.s",
    "TRAINER_RIVAL_ROUTE_203_CHIMCHAR": "res/field/scripts/scripts_route_203.s",
    "TRAINER_LEADER_ROARK": "res/field/scripts/scripts_oreburgh_city_gym.s",
    "TRAINER_COMMANDER_MARS_VALLEY_WINDWORKS": "res/field/scripts/scripts_valley_windworks_building.s",
    "TRAINER_LEADER_GARDENIA": "res/field/scripts/scripts_eterna_city_gym.s",
    "TRAINER_COMMANDER_JUPITER_TEAM_GALACTIC_ETERNA_BUILDING":
        "res/field/scripts/scripts_team_galactic_eterna_building_4f.s",
    "TRAINER_LEADER_FANTINA": "res/field/scripts/scripts_hearthome_city_gym_leader_room.s",
    "TRAINER_RIVAL_ROUTE_209_PIPLUP": "res/field/scripts/scripts_route_209_gate_to_hearthome_city.s",
    "TRAINER_RIVAL_ROUTE_209_TURTWIG": "res/field/scripts/scripts_route_209_gate_to_hearthome_city.s",
    "TRAINER_RIVAL_ROUTE_209_CHIMCHAR": "res/field/scripts/scripts_route_209_gate_to_hearthome_city.s",
    "TRAINER_LEADER_MAYLENE": "res/field/scripts/scripts_veilstone_city_gym.s",
    "TRAINER_RIVAL_PASTORIA_CITY_PIPLUP": "res/field/scripts/scripts_pastoria_city.s",
    "TRAINER_RIVAL_PASTORIA_CITY_TURTWIG": "res/field/scripts/scripts_pastoria_city.s",
    "TRAINER_RIVAL_PASTORIA_CITY_CHIMCHAR": "res/field/scripts/scripts_pastoria_city.s",
    "TRAINER_LEADER_WAKE": "res/field/scripts/scripts_pastoria_city_gym.s",
    "TRAINER_GALACTIC_BOSS_CYRUS_CELESTIC_TOWN_RUINS": "res/field/scripts/scripts_celestic_town_cave.s",
    "TRAINER_RIVAL_CANALAVE_CITY_PIPLUP": "res/field/scripts/scripts_canalave_city.s",
    "TRAINER_RIVAL_CANALAVE_CITY_TURTWIG": "res/field/scripts/scripts_canalave_city.s",
    "TRAINER_RIVAL_CANALAVE_CITY_CHIMCHAR": "res/field/scripts/scripts_canalave_city.s",
    "TRAINER_LEADER_BYRON": "res/field/scripts/scripts_canalave_city_gym.s",
    "TRAINER_COMMANDER_SATURN_VALOR_CAVERN": "res/field/scripts/scripts_valor_cavern.s",
    "TRAINER_COMMANDER_MARS_LAKE_VERITY": "res/field/scripts/scripts_lake_verity.s",
    "TRAINER_LEADER_CANDICE": "res/field/scripts/scripts_snowpoint_city_gym.s",
    "TRAINER_COMMANDER_SATURN_GALACTIC_HQ": "res/field/scripts/scripts_galactic_hq_control_room.s",
    "TRAINER_GALACTIC_BOSS_CYRUS_GALACTIC_HQ": "res/field/scripts/scripts_galactic_hq_4f.s",
    "TRAINER_GALACTIC_BOSS_CYRUS_DISTORTION_WORLD": "res/field/scripts/scripts_distortion_world_b7f.s",
    "TRAINER_LEADER_VOLKNER": "res/field/scripts/scripts_sunyshore_city_gym_room_3.s",
    "TRAINER_RIVAL_POKEMON_LEAGUE_PIPLUP": "res/field/scripts/scripts_pokemon_league_north_pokecenter_1f.s",
    "TRAINER_RIVAL_POKEMON_LEAGUE_TURTWIG": "res/field/scripts/scripts_pokemon_league_north_pokecenter_1f.s",
    "TRAINER_RIVAL_POKEMON_LEAGUE_CHIMCHAR": "res/field/scripts/scripts_pokemon_league_north_pokecenter_1f.s",
    "TRAINER_ELITE_FOUR_AARON": "res/field/scripts/scripts_pokemon_league_aaron_room.s",
    "TRAINER_ELITE_FOUR_BERTHA": "res/field/scripts/scripts_pokemon_league_bertha_room.s",
    "TRAINER_ELITE_FOUR_FLINT": "res/field/scripts/scripts_pokemon_league_flint_room.s",
    "TRAINER_ELITE_FOUR_LUCIAN": "res/field/scripts/scripts_pokemon_league_lucian_room.s",
    "TRAINER_CHAMPION_CYNTHIA": "res/field/scripts/scripts_pokemon_league_champion_room.s",
}
HG_SCR = "files/fielddata/script/scr_seq/"
HG_USED_IN = {
    "TRAINER_PASSERBY_BOY": HG_SCR + "scr_seq_0850_T21.s",
    "TRAINER_PASSERBY_BOY_2": HG_SCR + "scr_seq_0850_T21.s",
    "TRAINER_PASSERBY_BOY_3": HG_SCR + "scr_seq_0850_T21.s",
    "TRAINER_LEADER_FALKNER_FALKNER": HG_SCR + "scr_seq_0859_T22GYM0101.s",
    "TRAINER_EXECUTIVE_PROTON_PROTON": HG_SCR + "scr_seq_0060_D26R0102.s",
    "TRAINER_RIVAL_SILVER": HG_SCR + "scr_seq_0866_T23.s",
    "TRAINER_RIVAL_SILVER_7": HG_SCR + "scr_seq_0866_T23.s",
    "TRAINER_RIVAL_SILVER_10": HG_SCR + "scr_seq_0866_T23.s",
    "TRAINER_LEADER_BUGSY_BUGSY": HG_SCR + "scr_seq_0869_T23GYM0102.s",
    "TRAINER_LEADER_WHITNEY": HG_SCR + "scr_seq_0886_T25GYM0101.s",
    "TRAINER_RIVAL_SILVER_4": HG_SCR + "scr_seq_0023_D18R0101.s",
    "TRAINER_RIVAL_SILVER_8": HG_SCR + "scr_seq_0023_D18R0101.s",
    "TRAINER_RIVAL_SILVER_11": HG_SCR + "scr_seq_0023_D18R0101.s",
    "TRAINER_LEADER_MORTY_MORTY": HG_SCR + "scr_seq_0922_T27GYM0101.s",
    "TRAINER_LEADER_CHUCK_CHUCK": HG_SCR + "scr_seq_0877_T24GYM0101.s",
    "TRAINER_LEADER_JASMINE_JASMINE": HG_SCR + "scr_seq_0913_T26GYM0101.s",
    "TRAINER_EXECUTIVE_PETREL_PETREL_2": HG_SCR + "scr_seq_0091_D35R0104.s",
    "TRAINER_EXECUTIVE_ARIANA_ARIANA_2": HG_SCR + "scr_seq_0090_D35R0103.s",
    "TRAINER_LEADER_PRYCE_PRYCE": HG_SCR + "scr_seq_0932_T28GYM0101.s",
    "TRAINER_EXECUTIVE_PROTON_PROTON_2": "files/fielddata/eventdata/zone_event/181_D23R0104.json",
    "TRAINER_EXECUTIVE_PETREL_PETREL": HG_SCR + "scr_seq_0033_D23R0105.s",
    "TRAINER_EXECUTIVE_ARIANA_ARIANA": "files/fielddata/eventdata/zone_event/182_D23R0105.json",
    "TRAINER_EXECUTIVE_ARCHER_ARCHER": HG_SCR + "scr_seq_0034_D23R0106.s",
    "TRAINER_RIVAL_SILVER_12": HG_SCR + "scr_seq_0096_D37R0104.s",
    "TRAINER_RIVAL_SILVER_17": HG_SCR + "scr_seq_0096_D37R0104.s",
    "TRAINER_RIVAL_SILVER_18": HG_SCR + "scr_seq_0096_D37R0104.s",
    "TRAINER_LEADER_CLAIR_CLAIR": HG_SCR + "scr_seq_0943_T30GYM0101.s",
    "TRAINER_RIVAL_SILVER_5": HG_SCR + "scr_seq_0110_D43R0103.s",
    "TRAINER_RIVAL_SILVER_9": HG_SCR + "scr_seq_0110_D43R0103.s",
    "TRAINER_RIVAL_SILVER_13": HG_SCR + "scr_seq_0110_D43R0103.s",
    "TRAINER_ELITE_FOUR_WILL_WILL": HG_SCR + "scr_seq_0820_T10R0201.s",
    "TRAINER_ELITE_FOUR_KOGA_KOGA": HG_SCR + "scr_seq_0821_T10R0301.s",
    "TRAINER_ELITE_FOUR_BRUNO_BRUNO": HG_SCR + "scr_seq_0822_T10R0401.s",
    "TRAINER_ELITE_FOUR_KAREN_KAREN": HG_SCR + "scr_seq_0823_T10R0501.s",
    "TRAINER_CHAMPION_LANCE": HG_SCR + "scr_seq_0824_T10R0601.s",
    "TRAINER_RIVAL_SILVER_14": HG_SCR + "scr_seq_0007_D02R0101.s",
    "TRAINER_RIVAL_SILVER_15": HG_SCR + "scr_seq_0007_D02R0101.s",
    "TRAINER_RIVAL_SILVER_16": HG_SCR + "scr_seq_0007_D02R0101.s",
    "TRAINER_LEADER_LT_SURGE_LT__SURGE": HG_SCR + "scr_seq_0778_T06GYM0101.s",
    "TRAINER_LEADER_SABRINA_SABRINA": HG_SCR + "scr_seq_0829_T11GYM0101.s",
    "TRAINER_LEADER_MISTY_MISTY": HG_SCR + "scr_seq_0760_T04GYM0101.s",
    "TRAINER_LEADER_ERIKA_ERIKA": HG_SCR + "scr_seq_0786_T07GYM0101.s",
    "TRAINER_LEADER_JANINE_JANINE": HG_SCR + "scr_seq_0809_T08GYM0101.s",
    "TRAINER_LEADER_BROCK_BROCK": HG_SCR + "scr_seq_0752_T03GYM0101.s",
    "TRAINER_LEADER_BLAINE_BLAINE": HG_SCR + "scr_seq_0015_D11R0106.s",
    "TRAINER_LEADER_BLUE_BLUE": HG_SCR + "scr_seq_0743_T02GYM0101.s",
    "TRAINER_PKMN_TRAINER_RED_RED": HG_SCR + "scr_seq_0107_D41R0108.s",
}
# The script that hands the badge over (HGSS Clair's Rising Badge is given in the Dragon's Den, after the gym battle).
PT_BADGE_SCRIPT = {
    "BADGE_ID_COAL": "res/field/scripts/scripts_oreburgh_city_gym.s",
    "BADGE_ID_FOREST": "res/field/scripts/scripts_eterna_city_gym.s",
    "BADGE_ID_COBBLE": "res/field/scripts/scripts_veilstone_city_gym.s",
    "BADGE_ID_FEN": "res/field/scripts/scripts_pastoria_city_gym.s",
    "BADGE_ID_RELIC": "res/field/scripts/scripts_hearthome_city_gym_leader_room.s",
    "BADGE_ID_MINE": "res/field/scripts/scripts_canalave_city_gym.s",
    "BADGE_ID_ICICLE": "res/field/scripts/scripts_snowpoint_city_gym.s",
    "BADGE_ID_BEACON": "res/field/scripts/scripts_sunyshore_city_gym_room_3.s",
}
HG_BADGE_SCRIPT = {
    "BADGE_ZEPHYR": HG_SCR + "scr_seq_0859_T22GYM0101.s",
    "BADGE_HIVE": HG_SCR + "scr_seq_0869_T23GYM0102.s",
    "BADGE_PLAIN": HG_SCR + "scr_seq_0886_T25GYM0101.s",
    "BADGE_FOG": HG_SCR + "scr_seq_0922_T27GYM0101.s",
    "BADGE_STORM": HG_SCR + "scr_seq_0877_T24GYM0101.s",
    "BADGE_MINERAL": HG_SCR + "scr_seq_0913_T26GYM0101.s",
    "BADGE_GLACIER": HG_SCR + "scr_seq_0932_T28GYM0101.s",
    "BADGE_RISING": HG_SCR + "scr_seq_0112_D44R0103.s",
    "BADGE_BOULDER": HG_SCR + "scr_seq_0752_T03GYM0101.s",
    "BADGE_CASCADE": HG_SCR + "scr_seq_0760_T04GYM0101.s",
    "BADGE_THUNDER": HG_SCR + "scr_seq_0778_T06GYM0101.s",
    "BADGE_RAINBOW": HG_SCR + "scr_seq_0786_T07GYM0101.s",
    "BADGE_SOUL": HG_SCR + "scr_seq_0809_T08GYM0101.s",
    "BADGE_MARSH": HG_SCR + "scr_seq_0829_T11GYM0101.s",
    "BADGE_VOLCANO": HG_SCR + "scr_seq_0015_D11R0106.s",
    "BADGE_EARTH": HG_SCR + "scr_seq_0743_T02GYM0101.s",
}
# Diamond and Pearl: the badge each leader hands over. pret pokediamond has no script source (binary pieces only), so
# the Platinum disassembly's gym scripts and journal table are the source: the same eight leaders give the same eight
# badges, and pokediamond's own include/constants/badge.h numbers them in the same order.
DP_BADGE_OF_LEADER = {
    "Roark": "BADGE_COAL", "Gardenia": "BADGE_FOREST", "Maylene": "BADGE_COBBLE", "Wake": "BADGE_FEN",
    "Fantina": "BADGE_RELIC", "Byron": "BADGE_MINE", "Candice": "BADGE_ICICLE", "Volkner": "BADGE_BEACON",
}


# ---------------------------------------------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------------------------------------------
def join_names(names):
    if len(names) == 1:
        return names[0]
    if len(names) == 2:
        return "%s or %s" % (names[0], names[1])
    return "%s or %s" % (", ".join(names[:-1]), names[-1])


class Source:
    """One game's party lookups: pret data by id, plus the ROM dump when there is one."""

    def __init__(self, game):
        self.game = game
        self.by_const, self.rev_const = C.species_by_const()
        self.names = C.species_names()
        self.rom = None
        self.rom_trainers = None
        self.problems = []
        if game == "dp":
            self.rom = N.NdsRom(C.rom_path("NZ_ROM_DIAMOND", N.DIAMOND_ROM))
            self.rom_trainers = N.read_trainers(self.rom, 0)
            self.sha_ok = self.rom.sha1 == C.pret_text("pokediamond", "pokediamond.us.sha1").split()[0]
            data = C.pret_json("pokediamond", "files/poketool/trainer/trdata.json")["trdata"]
            self.trdata = {e["index"]: e for e in data}
        elif game == "pt":
            self.rom = N.NdsRom(C.rom_path("NZ_ROM_PLATINUM", N.PLATINUM_ROM))
            self.rom_trainers = N.read_trainers(self.rom, 2)
            self.sha_ok = self.rom.sha1 == C.pret_text("pokeplatinum", "platinum.us/rom_rev0.sha1").split()[0]
            self.consts = [l.strip() for l in C.pret_text("pokeplatinum", "generated/trainers.txt").split("\n")
                           if l.strip()]
            self.const_id = {c: i for i, c in enumerate(self.consts)}
        else:
            self.sha_ok = None
            self.trainers = C.pret_json("pokeheartgold", "files/poketool/trainer/trainers.json")["trainers"]
            self.const_id = {}
            self.id_const = {}
            for line in C.pret_text("pokeheartgold", "include/constants/trainers.h").split("\n"):
                m = re.match(r"#define\s+(TRAINER_\w+)\s+(\d+)", line)
                if m:
                    self.const_id.setdefault(m.group(1), int(m.group(2)))
                    self.id_const.setdefault(int(m.group(2)), m.group(1))
        self._pt_json = {}

    def species_id(self, const):
        if const not in self.by_const:
            raise KeyError("unknown species constant " + const)
        return self.by_const[const]

    def pret_party(self, tid):
        """[(species id, level)] from the disassembly's own trainer data, and its class or name for a sanity check."""
        if self.game == "dp":
            e = self.trdata[tid]
            return [(self.species_id(m["species"]), m["level"]) for m in e["party"]], e["class"]
        if self.game == "pt":
            c = self.consts[tid]
            path = "res/trainers/data/%s.json" % c[len("TRAINER_"):].lower()
            if path not in self._pt_json:
                self._pt_json[path] = C.pret_json("pokeplatinum", path)
            e = self._pt_json[path]
            return [(self.species_id(m["species"]), m["level"]) for m in e["party"]], e["class"]
        e = self.trainers[tid]
        return [(self.species_id(m["species"]), m["level"]) for m in e["party"]], e["class"]

    def rom_party(self, tid):
        if self.rom_trainers is None:
            return None
        return [(s, l) for (s, l, _it, _mv) in self.rom_trainers[tid]["party"]]


def fight_ids(game, src, row):
    """The ids of a fight (and the constants when the disassembly has them)."""
    if game == "dp":
        return list(row[3]), None
    consts = row[3]
    ids = []
    for c in consts:
        if c not in src.const_id:
            raise KeyError("%s is not a trainer constant of %s" % (c, game))
        ids.append(src.const_id[c])
    order = sorted(range(len(ids)), key=lambda i: ids[i])
    return [ids[i] for i in order], [consts[i] for i in order]


def compute_row(game, src, row, problems):
    """(cap, ace text, ids) of one fight, with every id checked against the ROM (dp, pt) and the disassembly."""
    ids, consts = fight_ids(game, src, row)
    key = row[0]
    parties = []
    for n, tid in enumerate(ids):
        party, cls = src.pret_party(tid)
        if game == "dp" and cls != row[4]:
            problems.append("%s %s: id %d has class %s, expected %s" % (game, key, tid, cls, row[4]))
        rp = src.rom_party(tid)
        if rp is not None and rp != party:
            problems.append("%s %s: id %d party in the ROM %s differs from pret %s" % (game, key, tid, rp, party))
        if not party:
            problems.append("%s %s: id %d has an empty party" % (game, key, tid))
        parties.append((tid, party))
    maxes = [(tid, max(l for _s, l in p)) for tid, p in parties]
    cap = max(m for _t, m in maxes)
    names = src.names
    tops = []
    lower = []
    for tid, party in parties:
        mx = max(l for _s, l in party)
        top_names = []
        for s, l in party:
            n = C.pretty_species(names[s])
            if l == mx and n not in top_names:
                top_names.append(n)
        if mx == cap:
            for n in top_names:
                if n not in tops:
                    tops.append(n)
        else:
            starter = C.pretty_species(names[party[-1][0]])
            lower.append((mx, starter))
    ace = join_names(tops)
    for mx, starter in lower:
        ace += " (%d with %s)" % (mx, starter)
    return cap, ace, ids


# ---------------------------------------------------------------------------------------------------------------
# badge bits
# ---------------------------------------------------------------------------------------------------------------
def struct_offset(text, struct, field, consts):
    """Byte offset of `field` in the C struct `struct` of a pret header (fixed-width integer fields only)."""
    m = re.search(r"typedef struct %s \{(.*?)\} %s;" % (struct, struct), text, re.S)
    if not m:
        raise ValueError("struct %s not found" % struct)
    sizes = {"u8": 1, "u16": 2, "u32": 4, "charcode_t": 2}
    off = 0
    bits_used = 0  # bits of the current u8 bit-field byte already taken (consecutive bit fields share a byte)
    for line in m.group(1).split("\n"):
        line = line.strip().rstrip(";")
        line = re.sub(r"/\*.*?\*/", "", line).strip()
        if not line or line.startswith("//"):
            continue
        fm = re.match(r"(u8|u16|u32|charcode_t)\s+(\w+)(?:\[(.+?)\])?(?:\s*:\s*(\d+))?$", line)
        if not fm:
            continue
        typ, name, count, width = fm.group(1), fm.group(2), fm.group(3), fm.group(4)
        if width is not None:
            w = int(width)
            if bits_used == 0 or bits_used + w > 8:
                if name == field:
                    return off
                off += 1
                bits_used = w
            else:
                bits_used += w
            continue
        bits_used = 0
        n = 1
        if count:
            expr = count
            for k, v in consts.items():
                expr = expr.replace(k, str(v))
            n = eval(expr, {"__builtins__": {}})
        if name == field:
            return off
        off += sizes[typ] * n
    raise ValueError("field %s not found in %s" % (field, struct))


def badge_bits(game, problems):
    """{badge constant: bit} plus a list of evidence lines, derived from the disassembly (see the module docstring)."""
    evidence = []
    if game == "pt":
        names = [l.strip() for l in C.pret_text("pokeplatinum", "generated/badges.txt").split("\n") if l.strip()]
        bits = {n: i for i, n in enumerate(names) if n.startswith("BADGE_ID_")}
        evidence.append("pt: generated/badges.txt numbers " + ", ".join("%s=%d" % (n[9:], b) for n, b in bits.items()))
        info = C.pret_text("pokeplatinum", "include/trainer_info.h")
        strlen = C.pret_text("pokeplatinum", "include/constants/string.h")
        tn = int(re.search(r"#define\s+TRAINER_NAME_LEN\s+(\d+)", strlen).group(1))
        off = struct_offset(info, "TrainerInfo", "badgeMask", {"TRAINER_NAME_LEN": tn})
        if off != 0x1A:
            problems.append("pt: TrainerInfo.badgeMask is at +0x%X, expected +0x1A" % off)
        evidence.append("pt: TrainerInfo.badgeMask at +0x%X (tracker badge byte 0x96 = TrainerInfo at 0x7C + 0x1A)" % off)
        journal = C.pret_text("pokeplatinum", "src/journal.c")
        jmap = dict((m.group(1), m.group(2)) for m in re.finditer(
            r"\{\s*TRAINER_LEADER_(\w+),\s*MAP_HEADER_\w+,\s*(BADGE_ID_\w+)\s*\}", journal))
        for badge, script in PT_BADGE_SCRIPT.items():
            text = C.pret_text("pokeplatinum", script)
            if not re.search(r"GiveBadge\s+%s\b" % badge, text):
                problems.append("pt: %s does not give %s" % (script, badge))
        return bits, evidence, jmap
    if game == "dp":
        text = C.pret_text("pokediamond", "include/constants/badge.h")
        bits = {("BADGE_" + m.group(1)): int(m.group(2)) for m in re.finditer(r"#define\s+BADGE_(\w+)\s+(\d+)", text)}
        evidence.append("dp: pokediamond include/constants/badge.h numbers " +
                        ", ".join("%s=%d" % (n[6:], b) for n, b in bits.items()))
        info = C.pret_text("pokediamond", "include/player_data.h")
        m = re.search(r"/\* 0x([0-9A-F]+) \*/ u8 badges;", info)
        off = int(m.group(1), 16) if m else -1
        if off != 0x1A:
            problems.append("dp: PlayerProfile.badges is at +0x%X, expected +0x1A" % off)
        evidence.append("dp: PlayerProfile.badges at +0x%X (tracker badge byte 0x292 = profile at 0x278 + 0x1A)" % off)
        return bits, evidence, None
    text = C.pret_text("pokeheartgold", "include/constants/badge.h")
    bits = {("BADGE_" + m.group(1)): int(m.group(2)) for m in re.finditer(r"#define\s+BADGE_(\w+)\s+(\d+)", text)}
    evidence.append("hgss: pokeheartgold include/constants/badge.h numbers " +
                    ", ".join("%s=%d" % (n[6:], b) for n, b in bits.items()))
    info = C.pret_text("pokeheartgold", "include/player_data.h")
    glob = C.pret_text("pokeheartgold", "include/constants/global.h")
    consts = {"PLAYER_NAME_LENGTH": int(re.search(r"#define\s+PLAYER_NAME_LENGTH\s+(\d+)", glob).group(1))}
    offj = struct_offset(info, "PlayerProfile", "johtoBadges", consts)
    offk = struct_offset(info, "PlayerProfile", "kantoBadges", consts)
    if (offj, offk) != (0x1A, 0x1F):
        problems.append("hgss: PlayerProfile badge fields at +0x%X and +0x%X, expected +0x1A and +0x1F" % (offj, offk))
    evidence.append("hgss: PlayerProfile.johtoBadges at +0x%X, kantoBadges at +0x%X (tracker bytes 0x8E and 0x93 = "
                    "profile at 0x74 + those)" % (offj, offk))
    ctext = C.pret_text("pokeheartgold", "src/player_data.c")
    if "(1 << (badge_no - 8))" not in ctext or "johtoBadges & (1 << badge_no)" not in ctext:
        problems.append("hgss: PlayerProfile_TestBadgeFlag no longer reads Johto bit N and Kanto bit N-8")
    for badge, script in HG_BADGE_SCRIPT.items():
        text = C.pret_text("pokeheartgold", script)
        if not re.search(r"GiveBadge\s+%s\b" % badge, text):
            problems.append("hgss: %s does not give %s" % (script, badge))
    return bits, evidence, None


# ---------------------------------------------------------------------------------------------------------------
# tracker table (nds/trainer-groups.tsv) and the research doc
# ---------------------------------------------------------------------------------------------------------------
TRACKER_CODE = {"dp": "45414441", "pt": "45555043", "hgss": "454B5049"}


def tracker_rows(game):
    out = {}
    for p in C.read_tsv_rows(C.NDS_RES / "nds" / "trainer-groups.tsv"):
        if p[0] != TRACKER_CODE[game]:
            continue
        ids = sorted(int(x) for x in p[7].split(",") if x)
        badge = int(p[8]) if len(p) > 8 and p[8] else None
        out[(p[2], p[6])] = (ids, badge)
    return out


def doc_rows(start, end):
    """The bold cap numbers of the research doc's tables between two headings: {Battle cell: (cap, raw cell)}."""
    text = open(str(C.DOCS / "nuzlocke-gen4-5.md"), encoding="utf-8").read().replace("\r\n", "\n")
    i = text.index(start)
    j = text.index(end, i + 1)
    rows = {}
    for line in text[i:j].split("\n"):
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 5:
            continue
        m = re.match(r"\*\*(\d+)", cells[3])
        if not m:
            continue
        rows.setdefault(cells[1], (int(m.group(1)), cells[3]))
    return rows


DOC_SECTIONS = {
    "dp": ("### 2.1 ", "### 2.2 "),
    "pt": ("### 2.2 ", "### 2.3 "),
    "hgss": ("### 3.1 ", "### 3.2 "),
}


# ---------------------------------------------------------------------------------------------------------------
# build
# ---------------------------------------------------------------------------------------------------------------
def table_of(game):
    return {"dp": DP, "pt": PT, "hgss": HGSS}[game]


def build_game(game, problems, evidence, notes):
    """The data lines of one game: list of tab-joined strings."""
    src = Source(game)
    if game in ("dp", "pt") and not src.sha_ok:
        problems.append("%s: the ROM dump's SHA1 is not the one pret publishes" % game)
    if game in ("dp", "pt"):
        evidence.append("%s: ROM %s rev %d sha1 %s matches pret: %s" % (game, src.rom.game_code, src.rom.revision,
                                                                       src.rom.sha1[:10], src.sha_ok))
    bits, ev, jmap = badge_bits(game, problems)
    evidence.extend(ev)
    trk = tracker_rows(game)
    try:
        doc = doc_rows(*DOC_SECTIONS[game])
    except (ValueError, OSError) as e:
        doc = {}
        problems.append("research doc could not be read: %s" % e)
    used_in = {"pt": PT_USED_IN, "hgss": HG_USED_IN}.get(game, {})
    lines = []
    checked_ids = 0
    matched_tracker = 0
    doc_seen = 0
    for seq, row in enumerate(table_of(game), start=1):
        key, kind, label = row[0], row[1], row[2]
        if game == "dp":
            badge_const, group, tracker, docsel = row[5], row[6], row[7], row[8]
        else:
            badge_const, group, tracker, docsel = row[4], row[5], row[6], row[7]
        cap, ace, ids = compute_row(game, src, row, problems)
        checked_ids += len(ids)
        # badge bit
        badge = ""
        if badge_const is not None:
            if badge_const not in bits:
                problems.append("%s %s: badge constant %s is not in the disassembly" % (game, key, badge_const))
            else:
                badge = str(bits[badge_const])
        # badge <-> leader: Platinum's journal table, HGSS's gym scripts
        if game == "pt" and badge_const:
            leader = row[3][0][len("TRAINER_LEADER_"):]
            if jmap.get(leader) != badge_const:
                problems.append("pt %s: journal.c gives %s the badge %s, not %s" % (key, leader, jmap.get(leader),
                                                                                    badge_const))
        if game == "hgss" and badge_const:
            text = C.pret_text("pokeheartgold", HG_BADGE_SCRIPT[badge_const])
            leader_const = row[3][0]
            if key != "gym8" and leader_const not in text:
                problems.append("hgss %s: %s does not reference %s" % (key, HG_BADGE_SCRIPT[badge_const], leader_const))
        if game == "hgss" and badge_const and bits.get(badge_const) is not None:
            # Johto gyms are the Nth fight and earn bit N-1; Kanto bits are 8..15
            if kind == "gym" and bits[badge_const] != int(key[3:]) - 1:
                problems.append("hgss %s: badge bit %d is not the gym number" % (key, bits[badge_const]))
        # usage evidence for pt / hgss
        if game in ("pt", "hgss"):
            for c in row[3]:
                path = used_in.get(c)
                repo = "pokeplatinum" if game == "pt" else "pokeheartgold"
                if path is None:
                    problems.append("%s %s: no usage evidence recorded for %s" % (game, key, c))
                else:
                    if not re.search(r"\b%s\b" % re.escape(c), C.pret_text(repo, path)):
                        problems.append("%s %s: %s does not reference %s" % (game, key, path, c))
        # tracker cross-check
        if tracker is not None and tracker in trk:
            tids, tbadge = trk[tracker]
            if tids != sorted(ids):
                problems.append("%s %s: tracker trainer-groups.tsv %s has ids %s, this file %s" % (
                    game, key, tracker, tids, sorted(ids)))
            else:
                matched_tracker += 1
            if tbadge is not None and badge:
                exp = tbadge - 1 + (8 if tracker[0] == "Kanto Gyms" else 0)
                if exp != int(badge):
                    problems.append("%s %s: tracker badge number %d means bit %d, this file says %s" % (
                        game, key, tbadge, exp, badge))
        elif tracker is not None:
            problems.append("%s %s: expected tracker row %s is missing from trainer-groups.tsv" % (game, key, tracker))
        # research doc
        if docsel:
            hit = None
            for cell, val in doc.items():
                if cell == docsel or (docsel.endswith("*") and cell.startswith(docsel[:-1])):
                    hit = (cell, val)
                    break
            if hit is None:
                notes.append("%s %s: research doc has no row %r" % (game, key, docsel))
            else:
                doc_seen += 1
                if hit[1][0] != cap:
                    notes.append("%s %s: research doc says %s, the game data says %d" % (game, key, hit[1][1], cap))
        lines.append("\t".join([game, str(seq), key, kind, label, str(cap), ace, ",".join(str(i) for i in ids),
                                badge, group]))
    # a trainer id must belong to exactly one row (the reader looks a fight up by the id the tracker reports)
    seen_ids = {}
    for l in lines:
        f = l.split("	")
        for i in f[7].split(","):
            if i in seen_ids:
                problems.append("%s: trainer id %s is in both %s and %s" % (game, i, seen_ids[i], f[2]))
            seen_ids[i] = f[2]
    keys = [l.split("	")[2] for l in lines]
    if len(keys) != len(set(keys)):
        problems.append("%s: a key is used twice" % game)
    # the tracker's own lab and final ids must be rows of this file
    lab = {"dp": [247, 248, 249], "pt": [850, 851, 852], "hgss": [495, 496, 497]}[game]
    final = {"dp": 267, "pt": 267, "hgss": 260}[game]
    first_ids = sorted(int(x) for x in lines[0].split("\t")[7].split(","))
    if first_ids != lab:
        problems.append("%s: NdsGameMap.labTrainerIds %s is not the first fight's ids %s" % (game, lab, first_ids))
    final_rows = [l.split("\t")[2] for l in lines if str(final) in l.split("\t")[7].split(",")]
    evidence.append("%s: %d trainer ids checked against pret%s; %d rows match trainer-groups.tsv; %d rows match the "
                    "research doc; NdsGameMap.labTrainerIds %s = first fight (%s); finalTrainerId %d = row %s"
                    % (game, checked_ids, " and the ROM" if game != "hgss" else " (no HG/SS dump)", matched_tracker,
                       doc_seen, lab, lines[0].split("\t")[2], final, ",".join(final_rows)))
    return lines


HEADER = [
    "# Hardcore Nuzlocke level caps for the vanilla Generation 4 DS games (2026-09-29).",
    "#",
    "# A cap is the level of the highest-level Pokemon on the boss's team in the first-run fight (the highest over every",
    "# starter variant of a rival fight). Rematches are left out. Every number below is COMPUTED by",
    "# tools/nuzlocke/gen4_levelcaps.py from the games' own trainer data, none is typed:",
    "#   dp   Diamond and Pearl: pret pokediamond trdata.json (entry = trainer id), checked party for party against a",
    "#        clean Diamond (US rev 5) dump. Pearl has no dump here; pret builds it from the same trdata.json.",
    "#   pt   Platinum: pret pokeplatinum trainer data, checked party for party against a clean Platinum (US rev 0)",
    "#        dump whose SHA1 is the one pret's platinum.us/rom_rev0.sha1 lists.",
    "#   hgss HeartGold and SoulSilver: pret pokeheartgold trainers.json (entry = trainer id). No HG/SS dump here.",
    "# pret commits: pokediamond 5bc4b1a, pokeplatinum c248fb3, pokeheartgold 9d8b759.",
    "@DOCLINE@",
    "#",
    "# These are the FALLBACK. When the tracker can read the trainer data out of the loaded ROM it uses the boss's real",
    "# party levels instead, which is what a randomized game needs.",
    "#",
    "# game: dp = Diamond and Pearl, pt = Platinum, hgss = HeartGold and SoulSilver.",
    "# seq: the order the fights normally come in (the research doc's story order), restarting at 1 for each game.",
    "# key: gymN is the Nth gym in FIGHT order (Platinum: Fantina is gym3), e4-N, champion, otherwise a short key.",
    "# kind: gym, e4, champion, post (a fight after the Champion in HGSS: the Kanto gyms, Blue, Red), rival and boss",
    "#   (extra cap points: Barry, Silver, Team Galactic commanders and Cyrus, Team Rocket executives; they are cap",
    "#   points only when the player turns rivals and team leaders into bosses; they carry no badge).",
    "# cap: the highest level on the team. ace: the top Pokemon, text only; a note in brackets says the cap of a rival",
    "#   variant that is lower (Silver in Goldenrod Tunnel is 34 with Meganium or Quilava and 32 with Feraligatr).",
    "# ids: the trainer ids the DS tracker reports as the opponent (the trainer table index). A rival fight lists all",
    "#   three starter variants. Platinum's Elite Four and Cynthia are the FIRST-RUN teams (FLAG_ARRESTED_CHARON_STARK_",
    "#   MOUNTAIN unset, ids 261-264 and 267); the higher post-Stark-Mountain teams are ids 866-870 and are not listed.",
    "#   The ids of HGSS silver1 are pret's TRAINER_PASSERBY_BOY entries, which the Cherrygrove script uses for Silver's",
    "#   first battle (pret's TRAINER_RIVAL_SILVER_2, _3 and _6 carry the same team but no script starts them).",
    "# badge: the 0-based bit of the badge this gym fight earns in the badge word the tracker reads (readBadges). How each",
    "#   game's bits were derived, all from the disassembly (checked again by --check):",
    "#   dp:   badge byte at PlayerProfile+0x1A (tracker 0x292). pokediamond include/constants/badge.h: Coal 0, Forest 1,",
    "#         Cobble 2, Fen 3, Relic 4, Mine 5, Icicle 6, Beacon 7. Roark, Gardenia, Maylene, Wake, Fantina, Byron,",
    "#         Candice and Volkner hand over Coal, Forest, Cobble, Fen, Relic, Mine, Icicle and Beacon (gym N = bit N-1).",
    "#   pt:   badge byte TrainerInfo.badgeMask at +0x1A (tracker 0x96). pokeplatinum generated/badges.txt has the same",
    "#         order as Diamond: Coal 0, Forest 1, Cobble 2, Fen 3, Relic 4, Mine 5, Icicle 6, Beacon 7. Each leader's gym",
    "#         script calls GiveBadge with that constant (src/journal.c lists the same pairs), so in FIGHT order Roark 0,",
    "#         Gardenia 1, Fantina 4 (third gym, bit 4), Maylene 2, Wake 3, Byron 5, Candice 6, Volkner 7.",
    "#   hgss: Johto byte PlayerProfile.johtoBadges at +0x1A (tracker 0x8E) and Kanto byte kantoBadges at +0x1F (tracker",
    "#         0x93), combined as Johto bits 0-7 and Kanto bits 8-15 like readBadges. pokeheartgold include/constants/",
    "#         badge.h: Zephyr 0, Hive 1, Plain 2, Fog 3, Storm 4, Mineral 5, Glacier 6, Rising 7 (Falkner ... Clair, gym",
    "#         N = bit N-1) and Boulder 8, Cascade 9, Thunder 10, Rainbow 11, Soul 12, Marsh 13, Volcano 14, Earth 15",
    "#         (Brock 8, Misty 9, Lt. Surge 10, Erika 11, Janine 12, Sabrina 13, Blaine 14, Blue 15). Clair's Rising Badge",
    "#         is handed over in the Dragon's Den after her gym battle, so bit 7 is set later than the fight.",
    "#   The tracker's own badge numbers in nds/trainer-groups.tsv (1-based) agree with these bits.",
    "# group: HGSS post rows that share a non-empty group can be fought in any order (Kanto gyms); Blue stands alone",
    "#   because he must be last; Red is on Mt. Silver. The Elite Four order is fixed in all three games.",
    "#",
    "# game\tseq\tkey\tkind\tlabel\tcap\tace\tids\tbadge\tgroup",
]


def build_all():
    problems = []
    evidence = []
    notes = []
    data = []
    for game in ("dp", "pt", "hgss"):
        data.extend(build_game(game, problems, evidence, notes))
    if any("research doc says" in n for n in notes):
        docline = ("# The research doc (docs/research/nuzlocke-gen4-5.md sections 2.1, 2.2, 3.1) disagrees with the game data "
                   "in %d cap(s); the game data is used." % sum(1 for n in notes if "research doc says" in n))
    else:
        docline = ("# The research doc's numbers (docs/research/nuzlocke-gen4-5.md sections 2.1, 2.2, 3.1) agree with "
                   "every value here.")
    header = [docline if h == "@DOCLINE@" else h for h in HEADER]
    text = C.render(header + data)
    return text, problems, evidence, notes


def main(argv):
    check = "--check" in argv
    try:
        text, problems, evidence, notes = build_all()
    except C.MissingSource as e:
        return C.finish(FILENAME, [], missing=[str(e)])
    for e in evidence:
        print("  " + e)
    for n in notes:
        print("  NOTE " + n)
    if not notes:
        print("  research doc: every level cap in sections 2.1, 2.2 and 3.1 that this file carries matches the game data")
    if check:
        C.compare_with_shipped(FILENAME, text, problems)
        return C.finish(FILENAME, problems)
    if problems:
        return C.finish(FILENAME, problems)
    C.write_out(FILENAME, text)
    return C.EXIT_OK


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
