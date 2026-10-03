package com.ironmonone.app

import com.ironmonone.tracker.TrackerState
import com.ironmonone.tracker.nds.NdsTrackerState
import java.io.File

/**
 * Whether a run began from the last run's in-game save with its team (rc33 audit P1 #42, #43). The save stays on a new
 * seed in every game (RunSaves, Blake 2026-09-30), and Continue on the title screen brings its team into the new seed;
 * the run's record must say so, or Your stats, NEW BEST and the streak count the run as clean. The write that did
 * (KEPT_SAVE) went when the save stopped being set aside, and nothing wrote it after.
 *
 * The app reads only a Gen 3 save's party, but it sees every party: each run's members are kept here by the id the
 * Nuzlocke ledger gives a Pokemon (personality value; a Game Boy Pokemon's trainer id and DVs), one file per game as
 * the in-game save is. A new run's first party is checked against the last run's: New Game shows its starter first,
 * which the last run never had, and Continue shows the kept team. A match goes on the new run's record, once.
 */
internal object KeptSave {
    private var cachedFile: File? = null
    private var cachedAttempt = -1
    private val cachedIds = LinkedHashSet<Long>()

    fun file(filesDir: File, game: String): File = File(filesDir, "saves/party-ids/$game.txt")

    /**
     * A Game Boy or GBA run's poll. [attempt] is the run's number as Play holds it, read once per run: reading it here
     * opened its files on every poll (rc32 audit P3 #55). It also keeps how far the run has got (RunProgress, rc32
     * audit P3 #58).
     */
    fun observe(store: PrepStore, session: GameSession, s: TrackerState?, attempt: Int) {
        RunProgress.observe(store, session, s)
        if (s == null || s.unreadable || !session.isRun || Demo.mode != null) return
        val generation = session.kind?.generation?.number ?: 3
        observe(store, session, s.party.filter { !it.mon.isEgg }.map { BstRule.keyOf(it.mon, generation) }, attempt)
    }

    /** A DS run's poll, and its progress as well. */
    fun observe(store: PrepStore, session: GameSession, s: NdsTrackerState?, attempt: Int) {
        RunProgress.observe(store, session, s)
        if (s == null || !s.located || !session.isRun || Demo.mode != null) return
        observe(store, session, s.party.filter { !it.mon.isEgg }.map { it.mon.pid }, attempt)
    }

    private fun observe(store: PrepStore, session: GameSession, ids: List<Long>, attempt: Int) {
        val game = session.kind?.id ?: return
        if (seen(store.files, game, attempt, ids)) store.runEvents(session)?.add(RunEvents.Kind.KEPT_SAVE, "in-game save", "the last run's team")
    }

    /**
     * One look at attempt [attempt]'s party, [ids], in [game]. True once, on the new run's first party, when it holds a
     * Pokemon of the last run's. A party that is empty (the title screen, the intro) waits, and keeps the last run's ids.
     */
    @Synchronized
    internal fun seen(filesDir: File, game: String, attempt: Int, ids: List<Long>): Boolean {
        val f = file(filesDir, game)
        if (f != cachedFile) load(f)
        if (attempt != cachedAttempt) {
            if (ids.isEmpty()) return false
            val kept = cachedIds.isNotEmpty() && ids.any { it in cachedIds }
            cachedAttempt = attempt
            cachedIds.clear(); cachedIds += ids
            write(f)
            return kept
        }
        if (ids.any { cachedIds.add(it) }) write(f)
        return false
    }

    private fun load(f: File) {
        cachedFile = f; cachedAttempt = -1; cachedIds.clear()
        val lines = runCatching { DiskWriter.read(f)?.lines() }.getOrNull() ?: return
        cachedAttempt = lines.firstOrNull()?.removePrefix("attempt ")?.trim()?.toIntOrNull() ?: -1
        lines.drop(1).mapNotNullTo(cachedIds) { it.trim().toLongOrNull() }
    }

    /** Whole or not at all (SafeWrite), off the poll loop's thread (DiskWriter): it was rewritten in place (rc32 audit P2 #65). */
    private fun write(f: File) {
        DiskWriter.write(f, (listOf("attempt $cachedAttempt") + cachedIds.map { it.toString() }).joinToString("\n", postfix = "\n"))
    }

    /** Forgets the cache, for a test that rewrites the file under it. */
    @Synchronized
    internal fun reset() { cachedFile = null; cachedAttempt = -1; cachedIds.clear() }
}
