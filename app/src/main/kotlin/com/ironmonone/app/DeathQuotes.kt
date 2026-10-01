package com.ironmonone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlin.random.Random

/**
 * The player's own game over lines (2026-09-30). The idea is UTDZac's Death Quotes extension (MIT) for the Gen 3
 * tracker, which changes the lines the Game Over screen shows. The extension has no lines of its own: it copies
 * the tracker's list when it starts and lets the player edit that copy, and its "(Default)" button puts the
 * tracker's list back. So the built-in lines stay where they always were (PcGameOverQuotes for Gen 1 to 3, and
 * the DS tracker's run-over lines by how the run ended), and this adds the player's own list on top of them:
 * kept in prep/death-quotes.txt, which Backup carries, with a switch, and one more for whether the built-in
 * lines stay in the pool (the extension let the player delete them; here they are not editable, only left out).
 *
 * How a line is picked is this file's own: at random, and none twice until every line has been shown ([QuoteDeck]).
 * A run gets one line, drawn when it first ends, and the game over box and the tracker's own card both show it
 * ([shown]); tapping the team icon in the box draws another ([reroll]). The lines are for a lost run: a win keeps
 * its own message.
 */
object DeathQuotes {
    /** Under filesDir, and in Backup's list. */
    const val FILE = "prep/death-quotes.txt"

    /** Enough for any player, and a bound on what one file can make a screen carry. */
    const val MAX_LINES = 100
    /** A line is cut here. The box wraps a long one, but a whole page of it is not a game over line. */
    const val MAX_LENGTH = 120
    /** A file is read this far and no further: 100 lines of 120 characters is under 50 KB. */
    private const val MAX_FILE_BYTES = 128 * 1024

    /** Why an add or an edit did nothing, or that it worked. */
    enum class Edit { OK, EMPTY, DUPLICATE, FULL }

    /**
     * The switch: the player's own lines are in the pool. On until turned off, and it changes nothing
     * while there are no lines, so it needs no first-time setup.
     */
    var enabled by mutableStateOf(true)
        private set

    /** Whether the built-in lines stay in the pool beside the player's own. On, so the lines are added on top. */
    var keepBuiltIn by mutableStateOf(true)
        private set

    /** The player's own lines, in the order they were added. */
    var lines by mutableStateOf<List<String>>(emptyList())
        private set

    private var file: File? = null

    // ------------------------------------------------------------------ the pool

    /**
     * What a game over draws from: [builtIn] with the player's lines on top of it when they are on, without
     * the built-in ones when the player left them out (and there is a line of their own to use). A line
     * that is there twice, in any capital letters, is there once. Never empty when [builtIn] is not.
     * [forLoss] is false for a win, whose message is the game's own.
     */
    fun pool(builtIn: List<String>, forLoss: Boolean = true): List<String> {
        val own = if (forLoss && enabled) lines else emptyList()
        if (own.isEmpty()) return builtIn
        val base = if (keepBuiltIn) builtIn else emptyList()
        return (base + own).distinctBy { it.lowercase() }
    }

    // ---------------------------------------------------------------- the picking

    /** Where the decks draw from. The tests hand in one with a seed. */
    internal var random: Random = Random.Default

    /** The source of Game Boy and Game Boy Advance game overs, which share one list of built-in lines. */
    const val PC_SOURCE = "pc"

    /** The DS tracker keeps a list of lines for each way a run can end, and each has its own deck. */
    fun dsSource(cause: com.ironmonone.tracker.nds.NdsRunOver): String = "ds:" + cause.name

    /** One deck per source, so the DS causes keep their own turns. */
    private val decks = HashMap<String, QuoteDeck>()
    private var shownKey = ""
    private var shownLine = ""

    /**
     * The line for the run [attempt] of [source] ([PC_SOURCE], [dsSource]): drawn once, the first time anything asks, and
     * the same on every ask after that until [reroll] or a new attempt. The box and the tracker's card ask on their
     * own and say the same thing. Main thread only, as the screens that call it.
     */
    fun shown(source: String, attempt: Int, builtIn: List<String>, forLoss: Boolean = true): String {
        val key = "$source#$attempt#$forLoss"
        if (key != shownKey || shownLine.isEmpty()) draw(key, source, builtIn, forLoss)
        return shownLine
    }

    /** Another line for the same run: the next one of the deck, never the one on screen while there is another. */
    fun reroll(source: String, attempt: Int, builtIn: List<String>, forLoss: Boolean = true): String {
        draw("$source#$attempt#$forLoss", source, builtIn, forLoss)
        return shownLine
    }

    private fun draw(key: String, source: String, builtIn: List<String>, forLoss: Boolean) {
        shownKey = key
        shownLine = decks.getOrPut(source) { QuoteDeck(random) }.next(pool(builtIn, forLoss))
    }

    // ---------------------------------------------------------------- the list

    /**
     * A line as it is kept: control characters and odd spaces (a pasted newline, a tab) turned into plain
     * spaces, runs of spaces made one, the ends trimmed, and cut to [MAX_LENGTH]. Null for a line with nothing in it.
     */
    fun clean(raw: String): String? {
        val flat = raw.map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }.joinToString("")
        val one = flat.replace(Regex(" {2,}"), " ").trim()
        val cut = if (one.length > MAX_LENGTH) one.substring(0, MAX_LENGTH).trimEnd() else one
        return cut.ifEmpty { null }
    }

    fun add(raw: String): Edit {
        val line = clean(raw) ?: return Edit.EMPTY
        if (lines.size >= MAX_LINES) return Edit.FULL
        if (lines.any { it.equals(line, ignoreCase = true) }) return Edit.DUPLICATE
        lines = lines + line
        save()
        return Edit.OK
    }

    /** Puts [raw] in place of the line at [index]. A line already there twice is refused, itself apart. */
    fun replace(index: Int, raw: String): Edit {
        if (index !in lines.indices) return Edit.EMPTY
        val line = clean(raw) ?: return Edit.EMPTY
        if (lines.withIndex().any { (i, l) -> i != index && l.equals(line, ignoreCase = true) }) return Edit.DUPLICATE
        lines = lines.mapIndexed { i, l -> if (i == index) line else l }
        save()
        return Edit.OK
    }

    fun remove(index: Int) {
        if (index !in lines.indices) return
        lines = lines.filterIndexed { i, _ -> i != index }
        save()
    }

    /** The switch for the player's own lines. Not named setEnabled: the property's own setter has that name. */
    fun useOwn(on: Boolean) { enabled = on; save() }

    fun useBuiltIn(on: Boolean) { keepBuiltIn = on; save() }

    // ---------------------------------------------------------------- the file

    /** A file in the format below, or none, or a damaged one: what can be read is used, and the rest is left as if it was not there. */
    fun load(f: File) {
        file = f
        val text = runCatching {
            // Read by hand: InputStream.readNBytes is Android 13 and up, and this runs on Android 8.
            f.takeIf { it.isFile }?.inputStream()?.use { input ->
                val buf = ByteArray(MAX_FILE_BYTES)
                var n = 0
                while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                String(buf, 0, n, Charsets.UTF_8)
            }
        }.getOrNull()
        val p = if (text == null) Parsed(true, true, emptyList()) else parse(text)
        enabled = p.enabled; keepBuiltIn = p.keepBuiltIn; lines = p.lines
    }

    fun save() {
        val f = file ?: return
        SafeWrite.text(f, format(enabled, keepBuiltIn, lines))
    }

    internal class Parsed(val enabled: Boolean, val keepBuiltIn: Boolean, val lines: List<String>)

    private const val HEADER = "KaizoCore game over lines"

    internal fun format(enabled: Boolean, keepBuiltIn: Boolean, lines: List<String>): String =
        (listOf(HEADER, "enabled=$enabled", "keepBuiltIn=$keepBuiltIn") + lines.map { "line=$it" }).joinToString("\n") + "\n"

    /**
     * The file's lines are `enabled=`, `keepBuiltIn=` and one `line=` per line of the player's. Anything else is
     * skipped, a value that is not true or false leaves the setting as it was, and the lines are cleaned, kept
     * once each (capitals aside) and cut off at [MAX_LINES], as if they had been added by hand.
     */
    internal fun parse(text: String): Parsed {
        var on = true
        var keep = true
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        for (raw in text.split('\n')) {
            val line = raw.trimEnd('\r')
            when {
                line.startsWith("enabled=") -> flag(line.removePrefix("enabled="))?.let { on = it }
                line.startsWith("keepBuiltIn=") -> flag(line.removePrefix("keepBuiltIn="))?.let { keep = it }
                line.startsWith("line=") -> clean(line.removePrefix("line="))?.let { if (out.size < MAX_LINES && seen.add(it.lowercase())) out += it }
            }
        }
        return Parsed(on, keep, out)
    }

    private fun flag(v: String): Boolean? = when (v.trim()) { "true" -> true; "false" -> false; else -> null }

    /** Forgets what is on screen and the decks, and puts every setting back, for the tests. */
    internal fun reset() {
        file = null; enabled = true; keepBuiltIn = true; lines = emptyList()
        decks.clear(); shownKey = ""; shownLine = ""; random = Random.Default
    }
}

/**
 * A shuffled deck of lines: every line once, in a random order, before any comes round again, and the first of a
 * new pass is never the last of the one before while there is another line to start with. When the pool it is
 * asked for is not the one it has been dealing from (a line was added, or the switch changed), it starts a new pass.
 */
class QuoteDeck(private val random: Random = Random.Default) {
    private var pool: List<String> = emptyList()
    private val left = ArrayDeque<String>()
    private var last: String? = null

    fun next(from: List<String>): String {
        require(from.isNotEmpty()) { "nothing to pick from" }
        if (from != pool) { pool = from.toList(); left.clear() }
        if (left.isEmpty()) {
            val pass = pool.shuffled(random).toMutableList()
            if (pass.size > 1 && pass.first() == last) pass.add(pass.removeAt(0))
            left.addAll(pass)
        }
        return left.removeFirst().also { last = it }
    }
}

/**
 * Everything the game over lines screen says (2026-09-30), in one place so the copy rules are checked on all of it:
 * no em dash, nothing about how the work is made, plain and dry. DeathQuotesDialog has no wording of its own.
 */
internal object DeathQuotesCopy {
    const val TITLE = "GAME OVER LINES"
    const val CLOSE = "Close"
    /** The X in the corner of the box, as the tracker's own screens draw it. */
    const val CLOSE_MARK = "X"
    const val INTRO = "Add your own lines to the game over box. It picks one at random, and no line comes up twice until every line has been shown."
    const val LOSSES_ONLY = "Your lines are for a lost run. A win keeps its own message."
    const val USE_MINE = "Use my own lines"
    const val KEEP_BUILT_IN = "Also use the built-in lines"
    const val ONLY_MINE = "Only your own lines are used. Turn the built-in lines back on to mix them in."
    const val NO_LINES_YET = "No lines of your own yet."
    const val BUILT_IN_USED = "The built-in lines are used."
    const val SWITCHED_OFF = "Your lines are switched off. The built-in lines are used."
    const val TYPE_A_LINE = "Type a line"
    const val LIMIT = "Up to ${DeathQuotes.MAX_LENGTH} characters."
    const val ADD = "ADD"
    const val SAVE = "SAVE"
    const val CANCEL = "CANCEL"
    const val EDIT = "EDIT"
    const val REMOVE = "REMOVE"
    const val DUPLICATE = "That line is already in your list."
    const val FULL = "Your list is full at ${DeathQuotes.MAX_LINES} lines. Remove one to add another."
    const val EMPTY = "Type something first."

    fun count(n: Int): String = when (n) {
        0 -> NO_LINES_YET
        1 -> "1 line of your own."
        else -> "$n lines of your own."
    }

    fun spoken(action: String, line: String): String = "$action: $line"

    fun message(e: DeathQuotes.Edit): String? = when (e) {
        DeathQuotes.Edit.OK -> null
        DeathQuotes.Edit.EMPTY -> EMPTY
        DeathQuotes.Edit.DUPLICATE -> DUPLICATE
        DeathQuotes.Edit.FULL -> FULL
    }

    /** The note under the switches: what the pool is right now, when it is not the built-in lines and the player's on top. */
    fun poolNote(enabled: Boolean, keepBuiltIn: Boolean, own: Int): String? = when {
        !enabled -> SWITCHED_OFF
        own == 0 -> BUILT_IN_USED
        !keepBuiltIn -> ONLY_MINE
        else -> null
    }

    /** Every line above, the counts and the notes, for the copy-rule test. */
    val all: List<String> = listOf(
        TITLE, CLOSE, INTRO, LOSSES_ONLY, USE_MINE, KEEP_BUILT_IN, ONLY_MINE, NO_LINES_YET, BUILT_IN_USED, SWITCHED_OFF, TYPE_A_LINE, LIMIT,
        ADD, SAVE, CANCEL, EDIT, REMOVE, DUPLICATE, FULL, EMPTY, count(0), count(1), count(7), spoken(EDIT, "Boom"), spoken(REMOVE, "Boom"),
    )
}
