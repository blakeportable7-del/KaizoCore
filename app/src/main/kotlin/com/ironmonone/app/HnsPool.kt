package com.ironmonone.app

import com.ironmonone.app.engine.HnsEngine
import com.ironmonone.app.engine.Randomizers
import java.io.File

/**
 * Which Pokemon a Heart & Soul run is randomized from (Blake, 2026-10-05: "one version you can play vanilla, one you can
 * play natl dex"): VANILLA is the hack's own Gen 1-3 scope, NATDEX every species through Gen 9. It is the player's
 * choice on the Kaizo IronMON and Nuzlocke screens, kept in prep/hns-pool.txt, and every new Heart & Soul run takes the
 * pool chosen then (Randomizers.hnsPoolNow). The run in play keeps its own: its recipe's engine id names it
 * ("hns-1.0 natdex", Randomizers.engineId), so changing the choice never changes what a run in progress is.
 */
object HnsPool {
    /** What the screens show for each pool, in the order they show them. */
    val LABELS: List<Pair<HnsEngine.Pool, String>> = listOf(
        HnsEngine.Pool.VANILLA to "Vanilla (Gen 1-3)",
        HnsEngine.Pool.NATDEX to "Nat. Dex (Gen 1-9)",
    )

    /** One plain line under the choice. */
    const val LINE = "Vanilla randomizes from the Pokémon of Gens 1 to 3, as Heart & Soul ships. " +
        "Nat. Dex randomizes from every Pokémon through Gen 9."

    /** A run that has not been told otherwise is Nat. Dex: the RSE NatDex v1.2 modes it plays are written for it. */
    val DEFAULT = HnsEngine.Pool.NATDEX

    fun label(pool: HnsEngine.Pool): String = LABELS.first { it.first == pool }.second

    private fun file(filesDir: File) = File(filesDir, "prep/hns-pool.txt")

    /** The pool the next Heart & Soul run takes. */
    fun chosen(filesDir: File): HnsEngine.Pool = runCatching {
        file(filesDir).takeIf { it.isFile }?.readText()?.trim()?.let { t -> HnsEngine.Pool.entries.firstOrNull { it.name == t } }
    }.getOrNull() ?: DEFAULT

    fun choose(filesDir: File, pool: HnsEngine.Pool) {
        file(filesDir).parentFile?.mkdirs()
        SafeWrite.text(file(filesDir), pool.name)
    }

    /** The pool of the Heart & Soul run in play, from its recipe; null when the run in play is not one or has no recipe. */
    fun ofRun(store: PrepStore): HnsEngine.Pool? = runCatching { Randomizers.hnsPoolOf(NextRun.currentRecipe(store)?.first?.engine) }.getOrNull()

    /**
     * Whether [kind]'s run in play takes the Nat. Dex rules where a rule differs (BstRule's 600 line, the Nat. Dex HM and
     * banned-ability exceptions): a Nat. Dex build always, Heart & Soul when its run is of the Nat. Dex pool, where it
     * plays the Emerald Nat. Dex rules; of the Vanilla pool, Emerald's.
     */
    fun rulesNatDex(kind: com.ironmonone.core.RomKind, filesDir: File): Boolean = if (kind.isHns) natDexRun(filesDir) else kind.isNatDex

    /** Whether the run in play takes the Nat. Dex rules where a rule differs by pool (BstRule's 600 line). */
    fun natDexRun(filesDir: File): Boolean = (ofRun(PrepStore(filesDir)) ?: chosen(filesDir)) == HnsEngine.Pool.NATDEX
}

/**
 * Whether the game in Play is Heart & Soul (the KaizoCore build, a run or the Library copy), as MaxDexInfo's
 * maxDexInPlay asks for MaxDex: TrackerPanel numbers its species by it.
 */
@androidx.compose.runtime.Composable
fun hnsInPlay(attempt: Int): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(attempt) {
        runCatching { PrepStore(context.applicationContext.filesDir).session().kind?.isHns == true }.getOrDefault(false)
    }
}
