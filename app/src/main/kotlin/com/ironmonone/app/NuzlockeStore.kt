package com.ironmonone.app

import com.ironmonone.tracker.NuzlockeAdapters
import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.NdsNuzlocke
import com.ironmonone.tracker.nds.NdsTrackerState
import com.ironmonone.tracker.nuzlocke.Heir
import com.ironmonone.tracker.nuzlocke.NuzlockeEdits
import com.ironmonone.tracker.nuzlocke.NuzlockeEngine
import com.ironmonone.tracker.nuzlocke.NuzlockeLedger
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.NuzlockeText
import com.ironmonone.tracker.nuzlocke.RunMeta
import com.ironmonone.tracker.nuzlocke.RunStatus
import com.ironmonone.tracker.nuzlocke.Snapshot
import java.io.File
import java.util.concurrent.Executors

/**
 * The Nuzlocke runs on disk (2026-09-29): one file per run under prep/nuzlocke/, written whole through SafeWrite
 * so a kill or a power cut never leaves half a ledger. The file's own format is NuzlockeText's.
 *
 * A run is tied to what it was started on, its bind: a library game's id, or a randomized run's "game/seed"
 * identity (PrepStore.runIdentity). The run a screen shows is the newest one for the game being played that has
 * not been replaced, so playing something else never shows another game's ledger, and starting a new Nuzlocke
 * on the same game keeps the old one as history.
 */
class NuzlockeStore(filesDir: File) {

    /** What listing a run costs: its first lines, not the whole ledger. */
    class Entry(val file: File, val header: NuzlockeText.Header)

    val dir = File(filesDir, "prep/nuzlocke")

    private fun fileName(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".txt"

    fun fileFor(id: String) = File(dir, fileName(id))

    /** Every run on disk, newest first. A file that is not a ledger, or is unreadable, is skipped and left alone. */
    fun list(): List<Entry> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") } ?: emptyArray()).also { lists++ }
            .mapNotNull { f ->
                val h = runCatching { f.bufferedReader(Charsets.UTF_8).use { r -> NuzlockeText.header(r.lineSequence().take(HEADER_LINES)) } }.getOrNull()
                h?.let { Entry(f, it) }
            }
            .sortedWith(compareByDescending<Entry> { it.header.startedAt }.thenByDescending { it.header.id })

    /** The newest run on [bind] that has not been replaced, or null. */
    fun current(bind: String): Entry? = list().firstOrNull { it.header.bind == bind && it.header.status != RunStatus.ABANDONED }

    fun load(id: String): NuzlockeLedger? =
        runCatching { fileFor(id).takeIf { it.isFile }?.readText(Charsets.UTF_8)?.let { NuzlockeText.parse(it) } }.getOrNull()

    /** Writes the ledger, after every write already queued. False when the disk refused it; the file on disk is then as it was. */
    fun save(ledger: NuzlockeLedger): Boolean = NuzlockeTracking.write(fileFor(ledger.meta.id), NuzlockeText.format(ledger), wait = true)

    /**
     * A new run on [bind]. A run still in progress on the same game is replaced by it and kept as history; one that
     * ended is left as it is. A Genlocke's next game passes the survivors of the last in [carry].
     */
    fun start(
        bind: String, game: String, rules: NuzlockeRules, at: Long,
        genlockeId: String = "", leg: Int = 0, carriedFrom: String = "", carry: List<Heir> = emptyList(),
        system: NuzlockeSystem = NuzlockeSystem.GEN3, gameKey: String = "",
    ): NuzlockeLedger {
        for (e in list()) if (e.header.bind == bind && e.header.status == RunStatus.ACTIVE) abandon(e.header.id, at)
        val meta = RunMeta(NuzlockeLedger.newId(at), bind, game, rules, at)
        meta.system = system
        meta.gameKey = gameKey
        if (rules.genlocke) {
            meta.genlockeId = genlockeId.ifEmpty { "gl-" + meta.id.removePrefix("nz-") }
            meta.leg = leg.coerceAtLeast(1)
            meta.carriedFrom = carriedFrom
            meta.heirsIn += carry
        }
        val ledger = NuzlockeLedger(meta)
        ledger.event(at, "start", "Run started: ${rules.preset.label} on $game.")
        if (carry.isNotEmpty()) ledger.event(at, "start", "Carried on from the last game: " + carry.joinToString(", ") { it.speciesName } + ".")
        save(ledger)
        NuzlockeTracking.changed()
        return ledger
    }

    /** Marks a run replaced, saying by what ([reason]). It stays on disk as history. The copy in memory, if there is one, is the freshest. */
    fun abandon(id: String, at: Long, reason: String = "Replaced by a newer run on this game."): Boolean {
        val ledger = NuzlockeTracking.loaded(id) ?: load(id) ?: return false
        if (ledger.meta.status == RunStatus.ABANDONED) return false
        ledger.meta.status = RunStatus.ABANDONED
        ledger.meta.endedAt = at
        ledger.event(at, "over", reason)
        val ok = save(ledger)
        NuzlockeTracking.forget(id)
        return ok
    }

    fun delete(id: String): Boolean {
        NuzlockeTracking.forget(id)
        // After anything still queued for it, or a late write would bring the file back.
        return NuzlockeTracking.write(fileFor(id), null, wait = true)
    }

    /**
     * Puts the ledgers of a game's randomized runs in order once the randomizer is idle (2026-09-29). A randomized
     * run's ledger is made before the game is (its seed is chosen first), so it can outlive a randomize that
     * failed, and it goes stale when the next randomize replaces the run, of the same game or another: there is one
     * run in place, so a randomized ledger for any other seed of any game is over (rc33 audit P1 #23: another game's
     * stayed "In progress" for good). [kindId] and [seedHex] are the run in place now (PrepStore.loadLastRun and
     * lastSeed). Such a ledger is deleted if nothing was ever recorded in it, and marked replaced if something was.
     * The seed in place and every library game's ledger are left alone, and nothing happens while the seed is not
     * known (half way through a new run). Returns how many ledgers it dealt with.
     */
    fun settleRandomized(kindId: String?, seedHex: String?, at: Long): Int {
        if (kindId == null || seedHex == null || !PrepStore.stampKnown("$kindId/$seedHex")) return 0
        val now = "$kindId/$seedHex"
        var n = 0
        for (e in list()) {
            val h = e.header
            if (h.status != RunStatus.ACTIVE || h.bind == now || !RUN_BIND.matches(h.bind)) continue
            val ledger = NuzlockeTracking.loaded(h.id) ?: load(h.id) ?: continue
            val sameGame = h.bind.startsWith("$kindId/")
            if (untouched(ledger)) delete(h.id)
            else abandon(h.id, at, if (sameGame) "Replaced by a newer run on this game." else "Replaced by a randomized run of another game.")
            n++
        }
        return n
    }

    /** A ledger nothing was ever recorded in: the run was made and never played, so nothing is lost by dropping it. */
    private fun untouched(l: NuzlockeLedger): Boolean =
        !l.meta.started && !l.meta.partySeen && l.roster.isEmpty() && l.areas.isEmpty() && l.notes.isEmpty() &&
            l.warnings.isEmpty() && l.events.all { it.kind == "start" }

    /**
     * NEW RUN in a randomized Nuzlocke (2026-09-30, UX audit P0-8): the next game gets a ledger of its own with the
     * same rules, and [old] stays in the list, marked replaced if it was still going (a lost or won run keeps its
     * ending). Before this the new seed had no ledger, so the Nuzlocke panel vanished without a word and the run
     * went on as a plain IronMON game.
     */
    fun startNextRandomized(old: NuzlockeLedger, kind: com.ironmonone.core.RomKind, seed: Long, at: Long): NuzlockeLedger {
        if (old.meta.status == RunStatus.ACTIVE) abandon(old.meta.id, at)
        return start(
            bindOfRun(kind.id, seed), NuzlockeStarts.gameLabel(kind, true), old.meta.rules, at,
            system = NuzlockeStarts.systemOf(kind), gameKey = NuzlockeStarts.gameKeyOf(kind),
        )
    }

    /** The survivors a finished Genlocke leg hands on: what the ledger saved at the Champion, or who is alive if it did not. */
    fun heirsOf(id: String): List<Heir> {
        val ledger = NuzlockeTracking.loaded(id) ?: load(id) ?: return emptyList()
        return ledger.meta.heirsOut.ifEmpty {
            ledger.alive.map { Heir(it.species, it.speciesName, it.nickname, it.level, it.gender, it.shiny) }
        }
    }

    companion object {
        /** The header lives in the first lines: the format line, the run, the flags and the rules. */
        const val HEADER_LINES = 40

        /** How many times the folder was listed, for the tests: a look that found nothing new must not list it again. */
        @Volatile internal var lists = 0

        /**
         * What a session is tied to: a library game's id, or for the randomized run its game and seed. Null while
         * the run's seed is not on disk yet (PrepStore.stampKnown), which is half way through starting one.
         */
        fun bindOf(session: GameSession, runIdentity: String): String? =
            if (session.isRun) runIdentity.takeIf { PrepStore.stampKnown(it) } else session.id

        /** The bind of a randomized run that has not been made yet: its game and the seed it will be made with. */
        fun bindOfRun(romKindId: String, seed: Long): String = "$romKindId/%016x".format(seed)

        /** What [bindOfRun] writes: a game id, a slash and the seed's 16 hex digits. A library game's bind never has a slash. */
        private val RUN_BIND = Regex("[A-Za-z0-9._-]+/[0-9a-f]{16}")
    }
}

/**
 * The Nuzlocke run the Play screen is on, kept in memory and fed from the tracker (2026-09-29).
 *
 * Nothing here is set up at start-up: the first screen that asks finds the run on disk and loads it, and every
 * later ask gets the same object. That is why MainActivity is not touched. What a poll changes is saved a moment
 * later on a worker thread, whole, in the order the changes were made.
 */
object NuzlockeTracking {

    /**
     * A run being fed. A [staged] one is a staged demo's (Demo.mode): a copy of the run read from disk, fed the demo's
     * made-up encounters, catches and deaths so the panel shows the ledger at work, and never written back. The
     * player's own ledger and its copy in memory are not touched (rc32 audit P2 #29).
     */
    class Live(val store: NuzlockeStore, val ledger: NuzlockeLedger, val staged: Boolean = false) {
        val engine = NuzlockeEngine(ledger)
        val edits = NuzlockeEdits(ledger)
        /** What the panel last drew from, so the ledger can show the same area and caps. */
        @Volatile var lastSnapshot: Snapshot? = null

        /** One tracker poll. True when the ledger changed (and is then on its way to disk). */
        fun feed(snapshot: Snapshot, at: Long = System.currentTimeMillis()): Boolean {
            lastSnapshot = snapshot
            val changed = engine.update(snapshot, at)
            if (changed) saveSoon()
            return changed
        }

        /** After a change made by hand. */
        fun edited() = saveSoon()

        /**
         * The ledger is copied here, on the thread that feeds it, and written to text on the writer: the whole file
         * (up to 4,000 events) was formatted on the main thread on every change (rc32 audit P3 #39). The writer never
         * reads the live ledger, which the next poll changes: the rule of rc33 audit P0-10.
         */
        private fun saveSoon() {
            if (staged) return
            val copy = ledger.detached()
            write(store.fileFor(ledger.meta.id), wait = false) { NuzlockeText.format(copy) }
        }

        /** Writes the ledger now, after everything queued, for a caller that is about to leave (and for the tests). A staged copy has nothing to write. */
        fun saveNow(): Boolean = staged || store.save(ledger)
    }

    /** One writer, so two saves of a ledger reach the disk in the order they were made. */
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "nuzlocke-writer").apply { isDaemon = true } }

    /** Writes [text] to [file], or deletes it when [text] is null. With [wait] the answer is the write's own. */
    fun write(file: File, text: String?, wait: Boolean): Boolean = write(file, wait) { text }

    /** The same, with the text made on the writer by [make], in its turn after every write already queued. */
    fun write(file: File, wait: Boolean, make: () -> String?): Boolean {
        // A failed write is said, waited for or not: the ledger's save was ignored on a full phone (rc33 audit P0-8).
        val job = writer.submit<Boolean> {
            val text = make()
            if (text == null) file.delete()
            else SafeWrite.text(file, text).also { if (!it) SaveTrouble.report(SaveTrouble.LEDGER, SaveTrouble.LEDGER_FAILED) }
        }
        return if (wait) runCatching { job.get() }.getOrDefault(false) else true
    }

    private val live = HashMap<String, Live>()

    /** A staged demo's copies of the runs (Live.staged), apart from the real ones; guarded by [live]'s lock. */
    private val stagedLive = HashMap<String, Live>()

    // What the last look at the disk found, so a poll every few hundred milliseconds does not re-read the library.
    private var checkedAt = 0L
    private var checkedFor: String? = null
    private var checkedBind: String? = null
    private var checkedStamp = -1L
    private var found: Live? = null
    private var prep: PrepStore? = null
    private var prepKey: String? = null

    /**
     * One tracker state for the run in play, from a caller that has the state and no panel on screen (2026-09-29).
     * The panel feeds the ledger from what it draws, which leaves a gap while the tracker is hidden (landscape's
     * "Hide tracker") because the game goes on without it. PlayScreen makes the state in its own polling loop
     * whether or not anything shows it, so a call there closes the gap. A state fed twice changes nothing, so the
     * panel and this can both run. Main thread only. True when the ledger changed.
     */
    fun observe(filesDir: File, state: TrackerState?, at: Long = System.currentTimeMillis()): Boolean {
        if (state?.nuz == null) return false
        // The Play screen's polling loop calls this: whatever a ledger or an adapter throws must never end that loop.
        return runCatching {
            val live = current(filesDir, at) ?: return@runCatching false
            val snapshot = NuzlockeAdapters.snapshot(state) ?: return@runCatching false
            live.feed(snapshot, at)
        }.getOrDefault(false)
    }

    /** The same for a DS game (2026-09-30): the DS tracker's own state, through its own adapter. */
    fun observeNds(filesDir: File, state: NdsTrackerState?, at: Long = System.currentTimeMillis()): Boolean {
        if (state == null || !state.located) return false
        return runCatching {
            val live = current(filesDir, at) ?: return@runCatching false
            val snapshot = NdsNuzlocke.snapshot(state) ?: return@runCatching false
            live.feed(snapshot, at)
        }.getOrDefault(false)
    }

    /** Bumped when a run starts or is replaced, so the next ask looks at the disk again. */
    @Volatile private var generation = 0
    private var checkedGeneration = -1

    fun changed() { generation++ }

    /** The in-memory copy of a run, when one is loaded. */
    fun loaded(id: String): NuzlockeLedger? = synchronized(live) { live[id]?.ledger }

    /** Lets go of a run's in-memory copy (it was replaced or deleted). */
    fun forget(id: String) {
        synchronized(live) { live.remove(id); stagedLive.remove(id) }
        if (found?.ledger?.meta?.id == id) found = null
        generation++
    }

    /**
     * The run for [bind] in [store], loading it the first time. Null when there is none. While a staged demo is set
     * (Demo.mode) it is a staged copy (Live.staged), so what the demo feeds never reaches the player's run.
     */
    fun liveFor(store: NuzlockeStore, bind: String): Live? {
        val entry = store.current(bind) ?: return null
        val staged = Demo.mode != null
        synchronized(live) {
            val lives = if (staged) stagedLive else live
            lives[entry.header.id]?.let { return it }
            val ledger = store.load(entry.header.id) ?: return null
            return Live(store, ledger, staged).also { lives[entry.header.id] = it }
        }
    }

    /** The run for a run id, whatever game it is on, for a screen that lists them. */
    fun liveById(store: NuzlockeStore, id: String): Live? {
        synchronized(live) {
            live[id]?.let { return it }
            val ledger = store.load(id) ?: return null
            return Live(store, ledger).also { live[id] = it }
        }
    }

    /**
     * The run the Play screen is on, for the game it has open, or null when that game has no Nuzlocke. Looks at
     * the disk at most every two seconds, and again at once when a run starts.
     *
     * Each look works out the game in play (a few small files), but reads the ledgers' headers only when that game,
     * the generation or the ledger folder changed since the last: it read every ledger on the phone every two seconds
     * of play, on the main thread, a cost that grew with every Nuzlocke played (rc32 audit P3 #39).
     */
    fun current(filesDir: File, now: Long = System.currentTimeMillis()): Live? {
        val key = filesDir.path
        if (checkedFor == key && checkedGeneration == generation && now - checkedAt < RECHECK_MS) return found
        val gen = generation
        checkedAt = now
        val p = prep.takeIf { prepKey == key } ?: PrepStore(filesDir).also { prep = it; prepKey = key }
        val bind = runCatching { NuzlockeStore.bindOf(p.session(), p.runIdentity()) }.getOrNull()
        val store = NuzlockeStore(filesDir)
        // The folder's own time moves when a ledger is added, removed or replaced (SafeWrite renames), so a ledger put
        // there by anything else (a restore) is still seen within a look.
        val stamp = store.dir.lastModified()
        if (checkedFor == key && checkedGeneration == gen && checkedBind == bind && checkedStamp == stamp) return found
        checkedFor = key; checkedGeneration = gen; checkedBind = bind; checkedStamp = stamp
        found = bind?.let { liveFor(store, it) }
        return found
    }

    /**
     * Whether the last look found a Nuzlocke run for the game in Play (2026-09-30), for a button that has no files
     * folder to ask with. The Play screen's polling loop looks every two seconds, so it is never staler than that.
     */
    fun inPlay(): Boolean = found != null

    const val RECHECK_MS = 2_000L

    /** Forgets everything in memory, for the tests. */
    internal fun reset() {
        synchronized(live) { live.clear(); stagedLive.clear() }
        found = null; checkedFor = null; checkedGeneration = -1; prep = null; prepKey = null; checkedBind = null; checkedStamp = -1L
    }
}
