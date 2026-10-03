package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The tracker's windows after the rc32 audit (P2 #19, #23, #26, #42, #43, #102, P3 #44, #74): their words follow the
 * phone's font size, their controls are 44 to 48dp targets with a spoken name, and the game-over card is never shrunk.
 * A Compose screen cannot run on the JVM, so what is drawn is held to the source, as TrackerTouchTest does; the pure
 * parts run for real.
 */
class TrackerWindowsTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    /** [text] with its comments dropped, so a note that names something is not counted as using it. */
    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    /** The body of the function declared by [signature] in [text], up to its closing brace at the start of a line. */
    private fun body(text: String, signature: String): String {
        val at = text.indexOf(signature)
        assertTrue(at >= 0, "no $signature")
        return text.substring(at, text.indexOf("\n}\n", at))
    }

    /** The arguments of every call to [name] in [text], each as written. */
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
            out += text.substring(at + name.length + 1, i)
        }
        return out
    }

    /** [args] split at the commas that are not inside a string or a bracket. */
    private fun topLevel(args: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var inString = false
        var current = StringBuilder()
        var i = 0
        while (i < args.length) {
            val c = args[i]
            if (inString) {
                current.append(c)
                if (c == '\\') { current.append(args[i + 1]); i++ } else if (c == '"') inString = false
            } else when (c) {
                '"' -> { inString = true; current.append(c) }
                '(', '{', '[' -> { depth++; current.append(c) }
                ')', '}', ']' -> { depth--; current.append(c) }
                ',' -> if (depth == 0) { out += current.toString(); current = StringBuilder() } else current.append(c)
                else -> current.append(c)
            }
            i++
        }
        out += current.toString()
        return out
    }

    /** Each DialogText size spelled as a number. */
    private fun dialogSizes(text: String): List<Int> =
        calls(text, "DialogText").mapNotNull { topLevel(it).getOrNull(1)?.trim()?.removePrefix("size = ")?.toIntOrNull() }

    // ---------------------------------------------------------------- P2 #19: words that follow the font size

    private val windows = listOf(
        "DeathQuotesDialog.kt", "TrainerScreens.kt", "HealsInBag.kt", "CoverageCalc.kt", "RandomEvos.kt", "Notebook.kt",
        "PastRuns.kt", "CatchRates.kt", "BattleDetails.kt", "TimeMachine.kt", "MoveHistoryStats.kt", "ScoreSheet.kt",
        "DsLogViewer.kt", "GameOverDialog.kt",
    )

    @Test
    fun `every tracker window draws its words in DialogText, at 12sp or more`() {
        for (f in windows) {
            val text = code(read(f))
            assertFalse("PixText(" in text, "$f draws PixText, which ignores the phone's font size")
            val sizes = dialogSizes(text)
            assertTrue(sizes.isNotEmpty(), "$f: no DialogText found, so the scan is not reading it")
            assertTrue(sizes.all { it >= PcMin.LABEL_SP }, "$f has a label under 12sp: $sizes")
        }
        // The step goal opens from the carousel, inside the panel, and took the panel's pixel size.
        val goal = body(code(read("Pedometer.kt")), "private fun StepGoalDialog(")
        assertFalse("PixText(" in goal, "the step goal dialog")
        assertTrue(dialogSizes(goal).all { it >= PcMin.LABEL_SP })
        // The panel's own line keeps the reference's pixels: it is drawn inside the PcCanvas.
        assertTrue("PixText(\"Steps: \"" in read("Pedometer.kt"))
    }

    @Test
    fun `the converted tables let a value grow instead of cutting it`() {
        for (f in listOf("DsLogViewer.kt", "CatchRates.kt", "MoveHistoryStats.kt", "PastRuns.kt", "Notebook.kt")) {
            val text = code(read(f))
            // A number in a fixed column wraps into pieces at a large font: the columns are a least width now.
            for (args in calls(text, "DialogText")) {
                val fixed = Regex("Modifier\\.width\\((\\d+)\\.dp\\)").find(args)?.groupValues?.get(1)?.toInt() ?: continue
                assertTrue(fixed >= 96, "$f: a ${fixed}dp fixed column in DialogText($args)")
            }
        }
        // Learned levels flow onto a new line instead of past the dialog's edge.
        assertTrue("FlowRow(" in read("MoveHistoryStats.kt"))
        assertFalse("learnLevels.chunked(8)" in read("MoveHistoryStats.kt"))
    }

    // ---------------------------------------------------------------- P2 #102, P3 #74: glyph buttons

    @Test
    fun `no glyph is tapped bare, X, the arrows and SEARCH are PcTap with a spoken name`() {
        val glyph = "\"(X|<|>|SEARCH)\""
        val bare = Regex("PixText\\($glyph[^\\n]*clickable")
        val inBox = Regex("clickable[^\\n]*\\)\\s*\\{\\s*PixText\\($glyph")
        val hits = mutableListOf<String>()
        for (f in dir.listFiles()!!.filter { it.extension == "kt" }) {
            var text = code(f.readText().replace("\r\n", "\n"))
            if (f.name == "TrackerGearDialog.kt") text = text.replace(body(text, "internal fun PcTap("), "")
            // The panel's move rows are swept too since Hidden Power's arrows became named buttons (rc35 follow-up N #25).
            text.lines().forEachIndexed { i, line -> if (bare.containsMatchIn(line) || inBox.containsMatchIn(line)) hits += "${f.name}:${i + 1}: ${line.trim()}" }
        }
        assertTrue(hits.isEmpty(), "bare glyph buttons:\n" + hits.joinToString("\n"))
        // The three dialogs that read "X" to a screen reader close through PcTap now (P3 #74).
        for (f in listOf("TrainerScreens.kt", "CoverageCalc.kt")) {
            val text = code(read(f))
            assertTrue(calls(text, "PcTap").isNotEmpty() && calls(text, "PcTap").all { "\"Close\"" in it }, "$f closes only through PcTap(..., \"Close\")")
        }
        assertEquals(2, Regex("PcTap\\(\"X\", 9, Pc\\.Dim, \"Close\"\\) \\{ onClose\\(\\) \\}").findAll(read("TrainerScreens.kt")).count(), "Trainers on Route and Trainer Info")
        for (f in listOf("HealsInBag.kt", "MoveHistoryStats.kt", "RandomEvos.kt", "ScoreSheet.kt")) assertTrue("PcTap(\"X\", 9, Pc.Dim, \"Close\")" in read(f), f)
        assertTrue("\"Previous evolution\"" in read("RandomEvos.kt") && "\"Next evolution\"" in read("RandomEvos.kt"))
        assertTrue("\"Previous encounter area\"" in read("RouteInfoScreen.kt") && "\"Next encounter area\"" in read("RouteInfoScreen.kt"))
        val info = body(read("PcTracker.kt"), "fun PcPokemonInfo(")
        for (s in listOf("\"Previous Pok\\u00e9mon\"", "\"Next Pok\\u00e9mon\"", "\"Look up a Pok\\u00e9mon\"")) assertTrue(s in info, s)
        assertTrue("if (history.isEmpty()) \"Close\" else \"Back\"" in read("DsLogViewer.kt"), "the DS log's X, or its back arrow")
    }

    @Test
    fun `a tab in these windows is 48dp, selectable and marked by more than a colour`() {
        val heals = read("HealsInBag.kt")
        assertTrue("heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)" in heals && "selectable(selected = on, role = Role.Tab)" in heals && "underline = on" in heals)
        val calc = read("CoverageCalc.kt")
        assertTrue("selectable(selected = on, role = Role.Tab) { tab = mult }" in calc, "Coverage Calc's buckets")
        assertTrue("toggleable(value = fullyEvolved, role = Role.Checkbox)" in calc, "and its switch")
        assertTrue("selectable(selected = on, role = Role.Tab) { openTab(t) }" in read("DsLogViewer.kt"), "the DS log's tabs")
    }

    // ---------------------------------------------------------------- P2 #42, #43: the PC heal counter

    @Test
    fun `the heal counter is one 44dp target that opens a sheet, and the heart is no switch`() {
        val counter = body(code(read("PcHeals.kt")), "internal fun PcHealCounter(")
        assertFalse("PcHeals.autoTracking = !PcHeals.autoTracking" in counter, "one stray tap on the heart switched automatic counting")
        assertFalse(Regex("PixText\\(\"[+-]\"[^\\n]*clickable").containsMatchIn(counter), "the + and - are not 5dp targets of their own")
        assertEquals(1, Regex("\\.clickable\\(").findAll(counter).count(), "one target for the whole counter")
        assertTrue("sizeIn(minWidth = PcMin.TOUCH_DP.dp, minHeight = PcMin.TOUCH_DP.dp)" in counter)
        assertTrue("contentDescription = PcHealsCopy.spoken(n)" in counter, "and a screen reader hears the count")
        val sheet = body(code(read("PcHeals.kt")), "private fun PcHealsSheet(")
        assertTrue("GearButton(PcHealsCopy.ADD) { PcHeals.add(attempt, +1) }" in sheet)
        assertTrue("GearButton(PcHealsCopy.REMOVE) { PcHeals.add(attempt, -1) }" in sheet)
        assertTrue("if (countsItself) GearToggle(PcHealsCopy.AUTO, PcHeals.autoTracking) { PcHeals.autoTracking = it }" in sheet, "a labelled switch, not on Game Boy")
        assertEquals("PC heals left: 7", PcHealsCopy.spoken(7, down = true))
        assertEquals("PC heals used: 3", PcHealsCopy.spoken(3, down = false))
        for (s in listOf(PcHealsCopy.TITLE, PcHealsCopy.ADD, PcHealsCopy.REMOVE, PcHealsCopy.AUTO, PcHealsCopy.CHANGE, PcHealsCopy.count(4, true)))
            assertFalse(s.contains(0x2014.toChar()) || s.contains(0x2013.toChar()) || " - " in s, s)
    }

    // ---------------------------------------------------------------- P3 #44: the ball call

    @Test
    fun `the ball call says which ball, and the die is a named 44dp button`() {
        val picker = body(read("PcTracker.kt"), "fun PcBallPicker(")
        assertTrue("clearAndSetSemantics { contentDescription = BallCallCopy.spoken(label); selected = chosen }" in picker)
        val dice = body(read("PcTracker.kt"), "fun PcDiceButton(")
        assertTrue("sizeIn(minWidth = PcMin.TOUCH_DP.dp, minHeight = PcMin.TOUCH_DP.dp)" in dice)
        assertTrue("contentDescription = BallCallCopy.REROLL" in dice && "role = Role.Button" in dice)
        assertFalse("size(24.dp)" in dice)
        assertEquals("Middle ball", BallCallCopy.spoken("MIDDLE"))
        assertEquals("Left ball", BallCallCopy.spoken("LEFT"))
    }

    // ---------------------------------------------------------------- P2 #23: the game-over card

    @Test
    fun `the game-over card is never shrunk, it scrolls inside a short picture`() {
        // A 300px card in a 228px picture (portrait on a 360dp phone): full size, scrolling. It was scaled to 0.76.
        assertEquals(228 - 12, GameOverFit.maxHeight(228f, 6f, 2000))
        assertTrue(GameOverFit.scrolls(300, GameOverFit.maxHeight(228f, 6f, 2000)))
        assertFalse(GameOverFit.scrolls(200, GameOverFit.maxHeight(228f, 6f, 2000)))
        assertEquals(1800, GameOverFit.maxHeight(null, 6f, 1800), "no picture: the window's height")
        assertEquals(GameOverFit.NO_BOUND, GameOverFit.maxHeight(null, 6f, androidx.compose.ui.unit.Constraints.Infinity), "always a bound")
        val src = code(read("GameOverDialog.kt"))
        assertFalse("coerceAtLeast(0.4f)" in src || "scaleX = s" in src, "nothing scales the card")
        assertTrue("border(2.dp, accent).verticalScroll(rememberScrollState())" in src, "the card scrolls")
        assertTrue("Modifier.size(InfoSheetClose.TOUCH_DP.dp).clickable(role = Role.Button) { onContinue() }" in src)
        assertTrue("contentDescription = InfoSheetClose.SPOKEN" in src, "the X says Close")
        val tile = body(src, "private fun GameOverTile(")
        assertTrue("heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)" in tile && "height(40.dp)" !in tile)
        assertTrue("Modifier.size(PcMin.DIALOG_TOUCH_DP.dp).clickable { pick(i) }" in src, "the team's pictures are 48dp to tap")
    }

    // ---------------------------------------------------------------- P2 #26: the randomizer log's small targets

    @Test
    fun `no word in the randomizer log is a thin target`() {
        val logs = dir.listFiles()!!.filter { it.name.startsWith("Log") && it.extension == "kt" }
        assertTrue(logs.size >= 8, "found the log viewer's files: ${logs.map { it.name }}")
        for (f in logs) {
            for (args in calls(code(f.readText().replace("\r\n", "\n")), "DialogText")) {
                if (".clickable" in args) assertTrue("DIALOG_TOUCH_DP" in args, "${f.name}: a tapped word with no 48dp box: DialogText($args)")
            }
        }
        val field = read("LogSearchField.kt")
        for (fn in listOf("internal fun LogChoice(", "internal fun LogButton(")) assertTrue("heightIn(min = PcMin.DIALOG_TOUCH_DP.dp)" in body(field, fn), fn)
        assertTrue("selectable(selected = on, role = Role.Tab)" in body(field, "internal fun LogChoice("))
        assertTrue("LogTmFilter.entries.forEach { f -> LogChoice(f.label, f == filter) { onFilter(f) } }" in read("LogTms.kt"))
        assertTrue("LogChoice(f.label, on) { onFilter(f) }" in read("LogTrainers.kt"))
        assertTrue("tabs.forEach { t -> LogChoice(\"\${t.label} \${count(t)}\", t == tab) { tab = t } }" in read("LogRoutes.kt"))
        val misc = read("LogMisc.kt")
        assertTrue("heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).toggleable(value = on, role = Role.Checkbox)" in misc, "the options are 48dp checkboxes")
        assertTrue("LogButton(\"Share Seed\", Pc.Text, 12) { share = true }" in misc && "LogButton(\"CLOSE\", Pc.Text) { onClose() }" in misc)
    }
}
