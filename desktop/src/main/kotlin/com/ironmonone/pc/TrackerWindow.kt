package com.ironmonone.pc

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants
import kotlin.math.roundToInt

/**
 * The tracker card in a window of its own, beside BizHawk (Blake, 2026-10-09). OBS takes it with a Window Capture.
 *
 * BizHawk drew the card inside its own screen, on the emulator's thread, every frame: a picture small enough to keep
 * the game smooth was blocky when stretched, and one big enough to stay sharp stalled the game. Here the card is drawn
 * at the size picked in KaizoCore PC (2 to 5 screen pixels per card pixel, a whole number so the art stays sharp).
 * Windows' own scaling is not applied again: on a 200% screen that was doubling Medium from the streamer's 3x
 * panel (450 by 480) to 900 by 960. Painted only when the card changes. The favorites strip is the one thing that moves: a few small sheets
 * redrawn in their own box.
 *
 * Clicking never takes the keyboard from BizHawk, so the game's keys keep working after a click on a move or a
 * star. The window takes the keyboard only while a note, a favorite or a log search is being typed.
 *
 * Made and used on Swing's thread only. [showCard], [showWaiting], [setTyping] and [setSize] may be called from any
 * thread.
 */
class TrackerWindow(private val home: File) {
    /** What the window hears, handed to the session. Called on Swing's thread. */
    interface Input {
        /** A click at ([x], [y]) in card pixels (150 by 160). */
        fun click(x: Float, y: Float, right: Boolean)
        /** The wheel: positive is up, 100 a notch (as BizHawk's script sends it). */
        fun wheel(delta: Int)
        /** A typed character, or '\b' backspace, '\n' Enter, '\u001b' Escape. */
        fun key(c: Char)
        /** [renderScale] changed (the size picked, or the window moved to a screen with other scaling). */
        fun rescaled()
        /** The mouse at ([x], [y]) in card pixels, for the hand cursor and the strip's pause. */
        fun move(x: Float, y: Float) {}
        /** The mouse left the window. */
        fun left() {}
    }

    var input: Input? = null

    /** Where the favorites' sheets are (the session's folder, where the card writes them). */
    @Volatile var sheetDir: File? = null

    /** A battle's effects (GbaTracker.battleDetails' lines): your side's, the foe's, the field's. */
    data class Effects(val you: List<String>, val foe: List<String>, val field: List<String>)

    /** A line of the effects strip: the row's label on its first line, and the effects that fit. */
    data class EffectLine(val label: String?, val text: String)

    private val frame = JFrame(TITLE)
    private val view = View()
    @Volatile private var card: BufferedImage? = null
    /** The strip under the card; null out of battle. */
    private var effects: Effects? = null
    /** The strip's page shown, and how many the last paint had: two lines a page, a page every [PAGE_MS]. */
    private var effectsPage = 0
    private var effectsPages = 1
    /** The mouse is on the strip: the page shown stays. */
    private var overStrip = false
    private val pageTimer = Timer(PAGE_MS) {
        if (effectsPages > 1 && !overStrip) { effectsPage = (effectsPage + 1) % effectsPages; repaintStrip() }
    }
    private var bannerText: String? = null
    private var banner: Banner? = null
    private var shift = 0.0
    private var ticks = 0L
    private var typing = false
    private val sheets = HashMap<String, Sheet?>()
    internal val font: Font = cardFont()

    /** The card size picked: window pixels (before Windows' scaling) per card pixel. */
    var size: Int = 3
        private set
    /** Windows' display scaling on the window's screen (1.0 at 100%, 1.5 at 150%). */
    private var dpi = 1.0
    /** Screen pixels per card pixel the card is drawn at: the size picked, or the next whole size up from the window's. */
    @Volatile var renderScale: Int = 3
        private set
    /**
     * On top of every other window (Blake, 2026-10-10: "it hide behind windows and is very annoying"): on unless turned off
     * in KaizoCore PC's window, and kept in tracker window.txt.
     */
    var pinned: Boolean = true
        private set
    /** The window's own size (its inside, in screen pixels) when the player dragged it to one; null: the size picked. */
    private var dragged: Pair<Int, Int>? = null

    private val strip = Timer(16) { stepBanner() }

    /**
     * The battle and progress effects over the card (developer mode only: the session sets it when kaizocore-dev.txt
     * sits beside the script). Null draws the card as it always was.
     */
    private var fx: com.ironmonone.pc.fx.FxDriver? = null
    /** What a card was drawn from, for [fx]: the state, the side shown, the battle's lasting effects, and whether the card is uncovered. */
    data class FxIn(val state: com.ironmonone.tracker.TrackerState?, val showOwn: Boolean, val details: com.ironmonone.tracker.GbaTracker.BattleDetails?, val onCard: Boolean)
    /** 60 a second while an effect moves; stopped the moment nothing does, so a still card costs nothing. */
    private val fxTimer = Timer(16) { stepFx() }.also { pageTimer.start() }
    private val saveLater = Timer(700) { save() }.apply { isRepeats = false }

    init {
        read()
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(object : WindowAdapter() {
            // The card is the run's tracker: the X tucks it away, it does not end anything.
            override fun windowClosing(e: WindowEvent) { frame.extendedState = JFrame.ICONIFIED }
            override fun windowIconified(e: WindowEvent) { strip.stop(); fxTimer.stop() }
            override fun windowDeiconified(e: WindowEvent) { syncStrip(); syncFx() }
        })
        // Any size the player drags it to (the card keeps its shape and fills it), and on top unless unpinned.
        frame.isResizable = true
        frame.isAlwaysOnTop = pinned
        frame.focusableWindowState = false
        frame.background = Color.BLACK
        frame.contentPane.background = Color.BLACK
        frame.contentPane.add(view)
        view.isFocusable = true
        view.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val k = dpi / shownScale()
                // The card, and the battle effects strip under it (a click there opens Battle Details).
                if (e.y * k >= CARD_H + EFFECTS_H || e.x * k >= CARD_W) return
                input?.click((e.x * k).toFloat(), (e.y * k).toFloat(), SwingUtilities.isRightMouseButton(e))
            }
        })
        // One page a notch, at most one every 150 ms (WheelGate): a smooth wheel's many small events raced the pages.
        val wheelGate = WheelGate()
        view.addMouseWheelListener { e -> wheelGate.feed(e.preciseWheelRotation, System.nanoTime() / 1_000_000)?.let { input?.wheel(-it * 100) } }
        // The mouse over the card: a hand over anything clickable, and the bottom strip holds still under it (Blake,
        // 2026-10-11). Only a move to another card pixel is passed on.
        var lastPx = -1 to -1
        view.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val k = dpi / shownScale()
                overStrip = e.y * k >= CARD_H
                val px = (e.x * k).toInt() to (e.y * k).toInt()
                if (px == lastPx) return
                lastPx = px
                input?.move((e.x * k).toFloat(), (e.y * k).toFloat())
            }
        })
        view.addMouseListener(object : MouseAdapter() {
            override fun mouseExited(e: MouseEvent) { lastPx = -1 to -1; overStrip = false; input?.left() }
        })
        view.addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
                val c = e.keyChar
                val k = when {
                    c == '\u001b' -> '\u001b'
                    c == '\b' -> '\b'
                    c == '\n' || c == '\r' -> '\n'
                    c >= ' ' && c != '\u007f' && c != KeyEvent.CHAR_UNDEFINED -> c
                    else -> return
                }
                input?.key(k)
            }
        })
        frame.addComponentListener(object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) {
                saveLater.restart()
                if (measure()) input?.rescaled()
            }
            override fun componentResized(e: ComponentEvent) {
                if (fitting) return
                dragged = view.width to view.height
                saveLater.restart()
                if (measure()) input?.rescaled()
                view.repaint()
            }
        })
        strip.isRepeats = true
        measure()
        fit()
        place()
        frame.isVisible = true
        if (measure()) { fit(); input?.rescaled() }
    }

    /** The effects' driver (null: off). Any thread. */
    fun setFx(driver: com.ironmonone.pc.fx.FxDriver?) {
        SwingUtilities.invokeLater { fx = driver; syncFx(); view.repaint() }
    }

    /** The card drawn for the session's state, and the favorites strip on it (null: none); [from] is what [fx] plays. Any thread. */
    fun showCard(img: BufferedImage, bannerText: String?, from: FxIn? = null) {
        SwingUtilities.invokeLater {
            val wantW = CARD_W * renderScale
            val wantH = CARD_H * renderScale
            card = if (img.width == wantW && img.height == wantH) img else enlarge(img, wantW, wantH)
            if (from != null) fx?.let { d -> runCatching { d.update(from.state, from.showOwn, from.details, from.onCard) } }
            syncFx()
            if (bannerText != this.bannerText) {
                this.bannerText = bannerText
                banner = bannerText?.let(::parseBanner)
                shift = 0.0
            }
            syncStrip()
            view.repaint()
        }
    }

    /** [img] grown to [w] by [h], each source pixel a block. A 3x card filling a 6x window stays sharp and costs the 3x draw. */
    private fun enlarge(img: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(img, 0, 0, w, h, null)
        g.dispose()
        return out
    }

    /** The battle's effects under the card, or null out of battle. Any thread. */
    fun showEffects(e: Effects?) {
        SwingUtilities.invokeLater {
            if (e == effects) return@invokeLater
            // A battle starting or ending starts at the first page; a turn counter ticking down keeps the page shown, or
            // the pages after the first would never come round.
            if ((e == null) != (effects == null)) effectsPage = 0
            effects = e
            repaintStrip()
        }
    }

    private fun repaintStrip() {
        val k = shownScale() / dpi
        view.repaint(0, (CARD_H * k).toInt() - 1, view.width, (EFFECTS_H * k).toInt() + 2)
    }

    /** No game: the waiting card. Any thread. */
    fun showWaiting() {
        SwingUtilities.invokeLater {
            card = null
            bannerText = null
            banner = null
            fx?.update(null, true, null, false)
            syncFx()
            syncStrip()
            view.repaint()
        }
    }

    /**
     * The keyboard: taken while something is being typed on the card, given back after, so a click on the card never
     * leaves the game's keys going nowhere. Any thread.
     */
    fun setTyping(on: Boolean) {
        SwingUtilities.invokeLater {
            if (on == typing) return@invokeLater
            typing = on
            frame.focusableWindowState = on
            if (on) {
                frame.toFront()
                view.requestFocusInWindow()
            }
        }
    }

    /** The size picked in KaizoCore PC's window, 2 to 5. Any thread. */
    fun setSize(s: Int) {
        SwingUtilities.invokeLater {
            val n = s.coerceIn(MIN_SIZE, MAX_SIZE)
            // The size already picked still snaps a dragged (blurred) window back to a sharp whole size.
            if (n == size && dragged == null) return@invokeLater
            size = n
            dragged = null
            measure(force = true)
            fit()
            save()
            input?.rescaled()
        }
    }

    /** The hand cursor over something clickable, the arrow elsewhere. Any thread. */
    fun setHand(on: Boolean) {
        SwingUtilities.invokeLater {
            val c = if (on) java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR) else java.awt.Cursor.getDefaultCursor()
            if (view.cursor != c) view.cursor = c
        }
    }

    /** On top of every other window, or not. Any thread. */
    fun setPinned(on: Boolean) {
        SwingUtilities.invokeLater {
            pinned = on
            frame.isAlwaysOnTop = on
            if (on) frame.toFront()
            save()
        }
    }

    /** Brings the window back if it was tucked away or behind something. Any thread. */
    fun bringBack() {
        SwingUtilities.invokeLater {
            frame.extendedState = JFrame.NORMAL
            frame.isVisible = true
            frame.toFront()
        }
    }

    fun close() {
        SwingUtilities.invokeLater {
            strip.stop()
            fxTimer.stop()
            save()
            frame.dispose()
        }
    }

    // ---- size and place ------------------------------------------------------------------------------------------

    /**
     * Reads Windows' scaling where the window is now, and sets [renderScale] to the size picked.
     * The picked size is already screen pixels per card pixel (the streamer's panel is 3: 450 by 480).
     * Multiplying by the display scale made Medium 6x on a 200% screen. True when [renderScale] changed.
     */
    private fun measure(force: Boolean = false): Boolean {
        val gc = frame.graphicsConfiguration ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val d = gc.defaultTransform.scaleX.takeIf { it > 0 } ?: 1.0
        // A dragged window draws the card at the next whole size up from what fits, and shows it scaled to fit.
        val rs = dragged?.let { (w, h) ->
            kotlin.math.ceil(minOf(w * d / CARD_W, h * d / (CARD_H + EFFECTS_H)) - 0.01).toInt().coerceIn(MIN_SIZE, MAX_DRAW)
        } ?: size.coerceAtLeast(1)
        if (!force && d == dpi && rs == renderScale) return false
        dpi = d
        if (rs == renderScale) return false
        renderScale = rs
        return true
    }

    /** The window's own resize events from [fit] are not the player's drag. */
    private var fitting = false

    private fun fit() {
        val (w, h) = dragged ?: (kotlin.math.ceil(CARD_W * renderScale / dpi).toInt() to kotlin.math.ceil((CARD_H + EFFECTS_H) * renderScale / dpi).toInt())
        fitting = true
        try {
            view.preferredSize = Dimension(w, h)
            frame.pack()
        } finally {
            SwingUtilities.invokeLater { fitting = false }
        }
    }

    /** Screen pixels per card pixel the card is shown at: the window's size, keeping the card's shape. */
    private fun shownScale(): Double {
        val pw = view.width * dpi; val ph = view.height * dpi
        if (pw <= 0 || ph <= 0) return renderScale.toDouble()
        return minOf(pw / CARD_W, ph / (CARD_H + EFFECTS_H))
    }

    private fun place() {
        val saved = savedAt
        val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
        if (saved != null && screens.any { it.intersects(Rectangle(saved.first, saved.second, 80, 80)) }) {
            frame.setLocation(saved.first, saved.second)
            return
        }
        val b = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        frame.setLocation(b.x + b.width - frame.width - 24, b.y + 24)
    }

    private var savedAt: Pair<Int, Int>? = null

    private fun read() {
        val f = File(home, FILE)
        if (!f.isFile) return
        val m = runCatching { f.readLines() }.getOrDefault(emptyList()).mapNotNull { l ->
            l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it).trim() to l.substring(it + 1).trim() }
        }.toMap()
        m["size"]?.toIntOrNull()?.let { size = it.coerceIn(MIN_SIZE, MAX_SIZE) }
        m["pin"]?.let { pinned = it != "0" }
        val dw = m["w"]?.toIntOrNull(); val dh = m["h"]?.toIntOrNull()
        if (dw != null && dh != null && dw > 50 && dh > 50) dragged = dw to dh
        val x = m["x"]?.toIntOrNull(); val y = m["y"]?.toIntOrNull()
        if (x != null && y != null) savedAt = x to y
    }

    private fun save() {
        if (!frame.isShowing || frame.extendedState == JFrame.ICONIFIED) return
        val at = frame.location
        val d = dragged
        runCatching { File(home, FILE).writeText("size=$size\nx=${at.x}\ny=${at.y}\npin=${if (pinned) 1 else 0}\n" + (d?.let { "w=${it.first}\nh=${it.second}\n" } ?: "")) }
    }

    // ---- the favorites strip -------------------------------------------------------------------------------------

    private class Sheet(val img: BufferedImage, val k: Double, val footX: Double, val footY: Double)
    private class Mon(val sheet: Sheet?, val name: String, val w: Int, val h: Int, val x: Int, val y: Int, val d: IntArray, val total: Int) {
        fun frame(steps: Long): Int {
            if (total <= 0 || d.isEmpty()) return 0
            var f = (steps % total).toInt()
            for (i in d.indices) { if (f < d[i]) return i; f -= d[i] }
            return 0
        }
    }
    private class Banner(val x: Int, val y: Int, val w: Int, val h: Int, val slot: Int, val mons: List<Mon>)

    private fun parseBanner(text: String): Banner? {
        val m = HashMap<String, String>()
        val mons = ArrayList<Mon>()
        for (line in text.lines()) {
            val i = line.indexOf('=')
            if (i <= 0) continue
            val key = line.substring(0, i); val value = line.substring(i + 1)
            if (key != "mon") { m[key] = value; continue }
            val p = value.split('|')
            if (p.size < 7) continue
            val w = p[2].toIntOrNull() ?: continue
            val h = p[3].toIntOrNull() ?: continue
            val d = p[6].split(',').mapNotNull { it.toIntOrNull() }.toIntArray()
            mons += Mon(sheet(p[0], w, h), p[1], w, h, p[4].toIntOrNull() ?: 0, p[5].toIntOrNull() ?: 0, d, d.sum())
        }
        if (m["on"] != "1" || mons.isEmpty()) return null
        return Banner(m["x"]?.toIntOrNull() ?: 2, m["y"]?.toIntOrNull() ?: 69, m["w"]?.toIntOrNull() ?: 136,
            m["h"]?.toIntOrNull() ?: 70, m["slot"]?.toIntOrNull() ?: 64, mons)
    }

    /** A favorite's sheet, read once, with how much it is shrunk so it is never bigger than the card's sprites. */
    private fun sheet(name: String, w: Int, h: Int): Sheet? = sheets.getOrPut(name) {
        val dir = sheetDir ?: return null
        val img = runCatching { ImageIO.read(File(dir, name)) }.getOrNull() ?: return null
        val rows = minOf(h, img.height)
        val frames = (img.width / w).coerceAtLeast(1)
        var l = w; var t = rows; var r = 0; var b = 0
        for (f in 0 until frames) for (y in 0 until rows) for (x in 0 until w) {
            if (f * w + x >= img.width) continue
            if ((img.getRGB(f * w + x, y) ushr 24) < 128) continue
            if (x < l) l = x
            if (y < t) t = y
            if (x + 1 > r) r = x + 1
            if (y + 1 > b) b = y + 1
        }
        val span = if (r > l && b > t) maxOf(r - l, b - t) else 0
        val k = if (span > com.ironmonone.pcui.PcPictures.SPRITE_MAX) com.ironmonone.pcui.PcPictures.SPRITE_MAX.toDouble() / span else 1.0
        Sheet(img, k, (l + r) / 2.0, b.toDouble())
    }

    private fun syncFx() {
        val run = fx?.active == true && card != null && frame.isShowing && frame.extendedState != JFrame.ICONIFIED
        if (run && !fxTimer.isRunning) fxTimer.start()
        if (!run && fxTimer.isRunning) fxTimer.stop()
    }

    private fun stepFx() {
        if (fx?.active != true || card == null) { fxTimer.stop(); view.repaint(); return }
        val k = shownScale() / dpi
        view.repaint(0, 0, (CARD_W * k).toInt() + 1, (CARD_H * k).toInt() + 1)
    }

    private fun syncStrip() {
        val run = banner != null && frame.isShowing && frame.extendedState != JFrame.ICONIFIED
        if (run && !strip.isRunning) strip.start()
        if (!run && strip.isRunning) strip.stop()
    }

    private fun stepBanner() {
        val b = banner ?: return
        // 1.5 card pixels a frame at 60 a second, the speed Blake picked (2026-10-09); a walk step every second frame.
        shift = (shift + 1.5) % (b.mons.size * b.slot)
        ticks++
        val k = shownScale() / dpi
        view.repaint((b.x * k).toInt() - 1, (b.y * k).toInt() - 1, (b.w * k).toInt() + 2, (b.h * k).toInt() + 2)
    }

    private fun drawBanner(g: Graphics2D, s: Double) {
        val b = banner ?: return
        val clip = g.clip
        g.clipRect((b.x * s).toInt(), (b.y * s).toInt(), (b.w * s).toInt(), (b.h * s).toInt())
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.font = font.deriveFont((7.0 * s).toFloat())
        // The names sit on the favorites box (PcPanel.Favorites: the lower box's fill). White on a light theme's box was
        // nearly gone, so the ink is whichever of near-black and white reads more on that fill, its shadow the other.
        val ground = (com.ironmonone.app.Pc.LowerGroundX ?: com.ironmonone.app.Pc.Ground).let { Color(it.red, it.green, it.blue) }
        val white = Color.WHITE; val black = Color(0x15, 0x16, 0x1A)
        val nameInk = if (ratio(white, ground) >= ratio(black, ground)) white else black
        val nameShadow = if (nameInk == white) Color(0, 0, 0, 160) else Color(255, 255, 255, 140)
        val span = b.mons.size * b.slot
        for ((i, mon) in b.mons.withIndex()) {
            val gx = i * b.slot - shift
            for (at in doubleArrayOf(gx, gx + span)) {
                if (at + mon.w + 12 < b.x || at > b.x + b.w) continue
                val sh = mon.sheet ?: continue
                val f = mon.frame(ticks / 2)
                val k = sh.k
                val dx = at + 6 + mon.x + sh.footX * (1 - k)
                val dy = b.y + mon.y + sh.footY * (1 - k)
                val x0 = (dx * s).roundToInt(); val y0 = (dy * s).roundToInt()
                val x1 = ((dx + mon.w * k) * s).roundToInt(); val y1 = ((dy + mon.h * k) * s).roundToInt()
                g.drawImage(sh.img, x0, y0, x1, y1, f * mon.w, 0, f * mon.w + mon.w, minOf(mon.h, sh.img.height), null)
                if (at > b.x && at < b.x + b.w - 24 && mon.name.isNotEmpty()) {
                    val tx = (at * s).toFloat(); val ty = ((b.y + 48) * s).toFloat()
                    g.color = nameShadow
                    g.drawString(mon.name, tx + s.toFloat() * 0.5f, ty + s.toFloat() * 0.5f)
                    g.color = nameInk
                    g.drawString(mon.name, tx, ty)
                }
            }
        }
        g.clip = clip
    }

    // ---- painting -------------------------------------------------------------------------------------------------

    private inner class View : JPanel() {
        init { isOpaque = true; background = Color.BLACK; isDoubleBuffered = true }

        override fun paintComponent(g0: Graphics) {
            val g = g0.create() as Graphics2D
            try {
                // Windows' scaling is undone here, so one picture pixel is one screen pixel.
                val t = g.transform
                g.transform = AffineTransform(1.0, 0.0, 0.0, 1.0, t.translateX, t.translateY)
                val pw = (width * t.scaleX).roundToInt(); val ph = (height * t.scaleY).roundToInt()
                val img = card
                if (img == null) {
                    waiting(g, pw, ph)
                    return
                }
                g.color = Color.BLACK
                g.fillRect(0, 0, pw, ph)
                // The card drawn at renderScale, shown at the window's scale (smooth when the two differ, sharp when equal).
                val k = shownScale() / (img.width / CARD_W.toDouble())
                // Within 1% is the window's rounding (175% display scaling makes 3.004 for 3): drawn 1:1, sharp.
                if (kotlin.math.abs(k - 1.0) > 1e-2) {
                    g.scale(k, k)
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, if (k >= 1.0 && kotlin.math.abs(k - Math.round(k)) < 1e-3)
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR else RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                }
                // With the effects on: the card at its shake on black, the effects over it. A failure draws the card as it was.
                val d = fx
                if (d == null) g.drawImage(img, 0, 0, null)
                else {
                    g.fillRect(0, 0, img.width, img.height)
                    if (runCatching { d.paint(g, img, img.width / CARD_W.toDouble()) }.isFailure) g.drawImage(img, 0, 0, null)
                }
                drawBanner(g, img.width / CARD_W.toDouble())
                effectsPages = paintEffects(g, img, img.width, effects, font, effectsPage)
                if (effectsPage >= effectsPages) { effectsPage = 0; repaint() }
            } finally {
                g.dispose()
            }
        }

        private fun waiting(g: Graphics2D, w: Int, h: Int) {
            g.color = Color(0x16, 0x18, 0x1D)
            g.fillRect(0, 0, w, h)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            val s = renderScale.toFloat()
            val top = (h * 0.40).toInt()
            center(g, "KaizoCore Tracker", font.deriveFont(Font.BOLD, 13f * s), Color(0xFF, 0xD2, 0x4A), w, top)
            center(g, "Waiting for BizHawk", font.deriveFont(9f * s), Color(0xD8, 0xDC, 0xE3), w, top + (16 * s).toInt())
            val hint = font.deriveFont(7.5f * s)
            center(g, "In BizHawk: Tools, Lua Console,", hint, Color(0x9A, 0xA0, 0xAB), w, top + (30 * s).toInt())
            center(g, "then open kaizocore-hns.lua", hint, Color(0x9A, 0xA0, 0xAB), w, top + (40 * s).toInt())
        }

        /** [text] centred at baseline [y], made smaller if it would not fit the width. */
        private fun center(g: Graphics2D, text: String, f: Font, color: Color, w: Int, y: Int) {
            var font = f
            while (g.getFontMetrics(font).stringWidth(text) > w * 0.9 && font.size2D > 6f) font = font.deriveFont(font.size2D * 0.92f)
            g.font = font
            g.color = color
            g.drawString(text, (w - g.fontMetrics.stringWidth(text)) / 2, y)
        }
    }

    companion object {
        /** The window's title, which OBS lists for a Window Capture. */
        const val TITLE = "KaizoCore Tracker"
        const val FILE = "tracker window.txt"
        val CARD_W = com.ironmonone.pcui.PcPanel.WIDTH
        val CARD_H = com.ironmonone.pcui.PcPanel.HEIGHT
        /**
         * The battle effects strip under the card, in card pixels: [EFFECT_LINES] lines of 8. Always there, so the window
         * keeps one size for OBS's Window Capture whether or not a battle is on. Two lines since 2026-10-11 (Blake: "shrink
         * it, and just have the battle info scroll"; it was five, mostly empty): more turn as pages every [PAGE_MS].
         */
        const val EFFECTS_H = 20
        const val EFFECT_LINES = 2
        /** A page of the strip stays this long: two short lines read, and a crowded battle's six lines go round in 7.5 s. */
        const val PAGE_MS = 2500
        const val MIN_SIZE = 2
        /** The largest whole size a dragged window draws the card at. */
        const val MAX_DRAW = 8

        /**
         * The strip under the card [img] (drawn at [img]'s scale, [w] pixels wide): "You", "Foe" and "Field" with their
         * effects, wrapped, [EFFECT_LINES] lines a page, page [page] of them shown with "1/3" at its right; a hint out of
         * battle ([e] null). Its ground is the card's own bottom row, so it follows the theme. Returns the page count.
         */
        fun paintEffects(g: Graphics2D, img: BufferedImage, w: Int, e: Effects?, font: Font, page: Int = 0): Int {
            val s = img.width / CARD_W.toDouble()
            val top = img.height
            val bg = Color(img.getRGB(0, img.height - 1))
            val light = (bg.red * 299 + bg.green * 587 + bg.blue * 114) / 1000 > 140
            // The card's theme (the settings' colours, or the lead's): its text, its gold, its quiet lines, each kept
            // only where it reads on the strip's ground.
            val text = themed(com.ironmonone.app.Pc.Text, bg, if (light) Color(0x20, 0x22, 0x28) else Color(0xE8, 0xEA, 0xEE))
            val dim = themed(com.ironmonone.app.Pc.Dim, bg, if (light) Color(0x60, 0x64, 0x6C) else Color(0x9A, 0xA0, 0xAB))
            val head = themed(com.ironmonone.app.Pc.Gold, bg, if (light) Color(0x8A, 0x5C, 0x00) else Color(0xFF, 0xD2, 0x4A))
            g.color = bg
            g.fillRect(0, top, w, (EFFECTS_H * s).roundToInt() + 1)
            g.color = dim
            g.fillRect((2 * s).roundToInt(), top, w - (4 * s).roundToInt(), maxOf(1, (s / 2).roundToInt()))
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g.font = font.deriveFont((7.0 * s).toFloat())
            val fm = g.fontMetrics
            val left = (3 * s).toFloat()
            val step = (8.0 * s).toFloat()
            var y = (top + 8.5 * s).toFloat()
            if (e == null || (e.you + e.foe + e.field).isEmpty()) {
                g.color = dim
                g.drawString(if (e == null) "Battle effects show here in a battle" else "No battle effects", left, y)
                return 1
            }
            val tagW = listOf("You: ", "Foe: ", "Field: ").maxOf { fm.stringWidth(it) }
            val countW = fm.stringWidth(" 9/9")
            val all = effectLines(e, (w - 6 * s - tagW - countW).toFloat()) { fm.stringWidth(it).toFloat() }
            // Each line's row, so a page that starts inside a row still names it.
            var owner = ""
            val owners = all.map { l -> l.label?.also { owner = it } ?: owner }
            val pages = all.indices.chunked(EFFECT_LINES)
            val shown = pages[page.coerceIn(0, pages.size - 1)]
            for ((n, i) in shown.withIndex()) {
                val line = all[i]
                if (line.label != null || n == 0) {
                    g.color = if (line.label != null) head else dim
                    g.drawString("${owners[i]}:", left, y)
                }
                g.color = text
                g.drawString(line.text, left + tagW, y)
                y += step
            }
            if (pages.size > 1) {
                val label = "${page.coerceIn(0, pages.size - 1) + 1}/${pages.size}"
                g.color = dim
                g.drawString(label, (w - 3 * s - fm.stringWidth(label)).toFloat(), (top + 8.5 * s).toFloat())
            }
            return pages.size
        }

        /** The WCAG contrast of two colours, 1 to 21. */
        internal fun ratio(x: Color, y: Color): Double {
            fun lum(c: Color): Double {
                fun ch(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
                return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
            }
            val (a, b) = lum(x) to lum(y)
            return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
        }

        /**
         * [c] as an AWT colour when it reads on [bg] (4.5 to 1, the card's own rule), else [fallback] when that does,
         * else near-black or white, whichever reads more: a mid-grey strip failed both of the first two.
         */
        internal fun themed(c: androidx.compose.ui.graphics.Color, bg: Color, fallback: Color): Color {
            val awt = Color(c.red, c.green, c.blue)
            if (ratio(awt, bg) >= 4.5) return awt
            if (ratio(fallback, bg) >= 4.5) return fallback
            val black = Color(0x15, 0x16, 0x1A)
            return if (ratio(Color.WHITE, bg) >= ratio(black, bg)) Color.WHITE else black
        }

        /**
         * "You", "Foe" and "Field" wrapped at their commas into lines [width] wide, every line kept: the strip shows them
         * [EFFECT_LINES] at a time and turns the pages.
         */
        fun effectLines(e: Effects, width: Float, measure: (String) -> Float): List<EffectLine> {
            fun wrap(items: List<String>): List<String> {
                val out = mutableListOf<String>()
                var cur = ""
                for ((i, item) in items.withIndex()) {
                    val piece = if (i < items.size - 1) "$item," else item
                    val next = if (cur.isEmpty()) piece else "$cur $piece"
                    if (cur.isNotEmpty() && measure(next) > width) { out += cur; cur = piece } else cur = next
                }
                if (cur.isNotEmpty()) out += cur
                return out
            }
            val rows = listOf("You" to e.you, "Foe" to e.foe, "Field" to e.field)
                .filter { it.second.isNotEmpty() }.map { it.first to wrap(it.second).toMutableList() }
            return rows.flatMap { (label, lines) -> lines.mapIndexed { i, t -> EffectLine(if (i == 0) label else null, t) } }
        }
        const val MAX_SIZE = 5

        /** The card's own face (Roboto Condensed, in the jar beside its licence), for the words the window draws itself. */
        internal fun cardFont(): Font {
            for (loader in listOfNotNull(TrackerWindow::class.java.classLoader, Thread.currentThread().contextClassLoader)) {
                val f = runCatching { loader.getResourceAsStream("fonts/RobotoCondensed-Regular.ttf")?.use { Font.createFont(Font.TRUETYPE_FONT, it) } }.getOrNull()
                if (f != null) return f
            }
            return Font(Font.SANS_SERIF, Font.PLAIN, 12)
        }
    }
}
