package com.ironmonone.pc

import com.ironmonone.pcui.PcLogData
import com.ironmonone.pcui.PcLogOverlay
import java.io.File

/**
 * The log viewer over the game area in BizHawk (PLAN row 7): drawn by pcui's PcLogOverlay while it is open, written
 * as a new PNG under a new name each time (as PanelOut does), then named in `overlay.txt` for kaizocore-hns.lua, which
 * draws it over the game's 240 by 160 every frame and sends what the mouse does there (oclick, wheel) and what is typed
 * in its search box (search). Closing deletes overlay.txt, and the script stops drawing. Session thread only.
 */
class OverlayOut(private val dir: File, private val say: (String) -> Unit) {
    private var renderer: PcLogOverlay.Renderer? = null
    private var seq = 0
    /** The window's scale, the panel's (PanelOut.scale). */
    var scale: Float = 1f
        private set
    /** The X clicked at the top level: the session closes the viewer (the card stays). */
    var onClose: () -> Unit = {}
    /** The view changed (a click here or on the panel's search screen): the session draws both pictures. */
    var onChanged: () -> Unit = {}

    /** The view the viewer and the panel's search screen and Pokemon card share while it is open. */
    val shared = PcLogOverlay.Shared().also { sh ->
        sh.onClose = { onClose() }
        sh.onChanged = { onChanged() }
    }

    val isOpen: Boolean get() = renderer != null

    /** The view as it stands, for the checks. */
    val view: PcLogOverlay.View? get() = renderer?.view

    private fun make(): PcLogOverlay.Renderer = PcLogOverlay.Renderer(scale / com.ironmonone.pcui.PcBmp.upscale(scale), shared, 1).also { r -> renderer = r }

    /** Opens on [data] (null: the log could not be read), on the lead's page when it has one. */
    fun open(data: PcLogData?, leadSpecies: Int?) {
        runCatching { renderer?.close() }
        renderer = null
        val t = System.nanoTime()
        make().open(data, leadSpecies)
        draw()
        say("log viewer open in %.0f ms".format((System.nanoTime() - t) / 1e6))
    }

    /** The prize card over the game area (GachaMonOverlay), the log open under it or not. */
    fun openPrize(g: com.ironmonone.pcui.PcGachaMon, sprite: androidx.compose.ui.graphics.ImageBitmap?) {
        shared.prizeSprite = sprite
        shared.prize = g
        if (renderer == null) make()
        draw()
    }

    val prizeOpen: Boolean get() = renderer != null && shared.prize != null

    /** The log viewer itself is open (not the GachaMon card alone): it takes the wheel. */
    val logOpen: Boolean get() = renderer != null && shared.data != null

    /** Takes the prize card away, as its own click does: the log under it stays open, or nothing is left. */
    fun closePrize() {
        if (!prizeOpen) return
        shared.prize = null
        if (shared.data == null) close() else draw()
    }

    /** The window changed size: the open viewer is drawn again at the new scale, as it stands. */
    fun setScale(s: Float) {
        if (s == scale) return
        scale = s
        val r = renderer ?: return
        runCatching { r.close() }
        renderer = null
        make()
        draw()
    }

    fun draw() {
        val r = renderer ?: return
        val png = try { r.render() } catch (e: Exception) { say("log viewer: $e"); return }
        seq++
        val name = "overlay-$seq.bmp"
        writeAtomic(File(dir, name), png)
        writeAtomic(File(dir, "overlay.txt"), "seq=$seq\nfile=$name\nscale=%.2f\n".format(scale).toByteArray(Charsets.UTF_8))
        File(dir, "overlay-${seq - 3}.bmp").delete()
    }

    fun click(x: Int, y: Int) { val r = renderer ?: return; runCatching { r.click(x, y) }.onFailure { say("log viewer click: $it") }; draw() }

    fun wheel(delta: Int) { val r = renderer ?: return; r.wheel(delta); draw() }

    fun search(text: String) { val r = renderer ?: return; r.search(text); draw() }

    fun close() {
        runCatching { renderer?.close() }
        renderer = null
        shared.data = null
        shared.prize = null
        shared.side = null
        File(dir, "overlay.txt").delete()
    }

    private fun writeAtomic(f: File, bytes: ByteArray) {
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
