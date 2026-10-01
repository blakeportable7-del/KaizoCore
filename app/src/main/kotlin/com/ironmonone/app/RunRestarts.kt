package com.ironmonone.app

import java.io.File

/**
 * File > Restart on a run (2026-09-30, IronMON rules check R11): the game goes back to its last in-game save, as a
 * soft reset does, and the app knows it happened, so the run's log says so (RunEvents RESET). The death card and the
 * share line then read "1 restart" beside the state loads. Outside a run there is no log to write.
 */
internal object RunRestarts {
    fun log(filesDir: File, at: Long = System.currentTimeMillis()) {
        runCatching {
            val store = PrepStore(filesDir)
            store.runEvents(store.session())?.add(RunEvents.Kind.RESET, "restart", "File > Restart", at)
        }
    }
}
