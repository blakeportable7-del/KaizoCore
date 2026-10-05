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
        for (part in listOf("LockButton(locked)", "if (!locked) GripDots()", "detectDragGestures", "detectTapGestures(onDoubleTap",
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
    fun `a narrow window gives the words a row of their own, and no button is ever cut off`() {
        // The rc35 minimum width, a battle: lock, grip, swap, gear, menu.
        assertTrue(WindowBarFit.twoRows(FloatFrame.MIN_W, buttons = 3, grip = true))
        assertFalse(WindowBarFit.twoRows(360f, buttons = 3, grip = true), "the usual window: one row")
        assertFalse(WindowBarFit.twoRows(250f, buttons = 2, grip = false), "locked, out of battle")
        // Whichever, the first row's buttons fit the narrowest window: the lock, the grip, the swap and the menu.
        assertTrue(PcMin.TOUCH_DP * 3 + WindowBarFit.GRIP + WindowBarFit.PAD <= FloatFrame.MIN_W)
        val ft = code("FloatingTracker.kt")
        assertTrue("if (twoRows) Row(" in ft && "val barDp = if (twoRows) 2 * BAR_DP else BAR_DP" in ft)
        assertTrue("FloatGrabs.of(shown, fitH, barDp.toFloat()" in ft, "the second row drags the window like the first")
    }
}
