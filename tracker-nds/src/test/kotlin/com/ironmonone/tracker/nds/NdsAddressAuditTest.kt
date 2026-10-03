package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every DS address in [NdsGameMap] against the reference tracker's
 * MemoryAddresses.lua (snapshotted by tools/trainer-data/convert_addresses.py
 * into nds-addresses.tsv). Same reason as the Gen 3 audit: the map headers,
 * badge bytes and battle pointers are hand-copied numbers.
 *
 * Since NDS-Ironmon-Tracker 6.3.11 the fixture's Black 2 and White 2 rows are offsets from the pointer at
 * MAIN_POINTER, not addresses (its old fixed values are gone from the reference). Those two maps are read here
 * through [NdsGameMap.atPointerBase] with a base of zero, which turns each offset into the field it lands in, so
 * a wrong name-to-field mapping fails the same way a wrong number does; the whole table is then compared
 * with the reference's in [pointer tables are the reference's].
 */
class NdsAddressAuditTest {
    private val reference: Map<Pair<String, String>, Long> by lazy {
        val out = HashMap<Pair<String, String>, Long>()
        val stream = javaClass.getResourceAsStream("/nds-addresses.tsv") ?: fail("nds-addresses.tsv missing")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("#")) return@forEach
                val p = line.split('\t'); if (p.size < 3) return@forEach
                out[p[0] to p[1]] = java.lang.Long.decode(p[2])
            }
        }
        out
    }

    private val maps = listOf(NdsGameMap.DP, NdsGameMap.PLATINUM, NdsGameMap.HGSS, NdsGameMap.BW, NdsGameMap.WHITE, NdsGameMap.B2W2, NdsGameMap.WHITE2)

    private val fields: List<Pair<String, (NdsGameMap) -> Long>> = listOf(
        "playerBase" to { m: NdsGameMap -> m.playerBase },
        "enemyBase" to { m: NdsGameMap -> m.enemyBase },
        "enemyTrainerID" to { m: NdsGameMap -> m.enemyTrainerId },
        "playerBattleMonPID" to { m: NdsGameMap -> m.playerBattleMonPid },
        "enemyBattleMonPID" to { m: NdsGameMap -> m.enemyBattleMonPid },
        "statStagesPlayer" to { m: NdsGameMap -> m.statStagesPlayer },
        // Gen 5's name for the same field, and the badge byte (the first, Johto's on HGSS has no row of this name).
        "statStagesStart" to { m: NdsGameMap -> m.statStagesPlayer },
        "badges" to { m: NdsGameMap -> m.badgeOffsets.first() },
        "statStagesEnemy" to { m: NdsGameMap -> m.statStagesEnemy },
        "battleSubscriptMsgs" to { m: NdsGameMap -> m.battleSubscriptMsgs },
        "itemStartNoBattle" to { m: NdsGameMap -> m.itemStartNoBattle },
        "childMapHeader" to { m: NdsGameMap -> m.childMapHeader },
        "parentMapHeader" to { m: NdsGameMap -> m.parentMapHeader },
        // Added 2026-09-28: White 2's battleStatus was shifted +0x80 with the rest while the
        // reference gives +0x40, and this list did not include it, so the audit passed.
        "battleStatus" to { m: NdsGameMap -> m.battleStatus },
        "itemStartBattle" to { m: NdsGameMap -> m.itemStartBattle },
        "berryBagStart" to { m: NdsGameMap -> m.berryBagStart },
        "berryBagStartBattle" to { m: NdsGameMap -> m.berryBagStartBattle },
        "repelSteps" to { m: NdsGameMap -> m.repelSteps },
        "mainBattleDataPtr" to { m: NdsGameMap -> m.mainBattleDataPtr },
        "doubleTripleFlag" to { m: NdsGameMap -> m.doubleTripleFlag },
        "abilityTriggerStart" to { m: NdsGameMap -> m.abilityTriggerStart },
        "totalMonsParty" to { m: NdsGameMap -> m.totalMonsParty },
        "playerBattleBase" to { m: NdsGameMap -> m.playerBattleBase },
        // HeartGold and SoulSilver's own rows, which only other tests' literals checked (rc32 audit P3 #117): the League
        // byte drives Survival's 7 heals for Kanto, the weekday the Bug-Catching Contest, and the two badge bytes.
        "leagueBeaten" to { m: NdsGameMap -> m.leagueBeaten },
        "dayOfWeek" to { m: NdsGameMap -> m.dayOfWeek },
        "johtoBadges" to { m: NdsGameMap -> m.badgeOffsets.first() },
        "kantoBadges" to { m: NdsGameMap -> m.badgeOffsets.getOrElse(1) { 0L } },
    )

    @Test
    fun `every copied DS address matches the reference tracker`() {
        var checked = 0
        val wrong = ArrayList<String>()
        for (map in maps) {
            // A map with a pointer table is read at base 0, where each field IS its offset; any other map is itself.
            val at = map.atPointerBase(0)
            for ((symbol, read) in fields) {
                val want = reference[map.name to symbol] ?: continue
                val got = read(at)
                if (got == 0L) continue
                checked++
                if (got != want) wrong += "${map.name} $symbol: ours 0x%08X, reference 0x%08X".format(got, want)
            }
        }
        assertTrue(wrong.isEmpty(), "DS addresses disagreeing with the reference:\n" + wrong.joinToString("\n"))
        assertTrue(checked > 40, "only $checked DS addresses were compared; the fixture or the field list is wrong")
    }

    @Test
    fun `pointer tables are the reference's, name for name, and battleStatus stays its GLOBAL address`() {
        for (map in listOf(NdsGameMap.B2W2, NdsGameMap.WHITE2)) {
            val table = map.pointerOffsets ?: fail("${map.name} has no pointer table")
            assertEquals(reference[map.name to "MAIN_POINTER"], table.pointer, "${map.name} MAIN_POINTER")
            // Every row the fixture has for this game except the two that are not in POINTER_OFFSETS.
            val rows = reference.filterKeys { it.first == map.name && it.second != "MAIN_POINTER" && it.second != "battleStatus" }
                .mapKeys { it.key.second }
            assertTrue(rows.size >= 26, "only ${rows.size} POINTER_OFFSETS rows for ${map.name}; the fixture is stale")
            assertEquals(rows.toSortedMap(), table.offsets.toSortedMap(), "${map.name}: our table against the reference's POINTER_OFFSETS, both ways")
            // GLOBAL: the one address the reference does not move with the pointer, and neither does atPointerBase.
            val global = reference[map.name to "battleStatus"] ?: fail("no battleStatus row for ${map.name}")
            assertEquals(global, map.battleStatus, "${map.name} battleStatus")
            assertEquals(global, map.atPointerBase(0x204CC4L).battleStatus, "${map.name} battleStatus after a rebase")
        }
        // Black and White (1), Gen 4: no table.
        for (map in listOf(NdsGameMap.BW, NdsGameMap.WHITE, NdsGameMap.DP, NdsGameMap.PLATINUM, NdsGameMap.HGSS))
            assertEquals(null, map.pointerOffsets, "${map.name} is not read through a pointer")
    }
}
