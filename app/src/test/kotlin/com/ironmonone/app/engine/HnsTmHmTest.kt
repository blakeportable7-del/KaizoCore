package com.ironmonone.app.engine

import com.ironmonone.app.Dumps
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * TM moves and the HMs' moves (rc38.1): no TM teaches an HM's move, in either pool. The vanilla Emerald rule (engine-zx
 * bans getHMMoves() from the TMs); Blake: "apply the vanilla rule to natl dex".
 */
class HnsTmHmTest {
    private val assets = HnsEngine.folderAssets(File("src/main/assets"))
    private val presets = File("src/main/assets/presets")

    private fun skip(): Boolean = (HnsEngineTest.rom == null).also {
        if (it && Dumps.required) fail("IRONMON_REQUIRE_DUMPS: hns-kaizo.gba is missing")
        if (it) println("HnsTmHmTest skipped: no .vendor/hns/hns-kaizo.gba")
    }

    private fun tms(preset: String, seed: Long, pool: HnsEngine.Pool): Pair<List<Int>, HnsEngine.Result> {
        val r = HnsEngine.randomize(HnsEngineTest.rom!!, HnsEngine.readSettings(File(presets, "$preset.rnqs")), seed, pool, assets)
        val g = HnsEngineTest.read(r.rom)
        val count = HnsEngineTest.layout.machines.count { it.kind == "TM" }
        return (0 until count).map { g.machineMoves[it] } to r
    }

    @Test
    fun `no TM holds an HM's move, in either pool`() {
        if (skip()) return
        val L = HnsEngineTest.layout
        val hmMoves = L.machines.filter { it.kind == "HM" }.map { it.move }.toSet()
        assertEquals(8, hmMoves.size)
        val bad = ArrayList<String>()
        val runs = listOf("RSE Kaizo" to HnsEngine.Pool.VANILLA, "RSE Survival" to HnsEngine.Pool.VANILLA,
            "RSE NatDex v1.2 Kaizo" to HnsEngine.Pool.NATDEX, "RSE NatDex v1.2 Survival" to HnsEngine.Pool.NATDEX)
        for ((preset, pool) in runs) for (seed in 1L..8L) {
            val (list, _) = tms(preset, seed, pool)
            val hit = list.withIndex().filter { it.value in hmMoves }
            if (hit.isNotEmpty()) bad += "$preset $pool seed $seed: " + hit.joinToString { "TM%02d move %d".format(it.index + 1, it.value) }
            if (list.toSet().size != list.size) bad += "$preset $pool seed $seed: a move on two TMs"
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    @Test
    fun `Blake's attempt 6 has no Fly on TM45 any more`() {
        if (skip()) return
        // Blake's rc38 attempt 6 (2026-10-07): seed 275abc81d5293a1d, RSE NatDex v1.2 Kaizo, Nat. Dex pool. rc38 put FLY on
        // TM45, Whitney's.
        val seed = java.lang.Long.parseUnsignedLong("275abc81d5293a1d", 16)
        val (list, r) = tms("RSE NatDex v1.2 Kaizo", seed, HnsEngine.Pool.NATDEX)
        val tm = r.logText.substring(r.logText.indexOf("--TM Moves--"), r.logText.indexOf("--TM Compatibility--"))
        assertFalse("\nTM45 FLY\n" in tm, tm)
        val fly = HnsEngineTest.layout.machines.first { it.kind == "HM" && it.num == 2 }.move
        assertFalse(fly in list, tm)
    }
}
