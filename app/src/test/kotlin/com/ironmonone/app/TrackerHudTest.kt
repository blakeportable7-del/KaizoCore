package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Tracker HUD (Blake, 2026-10-04). Its placement runs for real on every phone shape, system and pad layout here,
 * a moved button included; where it draws from and what Play does with it are held to the source.
 */
class TrackerHudTest {
    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun code(name: String) = File(src, name).readText().replace("\r\n", "\n")
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    private data class Shape(val name: String, val w: Float, val h: Float)
    private val shapes = listOf(
        Shape("16:9", 640f, 360f), Shape("19.5:9", 851f, 393f), Shape("20:9", 915f, 412f),
        Shape("21:9", 960f, 411f), Shape("tablet", 1280f, 800f),
    )
    private enum class Sys(val aspect: Float, val gb: Boolean, val nds: Boolean) { GB(10f / 9f, true, false), GBC(10f / 9f, true, false), GBA(1.5f, false, false), DS(2f, false, true) }

    /** The game as the pad layouts leave it: the full height less a SELECT/START row on a phone, centred. */
    private fun game(s: Shape, sys: Sys): HudLayout.R {
        val h = if (sys == Sys.DS) s.h else s.h * (if (s.w / s.h > 1.7f) 0.65f else 0.8f)
        val w = minOf(s.w, h * sys.aspect)
        val l = (s.w - w) / 2
        return HudLayout.R(l, 0f, l + w, h)
    }

    private fun pads(sys: Sys): List<Pair<String, PadLayout>> {
        val d = PadLayout.default(true, nds = sys.nds, gb = sys.gb)
        return listOf(
            "default" to d,
            // A player's own layout: R moved to the top middle, over the game, and A grown and moved up the right side.
            "custom" to d.with(PadLayout.Element.R, PadLayout.Place(0.5f, 0.08f)).with(PadLayout.Element.A, PadLayout.Place(0.9f, 0.3f, 1.4f)),
        )
    }

    @Test
    fun `no panel meets a control, leaves the screen or meets the other panel, on every shape, system and layout`() {
        var placed = 0
        for (s in shapes) for (sys in Sys.entries) for ((padName, pad) in pads(sys)) for (skin in PadSkin.entries) {
            val controls = PadGeometry.rects(pad, s.w, s.h, landscape = true, skin = skin).values.flatten().map { HudLayout.R(it.l, it.t, it.r, it.b) }
            val g = game(s, sys)
            val plan = HudLayout.plan(s.w, s.h, g, controls, ds = sys == Sys.DS)
            val where = "${s.name} $sys $padName $skin"
            val rects = listOfNotNull(plan.mine, plan.rest)
            for (r in rects) {
                placed++
                assertTrue(r.l >= 0f && r.t >= 0f && r.r <= s.w && r.b <= s.h, "$where: on screen, $r")
                assertTrue(r.w >= HudLayout.MIN_W - 0.01f && r.h >= HudLayout.MIN_H - 0.01f, "$where: readable, $r")
                for (c in controls) assertFalse(r.meets(c.grown(HudLayout.MARGIN)), "$where: $r meets the control at $c")
                if (sys == Sys.DS) assertFalse(r.meets(HudLayout.R(g.l + g.w * 2f / 3f, g.t, g.r, g.b)), "$where: over the DS touch screen")
            }
            if (plan.mine != null && plan.rest != null) assertFalse(plan.mine!!.meets(plan.rest!!), "$where: the two panels meet")
            // The way out of the HUD is the FILE bar, every view's; every phone but the narrowest DS gets a panel.
            if (s.name != "16:9" || sys != Sys.DS) assertNotNull(plan.rest, "$where: no panel at all")
            // Under the open bar a panel keeps its place and loses only its top, or waits for the bar to close.
            for (r in rects) {
                val under = r.underBar(FileBar.UNDER_DP.toFloat())
                if (under != null) {
                    assertTrue(under.l == r.l && under.r == r.r && under.b == r.b && under.t >= FileBar.UNDER_DP.toFloat(), "$where: trimmed in place")
                    assertTrue(under.h >= 72f, "$where: never too short to read")
                } else assertTrue(r.b - FileBar.UNDER_DP < 72f, "$where: hidden only when too short")
            }
        }
        assertTrue(placed > 100, "the sweep placed panels ($placed)")
    }

    @Test
    fun `wide bars take the panels beside the game, and a moved button is stepped round`() {
        val s = Shape("20:9", 915f, 412f)
        val g = game(s, Sys.GB)
        val controls = listOf(HudLayout.R(20f, 300f, 200f, 400f), HudLayout.R(800f, 300f, 900f, 400f))
        val plan = HudLayout.plan(s.w, s.h, g, controls, ds = false)
        assertTrue(plan.mine!!.r <= g.l && plan.rest!!.l >= g.r, "Game Boy on 20:9: both panels in the bars, none over the game")
        assertTrue(plan.mine!!.b <= 300f - HudLayout.MARGIN, "and above the controls in them")
        // A button moved to the top of the left bar: the panel goes under it instead.
        val moved = controls + HudLayout.R(40f, 10f, 120f, 60f)
        val p2 = HudLayout.plan(s.w, s.h, g, moved, ds = false)
        assertTrue(p2.mine!!.t >= 60f + HudLayout.MARGIN && p2.mine!!.b <= 300f - HudLayout.MARGIN, "between the moved button and the pad: ${p2.mine}")
    }

    @Test
    fun `the HUD never moves or resizes the game`() {
        // It rides on the floating window's path: Play reads nothing of the HUD's but hands it the game's frame.
        val play = code("PlayScreen.kt")
        assertFalse("trackerHud" in play, "Play never lays the game out differently for the HUD")
        assertEquals(1, Regex("TrackerHud\\.").findAll(play).count(), "one line in Play: the game's frame handed over")
        assertTrue(".onGloballyPositioned { gameFrame = it.boundsInWindow(); TrackerHud.game = gameFrame }" in play)
        // The HUD only reads the frame.
        val hud = code("TrackerHud.kt")
        assertFalse(Regex("TrackerHud\\.game\\s*=").containsMatchIn(hud), "the HUD never sets the game's frame")
        assertTrue("if (TrackerHud.ENABLED && TrackerOptions.trackerHud)" in code("FloatingTracker.kt"))
    }

    @Test
    fun `every section of the tracker is in the HUD, drawn by the panels' own code`() {
        // The whole panel is composed in the HUD; only your card moves, whole, to its own panel.
        val hud = code("TrackerHud.kt")
        assertTrue("FitWidth { content() }" in hud, "the tracker's whole content")
        assertTrue("portal.left?.invoke()" in hud, "your card, as the panel built it")
        assertFalse("TrackerOptions.show" in hud || "TrackerOptions.display" in hud, "no switch of the tracker's is read again here")
        val wide = code("TrackerWideView.kt")
        assertTrue("SideEffect { portal.left = left }\n        right()\n        return" in wide, "the rest is drawn in place, your card handed over")
        // Section by section, the docked panel's parts and where the HUD has them.
        val mapping = mapOf(
            "PcBattleBanner(" to "rest", "PartyCard(" to "mine", "EnemyCard(" to "rest", "PcCarousel(" to "rest",
        )
        val panel = code("TrackerPanel.kt")
        val split = panel.indexOf("}, right = {")
        for ((call, slot) in mapping) {
            val at = panel.indexOf(call, panel.indexOf("val viewingOwn = !state.inBattle"))
            assertTrue(at > 0, call)
            assertEquals(slot, if (at < split && at > panel.indexOf("WideCards(left = {")) "mine" else "rest", call)
        }
        // The HUD's own switches.
        assertTrue(TrackerOptions.hudShowMine && TrackerOptions.hudShowRest && !TrackerOptions.trackerHud, "defaults")
    }
}
