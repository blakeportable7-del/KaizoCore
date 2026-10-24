package com.ironmonone.pcui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ironmonone.app.EnemyCard
import com.ironmonone.app.referenceWidth
import com.ironmonone.app.InfoRules
import com.ironmonone.app.LocalPcCtx
import com.ironmonone.app.LocalPcReferenceRows
import com.ironmonone.app.PartyCard
import com.ironmonone.app.Pc
import com.ironmonone.app.PcAssets
import com.ironmonone.app.PcCanvas
import com.ironmonone.app.PcCarousel
import com.ironmonone.app.PcCrispText
import com.ironmonone.app.PcDesktopCtx
import com.ironmonone.app.PcMove
import com.ironmonone.app.PcRef
import com.ironmonone.app.PixText
import com.ironmonone.app.StatMarks
import com.ironmonone.app.TrackerOptions
import com.ironmonone.app.WalkingPals
import com.ironmonone.app.enemyMoveContext
import com.ironmonone.app.ownMoveContext
import com.ironmonone.app.pcDesktopCtx
import com.ironmonone.app.rp
import com.ironmonone.tracker.TrackerState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.roundToInt

/**
 * The PC panel: besteon's TrackerScreen (PcBesteon: your Pokemon, the opponent, the carousel, the gear and its
 * settings screen, PcSettingsScreen) drawn into the 150x160 area right of the game in BizHawk at TrackerScreen.lua's
 * own x and y, and at the window's scale (a 3x window gets a 450x480 picture, drawn 1:1 on BizHawk's client surface), so every glyph is a
 * hard pixel edge at any size. What it shows follows besteon's: your Pokemon out of battle, the opponent in battle
 * unless "Auto swap to enemy" is off or the player toggled the view (Start, as besteon's "Toggle view").
 */
/** A move or ability description covering the card until the back arrow. */
class Inspect {
    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var open by mutableStateOf(false)
    fun show(title: String, body: String) { this.title = title; this.body = body; open = true }
    fun close() { open = false }
}

object PcPanel {
    /** besteon's Constants.SCREEN.RIGHT_GAP x HEIGHT, in game pixels. */
    const val WIDTH = 150
    const val HEIGHT = 160

    /** Everything the panel draws, read off the tracker and the run's own data. */
    data class Input(
        val state: TrackerState?,
        val attempt: Int,
        /** The run's marks, notes, encounters, seen moves (StatMarks in the run's folder); null draws without them. */
        val marks: StatMarks? = null,
        val routeName: String? = null,
        /** Your card even in battle (the phone's swap), when the player asked for it. */
        val showOwn: Boolean = false,
        /** The tracker's title before a game is read. */
        val title: String = "KaizoCore",
        /** Bumped when [marks] changed, so a changed mark draws again (StatMarks itself is one object for the run). */
        val version: Int = 0,
        /** The note on the Pokemon the panel shows (the carousel's note line). */
        val note: String = "",
        /**
         * The run's ending, latched by the session (PLAN row 6): the game over card or the win card stands in for the
         * battle and the party until Continue playing or Retry, as the reference's GameOverScreen does; the tracker's
         * live read says LOST only while the lost battle lasts.
         */
        val over: PcOver? = null,
        /** The run's favorites for the start screen (PLAN row 8): the boxes the run counts, with their pictures. */
        val favorites: List<PcFavorite> = emptyList(),
        /** The species' level-up move levels (the tracker's learnset), for the opponent's "Moves 2/9 (21)". */
        val moveLevels: (Int) -> List<Int> = { emptyList() },
        /** A move's row from the game's table, for the opponent's moves seen in earlier battles. */
        val moveRowFor: (Int) -> com.ironmonone.tracker.MoveRow? = { null },
        /** The log viewer, while it is open over the game (PLAN row 7): the panel shows its search screen or Pokemon card. */
        val log: PcLogOverlay.Shared? = null,
        /** A species' note, for the log's Pokemon card. */
        val noteFor: (Int) -> String = { "" },
        /** "Grade my notes" (StatMarkingScoreSheet) over the game over card, until its back arrow. */
        val scoreSheet: com.ironmonone.app.ScoreSheet.Result? = null,
        /** besteon's ball picker (in the lab with no Pokemon): the random ball, or the balls holding a favorite. */
        val ballPicker: PcBallPick? = null,
        /** GachaMon's stars for your Pokemon on view (now, when it joined), beside the heals when "Track PC Heals" is off. */
        val gachaStars: Pair<Int, Int>? = null,
        /** The bottom strip's item now (the session's PcCarousel); null shows the badges, or the note on the opponent. */
        val strip: PcStrip? = null,
        /** This run's IronMON mode ("kaizo"), for the ban marks. */
        val rulesMode: String = "kaizo",
        /** The ball being confirmed in the lab (Program.checkForStarterSelection): the info card replaces the start screen. */
        val starter: StarterFacts? = null,
        /** The battle's own list and the ROM's move data, for the real hit chance and move power (PcReal); null out of battle. */
        val battle: PcBattleFacts? = null,
    )

    /** The resistance chart opened from a name, a sprite, or the starter card. */
    class ChartUi {
        var types by androidx.compose.runtime.mutableStateOf<List<Int>?>(null)
        var natDex by androidx.compose.runtime.mutableStateOf(false)
        fun open(types: List<Int>, natDex: Boolean) { this.types = types; this.natDex = natDex }
        fun close() { types = null }
    }

    /** How far the favorites strip has been moved, in favorite-widths. The wheel and the arrows change it. The strip also crawls on its own. */
    class FavScroll {
        var offset by androidx.compose.runtime.mutableStateOf(0)
        /** [dir] 1 moves toward the later favorites, -1 toward the earlier ones. */
        fun step(dir: Int) { offset += dir }
    }

    /** InfoScreen while a starter's picture is up: the facts the card in the screenshot shows. */
    data class StarterFacts(
        val species: Int,
        val name: String,
        val types: List<String>,
        val bst: String,
        val exp: String,
        val weight: String,
        val evolution: String,
        val levels: List<Int>,
        val weak: List<Pair<String, Double>>,
        val note: String,
    )

    /** Whether the phone records this battle's opponent as an encounter (a Pokemon Tower ghost is not: GhostCard). */
    fun recordsEncounter(st: TrackerState): Boolean = com.ironmonone.app.GhostCard.recordsEncounter(st)

    /** The species picture as the gear's Pictures choice says (PcPictures): the GBA set, Walking Pals, the player's own. */
    fun sprite(species: Int, hns: Boolean): ImageBitmap? = PcPictures.sprite(species, hns)

    @Composable
    fun View(
        input: Input, onCycleMark: (Int, Int) -> Unit = { _, _ -> }, onOver: (PcOverAction) -> Unit = {}, onEditFavorites: () -> Unit = {},
        settings: PcSettingsScreen.Ui = PcSettingsScreen.Ui(), onSettingsChanged: () -> Unit = {}, onPcHeal: (Int) -> Unit = {},
        onNote: () -> Unit = {}, onReroll: () -> Unit = {}, typing: PcTypeScreen.Ui = PcTypeScreen.Ui(),
        inspect: Inspect = Inspect(), onMove: (PcMove) -> Unit = {}, onAbility: (String) -> Unit = {},
        onGacha: () -> Unit = {},
        chart: ChartUi = ChartUi(), favScroll: FavScroll = FavScroll(), screens: PcScreens = PcScreens(),
    ) {
        CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides PcNoIndication,
            LocalTextStyle provides TextStyle(platformStyle = PcCrispText),
            LocalPcCtx provides pcDesktopCtx,
            LocalPcReferenceRows provides true,
        ) {
            Box(Modifier.fillMaxSize().background(Pc.Page)) {
                PcCanvas(Modifier.width(WIDTH.dp)) {
                    val st = input.state
                    when {
                        typing.open -> PcTypeScreen.View(typing)
                        chart.types != null -> Resistances(chart)
                        screens.top != null -> PcScreens.View(screens, input, chart)
                        input.starter != null && (st == null || st.partyCount == 0) -> InfoCard(input.starter, input, chart, screens, onNote)
                        input.log?.data != null -> PcLogSide.Panel(input.log, input, onNote)
                        input.over != null && input.scoreSheet != null -> PcScoreSheet.View(input.scoreSheet, settings.scorePage, { sprite(it, hnsOf(input.state)) },
                            { settings.scorePage = it }, { settings.scorePage = 1; onOverAction(onOver, PcOverAction.GRADE_CLOSE) })
                        input.over != null -> PcGameOverCard(input.over, input.attempt, { sprite(it, hnsOf(input.state)) }, onOver)
                        inspect.open -> InspectCard(inspect.title, inspect.body) { inspect.close() }
                        settings.open -> PcSettingsScreen.View(settings, onSettingsChanged)
                        st == null || st.partyCount == 0 -> Column(Modifier.fillMaxWidth().padding(top = PcRef.MARGIN.rp)) { Start(input, onEditFavorites, onReroll, favScroll) { settings.open = true } }
                        else -> {
                            val hns = hnsOf(st)
                            val ctx = BesteonCtx(input, { sprite(it, hns) }, onCycleMark, { settings.open = true }, onNote, onPcHeal,
                                onPedometerReset = { PcPedometer.press(st.steps) },
                                onMove = { onMove(it) }, onAbility = { onAbility(it) },
                                onTypes = { chart.open(it, hns) }, onGacha = onGacha, screens = screens,
                                onGoal = { typing.openGoal(PcPedometer.goalSteps) })
                            BesteonTracker(ctx, st, viewingOwn = !st.inBattle || st.enemy == null || input.showOwn)
                        }
                    }
                }
            }
        }
    }

    private fun onOverAction(f: (PcOverAction) -> Unit, a: PcOverAction) = f(a)

    /** LogSearchScreen.AllowedTabViews. */
    private val SEARCH_TABS = setOf(PcLogOverlay.Tab.POKEMON, PcLogOverlay.Tab.TRAINERS, PcLogOverlay.Tab.ROUTES)

    /** The announcer's line for the run [attempt] (GameOverScreen.randomizeAnnouncerQuote), the same until [rerollQuote]. */
    fun quote(attempt: Int): String = com.ironmonone.app.DeathQuotes.shown(com.ironmonone.app.DeathQuotes.PC_SOURCE, attempt, com.ironmonone.app.PcGameOverQuotes)

    /** Another line for the same run, as a click on the card's icon draws one. */
    fun rerollQuote(attempt: Int): String = com.ironmonone.app.DeathQuotes.reroll(com.ironmonone.app.DeathQuotes.PC_SOURCE, attempt, com.ironmonone.app.PcGameOverQuotes)

    /**
     * Whether the state is a Heart & Soul game's, for the pictures. The tracker's Heart & Soul map reads its badges as
     * Johto's ("GSC", Hns.kt), the only Gen 3 game that does; until 2026-10-07 this asked for "HNS", which only the
     * staged samples carry, so a real run drew every picture the tracker had no game picture for by the wrong numbers.
     */
    fun hnsOf(st: TrackerState?): Boolean = st?.badgeSet?.startsWith("HNS") == true || st?.badgeSet == "GSC" ||
        st?.routeVersion?.contains("HNS", ignoreCase = true) == true

    /**
     * Before a party: besteon's StartupScreen. The top box with the tracker's name, the game and the attempt; the
     * bottom box with the "Favorites" header, the first three as pictures 45 apart (PokemonFavorite1 to 3 at x 8, 53,
     * 98) and the rest on an "Other Favourites" line, as the streamer's own start screen shows its nine. A click on
     * the favorites box opens the editor (PLAN row 8). In the lab ([Input.ballPicker]) the top is TrackerScreen's ball
     * picker instead, and the favorites box sits where TrackerScreen.drawFavorites puts it (76 down).
     */
    @Composable
    private fun Start(input: Input, onEditFavorites: () -> Unit, onReroll: () -> Unit, favScroll: FavScroll, onGear: () -> Unit) {
        val m = PcRef.MARGIN
        val w = WIDTH - 2 * m
        val pick = input.ballPicker
        Box(Modifier.fillMaxWidth().height((HEIGHT - m).dp)) {
            if (pick != null) {
                BallPicker(pick, onReroll)
                // Beside it, where TrackerScreen's stats box is: the attempt.
                BRect(96, 0, w - 96, 75, Pc.Border, Pc.Ground)
                BText("Attempt", 99, 2, Pc.Text)
                BText("${input.attempt}", 99, 12, Pc.Text)
                Favorites(input, onEditFavorites, favScroll, y = 76, h = HEIGHT - 2 * m - 76)
                // Drawn last so the attempt box, which starts on this x, cannot take the click.
                if (pick.favorites.isEmpty()) BHit(74, 28, 22, 24, onReroll)
            } else {
                val topH = 50
                Box(Modifier.size(w.dp, topH.dp).background(Pc.Ground).border(1.dp, Pc.Border))
                PixText("IRONMON TRACKER", PcRef.FONT, Pc.Gold, Modifier.offset(3.dp, 3.dp), weight = androidx.compose.ui.text.font.FontWeight.Medium)
                PixText("KaizoCore PC", PcRef.FONT, Pc.Text, Modifier.offset(3.dp, 14.dp).width((w - 6).dp))
                PixText(input.title, PcRef.FONT, Pc.Text, Modifier.offset(3.dp, 25.dp).width((w - 6).dp))
                PixText("Attempts: ${input.attempt}", PcRef.FONT, Pc.Text, Modifier.offset(3.dp, 36.dp))
                Favorites(input, onEditFavorites, favScroll, y = topH, h = HEIGHT - 2 * m - topH)
            }
            // The settings gear before there is a Pokemon too, so the theme, pictures and Play-as can be set in the lab.
            BImage(PcBesteon.GEAR, w - 12, 2, Pc.Text)
            BHit(w - 14, 0, 14, 13, onGear)
        }
    }

    /**
     * The favorites box. The nine walking sprites are not painted into this picture: the script slides them
     * across the box from their sheets (favbanner.txt), so the panel file is not rewritten to animate them.
     */
    @Composable
    private fun Favorites(input: Input, onEditFavorites: () -> Unit, favScroll: FavScroll, y: Int, h: Int) {
        val w = WIDTH - 2 * PcRef.MARGIN
        BRect(0, y, w, h, Pc.LowerBorder, Pc.LowerGroundX ?: Pc.Ground)
        // The whole box opens the editor, not only the word "Favorites".
        BHit(0, y, w, h) { onEditFavorites() }
        androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides (Pc.LowerGroundX ?: Pc.Ground)) {
            PixText("Favorites", PcRef.FONT, Pc.Header, Modifier.offset(3.dp, (y + 2).dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { onEditFavorites() }, weight = androidx.compose.ui.text.font.FontWeight.Medium)
            if (input.favorites.isEmpty()) PixText("Click to set your favorites.", PcRef.FONT - 1, Pc.Dim, Modifier.offset(3.dp, (y + 16).dp).clickable { onEditFavorites() })
        }
    }

    /** Constants.PixelImages.POKEBALL (12x12): 1 the outline, 2 the top, 3 the white. */
    private val POKEBALL = listOf("000011110000", "001122221100", "012223222210", "012232222210", "122222222221", "122221122221",
        "112213312211", "131113311131", "013331133310", "013333333310", "001133331100", "000011110000")

    /** PokeBalls.ColorList and ColorListGray (Utils.calcGrayscale(0xFFF04037, 0.6)). */
    private val BALL = listOf(0xFF000000, 0xFFF04037, 0xFFFFFFFF)
    private val BALL_GRAY = listOf(0xFF000000, grayOf(0xFFF04037, 0.6), 0xFFFFFFFF)

    private fun grayOf(argb: Long, scale: Double): Long {
        val r = (argb shr 16 and 0xFF).toDouble(); val g = (argb shr 8 and 0xFF).toDouble(); val b = (argb and 0xFF).toDouble()
        val gray = 0.2989 * r + 0.5870 * g + 0.1140 * b
        fun c(v: Double) = (gray * scale + v * (1 - scale)).coerceIn(0.0, 255.0).toLong()
        return 0xFF000000L or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    /** The resistance chart (TypeDefensesScreen), from a name, a sprite, or the starter card. */
    @Composable
    private fun Resistances(chart: ChartUi) {
        val types = chart.types ?: return
        val buckets = com.ironmonone.tracker.Gen3Types.defenses(types, natDex = chart.natDex)
        Box(Modifier.size(WIDTH.rp, HEIGHT.rp).background(Pc.Page)) {
          androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides Pc.Page) {
            BText("RESISTANCES", 5, 3, Pc.Header)
            BImage(PcBesteon.BACK_ARROW, 129, 2, Pc.Text)
            // Nothing else on the chart takes a click, so all of it closes the chart.
            BHit(0, 0, WIDTH, HEIGHT) { chart.close() }
            var y = 16
            listOf(0.0 to "0x", 0.25 to "1/4x", 0.5 to "1/2x", 2.0 to "2x", 4.0 to "4x").forEach { (mult, label) ->
                val names = buckets[mult] ?: return@forEach
                BText(label, 5, y, if (mult < 1.0) Pc.Positive else Pc.Negative)
                wrapWords(names.joinToString(" "), 22).take(2).forEachIndexed { i, line -> BText(line, 36, y + i * 9, Pc.Text) }
                y += 18
            }
          }
        }
    }

    /** InfoScreen POKEMON_INFO: the lab's offered Pokemon, or any Pokemon a click opens (PcScreens). The lines match the log's Pokemon info card. */
    @Composable
    internal fun InfoCard(s: StarterFacts, input: Input, chart: ChartUi, screens: PcScreens, onNote: () -> Unit) {
        val ctx = LocalPcCtx.current
        val hns = hnsOf(input.state) || input.state == null
        val text = Pc.Text
        val lower = Pc.LowerText
        val lowerFill = Pc.LowerGroundX ?: Pc.Ground
        val typeIds = s.types.mapNotNull { n -> (0..18).firstOrNull { com.ironmonone.tracker.Gen3Types.name(it).equals(n, true) } }
        val openChart = { if (typeIds.isNotEmpty()) chart.open(typeIds, hns) }
        Box(Modifier.size(WIDTH.rp, HEIGHT.rp).background(Pc.Page)) {
            BRect(5, 5, 140, 58, Pc.Border, Pc.Ground)
            // y=3 sits on the box's top border and the panel clips the tops of the letters.
            BText(s.name.uppercase(), 5, 7, text)
            (PcPictures.picture(s.species, com.ironmonone.app.HnsNumbers.toPack(s.species)) ?: sprite(s.species, hns))
                ?.let { BPicture(it, 113, 2, 32, 32) }
            s.types.take(2).forEachIndexed { i, t ->
                val ty = 34 + 14 * i
                // h=13 draws 14px: a 12px icon under the top border, and a bottom border the icon does not cover.
                BRect(112, ty, 31, 13, Pc.Border, Pc.Border)
                PcAssets.typeIcon(ctx, t)?.let { BPicture(it, 113, ty + 1, 30, 12) }
            }
            // The picture and the type boxes: the resistances, as Show resistances does.
            BHit(112, 2, 33, 60) { openChart() }
            BText("BST: ${s.bst}", 7, 20, text)
            BText("EXP: ${s.exp}", 49, 20, text)
            BText("Weight:", 7, 30, text)
            BText(s.weight, 49, 30, text)
            BText("Evolution:", 7, 40, text)
            BText(s.evolution, 49, 40, text)
            BRect(5, 63, 140, 92, Pc.LowerBorder, lowerFill)
            // Everything from here down is on the lower box.
            androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides lowerFill) {
            BText("Learns a move at level:", 6, 64, lower)
            BText("History", 114, 64, lower)
            Box(Modifier.offset(115.rp, 73.rp).size(referenceWidth("History").rp, 1.rp).background(lower))
            BHit(113, 64, 30, 10) { screens.push(PcScreens.Screen.MoveHistory(s.species, s.name, s.levels)) }
            val levels = s.levels.filter { it > 1 }
            PcLogSide.LevelBoxes(levels, null, 6, 75, Pc.LowerBorder, lowerFill, lower)
            var by = 75 + 28
            if (levels.size <= (if (levels.size <= 16) 8 else 9)) by -= 10
            if (levels.isNotEmpty()) BHit(6, 75, 138, by - 75 - 1) { screens.push(PcScreens.Screen.MovesLearned(s.species, null)) }
            BText("Weak to:", 6, by, lower)
            BText("Show resistances", 0, by, lower, w = 143, align = androidx.compose.ui.text.style.TextAlign.End)
            Box(Modifier.offset((143 - referenceWidth("Show resistances") - 2).rp, (by + 9).rp).size((referenceWidth("Show resistances") + 3).rp, 1.rp).background(lower))
            BHit(6, by, 139, 12) { openChart() }
            by += 13
            // The weaknesses under it open the same chart.
            if (s.weak.isNotEmpty()) BHit(6, by - 1, 139, minOf(128, by + 13 * 3) - by + 13) { openChart() }
            if (s.weak.isEmpty()) BText("Has no weaknesses", 12, by, lower)
            var tx = 12
            s.weak.forEach { (t, m) ->
                if (by > 128) return@forEach
                BRect(tx - 1, by - 1, 31, 13, Pc.LowerBorder, Pc.LowerBorder)
                PcAssets.typeIcon(ctx, t)?.let { BPicture(it, tx, by, 30, 12) }
                if (m > 2.0) {
                    Box(Modifier.offset(tx.rp, by.rp).size(30.rp, 2.rp).background(androidx.compose.ui.graphics.Color.White))
                    Box(Modifier.offset(tx.rp, (by + 10).rp).size(30.rp, 2.rp).background(androidx.compose.ui.graphics.Color.White))
                }
                tx += 31
                if (tx > 120) { tx = 12; by += 13 }
            }
            if (s.note.isBlank()) BText("(Leave a note)", 8, 141, lower) else BText(s.note, 8, 141, lower, w = 115)
            BHit(6, 140, 115, 13, onNote)
            }
        }
    }

    /**
     * TrackerScreen.drawBallPicker at its own x and y: the three balls (Left 17,18; Middle 40,26; Right 63,18), the
     * down arrow over the chosen one and the others grey, the die (RerollBallPicker, 81,36), and under them "Randomly
     * chosen ball:" with its name. A ball holding a favorite the run may take is marked instead, and named; the die
     * is left out then, since that ball is the pick.
     */
    @Composable
    private fun BallPicker(pick: PcBallPick, onReroll: () -> Unit) {
        val text = Pc.Text
        BRect(0, 0, 96, 52, Pc.Border, Pc.Ground)
        val at = listOf(17 to 18, 40 to 26, 63 to 18)
        val marked = if (pick.favorites.isNotEmpty()) pick.favorites.map { it.first }.toSet() else setOf(pick.chosen)
        at.forEachIndexed { i, (x, y) ->
            if (i in marked) BImage(PcLogOverlay.DOWN_ARROW, x + 1, y - 13, text)
            BPix(POKEBALL, x, y, bColors(if (i in marked) BALL else BALL_GRAY))
        }
        if (pick.favorites.isEmpty()) {
            BPix(com.ironmonone.app.PcCategoryGlyphs.DICE.map { r -> r.joinToString("") }, 81, 36, listOf(text))
            BHit(74, 28, 22, 24, onReroll)
        }
        val names = listOf("Left", "Middle", "Right")
        val (top, bottom, color) = when (pick.favorites.size) {
            0 -> Triple("Randomly chosen ball:", names[pick.chosen.coerceIn(0, 2)], Pc.Gold)
            1 -> Triple("Favorite, ${names[pick.favorites[0].first].lowercase()} ball:", pick.favorites[0].second, Pc.Positive)
            else -> Triple("Favorites in the balls:", pick.favorites.joinToString(", ") { it.second }, Pc.Positive)
        }
        BRect(0, 52, 96, 23, Pc.Border, Pc.Ground)
        BText(top, 1, 53, text, w = 95, align = androidx.compose.ui.text.style.TextAlign.Center)
        BText(bottom, 1, 63, color, w = 95, align = androidx.compose.ui.text.style.TextAlign.Center)
    }

    /**
     * One panel kept alive between frames, at the window's [scale] (1 reference pixel = [scale] picture pixels): each
     * [render] lays out and draws once (about 10 ms warm), and a click goes to the same composition, so the cards' own
     * buttons answer as on the phone.
     */
    class Renderer(
        filesDir: File, initial: Input, val scale: Float = 1f,
        /** Each drawn pixel repeated this many times in the picture handed over (PcBmp.upscale). */
        val upscale: Int = 1,
        /** The settings screen's state (open, tab, theme page), kept by the caller across a remade renderer. */
        val settings: PcSettingsScreen.Ui = PcSettingsScreen.Ui(),
        /** The on-screen keyboard's state (the note, the favorites, Always use), kept by the caller the same way. */
        val typing: PcTypeScreen.Ui = PcTypeScreen.Ui(),
        val inspect: Inspect = Inspect(),
        val chart: ChartUi = ChartUi(),
        val favScroll: FavScroll = FavScroll(),
        val screens: PcScreens = PcScreens(),
    ) : AutoCloseable {
        @Volatile var input: Input = initial
        private var current = androidx.compose.runtime.mutableStateOf(initial)
        var onCycleMark: (Int, Int) -> Unit = { _, _ -> }
        /** A click on the game over card's buttons (PcGameOverCard). */
        var onOver: (PcOverAction) -> Unit = {}
        /** A click on the start screen's favorites (the editor opens in BizHawk). */
        var onEditFavorites: () -> Unit = {}
        /** A setting changed on the settings screen (the panel draws it at once; the session keeps the file). */
        var onSettingsChanged: () -> Unit = {}
        /** The PC heal counter's + (1) or - (-1). */
        var onPcHeal: (Int) -> Unit = {}
        /** A left click on "(Leave a note)": the note window, as a right click opens it. */
        var onNote: () -> Unit = {}
        /** The ball picker's die. */
        var onReroll: () -> Unit = {}
        var onMove: (PcMove) -> Unit = {}
        var onAbility: (String) -> Unit = {}
        var onGacha: () -> Unit = {}
        private val scene: ImageComposeScene
        val width: Int = (WIDTH * scale).roundToInt()
        val height: Int = (HEIGHT * scale).roundToInt()

        init {
            pcDesktopCtx = PcDesktopCtx(filesDir)
            // WalkingPals keeps its tables in Compose state that its own thread fills: made here, outside any
            // composition, so that thread's write never meets a snapshot not yet applied. (The PC panel draws the
            // still sprites; the walking ones are not in the jar.)
            WalkingPals.ready(pcDesktopCtx)
            scene = ImageComposeScene(width, height, Density(scale)) {
                // Every word on the card is kept readable on what is behind it (PixText, readableInk): the upper box's fill,
                // where most words are, unless an area says its own (the page, the lower box).
                androidx.compose.runtime.CompositionLocalProvider(com.ironmonone.app.LocalInkGround provides com.ironmonone.app.Pc.Ground) {
                View(current.value, { sp, i -> onCycleMark(sp, i) }, { onOver(it) }, { onEditFavorites() }, settings, { onSettingsChanged() }, { onPcHeal(it) }, { onNote() }, { onReroll() }, typing, inspect, { onMove(it) }, { onAbility(it) }, { onGacha() }, chart, favScroll, screens)
                }
            }
        }

        private var clock = 0L
        /** The last [render]'s milliseconds: drawing, then encoding. */
        var lastTimes = 0.0 to 0.0
            private set
        var lastDetail = ""
            private set

        /** Something on the panel wants drawing again (the carousel turned). */
        fun hasInvalidations(): Boolean = scene.hasInvalidations()

        /** The panel for [input] as BMP bytes (PcBmp). */
        fun render(input: Input): ByteArray {
            val img = draw(input)
            val t1 = System.nanoTime()
            val out = try { PcBmp.of(img, upscale) } finally { img.close() }
            lastTimes = lastTimes.first to (System.nanoTime() - t1) / 1e6
            return out
        }

        /** The panel for [input] as a picture for a window, at this renderer's pixels. */
        fun renderImage(input: Input): java.awt.image.BufferedImage {
            val img = draw(input)
            val w = img.width; val h = img.height
            val bmp = org.jetbrains.skia.Bitmap()
            bmp.allocPixels(org.jetbrains.skia.ImageInfo(w, h, org.jetbrains.skia.ColorType.BGRA_8888, org.jetbrains.skia.ColorAlphaType.PREMUL))
            img.readPixels(bmp, 0, 0)
            val px = bmp.readPixels(bmp.imageInfo, w * 4, 0, 0) ?: ByteArray(w * h * 4)
            bmp.close()
            img.close()
            val argb = IntArray(w * h)
            for (i in argb.indices) {
                val o = i * 4
                argb[i] = (0xFF shl 24) or ((px[o + 2].toInt() and 0xFF) shl 16) or ((px[o + 1].toInt() and 0xFF) shl 8) or (px[o].toInt() and 0xFF)
            }
            return java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB).apply { setRGB(0, 0, w, h, argb, 0, w) }
        }

        private fun draw(input: Input): org.jetbrains.skia.Image {
            this.input = input
            current.value = input
            // The state write reaches the composition through the snapshot's apply notifications, which nothing else
            // sends here; a changed input then recomposes on this frame. What the recomposition invalidates (a card
            // laid out for the first time) is drawn on a second frame at once, so one render shows the new input.
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            val t0 = System.nanoTime()
            val mx = java.lang.management.ManagementFactory.getThreadMXBean()
            val c0 = mx.currentThreadCpuTime
            clock += 16_000_000L
            var img = scene.render(clock)
            val ta = System.nanoTime()
            var twice = false
            if (scene.hasInvalidations()) { twice = true; clock += 16_000_000L; img.close(); img = scene.render(clock) }
            val t1 = System.nanoTime()
            lastDetail = "first %.1f ms, second %s, cpu %.1f ms".format((ta - t0) / 1e6, if (twice) "%.1f ms".format((t1 - ta) / 1e6) else "none", (mx.currentThreadCpuTime - c0) / 1e6)
            lastTimes = (t1 - t0) / 1e6 to 0.0
            return img
        }

        /** A left click at panel pixel ([x], [y]), in reference (game) pixels. */
        fun click(x: Int, y: Int) = clickAt(x + 0.5f, y + 0.5f)

        /** A left click at ([x], [y]) in reference pixels, between pixels too (the tracker window's own mouse). */
        fun clickAt(x: Float, y: Float) {
            val at = Offset(x * scale, y * scale)
            clock += 16_000_000L
            scene.sendPointerEvent(PointerEventType.Move, at, timeMillis = clock / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Press, at, timeMillis = clock / 1_000_000)
            clock += 50_000_000L
            scene.sendPointerEvent(PointerEventType.Release, at, timeMillis = clock / 1_000_000)
        }

        /**
         * Whether ([x], [y]), in reference pixels, is over something clickable: the pointer leaves the card (every click area
         * counts it out), then comes to the mouse (the areas under it count it in).
         */
        fun hoverAt(x: Float, y: Float): Boolean {
            clock += 16_000_000L
            scene.sendPointerEvent(PointerEventType.Move, Offset(-1000f, -1000f), timeMillis = clock / 1_000_000)
            PcHover.count = 0
            clock += 16_000_000L
            scene.sendPointerEvent(PointerEventType.Move, Offset(x * scale, y * scale), timeMillis = clock / 1_000_000)
            return PcHover.count > 0
        }

        override fun close() = scene.close()
    }
}
