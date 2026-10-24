package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ironmonone.core.Generation
import java.io.File

// The BstRule object (its lines, breaks, wildBreaks) is in engine-hns (BstLines.kt), so the Heart & Soul randomizer draws
// the same line in KaizoCore PC. What needs the app is here, as extensions called the same way: BstRule.ownBreaks(...).

/**
 * The X on your own Pokemon; with no record of what it joined as ([joined] unread), none. [name], the species as the
 * tracker names it, is asked only where the run has a [Lines.legendary] line: a legendary past it keeps the X even
 * when it evolved into that form.
 */
fun BstRule.ownBreaks(bst: Int?, lines: BstRule.Lines?, joined: JoinedForms?, pid: Long, species: Int, name: String? = null): Boolean =
    lines != null && joined != null && (breaks(bst, lines.own, joined.joinedAs(pid, species), species) || legendaryBreaks(bst, lines, name))

/**
 * A legendary (FavoriteRules.LEGENDARY, a form by its species) at or past the run's [Lines.legendary] line, whatever
 * it joined as: MaxDex's "except for 601+ BST legendaries". Saying it evolved does not clear this X.
 */
fun BstRule.legendaryBreaks(bst: Int?, lines: BstRule.Lines?, name: String?): Boolean {
    val line = lines?.legendary ?: return false
    return bst != null && bst >= line && name != null && FavoriteRules.isLegendary(name)
}

/** What follows [mon] through an evolution: its personality value, or on a Game Boy ([generation] 1 or 2) its trainer id and DVs. */
fun BstRule.keyOf(mon: com.ironmonone.tracker.PokemonDecoder.Mon, generation: Int): Long =
    if (generation < 3) com.ironmonone.tracker.Gen12Nuzlocke.partyId(mon) else mon.pid

/**
 * The species each of your Pokemon joined the run as, by its personality value, which an evolution keeps: so a Dratini
 * that grew into a Dragonite keeps no X, and a Zekrom taken from the lab keeps its X. One line a Pokemon ("pid
 * species") under an "attempt N" header, beside the run's marks, written the first time the tracker reads it in the
 * party. A new attempt starts the file over.
 */
class JoinedForms(private val file: File, private val attempt: Int) {
    companion object {
        /** Recorded for a Pokemon the player says evolved into its form: no species matches it, so no X. */
        const val EVOLVED = 0
    }

    private val joined = HashMap<Long, Int>()
    private var headed = false

    init {
        runCatching {
            if (file.exists()) {
                val lines = file.readLines()
                if (lines.firstOrNull()?.trim() == header()) {
                    headed = true
                    lines.drop(1).forEach { line ->
                        val p = line.trim().split(' ')
                        if (p.size == 2) p[0].toLongOrNull()?.let { pid -> p[1].toIntOrNull()?.let { joined[pid] = it } }
                    }
                }
            }
        }
    }

    private fun header() = "attempt $attempt"

    /**
     * The player's word that [pid] came into its form by evolving (a tap on its red BST): it keeps no X. For a run the
     * app first read after the Pokemon had already evolved, which it cannot tell from one caught that way.
     */
    @Synchronized
    fun markEvolved(pid: Long) {
        if (pid == 0L) return
        joined[pid] = EVOLVED
        runCatching {
            file.parentFile?.mkdirs()
            if (!headed) { file.writeText(header() + "\n"); headed = true }
            file.appendText("$pid $EVOLVED\n")
        }
    }

    /** The species [pid] joined as, recording [species] the first time it is seen. */
    @Synchronized
    fun joinedAs(pid: Long, species: Int): Int {
        if (pid == 0L || species <= 0) return species
        joined[pid]?.let { return it }
        joined[pid] = species
        runCatching {
            file.parentFile?.mkdirs()
            if (!headed) { file.writeText(header() + "\n"); headed = true }
            file.appendText("$pid $species\n")
        }
        return species
    }
}

/**
 * What a tap on a red BST shows: the rule, and for your own Pokemon a way to say it evolved into this form (the app
 * cannot tell for a run it first read after the evolution). States the rule and nothing more. [legendary], the run's
 * [BstRule.Lines.legendary] line (MaxDex), adds that a legendary past it is not allowed even by evolving.
 */
@Composable
internal fun BstRuleSheet(bst: Int, line: Int, own: Boolean, onEvolved: () -> Unit, legendary: Int? = null, onDismiss: () -> Unit) {
    InfoSheet("BST $bst", onDismiss) {
        InfoParagraph(null, "This run allows no Pokemon at BST $line or above, unless it evolved into that form." +
            (legendary?.let { " " + BstRule.legendarySays(it) } ?: ""))
        if (own) {
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(10.dp))
            PcButton("IT EVOLVED INTO THIS", spoken = "It evolved into this form: clear the mark") { onEvolved() }
        }
    }
}

/** The lines for the game in Play: a Kaizo IronMON run, on any console; null otherwise. MaxDex takes its own (BstRule). */
@Composable
fun bstLinesInPlay(attempt: Int): BstRule.Lines? {
    val context = LocalContext.current
    return remember(attempt) {
        runCatching {
            val filesDir = context.applicationContext.filesDir
            val store = PrepStore(filesDir)
            val session = store.session()
            if (PlayRules.kind(session, filesDir) != PlayRules.Kind.IRONMON) null
            // Heart & Soul's line follows the run's pool: 600 for Nat. Dex, the vanilla line for Gen 1-3 (HnsEngine.bstLine).
            else BstRule.lines(FavoriteBall.modeOf(store), session.kind?.let { HnsPool.rulesNatDex(it, filesDir) } == true,
                gen1 = session.kind?.generation == Generation.GB1,
                maxDex = session.kind?.isMaxDex == true, hns = session.kind?.isHns == true)
        }.getOrNull()
    }
}

/** The run's [JoinedForms], beside its marks; null without [lines] or where the session cannot be read. */
@Composable
fun joinedFormsInPlay(attempt: Int, lines: BstRule.Lines?): JoinedForms? {
    val context = LocalContext.current
    return remember(attempt, lines) {
        if (lines == null) null
        else runCatching {
            val store = PrepStore(context.applicationContext.filesDir)
            JoinedForms(File(store.marksFile(store.session()).parentFile, "joined.txt"), attempt)
        }.getOrNull()
    }
}
