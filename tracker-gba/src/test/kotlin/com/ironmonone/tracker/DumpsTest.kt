package com.ironmonone.tracker

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** rc32 audit P3 #105: a missing dump is a skip without the release gate and a failure under it (Dumps). */
class DumpsTest {
    private val empty: File = Files.createTempDirectory("dumps").toFile().apply { deleteOnExit() }
    private fun folders(vararg env: Pair<String, String>) = DumpFolders(mapOf(*env)::get)

    @Test
    fun `without the release gate a missing folder or dump is a skip`() {
        val none = folders()
        assertNull(none.romsDir()); assertNull(none.dumpsDir()); assertNull(none.rom("emerald-u.gba")); assertNull(none.dump("b2-clean-intro.bin"))
        val there = folders("IRONMON_ROMS" to empty.path, "IRONMON_DUMPS" to empty.path)
        assertNull(there.rom("emerald-u.gba")); assertNull(there.dump("b2-clean-intro.bin")); assertNull(there.file(empty, "red-u.gbc"))
    }

    @Test
    fun `under the release gate a missing folder or dump fails the test`() {
        assertFailsWith<AssertionError> { folders("IRONMON_REQUIRE_DUMPS" to "1").romsDir() }
        assertFailsWith<AssertionError> { folders("IRONMON_REQUIRE_DUMPS" to "1").dumpsDir() }
        assertFailsWith<AssertionError> { folders("IRONMON_REQUIRE_DUMPS" to "1", "IRONMON_ROMS" to File(empty, "nowhere").path).rom("red-u.gbc") }
        val gate = folders("IRONMON_REQUIRE_DUMPS" to "1", "IRONMON_ROMS" to empty.path, "IRONMON_DUMPS" to empty.path)
        assertFailsWith<AssertionError> { gate.rom("emerald-u.gba") }
        assertFailsWith<AssertionError> { gate.dump("b2-clean-intro.bin") }
        assertFailsWith<AssertionError> { gate.file(empty, "red-u.gbc") }
        // A game the release PC has no dump of yet is still a skip, and a dump that is there is found.
        assertNull(gate.rom("gold-u.gbc"))
        val there = File(empty, "red-u.gbc").apply { writeBytes(ByteArray(4)); deleteOnExit() }
        assertEquals(there, gate.rom("red-u.gbc"))
    }

    /** tools/release_checks.py reads DUMPS_REQUIRED from this test's results: IRONMON_REQUIRE_DUMPS reached this test JVM. */
    @Test
    fun `under the release gate both folders are there`() {
        if (!Dumps.required) return
        val roms = assertNotNull(Dumps.romsDir())
        val dumps = assertNotNull(Dumps.dumpsDir())
        println("DUMPS_REQUIRED roms=$roms dumps=$dumps")
    }
}
