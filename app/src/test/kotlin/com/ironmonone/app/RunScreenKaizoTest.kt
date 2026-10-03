package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Kaizo IronMON screen after the UX audit of 2026-09-30 (P0-12, P0-13, P1, P2): it opens on Kaizo and brings
 * a game's own last mode back, the mode row is in the order the rules build, each mode has one plain line written
 * from its rules file, the empty game list has the button it names, and the buttons name the attempt they start.
 *
 * A Compose screen cannot be run on the JVM, so what the screen decides lives in plain objects (RulesetCatalog,
 * RunCopy, RunPairing, RunGames, RunModeMemory) and is driven here, and the screen's wiring to them is read from
 * its source, the way BackToRunTest and HomeWiringTest read theirs.
 */
class RunScreenKaizoTest {
    private val assets = File("src/main/assets")
    private val rulesets = File(assets, "rulesets")
    private val presets = File(assets, "presets").listFiles { f -> f.extension == "rnqs" }!!.sortedBy { it.name }
    private val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText().replace("\r\n", "\n")
    private val shell = File("src/main/kotlin/com/ironmonone/app/ShellComponents.kt").readText().replace("\r\n", "\n")

    private fun named(vararg names: String): List<File> = names.map { File("/presets/$it") }
    private fun tempDir(): File = Files.createTempDirectory("runkaizo").toFile()

    /** The copy rules for anything a player reads here: plain ASCII and the accented e, so no dash, quote or emoji, and no AI. */
    private fun assertCopyRules(text: String, where: String) {
        assertTrue(text.all { it.code < 0x80 || it == 'é' }, "$where has a character that is not plain: $text")
        assertFalse(Regex("\\bAI\\b").containsMatchIn(text), "$where: $text")
        for (bad in listOf("Pokemon", "Pokeball", "Poke Ball", "colour"))
            assertFalse(bad in text, "$where says \"$bad\": $text")
    }

    // ------------------------------------------------------------- the order the rules build

    @Test
    fun `the mode row is in the order the rules build`() {
        assertEquals(
            listOf("standard", "ultimate", "kaizo", "superkaizo", "survival", "survivalrevival",
                "kaizodoubles", "chaoskaizo", "evokaizo", "ironmonjourney"),
            RulesetCatalog.keys)
        // Nat. Dex Emerald has every mode there is, and Emerald has eight of them.
        assertEquals(RulesetCatalog.keys, RulesetCatalog.forRom(RomKind.EMERALD_NATDEX_121, presets).map { it.key })
        assertEquals(
            listOf("standard", "ultimate", "kaizo", "superkaizo", "survival", "kaizodoubles", "chaoskaizo", "ironmonjourney"),
            RulesetCatalog.forRom(RomKind.EMERALD_U, presets).map { it.key })
        // Whatever a game offers: Standard, Ultimate, Kaizo, then Super Kaizo where it has it, then the variants.
        // MaxDex offers its one mode, Kaizo, as Trip ships it.
        assertEquals(listOf("kaizo"), RulesetCatalog.forRom(RomKind.FIRERED_MAXDEX_10, presets).map { it.key })
        for (k in RomKind.all.filterNot { it.isMaxDex }) {
            val keys = RulesetCatalog.forRom(k, presets).map { it.key }
            assertTrue(keys.take(3) == listOf("standard", "ultimate", "kaizo"), "${k.id}: $keys")
            if ("superkaizo" in keys) assertEquals("superkaizo", keys[3], "${k.id}: Super Kaizo follows Kaizo")
        }
        assertEquals(RulesetCatalog.keys.size, RulesetCatalog.keys.map { RulesetCatalog.rank(it) }.toSet().size)
        assertEquals(RulesetCatalog.keys.size, RulesetCatalog.rank("mymode"), "an unlisted mode goes last")
    }

    @Test
    fun `the in-run rules box lists the first four the same way`() {
        val four = listOf("standard", "ultimate", "kaizo", "superkaizo")
        assertEquals(four, RulesetCatalog.keys.take(4))
        assertEquals(four, Rules.order(RulesetCatalog.keys.reversed()).take(4))
    }

    // -------------------------------------------------------------------------- Kaizo opens

    @Test
    fun `every game opens on Kaizo, and a Nat Dex game on its own Nat Dex Kaizo file`() {
        for (k in RomKind.all) {
            val f = RulesetCatalog.openingFile(k, presets, null, null)
            assertTrue(f != null, "${k.id} opens on nothing")
            val info = RnqsInfo.of(f!!)
            assertEquals("kaizo", info.ruleset, "${k.id}: ${f.name}")
            assertEquals(k.family, info.gameTag, "${k.id}: ${f.name}")
            assertEquals(k.isNatDex, info.natDex, "${k.id}: ${f.name}")
        }
        assertEquals("RSE Kaizo.rnqs", RulesetCatalog.openingFile(RomKind.EMERALD_U, presets, null, null)!!.name)
        assertEquals("RSE NatDex v1.2 Kaizo.rnqs", RulesetCatalog.openingFile(RomKind.EMERALD_NATDEX_121, presets, null, null)!!.name)
        assertEquals("FRLG NatDex v1.2 Kaizo.rnqs", RulesetCatalog.openingFile(RomKind.FIRERED_NATDEX_121, presets, null, null)!!.name)
        assertEquals("FRLG Kaizo.rnqs", RulesetCatalog.openingFile(RomKind.FIRERED_U_V11, presets, null, null)!!.name)
    }

    @Test
    fun `a game opens on the file it last had picked, then the run last started on it, then Kaizo`() {
        val emerald = RomKind.EMERALD_U
        fun opens(remembered: String?, lastRun: String?) = RulesetCatalog.openingFile(emerald, presets, remembered, lastRun)!!.name
        assertEquals("RSE Kaizo.rnqs", opens(null, null))
        assertEquals("RSE Survival.rnqs", opens("RSE Survival.rnqs", null))
        assertEquals("RSE Survival.rnqs", opens(null, "RSE Survival.rnqs"), "a phone with runs from before the pick was kept")
        assertEquals("RSE Ultimate.rnqs", opens("RSE Ultimate.rnqs", "RSE Survival.rnqs"), "what was picked beats the run")
        // A file that is gone, that is another game's, or that is the Nat. Dex file of this game is skipped, never kept.
        assertEquals("RSE Kaizo.rnqs", opens("RSE Gone.rnqs", null))
        assertEquals("RSE Kaizo.rnqs", opens("FRLG Survival.rnqs", null))
        assertEquals("RSE Kaizo.rnqs", opens("RSE NatDex v1.2 Survival.rnqs", null))
        assertEquals("RSE Survival.rnqs", opens("RSE Gone.rnqs", "RSE Survival.rnqs"), "the next one down")
        assertEquals("RSE NatDex v1.2 Survival.rnqs",
            RulesetCatalog.openingFile(RomKind.EMERALD_NATDEX_121, presets, "RSE NatDex v1.2 Survival.rnqs", null)!!.name)
        // An edited copy the player picked stays, and is not swapped for the plain file of its mode.
        val withEdit = named("RSE Kaizo.rnqs", "RSE Kaizo (edited).rnqs", "RSE Standard.rnqs")
        assertEquals("RSE Kaizo (edited).rnqs", RulesetCatalog.openingFile(emerald, withEdit, "RSE Kaizo (edited).rnqs", null)!!.name)
        assertEquals("RSE Kaizo.rnqs", RulesetCatalog.openingFile(emerald, withEdit, null, null)!!.name)
    }

    @Test
    fun `a game with no Kaizo mode opens on the first mode in the row, and one with no files on nothing`() {
        val only = named("RSE Ultimate.rnqs", "RSE Standard.rnqs")
        assertEquals("RSE Standard.rnqs", RulesetCatalog.openingFile(RomKind.EMERALD_U, only, null, null)!!.name)
        assertNull(RulesetCatalog.openingFile(RomKind.EMERALD_U, emptyList(), null, null))
        assertNull(RulesetCatalog.openingFile(RomKind.EMERALD_U, named("FRLG Kaizo.rnqs"), null, null), "another game's files are no modes")
    }

    // ------------------------------------------------------------------ one line for each mode

    @Test
    fun `each mode has one plain line and the words for a mode nobody knows are the fallback`() {
        assertEquals(10, RulesetCatalog.keys.size)
        for (key in RulesetCatalog.keys) for (natDex in listOf(false, true)) {
            val line = RulesetCatalog.modeLine(key, natDex)
            assertTrue(line.isNotBlank() && '\n' !in line && line.endsWith("."), "$key: $line")
            assertTrue(line.length <= 160, "$key is ${line.length} characters: a line, not a paragraph")
            assertNotEquals(RulesetCatalog.OTHER_LINE, line, "$key has a line of its own")
            assertCopyRules(line, key)
        }
        assertEquals(10, RulesetCatalog.keys.map { RulesetCatalog.modeLine(it) }.toSet().size, "no two modes share a line")
        assertEquals("A variant of Kaizo. Read its rules first.", RulesetCatalog.modeLine("mymode"))
        assertCopyRules(RulesetCatalog.OTHER_LINE, "the fallback")
        assertCopyRules(RulesetCatalog.MODE_HEADER, "the line under Mode")
    }

    /** One thing a line says, and the sentence in the rules file that says it. */
    private class Claim(val key: String, val line: String, val rule: String)

    private val claims = listOf(
        Claim("standard", "Catch or kill one Pokémon per route", "Catch or Kill 1 per Route: You can only catch OR kill one Pokémon per Route"),
        Claim("standard", "gone for good", "Permadeath: Once a Pokémon faints, you must release/store it"),
        Claim("standard", "No shops, except for Poké Balls and Repels",
            "No Shops: You can only use items that you pick up or are given by an NPC, no use of stores except for any type of Pokeball or Repel"),
        Claim("ultimate", "no HM moves in battle", "No HM Moves: No HM Moves in Battle."),
        Claim("ultimate", "no leaving a gym until you beat every trainer in it",
            "No Way Out Gyms: Once You Enter a Gym or Dojo, You cannot leave until you defeat ALL TRAINERS."),
        Claim("ultimate", "one visit per dungeon", "One Shot Dungeons: You can only enter a dungeon once unless the story requires a revisit"),
        Claim("ultimate", "six Pokémon in all", "6 Pokémon Total: You may only obtain 6 Pokémon for the Entire Run"),
        Claim("kaizo", "Standard and Ultimate, plus",
            "Kaizo Ruleset: This ruleset includes all of the previous rules from standard & ultimate ironmon above"),
        Claim("kaizo", "one Pokémon at a time", "One Pokémon at a time: You may only use one Pokémon at all times."),
        Claim("kaizo", "no fighting wild Pokémon",
            "No Killing Wild Pokémon: The Catch or Kill rule is now Catch Only. No fighting any wild Pokémon"),
        Claim("kaizo", "no healing items outside of battle", "No Healing Items outside of Battle: No HP Healing Items while outside of battle"),
        Claim("kaizo", "Built to be very hard", "These additional rules are meant to be insanely stupid and hard."),
        Claim("superkaizo", "Kaizo, plus", "0. ALL PREVIOUS Standard, Ultimate, and Kaizo rules"),
        Claim("superkaizo", "trainers that pick their moves more cleverly", "1. Every Trainer has SMART AI, this requires a ROM patch"),
        Claim("superkaizo", "gym leaders with six Pokémon", "3. Every Gym Leader now has 6 Pokemon"),
        Claim("superkaizo", "a switch to a new Pokémon about halfway through",
            "4. You must pivot to a NEW Pokémon about midway through the game, before beating the game"),
        Claim("survival", "Harder than Kaizo", "The goal of this ruleset is to be even more difficult than Kaizo"),
        Claim("survival", "Ten Pokémon Center heals",
            "10 Heal Limit: You may only recover at a PokeCenter (or equivalent NPC) 10 times throughout the main run."),
        // IronMON rules check R15 (2026-09-30): the count starts after the first trainer that is not the rival.
        Claim("survival", "counted from your first trainer battle after the rival",
            "This rule goes into effect after the first trainer battle (not counting the initial rival fight), before then you may heal freely."),
        Claim("survival", "an eleventh after your eighth badge", "Once you earn your 8th badge you earn a bonus 11th heal."),
        Claim("survivalrevival", "Kaizo with", "ALL PREVIOUS Standard, Ultimate, and Kaizo rules"),
        Claim("survivalrevival", "five Pokémon Center heals after your first badge",
            "After receiving your first Gym badge you MUST immediately use the Pokémon Center. You may now only use the Pokémon Center up to 5 times for the remainder of the run."),
        Claim("survivalrevival", "one more after your eighth",
            "After Acquiring your 8th Gym badge, you gain one Bonus Pokémon Center usage."),
        Claim("kaizodoubles", "every trainer battle is a double battle", "All trainer battles possible are doubles battles (2v2)."),
        Claim("kaizodoubles", "The run ends if either of your two Pokémon faints",
            "if either of your two Pokémon faint it's game over and the run ends."),
        Claim("chaoskaizo", "random Pokémon types", "Pokémon Types: Random (follow evolutions)"),
        Claim("chaoskaizo", "random move power, accuracy and PP", "Move Data: Randomize move: Power, Accuracy, and PP"),
        Claim("chaoskaizo", "looser item rules",
            "Held Items Restriction: All items are allowed to be held, except for the ones listed below under Banned Items"),
        Claim("chaoskaizo", "marked as a work in progress", "This rule set is a work-in-progress."),
        Claim("evokaizo", "Kaizo, but", "All Kaizo Ironmon Rules are still in play unless otherwise specified."),
        Claim("evokaizo", "evolves on every level up and you cannot cancel it", "Your pokemon evolves on every level up. You cannot cancel an evo."),
        Claim("ironmonjourney", "Kaizo with", "Journey is based on the original Ironmon ruleset Kaizo difficulty"),
        Claim("ironmonjourney", "any starter you like is your whole team",
            "You can choose any of the 3 starters, but it will be your only Pokemon. No swapping or pivoting allowed (except for Rule #5)"),
        // IronMON rules check R16 (2026-09-30): the pivot may be used again while the pivot is not fully evolved.
        Claim("ironmonjourney", "apart from emergency swaps", "you may apply the Emergency Pivot again when it evolves to its final form"),
        Claim("ironmonjourney", "No fighting wild Pokémon", "You cannot intentionally engage in battle with wild Pokémon"),
        Claim("ironmonjourney", "every TM can be learned", "100% chance to learn any TM."),
    )

    @Test
    fun `every line says only what the rules file of that mode says, in every game that has the mode`() {
        assertEquals(RulesetCatalog.keys.toSet(), claims.map { it.key }.toSet(), "a mode with a line and no claim checked against its file")
        for (key in RulesetCatalog.keys) {
            val files = rulesets.listFiles()!!.map { File(it, "$key.md") }.filter { it.isFile }
            assertTrue(files.isNotEmpty(), "$key has no rules file")
            val line = RulesetCatalog.modeLine(key)
            for (c in claims.filter { it.key == key }) {
                assertTrue(c.line in line, "${c.key}: the line no longer says \"${c.line}\": $line")
                for (f in files) assertTrue(c.rule in f.readText(), "${f.parentFile.name}/${f.name} no longer says \"${c.rule}\"")
            }
        }
    }

    @Test
    fun `Ultimate says something else for a Nat Dex game, because its rules do`() {
        val plain = RulesetCatalog.modeLine("ultimate")
        val natDex = RulesetCatalog.modeLine("ultimate", natDex = true)
        assertNotEquals(plain, natDex)
        assertTrue("no HM moves in battle" in plain)
        assertTrue("no moves taught by HM items in battle" in natDex && "HM moves" !in natDex)
        val note = "Ultimate and harder: HM moves are allowed to be used in battle, as long as the moves are not taught with the HM items."
        val dirs = rulesets.listFiles()!!.filter { File(it, "ultimate.md").isFile }
        assertTrue(dirs.count { it.name.endsWith("-NatDex") } == 2 && dirs.size >= 10)
        for (d in dirs) assertEquals(d.name.endsWith("-NatDex"), note in File(d, "ultimate.md").readText(), d.name)
        // The other lines are the same for either kind of game.
        for (key in RulesetCatalog.keys - "ultimate") assertEquals(RulesetCatalog.modeLine(key), RulesetCatalog.modeLine(key, natDex = true), key)
    }

    @Test
    fun `the line under Mode is what the rules files hold`() {
        assertEquals("Standard is the base. Ultimate adds rules to it and Kaizo adds more. Every other mode starts from Kaizo.", RulesetCatalog.MODE_HEADER)
        var seen = 0
        for (dir in rulesets.listFiles()!!) for (f in dir.listFiles()!!.filter { it.extension == "md" }) {
            val heads = f.readLines().filter { it.startsWith("## ") }.map { it.removePrefix("## ") }
            val where = "${dir.name}/${f.name}"
            when (f.nameWithoutExtension) {
                "standard" -> assertEquals("Standard IronMON", heads.first(), where)
                "ultimate" -> assertEquals(listOf("Standard IronMON", "Ultimate IronMON"), heads.take(2), where)
                // Kaizo, and every mode after it, holds Standard, Ultimate and Kaizo, in that order, before its own.
                else -> assertEquals(listOf("Standard IronMON", "Ultimate IronMON", "Kaizo IronMON"), heads.take(3), where)
            }
            // Only Super Kaizo holds Super Kaizo: Survival and the rest start from Kaizo, not from it.
            assertEquals(f.nameWithoutExtension == "superkaizo", "Super Kaizo IronMON" in heads, where)
            seen++
        }
        assertTrue(seen >= 71, "$seen rules files")
    }

    // ----------------------------------------------------- a game's mode is kept for that game

    @Test
    fun `a game's last mode is kept for that game and no other, on disk`() {
        val dir = tempDir()
        val memory = RunModeMemory.of(dir)
        val emerald = RomKind.EMERALD_U.id
        val fireRed = RomKind.FIRERED_U_V11.id
        assertNull(memory.get(emerald))
        assertTrue(memory.set(emerald, "RSE Survival.rnqs"))
        assertTrue(memory.set(fireRed, "FRLG Ultimate.rnqs"))
        assertEquals("RSE Survival.rnqs", memory.get(emerald))
        assertEquals("FRLG Ultimate.rnqs", memory.get(fireRed))
        assertNull(memory.get(RomKind.CRYSTAL_U.id))
        // A new object reads what the last one wrote: it is on disk, not in the object.
        assertEquals("RSE Survival.rnqs", RunModeMemory.of(dir).get(emerald))
        // A later pick replaces the earlier one for its game and leaves the others alone.
        assertTrue(memory.set(emerald, "RSE Kaizo (edited).rnqs"))
        assertEquals("RSE Kaizo (edited).rnqs", RunModeMemory.of(dir).get(emerald))
        assertEquals("FRLG Ultimate.rnqs", RunModeMemory.of(dir).get(fireRed))
        assertTrue(memory.set(emerald, "RSE Kaizo (edited).rnqs"), "the same pick again is fine")
    }

    @Test
    fun `the memory skips a line it cannot read and refuses a pick it cannot keep`() {
        val dir = tempDir()
        File(dir, RunModeMemory.FILE).writeText(
            "emerald\tRSE Survival.rnqs\nno tab here\n\tRSE Kaizo.rnqs\nhalf\t\nfirered\tFRLG Kaizo.rnqs\ntoo\tmany\ttabs\n")
        val memory = RunModeMemory.of(dir)
        assertEquals("RSE Survival.rnqs", memory.get("emerald"))
        assertEquals("FRLG Kaizo.rnqs", memory.get("firered"))
        for (bad in listOf("half", "too", "no tab here", "")) assertNull(memory.get(bad), bad)
        assertFalse(memory.set("emerald", "a\tb"))
        assertFalse(memory.set("emerald", "a\nb"))
        assertFalse(memory.set("emerald", " "))
        assertFalse(memory.set("", "RSE Kaizo.rnqs"))
        assertFalse(memory.set("emer\tald", "RSE Kaizo.rnqs"))
        assertEquals("RSE Survival.rnqs", memory.get("emerald"), "a pick that was refused leaves the old one")
        assertEquals("FRLG Kaizo.rnqs", memory.get("firered"))
        assertNull(RunModeMemory.of(File(dir, "nowhere")).get("emerald"), "no file, nothing remembered")
    }

    @Test
    fun `the memory is a file directly in the files folder, not under prep, and not in the backup`() {
        val dir = tempDir()
        assertTrue(RunModeMemory.of(dir).set(RomKind.EMERALD_U.id, "RSE Kaizo.rnqs"))
        val f = File(dir, RunModeMemory.FILE)
        assertTrue(f.isFile)
        assertEquals(dir.canonicalFile, f.parentFile.canonicalFile)
        assertFalse(f.relativeTo(dir).path.replace('\\', '/').startsWith("prep/"), "BackupCoverageTest reads what is kept under prep/")
        assertFalse(Backup.admits(RunModeMemory.FILE), "a convenience: a restored phone opens each game on Kaizo again")
    }

    // ---------------------------------------------------------------------- the words

    @Test
    fun `the buttons name the attempt they start, and the run in play is continued`() {
        assertEquals("Start attempt 1", RunCopy.start(1))
        assertEquals("Start attempt 15", RunCopy.start(15))
        assertEquals("Continue attempt 14", RunCopy.continueRun(14))
        assertEquals("Cancel", RunCopy.CANCEL)
        // The number is the one PrepStore gives the new run: what is counted now, plus one.
        val store = PrepStore(tempDir())
        val id = RomKind.EMERALD_U.id
        assertEquals(0, store.attempt(id))
        assertEquals(store.attempt(id) + 1, store.bumpAttempt(id), "the first run is attempt 1")
        repeat(13) { store.bumpAttempt(id) }
        assertEquals(14, store.attempt(id))
        assertEquals(15, store.bumpAttempt(id), "the run started after attempt 14 is attempt 15")
        for (s in listOf(RunCopy.start(1), RunCopy.start(15), RunCopy.continueRun(14), RunCopy.READ_THE_RULES, RunCopy.ADD_A_GAME, RunCopy.CANCEL))
            assertCopyRules(s, s)
    }

    @Test
    fun `the question names both attempts, the game and the mode`() {
        val emerald = "Pokémon Emerald (U)"
        val fireRed = "Pokémon FireRed (U) v1.1"
        assertEquals("End attempt 14 and start attempt 15 on $emerald, Kaizo?",
            RunCopy.confirmNewAttempt(RunCopy.EndingRun(14, emerald), 15, emerald, "Kaizo"))
        // A run of another game is the one that ends, so both are named.
        assertEquals("End attempt 14 on $emerald and start attempt 1 on $fireRed, Super Kaizo?",
            RunCopy.confirmNewAttempt(RunCopy.EndingRun(14, emerald), 1, fireRed, "Super Kaizo"))
        // A file that is none of the modes has no label, and a run file may be there with nothing known about it.
        assertEquals("End attempt 3 and start attempt 4 on $emerald?", RunCopy.confirmNewAttempt(RunCopy.EndingRun(3, emerald), 4, emerald, null))
        assertEquals("End the current run and start attempt 2 on $emerald, Kaizo?", RunCopy.confirmNewAttempt(null, 2, emerald, "Kaizo"))
        assertEquals("Kaizo", RunCopy.modeName("Kaizo", File("RSE Kaizo.rnqs")))
        assertEquals("RSE my game", RunCopy.modeName(null, File("RSE my game.rnqs")))
        for (s in listOf(RunCopy.confirmNewAttempt(RunCopy.EndingRun(14, emerald), 15, emerald, "Kaizo"),
            RunCopy.confirmNewAttempt(RunCopy.EndingRun(14, emerald), 1, fireRed, "Kaizo"), RunCopy.confirmNewAttempt(null, 2, emerald, null)))
            assertCopyRules(s, s)
    }

    @Test
    fun `an empty game list says which case it is`() {
        assertEquals("No games yet." to "Add a Pokémon game you own. It shows here once the tracker can read it.", RunCopy.noGames(0))
        assertEquals("Nothing here is ready for Kaizo IronMON." to "Your library has 1 file the tracker cannot read.", RunCopy.noGames(1))
        assertEquals("Your library has 2 files the tracker cannot read.", RunCopy.noGames(2).second)
        assertEquals("Add a game", RunCopy.ADD_A_GAME)
        for (n in 0..3) { val (h, d) = RunCopy.noGames(n); assertCopyRules(h, "headline $n"); assertCopyRules(d, "detail $n") }
    }

    @Test
    fun `the guard says what to do, in the audit's words`() {
        val emerald = RomKind.EMERALD_U to File("emerald.gba")
        val natEmerald = RomKind.EMERALD_NATDEX_121 to File("emerald-natdex.gba")
        assertEquals("Add a game first.", RunPairing.problem(null, File("RSE Kaizo.rnqs")))
        assertEquals("Add a game first.", RunPairing.problem(null, null))
        assertEquals("Pick a mode first.", RunPairing.problem(emerald, null))
        assertNull(RunPairing.problem(emerald, File("RSE Kaizo.rnqs")))
        assertNull(RunPairing.problem(natEmerald, File("RSE NatDex v1.2 Kaizo.rnqs")))
        assertEquals("That mode is for the standard Pokédex, and this game is the Nat. Dex version. Pick a Nat. Dex mode.",
            RunPairing.problem(natEmerald, File("RSE Kaizo.rnqs")))
        assertEquals("That mode needs the Nat. Dex version of this game. Pick a standard mode, or make the Nat. Dex version in Library, Patched versions.",
            RunPairing.problem(emerald, File("RSE NatDex v1.2 Kaizo.rnqs")))
        for (s in listOf(RunPairing.NO_GAME, RunPairing.NO_MODE, RunPairing.NAT_DEX_GAME, RunPairing.STANDARD_GAME)) assertCopyRules(s, s)
        // The words that named "a settings file" and "a ROM" are gone from the screen.
        for (old in listOf("Set up a game first on the Library tab.", "Pick a settings file first, or import one.",
            "is a vanilla settings file", "is a Nat. Dex settings file", "That combination crashes the intro.")) assertFalse(old in run, old)
    }

    @Test
    fun `the selected game comes first and the rest keep their order`() {
        val games = RomKind.all.take(5).map { it to File("${it.id}.rom") }
        val last = games.last().first.id
        val shown = RunGames.selectedFirst(games, last)
        assertEquals(last, shown.first().first.id)
        assertEquals(games.dropLast(1), shown.drop(1), "the rest are in the order they were")
        assertEquals(games, RunGames.selectedFirst(games, games.first().first.id))
        assertEquals(games, RunGames.selectedFirst(games, null))
        assertEquals(games, RunGames.selectedFirst(games, "not-a-game"))
        assertEquals(emptyList(), RunGames.selectedFirst(emptyList(), last))
    }

    // ------------------------------------------------------------------- the screen's wiring

    @Test
    fun `the screen opens each game on its own mode and keeps every pick`() {
        assertTrue("RulesetCatalog.openingFile(" in run)
        assertTrue("val modeMemory = remember { RunModeMemory.of(context.filesDir) }" in run)
        assertTrue("onSelect = { k -> pickSettings(modes.first { it.key == k }.preset) }," in run, "the chips keep their pick")
        assertTrue("Modifier.fillMaxWidth().clickable { pickSettings(f) }" in run, "so do the rows of the settings list")
        assertTrue("file?.let { pickSettings(it, (rom ?: selectedRom)?.first) }" in run, "and a game just built")
        assertTrue("modeMemory.set(rom.first.id, s.name)" in run, "and a run started")
        // Tapping another game brings back that game's own, and the first mode in the row is not the fallback any more.
        val card = run.substringAfter("gamesShown.forEach { pair ->").substringBefore("Spacer(Modifier.height(8.dp))")
        assertTrue("openingFor(pair.first)?.let { selectedSettings = it }" in card)
        assertTrue("if (selectedRom?.first?.id != pair.first.id)" in card, "tapping the game that is picked leaves its mode as it is")
        assertFalse("modes.firstOrNull()?.let { selectedSettings = it.preset }" in run)
        val effect = run.substringAfter("LaunchedEffect(selectedRom) {").substringBefore("\n        }\n")
        // The screen's own list rule (RulesetCatalog.listedFor): an untagged file the player picked is not snapped away (rc32 audit P2 #78).
        assertTrue("openingFor(rom)?.let { selectedSettings = it }" in effect && "listedFor(rom, cur)" in effect)
    }

    @Test
    fun `under the chips there is one line for the mode and a link to its rules`() {
        val header = run.indexOf("RulesetCatalog.MODE_HEADER")
        val chips = run.indexOf("ShellSegmented(")
        val blurb = run.indexOf("ModeBlurb(rom, mode)")
        assertTrue(header in 1 until chips && chips < blurb, "the header above the chips, the line under them")
        val fn = run.substringAfter("private fun ModeBlurb(").substringBefore("\n}\n")
        assertTrue("RulesetCatalog.modeLine(mode.key, rom.isNatDex, rom.family)" in fn, "the line for this game's own book")
        assertTrue("RulesDialog(family = rom.family, mode = mode.key, natDex = rom.isNatDex, kind = rom, onDismiss = { showRules = false })" in fn)
        val link = run.substringAfter("private fun RulesLink(").substringBefore("\n}\n")
        assertTrue("heightIn(min = Shell.touchTarget)" in link && "Role.Button" in link, "a 48dp target, read out as a button")
        assertTrue("RunCopy.READ_THE_RULES" in link)
        assertEquals("Read all the rules", RunCopy.READ_THE_RULES, "the line above it is a summary, not the whole set")
    }

    @Test
    fun `an empty game list has the button it names, and the screen can reach the Library`() {
        assertTrue("onAddGame: () -> Unit = {}," in run, "a parameter with a default that does nothing")
        assertTrue("NoGamesCard(libraryFiles, onAddGame)" in run)
        val card = run.substringAfter("private fun NoGamesCard(").substringBefore("\n}\n")
        assertTrue("EmptyState(headline, detail, actionLabel = RunCopy.ADD_A_GAME, onAction = onAddGame)" in card)
        assertFalse("No games yet. Add one on the Library tab." in run)
        assertTrue("if (preparedList.isEmpty()) runCatching { store.library.list().size }.getOrDefault(0) else 0" in run, "the count of files the tracker cannot read")
        // The component: the action is optional, both or neither, so the callers that pass two things still do.
        val empty = shell.substringAfter("fun EmptyState(").substringBefore("\n}\n")
        assertTrue("actionLabel: String? = null," in empty && "onAction: (() -> Unit)? = null," in empty)
        assertTrue("if (actionLabel != null && onAction != null)" in empty)
        assertTrue("fun EmptyState(\n    headline: String,\n    detail: String,\n    modifier: Modifier = Modifier," in shell, "the first three stay as they were")
    }

    @Test
    fun `the start button says what it starts, and only Continue is red while a run exists`() {
        val footer = run.substringAfter("// ---- Sticky footer")
        assertTrue("RunCopy.start(nextAttempt),\n              modifier = Modifier.fillMaxWidth()," in footer)
        assertTrue("accent = inPlay == null," in footer, "red only when there is no run to continue")
        assertTrue("Gen3Button(RunCopy.start(nextAttempt), accent = inPlay == null) {" in footer, "the question's button too")
        assertTrue("Gen3Button(RunCopy.CANCEL) { confirmNewRun = false }" in footer)
        assertTrue("RunCopy.confirmNewAttempt(" in footer)
        assertTrue("Gen3Button(if (run.nuzlocke) RunCopy.CONTINUE_NUZLOCKE else RunCopy.continueRun(run.attempt), accent = true) {" in run, "Continue is the filled one")
        assertEquals(1, Regex("""accent = true""").findAll(run).count(), "one filled button on the screen")
        // The number is the next one: what is counted plus one.
        assertTrue("val nextAttempt = (selectedRom?.first?.id?.let { store.attemptOf(it, selectedSettings?.name) } ?: 0) + 1" in run)
        for (gone in listOf("\"Start new run\"", "\"Randomize\"", "YES, NEW RUN", "\"CANCEL\"", "Back to attempt", "End the current run and roll a new seed"))
            assertFalse(gone in run, gone)
    }

    @Test
    fun `a pairing the run would be refused on is said before the question, not after it`() {
        val tap = run.substringAfter("fun startTapped() {").substringBefore("\n    }\n")
        assertTrue(tap.indexOf("pairingProblem()") in 0 until tap.indexOf("confirmNewRun = true"), tap)
        assertTrue(") { startTapped() }" in run)
        assertTrue("fun pairingProblem(): String? = RunPairing.problem(selectedRom, selectedSettings)" in run)
    }

    /**
     * RC35-NOTICED N #16, the rest of rc32 audit P2 #63: the lists were read in composition, and listPrepared reads a build
     * whose checksum is not in its memo whole, seconds on the main thread for a DS game. They are read on the IO thread,
     * and every pick made from them follows the read, not the count that asked for it.
     */
    @Test
    fun `the games and the settings files are read off the main thread, and the picks follow the read`() {
        assertTrue("produceState<RunLists?>(null, refresh) {" in run)
        assertTrue("withContext(kotlinx.coroutines.Dispatchers.IO) { RunLists(refresh, store.listPrepared(), store.listSettings()) }" in run)
        assertFalse("remember(refresh) { store.listPrepared() }" in run)
        assertFalse("remember(refresh) { store.listSettings() }" in run)
        for (keyed in listOf("var selectedRom by remember(listed)", "var selectedSettings by remember(listed)", "val lastRun = remember(listed)",
            "val libraryFiles = remember(listed)"))
            assertTrue(keyed in run, keyed)
        // The selection after a save or a patched copy waits for the lists that hold the new file.
        assertEquals(2, Regex("""LaunchedEffect\(listed\) \{""").findAll(run).count())
        assertFalse("LaunchedEffect(refresh)" in run)
    }

    /** RC35-NOTICED N #13, the rest of rc32 audit P2 #33: Build your own and its pages were plain remember. */
    @Test
    fun `Build your own is kept with the activity, its game and its pages`() {
        assertTrue("var buildGame by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = RunLists.BuildGameSaver)" in run)
        val dir = java.nio.file.Files.createTempDirectory("buildgame").toFile()
        try {
            val rom = File(dir, "emerald.gba").apply { writeBytes(ByteArray(8)) }
            assertEquals(RomKind.EMERALD_U to rom, RunLists.buildGameOf(RomKind.EMERALD_U.id + "\n" + rom.path))
            assertEquals(null, RunLists.buildGameOf(RomKind.EMERALD_U.id + "\n" + File(dir, "gone.gba").path), "a game gone since")
            assertEquals(null, RunLists.buildGameOf("not-a-game\n" + rom.path))
            // The builder's plan, its starters and its picks, back as they were; a starting point gone since is none.
            val base = File(dir, "RSE Kaizo.rnqs").apply { writeBytes(byteArrayOf(1)) }
            for (plan in listOf(
                GameBuild.Plan(),
                GameBuild.Plan(base, GameBuild.Starters.Pick(listOf(1, null, 255)), linkedMapOf("movesets" to "RANDOM_PREFER_TYPE", "tms" to "keep")),
                GameBuild.Plan(null, GameBuild.Starters.Random, emptyMap()),
                GameBuild.Plan(null, GameBuild.Starters.Own, mapOf("evolutions" to "a=b")),
            )) assertEquals(plan, PlanText.planOf(PlanText.of(plan)))
            base.delete()
            assertEquals(null, PlanText.planOf(PlanText.of(GameBuild.Plan(base))).base)
            assertEquals(listOf(4, null), PlanText.slotsOf(PlanText.slotsText(listOf(4, null))))
        } finally { dir.deleteRecursively() }
        val build = File("src/main/kotlin/com/ironmonone/app/BuildYourGame.kt").readText()
        for (v in listOf("var step by rememberSaveable", "var plan by rememberSaveable(stateSaver = PlanText.Saver)", "var slot by rememberSaveable",
            "var typedName by rememberSaveable", "var pickMemory by rememberSaveable(stateSaver = PlanText.SlotsSaver)"))
            assertTrue(v in build, v)
    }

    @Test
    fun `the game list opens with the selected game first`() {
        // Keyed on the lists read (RC35-NOTICED N #16): they come from the IO thread now.
        assertTrue("val gamesShown = remember(listed) { RunGames.selectedFirst(preparedList, firstRom?.first?.id) }" in run)
        assertTrue("gamesShown.forEach { pair ->" in run)
        assertFalse("preparedList.forEach { pair ->" in run)
    }
}
