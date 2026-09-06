package com.ironmonone.editor

import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Both engines' Settings IO is declared with CONCRETE stream types.
 *
 * The editor reaches these by reflection, so an incorrect signature is not a
 * compile error - it is a NoSuchMethodException at the moment the user presses
 * SAVE AS. That shipped: the round-trip guard asked for `read(InputStream)`
 * and broke every save it existed to protect.
 *
 * This test needs no device and no ROM, which is precisely why it should have
 * existed before the feature shipped.
 */
class SettingsIoSignatureTest {

    private val classes = listOf(
        "com.dabomstew.pkrandom.Settings",
        "com.dabomstew.pkrandomzx.Settings",
    )

    @Test
    fun `read and write resolve with the concrete stream types`() {
        for (name in classes) {
            val cls = runCatching { Class.forName(name) }.getOrNull()
                ?: fail("$name not on the test classpath")
            runCatching { cls.getMethod("read", FileInputStream::class.java) }
                .onFailure { fail("$name.read(FileInputStream) missing: $it") }
            runCatching { cls.getMethod("write", FileOutputStream::class.java) }
                .onFailure { fail("$name.write(FileOutputStream) missing: $it") }
        }
    }

    @Test
    fun `the supertype lookups do NOT resolve`() {
        // Pins the reason: if a future engine widens these, this test fails
        // and whoever widens them can simplify the call sites deliberately
        // instead of discovering it through a broken save button.
        for (name in classes) {
            val cls = Class.forName(name)
            assertTrue(
                runCatching { cls.getMethod("read", java.io.InputStream::class.java) }
                    .isFailure,
                "$name.read(InputStream) unexpectedly exists",
            )
        }
    }
}
