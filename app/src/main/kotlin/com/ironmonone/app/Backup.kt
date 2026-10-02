package com.ironmonone.app

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup is one zip of everything the player made and nothing they can
 * re-add: save states and their screenshots, battery saves, the auto-save,
 * locks, run notes, favourites, the current and previous run, the attempts
 * saved from the game-over screen, attempt counts, presets, key bindings, layouts, skins, cheats, core options, tracker
 * colours and the image behind the tracker.
 *
 * Deliberately NOT in it: library ROMs and patches, prepared bases, the
 * Nat. Dex patch files. Those are the player's own dumps, they are big,
 * and re-adding them takes a tap. A backup with them would be a ROM
 * bundle, and it would also stop fitting in an email.
 *
 * Restore writes only paths the allowlist admits, relative to filesDir,
 * and refuses anything that tries to escape it. Existing files are
 * overwritten: a restore is "put my saves back", not a merge.
 */
object Backup {

    /** Relative-path prefixes and exact files, under filesDir. */
    private val PREFIXES = listOf(
        "saves/",
        "prep/attempts/", "prep/games/", "prep/cheats/", "prep/layouts/", "prep/coreopts/",
        "prep/library/notes/", "prep/runs/", "prep/settings/",
        // Per-game favourites and the DS past-runs logs (prep/pastruns-<family>.tsv).
        "prep/favorites/", "prep/pastruns-",
        // Every game's run history (prep/runhistory-<game>.tsv): personal bests and the death card.
        "prep/runhistory-",
        // The Nuzlocke ledgers (2026-09-29): one file per run, prep/nuzlocke/<run>.txt. The areas, the graveyard and the
        // corrections the player made by hand exist nowhere else, and a restore that dropped them would end the run.
        "prep/nuzlocke/",
        // Play as your Pokemon: the picture or sheets the player imported for their own sprite.
        "prep/spriteisme/",
        // "Save this attempt" (PrepStore.saveAttempt): the run's game, its log, a save state and the notes, one folder
        // per attempt. It said "Saved" and nothing backed it up, so an uninstall or a new phone lost every one
        // (2026-09-30, UX audit P0-5). Each holds a copy of the randomized game, as prep/runs/ does for the run in play.
        "attempts/",
    )
    private val FILES = setOf(
        "prep/marks.txt", "prep/notes.txt", "prep/routes.txt", "prep/moves.txt", "prep/abilities.txt", "prep/integrity.txt",
        "prep/favorites.txt", "prep/lastrun.txt", "prep/lastseed.txt", "prep/keys.txt", "prep/skin.txt",
        "prep/padforce.txt", "prep/playspeed.txt", "prep/playmute.txt", "prep/library/session.txt",
        // Left out until 2026-09-27, so a restore silently lost them (audit): the
        // tracker's settings, Hidden Power choices, PC heal counts, the summary
        // checks, the theme, the attempt counter and the tourney table.
        "prep/tracker-options.txt", "prep/hidden-power.txt", "prep/pc-heals.txt", "prep/summary-checked.txt",
        "prep/theme.txt", "prep/attempts.txt", "prep/tourney.tsv",
        // The switch for making the next run ahead (NextRunJob), set by the player.
        "prep/nextrun-off.txt",
        // The DS tracker's run-wide values (StatMarks: Hidden Power type, Pokecenter count).
        "prep/ds-tracked.txt",
        // The run's other notes beside marks.txt (StatMarks), missed until 2026-09-29 because the
        // coverage check did not read StatMarks' file names: encounter counts, the Safari record,
        // the DS encounters. And the time played per run (RunClock).
        "prep/encounters.txt", "prep/safari.txt", "prep/ds-encounters.txt", "prep/run-clock.txt",
        // The player's choices over the passes the rules add (ExtraPasses).
        "prep/extra-passes.txt",
        // The player's own game over lines and their switches (DeathQuotes), typed in by hand and nowhere else.
        "prep/death-quotes.txt",
        // Tracker themes (2026-09-29): the colours they saved under a name, and the image behind the tracker
        // with its dim and fit. The image makes the backup bigger by its own size: a JPEG of at most 1280 px
        // on its long side, usually a few hundred KB.
        "prep/theme-presets.txt", "prep/tracker-bg.txt", "prep/tracker-bg.jpg",
        // Play as your Pokemon (2026-09-29): the switch and the player's choices, beside the art under prep/spriteisme/.
        "prep/sprite-is-me.txt",
    )

    fun admits(rel: String): Boolean {
        val r = rel.replace('\\', '/')
        if (r.startsWith("/") || r.split('/').any { it == ".." || it.isEmpty() && r.endsWith("/") }) return false
        if (r.contains("/../") || r.startsWith("../")) return false
        if (r.endsWith(".tmp")) return false
        return r in FILES || PREFIXES.any { r.startsWith(it) }
    }

    /** Every file under filesDir the backup takes, as relative paths. */
    fun collect(filesDir: File): List<String> {
        val skip = liveRunLogs(filesDir)
        return filesDir.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(filesDir).path.replace('\\', '/') }
            .filter { admits(it) && it !in skip }.sorted().toList()
    }

    /**
     * The run in play's randomizer log, while that run is live: left out of every backup and cloud copy (2026-09-30,
     * Blake, on the IronMON rules check). The log is the seed's answers, which the app opens only from the game-over
     * screen, and a backup is a zip anyone can open. It goes in once the run has ended, which its record in the game's
     * run history says (the seed, and the game file's own time as the run's start); a randomized Nuzlocke's stays out
     * until a new run replaces it. The game file and a DS run's species file stay in: the tracker needs them.
     */
    internal fun liveRunLogs(filesDir: File): Set<String> = runCatching {
        val prep = File(filesDir, "prep")
        val runs = File(prep, "runs")
        val logs = runs.listFiles { f -> f.isFile && f.name.startsWith("current.") && f.name.endsWith(".log") }.orEmpty()
        if (logs.isEmpty()) return emptySet()
        val lines = File(prep, "lastrun.txt").takeIf { it.isFile }?.readLines().orEmpty()
        val romId = lines.getOrNull(0)?.trim().orEmpty()
        val seed = File(prep, "lastseed.txt").takeIf { it.isFile }?.readText()?.trim().orEmpty()
        val games = runs.listFiles { f -> f.isFile && f.name.startsWith("current.") && !f.name.endsWith(".log") && !f.name.endsWith(".tsv") }.orEmpty()
        val nuzlocke = lines.drop(2).any { it.trim() == "nuzlocke=true" }
        val ended = !nuzlocke && romId.isNotBlank() && seed.isNotBlank() &&
            RunHistory(File(prep, "runhistory-$romId.tsv")).all().any { r -> r.seed == seed && games.any { it.lastModified() == r.started } }
        if (ended) emptySet() else logs.map { "prep/runs/${it.name}" }.toSet()
    }.getOrDefault(emptySet())

    /** Write the backup. Returns the number of files written. */
    fun write(filesDir: File, out: OutputStream): Int {
        var n = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("KAIZOCORE-BACKUP.txt"))
            zip.write("KaizoCore backup v1\n".toByteArray()); zip.closeEntry()
            for (rel in collect(filesDir)) {
                val f = File(filesDir, rel)
                zip.putNextEntry(ZipEntry(rel).apply { time = f.lastModified() })
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry(); n++
            }
        }
        return n
    }

    /** Where a restore lands before it is known to be a whole KaizoCore backup. Not a backed-up path. */
    const val STAGING = ".restore-staging"

    /**
     * Restore from a backup. Returns files written; -1 when it is not a KaizoCore backup, and then nothing changed.
     *
     * The whole zip is read into [STAGING] first and moved into place only when it is a KaizoCore backup read to its
     * end (rc33 audit P1): entries used to be written as they were read, so a damaged or foreign zip left a phone half
     * restored, and the message said nothing had changed. A zip that breaks part way throws, with nothing moved.
     */
    fun read(filesDir: File, input: InputStream): Int {
        val stage = File(filesDir, STAGING).apply { deleteRecursively(); mkdirs() }
        try {
            var marker = false
            val staged = ArrayList<Pair<File, File>>()
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name == "KAIZOCORE-BACKUP.txt") { marker = true; zip.closeEntry(); continue }
                    if (e.isDirectory || !admits(e.name)) { zip.closeEntry(); continue }
                    val target = File(filesDir, e.name)
                    if (!target.canonicalPath.startsWith(filesDir.canonicalPath)) { zip.closeEntry(); continue }
                    val held = File(stage, e.name).apply { parentFile?.mkdirs() }
                    held.outputStream().use { zip.copyTo(it) }
                    if (e.time > 0) held.setLastModified(e.time)
                    staged += held to target
                    zip.closeEntry()
                }
            }
            if (!marker) return -1
            for ((held, target) in staged) {
                target.parentFile?.mkdirs()
                val time = held.lastModified()
                StateSlots.replace(held, target)
                if (time > 0) target.setLastModified(time)
            }
            afterRestore(filesDir)
            return staged.size
        } finally {
            stage.deleteRecursively()
        }
    }

    /**
     * A restore puts the run back to the backup's moment (2026-09-30, IronMON rules check R1). It goes into the run's
     * own log as a restore, as a Time Machine one does, and the auto-saves' left marks go, so the next open does not
     * drop the player into the backup's moment without a word: the auto-save is offered under File > States instead.
     */
    internal fun afterRestore(filesDir: File, at: Long = System.currentTimeMillis()) {
        runCatching { File(filesDir, "saves").walkTopDown().filter { it.isFile && it.name.endsWith(".left") }.toList().forEach { it.delete() } }
        if (File(filesDir, "prep/lastseed.txt").isFile)
            RunEvents(File(filesDir, "prep/integrity.txt")).add(RunEvents.Kind.RESTORE, "backup", "restored from a backup", at)
    }

    fun suggestedName(): String =
        "KaizoCore-backup-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".zip"
}

/**
 * ROMs and patches inside a zip. The picker hands the app a .zip; this
 * pulls out every entry with a known extension so the library can import
 * each one as if it had been picked on its own. Anything else in the
 * archive (readmes, art) is ignored. Entries are capped at 256 MB, which
 * is bigger than any DS cartridge, so a zip bomb stops there.
 */
object ZipImport {
    private val ROM_EXT = setOf("gba", "gbc", "gb", "nds", "bps", "ips", "ups", "xdelta", "vcdiff")
    /** Bigger than any DS cartridge (512 MB), so a zip bomb still stops. */
    private const val CAP = 600L * 1024 * 1024

    fun isZip(name: String, bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            (name.lowercase().endsWith(".zip") || (bytes[2].toInt() == 3 && bytes[3].toInt() == 4))

    /**
     * Stream each ROM or patch inside [input] to its own file under [dir],
     * never holding an entry in memory: a 512 MB DS dump inside a zip is the
     * normal case, not the edge. Returns (entry name, file) pairs.
     */
    fun extractToFiles(input: java.io.InputStream, dir: java.io.File, onProgress: ((Long) -> Unit)? = null): List<Pair<String, java.io.File>> {
        val out = ArrayList<Pair<String, java.io.File>>()
        dir.mkdirs()
        // Progress is measured on the COMPRESSED side, whose total the caller knows.
        var consumed = 0L
        val counted = object : java.io.FilterInputStream(input) {
            override fun read(): Int = super.read().also { if (it >= 0) { consumed++; onProgress?.invoke(consumed) } }
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) { consumed += it; onProgress?.invoke(consumed) } }
        }
        ZipInputStream(counted.buffered(1 shl 20)).use { zip ->
            var i = 0
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/').substringAfterLast('\\')
                val ext = name.substringAfterLast('.', "").lowercase()
                if (e.isDirectory || ext !in ROM_EXT || name.startsWith("._")) { zip.closeEntry(); continue }
                val f = java.io.File(dir, "zip${i++}-$name")
                var total = 0L; var over = false
                f.outputStream().buffered(1 shl 20).use { o ->
                    val chunk = ByteArray(1 shl 16)
                    while (true) {
                        val r = zip.read(chunk); if (r < 0) break
                        total += r; if (total > CAP) { over = true; break }
                        o.write(chunk, 0, r)
                    }
                }
                if (over || total == 0L) f.delete() else out += name to f
                zip.closeEntry()
            }
        }
        return out
    }

    /** (file name, bytes) for each ROM or patch inside. Small archives only; see [extractToFiles]. */
    fun extract(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val out = ArrayList<Pair<String, ByteArray>>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/').substringAfterLast('\\')
                val ext = name.substringAfterLast('.', "").lowercase()
                if (e.isDirectory || ext !in ROM_EXT || name.startsWith("._")) { zip.closeEntry(); continue }
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val r = zip.read(chunk); if (r < 0) break
                    total += r; if (total > CAP) break
                    buf.write(chunk, 0, r)
                }
                if (total <= CAP && total > 0) out += name to buf.toByteArray()
                zip.closeEntry()
            }
        }
        return out
    }
}
