package com.ironmonone.app

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One background writer for the small files play keeps changing: the run's tracker notes (StatMarks), the time played
 * (RunClock), the tracker's settings, colours and image settings, the DS past runs and tourney scores. Each was written
 * through SafeWrite on the main thread, and SafeWrite syncs to disk: at a battle's start, behind the auto-save's state
 * and the next run being written, those syncs held up the thread that draws the game and takes the on-screen pad's
 * touches (rc32 audit P2 #90, P3 #60, P3 #69).
 *
 * The text is made where the data lives (the main thread, for the notes) and handed over here. A file handed over
 * again before it was written is written once, with the newest text. Every write is SafeWrite's: a temp file beside
 * it, synced, then one rename, so the file is whole or as it was.
 *
 * Whatever reads one of these files back reads it through [read], which answers with the newest text handed over
 * before the disk; whatever copies them elsewhere (a backup, a saved attempt) waits for the queue first ([drain]);
 * whatever deletes or empties them (a new run's notes) has the queued writes forgotten first ([forget]).
 */
object DiskWriter {
    private class Entry(val file: File, val text: String) {
        /** Set by [forget] while the entry is being written: its rename must not happen. Read under [commit]. */
        @Volatile var cancelled = false
    }

    /** Guards the queue; never held while anything is written, so a save from the main thread never waits on the disk. */
    private val lock = Object()
    /** Held across a write's last step, the rename, and taken by [forget] to wait one out. */
    private val commit = Object()
    /** Waiting, by path: only the newest text. */
    private val queued = HashMap<String, Entry>()
    /** Being written now, by path. */
    private val writing = HashMap<String, Entry>()
    /** A restore is putting files back: nothing is written until [release]. */
    private var held = false

    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "disk-writer").apply { isDaemon = true } }

    /** Files replaced so far, for a test counting what a burst of saves became. */
    internal val written = AtomicInteger()

    /** Queues [text] as the whole of [file]. Returns at once. */
    fun write(file: File, text: String) {
        val key = file.absolutePath
        synchronized(lock) {
            val first = queued.put(key, Entry(file, text)) == null
            if (first && !held) thread.execute { writeNext(key) }
        }
    }

    /** What [file] holds once the queue reaches it: the newest text handed over for it, else what is on disk, else null. */
    fun read(file: File): String? {
        synchronized(lock) {
            val key = file.absolutePath
            (queued[key] ?: writing[key]?.takeUnless { it.cancelled })?.let { return it.text }
        }
        return runCatching { if (file.isFile) file.readText() else null }.getOrNull()
    }

    /**
     * Drops what is queued for [files], and stops a write of one of them already under way before its rename, for a
     * caller about to delete or empty them: a write landing after that brought the old notes back. Returns once none
     * of them is being written, or after [timeoutMs].
     */
    fun forget(files: Collection<File>, timeoutMs: Long = 2000) {
        val keys = files.mapTo(HashSet()) { it.absolutePath }
        synchronized(lock) {
            keys.forEach { queued.remove(it) }
            writing.forEach { (k, e) -> if (k in keys) e.cancelled = true }
        }
        // A rename already under way finishes before the caller empties the files; any later one sees the mark.
        synchronized(commit) {}
        synchronized(lock) {
            val until = System.nanoTime() + timeoutMs * 1_000_000
            while (writing.keys.any { it in keys }) {
                val left = (until - System.nanoTime()) / 1_000_000
                if (left <= 0) break
                lock.wait(left)
            }
        }
    }

    /** Waits, [timeoutMs] at most, for everything handed over before it. True when all of it is on disk. */
    fun drain(timeoutMs: Long = 3000): Boolean {
        runCatching { thread.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS) }
        return synchronized(lock) { queued.isEmpty() && writing.isEmpty() }
    }

    /**
     * A restore is about to put files back (RestoreGate): nothing more is written over them, and a write under way is
     * waited for, [timeoutMs] at most. What is handed over meanwhile is kept, and written if [release] comes instead of
     * the restart.
     */
    fun hold(timeoutMs: Long = 3000) {
        synchronized(lock) {
            held = true
            val until = System.nanoTime() + timeoutMs * 1_000_000
            while (writing.isNotEmpty()) {
                val left = (until - System.nanoTime()) / 1_000_000
                if (left <= 0) break
                lock.wait(left)
            }
        }
    }

    /** The restore did not go through: what was kept is written. */
    fun release() {
        synchronized(lock) {
            if (!held) return
            held = false
            queued.keys.toList().forEach { key -> thread.execute { writeNext(key) } }
        }
    }

    private fun writeNext(key: String) {
        val e = synchronized(lock) {
            if (held) return
            val e = queued.remove(key) ?: return
            writing[key] = e
            e
        }
        var failed = false
        try {
            val done = SafeWrite.write(e.file, e.text.toByteArray(Charsets.UTF_8)) { replace ->
                synchronized(commit) { if (e.cancelled) false else { replace(); true } }
            }
            if (done) written.incrementAndGet()
            failed = !done && !e.cancelled
        } finally {
            synchronized(lock) { writing.remove(key); lock.notifyAll() }
        }
        // Said, as every other save that fails with no screen waiting is (SaveTrouble, rc33 audit P0-8).
        if (failed) SaveTrouble.report(SaveTrouble.NOTES, SaveTrouble.NOTES_FAILED)
    }
}
