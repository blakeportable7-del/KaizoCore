package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rest of rc32 audit P2 #19 (rc35 follow-ups N #25, #26, #27, #29): the windows TrackerWindowsTest did not reach.
 * The Pokemon and move info windows, Calc Atk, the Nuzlocke ledger, the DS encounter window and route info drew their
 * words in PixText, 7 to 14dp that ignore the phone's font size, and the DS log's tapped words and Hidden Power's arrows
 * were small targets. What a Compose window draws cannot run on the JVM, so it is held to the source, the way
 * TrackerWindowsTest does; the panel's own lines (inside PcCanvas) keep the reference's pixels on purpose.
 */
class TrackerWindowsRestTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    /** [text] with its comments dropped, so a note that names something is not counted as using it. */
    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    /** The body of the function declared by [signature] in [text], up to its closing brace at the start of a line. */
    private fun body(text: String, signature: String): String {
        val at = text.indexOf(signature)
        assertTrue(at >= 0, "no $signature")
        val end = text.indexOf("\n}\n", at)
        return text.substring(at, if (end < 0) text.length else end)
    }

    /** Every call to [name] in [text], from its name to its closing bracket. */
    private fun calls(text: String, name: String): List<String> {
        val out = mutableListOf<String>()
        var from = 0
        while (true) {
            val at = text.indexOf("$name(", from)
            if (at < 0) break
            from = at + 1
            if (at > 0 && (text[at - 1].isLetterOrDigit() || text.substring(maxOf(0, at - 4), at) == "fun ")) continue
            var depth = 0
            var i = at + name.length
            var inString = false
            while (i < text.length) {
                val c = text[i]
                if (inString) { if (c == '\\') i++ else if (c == '"') inString = false }
                else if (c == '"') inString = true
                else if (c == '(') depth++
                else if (c == ')') { depth--; if (depth == 0) break }
                i++
            }
            out += text.substring(at, minOf(i + 1, text.length))
        }
        return out
    }

    /** The second argument of a call written out by [calls], when it is a plain number. */
    private fun secondArg(call: String): Int? {
        var depth = 0
        var inString = false
        val starts = mutableListOf<Int>()
        for ((i, c) in call.withIndex()) {
            if (inString) { if (c == '"' && call[i - 1] != '\\') inString = false; continue }
            when (c) {
                '"' -> inString = true
                '(', '{', '[' -> depth++
                ')', '}', ']' -> depth--
                ',' -> if (depth == 1) starts += i
            }
        }
        if (starts.isEmpty()) return null
        val end = starts.getOrNull(1) ?: (call.length - 1)
        return call.substring(starts[0] + 1, end).trim().removePrefix("size = ").toIntOrNull()
    }

    private fun assertWindow(where: String, text: String) {
        assertFalse("PixText(" in text, "$where draws PixText, which ignores the phone's font size")
        assertFalse("PcSmallButton(" in text, "$where has the panel's small button, drawn in PixText")
        val sizes = calls(text, "DialogText").mapNotNull { secondArg(it) }
        assertTrue(sizes.isNotEmpty(), "$where: no DialogText found, so the scan is not reading it")
        assertTrue(sizes.all { it >= PcMin.LABEL_SP }, "$where has a label under 12sp: $sizes")
    }

    @Test
    fun `the Pokemon and move info windows draw their words in sp, N 26`() {
        val pc = code(read("PcTracker.kt"))
        for (f in listOf("fun PcInfoDialog(", "fun PcPokemonInfo(", "fun PcNameLookup(", "private fun PcInfoRow(", "fun PcCoverage("))
            assertWindow("PcTracker.kt $f", body(pc, f))
        // The shared cards they and the move card are drawn in.
        assertWindow("InfoSheet.kt", code(read("InfoSheet.kt")))
        assertWindow("PcMoveInfo.kt", code(read("PcMoveInfo.kt")))
        // The panel itself keeps the reference's pixels.
        assertTrue("PixText(label, 8, Pc.LowerText)" in body(pc, "private fun PcCarouselLine("))
    }

    @Test
    fun `Calc Atk, the Nuzlocke ledger, the DS encounter window and route info do too, N 29`() {
        assertWindow("CalcAtkScreen.kt", code(read("CalcAtkScreen.kt")))
        assertWindow("RouteInfoScreen.kt", code(read("RouteInfoScreen.kt")))
        val nz = code(read("NuzlockePanel.kt"))
        assertWindow("NuzlockePanel.kt's ledger", nz.substring(nz.indexOf("fun NuzlockeLedgerDialog(")))
        assertTrue("PixText(panel.title, PcRef.FONT, Pc.Gold" in nz, "the panel's own lines stay the panel's")
        val enc = code(read("DsEncounterFrame.kt"))
        assertWindow("DsEncounterDialog", body(enc, "fun DsEncounterDialog("))
        assertWindow("EncounterRow", body(enc, "private fun EncounterRow("))
        assertTrue("PixText(\"\$uniqueSeen/\${area.totalPokemon}\", 8, Pc.Text)" in enc, "the panel's encounter line stays")
        // Their rows and choices are 48dp, and say what they are.
        assertTrue("heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) { editing = key }" in read("CalcAtkScreen.kt"))
        assertTrue(".selectable(selected = on, role = role) { onClick() }" in nz && "heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).toggleable(value = on, role = Role.Switch)" in nz)
        val route = code(read("RouteInfoScreen.kt"))
        assertTrue("heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).toggleable(value = on, role = Role.Checkbox) { onToggle() }" in route)
        // Route info's SEARCH looks up a route, and says so.
        assertTrue("PcTap(\"SEARCH\", 8, Pc.Gold, \"Look up a route\")" in route)
    }

    @Test
    fun `every word the DS log opens something from is a 48dp row, N 27`() {
        val log = code(read("DsLogViewer.kt"))
        var tapped = 0
        for (call in calls(log, "DialogText")) {
            if ("clickable" in call || "selectable(" in call || "dsTap" in call) {
                tapped++
                assertTrue("dsTap" in call || "heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)" in call, "a small tapped word: $call")
            }
        }
        assertTrue(tapped >= 12, "the scan found $tapped tapped words")
        assertTrue("heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(enabled = enabled, role = Role.Button) { onClick() }" in body(log, "private fun Modifier.dsTap("))
    }

    @Test
    fun `Hidden Power's arrows are named buttons, on the DS card and in the move card, N 25`() {
        val pc = code(read("PcTracker.kt"))
        val moves = body(pc, "fun PcMovesSection(")
        assertTrue("HiddenPowerArrow(forward = false) { onHiddenPower(false) }" in moves && "HiddenPowerArrow(forward = true) { onHiddenPower(true) }" in moves)
        val arrow = body(pc, "private fun HiddenPowerArrow(")
        assertTrue("clickable(onClickLabel = spoken, role = Role.Button)" in arrow && "contentDescription = spoken" in arrow)
        assertEquals("Previous Hidden Power type", HiddenPowerCopy.PREVIOUS)
        assertEquals("Next Hidden Power type", HiddenPowerCopy.NEXT)
        val picker = code(read("HiddenPower.kt"))
        assertTrue("HiddenPowerStep(HiddenPowerCopy.PREVIOUS)" in picker && "HiddenPowerStep(HiddenPowerCopy.NEXT)" in picker)
        assertTrue("sizeIn(minWidth = PcMin.DIALOG_TOUCH_DP.dp, minHeight = PcMin.DIALOG_TOUCH_DP.dp)" in picker)
        assertFalse(Regex("PcPixelImage\\([A-Z_]+_ARROW, Pc\\.Text, Modifier\\.clickable").containsMatchIn(picker), "a bare arrow")
    }

    @Test
    fun `level ranges say to, never a spaced hyphen, N 31`() {
        assertEquals("Level 3 to 7", trackedLevelsText(listOf(3, 5, 7), usesRange = true))
        assertEquals("Lv. 1 to 16", DsLog.levelRange(0, listOf(1 to "A", 5 to "B", 9 to "C", 13 to "D", 17 to "E")))
        assertTrue("\"\${ic.minLv} to \${ic.maxLv}\"" in read("RouteInfoScreen.kt"), "route info's Levels")
        assertTrue("\"Lv. \${r.minLevel} to \${r.maxLevel}\"" in read("DsLogViewer.kt"), "the DS log's route rows")
        for (f in listOf("RouteInfoScreen.kt", "DsLogViewer.kt", "DsEncounterFrame.kt", "DsLog.kt"))
            assertFalse(Regex("\\} - \\$").containsMatchIn(read(f)), "$f: a range with a spaced hyphen")
    }
}
