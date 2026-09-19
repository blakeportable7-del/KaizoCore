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

    /**
     * Not one ROM address may survive the v1.0 -> v1.1 copy() unchanged.
     *
     * Every ROM table and every ROM code address moves between the revisions,
     * so an address EQUAL on both maps was inherited by accident. Seven were:
     * speciesNames, moveNames, abilityNames, itemNames, frontPics, palettes
     * and startersBase, and a v1.1 ROM read all of them 112 bytes early. The
     * numbers stayed right while the words went wrong, so FLAAFFY displayed as
     * CHINCHOU and SUPERPOWER as "E POWER" with no error anywhere (Blake,
     * 2026-09-16, from a trainer-battle screenshot).
     *
     * The fields list above could not catch it: it is hand-maintained and the
     * name tables were never added to it, the same reason the trainer tables
     * slipped through before. This reads the fields off the data class itself,
     * so a field added later is covered without anyone remembering to.
     */
    @Test
    fun `no ROM address is inherited unshifted between FireRed revisions`() {
        val v10 = longFields(GameMap.FIRERED_U_V10)
        val v11 = longFields(GameMap.FIRERED_U_V11)
        val rom = v10.filterValues { it in 0x08000000L..0x09FFFFFFL }
        // Non-vacuous: if the sweep ever stops seeing fields, fail loudly
        // instead of passing on an empty set.
        assertTrue(rom.size >= 17, "only ${rom.size} ROM addresses parsed; the field sweep broke")
        val inherited = rom.keys.filter { v11[it] == v10[it] }.sorted()
        assertTrue(inherited.isEmpty(), "inherited from v1.0 unshifted: $inherited")
    }

    /** Every Long-valued field, read off the data class's own toString(). */
    private fun longFields(m: GameMap): Map<String, Long> =
        Regex("""(\w+)=(-?\d+)""").findAll(m.toString())
            .associate { it.groupValues[1] to it.groupValues[2].toLong() }

    /** The v1.1 tables, located in Blake's own dump (tools/find_tables.py). */
    @Test
    fun `FireRed v1_1 name and graphics tables sit 0x70 past v1_0`() {
        val a = GameMap.FIRERED_U_V10
        val b = GameMap.FIRERED_U_V11
        assertEquals(0x70L, b.speciesNames - a.speciesNames)
        assertEquals(0x70L, b.moveNames - a.moveNames)
        assertEquals(0x70L, b.abilityNames - a.abilityNames)
        assertEquals(0x70L, b.itemNames - a.itemNames)
        assertEquals(0x70L, b.frontPics - a.frontPics)
        assertEquals(0x70L, b.palettes - a.palettes)
        // Code, not data, and it does NOT take the +0x70 the tables take.
        assertEquals(0x78L, b.startersBase - a.startersBase)
    }

    /**
     * GameSettings.FriendshipRequiredToEvo from the reference's GameAddresses
     * JSONs. The byte there reads 219 in every one of Blake's six dumps, so
     * each game requires 220. Ruby and Sapphire share one address, and so do
     * FireRed v1.0 and LeafGreen; v1.1 moves with the code, by +0x14.
     */
    @Test
    fun `friendship requirement addresses match the reference`() {
        assertEquals(0x0806D1D6L, GameMap.EMERALD_U.friendshipRequiredAddr)
        assertEquals(0x08043002L, GameMap.FIRERED_U_V10.friendshipRequiredAddr)
        assertEquals(0x08043016L, GameMap.FIRERED_U_V11.friendshipRequiredAddr)
        assertEquals(0x08043002L, GameMap.LEAFGREEN_U.friendshipRequiredAddr)
        assertEquals(0x0803F5CAL, GameMap.RUBY_U.friendshipRequiredAddr)
        assertEquals(0x0803F5CAL, GameMap.SAPPHIRE_U.friendshipRequiredAddr)
    }

    /**
     * LeafGreen is a copy() of FireRed v1.0, like v1.1 was, and inherits its four
     * battle code addresses. That is correct: they are byte-identical in both
     * dumps. Its starter table is switched off (0), so it inherits nothing there.
     */
    @Test
    fun `LeafGreen shares FireRed's battle code addresses`() {
        val fr = GameMap.FIRERED_U_V10; val lg = GameMap.LEAFGREEN_U
        assertEquals(0L, lg.startersBase)
        assertEquals(fr.handleTurnAction, lg.handleTurnAction)
        assertEquals(fr.introDrawPartySummary, lg.introDrawPartySummary)
        assertEquals(fr.introOpponentSendsOut, lg.introOpponentSendsOut)
        assertEquals(fr.returnToOverworld, lg.returnToOverworld)
    }
}
