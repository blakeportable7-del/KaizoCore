package com.ironmonone.pcui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.ironmonone.app.Pc
import com.ironmonone.app.rp

/**
 * Typing on the panel itself (Blake, 2026-10-07: "the same keyboard as the log viewer's"): the note on a Pokemon, the
 * favorites, and Play as your Pokemon's "Always use" name, each typed with LogSearchScreen's on-screen keyboard
 * (1 to 0, QWERTYUIOP, ASDFGHJKL, ZXCVBNM, the space after M, backspace and clear), clicked with the mouse. Nothing
 * typed reaches the game, and no window opens over BizHawk: until this round the note and the favorites were BizHawk
 * forms windows. The keys are [PcLogSide.keyBox]'s boxes, so the two keyboards are the same keys in the same places.
 */
object PcTypeScreen {
    enum class Kind { NOTE, FAVORITES, ALWAYS, GOAL }

    /** What is being typed. Compose state, so a key redraws the panel; kept by the caller across a remade renderer. */
    class Ui {
        var kind by mutableStateOf<Kind?>(null)
        var text by mutableStateOf("")
        /** The note's Pokemon (its species, for the title); the favorite's slot (0-based). */
        var species by mutableStateOf(0)
        var title by mutableStateOf("")
        var slot by mutableStateOf(0)
        /** The favorites as they stand, shown in the slot grid; [shown] of them count in this run. */
        var slots by mutableStateOf(List(9) { "" })
        var shown by mutableStateOf(9)
        /** The names offered for what is typed (Favorites.suggest); set by the session, not part of the panel's input. */
        var suggest: (String) -> List<String> = { emptyList() }
        /** Save: the note's text, a favorite's (slot, name), the Always-use name. Set by the session. */
        var onNote: (Int, String) -> Unit = { _, _ -> }
        var onFavorite: (Int, String) -> Unit = { _, _ -> }
        var onAlways: (String) -> Unit = {}

        val open: Boolean get() = kind != null

        fun openNote(species: Int, name: String, note: String) { kind = Kind.NOTE; this.species = species; title = name; text = note }
        fun openFavorites(slots: List<String>, shown: Int, slot: Int = 0) {
            kind = Kind.FAVORITES; this.slots = slots; this.shown = shown.coerceIn(1, 9); this.slot = slot.coerceIn(0, this.shown - 1)
            text = slots.getOrElse(this.slot) { "" }
        }
        fun openAlways(current: String) { kind = Kind.ALWAYS; text = current }
        /** PedometerGoal: the goal in steps, typed in digits; empty or 0 is no goal. */
        fun openGoal(current: Int) { kind = Kind.GOAL; text = if (current > 0) "$current" else "" }
        fun close() { kind = null; text = "" }

        /** The step goal Save keeps. */
        fun saveGoal() { PcPedometer.setGoal(text.toIntOrNull() ?: 0); close() }

        /** One key: a letter or digit, ' ' for the space key, '\b' backspace, '\u0018' clear. */
        fun key(c: Char) {
            if (kind == Kind.GOAL && c.isLetter() || kind == Kind.GOAL && c == ' ') return
            val max = when (kind) { Kind.NOTE -> NOTE_MAX; Kind.GOAL -> 6; else -> NAME_MAX }
            text = when (c) {
                '\b' -> text.dropLast(1)
                '\u0018' -> ""
                else -> if (text.length < max) text + c else text
            }
        }
    }

    const val NOTE_MAX = 500
    const val NAME_MAX = 20
    /** The note's characters a line in the text box, and how many lines show (the last ones, with the caret). */
    private const val NOTE_COLS = 33
    private const val NOTE_ROWS = 6
    private const val FIELD_Y = 80
    private const val FAV_FIELD_Y = 51
    private const val ALWAYS_FIELD_Y = 20

    @Composable
    fun View(ui: Ui) {
        val upper = Pc.Ground; val border = Pc.Border; val text = Pc.Text
        Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
            BRect(5, 15, 140, 140, border, upper)
            when (ui.kind) {
                Kind.NOTE -> Note(ui)
                Kind.FAVORITES -> Favorites(ui)
                Kind.ALWAYS -> Always(ui)
                Kind.GOAL -> Goal(ui)
                null -> {}
            }
            // Backspace and clear, LogSearchScreen's boxes.
            val by = when (ui.kind) { Kind.FAVORITES -> FAV_FIELD_Y; Kind.ALWAYS, Kind.GOAL -> ALWAYS_FIELD_Y; else -> FIELD_Y }
            BRect(PcLogSide.BACK_X, by, 12, 12, border, upper) { ui.key('\b') }
            BImage(PcBesteon.LEFT_ARROW, PcLogSide.BACK_X + 2, by + 1, text)
            BRect(PcLogSide.CLEAR_X, by, 12, 12, border, upper) { ui.key('\u0018') }
            BImage(PcLogOverlay.CLOSE, PcLogSide.CLEAR_X + 2, by + 2, text)
            Keyboard(ui)
        }
    }

    @Composable
    private fun Keyboard(ui: Ui) {
        BRect(5, 95, 140, 60, Pc.Border, Pc.Ground)
        (PcLogSide.KEYS.joinToString("") + "_").forEach { c ->
            val b = PcLogSide.keyBox(c) ?: return@forEach
            BRect(b[0], b[1], b[2], b[3], Pc.LowerBorder, Pc.Ground) { ui.key(if (c == '_') ' ' else c) }
            BText(c.toString(), b[0], b[1], Pc.LowerText, w = b[2] + 1, align = TextAlign.Center)
        }
    }

    /** A button with a word in it, besteon's FULL_BORDER. */
    @Composable
    private fun Button(label: String, x: Int, y: Int, w: Int, onClick: () -> Unit) {
        BRect(x, y, w, 11, Pc.Border, Pc.Ground, onClick)
        BText(label, x, y, Pc.Text, w = w + 1, align = TextAlign.Center)
    }

    /** The note as it is typed, the last lines of it, the caret after it; Save keeps it, the back arrow drops it. */
    @Composable
    private fun Note(ui: Ui) {
        BText(("NOTE: " + ui.title).uppercase().take(24), 5, 3, Pc.Header)
        BImage(PcBesteon.BACK_ARROW, 129, 2, Pc.Text)
        BHit(127, 1, 18, 12) { ui.close() }
        val lines = wrapNote(ui.text + "_")
        lines.takeLast(NOTE_ROWS).forEachIndexed { i, line -> BText(line, 8, 18 + i * 10, Pc.Text) }
        Button("Save", 8, FIELD_Y, 40) { ui.onNote(ui.species, ui.text.trim()); ui.close() }
        BText("${ui.text.length}/$NOTE_MAX", 52, FIELD_Y + 1, Pc.Dim)
    }

    /** Lines of at most [NOTE_COLS] characters, broken at spaces where it can. */
    fun wrapNote(s: String): List<String> {
        val out = ArrayList<String>()
        s.split('\n').forEach { para ->
            var rest = para
            while (rest.length > NOTE_COLS) {
                val cut = rest.lastIndexOf(' ', NOTE_COLS).takeIf { it > 0 } ?: NOTE_COLS
                out += rest.substring(0, cut)
                rest = rest.substring(cut).trimStart()
            }
            out += rest
        }
        return out
    }

    /** The typed name and the names it offers; a click on one is [pick]ed. */
    @Composable
    private fun Field(ui: Ui, y: Int, rows: Int, pick: (String) -> Unit) {
        BRect(8, y, 104, 12, Pc.Border, Pc.Ground)
        BText(ui.text + "_", 9, y + 1, Pc.Text)
        val names = if (ui.text.isBlank()) emptyList() else ui.suggest(ui.text).take(rows)
        names.forEachIndexed { i, n ->
            val ny = y + 14 + i * 10
            BRect(8, ny, 134, 10, Pc.LowerBorder, Pc.LowerGroundX ?: Pc.Ground) { pick(n) }
            BText(n, 10, ny - 1, Pc.LowerText)
        }
    }

    /** The favorites: the slots this run counts, the one chosen marked; type, then click a name to set that slot. */
    @Composable
    private fun Favorites(ui: Ui) {
        BText("FAVORITES", 5, 3, Pc.Header)
        // Done keeps a typed name as Enter does, when it is a Pokemon's name (it used to be thrown away unless picked).
        Button("Done", 111, 2, 32) {
            val typed = ui.text.trim()
            val exact = if (typed.isEmpty()) null else ui.suggest(typed).firstOrNull { it.equals(typed, ignoreCase = true) }
            if (exact != null && exact != ui.slots.getOrElse(ui.slot) { "" }) ui.onFavorite(ui.slot, exact)
            ui.close()
        }
        for (i in 0 until ui.shown) {
            val x = 7 + (i % 3) * 46; val y = 17 + (i / 3) * 11
            val chosen = i == ui.slot
            BRect(x, y, 44, 10, if (chosen) Pc.Gold else Pc.LowerBorder, Pc.LowerGroundX ?: Pc.Ground) {
                ui.slot = i; ui.text = ui.slots.getOrElse(i) { "" }
            }
            BText("${i + 1} " + ui.slots.getOrElse(i) { "" }.take(9), x + 1, y - 1, if (chosen) Pc.Gold else Pc.LowerText)
        }
        Field(ui, FAV_FIELD_Y, 3) { name ->
            ui.slots = ui.slots.toMutableList().also { while (it.size < 9) it += ""; it[ui.slot] = name }
            ui.text = name
            ui.onFavorite(ui.slot, name)
        }
        Button("Remove", 70, 2, 38) {
            ui.slots = ui.slots.toMutableList().also { while (it.size < 9) it += ""; it[ui.slot] = "" }
            ui.text = ""
            ui.onFavorite(ui.slot, "")
        }
    }

    /** TrackerScreen.openEditStepGoalWindow: the goal in steps, Save keeps it (0 or nothing for no goal). */
    @Composable
    private fun Goal(ui: Ui) {
        BText("STEP GOAL", 5, 3, Pc.Header)
        BImage(PcBesteon.BACK_ARROW, 129, 2, Pc.Text)
        BHit(127, 1, 18, 12) { ui.close() }
        BRect(8, ALWAYS_FIELD_Y, 104, 12, Pc.Border, Pc.Ground)
        BText(ui.text + "_", 9, ALWAYS_FIELD_Y + 1, Pc.Text)
        BText("Steps to reach (0 for none)", 8, ALWAYS_FIELD_Y + 15, Pc.Dim)
        Button("Save", 8, ALWAYS_FIELD_Y + 30, 40) { ui.saveGoal() }
    }

    /** Play as your Pokemon's "Always use": type, click a name. */
    @Composable
    private fun Always(ui: Ui) {
        BText("ALWAYS USE", 5, 3, Pc.Header)
        BImage(PcBesteon.BACK_ARROW, 129, 2, Pc.Text)
        BHit(127, 1, 18, 12) { ui.close() }
        Field(ui, ALWAYS_FIELD_Y, 5) { name -> ui.onAlways(name); ui.close() }
    }
}
