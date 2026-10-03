package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kaizo's BST line on the tracker (Blake, 2026-10-02: a Zekrom of 679 from the Black 2 lab showed "no indication that
 * this is against the rules"). The lines and the record of what each Pokemon joined as run for real; the four cards
 * are held to the source.
 */
class BstRuleTest {
    private val dir = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(dir, name).readText().replace("\r\n", "\n")

    @Test
    fun `Kaizo and the modes on it draw the line at 599, Nat Dex at 600, Evo Kaizo's lab at 601`() {
        for (mode in listOf("kaizo", "survival", "superkaizo", "kaizodoubles", "survivalrevival")) {
            assertEquals(BstRule.Lines(599, 599), BstRule.lines(mode, natDex = false), mode)
            assertEquals(BstRule.Lines(600, 600), BstRule.lines(mode, natDex = true), mode)
        }
        assertEquals(BstRule.Lines(own = 601, wild = 599), BstRule.lines("evokaizo", natDex = false), "the lab allows up to 600")
        assertEquals(BstRule.Lines(own = 601, wild = 600), BstRule.lines("evokaizo", natDex = true))
        for (mode in listOf("standard", "ultimate", "chaoskaizo", "ironmonjourney", null))
            assertNull(BstRule.lines(mode, natDex = false), "$mode draws no line")
        // Every key a settings file can carry is one of these (RnqsInfo.RULESETS).
        val keys = read("RnqsInfo.kt").substringAfter("private val RULESETS = listOf(").substringBefore(")")
        assertEquals(10, Regex("\"([a-z]+)\"").findAll(keys).count())
    }

    @Test
    fun `the X stays on what joined past the line, never on what evolved past it`() {
        val kaizo = BstRule.lines("kaizo", natDex = false)
        val joined = JoinedForms(Files.createTempDirectory("joined").resolve("joined.txt").toFile(), attempt = 37)
        // Zekrom (644) from the lab: 679.
        assertTrue(BstRule.ownBreaks(679, kaizo, joined, pid = 0x1234ABCDL, species = 644))
        // Dratini (147) joined, then grew into Dragonite (149, 600): the record keeps Dratini.
        assertFalse(BstRule.ownBreaks(300, kaizo, joined, pid = 0x77L, species = 147))
        assertFalse(BstRule.ownBreaks(600, kaizo, joined, pid = 0x77L, species = 149))
        assertFalse(BstRule.ownBreaks(598, kaizo, joined, pid = 0x99L, species = 10), "under the line")
        assertFalse(BstRule.ownBreaks(null, kaizo, joined, pid = 0x98L, species = 11), "an unread BST")
        assertFalse(BstRule.ownBreaks(679, null, joined, pid = 0x1234ABCDL, species = 644), "a mode with no line")
        assertFalse(BstRule.ownBreaks(679, kaizo, null, pid = 0x1234ABCDL, species = 644), "no record, no X")
        assertTrue(BstRule.wildBreaks(680, kaizo), "a wild one would join as it is")
        assertFalse(BstRule.wildBreaks(580, kaizo))
        assertFalse(BstRule.wildBreaks(600, BstRule.lines("standard", natDex = false)))
        val evo = BstRule.lines("evokaizo", natDex = false)
        assertFalse(BstRule.ownBreaks(600, evo, joined, pid = 0x55L, species = 373), "a 600 from Evo Kaizo's lab is legal")
        assertTrue(BstRule.wildBreaks(600, evo), "a wild 600 there is not")
    }

    @Test
    fun `the player can say it evolved, for a run the app first read after the evolution`() {
        val file = Files.createTempDirectory("joined").resolve("joined.txt").toFile()
        val kaizo = BstRule.lines("kaizo", natDex = false)
        // A Garchomp (445, 600) the app first saw already evolved: recorded as Garchomp, so the X shows.
        val first = JoinedForms(file, attempt = 9)
        assertTrue(BstRule.ownBreaks(600, kaizo, first, pid = 0xABCL, species = 445))
        first.markEvolved(0xABCL)
        assertFalse(BstRule.ownBreaks(600, kaizo, first, pid = 0xABCL, species = 445), "gone at once")
        assertFalse(BstRule.ownBreaks(600, kaizo, JoinedForms(file, attempt = 9), pid = 0xABCL, species = 445), "and after a restart")
        assertTrue(BstRule.ownBreaks(600, kaizo, JoinedForms(file, attempt = 10), pid = 0xABCL, species = 445), "a new attempt keeps nothing")
        val sheet = read("BstRule.kt").substringAfter("internal fun BstRuleSheet(").substringBefore("\n}\n")
        assertTrue("\"This run allows no Pokemon at BST \$line or above, unless it evolved into that form.\"" in sheet, "the rule and nothing more")
        assertTrue("if (own) {" in sheet && "\"IT EVOLVED INTO THIS\"" in sheet, "only your own Pokemon offer it")
        val row = read("PcTracker.kt").substringAfter("fun PcStatRow(").substringBefore("\n}\n")
        assertTrue("if (broken && onBrokenTap != null) Modifier.clickable(role = Role.Button) { onBrokenTap() }" in row)
        for (panel in listOf("TrackerPanel.kt", "NdsTrackerPanel.kt")) {
            val src = read(panel)
            assertTrue("joinedForms?.markEvolved(it)" in src && "bstBroken = joinedVersion >= 0 && BstRule.ownBreaks(" in src, "$panel clears the X when told")
        }
    }

    @Test
    fun `the record survives a restart and starts over with a new attempt`() {
        val file = Files.createTempDirectory("joined").resolve("joined.txt").toFile()
        JoinedForms(file, attempt = 5).joinedAs(0x77L, 147)
        assertEquals(147, JoinedForms(file, attempt = 5).joinedAs(0x77L, 149), "read back after the app restarts")
        assertEquals(149, JoinedForms(file, attempt = 6).joinedAs(0x77L, 149), "a new attempt keeps nothing")
        assertEquals(listOf("attempt 6", "${0x77L} 149"), file.readLines())
        val again = JoinedForms(file, attempt = 6)
        assertEquals(0, again.joinedAs(0L, 0), "an empty slot is not recorded")
        assertEquals(2, file.readLines().size)
    }

    @Test
    fun `Red, Blue and Yellow ban over 480, and a Game Boy Pokemon is followed by trainer id and DVs`() {
        assertEquals(BstRule.Lines(481, 481), BstRule.lines("kaizo", natDex = false, gen1 = true), "Dragonite, the birds, Mew, Mewtwo")
        assertEquals(BstRule.Lines(599, 599), BstRule.lines("kaizo", natDex = false), "Gold, Silver and Crystal take Kaizo's")
        assertNull(BstRule.lines("standard", natDex = false, gen1 = true))
        fun gbMon(species: Int) = com.ironmonone.tracker.PokemonDecoder.Mon(
            // The Game Boy tracker's stand-in: species in the high half, trainer id in the low half (GbcTracker.partyMon).
            pid = (species.toLong() shl 16) or 0x1234L, level = 30, nickname = "", species = species,
            heldItem = 0, friendship = 70, moves = listOf(1, 2, 3, 4), pp = listOf(10, 10, 10, 10),
            ivs = listOf(9, 10, 11, 12, 13, 13), evs = List(6) { 0 }, ppUps = List(4) { 0 },
            abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = 50, maxHp = 50,
            atk = 40, def = 40, spe = 40, spAtk = 40, spDef = 40,
        )
        val dratini = gbMon(147); val dragonite = gbMon(149)
        assertTrue(dratini.pid != dragonite.pid, "the stand-in personality value changes with the species")
        assertEquals(BstRule.keyOf(dratini, 1), BstRule.keyOf(dragonite, 1), "the key does not")
        assertEquals(dragonite.pid, BstRule.keyOf(dragonite, 3), "Gen 3 and up keep the personality value")
        val joined = JoinedForms(Files.createTempDirectory("joined").resolve("joined.txt").toFile(), attempt = 3)
        val rby = BstRule.lines("kaizo", natDex = false, gen1 = true)
        assertFalse(BstRule.ownBreaks(300, rby, joined, BstRule.keyOf(dratini, 1), 147))
        assertFalse(BstRule.ownBreaks(500, rby, joined, BstRule.keyOf(dragonite, 1), 149), "Dratini that grew into Dragonite")
        assertTrue(BstRule.wildBreaks(485, rby), "a wild Articuno")
    }

    @Test
    fun `all four cards carry the X, on every console`() {
        val gba = read("TrackerPanel.kt")
        assertTrue("rightJustify = TrackerOptions.rightJustifiedNumbers, broken = bstBroken, onBrokenTap = onBstTap)" in gba, "your Pokemon")
        assertTrue("bstBroken = joinedVersion >= 0 && BstRule.ownBreaks(p.base?.bst, bstLines, joinedForms, BstRule.keyOf(p.mon, generation), p.mon.species, p.speciesName)," in gba)
        assertTrue("state.party.map { BstRule.keyOf(it.mon, generation) to it.mon.species }" in gba, "a Game Boy party by trainer id and DVs")
        assertTrue("broken = isWild && !e.isGhost && BstRule.wildBreaks(e.base?.bst, bstLines), onBrokenTap = onBstTap)" in gba, "a wild opponent, never a trainer's")
        val ds = read("NdsTrackerPanel.kt")
        assertTrue("PcStatRow(\"BST\", p.info?.bst?.toString() ?: \"?\", broken = bstBroken, onBrokenTap = onBstTap)" in ds)
        assertTrue("PcStatRow(\"BST\", e.info?.bst?.toString() ?: \"?\", broken = bstBroken, onBrokenTap = onBstTap)" in ds)
        assertTrue("bstBroken = joinedVersion >= 0 && BstRule.ownBreaks(p.info?.bst, bstLines, joinedForms, p.mon.pid, p.mon.species)," in ds)
        assertTrue("bstBroken = state.isWildBattle && BstRule.wildBreaks(it.info?.bst, bstLines)," in ds)
        for (src in listOf(gba, ds))
            assertTrue("party.forEach { (pid, sp) -> joinedForms.joinedAs(pid, sp) }" in src, "the whole party is recorded")
        val row = read("PcTracker.kt").substringAfter("fun PcStatRow(").substringBefore("\n}\n")
        assertTrue("if (broken) BstCross()" in row && "if (broken) Pc.Negative else" in row, "the X after the label, the number in red")
        assertTrue("RuleCross(\"Over this run's BST limit\")" in read("PcTracker.kt"), "a screen reader hears it")
        assertTrue("gen1 = session.kind?.generation == Generation.GB1" in read("BstRule.kt"), "Red, Blue and Yellow take their own line")
    }
}
