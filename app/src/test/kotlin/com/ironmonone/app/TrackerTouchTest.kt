package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tracker's touch targets and dialog text after the 2026-09-30 UX audit (P0-14 and P0-15): RUN asks twice, the
 * banner's buttons have room, Tracker Setup and Rules read at 12sp or more in the phone's own font size, and the
 * small controls beside them are 48dp. A Compose screen cannot run on the JVM, so what is drawn is held to the
 * source, for exactly the lines a phone would otherwise be needed to see. The two-tap logic runs for real.
 */
class TrackerTouchTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    /** [text] with its comments dropped, so a note that names something is not counted as using it. */
    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    /** The arguments of the call whose opening bracket is just before [start], split at the commas that are not nested. */
    private fun topLevelArgs(text: String, start: Int): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var inString = false
        var current = StringBuilder()
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                current.append(c)
                if (c == '\\') { current.append(text[i + 1]); i++ } else if (c == '"') inString = false
            } else when (c) {
                '"' -> { inString = true; current.append(c) }
                '(', '{', '[' -> { depth++; current.append(c) }
                ')', '}', ']' -> {
                    if (depth == 0) { out += current.toString(); return out }
                    depth--; current.append(c)
                }
                ',' -> if (depth == 0) { out += current.toString(); current = StringBuilder() } else current.append(c)
                else -> current.append(c)
            }
            i++
        }
        return out
    }

    /** How many DialogText calls [text] makes, and the size of each one that spells it as a number. */
    private fun dialogTextSizes(text: String): Pair<Int, List<Int>> {
        var calls = 0
        val sizes = mutableListOf<Int>()
        var from = 0
        while (true) {
            val at = text.indexOf("DialogText(", from)
            if (at < 0) break
            from = at + 1
            if (text.substring(maxOf(0, at - 4), at) == "fun ") continue
            calls++
            val second = topLevelArgs(text, at + "DialogText(".length).getOrNull(1)?.trim() ?: continue
            second.removePrefix("size = ").toIntOrNull()?.let { sizes += it }
        }
        return calls to sizes
    }

    // ---------------------------------------------------------------- RUN asks twice

    @Test
    fun `the first tap arms RUN and a second inside three seconds runs`() {
        val t = ArmedTap()
        assertFalse(t.armed, "nothing is armed to begin with")
        assertFalse(t.tap(1_000), "the first tap only arms it")
        assertTrue(t.armed)
        assertTrue(t.tap(3_999), "a second tap 2999 ms later runs")
        assertFalse(t.armed, "and it is spent")
        assertFalse(t.tap(5_000), "the next tap starts over")
        assertTrue(t.armed)
    }

    @Test
    fun `a second tap after the window arms it again instead of running`() {
        val t = ArmedTap()
        t.tap(0)
        assertFalse(t.tap(3_001), "3001 ms is too late: it arms again")
        assertTrue(t.armed)
        assertTrue(t.tap(3_001 + 2_999), "the new window counts from the second tap")

        val edge = ArmedTap()
        edge.tap(0)
        assertTrue(edge.tap(3_000), "exactly three seconds is still inside")
    }

    @Test
    fun `the timer disarms it, so a stray tap later starts over`() {
        val t = ArmedTap()
        t.tap(0)
        t.disarm()
        assertFalse(t.armed)
        assertFalse(t.tap(10), "after the timer, a tap arms again and does not run")
        assertTrue(t.armed)
    }

    @Test
    fun `the window is three seconds, the length of the library's Sure`() {
        assertEquals(3000L, ArmedTap.WINDOW_MS)
    }

    @Test
    fun `RUN goes through the guard, reads RUN with a question mark in the warning colour, and a timer per arming disarms it`() {
        val pc = code(read("PcTracker.kt"))
        assertTrue("if (run.tap(android.os.SystemClock.elapsedRealtime())) onFlee()" in pc, "flee only when the guard says so")
        assertFalse(Regex("PcSmallButton\\(\"RUN\"|onClick = onFlee|, onFlee\\)").containsMatchIn(pc), "no route from a tap straight to onFlee")
        assertTrue("color = if (run.armed) Pc.Negative else Pc.Text" in pc, "RUN? is drawn in the warning colour")
        assertTrue("LaunchedEffect(run.armedAt)" in pc && "delay(ArmedTap.WINDOW_MS); run.disarm()" in pc, "three seconds, then it disarms")
        assertEquals("RUN?", PcBannerCopy.run(true))
        assertEquals("RUN", PcBannerCopy.run(false))
        assertTrue(PcBannerCopy.runSpoken(true) != PcBannerCopy.runSpoken(false), "a screen reader hears the change")
    }

    // ---------------------------------------------------------------- room to touch

    @Test
    fun `the tracker's buttons have a 44dp box and RUN and SEE MINE a clear gap`() {
        assertTrue(PcMin.TOUCH_DP >= 44, "the touch box")
        assertTrue(PcMin.BUTTON_GAP_DP >= 8, "the gap between RUN and SEE MINE")
        val pc = read("PcTracker.kt")
        assertTrue("modifier.sizeIn(minWidth = PcMin.TOUCH_DP.dp, minHeight = PcMin.TOUCH_DP.dp)" in pc, "the box is real, both ways")
        assertTrue("Spacer(Modifier.width(PcMin.BUTTON_GAP_DP.dp))" in pc, "and the two boxes do not touch")
        // Both banners draw SEE MINE and SEE FOE through it, and neither still draws the old 17dp button.
        assertTrue("PcButton(PcBannerCopy.see(viewingOwn)" in pc)
        val ds = read("DsView.kt")
        assertTrue("PcButton(PcBannerCopy.see(viewingOwn)" in ds)
        assertFalse("PcSmallButton(" in ds)
    }

    @Test
    fun `SETUP is a tracker button in both panels, so it gets the same 44dp box`() {
        for (f in listOf("TrackerPanel.kt", "NdsTrackerPanel.kt")) {
            assertTrue("PcSmallButton(\"SETUP\") { g() }" in read(f), "$f draws SETUP as a tracker button")
            // In a battle SETUP ends the battle banner instead of taking a row of its own (2026-10-02, "wasted space").
            assertTrue("onGear = onGear" in read(f), "$f hands SETUP to the battle banner")
        }
        val pc = read("PcTracker.kt")
        assertTrue("fun PcSmallButton(label: String, onClick: () -> Unit) = PcButton(label, onClick = onClick)" in pc,
            "and every tracker button takes the box")
        val banner = pc.substringAfter("fun PcBattleBanner(").substringBefore("\n}\n")
        assertTrue("PcButton(\"SETUP\", spoken = \"Tracker Setup\", onClick = it)" in banner, "the banner's SETUP is a tracker button too")
        assertTrue("buttons = onSwapView != null || isWild || onGear != null" in banner, "and its band is 44dp tall for it")
    }

    @Test
    fun `the banner is a band the height of the box, so its buttons never reach the row above or the card below`() {
        val pc = read("PcTracker.kt")
        val band = pc.substringAfter("internal fun PcBannerBand(").substringBefore("\n}\n")
        // KaizoCore's look (2026-10-02): the drawn band IS the touch row, a rounded bar at least as tall as a button's
        // box, where it was a thin strip with blank space above and below it for the boxes.
        assertTrue("heightIn(min = if (buttons) PcMin.TOUCH_DP.dp else (PcRef.FONT + 8).rp)" in band, "the band is 44dp tall while it has buttons")
        assertFalse(Regex("padding\\((vertical|top|bottom) =|padding\\(\\d").containsMatchIn(band),
            "nothing pads the band above or below, so a 44dp box fits inside a 44dp band")
        assertTrue("Box(Modifier.weight(1f)) { label() }" in band, "the label gives way, the buttons never do")
        // DS keeps its solid strip: only the battle banner lets the tracker's image show through.
        assertTrue("fill = Pc.Ground, buttons = true" in read("DsView.kt"))
    }

    // ---------------------------------------------------------------- dialog text

    @Test
    fun `a dialog label is in sp and never under 12`() {
        assertEquals(12, PcMin.LABEL_SP)
        assertEquals(12, dialogSp(6))
        assertEquals(12, dialogSp(7))
        assertEquals(12, dialogSp(11))
        assertEquals(12, dialogSp(12))
        assertEquals(15, dialogSp(15))
        val fn = read("PcTracker.kt").substringAfter("fun DialogText(").substringBefore("\n}\n")
        assertTrue("fontSize = sp.sp" in fn, "sp, which follows the phone's font size")
        assertFalse(".rsp" in fn, "not reference pixels, which do not")
    }

    /** Tracker Setup's own code: the file up to PcTap, whose glyphs (X, <, >) are the tracker's pixels in a 48dp box. */
    private fun gearCode() = code(read("TrackerGearDialog.kt")).substringBefore("internal fun PcTap(")

    @Test
    fun `Tracker Setup and Rules draw no reference-pixel text, and every size they spell is 12 or more`() {
        for (f in listOf("TrackerGearDialog.kt", "RulesDialog.kt")) {
            val text = if (f == "TrackerGearDialog.kt") gearCode() else code(read(f))
            assertFalse("PixText(" in text, "$f still draws PixText, which ignores the phone's font size")
            assertFalse(".rsp" in text, "$f sizes text in reference pixels")
            for (m in Regex("fontSize = (\\d+)\\.sp").findAll(text)) {
                assertTrue(m.groupValues[1].toInt() >= 12, "$f has fontSize ${m.groupValues[1]}sp")
            }
            val (calls, sizes) = dialogTextSizes(text)
            assertTrue(calls >= 5, "$f: found only $calls DialogText calls, so the scan is not reading it")
            assertTrue(sizes.isNotEmpty() && sizes.all { it >= 12 }, "$f has a label under 12sp: $sizes")
        }
        assertTrue(GEAR_LABEL_SP >= 12)
        // The calls whose size is a name are the toggle row's and the button's, and the name is checked above.
        val (calls, sizes) = dialogTextSizes(gearCode())
        assertEquals(2, calls - sizes.size, "only GearToggle's and GearButton's labels take their size from GEAR_LABEL_SP")
        // Play as your Pokemon's section in the gear is drawn at the same scale: no reference-pixel text.
        assertFalse("PixText(" in code(read("SpriteIsMeUi.kt")), "SpriteIsMeUi.kt draws PixText in Tracker Setup")
    }

    @Test
    fun `a checkbox or radio row is 48dp, wraps its label, and says checked or selected`() {
        val fn = read("TrackerGearDialog.kt").substringAfter("internal fun GearToggle(").substringBefore("\n}\n")
        assertTrue(PcMin.DIALOG_TOUCH_DP >= 48)
        assertTrue("heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)" in fn, "48dp tall")
        assertTrue("selectable(selected = on, role = Role.RadioButton)" in fn, "a radio row is selected, to a screen reader too")
        assertTrue("toggleable(value = on, role = Role.Checkbox)" in fn, "a checkbox row is checked, to a screen reader too")
        assertTrue("DialogText(label, GEAR_LABEL_SP, Pc.Text, Modifier.weight(1f)" in fn, "the label wraps in the space beside the box")
    }

    @Test
    fun `the Rules tabs are 48dp and the chosen one is underlined, framed heavier and selected`() {
        val rules = read("RulesDialog.kt")
        assertTrue("heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)" in rules, "48dp tall")
        assertTrue("selectable(selected = on, role = Role.Tab)" in rules, "a screen reader hears the chosen tab as selected")
        assertTrue("underline = on" in rules, "an underline, not only a colour")
        assertTrue("Modifier.border(if (on) 2.dp else 1.dp, if (on) Pc.Gold else Pc.Border)" in rules, "and a heavier frame")
    }

    // ---------------------------------------------------------------- the words

    @Test
    fun `the PC trackers' names stay, and each option keeps the setting behind it`() {
        val gear = read("TrackerGearDialog.kt")
        val live = code(gear)
        val renamedToggles = mapOf(
            "Determine friendship readiness" to "TrackerOptions.determineFriendship",
            "Calculate variable damage" to "TrackerOptions.calculateVariableDamage",
            "Count enemy PP usage" to "TrackerOptions.countEnemyPp",
            "Open Book Play Mode" to "TrackerOptions.openBookPlayMode",
        )
        for ((label, setting) in renamedToggles) {
            val line = gear.lines().single { "GearToggle(\"$label\"" in it }
            assertTrue("GearToggle(\"$label\", $setting)" in line, "the toggle reads $setting")
            assertTrue("$setting = it; TrackerOptions.save()" in line, "and writes it")
        }
        assertTrue("GearButton(\"COVERAGE CALC\") { onCoverage() }" in gear)
        assertTrue("onTimeMachine?.let { GearButton(\"TIME MACHINE\") { it() } }" in gear)
        // IronMON players know these names from the PC trackers, and so does its dev team: plainer ones were tried
        // and taken back (2026-09-30). None of them may return in place of the PC tracker's name.
        for (renamed in listOf(
            "TYPE COVERAGE", "RESTORE POINTS", "Show when friendship evolutions are ready", "Estimate damage for variable-power moves",
            "Count the enemy's PP used", "Show all opponent info (open book)", "The run is over when",
        )) assertFalse(renamed in live, "$renamed replaced a PC tracker name")
    }

    @Test
    fun `the rule that ends the run heads Tracker Setup, in the PC trackers' words, on every game`() {
        val gear = read("TrackerGearDialog.kt")
        val call = gear.indexOf("RunOverSection(ds, runSettingsName)")
        assertTrue(call > 0, "the section is called")
        assertTrue(call < gear.indexOf("GearButton(\"RULES FOR THIS RUN\")"), "before the tools")
        assertTrue(call < gear.indexOf("GearHead(\"Options\")"), "before the options")
        assertEquals(1, Regex(Regex.escape("if (ds) \"Run is considered over when\" else \"Game is considered over when\"")).findAll(code(gear)).count(),
            "one head, in the PC trackers' words, for the DS setting and the other")
        // No game gates it: the call stands alone on its line and the section takes no Game Boy flag. The mode does: a
        // Kaizo IronMON run's rule, not a Nuzlocke's or a library game's (2026-10-01).
        assertTrue("if (scope.ironmon) {\n                RunOverSection(ds, runSettingsName)" in gear.replace("\r\n", "\n"))
        assertTrue(gear.lines().single { "RunOverSection(ds, runSettingsName)" in it && "fun " !in it }.trim() == "RunOverSection(ds, runSettingsName)")
        val section = gear.substringAfter("private fun RunOverSection(").substringBefore("\n}\n")
        assertEquals(2, Regex("LossCondition\\.entries\\.forEach").findAll(section).count(), "the DS choice and the Game Boy and Gen 3 choice")
        assertFalse("gameBoy" in section)
    }

    // ---------------------------------------------------------------- the info card's X

    @Test
    fun `the info card's X says Close and is a 48dp target`() {
        assertEquals("Close", InfoSheetClose.SPOKEN)
        assertTrue(InfoSheetClose.TOUCH_DP >= 48)
        val card = read("InfoSheet.kt").substringAfter("fun InfoCard(").substringBefore("\n}\n")
        assertTrue("Modifier.size(InfoSheetClose.TOUCH_DP.dp)" in card, "the touch box")
        assertTrue("contentDescription = InfoSheetClose.SPOKEN" in card, "what a screen reader reads")
        assertFalse("size(34.dp).border(1.dp, Pc.Border).clickable" in card, "the 34dp drawn X is not the target any more")
    }

    // ---------------------------------------------------------------- copy rules

    @Test
    fun `the five files carry no em dash or en dash, never say how the work is made, and spell Pokemon with its accent`() {
        for (name in listOf("PcTracker.kt", "DsView.kt", "TrackerGearDialog.kt", "RulesDialog.kt", "InfoSheet.kt")) {
            val text = read(name)
            assertFalse(text.contains(0x2014.toChar()), "$name has an em dash")
            assertFalse(text.contains(0x2013.toChar()), "$name has an en dash")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(text), "$name mentions AI")
        }
        val accented = "Pok" + 0xE9.toChar() + "mon"
        for (spoken in listOf(PcBannerCopy.SEE_MINE_SPOKEN, PcBannerCopy.SEE_FOE_SPOKEN)) {
            assertTrue(accented in spoken, spoken)
        }
        for (copy in listOf(PcBannerCopy.RUN_SPOKEN, PcBannerCopy.RUN_ARMED_SPOKEN, PcBannerCopy.TRAINER_SPOKEN)) {
            assertFalse(copy.contains(0x2014.toChar()) || copy.contains(0x2013.toChar()), copy)
            assertFalse(Regex("\\bAI\\b").containsMatchIn(copy), copy)
            assertFalse("Pokemon" in copy, "$copy needs the accent")
        }
    }
}
