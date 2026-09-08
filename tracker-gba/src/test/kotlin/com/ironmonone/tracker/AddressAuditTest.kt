package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every Gen 3 address in [GameMap] against the reference tracker's own
 * GameAddresses JSON (snapshotted by tools/trainer-data/convert_addresses.py
 * into gen3/addresses.tsv). This is the check the side screens most needed:
 * Battle Details, Catch Rates and Trainers On Route are twenty-odd numbers
 * typed by hand, and a single wrong digit reads plausible rubbish rather
 * than failing. A live battle cannot be driven in a unit test; a wrong
 * address can still be caught here.
 */
class AddressAuditTest {
    private val reference: Map<Pair<String, String>, Long> by lazy {
        val out = HashMap<Pair<String, String>, Long>()
        val stream = javaClass.getResourceAsStream("/gen3/addresses.tsv") ?: fail("gen3/addresses.tsv missing")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("#")) return@forEach
                val p = line.split('\t'); if (p.size < 3) return@forEach
                out[p[0] to p[1]] = java.lang.Long.decode(p[2])
            }
        }
        out
    }

    private val maps = listOf(GameMap.EMERALD_U, GameMap.FIRERED_U_V10, GameMap.FIRERED_U_V11, GameMap.LEAFGREEN_U, GameMap.RUBY_U, GameMap.SAPPHIRE_U)

    /** Every field of ours that is a straight copy of a reference symbol. */
    private val fields: List<Pair<String, (GameMap) -> Long>> = listOf(
        "gPlayerParty" to { m: GameMap -> m.party },
        "gPlayerPartyCount" to { m: GameMap -> m.partyCount },
        "gEnemyParty" to { m: GameMap -> m.enemyParty },
        "gBattleTypeFlags" to { m: GameMap -> m.battleTypeFlags },
        "gBattleMons" to { m: GameMap -> m.battleMons },
        "gBattlersCount" to { m: GameMap -> m.battlersCount },
        "gBattleOutcome" to { m: GameMap -> m.battleOutcome },
        "gBattleMainFunc" to { m: GameMap -> m.battleMainFunc },
        "gBattleWeather" to { m: GameMap -> m.weather },
        "gBaseStats" to { m: GameMap -> m.baseStats },
        "gLevelUpLearnsets" to { m: GameMap -> m.levelUpLearnsets },
        "gMapHeader" to { m: GameMap -> m.mapHeader },
        "gExperienceTables" to { m: GameMap -> m.expTables },
        "gTrainers" to { m: GameMap -> m.gTrainers },
        "gTrainerClassNames" to { m: GameMap -> m.gTrainerClassNames },
        "gBattleTerrain" to { m: GameMap -> m.battleTerrain },
        "gBattleStructPtr" to { m: GameMap -> m.battleStructPtr },
        "gStatuses3" to { m: GameMap -> m.statuses3 },
        "gSideStatuses" to { m: GameMap -> m.sideStatuses },
        "gSideTimers" to { m: GameMap -> m.sideTimers },
        "gDisableStructs" to { m: GameMap -> m.disableStructs },
        "gLockedMoves" to { m: GameMap -> m.lockedMoves },
        "gWishFutureKnock" to { m: GameMap -> m.wishFutureKnock },
        "gBattleResults" to { m: GameMap -> m.battleResults },
        "HandleTurnActionSelectionState" to { m: GameMap -> m.handleTurnAction },
        "ReturnFromBattleToOverworld" to { m: GameMap -> m.returnToOverworld },
        "bagPocket_Items_offset" to { m: GameMap -> m.bagItemsOffset },
        "bagPocket_Berries_offset" to { m: GameMap -> m.bagBerriesOffset },
        "bagPocket_Balls_offset" to { m: GameMap -> m.bagBallsOffset },
        "bagPocket_Balls_Size" to { m: GameMap -> m.bagBallsSlots.toLong() },
    )

    @Test
    fun `every copied address matches the reference tracker`() {
        var checked = 0
        val wrong = ArrayList<String>()
        for (map in maps) {
            for ((symbol, read) in fields) {
                val want = reference[map.name to symbol] ?: continue
                val got = read(map)
                if (got == 0L) continue     // not pinned for this build; other tests cover the features that need it
                checked++
                // The thumb bit is set on the two function addresses in our maps, as the reference's own comparison expects.
                val match = got == want || got == (want or 1L)
                if (!match) wrong += "${map.name} $symbol: ours 0x%08X, reference 0x%08X".format(got, want)
            }
        }
        assertTrue(wrong.isEmpty(), "addresses disagreeing with the reference:\n" + wrong.joinToString("\n"))
        assertTrue(checked > 120, "only $checked addresses were compared; the fixture or the field list is wrong")
    }

    /**
     * gPaydayMoney is the one address NOT copied: the reference's JSON puts
     * Emerald's on gWishFutureKnock, which made Future Sight report Pay Day
     * money. Ours is derived from gBattleOutcome the way FireRed's checks
     * out. Nail it down so a later "fix" back to the JSON is a red test.
     */
    @Test
    fun `pay day is deliberately not the reference value on Emerald`() {
        assertEquals(0x0202432EL, GameMap.EMERALD_U.paydayMoney)
        assertEquals(GameMap.EMERALD_U.wishFutureKnock, reference["Emerald (U)" to "gPaydayMoney"])
        // FireRed's own JSON value sits the same distance below gBattleOutcome, which is why the derivation is trusted.
        assertEquals(0x02023E7EL, GameMap.FIRERED_U_V10.paydayMoney)
        assertEquals(GameMap.FIRERED_U_V10.paydayMoney, reference["FireRed (U) v1.0" to "gPaydayMoney"])
        assertEquals(GameMap.EMERALD_U.battleOutcome - GameMap.EMERALD_U.paydayMoney, GameMap.FIRERED_U_V10.battleOutcome - GameMap.FIRERED_U_V10.paydayMoney)
    }
}
