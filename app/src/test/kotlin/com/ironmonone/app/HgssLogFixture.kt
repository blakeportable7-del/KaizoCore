package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Makes the HeartGold randomizer log the DS log viewer's tests read, by running
 * the real randomizer on the HeartGold dump with the "HGSS Kaizo" preset. Off
 * unless IRONMON_MAKE_FIXTURES=1 and IRONMON_ROMS holds heartgold-u.nds; it
 * writes .vendor/logs/heartgold.nds.log (gitignored, like the other logs) once
 * and leaves an existing one alone. Nothing else depends on it running.
 */
class HgssLogFixture {
    @Test
    fun `make the HeartGold log fixture`() {
        if (System.getenv("IRONMON_MAKE_FIXTURES") != "1") return
        val rom = System.getenv("IRONMON_ROMS")?.let { File(it, "heartgold-u.nds") }?.takeIf { it.isFile } ?: return
        val out = File("../.vendor/logs/heartgold.nds.log")
        if (out.isFile) return
        val kind = RomKind.byId("heartgold-u") ?: error("no RomKind heartgold-u")
        val settings = File("src/main/assets/presets/HGSS Kaizo.rnqs").takeIf { it.isFile } ?: error("no HGSS Kaizo preset")
        val dir = kotlin.io.path.createTempDirectory("hgss").toFile()
        try {
            val dest = File(dir, "heartgold.nds")
            Randomizers.randomize(kind, rom, settings, dest, seed = 20260910L)
            val log = Randomizers.logFor(dest)
            assertTrue(log.isFile && log.length() > 100_000, "the randomizer wrote no log")
            out.parentFile.mkdirs()
            log.copyTo(out)
        } finally {
            dir.deleteRecursively()
        }
    }
}
