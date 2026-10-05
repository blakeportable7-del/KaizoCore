package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The floating window's wide view (Blake, 2026-10-04). The switch-over runs for real; the layout is held to the source. */
class TrackerWideViewTest {
    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun code(name: String) = File(src, name).readText().replace("\r\n", "\n")
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    @Test
    fun `wide at two and a half to one, the stack when taller, and never below a readable unit`() {
        assertTrue(TrackerWideView.applies(750f, 300f), "2.5 to 1")
        assertTrue(TrackerWideView.applies(800f, 250f))
        assertFalse(TrackerWideView.applies(700f, 300f), "taller than that: the stack")
        assertFalse(TrackerWideView.applies(500f, 150f), "wide enough in shape, too small to read: the stack")
        assertFalse(TrackerWideView.applies(800f, 0f))
        // The unit never drops under the wide card's least, and never grows past the cap.
        val least = TrackerWideView.UNITS * WIDE_MIN_RPX.value
        assertTrue(TrackerWideView.applies(least, least / 2.5f))
        assertFalse(TrackerWideView.applies(least - 1f, (least - 1f) / 2.5f))
        assertEquals(TrackerWideView.MAX_RPX, TrackerWideView.unit(androidx.compose.ui.unit.Dp(5000f)).value)
    }

    @Test
    fun `the wide view draws the same cards with the same switches, in every panel`() {
        // No option of its own and no copy of a card: the panels' own calls sit inside WideCards.
        val wide = code("TrackerWideView.kt")
        assertFalse("TrackerOptions" in wide, "the wide view reads no switch of its own")
        for ((file, cards) in listOf(
            "TrackerPanel.kt" to listOf("PartyCard(", "EnemyCard(", "PcCarousel("),
            "NdsTrackerPanel.kt" to listOf("NdsPartyCard(", "NdsEnemyCard(", "PcCarousel("),
        )) {
            val body = code(file)
            val start = body.indexOf("WideCards(left = {")
            val mid = body.indexOf("}, right = {", start)
            assertTrue(start > 0 && mid > start, "$file wraps its cards")
            assertTrue(body.indexOf(cards[0], start) in start..mid, "$file: your card on the left")
            assertTrue(body.indexOf(cards[1], mid) > mid && body.indexOf(cards[2], mid) > mid, "$file: the rest on the right")
            assertEquals(1, body.split(cards[1] + "onMoveHistory").size - 1, "$file: one opponent card, not a copy")
        }
        val ft = code("FloatingTracker.kt")
        assertTrue("LocalTrackerWideView provides wideView" in ft)
        assertTrue("LocalTrackerRoom provides room.takeUnless { wideView }" in ft, "both cards at once when wide")
        // Only the floating window turns it on.
        val providers = src.listFiles { f -> f.extension == "kt" }!!.filter { "LocalTrackerWideView provides" in code(it.name) }.map { it.name }
        assertEquals(listOf("FloatingTracker.kt"), providers)
    }
}
