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
        "prep/next" to "the next run, randomized ahead (NextRun): a ROM of the player's game, made again after a restore",
        "prep/playing.txt" to "which game this phone's Play screen had open when the app died (CrashResume); on another phone it would resume a game never left there",
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
        out += "prep/runhistory-"  // built with a game id: File(root, "runhistory-${kind.id}.tsv")
        // A run's StatMarks keeps prep/marks.txt, and its other notes beside it: File(file.parentFile, "x").
        val besideMarks = Regex("File\\(file\\.parentFile, \"([A-Za-z0-9_.-]+)\"\\)")
        val statMarks = File(source, "StatMarks.kt").readText()
        val beside = besideMarks.findAll(statMarks).map { "prep/" + it.groupValues[1] }.toList()
        assertTrue(beside.size >= 8, "the StatMarks scan found only $beside")
        out += beside
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
                "prep/summary-checked.txt", "prep/theme.txt", "prep/attempts.txt", "prep/pastruns-HGSS.tsv", "prep/favorites/emerald-u.txt",
                "prep/death-quotes.txt"))
            assertTrue(Backup.admits(p), p)
    }

    @Test
    fun `Play as your Pokemon's choices and imported sprite are in the backup, and the scan is watching them`() {
        // The scan must SEE both paths in the source, or it is not guarding them (a rename would slip past it).
        val paths = prepPaths()
        assertTrue("prep/sprite-is-me.txt" in paths, "the scan no longer finds the settings file: $paths")
        assertTrue("prep/spriteisme" in paths, "the scan no longer finds the art folder")
        for (p in listOf("prep/sprite-is-me.txt", "prep/spriteisme/picture.png", "prep/spriteisme/sheet/idle.png",
                "prep/spriteisme/sheet/walk.png", "prep/spriteisme/sheet/sleep.png", "prep/spriteisme/sheet/faint.png"))
            assertTrue(Backup.admits(p), p)
        // Its files are the player's, not this phone's: none of them is on the excluded list.
        assertTrue(excluded.keys.none { it.startsWith("prep/sprite") })
    }

    /**
     * "Save this attempt" said "Saved" into a folder nothing backed up (2026-09-30, UX audit P0-5). The attempt is
     * made through PrepStore, so the path the app writes and the path the backup takes cannot drift apart.
     */
    @Test
    fun `a saved attempt is in the backup`() {
        val filesDir = java.nio.file.Files.createTempDirectory("attempts").toFile()
        try {
            val store = PrepStore(filesDir)
            val kind = com.ironmonone.core.RomKind.EMERALD_U
            store.currentRunFor(kind).apply { parentFile.mkdirs(); writeBytes(ByteArray(64) { 1 }) }
            File(filesDir, "prep/notes.txt").writeText("25:fast")
            assertTrue(store.saveAttempt(kind, 14, "00000000000000ab", byteArrayOf(1, 2, 3)))
            val got = Backup.collect(filesDir).filter { it.startsWith("attempts/") }
            for (name in listOf("run.gba", "state.bin", "notes.txt", "attempt.txt"))
                assertTrue(got.any { it.endsWith("/$name") }, "$name of the saved attempt is not in the backup: $got")
        } finally { filesDir.deleteRecursively() }
    }
}
