package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The game over lines as they are wired into the game over box, the tracker's cards, the gear and start-up
 * (2026-09-30). A Compose screen cannot run on the JVM, so this reads the source, for exactly the lines a phone
 * would otherwise be needed to see. What the lines do, and how they are kept, is run for real in DeathQuotesTest.
 */
class DeathQuotesWiringTest {
    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(src, name).readText().replace("\r\n", "\n")

    /** [text] with its comments dropped, so a note that names something is not counted as using it. */
    private fun code(text: String) = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\\n]*"), "")

    @Test
    fun `the game over box takes its line from DeathQuotes, one per run, and a tap on the team draws another`() {
        val box = code(read("GameOverDialog.kt"))
        assertTrue("mutableStateOf(DeathQuotes.shown(quoteSource, attempt, builtIn, forLoss = !won))" in box, "the line is drawn once per run")
        assertTrue("quote = DeathQuotes.reroll(quoteSource, attempt, builtIn, forLoss = !won)" in box, "the team icon draws another")
        assertFalse("quoteIndex" in box || "quotes[" in box || "PcGameOverQuotes[" in box, "no pick by attempt number is left")
        // The built-in lines are still the trackers' own: the Game Boy and Game Boy Advance list, the DS causes' lists.
        assertTrue("else -> PcGameOverQuotes" in box && "GameOverFamily.DS -> ndsRunOverLines(" in box)
        assertTrue("DeathQuotes.dsSource(" in box && "DeathQuotes.PC_SOURCE" in box)
        // A win keeps its own message: CONGRATULATIONS on the Game Boy families, the DS wins' list on DS.
        assertTrue("if (won && family != GameOverFamily.DS) \"CONGRATULATIONS!!\" else quote" in box)
    }

    @Test
    fun `the tracker's own cards say the line the box says, from the same source and the same list`() {
        assertTrue("else DeathQuotes.shown(DeathQuotes.PC_SOURCE, attempt, PcGameOverQuotes)" in code(read("PcTracker.kt")))
        val nds = code(read("NdsTrackerPanel.kt"))
        assertTrue("DeathQuotes.shown(DeathQuotes.dsSource(cause), attempt, lines, forLoss = !won)" in nds)
        // The card's list is the box's: ndsRunOverLines and the card both read NDS_RUN_OVER_MESSAGES[cause], else the standard pool.
        assertEquals(2, Regex("NDS_RUN_OVER_MESSAGES\\[cause\\]").findAll(nds).count())
        assertEquals(2, Regex("NDS_RUN_OVER_MESSAGES\\.getValue\\(com\\.ironmonone\\.tracker\\.nds\\.NdsRunOver\\.STANDARD\\)").findAll(nds).count())
        // Nothing else picks a game over line by attempt number.
        for (f in src.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            assertFalse("PcGameOverQuotes[" in code(f.readText()), "${f.name} picks a Game Boy line by hand")
        }
    }

    @Test
    fun `the gear opens the screen from its own state, and PlayScreen carries nothing for it`() {
        val gear = code(read("TrackerGearDialog.kt"))
        assertTrue("var linesOpen by remember { mutableStateOf(false) }" in gear)
        assertTrue("GearButton(DeathQuotesCopy.TITLE) { linesOpen = true }" in gear, "a button among the tracker's screens")
        assertTrue("if (linesOpen) DeathQuotesDialog { linesOpen = false }" in gear)
        // The button is not gated on a game: Gen 3, Game Boy and DS all use the box.
        val line = gear.lines().single { "DeathQuotesCopy.TITLE" in it }
        assertFalse("gameBoy" in line || "ds" in line.substringAfter("DeathQuotesCopy.TITLE"), line)
        // PlayScreen is at ART's verifier limit (playscreen-verifier-limit): not a byte for this.
        assertFalse("DeathQuotes" in read("PlayScreen.kt"))
    }

    @Test
    fun `the lines are read once at start-up, from the file Backup carries`() {
        assertTrue("DeathQuotes.load(java.io.File(filesDir, DeathQuotes.FILE))" in read("MainActivity.kt"))
        assertEquals("prep/death-quotes.txt", DeathQuotes.FILE)
        assertTrue(Backup.admits(DeathQuotes.FILE))
    }

    @Test
    fun `the screen has no wording of its own, so the copy rules cover everything the player reads`() {
        // An empty string is the text box being cleared, not a word the player reads.
        val literals = Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(code(read("DeathQuotesDialog.kt"))).map { it.value }.filter { it != "\"\"" }.toList()
        assertTrue(literals.isEmpty(), "DeathQuotesDialog.kt says things DeathQuotesCopy does not: $literals")
        val dialog = read("DeathQuotesDialog.kt")
        assertTrue("DeathQuotes.add(draft)" in dialog && "DeathQuotes.replace(at, draft)" in dialog && "DeathQuotes.remove(i)" in dialog, "add, edit and remove")
        assertTrue("DeathQuotes.useOwn(it)" in dialog && "DeathQuotes.useBuiltIn(it)" in dialog, "the two switches")
    }

    @Test
    fun `the three files carry no em dash and never say how the work is made`() {
        for (name in listOf("DeathQuotes.kt", "DeathQuotesDialog.kt", "GameOverDialog.kt")) {
            val text = read(name)
            assertFalse(text.contains(0x2014.toChar()), "$name has an em dash")
            assertFalse(Regex("\\bAI\\b").containsMatchIn(text), "$name mentions AI")
        }
    }
}
