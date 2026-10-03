package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * rc33 audit P1: a bundled patch copied out of the APK and cut short (a full phone, the app killed) stayed under its
 * own name and was used for ever, failing as if the player's dump were wrong. A copy is now whole or not there.
 */
class BundledCopyTest {
    private val dir = Files.createTempDirectory("bundled").toFile()

    @Test
    fun `a copy that fails part way leaves nothing to use`() {
        val dest = File(dir, "patches/faster.ips")
        val got = BundledCopy.copyWhole(dest) { out -> out.write(ByteArray(10)); throw java.io.IOException("No space left on device") }
        assertNull(got)
        assertFalse(dest.exists())
        assertTrue(dest.parentFile.listFiles()!!.none { it.name.endsWith(".tmp") }, "no part copy left behind")
        assertFalse(BundledCopy.whole(dest))
    }

    /**
     * rc32 audit P2 #74: two first-time copies of one patch (two patch jobs at once) shared one .tmp, so one emptied the
     * other's half-written copy and the patch read back half written. Here the second copy starts while the first is
     * half way through, and the first, which ends last, is what stays: whole, and marked at its own length.
     */
    @Test
    fun `two copies of one patch at once do not write into each other`() {
        val dest = File(dir, "patches/natdex.bps")
        val inner = arrayOfNulls<File>(1)
        val got = BundledCopy.copyWhole(dest) { outer ->
            outer.write(ByteArray(100) { 1 })
            inner[0] = BundledCopy.copyWhole(dest) { it.write(ByteArray(300) { 2 }) }
            outer.write(ByteArray(100) { 1 })
        }
        assertEquals(dest, inner[0], "the second copy finished whole")
        assertEquals(dest, got, "and so did the first")
        assertEquals(200L, dest.length())
        assertTrue(dest.readBytes().all { it == 1.toByte() }, "the copy that ended last is the one kept, unmixed")
        assertTrue(BundledCopy.whole(dest))
        assertTrue(dest.parentFile.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `a whole copy is marked, and a copy of another size is not trusted`() {
        val dest = File(dir, "patches/journey.ups")
        assertEquals(dest, BundledCopy.copyWhole(dest) { it.write(ByteArray(64) { 7 }) })
        assertTrue(BundledCopy.whole(dest))
        dest.writeBytes(ByteArray(10))                     // cut short behind the mark's back
        assertFalse(BundledCopy.whole(dest))
        val legacy = File(dir, "patches/old.ips").apply { parentFile.mkdirs(); writeBytes(ByteArray(5)) }
        assertFalse(BundledCopy.whole(legacy), "a copy from before the mark is copied again once")
    }

    @Test
    fun `a BPS is trusted by its own checksum, and one cut short is not`() {
        val bps = File(dir, "patches/natdex.bps").apply { parentFile.mkdirs(); writeBytes("BPS1".toByteArray() + ByteArray(20)) }
        assertFalse(BundledCopy.bpsIntact(bps), "no valid checksum")
        assertFalse(BundledCopy.whole(bps))
    }

    @Test
    fun `both bundled paths go through the whole copy`() {
        val store = File("src/main/kotlin/com/ironmonone/app/PrepStore.kt").readText().replace("\r\n", "\n")
        val needle = "BundledCopy.extract(context, \"patches/" + "$" + "name\", dest)"
        assertEquals(2, store.split(needle).size - 1)
        assertTrue("if (dest.isFile && (BundledCopy.whole(dest) || BundledCopy.bpsIntact(dest))) return dest" in store)
    }
}
