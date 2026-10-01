package com.ironmonone.app

import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tracker on a second display (roadmap item 5). The display itself is checked on the
 * emulator's overlay display (docs/QA-RC30.md); here, that the play screen hands the tracker
 * over in every layout and that the host never takes the game down.
 */
class SecondScreenTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test fun `the play screen gives the tracker to a second display in every layout`() {
        val play = src("PlayScreen.kt")
        assertTrue("val trackerOnSecond = SecondScreenHost(session.tracked && !streamClean && trackerOpen && TrackerOptions.trackerOnSecondScreen) { trackerContent() }" in play)
        assertTrue("if (landscape && !streamClean && !trackerOnSecond) trackerPane()" in play, "landscape, docked")
        assertTrue("session.tracked && !trackerOnSecond && TrackerOptions.landscapeTracker == LandscapeTracker.FLOATING" in play, "landscape, floating")
        assertEquals(2, Regex("""if \(streamClean \|\| !session\.tracked \|\| trackerOnSecond\)""").findAll(play).count(), "portrait: the spacer and the panel")
    }

    @Test fun `a refused display leaves the tracker on the phone`() {
        val host = src("SecondScreen.kt")
        assertTrue("showing = runCatching { presentation.show(); true }.getOrDefault(false)" in host)
        assertFalse("throw e" in host, "no error from an optional screen is rethrown")
        assertTrue(") { SecondScreenFrame { latest.value() } }" in host, "the theme and the canvas size are set again there")
        assertTrue("LocalActivityResultRegistryOwner provides activity" in host, "the camera's launcher finds its registry there")
        assertTrue("awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }" in host,
            "view only: no dialog opens from a presentation window")
        assertTrue("var trackerOnSecondScreen by mutableStateOf(false)" in
            File("src/main/kotlin/com/ironmonone/app/TrackerOptions.kt").readText(), "off until seen on a real second screen")
    }

    @Test fun `with the tracker on the second screen the phone still reaches its setup`() {
        // The second screen swallows every touch, so its SETUP is not a way in (emulator QA, 2026-09-29).
        val menu = src("PlayScreen.kt").substringAfter("val FileMenu: @Composable () -> Unit = {").substringBefore("MenuRule()\n            // Tools.")
        assertTrue("if (session.tracked && TrackerOptions.trackerOnSecondScreen) com.ironmonone.app.gen3.Gen3Button(\"TRACKER SETUP\", onClick = { menuOpen = false; gearDialog = true })" in menu,
            "the phone's FILE menu opens the tracker's setup while the tracker may be on the other screen")
        assertTrue("GearToggle(\"Tracker on the second screen (view only)\"" in src("TrackerGearDialog.kt"), "and the setup is where it is switched off")
    }

    @Test fun `the empty tracker's line wraps instead of being cut off`() {
        assertTrue("PixText(\"No Pokemon yet. The tracker fills in when you get your first one.\", 8, Pc.Dim, wrap = true)" in src("TrackerPanel.kt"), "GBA")
        assertTrue("PixText(\"No Pokemon yet. The tracker fills in when you get your first one.\", 8, Pc.Dim, wrap = true)" in src("NdsTrackerPanel.kt"), "DS")
    }

    @Test fun `the column fits the display`() {
        assertEquals(405.dp, secondScreenColumnWidth(960.dp, 540.dp), "a 16:9 screen: three quarters of its height")
        assertEquals(360.dp, secondScreenColumnWidth(360.dp, 800.dp), "a tall screen: its width")
    }
}
