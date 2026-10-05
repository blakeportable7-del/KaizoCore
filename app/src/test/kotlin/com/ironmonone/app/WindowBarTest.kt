package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The floating window's one-row top (Blake, 2026-10-04: "less bulky"). The text slot's words run for real; where the
 * row is drawn, and that nothing the two rows did is lost, is held to the source.
 */
class WindowBarTest {
    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun code(name: String) = File(src, name).readText().replace("\r\n", "\n")
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    @Test
    fun `the slot reads attempt, battle, side, weather in that order, and the area out of a battle`() {
        val battle = WindowBarText.withAttempt(791, WindowBarText.battle(isWild = false, side = "MINE ON THE LEFT", weather = "RAIN"))
        assertEquals("ATTEMPT 791 · TRAINER BATTLE · MINE ON THE LEFT · Rain", WindowBarText.plain(battle))
        assertEquals(Pc.Negative, battle[1].color, "a trainer battle in the theme's negative colour")
        assertEquals(Pc.Positive, WindowBarText.battle(isWild = true, side = null, weather = null)[0].color, "a wild one in its positive")
        assertEquals("WILD BATTLE", WindowBarText.plain(WindowBarText.withAttempt(null, WindowBarText.battle(true, null, "CLEAR"))), "no attempt, no side, clear skies")
        assertEquals("ATTEMPT 4 · Rustboro City", WindowBarText.plain(WindowBarText.withAttempt(4, WindowBarText.overworld("Rustboro City"))))
        assertEquals("Attempt 791, Trainer battle, Mine on the left, Rain", WindowBarText.spoken(battle), "a screen reader hears it all")
    }

    @Test
    fun `with no motion and no room, the weather goes first, then the side, then the attempt, and the title stays`() {
        val all = WindowBarText.withAttempt(791, WindowBarText.battle(false, "MINE ON THE LEFT", "SUN"))
        fun fitting(n: Int) = WindowBarText.keep(all) { it.size <= n }.map { it.text }
        assertEquals(listOf("ATTEMPT 791", "TRAINER BATTLE", "MINE ON THE LEFT", "Sunlight"), fitting(4))
        assertEquals(listOf("ATTEMPT 791", "TRAINER BATTLE", "MINE ON THE LEFT"), fitting(3))
        assertEquals(listOf("ATTEMPT 791", "TRAINER BATTLE"), fitting(2))
        assertEquals(listOf("TRAINER BATTLE"), fitting(1))
        assertEquals(listOf("TRAINER BATTLE"), fitting(0), "never less than the title")
        // The slot itself: an ellipsis when still, the quick marquee when moving.
        val slot = code("WindowBar.kt").substringAfter("fun WindowBarTextSlot(").substringBefore("\n}\n")
        assertTrue("if (still) WindowBarText.keep(segs)" in slot && "TextOverflow.Ellipsis" in slot)
        assertTrue("basicMarquee(iterations = Int.MAX_VALUE, velocity = WindowBarText.MARQUEE_DP_PER_S.dp)" in slot)
        assertTrue("TrackerMotion.animationsOn(context)" in slot, "the phone's own animation setting decides")
        assertTrue("contentDescription = WindowBarText.spoken(segs)" in slot, "read in full")
    }

    @Test
    fun `only the floating window merges the rows, and every row hands up all it had`() {
        val all = src.listFiles { f -> f.extension == "kt" }!!
        assertEquals(listOf("FloatingTracker.kt"), all.filter { "LocalWindowBar provides" in code(it.name) }.map { it.name }, "docked, portrait, second display and HUD keep their rows")
        // The battle banner: its words, the trainer tap, the swap with both its labels, the gear and the corner menu.
        val banner = code("PcTracker.kt").substringAfter("fun PcBattleBanner(").substringBefore("PcBannerBand(")
        for (part in listOf("WindowBarText.battle(isWild, side?.full, weather)", "onTextTap = if (isWild) null else onTrainerTap",
            "SwapAction(PcBannerCopy.see(viewingOwn), swapSpoken ?: PcBannerCopy.seeSpoken(viewingOwn), it)", "onGear = onGear", "extra = trailing", "))) return")) {
            assertTrue(part in banner, part)
        }
        // The area's row in both panels: the area, the gear and the repel bar.
        for (f in listOf("TrackerPanel.kt", "NdsTrackerPanel.kt")) {
            val row = code(f)
            assertTrue("WindowBarText.overworld(" in row && "onGear = g" in row && "PcRepelBar(" in row.substringAfter("publishToWindowBar(") && "))) return@let" in row, f)
        }
        // The bar: lock, grip, the drag and double tap while unlocked, the trainer tap, the swap, the gear, the menu.
        val ft = code("FloatingTracker.kt")
        for (part in listOf("LockButton(locked)", "if (top.grip) GripDots()", "detectDragGestures", "detectTapGestures(onDoubleTap",
            "WindowBarTextSlot(segs, parts?.onTextTap, parts?.tapLabel", "SwapIconButton(it)", "TrackerGearButton(onClick = it)", "menu(dock)", "parts?.extra?.invoke()")) {
            assertTrue(part in ft, part)
        }
    }

    @Test
    fun `the swap icon keeps its tap and its spoken words, and a long press shows its words`() {
        val icon = code("WindowBar.kt").substringAfter("fun SwapIconButton(").substringBefore("\n}\n")
        assertTrue("onClick = s.onClick" in icon, "the tap swaps, as the button did")
        assertTrue("contentDescription = s.spoken" in icon, "the sentence a screen reader heard")
        assertTrue("onLongClick = { showWords = true }" in icon && "Text(s.label" in icon && "onLongClickLabel = s.label" in icon)
        assertTrue("Modifier.size(PcMin.TOUCH_DP.dp)" in icon, "a full touch box round a slim icon")
        assertEquals("SEE FOE", PcBannerCopy.see(viewingOwn = true))
        assertEquals("SEE MINE", PcBannerCopy.see(viewingOwn = false))
    }

    @Test
    fun `a row that leaves hands the bar back`() {
        val slot = WindowBarSlot()
        val a = Any()
        val b = Any()
        slot.set(a, WindowBarParts(WindowBarText.overworld("Route 1"), null, null, null, null))
        slot.set(b, WindowBarParts(WindowBarText.battle(true, null, null), null, null, null, null))
        slot.clear(a)
        assertEquals("WILD BATTLE", WindowBarText.plain(slot.parts!!.segments), "the old row leaving does not clear the new one")
        slot.clear(b)
        assertFalse(slot.parts != null)
    }

    @Test
    fun `the top is one row at every width, and the gear, menu, lock, swap and text tap all stay reachable`() {
        // Blake, 2026-10-04: "The tracker still has too much space on top" (rc35.1's second row in a narrow window).
        var w = FloatFrame.MIN_W
        while (w <= 1200f) {
            for (swap in listOf(true, false)) for (gear in listOf(true, false)) for (locked in listOf(true, false)) {
                val t = WindowBarFit.top(w, swap = swap, gear = gear, locked = locked)
                val at = "w=$w swap=$swap gear=$gear locked=$locked: $t"
                // The lock, the menu, the swap in a battle and the gear while it is in the row: every button whole, in one row.
                val buttons = 2 + (if (swap) 1 else 0) + (if (t.gearInRow) 1 else 0)
                val used = PcMin.TOUCH_DP * buttons + (if (t.grip) WindowBarFit.GRIP else 0f) + WindowBarFit.PAD + t.text
                assertTrue(used <= w + 0.01f, "one row holds it all, $at")
                assertTrue(t.text >= 0f, at)
                // The gear is in the row or in the menu, never lost; the text's tap is a fair target or in the menu too.
                if (gear && !t.gearInRow) assertTrue(t.text < WindowBarFit.MIN_TEXT + PcMin.TOUCH_DP, "the gear leaves only when it must, $at")
                assertTrue(t.text >= WindowBarFit.MIN_TEXT || t.textTapInMenu, at)
                if (locked) assertFalse(t.grip, "no grip on a locked window, $at")
            }
            w += 1f
        }
        // The narrowest window in a battle: lock, swap and menu stay, the gear goes in the menu.
        val narrow = WindowBarFit.top(FloatFrame.MIN_W, swap = true, gear = true, locked = false)
        assertFalse(narrow.gearInRow); assertFalse(narrow.grip)
        // The usual window keeps them all in the row.
        val usual = WindowBarFit.top(360f, swap = true, gear = true, locked = false)
        assertTrue(usual.gearInRow && usual.grip && !usual.textTapInMenu)
        // And the window draws it so: one Row, the menu gets what the row has no room for.
        val ft = code("FloatingTracker.kt")
        assertFalse("twoRows" in ft || "2 * BAR_DP" in ft, "no second row")
        assertEquals(1, Regex("""\bRow\(""").findAll(ft.substringAfter("val barSlot").substringBefore("val room =")).count(), "one Row on top")
        assertTrue("val barDp = WindowBarFit.ROW_DP" in ft && WindowBarFit.ROW_DP < PcMin.TOUCH_DP, "the row is drawn slimmer than a touch box")
        assertTrue("if (top.gearInRow) parts?.onGear?.let" in ft)
        assertTrue("parts?.onGear?.takeIf { !top.gearInRow }?.let { HudMenuItem(\"Tracker Setup\", it) }" in ft)
        assertTrue("parts?.onTextTap?.takeIf { top.textTapInMenu }" in ft && "LocalWindowMenuItems provides moved" in ft)
        assertTrue("parts?.swap?.let { Box(Modifier.overhang()) { SwapIconButton(it) } }" in ft, "the swap is never moved off the row")
        assertTrue("for (item in LocalWindowMenuItems.current) DropdownMenuItem(" in code("LandscapeChrome.kt"), "the menu draws them")
        assertTrue("FloatGrabs.of(shown, fitH, barDp.toFloat()" in ft)
    }
}
