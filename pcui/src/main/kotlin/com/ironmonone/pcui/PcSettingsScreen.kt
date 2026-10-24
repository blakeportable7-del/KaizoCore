package com.ironmonone.pcui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.ironmonone.app.Pc
import com.ironmonone.app.ThemePresets
import com.ironmonone.app.ThemeStore
import com.ironmonone.app.TrackerOptions
import com.ironmonone.app.referenceWidth
import com.ironmonone.app.rp

/**
 * The settings the gear opens, laid out as besteon's GameOptionsScreen (Ironmon-Tracker, MIT): the title on the page at
 * y 3, the tabs along y 15 (12 tall, 10 wider than their words, the chosen one joined to the box), the box from y 27,
 * the checkboxes 8 by 8 at x 9 from y 31, 12 apart, the words one pixel after them, and the back arrow at (129, 142).
 * The options are the ones the PC panel honours; the Theme tab picks one of the tracker's own themes (ThemePresets),
 * which colour the whole panel. Everything is kept in the files KaizoCore PC loads at start (TrackerOptions, ThemeStore).
 */
object PcSettingsScreen {
    enum class Tab(val label: String) { BATTLE("Battle"), DISPLAY("Display"), THEME("Theme"), PLAY_AS("Play as") }

    /** What a row asks KaizoCore PC to do: a file to choose, a name to type. */
    enum class Action { ADD_PICTURES, PLAY_AS_ALWAYS, PLAY_AS_PICTURE, PLAY_AS_SHEETS, PLAY_AS_REMOVE }

    /** What the screen shows: open or not, which tab, the theme page. Compose state, so a click redraws it. */
    class Ui {
        var open by mutableStateOf(false)
        var tab by mutableStateOf(Tab.BATTLE)
        var themePage by mutableStateOf(0)
        /** The score sheet's page (Grade my notes). */
        var scorePage by mutableStateOf(1)
        /** A line under the options: what the last action did ("3 pictures added"). */
        var message by mutableStateOf("")
        /** The Pokemon "Always use" names, for its row; set by the session. */
        var alwaysName by mutableStateOf("")
        /** Set by the session: a row that needs KaizoCore PC (a file chooser, the keyboard). */
        var onAction: (Action) -> Unit = {}
    }

    class Option(val label: String, val get: () -> Boolean, val set: (Boolean) -> Unit)

    fun options(tab: Tab): List<Option> = when (tab) {
        Tab.BATTLE -> listOf(
            Option("Auto swap to enemy", { TrackerOptions.autoSwapToEnemy(false) }, { TrackerOptions.chooseAutoSwapToEnemy(it) }),
            Option("Show Poké Ball catch rate", { TrackerOptions.showCatchRate }, { TrackerOptions.showCatchRate = it }),
            Option("Show move effectiveness", { TrackerOptions.showMoveEffectiveness }, { TrackerOptions.showMoveEffectiveness = it }),
            Option("Show physical special icons", { TrackerOptions.showCategoryIcons }, { TrackerOptions.showCategoryIcons = it }),
            Option("Show last damage calcs", { TrackerOptions.showLastDamage }, { TrackerOptions.showLastDamage = it }),
            Option("Show real hit chance", { TrackerOptions.realHitChance }, { TrackerOptions.realHitChance = it }),
            Option("Show real move power", { TrackerOptions.realMovePower }, { TrackerOptions.realMovePower = it }),
            Option("Show random move/type data", { TrackerOptions.revealInfoIfRandomized }, { TrackerOptions.revealInfoIfRandomized = it }),
            Option("Track PC Heals", { TrackerOptions.trackPcHeals }, { TrackerOptions.trackPcHeals = it }),
            Option("Battle animations", { TrackerOptions.battleAnimations }, { TrackerOptions.battleAnimations = it }),
        )
        Tab.DISPLAY -> listOf(
            Option("Show nicknames", { TrackerOptions.showNicknames }, { TrackerOptions.showNicknames = it }),
            Option("Show experience points bar", { TrackerOptions.showExpBar }, { TrackerOptions.showExpBar = it }),
            Option("Show heals as whole number", { TrackerOptions.healsWhole }, { TrackerOptions.healsWhole = it }),
            Option("Color stat numbers by nature", { TrackerOptions.colorStatNumbers }, { TrackerOptions.colorStatNumbers = it }),
            Option("Right justified numbers", { TrackerOptions.rightJustifiedNumbers }, { TrackerOptions.rightJustifiedNumbers = it }),
            Option("Display repel usage", { TrackerOptions.showRepel }, { TrackerOptions.showRepel = it }),
            // The panel's pictures (PcPictures, kept in pictures.txt): the phone's "Animated Pokemon (Walking Pals)", and
            // the player's own pictures over either set.
            Option("Animated Pok\u00e9mon (Walking Pals)", { PcPictures.set == PcPictures.Set.WALKING },
                { PcPictures.set = if (it) PcPictures.Set.WALKING else PcPictures.Set.GBA; PcPictures.save() }),
            Option("Use my own pictures", { PcPictures.own }, { PcPictures.own = it; PcPictures.save() }),
            // On Display since 2026-10-10: the Battle tab ran past its box with the real hit chance and power added.
            Option("Level-up and item animations", { TrackerOptions.progressAnimations }, { TrackerOptions.progressAnimations = it }),
        )
        Tab.THEME, Tab.PLAY_AS -> emptyList()
    }

    /** Seven a page: the first row is "Auto Pokemon Themes". */
    const val THEMES_PER_PAGE = 7
    fun themes(): List<ThemePresets.Preset> = ThemePresets.builtIns(false) + ThemePresets.yours

    /** The y of checkbox row [i]: GameOptionsScreen's startY (MARGIN + TAB_HEIGHT + 14), then LINESPACING + 1 apart. */
    fun rowY(i: Int) = 5 + 12 + 14 + i * 12

    /** The tabs' x and width: 10 wider than the words (less when four do not fit in the box's 140), side by side from x 5. */
    fun tabBoxes(): List<Pair<Int, Int>> {
        val words = Tab.entries.map { referenceWidth(it.label) }
        val pad = minOf(10, (140 - words.sum()) / words.size).coerceAtLeast(2)
        var x = 5
        return words.map { ww -> val w = pad + ww; (x to w).also { x += w } }
    }

    @Composable
    fun View(ui: Ui, onChanged: () -> Unit) {
        val text = Pc.LowerText
        val border = Pc.LowerBorder
        val fill = Pc.LowerGroundX ?: Pc.Ground
        Box(Modifier.size(PcPanel.WIDTH.rp, PcPanel.HEIGHT.rp).background(Pc.Page)) {
            androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides Pc.Page) { BText("TRACKER SETTINGS", 5, 3, Pc.Header) }
            // The box, then the tabs over its top edge.
            BRect(5, 27, 140, 128, border, fill)
            tabBoxes().forEachIndexed { i, (x, w) ->
                val tab = Tab.entries[i]
                val selected = ui.tab == tab
                val y = 15; val h = 12
                Box(Modifier.offset((x + 1).rp, (y + 1).rp).size(w.rp, (if (selected) h else h - 1).rp)
                    .background(if (selected) fill else darken(fill)))
                Canvas(Modifier.offset(x.rp, y.rp).size((w + 1).rp, (h + 1).rp)) {
                    val u = size.width / (w + 1)
                    fun px(ax: Int, ay: Int, bx: Int, by: Int) = drawRect(border, Offset(ax * u, ay * u), androidx.compose.ui.geometry.Size((bx - ax + 1) * u, (by - ay + 1) * u))
                    px(1, 0, w - 1, 0); px(0, 1, 0, h - 1); px(w, 1, w, h - 1)
                }
                androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides (if (selected) fill else darken(fill))) {
                    BText(tab.label, x, y, if (selected) Pc.Gold else text, w = w + 1, align = TextAlign.Center)
                }
                BHit(x, y, w, h) { ui.tab = tab }
            }
            // The rows, the message and the arrow are on the box.
            androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides fill) {
            when (ui.tab) {
                Tab.THEME -> Themes(ui, text, border, fill, onChanged)
                Tab.PLAY_AS -> PlayAs(ui, text, border, fill, onChanged)
                else -> {
                    val opts = options(ui.tab)
                    opts.forEachIndexed { i, o ->
                        val y = rowY(i)
                        Checkbox(9, y, o.get(), border, fill)
                        BText(o.label, 18, y - 2, text)
                        // Ends at x 123, short of the Back arrow (124), which the last row's click used to cover.
                        BHit(9, y, 114, 11) { o.set(!o.get()); TrackerOptions.save(); onChanged() }
                    }
                    if (ui.tab == Tab.DISPLAY) {
                        val y = rowY(opts.size)
                        // What adding pictures did is said on this row, not on a line laid over row 8.
                        if (ui.message.isNotEmpty()) BText(ui.message, 18, y - 2, Pc.Gold, w = 107)
                        else BText("+ Add my own pictures", 18, y - 2, Pc.Positive)
                        BHit(9, y - 1, 114, 10) { ui.onAction(Action.ADD_PICTURES) }
                    }
                }
            }
            if (ui.message.isNotEmpty() && ui.tab != Tab.DISPLAY && ui.tab != Tab.BATTLE) BText(ui.message, 7, 130, Pc.Gold, w = 118)
            }
            BImage(PcBesteon.BACK_ARROW, 129, 142, text)
            BHit(124, 140, 21, 15) { ui.open = false }
        }
    }

    @Composable
    private fun Themes(ui: Ui, text: Color, border: Color, fill: Color, onChanged: () -> Unit) {
        val all = themes()
        val pages = (all.size + THEMES_PER_PAGE - 1) / THEMES_PER_PAGE
        val page = ui.themePage.coerceIn(0, (pages - 1).coerceAtLeast(0))
        // Auto Pokemon Themes (AutoPokemonThemes, the phone's AutoTheme): the panel in the lead's colours; off gives the
        // theme picked below back. A theme picked while it is on becomes the one it gives back.
        Checkbox(9, rowY(0), TrackerOptions.autoPokemonThemes, border, fill)
        BText("Auto Pok\u00e9mon Themes", 18, rowY(0) - 2, text)
        BHit(9, rowY(0), 150 - 12, 8) {
            TrackerOptions.autoPokemonThemes = !TrackerOptions.autoPokemonThemes; TrackerOptions.save()
            if (!TrackerOptions.autoPokemonThemes) com.ironmonone.app.AutoTheme.release()
            onChanged()
        }
        val now = com.ironmonone.app.AutoTheme.userTheme()
        all.drop(page * THEMES_PER_PAGE).take(THEMES_PER_PAGE).forEachIndexed { i0, p ->
            val i = i0 + 1
            val y = rowY(i)
            Checkbox(9, y, same(p.theme, now), border, fill)
            BText(p.name, 18, y - 2, text)
            // A swatch of the theme's own colours after the name: page, boxes, text, the three text accents.
            val swatch = listOf(p.theme.page, p.theme.ground, p.theme.border, p.theme.text, p.theme.positive, p.theme.negative, p.theme.gold)
            swatch.forEachIndexed { k, col -> Box(Modifier.offset((104 + k * 5).rp, y.rp).size(5.rp, 8.rp).background(col)) }
            BHit(9, y, 150 - 12, 11) {
                // The theme already in use is left as it is (it used to reset to the default theme).
                if (!same(p.theme, now)) {
                    com.ironmonone.app.AutoTheme.suspend()
                    ThemePresets.apply(p)
                    com.ironmonone.app.AutoTheme.resume()
                    onChanged()
                }
            }
        }
        if (pages > 1) {
            BImage(PcBesteon.LEFT_ARROW, 48, 142, text)
            BText("${page + 1}/$pages", 60, 142, text, w = 25, align = TextAlign.Center)
            BImage(PcBesteon.RIGHT_ARROW, 87, 142, text)
            BHit(46, 141, 13, 11) { ui.themePage = (page - 1 + pages) % pages }
            BHit(86, 141, 13, 11) { ui.themePage = (page + 1) % pages }
        }
    }

    /**
     * Play as your Pokemon (Sprite Is Me by UTDZac, MIT; the phone's SpriteIsMeSettings, kept in "play as.txt"): the
     * switch, who you walk as (your lead, a Pokemon you always use, your own sprite) and your own sprite's files.
     */
    @Composable
    private fun PlayAs(ui: Ui, text: Color, border: Color, fill: Color, onChanged: () -> Unit) {
        val s = com.ironmonone.app.SpriteIsMeSettings
        fun saved() { s.save(); onChanged() }
        val rows = listOf<Triple<String, Boolean?, () -> Unit>>(
            Triple("Play as your Pok\u00e9mon", s.on) { s.on = !s.on; saved() },
            Triple("Your lead Pok\u00e9mon", s.who == com.ironmonone.app.SpriteIsMeSettings.Who.LEAD) { s.who = com.ironmonone.app.SpriteIsMeSettings.Who.LEAD; saved() },
            Triple("Always use: " + ui.alwaysName.ifEmpty { "(click to pick)" }, s.who == com.ironmonone.app.SpriteIsMeSettings.Who.ALWAYS) {
                if (s.who == com.ironmonone.app.SpriteIsMeSettings.Who.ALWAYS) { s.who = com.ironmonone.app.SpriteIsMeSettings.Who.LEAD; saved() }
                else if (s.always <= 0) ui.onAction(Action.PLAY_AS_ALWAYS) else { s.who = com.ironmonone.app.SpriteIsMeSettings.Who.ALWAYS; saved() } },
            Triple("   Shiny", s.alwaysShiny) { s.alwaysShiny = !s.alwaysShiny; saved() },
            Triple("Your own sprite: " + when (s.own) { com.ironmonone.app.SpriteIsMeSettings.Own.PICTURE -> "a picture"; com.ironmonone.app.SpriteIsMeSettings.Own.SHEET -> "a sheet set"; else -> "none" },
                s.who == com.ironmonone.app.SpriteIsMeSettings.Who.OWN) {
                if (s.who == com.ironmonone.app.SpriteIsMeSettings.Who.OWN) { s.who = com.ironmonone.app.SpriteIsMeSettings.Who.LEAD; saved() }
                else if (s.own != com.ironmonone.app.SpriteIsMeSettings.Own.NONE) { s.who = com.ironmonone.app.SpriteIsMeSettings.Who.OWN; saved() }
                else ui.onAction(Action.PLAY_AS_PICTURE) },
            Triple("+ Choose a picture", null) { ui.onAction(Action.PLAY_AS_PICTURE) },
            Triple("+ Choose a sheet set", null) { ui.onAction(Action.PLAY_AS_SHEETS) },
            Triple("+ Remove my sprite", null) { ui.onAction(Action.PLAY_AS_REMOVE) },
            Triple("+ Pick who to always use", null) { ui.onAction(Action.PLAY_AS_ALWAYS) },
        )
        rows.forEachIndexed { i, (label, on, click) ->
            val y = rowY(i)
            val inset = label.startsWith("   ")
            if (on != null) Checkbox(if (inset) 18 else 9, y, on, border, fill)
            BText(label.trimStart(), if (inset) 27 else 18, y - 2, if (on == null) Pc.Positive else text)
            BHit(9, y - 1, 150 - 12, 10, click)
        }
    }

    /** Drawing.drawButton's CHECKBOX: the 8 by 8 box, an X in Positive text when on. */
    @Composable
    private fun Checkbox(x: Int, y: Int, on: Boolean, border: Color, fill: Color) {
        BRect(x, y, 8, 8, border, fill)
        if (on) Canvas(Modifier.offset(x.rp, y.rp).size(9.rp, 9.rp)) {
            val u = size.width / 9f
            for (k in 1..7) {
                drawRect(Pc.Positive, Offset(k * u, k * u), androidx.compose.ui.geometry.Size(u, u))
                drawRect(Pc.Positive, Offset(k * u, (8 - k) * u), androidx.compose.ui.geometry.Size(u, u))
            }
        }
    }

    /** The same colours on the panel: the eight, and the lower box and header they fall back to. */
    private fun same(a: com.ironmonone.app.WholeTheme, b: com.ironmonone.app.WholeTheme) =
        a.keys == b.keys && a.header == b.header && a.lowerText == b.lowerText && a.lowerBorder == b.lowerBorder && a.lowerGround == b.lowerGround

    /** Drawing.ColorEffects.DARKEN over a colour. */
    private fun darken(c: Color) = Color(c.red * 0.75f, c.green * 0.75f, c.blue * 0.75f, c.alpha)
}
