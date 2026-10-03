package com.ironmonone.app

import com.ironmonone.app.engine.MaxDexEngine
import com.ironmonone.app.engine.NatDexEngine
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.Engine
import com.ironmonone.core.RomKind
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * MaxDex 1.0 is randomized by its own engine (engine-maxdex), chosen by the game's kind, and the engine never
 * touches a ROM that is not the pinned build: Trip's detection takes any FireRed 1.1 and would write MaxDex's
 * addresses into it. The run itself is checked on the patched test ROM when it is to hand (IRONMON_ROMS holding
 * firered-maxdex.gba, else Blake's vendor folder).
 */
class MaxDexEngineTest {

    /** "FRLG MaxDex Kaizo.rnqs" from Tripc423/Maxdex, the 112 bytes engine-maxdex's own test pins. */
    private val preset = Base64.getDecoder().decode(
        "AAADhgAAAGhXUklrRWpMOEFQOEFBZ0dSQUFLZUJoc0VDUUVBRkFBeUNRQXVFZ0FBQy84QUJSQXc1QVRrQW9aSUNUSUdCQUl5QUFV" +
            "WUVFWnBjbVVnVW1Wa0lDaFZLU0F4TGpINTlIbmVJNnp2VGc9PQ==")

    private fun rom(name: String): File? =
        File(System.getenv("IRONMON_ROMS") ?: "C:/Users/bepor/IronMonOne/.vendor/roms", name).takeIf { it.isFile }

    @Test
    fun `the engine follows the kind`() {
        assertEquals(Engine.MAXDEX, RomKind.FIRERED_MAXDEX_10.engine)
        assertEquals(MaxDexEngine.ID, Randomizers.engineId(RomKind.FIRERED_MAXDEX_10))
        assertEquals("maxdex-1.0", MaxDexEngine.ID)
        assertEquals(MaxDexEngine.DISPLAY_NAME, Randomizers.engineName(RomKind.FIRERED_MAXDEX_10))
        assertEquals(NatDexEngine.DISPLAY_NAME, Randomizers.engineName(RomKind.FIRERED_NATDEX_121))
        assertTrue("4.6.0-END112" in MaxDexEngine.DISPLAY_NAME)
    }

    @Test
    fun `a file that is not MaxDex 1_0 is refused before the engine reads it`() {
        val junk = File.createTempFile("not-maxdex", ".gba").apply { writeBytes(ByteArray(4096) { (it * 31).toByte() }) }
        val settings = File.createTempFile("FRLG MaxDex Kaizo", ".rnqs").apply { writeBytes(preset) }
        val dest = File.createTempFile("out", ".gba").apply { delete() }
        try {
            assertNotNull(MaxDexEngine.refusal(junk))
            val e = assertFailsWith<NatDexEngine.EngineException> { MaxDexEngine.randomize(junk, settings, dest, 1L) }
            assertTrue("is not Pok\u00e9mon FireRed + MaxDex 1.0" in (e.message ?: ""), e.message)
            assertTrue(!dest.exists(), "nothing was written")
            // A real FireRed 1.1, which Trip's own detection would take, is refused just the same.
            rom("firered-u-v11.gba")?.let { assertNotNull(MaxDexEngine.refusal(it)) }
        } finally { junk.delete(); settings.delete(); dest.delete() }
    }

    @Test
    fun `NEW RUN on the MaxDex build randomizes it with its own engine`() {
        val base = rom("firered-maxdex.gba") ?: return println("MaxDexEngineTest skipped: firered-maxdex.gba not to hand")
        assertEquals(null, MaxDexEngine.refusal(base))
        val settings = File.createTempFile("FRLG MaxDex Kaizo", ".rnqs").apply { writeBytes(preset) }
        val dest = File.createTempFile("maxdex-run", ".gba")
        try {
            val out = Randomizers.randomize(RomKind.FIRERED_MAXDEX_10, base, settings, dest, 12345L)
            val crc = java.util.zip.CRC32().apply { update(dest.readBytes()) }.value
            // engine-maxdex/PINNED.txt's golden run for this seed.
            assertEquals(0xFC3D321BL, crc)
            assertTrue(out.logText.startsWith("Randomizer Version: 4.6.0-END112"), out.logText.take(60))
            assertTrue(Randomizers.logFor(dest).isFile, "the log is kept beside the run")
        } finally { settings.delete(); dest.delete(); Randomizers.logFor(dest).delete() }
    }
}
