package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.LossCondition
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every Kaizo run follows the rules of its own game and its own mode (Blake, 2026-10-01: "make sure all kaizo runs have
 * the right rules, and base it on whatever game is doing a run because different kaizo's have different rules, and
 * different kaizo modes have different rules"). What the rules check of that day found, held here.
 */
class RunRulesByModeTest {
    private val books = File("src/main/assets/rulesets")

    private fun prep(): File = File(Files.createTempDirectory("runmode").toFile(), "prep").apply { File(this, "settings").mkdirs() }
    private fun preset(prep: File, name: String, meta: String? = null) {
        File(prep, "settings/$name").writeBytes(byteArrayOf(1, 2, 3))
        meta?.let { File(prep, "settings/$name.meta").writeText(it) }
    }
    private fun loss(p: File, kind: RomKind, name: String) = LossCondition.forSettingsName(RunModeName.ofPrep(p, kind.id, name))

    @Test
    fun `a run's mode is read from its file and its sidecar, on its own game`() {
        val p = prep()
        // Saved from the editor under a plain name: the mode is only in the sidecar.
        preset(p, "My run.rnqs", "ruleset=survival\nfamily=RBY\n")
        assertEquals("RBY Survival", RunModeName.ofPrep(p, RomKind.YELLOW_U.id, "My run.rnqs"))
        assertEquals(LossCondition.HIGHEST_LEVEL, loss(p, RomKind.YELLOW_U, "My run.rnqs"))
        assertEquals(PcHeals.Limit.SURVIVAL, PcHeals.limitFor(RunModeName.ofPrep(p, RomKind.YELLOW_U.id, "My run.rnqs")))
        // Gen 1 Survival's own rule on a Yellow run, with no "RBY" in the file's name; the lead on another game.
        preset(p, "Survival.rnqs")
        assertEquals(LossCondition.HIGHEST_LEVEL, loss(p, RomKind.YELLOW_U, "Survival.rnqs"))
        assertEquals(LossCondition.LEAD, loss(p, RomKind.EMERALD_U, "Survival.rnqs"))
        // Standard ends with the whole party, Kaizo Doubles with either of the first two, from the sidecar alone.
        preset(p, "Saved.rnqs", "ruleset=standard\n")
        preset(p, "Pair.rnqs", "ruleset=kaizodoubles\n")
        assertEquals(LossCondition.ENTIRE_PARTY, loss(p, RomKind.EMERALD_U, "Saved.rnqs"))
        assertEquals(LossCondition.EITHER_OF_FIRST_TWO, loss(p, RomKind.EMERALD_U, "Pair.rnqs"))
        preset(p, "Revival.rnqs", "ruleset=survivalrevival\n")
        assertEquals(PcHeals.Limit.REVIVAL, PcHeals.limitFor(RunModeName.ofPrep(p, RomKind.EMERALD_U.id, "Revival.rnqs")))
        // Nothing to read: the name as it was.
        preset(p, "Mystery.rnqs")
        assertEquals("Mystery.rnqs", RunModeName.ofPrep(p, RomKind.EMERALD_U.id, "Mystery.rnqs"))
    }

    @Test
    fun `Tracker Setup's defaults and the ball picker follow the mode the sidecar holds`() {
        val saved = TrackerOptions.text()
        val p = prep()
        val f = File(p, "tracker-options.txt")
        try {
            TrackerOptions.load(f)
            TrackerOptions.showBallPicker = true
            preset(p, "My journey.rnqs", "ruleset=ironmonjourney\n")
            File(p, "lastrun.txt").writeText("emerald-u\nMy journey.rnqs\n")
            assertTrue(TrackerOptions.journeyRun())
            assertFalse(TrackerOptions.ballPickerShows(nuzlocke = false), "Journey lets you choose any starter")
            preset(p, "My survival.rnqs", "ruleset=survival\n")
            File(p, "lastrun.txt").writeText("yellow-u\nMy survival.rnqs\n")
            File(p, "lastrun.txt").setLastModified(System.currentTimeMillis() + 5000)
            assertFalse(TrackerOptions.journeyRun())
            assertEquals(LossCondition.HIGHEST_LEVEL, TrackerOptions.lossConditionFor("My survival.rnqs"))
        } finally {
            f.writeText(saved); TrackerOptions.load(f)
        }
    }

    @Test
    fun `the Survival line says what each game's own book says`() {
        val bw = RulesetCatalog.modeLine("survival", family = "BW")
        assertTrue("the trainer after N" in bw, bw)
        assertTrue("Survival PC Heals start counting only after you beat the trainer after N." in File(books, "BW/survival.md").readText())
        for (family in listOf("GSC", "HGSS")) {
            val line = RulesetCatalog.modeLine("survival", family = family)
            assertTrue("seven more for Kanto" in line, "$family: $line")
            assertTrue("you earn an additional 7 heals for Kanto" in File(books, "$family/survival.md").readText(), family)
        }
        assertEquals(RulesetCatalog.modeLine("survival"), RulesetCatalog.modeLine("survival", family = "FRLG"))
        for (family in listOf("BW", "GSC", "HGSS")) {
            val line = RulesetCatalog.modeLine("survival", family = family)
            assertTrue(line.length <= 160 && line.endsWith("."), line)
            assertFalse(Char(0x2014) in line || Char(0x2013) in line, line)
        }
    }

    @Test
    fun `the rules box shows the modes a game can run, in the Mode row's order`() {
        fun folder(name: String) = File(books, name).list()!!.map { it.removeSuffix(".md") }
        assertTrue("superkaizo" in Rules.shown(folder("RSE"), RomKind.EMERALD_U))
        assertFalse("superkaizo" in Rules.shown(folder("RSE"), RomKind.RUBY_U))
        assertFalse("superkaizo" in Rules.shown(folder("DPPt"), RomKind.DIAMOND_U))
        assertTrue("superkaizo" in Rules.shown(folder("DPPt"), RomKind.PLATINUM_U))
        assertEquals(RulesetCatalog.keys, Rules.order(RulesetCatalog.keys.reversed()))
    }

    @Test
    fun `Red, Blue and Yellow ban their six over 480 BST as favorites too`() {
        val rby = File(books, "RBY/kaizo.md").readText()
        assertEquals(setOf("dragonite", "articuno", "zapdos", "moltres", "mew", "mewtwo"), FavoriteRules.bannedByName(rby))
        assertEquals(FavoriteRules.bannedByName(rby), FavoriteRules.bannedByName(File(books, "RBY/survival.md").readText()))
        assertEquals(1, FavoriteRules.problems(listOf("Dragonite", "Gyarados"), "kaizo", rby).size)
        assertEquals(1, FavoriteRules.problems(listOf("Mewtwo"), "kaizo", rby).size, "banned, not just a legendary")
        assertTrue(FavoriteRules.lines(rby).any { "480 BST" in it })
        assertTrue(FavoriteRules.problems(listOf("Dragonite"), "kaizo", File(books, "FRLG/kaizo.md").readText()).isEmpty())
    }

    @Test
    fun `Play keeps a library game's pick off the last run, and Journey has no ball call`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        // Tracker Setup reads the run's settings name for itself now (FileBar.kt's TrackerGearDialog(links)).
        assertTrue("runCatching { PrepStore(filesDir).let { s -> if (s.session().isRun) s.loadLastRun()?.second else null } }" in
            File("src/main/kotlin/com/ironmonone/app/FileBar.kt").readText())
        assertTrue("""if (TrackerOptions.ballPickerShows()) " New ball call incoming." else "")""" in play)
        assertTrue("kind = session.kind, onDismiss = { rulesDialog = false }" in play)
        val gear = File("src/main/kotlin/com/ironmonone/app/TrackerGearDialog.kt").readText()
        assertTrue("} else if (scope.ironmon && TrackerOptions.journeyRun()) {" in gear)
    }
}
