package com.ironmonone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * "Play as your Pokemon", the player's choices (Sprite Is Me by UTDZac, MIT). One switch, off by default,
 * that works in every mode on Home: a Library game with no rules, a Kaizo IronMON run, a Nuzlocke, a ROM
 * hack. It is a setting of the phone, not of a run, so it lives in its own file (prep/sprite-is-me.txt, which
 * Backup carries) and not in the tracker's options, and nothing here asks what kind of game is running.
 *
 * Who the player becomes, in the extension's order: a Pokemon picked ("Always use"), else the player's own
 * sprite, else the lead; the lead being the first Pokemon in the party that is not an egg, and "If no
 * Pokemon" what is used when there is none.
 */
object SpriteIsMeSettings {
    const val FILE = "prep/sprite-is-me.txt"

    enum class Who(val key: String) {
        /** The lead Pokemon: the first in the party that is not an egg. */
        LEAD("lead"),
        /** One chosen species, whatever the party holds. */
        ALWAYS("always"),
        /** The picture or sheet set the player imported. */
        OWN("own"),
        ;

        companion object { fun byKey(k: String): Who = entries.firstOrNull { it.key == k } ?: LEAD }
    }

    /** What the player imported, if anything. Only one at a time: a new import replaces the last. */
    enum class Own(val key: String) {
        NONE("none"), PICTURE("picture"), SHEET("sheet");

        companion object { fun byKey(k: String): Own = entries.firstOrNull { it.key == k } ?: NONE }
    }

    /** The switch. Off, nothing at all is drawn, hidden or read. */
    var on by mutableStateOf(false)
    var who by mutableStateOf(Who.LEAD)
    /**
     * The species "Always use" names, as the Nat. Dex table numbers them whatever the game (Favorites' ids: Gen 3's own
     * up to 411, Turtwig 412 to Pecharunt 1050, the forms after); 0 = none chosen.
     */
    var always by mutableIntStateOf(0)
    /** "Always use" plays the picked Pokemon's shiny (Blake, 2026-10-03), where one ships; the plain one where none does. */
    var alwaysShiny by mutableStateOf(false)
    var own by mutableStateOf(Own.NONE)
    /** The sheet set's frame size (0 = worked out from the sheets) and each animation's frame lengths (blank = default). */
    var sheetWidth by mutableIntStateOf(0)
    var sheetHeight by mutableIntStateOf(0)
    var idleLengths by mutableStateOf("")
    var walkLengths by mutableStateOf("")
    var sleepLengths by mutableStateOf("")
    var faintLengths by mutableStateOf("")

    /** Bumped whenever the imported art or its numbers change, so the engine loads it again. */
    var artVersion by mutableIntStateOf(0)

    val spec: SheetSet.Spec
        get() = SheetSet.Spec(sheetWidth, sheetHeight, idleLengths, walkLengths, sleepLengths, faintLengths)

    private var file: File? = null

    /** Load once per process, from the app's files folder. Safe to call from any composable. */
    fun ensureLoaded(filesDir: File) {
        val f = File(filesDir, FILE)
        if (file?.path == f.path) return
        load(f)
    }

    fun load(f: File) {
        file = f
        on = false; who = Who.LEAD; always = 0; alwaysShiny = false; own = Own.NONE
        sheetWidth = 0; sheetHeight = 0; idleLengths = ""; walkLengths = ""; sleepLengths = ""; faintLengths = ""
        if (!f.isFile) return
        runCatching {
            f.forEachLine { line ->
                val cut = line.indexOf('='); if (cut <= 0) return@forEachLine
                val k = line.substring(0, cut).trim(); val v = line.substring(cut + 1).trim()
                when (k) {
                    "on" -> on = v == "true"
                    "who" -> who = Who.byKey(v)
                    "always" -> always = species(v)
                    "alwaysShiny" -> alwaysShiny = v == "true"
                    "own" -> own = Own.byKey(v)
                    "sheetWidth" -> sheetWidth = size(v)
                    "sheetHeight" -> sheetHeight = size(v)
                    "idle" -> idleLengths = lengths(v)
                    "walk" -> walkLengths = lengths(v)
                    "sleep" -> sleepLengths = lengths(v)
                    "faint" -> faintLengths = lengths(v)
                }
            }
        }
    }

    fun save() {
        val f = file ?: return
        SafeWrite.text(f, text())
    }

    fun text(): String = "on=$on\nwho=${who.key}\nalways=$always\nalwaysShiny=$alwaysShiny\nown=${own.key}\n" +
        "sheetWidth=$sheetWidth\nsheetHeight=$sheetHeight\nidle=$idleLengths\nwalk=$walkLengths\nsleep=$sleepLengths\nfaint=$faintLengths\n"

    /** Every setting back to what a fresh install has (the art files are the store's to delete). */
    fun reset() {
        on = false; who = Who.LEAD; always = 0; alwaysShiny = false; own = Own.NONE
        sheetWidth = 0; sheetHeight = 0; idleLengths = ""; walkLengths = ""; sleepLengths = ""; faintLengths = ""
        artVersion++
    }

    /** A species id from a file: 0-1300, or 0 for anything else. */
    private fun species(v: String): Int = v.toIntOrNull()?.takeIf { it in 0..1300 } ?: 0
    private fun size(v: String): Int = v.toIntOrNull()?.takeIf { it in 0..SpriteArt.MAX_FRAME } ?: 0

    /** Frame lengths: digits, commas and spaces only, so a file cannot carry anything else onto the screen. */
    fun lengths(v: String): String = v.filter { it.isDigit() || it == ',' || it == ' ' }.take(80)
}

/**
 * Every line "Play as your Pokemon" shows, in one place so a test can hold them to the copy rules (no em dashes,
 * plain and dry, nothing about how the app was made). The words are the ones the section is specified in.
 */
object SpriteIsMeCopy {
    const val TITLE = "Play as your Pokemon"
    /**
     * Under the switch (Blake, 2026-09-30), on or off. Every Gen 1-3 Pokemon has a walking sprite and so do most later
     * ones; the few nobody has drawn yet (natdex-map.tsv names them) leave you the trainer.
     */
    const val GENS = "Most Pokemon have a walking sprite. A few later ones do not yet, and with one of those in the lead you stay the trainer."
    const val WHAT = "Your character becomes a Pokemon while you walk around. It changes the picture only: not the game, not your save."
    const val WHO = "Who you play as"
    const val LEAD = "Your lead Pokemon"
    const val ALWAYS = "Always use"
    const val OWN = "Your own sprite"
    const val CHOOSE = "Choose"
    /** Under "Always use": the picked Pokemon's shiny colors (Blake, 2026-10-03). */
    const val SHINY = "Shiny"
    const val CHOOSE_PICTURE = "Choose a picture"
    const val CHOOSE_SHEETS = "Choose a sheet set"
    const val REMOVE = "Remove"
    const val SHEET_SETTINGS = "Sheet settings"
    const val SAVE = "Save"
    const val CLOSE = "Close"
    const val SEARCH = "Type a name"
    const val NO_MATCH = "No Pokemon by that name has a sprite."

    // The one line a game that cannot use it gets.
    const val NOT_GBA = "Play as your Pokemon works on Game Boy Advance games only."
    const val NOT_KNOWN = "This game is not one Play as your Pokemon knows."
    const val NAT_DEX = "Play as your Pokemon could not find its way around this Nat. Dex build."
    /** MaxDex 1.0's overworld is read out of its own code, as Nat. Dex's is; this is said only when that finds nothing. */
    const val MAX_DEX = "Play as your Pokemon could not find its way around this MaxDex build."
    /** Heart & Soul's overworld is its build's own symbols (Overworld.HEARTSOUL_KAIZO); this is said only if the emulator side refuses them. */
    const val HEART_SOUL = "Play as your Pokemon could not find its way around this Heart & Soul build."
    /** A game named by its header whose code moved (a hack built from the decompilations), and that could not be read (rc32 audit P2 #87). */
    const val NO_OVERWORLD = "Play as your Pokemon could not find its way around this game, so you stay the trainer."
    const val LOOKING = "Looking at this game..."

    const val NO_OWN_YET = "Nothing imported yet. A picture is fitted into a 32 by 32 box; a sheet set is idle, walk, sleep and faint sheets."
    /** Beside the import buttons: what is imported goes wherever the backup goes (rc32 audit P2 #94). */
    const val IN_BACKUPS = "Backups and cloud sync include the picture or sheets you import."
    const val OWN_PICTURE = "A picture is set."
    const val OWN_SHEETS = "A sheet set is set."
    const val SHEET_HELP = "Idle and walk have eight rows, one per direction, and sleep and faint one, with the frames side by side. Leave a number blank to have it worked out."
    const val FRAME_WIDTH = "Frame width"
    const val FRAME_HEIGHT = "Frame height"
    const val LENGTHS_HINT = "Frame lengths in game frames, like 40,6,6"
    const val IDLE = "Idle"
    const val WALK = "Walk"
    const val SLEEP = "Sleep"
    const val FAINT = "Faint"

    fun picked(name: String) = "Playing as $name."
    const val PICTURE_FAILED = "That picture could not be read."
    const val SHEETS_FAILED = "No sheets found. Name them idle, walk, sleep and faint, as PNG files or in a zip."
    /** The sheets were read, and the phone would not keep them (rc32 audit P3 #67). The set in use stays. */
    const val SHEETS_NOT_SAVED = "The sheets could not be saved. If this phone is out of space, free some. Your last sprite is still in use."
    fun sheetsFound(n: Int) = if (n == 1) "Found 1 sheet." else "Found $n sheets."

    /** What an import of a sheet set says: how many were kept, and that one was too big when it was. */
    fun sheetsSaved(kept: Int, tooBig: Boolean): String = when {
        kept > 0 && tooBig -> sheetsFound(kept) + " " + SHEET_TOO_BIG
        kept > 0 -> sheetsFound(kept)
        tooBig -> SHEET_TOO_BIG
        else -> SHEETS_FAILED
    }
    /** A sheet past SheetSet's bound is not kept or drawn (rc32 audit P2 #88, P3 #66). */
    const val SHEET_TOO_BIG = "A sheet can be at most 2048 by 1024 pixels, so a bigger one is left out."
    /** Imported art on disk that would not decode: the lead is played as instead (rc32 audit P2 #88). */
    const val OWN_UNREADABLE = "Your own sprite could not be read, so you play as your lead."

    val all: List<String> = listOf(
        TITLE, GENS, WHAT, WHO, LEAD, ALWAYS, OWN, CHOOSE, SHINY, CHOOSE_PICTURE, CHOOSE_SHEETS, REMOVE, SHEET_SETTINGS, SAVE, CLOSE, SEARCH, NO_MATCH,
        NOT_GBA, NOT_KNOWN, NAT_DEX, MAX_DEX, HEART_SOUL, NO_OVERWORLD, LOOKING, NO_OWN_YET, IN_BACKUPS, OWN_PICTURE, OWN_SHEETS, SHEET_HELP, FRAME_WIDTH, FRAME_HEIGHT, LENGTHS_HINT,
        IDLE, WALK, SLEEP, FAINT, picked("Pikachu"), PICTURE_FAILED, SHEETS_FAILED, SHEETS_NOT_SAVED, sheetsFound(1), sheetsFound(3), SHEET_TOO_BIG, OWN_UNREADABLE,
        sheetsSaved(2, tooBig = true),
    )
}

/**
 * The Pokemon picker's search (Blake, 2026-09-30: "type a name should suggest names of all the pokemon with the
 * letters you type"). Every name that holds the letters, as the picker always listed, now in the order a player
 * looks for them: names that start with the letters, then names with a later word that does ("mime" finds Mr. Mime),
 * then the rest, each group in Pokedex order. Case, spaces, dots, dashes and accents do not count.
 */
object SpriteIsMeSearch {
    fun matches(all: List<Pair<Int, String>>, query: String): List<Pair<Int, String>> {
        val q = fold(query)
        if (q.isEmpty()) return all
        val starts = ArrayList<Pair<Int, String>>()
        val words = ArrayList<Pair<Int, String>>()
        val inside = ArrayList<Pair<Int, String>>()
        for (e in all) {
            val name = fold(e.second)
            when {
                name.startsWith(q) -> starts += e
                e.second.split(' ', '-', '.').drop(1).any { fold(it).startsWith(q) } -> words += e
                q in name -> inside += e
            }
        }
        return starts + words + inside
    }

    /** Lower-case ASCII letters and digits only, accents taken off: "Flabébé" reads as "flabebe". */
    fun fold(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).lowercase().filter { it.code < 128 && it.isLetterOrDigit() }
}
