package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackTest {
    private val dev = Feedback.Device("Google Pixel 7", "Android 14 (API 34)", "1.0.0-rc15")

    @Test
    fun `a report carries device, version, family and words, and never a file name`() {
        val r = Feedback.compose(dev, "FRLG", "  the tracker froze after NEW RUN ", "I/KaizoCore: line 1\nI/KaizoCore: line 2")
        val lines = r.lines()
        assertEquals("KaizoCore beta feedback", lines[0])
        assertEquals("App: 1.0.0-rc15", lines[1])
        assertEquals("Device: Google Pixel 7, Android 14 (API 34)", lines[2])
        assertEquals("Game family: FRLG", lines[3])
        assertTrue("What happened:\nthe tracker froze after NEW RUN\n" in r)
        assertTrue(r.trimEnd().endsWith("I/KaizoCore: line 2"))
        assertFalse(".gba" in r || ".nds" in r || ".gbc" in r)
    }

    @Test
    fun `empty words and log are said to be empty rather than dropped`() {
        val r = Feedback.compose(dev, null, "", "")
        assertTrue("Game family: none" in r)
        assertTrue("(no description)" in r)
        assertTrue(r.trimEnd().endsWith("(none)"))
    }

    @Test
    fun `the outward links are blank until they exist, so their buttons stay hidden`() {
        // Deliberate: no invented URL ships. Set each when the real one exists.
        for (l in listOf(Feedback.Links.RELEASES, Feedback.Links.SUPPORT, Feedback.Links.BUG_FORM)) {
            assertTrue(l.isBlank() || l.startsWith("https://"), "a link is blank or https: $l")
        }
    }

    /** The last crash rides in the report, before the log tail, and only when there is one. */
    @Test
    fun `a crash report is included when present and absent otherwise`() {
        val dev = Feedback.Device("Pixel", "Android 14 (API 34)", "1.0.0-rc15")
        val with = Feedback.compose(dev, "DPPt", "froze", "log", crash = "REASON_CRASH at 12:00" + System.lineSeparator() + "trace")
        kotlin.test.assertTrue(with.indexOf("Last crash:") in 0 until with.indexOf("Log tail:"))
        kotlin.test.assertTrue("REASON_CRASH at 12:00" in with)
        kotlin.test.assertFalse("Last crash:" in Feedback.compose(dev, "DPPt", "froze", "log"))
        val mail = Feedback.Links.EMAIL
        kotlin.test.assertEquals("blake@willowcreek.group", mail)
    }
}
