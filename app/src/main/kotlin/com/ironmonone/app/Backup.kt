package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup is one zip of everything the player made and nothing they can
 * re-add: save states and their screenshots, battery saves, the auto-save,
 * locks, run notes, favourites, the current run with its randomized game, the attempts
 * saved from the game-over screen (each with its game), attempt counts, presets, key bindings, layouts, skins, cheats,
 * core options, tracker colours and the pictures chosen for the tracker and for Play as your Pokemon.
 *
 * Deliberately NOT in it: library ROMs and patches, prepared bases, the
 * Nat. Dex patch files, and the game of a run a new one replaced (nothing reads it). Those are the player's own dumps,
 * they are big, and re-adding them takes a tap. A backup with them would be a ROM
 * bundle, and it would also stop fitting in an email.
 *
 * Restore writes only paths the allowlist admits, relative to filesDir,
 * and refuses anything that tries to escape it. Existing files are
 * overwritten: a restore is "put my saves back", not a merge. The one exception is a backup of another run: the
 * phone's own run's notes, log and record of loads go with that run, since they are not the backup's (rc32 audit P2 #12).
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
        // GachaMon (2026-10-03): the collection, this run's captures, the GachaDex and its options (GachaMon.DIR). The
        // collection is the player's for good and exists nowhere else.
        "prep/gachamon/",
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
        // How far the run in play has got, for its record if a new run replaces it (RunProgress, rc32 audit P3 #58).
        "prep/run-progress.txt",
        // The player's choices over the passes the rules add (ExtraPasses).
        "prep/extra-passes.txt",
        // Heart & Soul's pool, Vanilla or Nat. Dex (HnsPool), the player's choice.
        "prep/hns-pool.txt",
        // The player's own game over lines and their switches (DeathQuotes), typed in by hand and nowhere else.
        "prep/death-quotes.txt",
        // Tracker themes (2026-09-29): the colours they saved under a name, and the image behind the tracker
        // with its dim and fit. The image makes the backup bigger by its own size: a JPEG of at most 1280 px
        // on its long side, usually a few hundred KB.
        "prep/theme-presets.txt", "prep/tracker-bg.txt", "prep/tracker-bg.jpg",
        // Play as your Pokemon (2026-09-29): the switch and the player's choices, beside the art under prep/spriteisme/.
        "prep/sprite-is-me.txt",
    )

    /** The run's own files under prep/ (PrepStore.clearRunNotes' list): a new run, or a backup of another one, ends them. */
    private val RUN_NOTES = listOf(
        "marks.txt", "notes.txt", "routes.txt", "moves.txt", "abilities.txt", "encounters.txt", "ds-encounters.txt",
        "safari.txt", "ds-tracked.txt", "integrity.txt", RunProgress.FILE,
    )

    /** The extensions a run's game can have. */
    private val GAME_EXTENSIONS = (RomKind.allV1 + RomKind.allNatDex + RomKind.allPatched).map { it.fileExtension }.toSet()

    fun admits(rel: String): Boolean {
        val r = rel.replace('\\', '/')
        if (r.startsWith("/") || r.split('/').any { it == ".." || it.isEmpty() && r.endsWith("/") }) return false
        if (r.contains("/../") || r.startsWith("../")) return false
        if (r.endsWith(".tmp")) return false
        // The game of a run a new one replaced: nothing reads it, and it was a whole game, up to 512 MB for a DS one, in
        // every backup and cloud copy (rc32 audit P2 #2). Its log stays.
        if (r.startsWith("prep/runs/previous.") && r.removePrefix("prep/runs/previous.") in GAME_EXTENSIONS) return false
        return r in FILES || PREFIXES.any { r.startsWith(it) }
    }

    /** Every file under filesDir the backup takes, as relative paths. */
    fun collect(filesDir: File): List<String> {
        // The notes and settings still on their way to disk go first, so the zip holds the newest (DiskWriter).
        DiskWriter.drain()
        val skip = liveRunLogs(filesDir)
        val unfinished = unfinishedAttempts(filesDir)
        return filesDir.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(filesDir).path.replace('\\', '/') }
            .filter { admits(it) && it !in skip && unfinished.none { dir -> it.startsWith(dir) } }.sorted().toList()
    }

    /**
     * "attempts/<folder>/" for every saved attempt without its last file (PrepStore.ATTEMPT_DONE): a copy being made
     * now, or one a kill cut short, which a backup must not carry (rc32 audit P2 #66).
     */
    private fun unfinishedAttempts(filesDir: File): List<String> =
        File(filesDir, "attempts").listFiles().orEmpty().filter { it.isDirectory && !File(it, ATTEMPT_DONE).isFile }
            .map { "attempts/" + it.name + "/" }

    /**
     * The run in play's randomizer log, while that run is live: left out of every backup and cloud copy (2026-09-30,
     * Blake, on the IronMON rules check). The log is the seed's answers, which the app opens only from the game-over
     * screen, and a backup is a zip anyone can open. It goes in once the run has ended, which its record in the game's
     * run history says (the seed, and the game file's own time as the run's start); a randomized Nuzlocke's stays out
     * until a new run replaces it. The game file and a DS run's species file stay in: the tracker needs them.
     *
     * A record that Retry reopened is not an end (rc32 audit P3 #19): after Retry the battle, the loss stays on file
     * with the same seed and start while the run goes on, and the next backup carried the live run's answers.
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
        val events by lazy { RunEvents(File(prep, "integrity.txt")).entries() }
        val ended = !nuzlocke && romId.isNotBlank() && seed.isNotBlank() &&
            RunHistory(File(prep, "runhistory-$romId.tsv")).all().any { r ->
                r.seed == seed && games.any { it.lastModified() == r.started } && !retriedAfter(r, events)
            }
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

    /** [read]'s answer when the phone ran short of space: nothing was changed. */
    const val NO_ROOM = -2

    /**
     * Bigger than any file a backup holds (a 512 MB DS game in prep/runs/ or attempts/), so a crafted or damaged entry
     * stops here instead of filling the phone (rc32 audit P3 #20). ZipImport's cap, for the same reason.
     */
    const val ENTRY_CAP = 600L * 1024 * 1024

    /** A restore stops before the phone's free space falls under this, so its saves and auto-saves still fit. */
    const val FREE_FLOOR = 32L * 1024 * 1024

    private class NoRoom : java.io.IOException("not enough free space to restore")

    /**
     * Restore from a backup. Returns files written; -1 when it is not a KaizoCore backup and [NO_ROOM] when the phone
     * ran short of space, and in both cases nothing changed.
     *
     * The whole zip is read into [STAGING] first and moved into place only when it is a KaizoCore backup read to its
     * end (rc33 audit P1): entries used to be written as they were read, so a damaged or foreign zip left a phone half
     * restored, and the message said nothing had changed. A zip that breaks part way throws, with nothing moved.
     *
     * "Read to its end" is the zip's own end record (rc32 audit P2 #13). ZipInputStream stops, with no error, where a
     * cut leaves the next entry's header short, so a backup cut there restored the files before the cut (the run's
     * name and seed, say, without its game) and called it done. The bytes after the entries are read too, and the last
     * of them must be the zip's end record, counting the same entries.
     */
    fun read(filesDir: File, input: InputStream, entryCap: Long = ENTRY_CAP, freeFloor: Long = FREE_FLOOR): Int {
        val stage = File(filesDir, STAGING).apply { deleteRecursively(); mkdirs() }
        try {
            var marker = false
            var entries = 0
            val staged = ArrayList<Pair<File, File>>()
            val tail = ZipTail(input.buffered())
            ZipInputStream(tail).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    entries++
                    if (e.name == "KAIZOCORE-BACKUP.txt") { marker = true; zip.closeEntry(); continue }
                    if (e.isDirectory || !admits(e.name)) { zip.closeEntry(); continue }
                    val target = File(filesDir, e.name)
                    if (!target.canonicalPath.startsWith(filesDir.canonicalPath)) { zip.closeEntry(); continue }
                    val held = File(stage, e.name).apply { parentFile?.mkdirs() }
                    held.outputStream().use { copyCapped(zip, it, entryCap, stage, freeFloor) }
                    if (e.time > 0) held.setLastModified(e.time)
                    staged += held to target
                    zip.closeEntry()
                }
                tail.finish()
            }
            if (!marker) return -1
            if (!tail.endsWithZipEnd(entries)) throw java.io.EOFException("the backup file is cut short")
            // Decided before anything moves: whether the backup is another run than the one on this phone.
            val otherRun = anotherRun(filesDir, stage)
            var moved = 0
            try {
                for ((held, target) in staged) {
                    target.parentFile?.mkdirs()
                    val time = held.lastModified()
                    StateSlots.replace(held, target)
                    if (time > 0) target.setLastModified(time)
                    moved++
                }
            } catch (t: Throwable) {
                // Some files are in place: the run's record says a restore happened all the same (R1), then it fails.
                if (moved > 0) afterRestore(filesDir)
                throw t
            }
            if (otherRun) dropOtherRunFiles(filesDir, staged.mapTo(HashSet()) { it.second.absolutePath })
            afterRestore(filesDir)
            return staged.size
        } catch (e: NoRoom) {
            return NO_ROOM
        } finally {
            stage.deleteRecursively()
        }
    }

    /** One entry into the stage: at most [cap] bytes, and never past [floor] free space on [disk]'s volume. */
    private fun copyCapped(from: InputStream, to: OutputStream, cap: Long, disk: File, floor: Long) {
        if (disk.usableSpace < floor) throw NoRoom()
        val buf = ByteArray(1 shl 16)
        var total = 0L
        var sinceCheck = 0L
        while (true) {
            val r = from.read(buf)
            if (r < 0) break
            total += r
            if (total > cap) throw java.io.IOException("an entry is bigger than any file a backup holds")
            to.write(buf, 0, r)
            sinceCheck += r
            if (sinceCheck >= 4L shl 20) {
                sinceCheck = 0
                if (disk.usableSpace < floor) throw NoRoom()
            }
        }
    }

    /**
     * Whether the backup in [stage] is another run than the one on this phone: it has one (prep/lastseed.txt), and
     * its seed or its game is not the phone's.
     */
    private fun anotherRun(filesDir: File, stage: File): Boolean {
        val theirs = File(stage, "prep/lastseed.txt").takeIf { it.isFile } ?: return false
        fun seed(f: File) = runCatching { f.readText().trim() }.getOrDefault("")
        fun game(dir: File) = runCatching { File(dir, "prep/lastrun.txt").readLines().firstOrNull()?.trim() }.getOrNull().orEmpty()
        return seed(theirs) != seed(File(filesDir, "prep/lastseed.txt")) || game(stage) != game(filesDir)
    }

    /**
     * After a backup of another run is in: the phone's own run files that the backup did not replace (rc32 audit P2
     * #12). Restore only adds and overwrites, so the phone's run kept its randomizer log beside the restored game
     * (Open Book, the game-over log and Save this attempt read it), its event log (the restored run's record counted
     * its loads), and any notes the backed-up run had not written. A backup of the same run keeps them: they are its.
     */
    private fun dropOtherRunFiles(filesDir: File, restored: Set<String>) {
        val prep = File(filesDir, "prep")
        val runFiles = RUN_NOTES.map { File(prep, it) } + File(prep, "runs").listFiles().orEmpty().filter { f ->
            f.isFile && f.name.startsWith("current.") &&
                (f.name.endsWith(".log") || f.name == "current.species.tsv" || f.name == "current.recipe")
        }
        val gone = runFiles.filter { it.absolutePath !in restored }
        DiskWriter.forget(gone)
        gone.forEach { runCatching { it.delete() } }
    }

    /**
     * The bytes read through it, the last [KEEP] of them kept, so that once the entries are read the rest of the file
     * can be read too and its end checked for the zip's end record.
     */
    private class ZipTail(input: InputStream) : FilterInputStream(input) {
        private val ring = ByteArray(KEEP)
        private var count = 0L

        private fun keep(b: ByteArray, off: Int, n: Int) {
            for (i in 0 until n) { ring[(count % KEEP).toInt()] = b[off + i]; count++ }
        }

        override fun read(): Int = super.read().also { if (it >= 0) { ring[(count % KEEP).toInt()] = it.toByte(); count++ } }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) keep(b, off, it) }

        // Skipped bytes are read, so the end is still seen.
        override fun skip(n: Long): Long {
            val buf = ByteArray(8192)
            var left = n
            while (left > 0) { val r = read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (r < 0) break; left -= r }
            return n - left
        }

        /** Reads what the entries left: the central directory and the end record. */
        fun finish() {
            val buf = ByteArray(8192)
            while (read(buf, 0, buf.size) >= 0) Unit
        }

        /** True when the last bytes are a zip end record (with its comment, if any) counting [entries] entries. */
        fun endsWithZipEnd(entries: Int): Boolean {
            val n = minOf(count, KEEP.toLong()).toInt()
            if (n < END_RECORD) return false
            val t = ByteArray(n) { i -> ring[((count - n + i) % KEEP).toInt()] }
            fun u16(at: Int) = (t[at].toInt() and 0xff) or ((t[at + 1].toInt() and 0xff) shl 8)
            for (at in n - END_RECORD downTo 0) {
                if (t[at] != 0x50.toByte() || t[at + 1] != 0x4b.toByte() || t[at + 2] != 5.toByte() || t[at + 3] != 6.toByte()) continue
                if (at + END_RECORD + u16(at + 20) != n) continue
                val total = u16(at + 10)
                // A zip of more entries than two bytes count keeps the real count in its Zip64 record.
                return total == 0xFFFF || total == (entries and 0xFFFF)
            }
            return false
        }

        companion object {
            /** The end record is 22 bytes, then up to 65535 of comment. */
            const val END_RECORD = 22
            const val KEEP = END_RECORD + 65535
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

    /**
     * A staging folder left by a restore the app was ended in the middle of: until the next restore it held up to a
     * whole backup's size (rc32 audit P3 #20). Swept at launch, never while a restore is under way.
     */
    fun sweepStaging(filesDir: File) {
        if (!RestoreGate.isOpen) runCatching { File(filesDir, STAGING).deleteRecursively() }
    }

    fun suggestedName(): String =
        "KaizoCore-backup-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".zip"
}

/**
 * What a restore holds still while it puts files back (rc32 audit P2 #9). Both restores restart the app at once (rc33
 * audit P1), and the restart ended whatever else was writing: a background cloud sync copying the zip over the only
 * cloud copy (left cut short), a new run between moving the old game away and the new one in (left with no game). So
 * a restore starts only when no new run is being made, holds new runs and background syncs off until the restart,
 * waits for a sync already under way, and keeps the background writer (DiskWriter) from writing over what it puts back.
 * All of it in memory: a crash cannot leave anything held.
 */
object RestoreGate {
    const val NEW_RUN = "A new run is being made. Restore once it is in."
    const val SYNCING = "A cloud sync is still writing. Restore again in a minute."
    /** Under the spinner while a sync under way is waited for: a DS backup can take tens of seconds. */
    const val WAITING = "Waiting for the cloud sync to finish first."

    /** From [open] until the restart, or [close]. */
    @Volatile var isOpen = false
        private set

    /** On the main thread, as Restore is pressed: null when the restore may start, else why not. */
    fun open(): String? {
        if (RunJob.busy || !NewRunGuard.claim()) return NEW_RUN
        isOpen = true
        CloudSync.hold()
        return null
    }

    /**
     * Off the main thread, before the backup is read: waits [syncWaitMs] at most for a cloud sync under way, then
     * holds the background writer. Null when the restore may go on; else why not, with everything let go.
     */
    fun settle(syncWaitMs: Long = 60_000): String? {
        if (!CloudSync.awaitIdle(syncWaitMs)) { close(); return SYNCING }
        DiskWriter.hold()
        return null
    }

    /** A restore that changed nothing: everything goes on as before. */
    fun close() {
        DiskWriter.release()
        CloudSync.release()
        if (isOpen) { isOpen = false; NewRunGuard.release() }
    }
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
