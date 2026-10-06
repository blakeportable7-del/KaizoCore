package com.ironmonone.tracker

import java.io.File
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Why Blake's rc36 Heart & Soul run sat on "Tracker: waiting for the game..." in rc36.1 (2026-10-06), on the real ROMs:
 * the rc36 comfort build (C993EB6E, made by applying v1.0.0-rc36's hns-kaizo-heartsoul-206.bps to the official 2.0.6;
 * kept as .vendor/hns/hns-kaizo-C993EB6E.gba, never committed) is a Heart & Soul ROM by its header, so GameMap.resolve
 * takes the Heart & Soul branch, and its species and move tables are not where this build's layout (HnsLayout, E35A0E40)
 * puts them, so the build check fails and resolve refuses it. The refusal is UnreadableBuild now, which the Play screen
 * catches by name; it used to be a bare IllegalArgumentException that PlayScreen's runCatching dropped, every poll, for
 * good. Without the old ROM the old-build tests return (it is an extra regression file, not a release dump).
 */
class HnsOlderBuildTest {
    private val dir: File? = HnsTrackerTest.romDir()
    private val old: ByteArray? by lazy { dir?.let { File(it, "hns-kaizo-C993EB6E.gba") }?.takeIf { it.isFile }?.readBytes() }
    private val current: ByteArray? by lazy { Dumps.file(dir, "hns-kaizo.gba")?.readBytes() }

    private class Rom(val rom: ByteArray) : MemoryReader {
        override fun read(address: Long, length: Int): ByteArray {
            if (address !in 0x08000000L..0x09FFFFFFL) return ByteArray(0)
            val o = (address - 0x08000000L).toInt()
            if (o >= rom.size) return ByteArray(0)
            return rom.copyOfRange(o, minOf(rom.size, o + length))
        }
    }

    private fun crc(b: ByteArray) = CRC32().also { it.update(b) }.value

    @Test
    fun `rc36's build is refused by name, as an unreadable build of Heart & Soul`() {
        val r = old ?: return println("HnsOlderBuildTest skipped: no hns-kaizo-C993EB6E.gba")
        assertEquals(0xC993EB6EL, crc(r))
        val m = Rom(r)
        assertTrue(HnsMaps.isHns(m), "the header says Heart & Soul")
        assertFalse(HnsMaps.isKaizoBuild(m), "the names are not where this build's layout puts them")
        val e = assertFailsWith<UnreadableBuild> { GameMap.resolve(m) }
        assertEquals(HnsMaps.GAME, e.game)
        assertTrue(e is IllegalArgumentException, "callers that caught the old refusal still catch it")
        assertEquals(null, GameMap.resolveOrNull(m))
    }

    @Test
    fun `rc36 point 1s build is refused by name too`() {
        val r = dir?.let { File(it, "hns-kaizo-E35A0E40.gba") }?.takeIf { it.isFile }?.readBytes()
            ?: return println("HnsOlderBuildTest skipped: no hns-kaizo-E35A0E40.gba")
        assertEquals(0xE35A0E40L, crc(r))
        val m = Rom(r)
        assertTrue(HnsMaps.isHns(m))
        assertFalse(HnsMaps.isKaizoBuild(m), "rc37 moved the tables")
        assertEquals(HnsMaps.GAME, assertFailsWith<UnreadableBuild> { GameMap.resolve(m) }.game)
    }

    @Test
    fun `this build is still read`() {
        val r = current ?: return
        assertEquals(HnsLayout.BUILD_CRC, crc(r))
        assertNotNull(GameMap.resolveOrNull(Rom(r)))
    }
}
