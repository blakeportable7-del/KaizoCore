package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The run's notes for Play (StatMarks), read on Dispatchers.IO (rc35 follow-up N #17). They were built in Play's
 * composition, nine note files read on the main thread each time Play opened. Null until they are read, a moment on
 * entry, and Play composes nothing below them until then; one per note file, so a run keeps its instance through New
 * Run (which clears it) as it did when it was remembered by the session.
 */
@Composable
internal fun rememberStatMarks(store: PrepStore, session: GameSession): StatMarks? {
    val file = store.marksFile(session)
    val loaded by produceState<Pair<File, StatMarks>?>(null, file) {
        value = file to withContext(Dispatchers.IO) { StatMarks(file) }
    }
    return loaded?.takeIf { it.first == file }?.second
}
