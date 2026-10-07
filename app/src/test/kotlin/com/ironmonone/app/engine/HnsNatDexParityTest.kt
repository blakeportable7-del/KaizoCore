package com.ironmonone.app.engine

import com.ironmonone.app.engine.HnsEngineTest.Companion.rom
import com.ironmonone.app.engine.hns.HnsRandomizer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * rc38, "in line with Emerald Nat. Dex" (Blake, 2026-10-06): seed sweeps over every Nat. Dex v1.2 mode on both pools,
 * through the app's path (HnsEveryModeTest.make), holding Heart & Soul to what the Nat. Dex fork does on Nat. Dex
 * Emerald 1.2.1 (docs/HNS-KAIZO.md, "rc38: in line with Emerald Nat. Dex"):
 * - no evolution asks for a trade (Nat. Dex Emerald's own table has none);
 * - abilities roll from the pool's game (Blake, 2026-10-07): Nat. Dex 1 to 164 as Black 2 / White 2 Kaizo, Vanilla 1 to
 *   76 (Air Lock) as vanilla Emerald Kaizo; Nat. Dex rolls all three slots, Vanilla's hidden slot is its first ability;
 *   each game's always-banned list and the mode's bans hold, Wonder Guard stays only where it was,
 *   and the forms UPR copies take their base's abilities;
 * - the item lists are the fork's allowed and non-bad lists, and the items a run places come from them.
 * Needs the comfort build, as HnsEveryModeTest does; without it the test returns early.
 */
class HnsNatDexParityTest {
    private val assets = HnsEngine.folderAssets(File("src/main/assets"))
    private val seeds = listOf(20261006L, 7L)

    private fun settings(mode: String) = File(HnsEveryModeTest.presets, "RSE NatDex v1.2 $mode.rnqs")

    @Test
    fun `no evolution asks for a trade, every mode, both pools, two seeds`() {
        if (rom == null || HnsEveryModeTest.romFile == null) return
        val vanilla = HnsEveryModeTest.vanilla
        val trade = vanilla.L.enumValue("EVO", "EVO_TRADE")!!
        // The check can fail: the build's own table has trade evolutions (Kadabra, Machoke, Haunter...).
        val before = vanilla.mons.filterNotNull().sumOf { m -> m.evos.count { it.method == trade } }
        assertTrue(before >= 20, "the build has $before trade evolutions; expected Heart & Soul's ~30")
        val out = StringBuilder()
        val fails = ArrayList<String>()
        for (mode in HnsEveryModeTest.MODES) for (pool in HnsEngine.Pool.entries) for (seed in seeds) {
            val made = HnsEveryModeTest.make(settings(mode), pool, seed)
            val g = HnsEveryModeTest.read(made.bytes)
            val left = g.mons.filterNotNull().filter { it.enabled }.flatMap { m -> m.evos.filter { it.method == trade }.map { "${m.const} -> ${it.target}" } }
            val section = made.logText.lines().dropWhile { it.trim() != "--Removing Impossible Evolutions--" }.drop(1).takeWhile { it.isNotBlank() }
            out.appendLine("$mode|$pool|$seed|trade left ${left.size}|converted ${section.size}|${section.take(3)}")
            if (left.isNotEmpty()) fails += "$mode $pool $seed: $left"
            if (mode != "Evo Kaizo" && section.isEmpty()) fails += "$mode $pool $seed: no --Removing Impossible Evolutions-- lines"
            if (section.any { !it.contains(" at level ") && !it.contains(" using a ") }) fails += "$mode $pool $seed: $section"
        }
        File("build").mkdirs(); File("build/hns-natdex-evos.txt").writeText(out.toString())
        assertTrue(fails.isEmpty(), fails.joinToString("\n") + "\n" + out)
    }

    @Test
    fun `the same seed makes the same run`() {
        if (rom == null || HnsEveryModeTest.romFile == null) return
        val a = HnsEveryModeTest.make(settings("Kaizo"), HnsEngine.Pool.NATDEX, 99L)
        val b = HnsEveryModeTest.make(settings("Kaizo"), HnsEngine.Pool.NATDEX, 99L)
        assertTrue(a.bytes.contentEquals(b.bytes) && a.logText == b.logText)
    }

    @Test
    fun `abilities roll from the pool's game, Black 2 White 2 on Nat Dex and Emerald on Vanilla, and the mode's bans hold`() {
        if (rom == null || HnsEveryModeTest.romFile == null) return
        val vanilla = HnsEveryModeTest.vanilla
        val L = vanilla.L
        fun ab(n: String) = L.enumValue("ABILITY", "ABILITY_$n")
        // Heart & Soul numbers abilities the official way: Teravolt is 164 (Gen5Constants.highestAbilityIndex), Air Lock 76.
        assertEquals(164, ab("TERAVOLT")); assertEquals(76, ab("AIR_LOCK")); assertEquals(null, ab("CACOPHONY"))
        // No id up to 164 is a placeholder in this build.
        assertTrue((1..164).all { vanilla.abilityNames[it].isNotEmpty() && !vanilla.abilityNames[it].startsWith("-") })
        val wg = ab("WONDER_GUARD")!!
        val trapping = setOfNotNull(ab("SHADOW_TAG"), ab("MAGNET_PULL"), ab("ARENA_TRAP"))
        val negative = setOfNotNull(ab("TRUANT"), ab("SLOW_START"), ab("DEFEATIST"), ab("KLUTZ"), ab("STALL"))
        // ZX's always-banned lists: Gen5Constants.uselessAbilities for Black 2 / White 2, Gen3Constants' (Forecast, and
        // Cacophony, which Heart & Soul has not) for Emerald.
        val uselessGen5 = setOfNotNull(ab("FORECAST"), ab("MULTITYPE"), ab("FLOWER_GIFT"), ab("ZEN_MODE"))
        val uselessGen3 = setOfNotNull(ab("FORECAST"))
        val hadWg = vanilla.mons.filterNotNull().filter { wg in it.abilities }.map { it.id }.toSet()
        val fails = ArrayList<String>()
        val out = StringBuilder()
        for (mode in HnsEveryModeTest.MODES) for (pool in HnsEngine.Pool.entries) {
            val last = if (pool == HnsEngine.Pool.NATDEX) 164 else 76
            val useless = if (pool == HnsEngine.Pool.NATDEX) uselessGen5 else uselessGen3
            val o = HnsEngine.readSettings(settings(mode))
            val made = HnsEveryModeTest.make(settings(mode), pool, seeds[0])
            val g = HnsEveryModeTest.read(made.bytes)
            val seen = HashSet<Int>()
            var hiddenChanged = 0
            for (m in g.mons.filterNotNull()) {
                if (!m.enabled || m.id == g.speciesEgg || m.id in hadWg) continue
                if (m.abilities[2] != vanilla.mons[m.id]!!.abilities[2]) hiddenChanged++
                for (a in m.abilities) {
                    if (a == 0) continue
                    seen += a
                    if (a > last) fails += "$mode $pool: ${m.const} has ${g.abilityNames[a]} ($a)"
                    if (a in useless) fails += "$mode $pool: ${m.const} has ${g.abilityNames[a]}"
                    if (a == wg && !o.allowWonderGuard) fails += "$mode $pool: ${m.const} has Wonder Guard"
                    if (o.banTrappingAbilities && a in trapping) fails += "$mode $pool: ${m.const} has trapping ${g.abilityNames[a]}"
                    if (o.banNegativeAbilities && a in negative) fails += "$mode $pool: ${m.const} has negative ${g.abilityNames[a]}"
                }
                if (pool == HnsEngine.Pool.VANILLA && m.abilities[2] != m.abilities[0]) fails += "$mode $pool: ${m.const} hidden ${m.abilities[2]} is not its first ${m.abilities[0]}"
                if (pool == HnsEngine.Pool.NATDEX && m.abilities[2] == 0) fails += "$mode $pool: ${m.const} has no hidden ability ${m.abilities.toList()}"
            }
            val later = seen.count { it in 77..164 }
            if (pool == HnsEngine.Pool.NATDEX && later < 50) fails += "$mode $pool: only $later Gen 4-5 abilities seen"
            if (pool == HnsEngine.Pool.VANILLA && later > 0) fails += "$mode $pool: $later abilities past Air Lock"
            for ((form, base) in HnsRandomizer.FORM_ABILITIES_FROM_BASE) {
                val f = g.mons.firstOrNull { it?.const == "SPECIES_$form" } ?: continue
                val b = g.mons.firstOrNull { it?.const == "SPECIES_$base" } ?: continue
                if (f.enabled && b.enabled && !f.abilities.contentEquals(b.abilities)) fails += "$mode $pool: $form ${f.abilities.toList()} vs $base ${b.abilities.toList()}"
            }
            out.appendLine("$mode|$pool|${seen.size} abilities seen, $later of them 77-164, max ${seen.maxOrNull()}, hidden slots changed $hiddenChanged")
        }
        File("build").mkdirs(); File("build/hns-natdex-abilities.txt").writeText(out.toString())
        assertTrue(fails.isEmpty(), fails.take(40).joinToString("\n") + "\n(${fails.size})\n" + out)
    }

    @Test
    fun `item lists are the fork's allowed and non-bad lists, and a run places only those`() {
        if (rom == null || HnsEveryModeTest.romFile == null) return
        val vanilla = HnsEveryModeTest.vanilla
        val fails = ArrayList<String>()
        for (pool in HnsEngine.Pool.entries) {
            val allowedKeys = HnsEngine.poolAllowedKeys(pool, assets)
            val nonBadKeys = HnsEngine.poolNonBadKeys(pool, assets)
            assertTrue(nonBadKeys.all { it in allowedKeys }, "$pool: non-bad outside allowed ${nonBadKeys - allowedKeys}")
            val o = HnsEngine.readSettings(settings("Kaizo"))
            val r = HnsRandomizer(vanilla, o, 1L, pool, null, HnsEngine.poolItemKeys(pool, assets), nonBadKeys, null, allowedKeys)
            fun key(id: Int) = HnsRandomizer.itemKey(vanilla.items[id]!!.name)
            r.itemPoolForTest(false).filter { key(it) !in allowedKeys }.takeIf { it.isNotEmpty() }?.let { fails += "$pool allowed: ${it.map(::key)}" }
            r.itemPoolForTest(true).filter { key(it) !in nonBadKeys }.takeIf { it.isNotEmpty() }?.let { fails += "$pool non-bad: ${it.map(::key)}" }
            // A Kaizo run's placed items: field items (TMs aside), the lab trash can, the starter's item, wild held items.
            val made = HnsEveryModeTest.make(settings("Kaizo"), pool, seeds[0])
            val g = HnsEveryModeTest.read(made.bytes)
            val tms = g.L.machines.map { it.item }.toSet()
            val placed = g.fieldItems.map { it.item }.filter { it !in tms } +
                g.mons.filterNotNull().filter { it.enabled }.flatMap { listOf(it.itemCommon, it.itemRare) }.filter { it != 0 }
            val wrong = placed.filter { it != 0 && g.items.getOrNull(it)?.let { d -> HnsRandomizer.itemKey(d.name) in nonBadKeys } != true }
                .filter { g.items.getOrNull(it)?.pocket != g.L.enumValue("POCKET", "POCKET_KEY_ITEMS") }
            if (wrong.isNotEmpty()) fails += "$pool placed outside the non-bad list: ${wrong.distinct().map { g.items[it]?.name }}"
        }
        assertTrue(fails.isEmpty(), fails.joinToString("\n"))
    }
}
