package com.ironmonone.app

import com.dabomstew.pkrandomzx.RandomSource
import com.dabomstew.pkrandomzx.romhandlers.Gen2RomHandler
import com.dabomstew.pkrandomzx.romhandlers.Gen3RomHandler
import com.dabomstew.pkrandomzx.romhandlers.RomHandler
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The official 60% levels on real dumps: IRONMON_ROMS holding emerald-u.gba
 * for the Emerald half and crystal-u.gbc for the Crystal half, each skipped
 * without its dump. The levels are read back out of the randomized ROM with
 * the randomizer's own handler and checked against the settings page's
 * arithmetic, per slot: the randomizer sets a level to min(100, round(L x
 * (1 + m/100))) for a modifier m, so the pre-pass then the mode's +50% gives
 * round(round(L x 1.06) x 1.5), about x1.59. Checked on every wild slot and
 * every trainer whose party the mode did not grow (Kaizo adds three Pokemon
 * to boss trainers, which moves their slots).
 */
class PrePassLevelsTest {
    private val presets = File("src/main/assets/presets")
    private val bundled by lazy { presets.listFiles { f -> f.extension == "rnqs" }!!.associate { it.name to it.readBytes() } }
    private fun rom(name: String): File? = Dumps.rom(name)

    private fun handler(gen: Generation, f: File): RomHandler {
        val factory = if (gen == Generation.GBC2) Gen2RomHandler.Factory() else Gen3RomHandler.Factory()
        return factory.create(RandomSource.instance()).also { assertTrue(it.loadRom(f.absolutePath), f.name) }
    }

    private fun scaled(l: Int, a: Double, b: Double): Int = minOf(100, (minOf(100, (l * a).roundToInt()) * b).roundToInt())

    /** Every wild slot and every unchanged-size trainer party follows [a] then [b]; returns the mean trainer ratio at level 10 and up. */
    private fun assertScaled(clean: RomHandler, out: RomHandler, a: Double, b: Double, what: String): Double {
        var checked = 0; var ratio = 0.0; var n = 0
        for ((t0, t1) in clean.trainers.zip(out.trainers)) {
            if (t0.pokemon.size != t1.pokemon.size) continue
            for ((p0, p1) in t0.pokemon.zip(t1.pokemon)) {
                assertEquals(scaled(p0.level, a, b), p1.level, "$what: ${t0.fullDisplayName} Lv${p0.level}")
                checked++
                if (p0.level >= 10) { ratio += p1.level.toDouble() / p0.level; n++ }
            }
        }
        for ((s0, s1) in clean.getEncounters(false).zip(out.getEncounters(false)))
            for ((e0, e1) in s0.encounters.zip(s1.encounters)) {
                assertEquals(scaled(e0.level, a, b), e1.level, "$what: ${s0.displayName} Lv${e0.level}")
                checked++
            }
        assertTrue(checked > 1000, "$what: only $checked levels checked")
        return ratio / n
    }

    private fun randomize(kind: RomKind, src: File, preset: String, withPrePass: Boolean): File {
        val dest = File.createTempFile("prepass", "." + kind.fileExtension)
        val pre = if (withPrePass) File(presets, ExtraPasses.prePassName(kind)!!) else null
        Randomizers.randomize(kind, src, File(presets, preset), dest, 20260929L, prePass = pre)
        return dest
    }

    private fun check(kind: RomKind, src: File, preset: String) {
        // The run takes the pass because the preset is the official one: the default, not the test, decides.
        val on = ExtraPasses.prePassOn(kind, preset, File(presets, preset).readBytes(), bundled,
            ExtraPasses.Choices(File.createTempFile("choices", ".txt").apply { delete() }))
        assertTrue(on, "the official $preset takes the 60% levels by default on ${kind.id}")
        val clean = handler(kind.generation, src)
        val sixty = randomize(kind, src, preset, withPrePass = on)
        val fifty = randomize(kind, src, preset, withPrePass = false)
        try {
            val r60 = assertScaled(clean, handler(kind.generation, sixty), 1.06, 1.5, "${kind.id} with the pre-pass")
            val r50 = assertScaled(clean, handler(kind.generation, fifty), 1.0, 1.5, "${kind.id} without it")
            println("PREPASS ${kind.id} $preset: trainers x%.3f with, x%.3f without".format(r60, r50))
            assertTrue(r60 in 1.57..1.63 && r50 in 1.48..1.53, "x$r60 and x$r50")
        } finally {
            for (f in listOf(sixty, fifty)) { f.delete(); Randomizers.logFor(f).delete() }
        }
    }

    @Test
    fun `Emerald Kaizo from the official preset comes out at 1_06 then 1_5, and at 1_5 without the pre-pass`() {
        val src = rom("emerald-u.gba") ?: return println("PrePassLevelsTest skipped: no emerald-u.gba in IRONMON_ROMS")
        check(RomKind.EMERALD_U, src, "RSE Kaizo.rnqs")
        // One trainer, spelled out: the first Team Aqua Grunt at Lv32 is Lv51 at 60% and Lv48 at 50%.
        assertEquals(51 to 48, scaled(32, 1.06, 1.5) to scaled(32, 1.0, 1.5))
    }

    @Test
    fun `Crystal Kaizo from the official preset the same, with the Gold, Silver and Crystal pre-pass`() {
        val src = rom("crystal-u.gbc") ?: return println("PrePassLevelsTest skipped: no crystal-u.gbc in IRONMON_ROMS")
        check(RomKind.CRYSTAL_U, src, "GSC Kaizo.rnqs")
    }

    @Test
    fun `Faster Emerald 1_3_2 is the pre-pass already applied, trainers and wild, so it takes none`() {
        val src = rom("emerald-u.gba") ?: return println("PrePassLevelsTest skipped: no emerald-u.gba in IRONMON_ROMS")
        val opt = PrepOptions.forKind(RomKind.EMERALD_U).first { it.out?.id == RomKind.EMERALD_FASTER.id }
        val faster = File.createTempFile("faster132", ".gba").apply { writeBytes(Patcher.apply(File("src/main/assets/patches/" + opt.asset).readBytes(), src.readBytes(), "Emerald")) }
        val pre = File.createTempFile("prepassed", ".gba")
        try {
            // The pre-pass alone, over the clean dump.
            com.ironmonone.app.engine.ZxEngine.randomize(src, File(presets, ExtraPasses.RSE_PRE_PASS), pre, 7L, Generation.GBA3)
            val a = handler(Generation.GBA3, pre); val b = handler(Generation.GBA3, faster)
            var slots = 0
            for ((t0, t1) in a.trainers.zip(b.trainers)) for ((p0, p1) in t0.pokemon.zip(t1.pokemon)) {
                assertEquals(p0.level, p1.level, "${t0.fullDisplayName}"); slots++
            }
            for ((s0, s1) in a.getEncounters(false).zip(b.getEncounters(false))) for ((e0, e1) in s0.encounters.zip(s1.encounters)) {
                assertEquals(e0.level to e0.maxLevel, e1.level to e1.maxLevel, s0.displayName); slots++
            }
            assertTrue(slots > 3000, "$slots slots")
            assertEquals(null, ExtraPasses.prePassName(RomKind.EMERALD_FASTER), "so no second 6% on this build")
        } finally {
            faster.delete(); pre.delete()
        }
    }
}
