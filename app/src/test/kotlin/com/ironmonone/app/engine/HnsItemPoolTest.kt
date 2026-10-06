package com.ironmonone.app.engine

import com.ironmonone.app.engine.HnsEngineTest.Companion.vanilla
import com.ironmonone.app.engine.HnsEngineTest.Companion.rom
import com.ironmonone.app.engine.hns.HnsOptions
import com.ironmonone.app.engine.hns.HnsRandomizer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Measures the item pools against their source games (Blake, 2026-10-05: does the starter item favor TMs?). */
class HnsItemPoolTest {
    private val assets = HnsEngine.folderAssets(File("src/main/assets"))

    @Test
    fun `each pool rolls the source game's UPR non-bad items, TM share within 2 points`() {
        if (rom == null) return
        val g = vanilla
        val tms = g.L.machines.filter { it.kind == "TM" }.map { it.item }.toSet()
        val o = HnsEngine.readSettings(File("src/main/assets/presets/${HnsEngineTest.KAIZO}.rnqs"))
        val hnsKeys = g.items.filterNotNull().filter { it.id != 0 && it.name.isNotEmpty() }.associateBy { HnsRandomizer.itemKey(it.name) }
        val out = StringBuilder()
        val shares = HashMap<HnsEngine.Pool, Triple<Double, Double, Boolean>>()
        for (pool in HnsEngine.Pool.values()) {
            val src = assets(HnsEngine.poolItemsAsset(pool)).lineSequence().map { it.trimEnd('\r') }
                .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.substringBefore('\t').toInt() to it.substringAfter('\t') }.toList()
            val upr = if (pool == HnsEngine.Pool.VANILLA) com.dabomstew.pkrandomzx.constants.Gen3Constants.getNonBadItems(2)
                      else null
            val uprN = if (pool == HnsEngine.Pool.NATDEX) com.dabomstew.pkrandom.constants.Gen3Constants.getNonBadItems(2) else null
            val srcNonBad = src.filter { (id, _) -> upr?.isAllowed(id) ?: uprN!!.isAllowed(id) }
            val srcTm = srcNonBad.count { (id, _) -> upr?.isTM(id) ?: uprN!!.isTM(id) }
            val unmatched = src.filter { (_, n) -> HnsRandomizer.itemKey(n) !in hnsKeys }.map { it.second }
            val unmatchedNonBad = srcNonBad.filter { (_, n) -> HnsRandomizer.itemKey(n) !in hnsKeys }.map { it.second }
            val r = HnsRandomizer(g, o, 1L, pool, null, HnsEngine.poolItemKeys(pool, assets), HnsEngine.poolNonBadKeys(pool, assets))
            val list = r.itemPoolForTest(true)
            val tmN = list.count { it in tms }
            var tmRolls = 0
            for (seed in 1L..1000L) if (HnsRandomizer(g, o, seed, pool, null, HnsEngine.poolItemKeys(pool, assets), HnsEngine.poolNonBadKeys(pool, assets)).starterItemRollForTest() in tms) tmRolls++
            out.appendLine("== $pool")
            out.appendLine("source nonBad (UPR): ${srcNonBad.size} items, $srcTm TMs, TM share ${"%.3f".format(srcTm.toDouble() / srcNonBad.size)}")
            out.appendLine("HnS pool (banBad): ${list.size} items, $tmN TMs, TM share ${"%.3f".format(tmN.toDouble() / list.size)}; starter rolls over 1000 seeds: $tmRolls TMs")
            out.appendLine("source names with no HnS item (${unmatched.size}): $unmatched")
            out.appendLine("of them in UPR's nonBad (${unmatchedNonBad.size}): $unmatchedNonBad")
            val srcNonBadKeys = srcNonBad.map { HnsRandomizer.itemKey(it.second) }.toSet()
            val inPoolNotNonBad = list.filter { HnsRandomizer.itemKey(g.items[it]!!.name) !in srcNonBadKeys }.map { g.items[it]!!.name }
            val nonBadNotInPool = srcNonBad.filter { (_, n) -> val h = hnsKeys[HnsRandomizer.itemKey(n)]; h != null && h.id !in list }.map { it.second }
            // Lucky Egg is banned by the preset (UPR does it in Gen3RomHandler); key items never move.
            shares[pool] = Triple(tmN.toDouble() / list.size, srcTm.toDouble() / srcNonBad.size, nonBadNotInPool.all { it.uppercase().replace(" ", "") in setOf("LUCKYEGG", "EXP.SHARE") })
            out.appendLine("in the HnS pool but not UPR's nonBad (${inPoolNotNonBad.size}): $inPoolNotNonBad")
            out.appendLine("in UPR's nonBad, in HnS, but not in the HnS pool (${nonBadNotInPool.size}): $nonBadNotInPool")
        }
        File("build/hns-item-pool.txt").writeText(out.toString())
        for ((pool, share) in shares) assertTrue(kotlin.math.abs(share.first - share.second) < 0.02 && share.third, "$pool: $out")
    }
}
