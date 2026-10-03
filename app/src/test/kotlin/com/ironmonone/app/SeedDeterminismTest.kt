package com.ironmonone.app

import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.core.Generation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Run codes (RunCode) promise that the same dump, settings and seed give the same ROM on
 * another phone. That holds only if the randomizer is deterministic for a seed, so it is
 * checked here on real dumps: the same seed twice must give byte-identical ROMs, and another
 * seed a different one. Skipped without IRONMON_ROMS.
 */
class SeedDeterminismTest {
    private fun rom(name: String): File? = Dumps.rom(name)

    private fun crcOf(src: File, preset: String, seed: Long, gen: Generation): Long {
        val out = File.createTempFile("seed", "." + src.extension)
        try {
            ZxEngine.randomize(src, File("src/main/assets/presets/$preset"), out, seed, gen)
            return RunCode.crc32(out)
        } finally { out.delete() }
    }

    @Test fun `the same seed gives the same ROM, another seed another`() {
        for ((name, preset) in listOf("emerald-u.gba" to "RSE Kaizo.rnqs", "firered-u-v10.gba" to "FRLG Kaizo.rnqs")) {
            val src = rom(name) ?: continue
            val a = crcOf(src, preset, 0x5261db990e333467L, Generation.GBA3)
            val b = crcOf(src, preset, 0x5261db990e333467L, Generation.GBA3)
            val c = crcOf(src, preset, 0x5261db990e333468L, Generation.GBA3)
            assertEquals(a, b, "$name: one seed, two different ROMs")
            assertNotEquals(a, c, "$name: two seeds, one ROM")
        }
    }
}
