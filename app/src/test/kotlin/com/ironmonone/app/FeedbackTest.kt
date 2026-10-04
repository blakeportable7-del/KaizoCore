package com.ironmonone.app

import java.io.File
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

    /**
     * Blake, 2026-10-04: the IronMON community is free and KaizoCore asks for money nowhere. The support link is blank,
     * so its button never shows, and no payment, donation or tip link ships in the app's code, strings or assets. The
     * hack creators' own pages in HackLinks are theirs to link, and stay.
     */
    @Test
    fun `the app offers no payment link`() {
        assertTrue(Feedback.Links.SUPPORT.isBlank(), "no support button")
        val pay = Regex("buy\\.stripe\\.com|checkout\\.stripe|paypal\\.(com|me)|patreon\\.com|ko-fi\\.com|buymeacoffee|github\\.com/sponsors|liberapay|cash\\.app|venmo\\.com|\\bdonat(e|ion)", RegexOption.IGNORE_CASE)
        val roots = listOf("src/main/kotlin", "src/main/res", "src/main/assets").map { File(it) }
        val hits = roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml", "html", "js", "json", "md", "txt") } }
            .filter { it.name != "HackLinks.kt" && "licenses" !in it.path.replace('\\', '/') }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if (pay.containsMatchIn(line)) "${f.path}:${i + 1}: ${line.trim()}" else null } }
        assertTrue(hits.isEmpty(), "payment links or wording in the app:\n" + hits.joinToString("\n"))
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

    /**
     * rc32 audit P3 #16: Share instead, where a phone with no mail app is sent, built its own report and left out the
     * last crash the card promises. Both buttons send the one report now, the crash in it.
     */
    @Test
    fun `Email Blake and Share instead send the same report, with the last crash`() {
        val about = java.io.File("src/main/kotlin/com/ironmonone/app/AboutScreen.kt").readText().replace("\r\n", "\n")
        val code = about.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")
        assertEquals(1, Regex("Feedback\\.compose\\(").findAll(code).count(), "one place builds the report")
        val build = code.substringAfter("Feedback.compose(").substringBefore("\n                )")
        assertTrue("CrashLog.existing(context)" in build, "with the last crash")
        val email = code.substringAfter("Gen3Button(\"Email Blake\"").substringBefore("Gen3Button(\"Share instead\")")
        val share = code.substringAfter("Gen3Button(\"Share instead\")").substringBefore("fun link(")
        assertTrue("report(device)" in email, "Email Blake sends it")
        assertTrue("report(Feedback.device(context))" in share, "and so does Share instead")
        // The report itself carries the crash when there is one.
        assertTrue("Last crash:" in Feedback.compose(dev, "DPPt", "froze", "log", "a crash"))
    }
}
