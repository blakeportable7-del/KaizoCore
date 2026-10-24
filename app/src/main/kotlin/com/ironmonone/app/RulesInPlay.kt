package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The rules for the game in Play: a Kaizo IronMON run on any console; null otherwise, as for the BST X. */
@Composable
fun moveRulesInPlay(attempt: Int): MoveRule.Rules? {
    val context = LocalContext.current
    return remember(attempt) {
        runCatching {
            val filesDir = context.applicationContext.filesDir
            val store = PrepStore(filesDir)
            val session = store.session()
            val kind = session.kind
            if (PlayRules.kind(session, filesDir) != PlayRules.Kind.IRONMON || kind == null) null
            else MoveRule.rules(FavoriteBall.modeOf(store), kind.family, HnsPool.rulesNatDex(kind, filesDir), kind.baseId ?: kind.id)
        }.getOrNull()
    }
}

/**
 * The run in Play for [RuleMarks]: a Kaizo IronMON run on any console, with its mode; null otherwise, as for the BST X.
 * Its evolutions are the seed's, from the run's randomizer log once read (off the main thread).
 */
@Composable
fun ruleRunInPlay(attempt: Int): RuleMarks.Run? {
    val context = LocalContext.current
    val found = remember(attempt) {
        runCatching {
            val filesDir = context.applicationContext.filesDir
            val store = PrepStore(filesDir)
            val session = store.session()
            val kind = session.kind
            val mode = FavoriteBall.modeOf(store)
            if (PlayRules.kind(session, filesDir) != PlayRules.Kind.IRONMON || kind == null || mode == null) null
            else Triple(RuleMarks.Run(mode, HnsPool.rulesNatDex(kind, filesDir), kind.family), store, kind)
        }.getOrNull()
    }
    val evolves = androidx.compose.runtime.produceState<((String) -> Boolean?)?>(null, found) {
        val (_, store, kind) = found ?: return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { store.currentRunLogFor(kind)?.let { RandomizerLog.parse(it) }?.let { RuleMarks.seedEvolutions(it) } }.getOrNull()
        }
    }.value
    return found?.first?.copy(evolves = evolves)
}
