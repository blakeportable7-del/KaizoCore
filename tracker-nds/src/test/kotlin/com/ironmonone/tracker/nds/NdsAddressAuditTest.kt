package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every DS address in [NdsGameMap] against the reference tracker's
 * MemoryAddresses.lua (snapshotted by tools/trainer-data/convert_addresses.py
 * into nds-addresses.tsv). Same reason as the Gen 3 audit: the map headers,
 * badge bytes and battle pointers are hand-copied numbers.
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
        "statStagesEnemy" to { m: NdsGameMap -> m.statStagesEnemy },
        "battleSubscriptMsgs" to { m: NdsGameMap -> m.battleSubscriptMsgs },
        "itemStartNoBattle" to { m: NdsGameMap -> m.itemStartNoBattle },
        "childMapHeader" to { m: NdsGameMap -> m.childMapHeader },
        "parentMapHeader" to { m: NdsGameMap -> m.parentMapHeader },
    )

    @Test
    fun `every copied DS address matches the reference tracker`() {
        var checked = 0
        val wrong = ArrayList<String>()
        for (map in maps) {
            for ((symbol, read) in fields) {
                val want = reference[map.name to symbol] ?: continue
                val got = read(map)
                if (got == 0L) continue
                checked++
                if (got != want) wrong += "${map.name} $symbol: ours 0x%08X, reference 0x%08X".format(got, want)
            }
        }
        assertTrue(wrong.isEmpty(), "DS addresses disagreeing with the reference:\n" + wrong.joinToString("\n"))
        assertTrue(checked > 40, "only $checked DS addresses were compared; the fixture or the field list is wrong")
    }
}
