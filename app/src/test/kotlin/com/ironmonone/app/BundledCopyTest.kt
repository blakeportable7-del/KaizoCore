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
        assertFalse(File(dir, "patches/faster.ips.tmp").exists())
        assertFalse(BundledCopy.whole(dest))
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
