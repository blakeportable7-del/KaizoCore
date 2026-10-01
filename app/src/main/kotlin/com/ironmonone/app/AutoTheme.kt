package com.ironmonone.app

import androidx.compose.ui.graphics.Color

/**
 * Auto Pokemon Themes (Fellshadow's extension for the Gen 3 tracker, MIT,
 * v1.2), native. Its table is one Gen 3 theme code per species (386 rows,
 * gen3/autothemes.tsv, made by tools/trainer-data/convert_autothemes.py by
 * running the extension's own loader over its AutoThemeSets.txt).
 *
 * Its rules, from AutoThemes.lua: every update, the lead is party slot 1, or
 * when that is an egg the next non-egg slot, wrapping. A lead with a theme
 * loads it; a lead with none loads the user's theme back; an empty party keeps
 * whatever is showing. The user's theme is never overwritten: it is taken when
 * the first auto theme goes on and put back exactly on release, and the colour
 * editor suspends the auto theme so what it edits and saves is the user's own
 * (the extension's README warns that editing while an auto theme shows saved
 * the auto theme; this closes that). "The user's theme" is the whole theme since
 * 2026-09-29 ([WholeTheme]): a preset the player picked keeps its lower box,
 * header and move-type bar through an auto theme, where release() used to clear
 * everything past the eight editor colours.
 *
 * Theme code: 11 hex colours in Theme.lua's order (Default text, Lower box
 * text, Positive, Negative, Intermediate, Header, Upper border, Upper
 * background, Lower border, Lower background, Main background), then flag 1
 * (MOVE_TYPES_ENABLED; "0" = names in the lower text with a type bar) and
 * flag 2 (text shadows, which this app does not draw).
 *
 * DS games use the DS tracker's own themes and rules instead (onDs; its themes are
 * built in, 14 colours each, in com.ironmonone.tracker.nds.NdsAutoThemes).
 */
object AutoTheme {
    private val gba: Map<Int, String> by lazy {
        val out = HashMap<Int, String>()
        com.ironmonone.tracker.GbaTracker::class.java.getResourceAsStream("/gen3/autothemes.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { l ->
                    if (l.startsWith("#")) return@forEach
                    val tab = l.indexOf('\t')
                    if (tab > 0) l.substring(0, tab).trim().toIntOrNull()?.let { out[it] = l.substring(tab + 1).trim() }
                }
            }
        out
    }

    /** The theme code showing, or null when the user's own colours are showing. */
    @Volatile var current: String? = null
        private set
    private var saved: WholeTheme? = null
    private var suspended = false

    fun gbaTheme(species: Int): String? = gba[species]

    /**
     * AutoThemes.lua afterProgramDataUpdate for a Gen 3 or Game Boy game.
     * [party] is (species, isEgg) in slot order.
     */
    fun onGba(party: List<Pair<Int, Boolean>>, on: Boolean) {
        if (suspended) return
        if (!on) { release(); return }
        if (party.isEmpty()) return
        // Slot 1, or the next non-egg slot after it; all eggs: slot 1.
        val lead = party.firstOrNull { !it.second } ?: party[0]
        val code = gba[lead.first]
        if (code == null) { release(); return }
        if (code != current) apply(code)
    }

    /**
     * Parses and shows [code]; false (and nothing changed) for a malformed one. The reading is
     * [ThemeCodes.gen3], the one the theme presets are shown through, so a preset and an auto
     * theme made from the same code cannot differ.
     */
    fun apply(code: String): Boolean {
        val theme = ThemeCodes.gen3(code) ?: return false
        if (saved == null) saved = ThemeStore.snapshot()
        ThemeStore.show(theme)
        current = code
        return true
    }

    /** The Pokemon a DS theme follows: the game's generation, its species and its form. */
    data class DsPokemon(val gen: Int, val species: Int, val form: Int)

    /**
     * The reference's playerPokemon in [state] (Program.lua:521-535): your Pokemon on
     * the field in a fetched battle, else the first party member standing that is not
     * an egg, the one [NdsTrackerState.healsPid] names. Null while there is none.
     */
    fun dsPokemon(state: com.ironmonone.tracker.nds.NdsTrackerState): DsPokemon? {
        val mon = (state.party.firstOrNull { it.mon.pid == state.healsPid }
            ?: state.playerActive?.takeIf { it.mon.pid == state.healsPid })?.mon ?: return null
        return DsPokemon(if (state.badgeSet.startsWith("BW")) 5 else 4, mon.species, mon.form)
    }

    /**
     * PokemonThemeManager.update(playerPokemon.pokemonID) for a DS game
     * (PokemonThemeManager.lua:103-113, from Program.readMemory, Program.lua:671-673).
     * Its rules are not the Gen 3 extension's: readCurrentPokemonID (lua:44-55) only
     * loads a theme that exists, so a Pokemon without one, or no Pokemon at all, keeps
     * whatever shows; the option going off gives the user's theme back
     * (undoPokemonTheme). The colour editor suspends it as it does the Gen 3 one.
     */
    fun onDs(pokemon: DsPokemon?, on: Boolean) {
        if (suspended) return
        if (!on) { release(); return }
        val code = pokemon?.let { com.ironmonone.tracker.nds.NdsAutoThemes.code(it.gen, it.species, it.form) } ?: return
        if (code != current) applyDs(code)
    }

    /**
     * Shows a DS theme string as formatPokemonTheme leaves it
     * (com.ironmonone.tracker.nds.NdsAutoThemes.read); false, and nothing changed, for a
     * malformed one. Its first eleven colours mean what a Gen 3 theme's do (top box =
     * upper box, move header = header) and land in the same places. Of the rest:
     * move names in the bottom box's text colour with the type beside them ("Color
     * move names by type" off, type icons on: this app's type bar stands in for the
     * DS type icon), the physical and special icons on and in the bottom box's text
     * colour (where this app always draws them), the gear in the top box's.
     */
    fun applyDs(code: String): Boolean {
        val t = com.ironmonone.tracker.nds.NdsAutoThemes.read(code) ?: return false
        fun c(rgb: Long) = Color(0xFF000000L or rgb)
        if (saved == null) saved = ThemeStore.snapshot()
        Pc.Text = c(t.topText)
        Pc.LowerTextX = c(t.bottomText); Pc.Dim = c(t.bottomText)
        Pc.Positive = c(t.positive)
        Pc.Negative = c(t.negative)
        Pc.Gold = c(t.intermediate)
        Pc.HeaderX = c(t.moveHeader)
        Pc.Border = c(t.topBorder)
        Pc.Ground = c(t.topBackground)
        Pc.LowerBorderX = c(t.bottomBorder)
        Pc.LowerGroundX = c(t.bottomBackground)
        Pc.Page = c(t.mainBackground); Pc.HeaderGroundX = c(t.mainBackground)
        Pc.AltPositiveX = t.altPositive?.let(::c)
        Pc.AltNegativeX = t.altNegative?.let(::c)
        Pc.moveTypeBar = true
        Pc.categoryIconsX = true
        current = code
        return true
    }

    /**
     * Puts the user's whole theme back exactly as it was when the first auto theme went on,
     * extras and all. With no auto theme showing there is nothing to put back and nothing is
     * touched: it used to clear every override here, which was harmless while only an auto
     * theme set them, and would now wipe a preset's lower box on every tracker update.
     */
    fun release() {
        saved?.let { ThemeStore.show(it) }
        saved = null
        current = null
    }

    /** The colour editor is open: show and edit the user's own theme. */
    fun suspend() { release(); suspended = true }

    /** The editor closed; the next update applies the lead's theme again. */
    fun resume() { suspended = false }
}
