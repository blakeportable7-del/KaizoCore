package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every file the app keeps under prep/ is either in the backup or deliberately
 * left out, by name. Five settings files and the DS past-runs logs were missing
 * until 2026-09-27, so a restore dropped them without a word (audit). This reads
 * the source, so a file added later cannot be forgotten the same way.
 */
class BackupCoverageTest {

    /** Left out on purpose, with the reason. */
    private val excluded = mapOf(
        "prep/cloudsync.txt" to "the link to this phone's cloud file; restoring it on another phone would point at a file it cannot open",
        "prep/crc-cache.txt" to "a cache, rebuilt from the files",
        "prep/run-error.txt" to "the last randomizer failure, a diagnostic",
        "prep/stream-token.txt" to "the stream page's key on this phone; OBS on this phone's network holds it, and another phone gets its own",
        "prep/tmp" to "scratch space",
        "prep/patches" to "patch files the player can re-add",
        "prep/prepared" to "prepared ROMs, which are the player's own dumps",
        "prep/library" to "library ROMs; only prep/library/notes/ and session.txt are theirs to keep",
    )

    private val source = File("src/main/kotlin/com/ironmonone/app")

    private fun prepPaths(): Set<String> {
        val out = HashSet<String>()
        val literal = Regex("\"(prep/[A-Za-z0-9_./-]+)\"")
        source.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            literal.findAll(f.readText()).forEach { out += it.groupValues[1] }
        }
        // PrepStore's root IS prep/, so its File(root, "x") files are prep/x.
        val underRoot = Regex("File\\(root, \"([A-Za-z0-9_.-]+)\"\\)")
        underRoot.findAll(File(source, "PrepStore.kt").readText()).forEach { out += "prep/" + it.groupValues[1] }
        out += "prep/pastruns-"   // built with a family name: File(root, "pastruns-$family.tsv")
        return out
    }

    @Test
    fun `every prep file is backed up or excluded by name`() {
        val paths = prepPaths()
        assertTrue(paths.size > 20, "the source scan found only ${paths.size} paths; the scan is broken, not the backup")
        val missing = paths.filter { p ->
            val asFile = p.trimEnd('/')
            excluded.keys.none { asFile == it || asFile.startsWith("$it/") } &&
                !Backup.admits(p) && !Backup.admits("$asFile/x") && !Backup.admits("${asFile}x.tsv")
        }
        assertTrue(missing.isEmpty(), "kept under prep/ but neither backed up nor excluded: $missing")
    }

    @Test
    fun `the files restore used to drop are in the backup`() {
        for (p in listOf("prep/tracker-options.txt", "prep/hidden-power.txt", "prep/pc-heals.txt",
                "prep/summary-checked.txt", "prep/theme.txt", "prep/attempts.txt", "prep/pastruns-HGSS.tsv", "prep/favorites/emerald-u.txt"))
            assertTrue(Backup.admits(p), p)
    }
}
