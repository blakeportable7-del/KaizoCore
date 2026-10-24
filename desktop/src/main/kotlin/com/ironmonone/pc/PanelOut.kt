package com.ironmonone.pc

import com.ironmonone.app.WalkingPals
import com.ironmonone.app.pcDesktopCtx
import com.ironmonone.pcui.PcFavorite
import com.ironmonone.pcui.PcPanel
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The panel beside the game in BizHawk (PLAN row 4, section 1.2): drawn by pcui's PcPanel when what it shows changed,
 * written as a new PNG under a new name each time (BizHawk caches a drawn picture by its path), then named in
 * `panel.txt` for kaizocore-hns.lua, which draws it at x 240 every frame. The three newest pictures are kept, so one
 * BizHawk is drawing is never the one being replaced. Everything runs on the session's own thread: the panel's
 * Compose scene is made and used there only.
 */
class PanelOut(private val dir: File, private val say: (String) -> Unit) {
    private var renderer: PcPanel.Renderer? = null
    private var last: PcPanel.Input? = null
    private var seq = 0
    /** The species the panel shows a card for (the opponent in battle, else your lead), for a note. */
    var shownSpecies = 0
    /** The offered starter's name, for the note screen before that Pokemon is in the party. */
    var shownName = ""
        private set
    /** Set by the session: a click on one of the opponent's stat boxes. */
    var onCycleMark: (Int, Int) -> Unit = { _, _ -> }
    /** Set by the session: a click on one of the game over card's buttons (PLAN row 6). */
    var onOver: (com.ironmonone.pcui.PcOverAction) -> Unit = {}
    /** Set by the session: a click on the start screen's favorites (PLAN row 8). */
    var onEditFavorites: () -> Unit = {}
    /** Set by the session: a setting changed on the gear's settings screen. */
    var onSettingsChanged: () -> Unit = {}
    /** Set by the session: the PC heal counter's + (1) or - (-1). */
    var onPcHeal: (Int) -> Unit = {}
    /** Set by the session: a left click on "(Leave a note)". */
    var onNote: () -> Unit = {}
    /** Set by the session: the ball picker's die (RerollBallPicker). */
    var onReroll: () -> Unit = {}
    /** The settings screen's state, kept across a renderer remade for a new window size. */
    val settings = com.ironmonone.pcui.PcSettingsScreen.Ui()
    /** The on-screen keyboard's state, kept the same way. */
    val typing = com.ironmonone.pcui.PcTypeScreen.Ui()
    /** A move or ability description, kept across a remade renderer. */
    val inspect = com.ironmonone.pcui.Inspect()
    val chart = com.ironmonone.pcui.PcPanel.ChartUi()
    /** Which favorite is at the top of the start screen's list. The wheel and the arrows move it. */
    val favScroll = com.ironmonone.pcui.PcPanel.FavScroll()
    /** besteon's screens opened from the card (History and the like), kept the same way. */
    val screens = com.ironmonone.pcui.PcScreens()
    var onMove: (com.ironmonone.app.PcMove) -> Unit = {}
    var onAbility: (String) -> Unit = {}
    var onGacha: () -> Unit = {}

    /** The window's scale: picture pixels per game pixel (kaizocore-hns.lua writes panel.want when it changes). */
    var scale: Float = 1f
        private set

    // Inside BizHawk the BMP is drawn smaller and enlarged as it is written (PcBmp.upscale). The window's card is drawn
    // at its full size up to [COMPOSE_MAX]: past that, composing the card (measured 30 to 160 ms at 6x, 900 by 960) was
    // the slow part, so it is drawn at [COMPOSE_MAX] and the window enlarges it with nearest-neighbour, which keeps
    // every edge on a pixel.
    private fun drawScale(): Float = if (window != null) scale.coerceAtMost(COMPOSE_MAX) else scale / com.ironmonone.pcui.PcBmp.upscale(scale)

    private fun r(): PcPanel.Renderer = renderer ?: PcPanel.Renderer(File(dir, "panel-files"), PcPanel.Input(null, 0), drawScale(), 1, settings, typing, inspect, chart, favScroll, screens).also {
        it.onCycleMark = { sp, i -> onCycleMark(sp, i) }
        it.onSettingsChanged = { onSettingsChanged() }
        it.onPcHeal = { d -> onPcHeal(d) }
        it.onNote = { onNote() }
        it.onOver = { a -> onOver(a) }
        it.onEditFavorites = { onEditFavorites() }
        it.onReroll = { onReroll() }
        it.onMove = { mv -> onMove(mv) }
        it.onAbility = { name -> onAbility(name) }
        it.onGacha = { onGacha() }
        renderer = it
    }

    /**
     * The tracker window, when the card has its own window (KaizoCore PC's normal way). The card is then handed to it in
     * memory, drawn at the window's pixels, and BizHawk draws none of it.
     */
    var window: TrackerWindow? = null

    /** The battle's lasting effects for the next card (the session's read), for the window's effects in developer mode. */
    @Volatile var fxDetails: com.ironmonone.tracker.GbaTracker.BattleDetails? = null

    /** The scale the script asked for; a change remakes the renderer and the next [show] draws. Returns whether it changed. */
    fun setScale(s: Float): Boolean {
        if (window != null) return false
        // The window's own scale, up to 2. A picture past that, redrawn every frame inside BizHawk, stalled the game.
        return rescale(s.coerceIn(1f, 2f))
    }

    /** The tracker window's pixels per card pixel (a whole number, so the sprites stay sharp). */
    fun setWindowScale(s: Int): Boolean = rescale(s.coerceIn(1, 12).toFloat())

    private fun rescale(want: Float): Boolean {
        if (kotlin.math.abs(want - scale) < 0.005f) return false
        scale = want
        runCatching { renderer?.close() }
        renderer = null
        last = null
        say("panel at %.2fx (%d by %d)".format(scale, (PcPanel.WIDTH * scale).toInt(), (PcPanel.HEIGHT * scale).toInt()))
        return true
    }

    /** Makes the scene and draws once, so the first real picture costs a frame, not a second. */
    fun warmUp() {
        val t = System.nanoTime()
        // The cards' code warmed by the JIT before the first click: one render left the first clicks 3 to 10 times slower
        // than later ones (measured 2026-10-07: 9 to 45 ms at 1x live, 2.7 ms warm).
        runCatching {
            val samples = listOf(com.ironmonone.pcui.Samples.enemy, com.ironmonone.pcui.Samples.party, null)
            val n = if (window != null) 8 else 30
            for (i in 0 until n) {
                val input = PcPanel.Input(samples[i % 3], i)
                if (window != null) r().renderImage(input) else r().render(input)
            }
            if (window != null) r().renderImage(PcPanel.Input(null, 0)) else r().render(PcPanel.Input(null, 0))
        }.onFailure { say("panel: $it") }
        say("panel ready in %.0f ms".format((System.nanoTime() - t) / 1e6))
    }

    /**
     * Draws [input] when it differs from the last one drawn, or when [force]d (after a click). [battle] goes on the
     * line for the script, which takes the state a battle began from when it turns on (Retry the battle).
     */
    fun show(input: PcPanel.Input, note: String, force: Boolean = false, battle: Boolean = false) {
        // A new picture only when the card changed. A timer used to paint a new file several times a second
        // so the sprites could walk. BizHawk keeps every file it has drawn, and deleting one it still has open
        // is the .NET error box. The picture is written when the tracker data changes, not on a clock.
        if (!force && input == last) return
        // A screen opened from the card is about what the card showed: a battle starting or ending, or the lab's
        // offered Pokemon changing, takes it away.
        // The resistance chart and a move or ability description go with them: the chart drew over the new battle's card.
        if (last != null && (input.state?.inBattle != last?.state?.inBattle || input.starter?.species != last?.starter?.species)) {
            screens.close(); chart.close(); inspect.close()
        }
        last = input
        val st = input.state
        val logMon = input.log?.let { sh -> (sh.side as? com.ironmonone.pcui.PcLogOverlay.Side.History)?.pokemon ?: sh.view.pokemon }
        val offered = input.starter
        shownSpecies = when {
            logMon != null -> input.log?.data?.speciesId(logMon) ?: 0
            offered != null && (st == null || st.partyCount == 0) -> offered.species
            st == null -> 0
            st.inBattle && st.enemy != null && !input.showOwn -> st.enemy!!.species
            else -> (st.lead ?: st.party.firstOrNull())?.mon?.species ?: 0
        }
        shownName = offered?.name.orEmpty()
        val w = window
        if (w != null) {
            // The game's changes come in bursts (an HP bar draining): the card is drawn at most once in GAP, the
            // newest state, so KaizoCore PC does not keep a core busy. A click is drawn at once.
            if (!force && System.nanoTime() - drawnAt < gap) {
                pending = Triple(input, note, battle)
                writeStatus(status(input, note, battle, null))
                return
            }
            drawWindow(w, input, note, battle, force)
            return
        }
        val png = try { r().render(input) } catch (e: Exception) { say("panel: $e"); return }
        val t1 = System.nanoTime()
        seq++
        val name = "panel-$seq.bmp"
        writeAtomic(File(dir, name), png)
        val t2 = System.nanoTime()
        if (force) r().lastTimes.let { (d, e) -> say("panel %d: drawn %.1f ms, encoded %.1f ms, written %.1f ms (%d bytes) [%s]".format(seq, d, e, (t2 - t1) / 1e6, png.size, r().lastDetail)) }
        writeStatus(status(input, note, battle, name))
        writeBanner(input)
        // Far behind the picture on screen. The newest files stay until BizHawk has let them go.
        File(dir, "panel-${seq - 48}.bmp").delete()
    }

    private var drawnAt = 0L
    /** [GAP], or half again the last card's drawing time when that is longer: the card never takes most of a core. */
    private var gap = GAP
    private var pending: Triple<PcPanel.Input, String, Boolean>? = null

    private fun drawWindow(w: TrackerWindow, input: PcPanel.Input, note: String, battle: Boolean, log: Boolean) {
        pending = null
        val t0 = System.nanoTime()
        val img = try { r().renderImage(input) } catch (e: Exception) { say("panel: $e"); return }
        seq++
        // The card is uncovered when no screen, log, ending or settings page is over it: only then do effects play on it.
        val uncovered = screens.top == null && input.log == null && input.over == null && input.scoreSheet == null && !settings.open
        w.showCard(img, banner(input), TrackerWindow.FxIn(input.state, input.showOwn, fxDetails, uncovered))
        w.setTyping(typing.open || logSearch(input))
        if (log) say("card %d: drawn in %.1f ms (%d by %d)".format(seq, (System.nanoTime() - t0) / 1e6, img.width, img.height))
        writeStatus(status(input, note, battle, null))
        drawnAt = System.nanoTime()
        gap = maxOf(GAP, (drawnAt - t0) * 3 / 2)
    }

    /** Draws a card held back by [show]'s spacing once its time has come. The session's loop calls it every turn. */
    fun flush() {
        val w = window ?: return
        val p = pending ?: return
        if (System.nanoTime() - drawnAt < gap) return
        drawWindow(w, p.first, p.second, p.third, false)
    }

    /**
     * panel.txt for the script. [file] is the picture BizHawk draws; with the tracker window there is none, and
     * window=1 tells the script to leave BizHawk's screen to the game.
     */
    private fun status(input: PcPanel.Input, note: String, battle: Boolean, file: String?): String {
        val st = input.state
        val repel = st?.takeIf { it.repelVisible && com.ironmonone.app.TrackerOptions.showRepel }?.repelSteps ?: 0
        val repelDur = st?.repelDuration ?: 100
        if (file == null) {
            // The card in its own window: the script draws only the repel over the game and keeps the battle's state, so
            // that is all it is told. A line that changed with every card (seq, species, the note) made a new panel.txt
            // for each one, and the script's first read of each waited on Windows Defender, on BizHawk's thread (about 20 ms,
            // 2026-10-09). seq changes only with the repel, which the script refreshes on a new seq.
            return listOf("seq=w$repel.$repelDur", "window=1", "battle=${if (battle) 1 else 0}", "repel=$repel", "repeldur=$repelDur",
                "typing=${if (typing.open) 1 else 0}").joinToString("\n", postfix = "\n")
        }
        return listOfNotNull("seq=$seq", file?.let { "file=$it" }, if (file == null) "window=1" else null,
            "species=$shownSpecies", "scale=%.2f".format(scale), "battle=${if (battle) 1 else 0}",
            "repel=${st?.takeIf { it.repelVisible && com.ironmonone.app.TrackerOptions.showRepel }?.repelSteps ?: 0}",
            "repeldur=${st?.repelDuration ?: 100}",
            "over=${if (input.over != null) 1 else 0}", "typing=${if (typing.open) 1 else 0}",
            "kind=${typing.kind?.name?.lowercase().orEmpty()}",
            "typed=" + escape(if (typing.open) typing.text else ""),
            "logtype=${if (logSearch(input)) 1 else 0}",
            "logq=" + escape(if (logSearch(input)) input.log?.view?.query.orEmpty() else ""),
            "note=" + escape(note)).joinToString("\n", postfix = "\n")
    }

    private var statusWritten: String? = null

    /** panel.txt, only when what it says changed: each new one costs the script a slow first read. */
    private fun writeStatus(text: String) {
        if (text == statusWritten) return
        statusWritten = text
        writeAtomic(File(dir, "panel.txt"), text.toByteArray(Charsets.UTF_8))
    }

    /** Whether the panel's own log search box is up (what is typed then goes to the log's search). */
    fun searching(input: PcPanel.Input? = last): Boolean = input != null && logSearch(input)

    /**
     * The nine favorites as sheets the script can slide and animate (one file per Pokemon, written once).
     * Each favorite is the Nat. Dex id on the name. Aggron is 384, drawn from the game's follower (PcPictures.GAME_FOLLOWER),
     * not walkingpals/384, which reads as the mega. Aggron-M is 1078 (walkingpals-ow/306-mega).
     */
    private fun writeBanner(input: PcPanel.Input) {
        val file = File(dir, "favbanner.txt")
        val text = banner(input)
        if (text == null) {
            if (file.isFile) file.delete()
            bannerText = null
            return
        }
        if (text == bannerText) return
        bannerText = text
        writeAtomic(file, text.toByteArray(Charsets.UTF_8))
    }

    /** The favorites strip for [input], or null when the start screen with favorites is not up. */
    private fun banner(input: PcPanel.Input): String? {
        val favs = input.favorites
        val st = input.state
        val start = favs.isNotEmpty() && input.starter == null && input.log == null && !typing.open && !settings.open &&
            !inspect.open && chart.types == null && screens.top == null && (st == null || st.partyCount == 0)
        if (!start) return null
        val lab = input.ballPicker != null
        val boxY = if (lab) 76 else 50
        val boxH = if (lab) PcPanel.HEIGHT - 10 - 76 else PcPanel.HEIGHT - 10 - 50
        val index = WalkingPals.index(pcDesktopCtx)
        val lines = ArrayList<String>()
        for (f in favs) {
            val line = bannerMon(index, f) ?: continue
            lines += line
        }
        val text = buildString {
            append("on=1\n")
            append("x=2\n")
            append("y=").append(5 + boxY + 14).append('\n')
            append("w=136\n")
            append("h=").append(boxH - 18).append('\n')
            append("slot=64\n")
            lines.forEach { append("mon=").append(it).append('\n') }
        }
        return text
    }

    private var bannerText: String? = null

    /** Set by the session: a Heart & Soul species' follower out of the game (the run's ROM, else the build's). */
    var follower: (Int) -> com.ironmonone.tracker.HnsPics.Follower? = { null }

    /** [f]'s strip from the game's follower, facing down (frames 0 and 1), for the species in PcPictures.GAME_FOLLOWER. */
    private fun bannerFollower(f: PcFavorite): String? {
        val sp = f.species?.takeIf { it in com.ironmonone.pcui.PcPictures.GAME_FOLLOWER } ?: return null
        val name = "fav-game-$sp.png"
        val out = File(dir, name)
        val fol = runCatching { follower(sp) }.getOrNull()?.takeIf { it.frames.size >= 2 } ?: return null
        if (!out.isFile) {
            val img = BufferedImage(fol.width * 2, fol.height, BufferedImage.TYPE_INT_ARGB)
            for (i in 0 until 2) img.setRGB(i * fol.width, 0, fol.width, fol.height, fol.frames[i], 0, fol.width)
            ImageIO.write(img, "png", out)
        }
        val who = f.name.replace("|", " ").replace("\n", " ")
        // The Heart & Soul strips in walkingpals-ow sit the same way: no x shift, 3 down, two frames of 32.
        return "$name|$who|${fol.width}|${fol.height}|0|3|32,32"
    }

    private fun bannerMon(index: WalkingPals.Index, f: PcFavorite): String? {
        bannerFollower(f)?.let { return it }
        val id = f.pack ?: return null
        val pal = index.find(id, WalkingPals.Dex.NAT_DEX) ?: return null
        val sheet = index.sheets(pal)?.get(WalkingPals.Anim.IDLE) ?: return null
        val art = index.art(pal, WalkingPals.Anim.IDLE)
        val name = "fav-${pal.pack.name.lowercase()}-${pal.key}.png"
        val out = File(dir, name)
        if (!out.isFile) {
            val img = com.ironmonone.app.PcDesktopCtx::class.java.getResourceAsStream("/assets/${art.path}")?.use { ImageIO.read(it) } ?: return null
            val argb = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB).also {
                it.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
            }
            ImageIO.write(argb, "png", out)
        }
        val who = f.name.replace("|", " ").replace("\n", " ")
        return "$name|$who|${sheet.w}|${sheet.h}|${sheet.x}|${sheet.y}|${sheet.durations.joinToString(",")}"
    }

    private fun logSearch(input: PcPanel.Input): Boolean {
        val sh = input.log ?: return false
        val v = sh.view
        return sh.side == null && v.pokemon == null && !v.inPage &&
            v.tab in setOf(com.ironmonone.pcui.PcLogOverlay.Tab.POKEMON, com.ironmonone.pcui.PcLogOverlay.Tab.TRAINERS, com.ironmonone.pcui.PcLogOverlay.Tab.ROUTES)
    }

    /** A left click at panel pixel ([x], [y]); the caller redraws. */
    fun click(x: Int, y: Int) = orClose(x.toFloat(), y.toFloat()) { runCatching { r().click(x, y) }.onFailure { say("panel click: $it") } }

    /** Whether the mouse at ([x], [y]), in card pixels, is over something clickable on the card. */
    fun hoverAt(x: Float, y: Float): Boolean = runCatching { r().hoverAt(x, y) }.getOrDefault(false)

    /** A left click from the tracker window, in card pixels; the caller redraws. */
    fun clickAt(x: Float, y: Float) = orClose(x, y) { runCatching { r().clickAt(x, y) }.onFailure { say("panel click: $it") } }

    /**
     * Click a move, read its card, click again anywhere and you are back on the Pokemon's card (Blake, 2026-10-10: "click
     * the move, see the info card, click again and im back"). With an info card or the resistance chart open, a click that
     * changes nothing (it hit nothing clickable: no page arrow, tab, link or note) closes them all. The settings, the keyboard and the log
     * viewer keep their own ways out: a stray click there must not throw away what is being done.
     */
    private fun orClose(x: Float, y: Float, click: () -> Unit) {
        val info = (screens.top != null || chart.types != null) && !typing.open && !settings.open
        if (!info) { click(); return }
        // A click on something clickable (the tab already chosen, HP +/- at its end) changes nothing and must not close it all.
        if (hoverAt(x, y)) { click(); return }
        fun state() = listOf(screens.stack, screens.page, screens.tab, screens.hpAdjust, chart.types, inspect.open, typing.open, settings.open)
        val before = state()
        click()
        if (state() == before) {
            screens.close()
            chart.close()
        }
    }

    /** Closes the card on top of the tracker (keyboard, resistances, a description, settings). Returns whether one was open. */
    fun back(): Boolean = when {
        typing.open -> { typing.close(); true }
        chart.types != null -> { chart.close(); true }
        screens.top != null -> { screens.back(); true }
        inspect.open -> { inspect.close(); true }
        settings.open -> { settings.open = false; true }
        else -> false
    }

    /** No game to track: the panel goes away (the script removes the padding when panel.txt is gone). */
    /** A new game: every card opened from the last one is closed (a note half typed is dropped, not saved to the new run). */
    fun newGame() {
        screens.close(); chart.close(); inspect.close(); typing.close(); settings.open = false
        last = null
    }

    fun clear() {
        statusWritten = null
        last = null
        pending = null
        shownSpecies = 0
        shownName = ""
        File(dir, "panel.txt").delete()
        window?.showWaiting()
    }

    fun close() { runCatching { renderer?.close() }; renderer = null }

    private fun writeAtomic(f: File, bytes: ByteArray) {
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    companion object {
        /** The least time between two cards the game's changes draw in the tracker window (a card is 25 to 80 ms at 3x). */
        private const val GAP = 120_000_000L
        /** Card pixels composed per card pixel. The window may be larger; it enlarges this (TrackerWindow.showCard). */
        const val COMPOSE_MAX = 3f

        /** A note on one line of panel.txt: backslash, newline and return written as \\, \n and \r. */
        fun escape(s: String): String = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")
        fun unescape(s: String): String {
            val out = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) { 'n' -> out.append('\n'); 'r' -> out.append('\r'); else -> out.append(s[i + 1]) }
                    i += 2
                } else { out.append(c); i++ }
            }
            return out.toString()
        }
    }
}
