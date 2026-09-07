package com.ironmonone.app

import android.content.Context
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Bps
import com.ironmonone.patch.Crc32
import java.io.File

/**
 * App-private storage for everything the loop needs: imported patches, prepared ROMs,
 * settings files, and the current/previous run outputs.
 *
 * Real files on purpose: the randomizer engine's API is path-based, and filesDir gives
 * real paths without any SAF copying. Nothing here is visible to other apps; exporting
 * the current run out to shared storage (where ironmon_emu can open it) goes through
 * SAF in the UI layer.
 */
class PrepStore(context: Context) {

    private val root = File(context.filesDir, "prep").apply { mkdirs() }
    private val patches = File(root, "patches").apply { mkdirs() }
    private val prepared = File(root, "prepared").apply { mkdirs() }
    private val settings = File(root, "settings").apply { mkdirs() }
    private val runs = File(root, "runs").apply { mkdirs() }
    private val filesDir: File = context.filesDir

    // ---------------------------------------------------------------- sessions

    /** The player's own ROMs, playable with or without a tracker. */
    val library = LibraryStore(File(root, "library"))

    /**
     * What Play should open. A library selection that no longer resolves
     * (file deleted, or a file with no console) falls back to the run and
     * clears itself, so Play never opens on a dead pointer.
     */
    fun session(): GameSession {
        val libName = library.selectedLibraryName()
        if (libName != null) {
            val s = library.find(libName)?.let { GameSession.forLibrary(it) }
            if (s != null) return s
            library.selectRun()
        }
        return GameSession.forRun(currentRun, RomKind.byId(loadLastRun()?.first))
    }

    fun sramFile(s: GameSession): File = SessionPaths.sram(filesDir, s, loadLastRun()?.first)
    fun slotFile(s: GameSession, n: Int): File = SessionPaths.slot(filesDir, s, n)
    fun slotStamp(s: GameSession, n: Int): File = SessionPaths.slotStamp(filesDir, s, n)
    fun marksFile(s: GameSession): File = SessionPaths.marks(root, s)

    /**
     * Per-game settings (GameSettings). The old app-wide playspeed.txt and
     * playmute.txt seed the global fallback once, so an existing install
     * keeps its desk setup on the first launch after this change.
     */
    val games: GameSettings by lazy { GameSettings(File(root, "games")).also { g ->
        if (!File(root, "games/_last.properties").exists() &&
            (speedFile.exists() || muteFile.exists())) {
            g.save("_migrated", GameSettings.Values(speed = playSpeed(), muted = playMuted()))
        }
    } }

    /** Core options per console (CoreOptions / CoreOptionStore). */
    val coreOptions by lazy { CoreOptionStore(File(root, "coreopts")) }

    /** How the pad is drawn (PadSkin): one choice for the whole app. */
    private val skinFile = File(root, "skin.txt")
    fun padSkin(): PadSkin = PadSkin.load(skinFile)
    fun setPadSkin(s: PadSkin) = PadSkin.save(skinFile, s)

    /** Pad and DS screen layouts per orientation and console (LayoutStore). */
    val layouts by lazy { LayoutStore(File(root, "layouts")) }

    /** Per-game cheat lists (CheatStore). */
    val cheats by lazy { CheatStore(File(root, "cheats")) }

    fun gameSettings(s: GameSession): GameSettings.Values = games.load(s.id)
    fun saveGameSettings(s: GameSession, v: GameSettings.Values) = games.save(s.id, v)

    /** What a save state is stamped with: the run's kind+seed, or the library id. */
    fun stateStamp(s: GameSession): String = if (s.isRun) runIdentity() else s.id

    // ------------------------------------------------------------------ patches

    /**
     * Files the Nat. Dex patches are kept under, decided by the patch's own embedded
     * source CRC rather than its filename. Import once; found automatically after.
     */
    private fun patchNameFor(kind: RomKind): String? = when (kind.expectedCrc) {
        RomKind.EMERALD_U.expectedCrc -> "natdex-emerald.bps"
        RomKind.FIRERED_U_V11.expectedCrc -> "natdex-firered.bps"
        else -> null
    }

    fun patchFileFor(kind: RomKind): File? =
        patchNameFor(kind)?.let { File(patches, it) }?.takeIf { it.exists() }

    /**
     * The Nat. Dex patch for [kind], materialising the bundled copy on first use.
     *
     * The patches ship inside the APK so a fresh install can build a Nat. Dex ROM
     * with no hunting for files. They are extracted on demand rather than at
     * startup: together they are ~51MB, and a first run should not pay that cost
     * for a ROM the user may never patch. An imported patch already on disk wins,
     * so an imported copy of a KNOWN release wins over the bundled one; a release this app has never seen still needs an app update, because prepared ROMs are identified by CRC.
     */
    fun patchFileOrBundled(context: Context, kind: RomKind): File? {
        patchFileFor(kind)?.let { return it }
        val name = patchNameFor(kind) ?: return null
        val dest = File(patches, name)
        return runCatching {
            context.assets.open("patches/$name").use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            dest
        }.getOrNull()
    }

    /** Returns the stored location, or a plain-English refusal. */
    fun importPatch(bytes: ByteArray): Result<File> {
        val info = runCatching { Bps.info(bytes) }.getOrElse {
            return Result.failure(IllegalArgumentException("That is not a BPS patch."))
        }
        if (!info.patchIntact) {
            return Result.failure(IllegalArgumentException("That patch file is damaged."))
        }
        val name = when (info.sourceCrc) {
            RomKind.EMERALD_U.expectedCrc -> "natdex-emerald.bps"
            RomKind.FIRERED_U_V11.expectedCrc -> "natdex-firered.bps"
            else -> return Result.failure(IllegalArgumentException(
                "That patch does not apply to Emerald (U) or FireRed 1.1, so this app " +
                    "has no use for it."))
        }
        val f = File(patches, name)
        f.writeBytes(bytes)
        return Result.success(f)
    }

    // ------------------------------------------------------------ prepared ROMs

    fun preparedFile(kind: RomKind): File =
        File(prepared, "${kind.id}.${kind.fileExtension}")

    fun savePrepared(kind: RomKind, bytes: ByteArray): File =
        preparedFile(kind).apply { writeBytes(bytes) }

    /**
     * The same from a file on disk, MOVED when the source is ours (the PREP
     * cache), copied otherwise. A copy of a 512 MB dump needs another 512 MB
     * free and, left behind in the cache, filled a phone up (2026-09-07).
     */
    fun savePrepared(kind: RomKind, file: File): File {
        val dest = preparedFile(kind)
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        if (!file.renameTo(dest)) { file.copyTo(dest, overwrite = true); file.delete() }
        crcCache.remove(dest.absolutePath)
        return dest
    }

    /**
     * CRC per prepared file, keyed on (path, mtime, size) so an unchanged file
     * is never hashed twice. Hashing read every ROM in full - up to five files
     * of 16-128MB - and listPrepared runs on the COMPOSITION thread from two
     * screens, one of which re-calls it four times per prepare. That was a
     * guaranteed multi-second jank and a plausible ANR.
     */
    private val crcCache = HashMap<String, Pair<String, Long>>()

    /**
     * The CRC of a prepared file, streamed and remembered on disk. This used to
     * be Crc32.of(f.readBytes()): with a 512 MB Black 2 stored, the PREP tab's
     * "Already prepared" list asked for 512 MB of heap on every launch and the
     * app could not open at all (Blake's phone, 2026-09-07). The on-disk
     * memo (path, mtime:size, crc) means the file is read once, not per launch.
     */
    private val crcMemo = File(root, "crc-cache.txt")
    private fun cachedCrc(f: File): Long {
        val stamp = f.lastModified().toString() + ":" + f.length()
        crcCache[f.absolutePath]?.let { (s, crc) -> if (s == stamp) return crc }
        if (crcCache.isEmpty()) runCatching {
            crcMemo.takeIf { it.isFile }?.forEachLine { line ->
                val p = line.split('|'); if (p.size == 3) crcCache[p[0]] = p[1] to (p[2].toLongOrNull() ?: return@forEachLine)
            }
            crcCache[f.absolutePath]?.let { (s, crc) -> if (s == stamp) return crc }
        }
        val c = java.util.zip.CRC32()
        f.inputStream().buffered(1 shl 20).use { i -> val buf = ByteArray(1 shl 20); while (true) { val n = i.read(buf); if (n < 0) break; c.update(buf, 0, n) } }
        val crc = c.value
        crcCache[f.absolutePath] = stamp to crc
        runCatching { crcMemo.writeText(crcCache.entries.joinToString(System.lineSeparator()) { (k, v) -> k + "|" + v.first + "|" + v.second }) }
        return crc
    }

    /** Every prepared ROM on hand, identified by CRC so a stale file cannot lie. */
    fun listPrepared(): List<Pair<RomKind, File>> {
        val prepared = (RomKind.allV1 + RomKind.allNatDex).mapNotNull { kind ->
            val f = preparedFile(kind)
            if (f.exists() && cachedCrc(f) == kind.expectedCrc) kind to f else null
        }
        // 2026-09-07: a verified dump added on the ROMS tab is as good a base as
        // one that went through PREP (PREP only adds Nat. Dex patching), and
        // Blake's phone had Black 2 in the library with nothing to randomize.
        // Library entries fill in for kinds PREP has not stored.
        val have = prepared.map { it.first.id }.toSet()
        val fromLibrary = runCatching { library.list() }.getOrDefault(emptyList())
            .filter { it.verified && it.kind != null && it.kind.id !in have }
            .distinctBy { it.kind!!.id }
            .map { it.kind!! to it.file }
        return prepared + fromLibrary
    }

    // ---------------------------------------------------------------- settings

    /**
     * Copies the bundled Kaizo presets in on first run, so a fresh install can
     * randomize and can open the editor immediately. Without a preset on disk
     * there is nothing to edit, which made "make a custom preset" impossible
     * until the user had hunted down an .rnqs by hand.
     *
     * Only ever ADDS missing files: a preset the user edited or deleted is
     * never restored or overwritten.
     */
    fun seedBundledPresets(context: Context): Int {
        var added = 0
        runCatching {
            val names = context.assets.list("presets") ?: return 0
            names.forEach { name ->
                val dest = File(settings, name)
                if (!dest.exists()) {
                    context.assets.open("presets/$name").use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    }
                    added++
                }
            }
        }
        return added
    }

    /** Per-run stat notes (see StatMarks). Lives beside the run, cleared with it. */
    /**
     * Force the on-screen pad even when a controller is detected.
     *
     * Detection treats any gamepad, joystick, or non-virtual keyboard as a
     * controller and hides the pad entirely. A paired keyboard - or a phantom
     * device the system reports - therefore left no way to play by touch and
     * no way to get the pad back. This is that way back, and it persists so
     * the choice is not re-litigated on every launch.
     */
    private val padForceFile = File(root, "padforce.txt")

    fun padForced(): Boolean = padForceFile.exists()

    fun setPadForced(on: Boolean) {
        runCatching {
            if (on) { padForceFile.parentFile?.mkdirs(); padForceFile.writeText("1") }
            else padForceFile.delete()
        }
    }

    // ------------------------------------------------------ play preferences

    /**
     * Turbo speed and mute, kept across NEW RUN.
     *
     * These are how the player has set up their desk, not part of a run. A
     * seed dies to a crit every few minutes and NEW RUN is the most-pressed
     * button in the app; resetting to 1x-and-audible every time meant
     * re-muting and re-pressing turbo on each attempt.
     *
     * Stored as plain files beside padforce.txt rather than in preferences,
     * so every persisted flag in this app lives in one directory.
     */
    private val speedFile = File(root, "playspeed.txt")
    private val muteFile = File(root, "playmute.txt")

    /** Last turbo multiplier. Clamped, so a corrupt file cannot wedge playback. */
    fun playSpeed(): Int = runCatching {
        speedFile.takeIf { it.exists() }?.readText()?.trim()?.toInt() ?: 1
    }.getOrNull()?.takeIf { it in 1..16 } ?: 1

    fun setPlaySpeed(v: Int) {
        runCatching { speedFile.parentFile?.mkdirs(); speedFile.writeText("$v") }
    }

    fun playMuted(): Boolean = muteFile.exists()

    fun setPlayMuted(on: Boolean) {
        runCatching {
            if (on) { muteFile.parentFile?.mkdirs(); muteFile.writeText("1") }
            else muteFile.delete()
        }
    }

    // ------------------------------------------------------ last run failure

    /**
     * Why the last NEW RUN failed, kept on disk.
     *
     * A failed NEW RUN drops the Play tab to its "No run yet." empty state,
     * and that branch returns BEFORE the status line exists - so the reason
     * was composed, then discarded, and the user saw a blank screen with no
     * explanation for a run that had just vanished. Persisting it means the
     * failure explains itself even though the composition that produced it is
     * gone.
     */
    private val runErrorFile = File(root, "run-error.txt")

    fun lastRunError(): String? =
        runErrorFile.takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    fun setLastRunError(message: String?) {
        runCatching {
            if (message.isNullOrBlank()) runErrorFile.delete()
            else { runErrorFile.parentFile?.mkdirs(); runErrorFile.writeText(message) }
        }
    }

    fun marksFile(): File = File(root, "marks.txt")

    /** Scratch space for writes that must not leave a half-file behind. */
    fun cacheDirFor(): File = File(root, "tmp").apply { mkdirs() }

    fun importSettings(name: String, bytes: ByteArray): File =
        File(settings, name.substringAfterLast('/').substringAfterLast('\\'))
            .apply { writeBytes(bytes) }

    /** The bundled Gen 1 PART 2 file, for a Gen 1 kind; null for every other console. */
    fun secondPassSettings(kind: RomKind): File? =
        if (kind.generation == com.ironmonone.core.Generation.GB1) File(settings, com.ironmonone.app.engine.Randomizers.GEN1_SECOND_PASS) else null

    fun listSettings(): List<File> =
        settings.listFiles { f -> f.isFile && f.name.endsWith(".rnqs", true) }
            ?.sortedBy { it.name } ?: emptyList()

    // --------------------------------------------------------------- favorites

    /** One name per line or comma-separated; matching is case-insensitive. */
    private val favoritesFile = File(root, "favorites.txt")

    fun loadFavorites(): Set<String> =
        if (!favoritesFile.exists()) emptySet()
        else favoritesFile.readText().split('\n', ',')
            .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    fun saveFavorites(text: String) = favoritesFile.writeText(text)

    fun favoritesText(): String =
        if (favoritesFile.exists()) favoritesFile.readText() else ""

    // ------------------------------------------------------- last run recipe

    private val lastRunFile = File(root, "lastrun.txt")

    /** Remember what the Run tab last randomized, so Play's NEW RUN can repeat it. */
    fun saveLastRun(romKindId: String, settingsName: String) =
        lastRunFile.writeText("$romKindId\n$settingsName")

    fun loadLastRun(): Pair<String, String>? {
        if (!lastRunFile.exists()) return null
        val lines = lastRunFile.readLines()
        return if (lines.size >= 2) lines[0] to lines[1] else null
    }

    // ------------------------------------------------------- stream layout data

    /** Attempt counter, IronMON style. Also the OBS text-source file (brief 16.4):
     *  a single integer in attempts.txt, bumped on every New Run. */
    private val attemptsFile = File(root, "attempts.txt")
    private val attemptsDir = File(root, "attempts").apply {
        mkdirs()
        // The old single attempts.txt counted every game together, so its value
        // is a mix that cannot be split back apart. Delete it rather than leave
        // a stale number on disk that looks meaningful.
        runCatching { File(root, "attempts.txt").takeIf { it.exists() }?.delete() }
    }

    /**
     * Attempts are counted PER GAME, not once for the whole app.
     *
     * There used to be a single attempts.txt, so a FireRed run and an Emerald
     * run bumped the same number and each game showed the other's attempts.
     * An IronMON attempt count only means anything against one game.
     *
     * The old shared number cannot be split back out - there is no record of
     * which game each of those attempts belonged to - so it is not migrated,
     * and every game starts its own count.
     */
    private fun attemptFile(romId: String) =
        File(attemptsDir, romId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".txt")

    private fun currentRomId(): String = loadLastRun()?.first ?: "unknown"

    fun attempt(romId: String = currentRomId()): Int =
        if (Demo.mode != null) Demo.ATTEMPT
        else attemptFile(romId).takeIf { it.exists() }
            ?.readText()?.trim()?.toIntOrNull() ?: 0

    fun bumpAttempt(romId: String = currentRomId()): Int =
        (attempt(romId) + 1).also { attemptFile(romId).writeText("$it") }

    private val lastSeedFile = File(root, "lastseed.txt")

    fun saveLastSeed(seed: Long) = lastSeedFile.writeText("%016x".format(seed))

    /**
     * Which randomization is loaded right now: the prepared ROM's id plus the
     * seed. A save state only makes sense against the exact ROM it was taken
     * from, so this is what a state gets stamped with.
     */
    fun runIdentity(): String {
        val rom = loadLastRun()?.first ?: "?"
        val seed = runCatching { lastSeedFile.readText().trim() }.getOrDefault("?")
        return "$rom/$seed"
    }

    fun lastSeed(): String? = lastSeedFile.takeIf { it.exists() }?.readText()?.trim()

    // -------------------------------------------------------------------- runs

    /**
     * The playable output of the last randomize. The extension follows the ROM's
     * own kind rather than a hardcoded "gba", so a DS run lands as current.nds
     * and the play screen picks its core from that (brief 15.5).
     */
    private fun runExtension(): String =
        loadLastRun()?.first?.let { id ->
            (RomKind.allV1 + RomKind.allNatDex).firstOrNull { it.id == id }?.fileExtension
        } ?: "gba"

    val currentRun: File get() = File(runs, "current.${runExtension()}")
    val previousRun: File get() = File(runs, "previous.${runExtension()}")

    /**
     * Explicit form for the randomize call, which must write the NEW rom's
     * extension: [currentRun] resolves through the *last* run, so using it as a
     * destination would drop a .nds seed into current.gba the first time the
     * console changes.
     */
    fun currentRunFor(kind: RomKind): File =
        File(runs, "current.${kind.fileExtension}")

    fun previousRunFor(kind: RomKind): File =
        File(runs, "previous.${kind.fileExtension}")

    /**
     * "Save this attempt": the reference's GameOverScreen.saveCurrentGameFiles
     * copies the ROM, its log and the tracked data into a saved_games folder,
     * named by seed, and never overwrites an earlier save. Here: the current
     * run's ROM and log, a save state of the moment (when one could be taken),
     * and the per-run notes, into files/attempts/<game>-<attempt>-<seed>/.
     * Returns whether every file that exists was copied.
     */
    fun saveAttempt(kind: RomKind, attempt: Int, seed: String, state: ByteArray?): Boolean = runCatching {
        val base = File(File(root.parentFile, "attempts"), "${kind.id}-attempt$attempt-$seed".replace(Regex("[^A-Za-z0-9._-]"), "_"))
        var dir = base; var n = 2
        while (dir.exists()) { dir = File(base.parentFile, base.name + "-$n"); n++ }
        dir.mkdirs()
        val rom = currentRunFor(kind)
        if (!rom.isFile) error("no run")
        rom.copyTo(File(dir, "run.${kind.fileExtension}"), overwrite = true)
        currentRunLogFor(kind)?.copyTo(File(dir, "run.${kind.fileExtension}.log"), overwrite = true)
        state?.takeIf { it.isNotEmpty() }?.let { File(dir, "state.bin").writeBytes(it) }
        listOf("marks.txt", "notes.txt", "routes.txt", "moves.txt", "abilities.txt").forEach { f ->
            File(root, f).takeIf { it.isFile }?.copyTo(File(dir, f), overwrite = true)
        }
        File(dir, "attempt.txt").writeText("game=${kind.id}\nattempt=$attempt\nseed=$seed\n")
        true
    }.getOrDefault(false)

    /** The seed of the run in play, as saved by the last randomization, or "" before any. */
    fun lastSeedText(): String = runCatching { lastSeedFile.readText().trim() }.getOrDefault("")

    /** The randomizer log for the current run (Randomizers.logFor), or null when none was kept. */
    fun currentRunLogFor(kind: RomKind): File? =
        com.ironmonone.app.engine.Randomizers.logFor(currentRunFor(kind)).takeIf { it.isFile && it.length() > 0 }

    /** Rotate current -> previous before a new seed lands. */
    /**
     * Deletes the per-run notes (stat marks, free-text notes, route
     * sightings). They describe one seed's randomization and must die with
     * it. Called by BOTH new-run paths.
     */
    fun clearRunNotes() {
        listOf("marks.txt", "notes.txt", "routes.txt", "moves.txt",
            "abilities.txt").forEach {
            runCatching { File(root, it).delete() }
        }
    }

    fun rotateRuns(kind: RomKind? = null) {
        val cur = kind?.let { currentRunFor(it) } ?: currentRun
        val prev = kind?.let { previousRunFor(it) } ?: previousRun
        if (cur.exists()) {
            prev.delete()
            cur.copyTo(prev, overwrite = true)
        }
        // The log travels with its ROM.
        val curLog = com.ironmonone.app.engine.Randomizers.logFor(cur)
        val prevLog = com.ironmonone.app.engine.Randomizers.logFor(prev)
        prevLog.delete()
        if (curLog.isFile) runCatching { curLog.copyTo(prevLog, overwrite = true) }
    }
}
