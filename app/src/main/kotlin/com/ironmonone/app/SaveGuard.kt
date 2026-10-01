package com.ironmonone.app

import com.ironmonone.core.Platform
import com.swordfish.libretrodroid.GLRetroView
import java.io.File

/**
 * Loading a state never changes the in-game save (Blake, 2026-09-30: "Auto save won't override an in-game save
 * or a save slot save?").
 *
 * GBA (mGBA) never touched it: its retro_unserialize loads SAVESTATE_RTC only. Game Boy (gambatte) keeps cartridge
 * RAM inside every state; libretrodroid's unserializeState now puts the battery save back after any load.
 * melonDS (DS) keeps its own .sav and writes the state's copy of the save memory to it a moment after a state loads:
 * on the emulator, heartgold-u.sav marked by hand lost its mark within 2 s of a Resume (2026-09-30). For DS this keeps
 * the .sav as it was before the load: the file is read first, and the first write to it within [WINDOW_MS] after a
 * load is undone. A copy of the kept save stays beside it as <name>.sav.before-load. The window is short on purpose:
 * an in-game save the player makes after loading an older state is theirs, and it takes longer than that to reach.
 * melonDS keeps the state's save in memory; the game writes it to the file only when the player saves in the game.
 *
 * It sits on GLRetroView's StateLoadListener, so every way a state is loaded is covered: a slot, Undo, rewind, a
 * restore point, the battle retry and the crash resume.
 */
internal object SaveGuard {
    const val WINDOW_MS = 4_000L
    const val STEP_MS = 100L

    /** The .sav melonDS writes for [rom] in the saves folder: the ROM's name with .sav. */
    fun dsSaveFile(savesDir: File, rom: File) = File(savesDir, rom.nameWithoutExtension + ".sav")

    fun listener(platform: Platform, savesDir: File, rom: File): GLRetroView.StateLoadListener? =
        if (platform == Platform.NDS) DsWatch(dsSaveFile(savesDir, rom)) else null

    /**
     * The DS watch for one game. Rewind loads states one after another, so a load while an earlier one is still
     * being watched keeps the save from before the first. [clock], [sleep] and [startWorker] are the test's to replace.
     */
    class DsWatch(
        private val save: File,
        private val clock: () -> Long = System::currentTimeMillis,
        private val sleep: (Long) -> Unit = { Thread.sleep(it) },
        private val startWorker: (Runnable) -> Unit = { r -> Thread(r, "save-guard").apply { isDaemon = true }.start() },
    ) : GLRetroView.StateLoadListener {
        private val lock = Any()
        private var kept: ByteArray? = null
        private var deadline = 0L
        private var working = false

        /** A copy of the kept save, so even a write this misses leaves the newer save on the phone. */
        val backup: File get() = File(save.path + ".before-load")

        override fun beforeStateLoad() {
            synchronized(lock) {
                if (kept != null && clock() <= deadline) return
                kept = runCatching { save.takeIf { it.isFile }?.readBytes() }.getOrNull()
            }
        }

        override fun afterStateLoad(loaded: Boolean) {
            val start: Boolean
            synchronized(lock) {
                val k = kept
                if (!loaded || k == null) return
                deadline = clock() + WINDOW_MS
                SafeWrite.bytes(backup, k)
                start = !working
                working = true
            }
            if (start) startWorker(Runnable { watch() })
        }

        /** Until the deadline: the first time the file stops matching the kept save, write the kept save back. */
        internal fun watch() {
            try {
                while (true) {
                    val k: ByteArray
                    synchronized(lock) {
                        if (clock() > deadline) { kept = null; return }
                        k = kept ?: return
                    }
                    val now = runCatching { save.readBytes() }.getOrNull()
                    if (now != null && !now.contentEquals(k)) {
                        SafeWrite.bytes(save, k)
                        synchronized(lock) { deadline = 0L; kept = null }
                        return
                    }
                    sleep(STEP_MS)
                }
            } finally {
                synchronized(lock) { working = false }
            }
        }
    }
}
