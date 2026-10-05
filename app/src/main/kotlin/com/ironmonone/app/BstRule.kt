package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ironmonone.core.Generation
import java.io.File

/**
 * Kaizo's "No Uber Strong Mons": "No using 599+ BST Pokémon. If your Pokémon evolves into something with 600 BST or
 * higher that is ok" (the IronMON rules gist, read 2026-10-02). Blake, the same day, playing Black 2 Kaizo with a
 * Zekrom of 679 from the lab: "there is no indication that this is against the rules", then "Or BSTX red color base
 * stat total number". The tracker states the rule and nothing more (the IronMON dev team, 2026-09-30): an X after BST
 * and the number in red, on your Pokemon while it is still the species it joined the run as, and on a wild one, which
 * would join as it is. A trainer's Pokemon cannot join, so it gets none.
 *
 * Which modes, from ironmon-ref/IRONMON-MODES-REPORT.md: Kaizo and the modes built on it (Survival, Super Kaizo,
 * Kaizo Doubles, Survival Revival) from 599; their Nat. Dex versions, which "also ban 600+ BST", from 600; Evo Kaizo,
 * whose lab Pokemon are legal "up to 600 BST", from 601 on your own and Kaizo's line on a wild one. Standard, Ultimate,
 * Journey and Chaos Kaizo (a ban list of its own) draw no line. Red, Blue and Yellow ban "BST over 480" by a per-game
 * rule (Dragonite, the three birds, Mew, Mewtwo), so their line is 481; Gold, Silver and Crystal take Kaizo's. A Game Boy
 * Pokemon has no personality value, so it is followed by its trainer id and DVs instead, which an evolution keeps
 * (Gen12Nuzlocke.partyId, as the Nuzlocke ledger does).
 *
 * MaxDex is the one build held to the Nat. Dex page's older section, "v1.0.0 to v1.1.3 only", the version it is built on
 * (Blake, 2026-10-03: "Max dex is allowed a bst 600 pokemon"); see [maxDexLines]. KaizoCore's Nat. Dex games are 1.2.1
 * and keep the v1.2.0+ lines above.
 */
object BstRule {
    private val FROM_KAIZO = setOf("kaizo", "survival", "superkaizo", "kaizodoubles", "survivalrevival")

    /**
     * The BST from which a Pokemon is banned: [own] for yours, [wild] for one you could catch, and [legendary] for a
     * legendary of yours however it came to its form, evolving included (MaxDex's line; null everywhere else, where an
     * evolution into any form is fine).
     */
    data class Lines(val own: Int, val wild: Int, val legendary: Int? = null)

    /**
     * The run's lines, by its [mode] ("kaizo", "evokaizo"); null where it draws none. [gen1]: Red, Blue or Yellow.
     * [maxDex]: MaxDex 1.0, which follows the Nat. Dex 1.1.3 rules ([maxDexLines]) though it is a Nat. Dex build too.
     */
    fun lines(mode: String?, natDex: Boolean, gen1: Boolean = false, maxDex: Boolean = false): Lines? {
        if (maxDex) return maxDexLines(mode)
        val kaizo = when { gen1 -> 481; natDex -> 600; else -> 599 }
        return when {
            mode == "evokaizo" -> Lines(own = 601, wild = kaizo)
            mode in FROM_KAIZO -> Lines(kaizo, kaizo)
            else -> null
        }
    }

    /**
     * MaxDex's lines, from the Nat. Dex Ruleset Changes page's "v1.0.0 to v1.1.3 only" section (tools/upr-settings/
     * community/Nat.-Dex-Ruleset-Changes.md): "BST limit for Kaizo, Survival and Super Kaizo is 599, or 600 for starter
     * Pokemon, including legendaries. 600+ BST mons may be obtained through evolution, except for 601+ BST legendaries."
     * So a starter of 600 keeps no X and one of 601 has it, a wild one is legal under 600, and evolving past 600 is fine
     * unless the Pokemon becomes a legendary of 601 or more. The tracker cannot tell your starter from a Pokemon you were
     * given, so every Pokemon of yours takes the starter's line, as Evo Kaizo's lab line is drawn above; MaxDex's own
     * randomizer keeps every Pokemon of 600 or more out of the wild. Standard and Ultimate: "the BST limit is 640
     * inclusive, and the Legendary evolution exception does not apply" ("acquire a legal Arceus by evolving a
     * Vigoroth"). The section names no Evo Kaizo, which keeps the Nat. Dex line. MaxDex ships Kaizo only.
     */
    private fun maxDexLines(mode: String?): Lines? = when {
        mode in FROM_KAIZO -> Lines(own = 601, wild = 600, legendary = 601)
        mode == "evokaizo" -> Lines(own = 601, wild = 600)
        mode == "standard" || mode == "ultimate" -> Lines(own = 641, wild = 641)
        else -> null
    }

    /** At or past [line], and still the species it joined as (a [joinedAs] of null: a wild one, joining as it is). */
    fun breaks(bst: Int?, line: Int?, joinedAs: Int?, species: Int): Boolean =
        bst != null && bst > 0 && line != null && bst >= line && (joinedAs == null || joinedAs == species)

    /**
     * The X on your own Pokemon; with no record of what it joined as ([joined] unread), none. [name], the species as the
     * tracker names it, is asked only where the run has a [Lines.legendary] line: a legendary past it keeps the X even
     * when it evolved into that form.
     */
    fun ownBreaks(bst: Int?, lines: Lines?, joined: JoinedForms?, pid: Long, species: Int, name: String? = null): Boolean =
        lines != null && joined != null && (breaks(bst, lines.own, joined.joinedAs(pid, species), species) || legendaryBreaks(bst, lines, name))

    /**
     * A legendary (FavoriteRules.LEGENDARY, a form by its species) at or past the run's [Lines.legendary] line, whatever
     * it joined as: MaxDex's "except for 601+ BST legendaries". Saying it evolved does not clear this X.
     */
    fun legendaryBreaks(bst: Int?, lines: Lines?, name: String?): Boolean {
        val line = lines?.legendary ?: return false
        return bst != null && bst >= line && name != null && FavoriteRules.isLegendary(name)
    }

    /** The X on a wild opponent. */
    fun wildBreaks(bst: Int?, lines: Lines?): Boolean = breaks(bst, lines?.wild, null, 0)

    /** What the rule sheet adds where a run has a [Lines.legendary] line. */
    fun legendarySays(line: Int): String = "A legendary at BST $line or above is not allowed even then."

    /** What follows [mon] through an evolution: its personality value, or on a Game Boy ([generation] 1 or 2) its trainer id and DVs. */
    fun keyOf(mon: com.ironmonone.tracker.PokemonDecoder.Mon, generation: Int): Long =
        if (generation < 3) com.ironmonone.tracker.Gen12Nuzlocke.partyId(mon) else mon.pid
}

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
                maxDex = session.kind?.isMaxDex == true)
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
