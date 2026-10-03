package com.ironmonone.app

import com.ironmonone.app.engine.NatDexEngine
import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.core.Generation
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * rc35 follow-up N #5: a file the randomizer cannot open was "not a GBA Pokemon ROM this engine can open", ROM being a
 * word the app's copy retired, and a Game Boy game was called a GBA one. The player reads it as it is
 * (RunJob.randomizeFailure).
 */
class EngineWordsTest {
    private val presets = File("src/main/assets/presets")

    @Test
    fun `a file the randomizer cannot open is a game of its own console`() {
        val dir = Files.createTempDirectory("notagame").toFile()
        for ((file, preset, gen, console) in listOf(
            Quad("junk.gb", "RBY Kaizo.rnqs", Generation.GB1, "Game Boy"),
            Quad("junk.gbc", "GSC Kaizo.rnqs", Generation.GBC2, "Game Boy"),
            Quad("junk.gba", "RSE Kaizo.rnqs", Generation.GBA3, "GBA"),
            Quad("junk.nds", "DPPt Kaizo.rnqs", Generation.NDS4, "DS"),
        )) {
            val f = File(dir, file).apply { writeBytes(ByteArray(4096) { (it * 7).toByte() }) }
            val e = assertFailsWith<NatDexEngine.EngineException> { ZxEngine.randomize(f, File(presets, preset), File(dir, "out-$file"), 1L, gen) }
            val said = "\"$file\" is not a $console Pokémon game the randomizer can open."
            assertEquals(said, e.message)
            assertEquals(said, RunJob.randomizeFailure(e), "shown as it is")
        }
        val f = File(dir, "natdex.gba").apply { writeBytes(ByteArray(4096)) }
        val e = assertFailsWith<NatDexEngine.EngineException> { NatDexEngine.randomize(f, File(presets, "RSE NatDex v1.2 Kaizo.rnqs"), File(dir, "out.gba"), 1L) }
        assertEquals("\"natdex.gba\" is not a GBA Pokémon game the Nat. Dex randomizer can open.", e.message)
        for (src in listOf("engine/ZxEngine.kt", "engine/NatDexEngine.kt")) {
            val text = File("src/main/kotlin/com/ironmonone/app/$src").readText()
            val literals = text.lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
                .flatMap { line -> Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(line).map { it.groupValues[1] } }
            for (s in literals) assertFalse(Regex("\\bROM\\b").containsMatchIn(s), "$src: ROM in prose: $s")
        }
    }

    private data class Quad(val a: String, val b: String, val c: Generation, val d: String)
}
