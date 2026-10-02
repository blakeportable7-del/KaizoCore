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
class PrepStore(private val filesDir: File) {

    constructor(context: Context) : this(context.filesDir)

    companion object {
        /**
         * Whether a state stamp names a run. [runIdentity] writes "?" for a
         * game or seed not on disk, which is also the stamp half way through
         * NEW RUN (installRun forgets the seed first), and two unknowns must
         * never match each other.
         */
        fun stampKnown(stamp: String): Boolean = stamp.isNotBlank() && stamp.split('/').none { it == "?" || it.isBlank() }
    }

    private val root = File(filesDir, "prep").apply { mkdirs() }
    private val patches = File(root, "patches").apply { mkdirs() }
    private val prepared = File(root, "prepared").apply { mkdirs() }
    private val settings = File(root, "settings").apply { mkdirs() }
    private val runs = File(root, "runs").apply { mkdirs() }

    /** The next run, made ahead of the player asking (NextRun, NextRunJob). */
    val nextRun = NextRun(File(root, "next"))

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
     * The run's events: every load, restore, retry and resume (RunEvents),
     * beside its stat notes and gone with them on a new run. A library game
     * is not a run and keeps none.
     */
    fun runEvents(s: GameSession): RunEvents? = if (s.isRun) RunEvents(File(root, "integrity.txt")) else null

    /** Names the game the Play screen has up, while it has one (CrashResume). */
    val playMarker = File(root, "playing.txt")

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
        val name = patchNameFor(kind) ?: return null
        val dest = File(patches, name)
        // Used when known whole: copied whole out of the APK (marked), or a BPS whose own checksum holds, checked
        // once and then marked; an imported copy counts too. A copy cut short is copied again (rc33 audit P1).
        if (dest.isFile && (BundledCopy.whole(dest) || BundledCopy.bpsIntact(dest))) return dest
        return BundledCopy.extract(context, "patches/$name", dest)
    }

    /** A ruleset patch shipped in the APK (assets/patches/<name>), materialised on first use and whole. */
    fun bundledPatch(context: Context, name: String): File? {
        val dest = File(patches, name)
        if (BundledCopy.whole(dest)) return dest
        return BundledCopy.extract(context, "patches/$name", dest)
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
        val prepared = (RomKind.allV1 + RomKind.allNatDex + RomKind.allPatched).mapNotNull { kind ->
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
     * Only ever ADDS missing files, apart from one step: a copy that is byte
     * for byte a preset an earlier release shipped is brought up to date
     * (PresetMigration). A preset the user edited is never overwritten.
     */
    fun seedBundledPresets(context: Context): Int = runCatching {
        val names = context.assets.list("presets")?.toList() ?: return 0
        PresetMigration.seed(settings, names) { n -> runCatching { context.assets.open("presets/$n").use { it.readBytes() } }.getOrNull() }
    }.getOrDefault(0)

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

    /** "Get the next run ready in the background" (NextRunJob): on unless this file says off. */
    private val nextRunOffFile = File(root, "nextrun-off.txt")

    fun nextRunAhead(): Boolean = !nextRunOffFile.exists()

    fun setNextRunAhead(on: Boolean) {
        runCatching { if (on) nextRunOffFile.delete() else nextRunOffFile.writeText("1") }
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

    /** SeedLogger's past runs (DS tracker): prep/pastruns-<GameInfo NAME>.tsv; older builds keyed them by badge set. */
    fun pastRunsFile(family: String): File = File(root, "pastruns-$family.tsv")

    /** TourneyTracker's scores.tdata: prep/tourney.tsv. */
    fun tourneyFile(): File = File(root, "tourney.tsv")

    /** Scratch space for writes that must not leave a half-file behind. */
    fun cacheDirFor(): File = File(root, "tmp").apply { mkdirs() }

    /**
     * A settings file the player picked: kept only if it is an .rnqs one of
     * the two randomizers can read, and never over a file already here (a
     * second "FRLG Kaizo.rnqs" becomes "FRLG Kaizo (2).rnqs"). IMPORT used to
     * take any file under any name and replace a same-named preset without a
     * word (audit, 2026-09-27). Returns the file, or why it was refused.
     */
    fun addSettingsFile(name: String, bytes: ByteArray): Result<File> {
        val clean = name.substringAfterLast('/').substringAfterLast('\\')
        if (!clean.endsWith(".rnqs", true))
            return Result.failure(IllegalArgumentException("$clean is not a randomizer settings file (.rnqs)."))
        fun reads(read: (java.io.FileInputStream) -> Any): Boolean {
            val tmp = File.createTempFile("check", ".rnqs", cacheDirFor())
            return try { tmp.writeBytes(bytes); runCatching { java.io.FileInputStream(tmp).use { read(it) } }.isSuccess } finally { tmp.delete() }
        }
        if (!reads { com.dabomstew.pkrandomzx.Settings.read(it) } && !reads { com.dabomstew.pkrandom.Settings.read(it) })
            return Result.failure(IllegalArgumentException("$clean could not be read. It may be damaged or from a newer randomizer."))
        val stem = clean.substring(0, clean.length - 5)
        var target = File(settings, clean)
        var n = 2
        while (target.exists()) { target = File(settings, "$stem ($n).rnqs"); n++ }
        target.writeBytes(bytes)
        return Result.success(target)
    }

    // importSettings (write under any name, over any file) is gone: the editor's
    // Save As and PASTE used it and replaced same-named presets, bundled ones
    // included, without a word. Everything goes through addSettingsFile now
    // (2026-09-27, audit).

    /** The bundled Gen 1 PART 2 file, for a Gen 1 kind; null for every other console. */
    fun secondPassSettings(kind: RomKind): File? =
        if (kind.generation == com.ironmonone.core.Generation.GB1) File(settings, com.ironmonone.app.engine.Randomizers.GEN1_SECOND_PASS) else null

    /** A settings file by name, for RnqsInfo.of (which also reads its sidecar). */
    fun settingsFile(name: String): File = File(settings, name)

    fun listSettings(): List<File> =
        settings.listFiles { f -> f.isFile && f.name.endsWith(".rnqs", true) }
            ?.sortedBy { it.name } ?: emptyList()

    // --------------------------------------------------------------- favorites

    /** One name per line or comma-separated; matching is case-insensitive. */
    /**
     * Favorites are PER GAME (Blake, 2026-09-07: "favorites are varying per
     * rom"), the way the DS tracker keeps savedData/<game>.faves. One file per
     * RomKind id under prep/favorites/; the old single favorites.txt is the
     * starting value for any game that has none yet, so nothing typed before
     * this change is lost.
     */
    private val favoritesDir = File(root, "favorites").apply { mkdirs() }
    private val legacyFavoritesFile = File(root, "favorites.txt")
    private fun favoritesFile(romId: String?): File =
        File(favoritesDir, (romId ?: "unknown").replace(Regex("[^A-Za-z0-9._-]"), "_") + ".txt")

    fun favoritesText(romId: String? = currentRomId()): String {
        val f = favoritesFile(romId)
        return when {
            f.exists() -> f.readText()
            legacyFavoritesFile.exists() -> legacyFavoritesFile.readText()
            else -> ""
        }
    }

    fun saveFavorites(romId: String?, text: String) = runCatching { favoritesFile(romId).writeText(text) }.let { }

    fun loadFavorites(romId: String? = currentRomId()): Set<String> =
        favoritesText(romId).split('\n', ',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    // ------------------------------------------------------- last run recipe

    private val lastRunFile = File(root, "lastrun.txt")

    /**
     * Remember what the Run tab last randomized, so Play's NEW RUN can repeat it. Every reader takes the first two
     * lines; after them come what is known about the run: whether its settings file is custom (CustomRuns), and
     * whether it is a Nuzlocke, which counts no attempt and files no IronMON record.
     */
    fun saveLastRun(romKindId: String, settingsName: String, custom: Boolean? = null, nuzlocke: Boolean = false, variant: String? = null) =
        lastRunFile.writeText("$romKindId\n$settingsName" + (custom?.let { "\ncustom=$it" } ?: "") + (if (nuzlocke) "\nnuzlocke=true" else "") +
            (variant?.takeIf { it.isNotBlank() }?.let { "\nvariant=" + it.replace('\n', ' ') } ?: ""))

    private fun lastRunFlag(line: String): Boolean =
        runCatching { lastRunFile.readLines().drop(2).any { it.trim() == line } }.getOrDefault(false)

    /**
     * Whether the run in play was started from a custom settings file (CustomRuns). A run started before rc32 has no
     * line saying so: it is judged from its file now, as the Kaizo IronMON screen judges one; false when that cannot be.
     */
    fun lastRunCustom(): Boolean {
        val lines = runCatching { lastRunFile.readLines() }.getOrNull() ?: return false
        lines.drop(2).firstOrNull { it.trim().startsWith("custom=") }?.let { return it.trim() == "custom=true" }
        val name = lines.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return false
        val bundled = CustomRuns.bundled?.invoke()?.takeIf { it.isNotEmpty() } ?: return false
        return CustomRuns.isCustom(name, runCatching { settingsFile(name).takeIf { it.isFile }?.readBytes() }.getOrNull(), bundled)
    }

    /** Whether the run in play is a randomized Nuzlocke (installRun with no attempt counted). */
    fun lastRunNuzlocke(): Boolean = lastRunFlag("nuzlocke=true")

    /** What the run in play's official file ran without (ExtraPasses.variant), or null. */
    fun lastRunVariant(): String? =
        runCatching { lastRunFile.readLines().drop(2).firstOrNull { it.startsWith("variant=") }?.removePrefix("variant=")?.trim() }.getOrNull()?.ifBlank { null }

    /**
     * Where the app should open (2026-09-29): "HOME", the menu, except "PLAY" when the app is
     * being reopened after it closed in the middle of a game (a crash, Android freeing memory,
     * a swipe from recents). Play's marker names that game and is deleted whenever Play closes
     * the normal way (CrashResume), so it is still there only after such a close, and opening on
     * Play is what lets CrashResume bring the game back where it was. It is read with
     * [CrashResume.parse], never [CrashResume.leftover]: leftover is the read Play's core-up
     * makes once per process, and taking it here would leave Play with nothing to resume.
     *
     * A staged demo ([demo], Demo.mode) shows on the Play screen, so it opens where every launch
     * used to when a run was waiting: on Play. Until Home existed the app opened on PLAY for any
     * run waiting, on RUN with a game prepared and on PREP with nothing (audit, 2026-09-27: a
     * player mid-run landed on "Add a game dump" every launch); Home's Continue card is now the
     * way back into a run.
     */
    fun startingPoint(demo: String? = null): String = when {
        CrashResume.parse(playMarker) != null -> "PLAY"
        demo != null && runWaiting() -> "PLAY"
        else -> "HOME"
    }

    /** A randomized run is on disk for the game the app last randomized. */
    private fun runWaiting(): Boolean {
        val kind = loadLastRun()?.first?.let { RomKind.byId(it) }
        return kind != null && currentRunFor(kind).isFile
    }

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
     * Attempts are counted per game and settings file since 2026-09-30 (IronMON rules check R8, Blake's call), the way
     * the PC tracker counts per profile. One count per game put every Standard, custom and Nuzlocke game into the
     * number on a Kaizo death card. (Before that, a single count for the whole app put every game into it.)
     *
     * - prep/attempts/<game>.txt counts every run started on the game, whatever its file: Your stats reads it.
     * - prep/attempts/<game>/<settings file>.txt is the attempt number a run of that file shows.
     * - A file with no count yet starts from the game's count as it stood when this came in (prep/attempts/<game>.base,
     *   written the first time it is needed), so no one's number went back to 1 (Blake: "start each file's count from
     *   your current number"). A game first played after this starts at 0.
     * - A randomized Nuzlocke counts no attempt: installRun's [countAttempt].
     */
    private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun attemptFile(romId: String) = File(attemptsDir, safeName(romId) + ".txt")
    private fun fileAttemptFile(romId: String, settingsName: String) = File(File(attemptsDir, safeName(romId)), safeName(settingsName) + ".txt")
    private fun baseFile(romId: String) = File(attemptsDir, safeName(romId) + ".base")
    private fun readCount(f: File): Int? = runCatching { f.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull() }.getOrNull()

    private fun currentRomId(): String = loadLastRun()?.first ?: "unknown"

    /** Every run started on [romId], all its settings files together. */
    fun gameAttempts(romId: String): Int = readCount(attemptFile(romId)) ?: 0

    /** The game's count when counting per settings file came in: kept the first time it is asked for. */
    private fun base(romId: String): Int =
        readCount(baseFile(romId)) ?: gameAttempts(romId).also { SafeWrite.text(baseFile(romId), "$it") }

    /** The attempts started on [romId] with [settingsName]; with no file named, the game's own count. */
    fun attemptOf(romId: String, settingsName: String?): Int = when {
        Demo.mode != null -> Demo.ATTEMPT
        settingsName.isNullOrBlank() -> gameAttempts(romId)
        else -> readCount(fileAttemptFile(romId, settingsName)) ?: base(romId)
    }

    /** The attempt number of the run in play on [romId]: its settings file's count (prep/lastrun.txt). */
    fun attempt(romId: String = currentRomId()): Int =
        attemptOf(romId, loadLastRun()?.takeIf { it.first == romId }?.second)

    /**
     * A new run of [romId] from [settingsName]: that file's next attempt number, and one more run started on the game.
     * With no file named (an old caller), the game's count alone.
     */
    fun bumpAttempt(romId: String = currentRomId(), settingsName: String? = loadLastRun()?.takeIf { it.first == romId }?.second): Int {
        val game = gameAttempts(romId) + 1
        if (settingsName.isNullOrBlank()) { SafeWrite.text(attemptFile(romId), "$game"); return game }
        val n = (readCount(fileAttemptFile(romId, settingsName)) ?: base(romId)) + 1
        SafeWrite.text(fileAttemptFile(romId, settingsName), "$n")
        SafeWrite.text(attemptFile(romId), "$game")
        return n
    }

    /**
     * What is kept under an attempt number starts over for the run that takes it: counted per settings file, two files'
     * runs can carry the same number. The Survival heal count and the summary check go; the time played is kept apart
     * (RunClock.retire), so Your stats still counts it.
     */
    private fun freshAttempt(romId: String, n: Int) {
        PcHeals.forgetAttempt(n)
        SummaryChecks.forget(n)
        RunClock.retire(RunClock.key(romId, n))
    }

    /**
     * The run in play, when a new one replaces it before it ended, is filed as ended by a new run (IronMON rules check
     * R13): a re-roll in the lab or a bail before the game-over screen left no line in its history. A Nuzlocke keeps
     * its own ledger, and a run with a record already keeps that record.
     */
    private fun fileOpenRunAsEnded(at: Long = System.currentTimeMillis()) {
        runCatching {
            val (romId, settingsName) = loadLastRun() ?: return
            if (lastRunNuzlocke()) return
            val kind = RomKind.byId(romId) ?: return
            val run = currentRunFor(kind).takeIf { it.isFile } ?: return
            val seed = lastSeedText().takeIf { it.isNotBlank() } ?: return
            val n = attemptOf(romId, settingsName)
            val history = RunHistory(runHistoryFile(kind))
            if (history.find(n, seed) != null) return
            val events = RunEvents(File(root, "integrity.txt")).entries()
            history.record(RunRecord(
                attempt = n, seed = seed, ruleset = settingsName, started = run.lastModified(), ended = at,
                playSeconds = RunClock.of(RunClock.key(romId, n)), outcome = RunRecord.Outcome.ENDED, badges = 0,
                lead = null, killer = null, trainer = "", location = "",
                restores = rewinds(events), resumes = events.count { it.kind == RunEvents.Kind.RESUME },
                resets = events.count { it.kind == RunEvents.Kind.RESET },
                keptSave = events.any { it.kind == RunEvents.Kind.KEPT_SAVE }, custom = lastRunCustom(),
                variant = lastRunVariant().orEmpty(), fromCode = events.any { it.kind == RunEvents.Kind.CODE },
            ))
        }
    }

    private val lastSeedFile = File(root, "lastseed.txt")

    /** Every run of [kind] that ended (RunHistory): personal bests and the death card. */
    fun runHistoryFile(kind: RomKind): File = File(root, "runhistory-${kind.id}.tsv")

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
            (RomKind.allV1 + RomKind.allNatDex + RomKind.allPatched).firstOrNull { it.id == id }?.fileExtension
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
        listOf("marks.txt", "notes.txt", "routes.txt", "moves.txt", "abilities.txt", "encounters.txt", "ds-encounters.txt", "safari.txt", "ds-tracked.txt", "integrity.txt").forEach { f ->
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

    /**
     * Deletes the per-run notes (stat marks, free-text notes, route
     * sightings) and the run's events (RunEvents). They describe one seed's
     * randomization and must die with it. Called by [installRun], which both
     * new-run paths go through.
     */
    fun clearRunNotes() {
        listOf("marks.txt", "notes.txt", "routes.txt", "moves.txt",
            "abilities.txt", "encounters.txt", "ds-encounters.txt", "safari.txt", "ds-tracked.txt", "integrity.txt").forEach {
            runCatching { File(root, it).delete() }
        }
    }

    /**
     * Rotate current -> previous before a new run lands. Renamed, not copied:
     * a DS run is 300 MB, and the copy cost seconds of every NEW RUN and that
     * much free space. Nothing is written over current any more (NextRun
     * moves a whole new file in), so the copy left in place had no reader.
     */
    fun rotateRuns(kind: RomKind? = null) {
        val cur = kind?.let { currentRunFor(it) } ?: currentRun
        val prev = kind?.let { previousRunFor(it) } ?: previousRun
        if (cur.exists()) {
            prev.delete()
            moveOrCopy(cur, prev)
        }
        // The log travels with its ROM.
        val curLog = com.ironmonone.app.engine.Randomizers.logFor(cur)
        val prevLog = com.ironmonone.app.engine.Randomizers.logFor(prev)
        prevLog.delete()
        if (curLog.isFile) runCatching { moveOrCopy(curLog, prevLog) }
    }

    private fun moveOrCopy(from: File, to: File) {
        if (!from.renameTo(to)) { from.copyTo(to, overwrite = true); from.delete() }
    }

    /**
     * Puts a new run in place: the one path both new-run buttons take (Play's
     * NEW RUN and the Run tab, through RunStart), for a run made there and
     * then or ahead of time (NextRun). The bookkeeping used to be written out
     * twice, once per button, and the two copies had already drifted apart.
     */
    fun installRun(
        kind: RomKind, settingsName: String, staged: NextRun.Staged, countAttempt: Boolean = true,
        /** What its official file runs without (ExtraPasses.variant), for its record. */
        variant: String? = null,
        /** Built from a run code: its record says so, and whether the seed was played before (R7). */
        fromCode: Boolean = false,
    ) {
        // Before anything moves: the run this replaces, if it never ended, goes into its history as ended (R13).
        fileOpenRunAsEnded()
        // First. A save state is stamped with the seed (runIdentity), and were
        // the app to die between the new ROM moving in and the new seed being
        // saved, the old seed would vouch for an old state on the new ROM.
        // With no seed on disk no state matches until the new one is written.
        lastSeedFile.delete()
        // A DS run's save is one per game: another DS game's is parked and this game's comes back (RunSaves.dsSwap,
        // rc33 audit P1). Before lastrun.txt names the new run, so a save from before rc33 is filed under its own game.
        RunSaves.dsSwap(filesDir, kind, RomKind.byId(loadLastRun()?.first))
        // The in-game save stays, in every game, and a copy rc32's first builds set aside comes back when there is
        // none (RunSaves, Blake 2026-09-30). Before the ROM moves: the name is the old run's.
        RunSaves.onNewSeed(RunSaves.file(filesDir, kind, currentRunFor(kind)), kind)
        rotateRuns(kind)
        nextRun.install(staged, currentRunFor(kind))
        // Custom or not, decided on the file's bytes now, while it is the file the run was made from (R2).
        val custom = CustomRuns.bundled?.invoke()?.takeIf { it.isNotEmpty() }?.let { b ->
            CustomRuns.isCustom(settingsName, runCatching { settingsFile(settingsName).takeIf { it.isFile }?.readBytes() }.getOrNull(), b)
        }
        saveLastRun(kind.id, settingsName, custom, nuzlocke = !countAttempt, variant = variant)
        saveLastSeed(staged.seed)
        // A fresh seed is what the player wants to play next, even if a
        // library ROM was open before.
        library.selectRun()
        // Named explicitly: this counter is per game and settings file and must not depend on the order of the lines
        // above. A randomized Nuzlocke takes its file's number without counting one (R8).
        val n = if (countAttempt) bumpAttempt(kind.id, settingsName) else attemptOf(kind.id, settingsName)
        freshAttempt(kind.id, n)
        // Marks, notes and route sightings describe the OLD seed's
        // randomization; carrying them into the new run is misleading.
        clearRunNotes()
        setLastRunError(null)
        // A run code's run: said on its record, with the attempt that played the same seed before, if one did (R7).
        if (fromCode) {
            val before = RunCodeHistory.playedBefore(RunHistory(runHistoryFile(kind)), "%016x".format(staged.seed), settingsName)
            RunEvents(File(root, "integrity.txt")).add(RunEvents.Kind.CODE, "run code", before?.let { "seed played before as attempt ${it.attempt}" }.orEmpty())
        }
    }
}
