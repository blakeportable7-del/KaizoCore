"""Snapshot both reference trackers' address tables as test fixtures, so a
hand-typed address in GameMap / NdsGameMap can be checked against the source
it was copied from.

    python tools/trainer-data/convert_addresses.py <Ironmon-Tracker/ironmon_tracker> <NDS-Ironmon-Tracker/ironmon_tracker> <repo root>

Writes tracker-gba/src/test/resources/gen3/addresses.tsv (revision, symbol,
value from GameAddresses/*.json) and tracker-nds/src/test/resources/nds-addresses.tsv
(map, symbol, value from MemoryAddresses.lua). AddressAuditTest and
NdsAddressAuditTest assert the app's constants against these.
"""
import sys, json, pathlib
from lupa import LuaRuntime

gba_ref = pathlib.Path(sys.argv[1]); nds_ref = pathlib.Path(sys.argv[2]); root = pathlib.Path(sys.argv[3])
tab = chr(9); nl = chr(10)

REVS = {"Emerald (U)": "Pokemon Emerald", "FireRed (U) v1.0": "Pokemon FireRed v1.0", "FireRed (U) v1.1": "Pokemon FireRed v1.1",
        "LeafGreen (U) v1.0": "Pokemon LeafGreen v1.0", "Ruby (U) v1.0": "Pokemon Ruby v1.0", "Sapphire (U) v1.0": "Pokemon Sapphire v1.0"}
WANT = ["gBattleTerrain", "gBattleWeather", "gBattleStructPtr", "gStatuses3", "gSideStatuses", "gSideTimers", "gDisableStructs",
        "gLockedMoves", "gWishFutureKnock", "gPaydayMoney", "gBattleResults", "gTrainers", "gTrainerClassNames", "gExperienceTables",
        "gBaseStats", "gPlayerParty", "gPlayerPartyCount", "gEnemyParty", "gBattleTypeFlags", "gBattleMons", "gBattlersCount",
        "gBattleOutcome", "gBattleMainFunc", "gMapHeader", "gLevelUpLearnsets", "gSaveBlock1ptr", "gSaveBlock2ptr",
        "bagPocket_Items_offset", "bagPocket_Berries_offset", "bagPocket_Balls_offset", "bagPocket_Balls_Size",
        "HandleTurnActionSelectionState", "ReturnFromBattleToOverworld"]
out = root / "tracker-gba/src/test/resources/gen3/addresses.tsv"
out.parent.mkdir(parents=True, exist_ok=True)
n = 0
with open(out, "w", encoding="utf-8", newline=nl) as f:
    f.write("# revision" + tab + "symbol" + tab + "value  (Ironmon-Tracker GameAddresses/*.json, converted by tools/trainer-data/convert_addresses.py)" + nl)
    for rev, fn in REVS.items():
        a = json.load(open(gba_ref / "GameAddresses" / (fn + ".json"), encoding="utf-8"))["Addresses"]
        for k in WANT:
            v = a.get(k)
            if v:
                f.write(rev + tab + k + tab + ("0x%08X" % int(str(v), 16)) + nl); n += 1
print("gen3 rows", n)

lua = LuaRuntime(unpack_returned_tuples=True)
lua.execute("GameInfo = { VERSION_NUMBER = { DIAMOND=0x45414441, PEARL=0x45415041, PLATINUM=0x45555043, HEART_GOLD=0x454B5049, SOUL_SILVER=0x45475049, BLACK=0x4F425249, WHITE=0x4F415249, BLACK2=0x4F455249, WHITE2=0x4F445249 } }")
lua.execute(open(nds_ref / "constants" / "MemoryAddresses.lua", encoding="utf-8").read())
ma = lua.globals().MemoryAddresses
NAMES = {0x45414441: "Pokemon Diamond / Pearl", 0x45555043: "Pokemon Platinum", 0x454B5049: "Pokemon HeartGold / SoulSilver",
         0x4F425249: "Pokemon Black", 0x4F415249: "Pokemon White", 0x4F455249: "Pokemon Black 2", 0x4F445249: "Pokemon White 2"}
out = root / "tracker-nds/src/test/resources/nds-addresses.tsv"
out.parent.mkdir(parents=True, exist_ok=True)
n = 0
with open(out, "w", encoding="utf-8", newline=nl) as f:
    f.write("# map" + tab + "symbol" + tab + "value  (NDS-Ironmon-Tracker MemoryAddresses.lua, converted by tools/trainer-data/convert_addresses.py)" + nl)
    for code, name in NAMES.items():
        e = ma[code]
        if e is None: continue
        for block in ("VERSION_POINTER_OFFSETS", "GLOBAL"):
            t = e[block]
            if t is None: continue
            for k in sorted(dict(t).keys()):
                v = t[k]
                if isinstance(v, (int, float)):
                    f.write(name + tab + str(k) + tab + ("0x%08X" % int(v)) + nl); n += 1
        for k in ("GLOBAL_POINTER", "VERSION_POINTER_OFFSET"):
            v = e[k]
            if isinstance(v, (int, float)):
                f.write(name + tab + str(k) + tab + ("0x%08X" % int(v)) + nl); n += 1
print("nds rows", n)
