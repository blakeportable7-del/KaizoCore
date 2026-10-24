package com.ironmonone.app.engine

import com.ironmonone.app.Dumps
import com.ironmonone.app.engine.hns.HnsUprRules
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Heart & Soul Nat. Dex Kaizo (Blake, 2026-10-08), replacing the withdrawn Black 2 pool:
 * - Moves are the Nat. Dex fork's list, plus every Fairy-type move. Vanilla stays Gen 1 to 3.
 * - No HM move in any roll.
 * - Abilities stop at Air Lock (76) on the Nat. Dex pool. The hidden slot is the first ability.
 */
class HnsBlack2RulesTest {
    private val assets = HnsEngine.folderAssets(File("src/main/assets"))
    private val presets = File("src/main/assets/presets")

    private fun skip(): Boolean = (HnsEngineTest.rom == null).also {
        if (it && Dumps.required) fail("IRONMON_REQUIRE_DUMPS: hns-kaizo.gba is missing")
        if (it) println("HnsBlack2RulesTest skipped: no .vendor/hns/hns-kaizo.gba")
    }

    private fun run(preset: String, seed: Long, pool: HnsEngine.Pool) =
        HnsEngine.randomize(HnsEngineTest.rom!!, HnsEngine.readSettings(File(presets, "$preset.rnqs")), seed, pool, assets)

    /** Every move id a run can hand a Pokemon: learnsets, egg moves, the TMs. */
    private fun rolledMoves(g: com.ironmonone.app.engine.hns.HnsGame): Map<String, Set<Int>> {
        val L = HnsEngineTest.layout
        val tms = (0 until L.machines.count { it.kind == "TM" }).map { g.machineMoves[it] }.toSet()
        val learn = HashSet<Int>(); val egg = HashSet<Int>()
        for (m in g.mons) if (m != null && m.enabled) { m.learnset.forEach { learn += it.move }; egg += m.eggMoves }
        return mapOf("learnsets" to learn, "egg moves" to egg, "TMs" to tms)
    }

    @Test
    fun `no HM move is ever rolled, in either pool`() {
        if (skip()) return
        val L = HnsEngineTest.layout
        val hm = L.machines.filter { it.kind == "HM" }.map { it.move }.toSet()
        val vanilla = HnsEngineTest.vanilla
        val bad = ArrayList<String>()
        for ((preset, pool) in listOf("RSE Kaizo" to HnsEngine.Pool.VANILLA, "RSE NatDex v1.2 Kaizo" to HnsEngine.Pool.NATDEX,
            "RSE NatDex v1.2 Survival" to HnsEngine.Pool.NATDEX)) for (seed in 1L..4L) {
            val g = HnsEngineTest.read(run(preset, seed, pool).rom)
            for ((where, ids) in rolledMoves(g)) {
                // A Pokemon whose learnset the randomizer left as it was keeps its own moves: only rolled lists count.
                val rolled = if (where == "learnsets") g.mons.filterNotNull().filter { m -> m.enabled && m.learnset.map { it.move } != vanilla.mons[m.id]?.learnset?.map { it.move } }
                    .flatMap { m -> m.learnset.map { it.move } }.toSet() else ids
                val hit = rolled intersect hm
                if (hit.isNotEmpty()) bad += "$preset $pool seed $seed $where: ${hit.map { g.moves[it]?.name }}"
            }
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    @Test
    fun `Nat Dex moves are the fork's list plus Fairy, and abilities stop at Air Lock`() {
        if (skip()) return
        val rules = HnsUprRules(zx = false)
        assertEquals(null, rules.onlyMoves)
        val vanilla = HnsEngineTest.vanilla
        val fairy = vanilla.typeNames.entries.first { it.value == "FAIRY" }.key
        val airLock = HnsEngineTest.layout.enumValue("ABILITY", "ABILITY_AIR_LOCK")!!
        assertEquals(76, airLock)
        var sawFairy = false
        for (seed in 1L..3L) {
            val g = HnsEngineTest.read(run("RSE NatDex v1.2 Kaizo", seed, HnsEngine.Pool.NATDEX).rom)
            val rolled = g.mons.filterNotNull().filter { m -> m.enabled && m.learnset.map { it.move } != vanilla.mons[m.id]?.learnset?.map { it.move } }
                .flatMap { m -> m.learnset.map { it.move } }.toSet() + rolledMoves(g).getValue("TMs")
            for (id in rolled) {
                val mv = g.moves[id] ?: continue
                if (mv.type == fairy) sawFairy = true
                val names = HnsEngineTest.layout.enum("MOVE").entries.filter { it.value == id }.map { it.key.removePrefix("MOVE_") }
                val allowed = names.none { it in rules.bannedMoves } || mv.type == fairy
                assertTrue(allowed, "seed $seed: ${mv.name} is outside the Nat Dex list and is not Fairy")
            }
            for (m in g.mons) if (m != null && m.enabled) for (a in m.abilities) {
                if (a > 76) assertTrue(false, "seed $seed: ${m.name} has ability $a")
            }
        }
        assertTrue(sawFairy, "no Fairy move was rolled")
    }

    @Test
    fun `Nat Dex keeps the full species list`() {
        if (skip()) return
        val g = HnsEngineTest.read(run("RSE NatDex v1.2 Kaizo", 1L, HnsEngine.Pool.NATDEX).rom)
        val enabled = g.mons.count { it != null && it.enabled }
        assertTrue(enabled >= 1400, "Nat Dex enabled species $enabled, expected the full Heart & Soul list")
    }
}
