package com.ironmonone.app

import com.ironmonone.app.engine.GameFacts
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.dabomstew.pkrandom.Settings as NdSettings
import com.dabomstew.pkrandomzx.Settings as ZxSettings

/**
 * "Build your own" against real dumps (2026-09-29), for the checks a fixture cannot make: that the
 * species list the builder reads off a game is the one that game's randomizer knows, and that a
 * starter chosen in it is the Pokemon found in the ROM the engine writes.
 *
 * Skipped without IRONMON_ROMS (a folder of dumps named by RomKind id, as PrepOptionsTest and
 * SeedDeterminismTest read them: emerald-u.gba, firered-u-v11.gba, and so on). The Nat. Dex builds
 * are `emerald-natdex-121.gba` and `firered-natdex-121.gba`, the same folder's patched dumps.
 */
class GameBuildRomTest {
    private val roms = Dumps.romsDir()
    private fun rom(kind: RomKind): File? = roms?.let { Dumps.file(it, kind.id + "." + kind.fileExtension) }

    private val presets = File("src/main/assets/presets").listFiles { f -> f.extension == "rnqs" }!!.sortedBy { it.name }

    private val dirs = ArrayList<File>()
    private fun newStore(): PrepStore = PrepStore(Files.createTempDirectory("gamebuildrom").toFile().also { dirs += it })

    @AfterTest
    fun cleanUp() {
        dirs.forEach { it.deleteRecursively() }
        dirs.clear()
    }

    private fun skipped() = println("GameBuildRomTest skipped: set IRONMON_ROMS")

    // ------------------------------------------------------------------ the list

    @Test
    fun `the species list read off a real dump is the one the game's randomizer knows`() {
        if (roms == null) return skipped()
        // kind, how many Pokemon its list has, the starters the ROM ships with, abilities, move tutors
        class Expect(val kind: RomKind, val count: Int, val own: List<Int>, val abilities: Boolean, val tutors: Boolean)
        val expected = listOf(
            Expect(RomKind.FIRERED_U_V11, 386, listOf(1, 4, 7), true, true),
            Expect(RomKind.FIRERED_U_V10, 386, listOf(1, 4, 7), true, true),
            Expect(RomKind.LEAFGREEN_U, 386, listOf(1, 4, 7), true, true),
            Expect(RomKind.EMERALD_U, 386, listOf(252, 255, 258), true, true),
            Expect(RomKind.SAPPHIRE_U, 386, listOf(252, 255, 258), true, false),
            Expect(RomKind.GOLD_U, 251, listOf(155, 158, 152), false, false),
            Expect(RomKind.PLATINUM_U, 493, listOf(387, 390, 393), true, true),
            Expect(RomKind.DIAMOND_U, 493, listOf(387, 390, 393), true, false),
            Expect(RomKind.FIRERED_NATDEX_121, 1258, listOf(1, 4, 7), true, true),
            Expect(RomKind.EMERALD_NATDEX_121, 1258, listOf(252, 255, 258), true, true),
        )
        var ran = 0
        for (e in expected) {
            val src = rom(e.kind) ?: continue
            val f = GameFacts.read(e.kind, src)
            assertEquals(e.count, f.species.size, "${e.kind.id}: how many Pokémon")
            assertEquals((1..e.count).toList(), f.species.map { it.number }, "${e.kind.id}: numbered 1 to N with no gaps")
            assertEquals(e.own, f.ownStarters, "${e.kind.id}: the game's own starters")
            assertEquals(3, f.starterCount, e.kind.id)
            assertEquals(e.abilities, f.hasAbilities, "${e.kind.id}: abilities")
            assertEquals(e.tutors, f.hasMoveTutors, "${e.kind.id}: move tutors")
            assertEquals(e.count, f.lastNumber, e.kind.id)
            val rows = GameBuild.entries(f)
            assertEquals("Pikachu", rows[24].label, e.kind.id)
            assertEquals("Mr. Mime", rows[121].label, e.kind.id)
            assertEquals("Ho-Oh", rows[249].label, e.kind.id)
            if (e.count >= 386) assertEquals("Treecko", rows[251].label, e.kind.id)
            assertEquals(listOf(e.count), rows.filter { it.blocked != null }.map { it.number }, "${e.kind.id}: only the last one is refused")
            // The choices follow what the ROM says the game has.
            val ids = GameBuild.choices(e.kind, f).map { it.id }
            assertEquals(e.abilities, "abilities" in ids, "${e.kind.id}: the abilities question")
            assertEquals(e.tutors, "tutorMoves" in ids && "tutorCompat" in ids, "${e.kind.id}: the move tutor questions")
            ran++
        }
        // What only the Nat. Dex list has.
        rom(RomKind.EMERALD_NATDEX_121)?.let { src ->
            val f = GameFacts.read(RomKind.EMERALD_NATDEX_121, src)
            val rows = GameBuild.entries(f)
            assertEquals("Sylveon", rows[699].label)
            assertEquals("Pecharunt", rows[1024].label)
            assertNotNull(rows[699].iconId, "a picture for Sylveon")
            assertTrue(rows.any { it.label.endsWith("-M") }, "the fork lists mega forms too")
        }
        println("BUILD_FACTS_READ=$ran")
        assertTrue(ran > 0, "IRONMON_ROMS holds none of the dumps this checks")
    }

    // ------------------------------------------------------------------ the ROM

    private class Case(val kind: RomKind, val picks: List<Int?>)

    /** Randomizes [src] with the file [plan] saves as, and returns the ROM's starters as its own randomizer reads them back, and the log. */
    private fun randomizeWith(kind: RomKind, src: File, plan: GameBuild.Plan, facts: GameFacts.Facts): Pair<List<Int>, String> {
        val saved = GameBuild.save(newStore(), kind, plan, facts, "proof").getOrThrow()
        val out = File.createTempFile("gamebuild", "." + kind.fileExtension)
        try {
            val outcome = Randomizers.randomize(kind, src, saved, out, 20260929L)
            return GameFacts.read(kind, out).ownStarters to outcome.logText
        } finally {
            out.delete(); Randomizers.logFor(out).delete(); Randomizers.sidecarFor(out).delete()
        }
    }

    @Test
    fun `a starter chosen in the builder is the starter in the ROM the engine writes, on ZX and on the Nat Dex fork`() {
        if (roms == null) return skipped()
        val cases = listOf(
            Case(RomKind.FIRERED_U_V11, listOf(25, null, 152)),
            Case(RomKind.LEAFGREEN_U, listOf(94, 6, null)),
            Case(RomKind.EMERALD_U, listOf(null, 130, 248)),
            Case(RomKind.GOLD_U, listOf(25, 6, null)),
            Case(RomKind.FIRERED_NATDEX_121, listOf(700, null, 152)),
            Case(RomKind.EMERALD_NATDEX_121, listOf(null, 1000, 6)),
        )
        var ran = 0
        for (c in cases) {
            val src = rom(c.kind) ?: continue
            val facts = GameFacts.read(c.kind, src)
            val (starters, log) = randomizeWith(c.kind, src, GameBuild.Plan(starters = GameBuild.Starters.Pick(c.picks)), facts)
            assertEquals(3, starters.size, c.kind.id)
            val chosen = c.picks.filterNotNull()
            c.picks.forEachIndexed { i, n ->
                if (n != null) assertEquals(n, starters[i], "${c.kind.id}: starter ${i + 1} is the one that was picked")
                else {
                    assertTrue(starters[i] in 1 until facts.lastNumber, "${c.kind.id}: the random slot got ${starters[i]}")
                    assertTrue(starters[i] !in chosen, "${c.kind.id}: the random slot repeated a chosen Pokémon")
                }
            }
            // The engine's own log says the same, in its own words.
            assertTrue("Set starter 1 to" in log && "Set starter 3 to" in log, "${c.kind.id}: the log names the starters")
            val first = c.picks[0]
            if (first != null) assertTrue(GameBuild.entries(facts)[first - 1].label.lowercase() in log.lowercase(), "${c.kind.id}: the log names ${first}")
            ran++
        }
        println("BUILD_ROMS_RANDOMIZED=$ran")
        assertTrue(ran > 0, "IRONMON_ROMS holds none of the dumps this checks")
    }

    @Test
    fun `starters chosen over an official mode survive that mode's other changes in a real randomization`() {
        if (roms == null) return skipped()
        var ran = 0
        for ((kind, picks) in listOf(RomKind.FIRERED_U_V11 to listOf(25, null, 152), RomKind.FIRERED_NATDEX_121 to listOf(700, null, 152), RomKind.EMERALD_U to listOf(null, 130, 248))) {
            val src = rom(kind) ?: continue
            val facts = GameFacts.read(kind, src)
            val kaizo = RulesetCatalog.forRom(kind, presets).single { it.key == "kaizo" }.preset
            val plan = GameBuild.Plan(base = kaizo, starters = GameBuild.Starters.Pick(picks), picks = mapOf("trainerLevels" to "20"))
            val (starters, log) = randomizeWith(kind, src, plan, facts)
            picks.forEachIndexed { i, n -> if (n != null) assertEquals(n, starters[i], "${kind.id}: starter ${i + 1} over Kaizo") }
            // Kaizo's own changes still happened around the starters: its random trainers are in the log.
            assertTrue("--Trainers Pokemon--" in log, "${kind.id}: Kaizo's trainers are in the log")
            ran++
        }
        println("BUILD_ROMS_OVER_KAIZO=$ran")
        assertTrue(ran > 0, "IRONMON_ROMS holds none of the dumps this checks")
    }

    // ------------------------------------------------------------------ why the last one is refused

    @Test
    fun `the engine swaps the last Pokemon in a game's list for the game's own starter, which is why the builder refuses it`() {
        if (roms == null) return skipped()
        var ran = 0
        rom(RomKind.FIRERED_U_V11)?.let { src ->
            val kind = RomKind.FIRERED_U_V11
            val facts = GameFacts.read(kind, src)
            val h = com.dabomstew.pkrandomzx.romhandlers.Gen3RomHandler.Factory().create(com.dabomstew.pkrandomzx.RandomSource.instance())
            h.loadRom(src.absolutePath)
            fun after(number: Int): Int {
                val s = GameBuild.blank(kind, facts) as ZxSettings
                s.setStartersMod(false, true, false, false)
                s.customStarters = intArrayOf(GameBuild.storedStarter(number), 1, 1)
                s.tweakForRom(h)
                return s.customStarters[0]
            }
            assertEquals(GameBuild.storedStarter(385), after(385), "the one before the last is kept")
            assertNotEquals(GameBuild.storedStarter(386), after(386), "the last one (Deoxys) is replaced by the engine")
            ran++
        }
        rom(RomKind.FIRERED_NATDEX_121)?.let { src ->
            val kind = RomKind.FIRERED_NATDEX_121
            val facts = GameFacts.read(kind, src)
            val h = com.dabomstew.pkrandom.romhandlers.Gen3RomHandler.Factory().create(com.dabomstew.pkrandom.RandomSource.instance())
            h.loadRom(src.absolutePath)
            fun after(number: Int): Int {
                val s = GameBuild.blank(kind, facts) as NdSettings
                s.setStartersMod(false, true, false, false)
                s.customStarters = intArrayOf(GameBuild.storedStarter(number), 1, 1)
                s.tweakForRom(h)
                return s.customStarters[0]
            }
            assertEquals(GameBuild.storedStarter(1257), after(1257), "the one before the last is kept")
            assertNotEquals(GameBuild.storedStarter(1258), after(1258), "the last one is replaced by the fork too")
            ran++
        }
        println("BUILD_ENGINE_SWAPS_CHECKED=$ran")
        assertTrue(ran > 0, "IRONMON_ROMS holds none of the dumps this checks")
    }
}
