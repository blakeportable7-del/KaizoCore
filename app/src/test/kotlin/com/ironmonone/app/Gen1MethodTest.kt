package com.ironmonone.app

import com.dabomstew.pkrandomzx.RandomSource
import com.dabomstew.pkrandomzx.pokemon.ExpCurve
import com.dabomstew.pkrandomzx.romhandlers.Gen1RomHandler
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Gen 1's two official ways, on the real Red, Blue and Yellow dumps
 * (IRONMON_ROMS holding red-u.gbc, blue-u.gbc, yellow-u.gbc; each is skipped
 * without its dump). The pseudo-fluctuating patch rewrites the Medium Slow
 * entry of the growth formula table, and PART 1 puts every Pokemon but the
 * legendaries on Medium Slow, so the patched game with PART 1 alone plays on
 * the patch's curve. PART 2 puts every Pokemon on Slow, which is the other
 * way and leaves the patch unused: every run used to take it.
 */
class Gen1MethodTest {
    private val presets = File("src/main/assets/presets")
    private val bundled by lazy { presets.listFiles { f -> f.extension == "rnqs" }!!.associate { it.name to it.readBytes() } }

    private fun curves(f: File): Map<Boolean, Set<ExpCurve>> {
        val h = Gen1RomHandler.Factory().create(RandomSource.instance())
        assertTrue(h.loadRom(f.absolutePath), f.name)
        return h.pokemon.filterNotNull().groupBy({ it.isLegendary }, { it.growthCurve }).mapValues { it.value.toSet() }
    }

    @Test
    fun `a patched build takes PART 1 only and keeps the patch's curve, a vanilla one takes both passes`() {
        val roms = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory }
            ?: return println("Gen1MethodTest skipped: set IRONMON_ROMS")
        val part1 = File(presets, "RBY Kaizo.rnqs")
        var checked = 0
        for (base in listOf(RomKind.RED_U, RomKind.BLUE_U, RomKind.YELLOW_U)) {
            val clean = File(roms, base.id + "." + base.fileExtension).takeIf { it.isFile } ?: continue
            val pf = RomKind.allPatched.first { it.baseId == base.id && it.patchTag == "pseudofluct" }
            val built = File.createTempFile("pfbuild", ".gbc")
            val patchedRun = File.createTempFile("pfrun", ".gbc")
            val vanillaRun = File.createTempFile("run", ".gbc")
            try {
                Patcher.applyFiles(File("src/main/assets/patches/pseudofluct-${base.id}.bps"), clean, built)
                val choices = ExtraPasses.Choices(File.createTempFile("choices", ".txt").apply { delete() })
                // What each run takes, from the same decision RUN and NEW RUN use.
                val onPatched = ExtraPasses.part2On(pf, part1.name, part1.readBytes(), bundled, choices)
                val onVanilla = ExtraPasses.part2On(base, part1.name, part1.readBytes(), bundled, choices)
                assertFalse(onPatched, "${pf.id}: the patch with PART 1 is one way; PART 2 would undo it")
                assertTrue(onVanilla, "${base.id}: without the patch, the official way is both passes")
                val part2 = File(presets, Randomizers.GEN1_SECOND_PASS)
                Randomizers.randomize(pf, built, part1, patchedRun, 20260929L, secondPass = part2.takeIf { onPatched })
                Randomizers.randomize(base, clean, part1, vanillaRun, 20260929L, secondPass = part2.takeIf { onVanilla })

                // The patch's bytes (the Medium Slow formula), apart from the header checksum, are still there...
                val c = clean.readBytes(); val p = built.readBytes(); val r = patchedRun.readBytes()
                val patchBytes = p.indices.filter { p[it] != c[it] && it !in 0x14E..0x14F }
                assertEquals(4, patchBytes.size, "${pf.id}: the patch writes the one formula entry")
                assertTrue(patchBytes.all { r[it] == p[it] }, "${pf.id}: the run kept the patched formula")
                // ...and every Pokemon but the legendaries is on it.
                val patched = curves(patchedRun)
                assertEquals(setOf(ExpCurve.MEDIUM_SLOW), patched[false], "${pf.id}: non-legendaries on the patched Medium Slow")
                assertEquals(setOf(ExpCurve.SLOW), patched[true], "${pf.id}: legendaries Slow")
                // The vanilla run took PART 2: every curve Slow.
                assertEquals(setOf(ExpCurve.SLOW), curves(vanillaRun).values.flatten().toSet(), "${base.id}: PART 2 ran")
                checked++
            } finally {
                for (f in listOf(built, patchedRun, vanillaRun)) { f.delete(); Randomizers.logFor(f).delete() }
            }
        }
        println("GEN1_METHOD_CHECKED=$checked")
    }
}
