package com.ironmonone.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A file the player adds is kept only if it is a game and not one already in
 * the library. A photo picked by mistake used to become an entry, and the same
 * dump added twice showed twice (audit, 2026-09-27).
 */
class LibraryRefuseTest {

    private fun dsRom(seed: Int): ByteArray {
        val r = ByteArray(0x1000)
        "POKEMON HG".forEachIndexed { i, c -> r[i] = c.code.toByte() }
        "IPKE".forEachIndexed { i, c -> r[0x0C + i] = c.code.toByte() }
        val crc = com.ironmonone.patch.DsHeader.crc16(r, 0, 0x15E)
        r[0x15E] = (crc and 0xFF).toByte(); r[0x15F] = ((crc shr 8) and 0xFF).toByte()
        for (i in 0x200 until r.size) r[i] = (i * seed).toByte()
        return r
    }

    @Test
    fun `a game is kept, the same game again is not, and a photo is not`() {
        val lib = LibraryStore(Files.createTempDirectory("librefuse").toFile())
        val first = lib.import("heartgold.nds", dsRom(7))
        assertNull(lib.refuse("heartgold.nds", first), "a new game is kept")

        val again = lib.import("heartgold copy.nds", dsRom(7))
        val why = assertNotNull(lib.refuse("heartgold copy.nds", again))
        assertTrue("already in your library as ${first.name}" in why, why)

        val photo = lib.import("IMG_2041.jpg", ByteArray(5000) { (it * 31).toByte() })
        assertNotNull(lib.refuse("IMG_2041.jpg", photo))

        assertEquals(listOf(first.name), lib.list().map { it.name })
        assertTrue(!again.file.exists() && !photo.file.exists(), "refused files are deleted")
    }

    @Test
    fun `a different game with the same header is kept`() {
        val lib = LibraryStore(Files.createTempDirectory("librefuse2").toFile())
        assertNull(lib.refuse("a.nds", lib.import("a.nds", dsRom(7))))
        assertNull(lib.refuse("b.nds", lib.import("b.nds", dsRom(11))))
        assertEquals(2, lib.list().size)
    }
}
