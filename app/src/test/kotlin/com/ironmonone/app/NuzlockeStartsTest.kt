package com.ironmonone.app

import com.ironmonone.core.Generation
import com.ironmonone.core.Platform
import com.ironmonone.core.RomKind
import com.ironmonone.tracker.nuzlocke.NuzlockeNotes
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.RunStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the Nuzlocke screen decides, apart from the screen (2026-09-29): which games can start which kind of run,
 * what a run is called, why Start is off.
 */
class NuzlockeStartsTest {

    private fun entry(kind: RomKind?, crc: Long, name: String = "game.gba") =
        LibraryStore.Entry(File(name), name, crc, kind, kind?.platform ?: Platform.GBA, "a game")

    private fun verified(kind: RomKind, name: String = kind.id + ".gba") = entry(kind, kind.expectedCrc, name)

    // ---------------------------------------------------------------- games

    @Test
    fun `only a checked copy from the Library can start a plain run, on any console the tracker reads`() {
        val emerald = verified(RomKind.EMERALD_U)
        val natDex = verified(RomKind.FIRERED_NATDEX_121)
        val ruby = verified(RomKind.RUBY_U)
        val modified = entry(RomKind.EMERALD_U, RomKind.EMERALD_U.expectedCrc xor 1L, "hack.gba")
        val unknown = entry(null, 0x1234L, "mystery.gba")
        val ds = verified(RomKind.HEARTGOLD_U)
        val gameBoyKind = RomKind.all.first { it.generation == Generation.GBC2 && it.expectedCrc != RomKind.CRC_UNKNOWN }
        val gameBoy = verified(gameBoyKind)
        val got = NuzlockeStarts.plainGames(listOf(emerald, modified, natDex, unknown, ds, gameBoy, ruby))
        assertEquals(listOf(emerald, natDex, ds, gameBoy, ruby), got, "the tracker reads only a copy whose checksum matched, and every console it reads has a Nuzlocke")
        assertTrue(modified.kind != null && !modified.verified, "the header alone is not enough")
    }

    @Test
    fun `every game the app can check is offered, so no supported game is left out`() {
        val checked = RomKind.all.filter { it.expectedCrc != RomKind.CRC_UNKNOWN }
        assertTrue(checked.size >= 25, "the app knows only ${checked.size} checked games")
        val got = NuzlockeStarts.plainGames(checked.map { verified(it) }).map { it.kind }
        assertEquals(checked, got)
    }

    @Test
    fun `a game with the header of a supported game and no pinned checksum is not offered`() {
        // A kind identified by its header whose checksum has not been pinned from a dump yet (Black is one on DS).
        val unpinned = RomKind.EMERALD_U.copy(id = "emerald-unpinned", expectedCrc = RomKind.CRC_UNKNOWN)
        assertEquals(Generation.GBA3, unpinned.generation)
        assertEquals(emptyList(), NuzlockeStarts.plainGames(listOf(entry(unpinned, 0x1L))))
    }

    @Test
    fun `every prepared game can be randomized for a Nuzlocke`() {
        val prepared = listOf(
            RomKind.EMERALD_U to File("emerald.gba"),
            RomKind.PLATINUM_U to File("platinum.nds"),
            RomKind.FIRERED_NATDEX_121 to File("firered-natdex.gba"),
            RomKind.CRYSTAL_U to File("crystal.gbc"),
        )
        assertEquals(prepared.map { it.first }, NuzlockeStarts.randomGames(prepared).map { it.first })
        assertEquals(emptyList(), NuzlockeStarts.randomGames(emptyList()))
    }

    // ---------------------------------------------------------------- the family of games a run is on

    @Test
    fun `a game belongs to the family its generation says, and an unknown one is Generation 3`() {
        assertEquals(NuzlockeSystem.GEN1, NuzlockeStarts.systemOf(RomKind.RED_U))
        assertEquals(NuzlockeSystem.GEN1, NuzlockeStarts.systemOf(RomKind.YELLOW_PF))
        assertEquals(NuzlockeSystem.GEN2, NuzlockeStarts.systemOf(RomKind.CRYSTAL_U))
        assertEquals(NuzlockeSystem.GEN2, NuzlockeStarts.systemOf(RomKind.GOLD_U))
        assertEquals(NuzlockeSystem.GEN3, NuzlockeStarts.systemOf(RomKind.EMERALD_U))
        assertEquals(NuzlockeSystem.GEN3, NuzlockeStarts.systemOf(RomKind.FIRERED_NATDEX_121))
        assertEquals(NuzlockeSystem.GEN4, NuzlockeStarts.systemOf(RomKind.PLATINUM_U))
        assertEquals(NuzlockeSystem.GEN4, NuzlockeStarts.systemOf(RomKind.HEARTGOLD_SUPERKAIZO))
        assertEquals(NuzlockeSystem.GEN5, NuzlockeStarts.systemOf(RomKind.BLACK2_U))
        assertEquals(NuzlockeSystem.GEN5, NuzlockeStarts.systemOf(RomKind.WHITE_U))
        assertEquals(NuzlockeSystem.GEN3, NuzlockeStarts.systemOf(null))
        for (g in Generation.entries) {
            val kinds = RomKind.all.filter { it.generation == g }
            assertTrue(kinds.isNotEmpty(), g.name)
            assertEquals(1, kinds.map { NuzlockeStarts.systemOf(it) }.toSet().size, "one family for all of $g")
        }
    }

    @Test
    fun `the game key is the game a patched copy was made from, and a Generation 3 run has none`() {
        assertEquals("red", NuzlockeStarts.gameKeyOf(RomKind.RED_U))
        assertEquals("red", NuzlockeStarts.gameKeyOf(RomKind.RED_PF))
        assertEquals("crystal", NuzlockeStarts.gameKeyOf(RomKind.CRYSTAL_PF))
        assertEquals("gold", NuzlockeStarts.gameKeyOf(RomKind.GOLD_PF))
        assertEquals("platinum", NuzlockeStarts.gameKeyOf(RomKind.PLATINUM_SUPERKAIZO))
        assertEquals("heartgold", NuzlockeStarts.gameKeyOf(RomKind.HEARTGOLD_SUPERKAIZO))
        assertEquals("black2", NuzlockeStarts.gameKeyOf(RomKind.BLACK2_FASTERPWT))
        assertEquals("white", NuzlockeStarts.gameKeyOf(RomKind.WHITE_U))
        assertEquals("", NuzlockeStarts.gameKeyOf(RomKind.EMERALD_U))
        assertEquals("", NuzlockeStarts.gameKeyOf(RomKind.EMERALD_FASTER))
        assertEquals("", NuzlockeStarts.gameKeyOf(null))
    }

    @Test
    fun `every Game Boy and DS game the app knows has a family and a key, and the rules page has words for it`() {
        for (k in RomKind.all) {
            val system = NuzlockeStarts.systemOf(k)
            if (system == NuzlockeSystem.GEN3) continue
            val key = NuzlockeStarts.gameKeyOf(k)
            assertTrue(key.isNotBlank(), k.id)
            val notes = NuzlockeNotes.forGame(system, key)
            assertTrue(notes.automatic.isNotEmpty() && notes.byHand.isNotEmpty(), "${k.id}: nothing on the rules page")
        }
    }

    @Test
    fun `a run is named for the game as the app knows it, and says when the game was randomized`() {
        assertEquals(RomKind.EMERALD_U.displayName, NuzlockeStarts.gameLabel(RomKind.EMERALD_U, false))
        assertEquals(RomKind.EMERALD_U.displayName + ", randomized", NuzlockeStarts.gameLabel(RomKind.EMERALD_U, true))
    }

    // ---------------------------------------------------------------- Monotype

    @Test
    fun `Monotype offers the seventeen types, and Fairy only on a Nat Dex build`() {
        val plain = NuzlockeStarts.types(false)
        assertEquals(17, plain.size)
        assertFalse(9 in plain, "the unused Mystery slot is not a type")
        assertFalse(NuzlockeStarts.FAIRY in plain)
        assertEquals(plain + NuzlockeStarts.FAIRY, NuzlockeStarts.types(true))
        assertEquals(plain.size, plain.toSet().size)
    }

    @Test
    fun `Red, Blue and Yellow have no Steel or Dark Pokemon to pick a type for`() {
        val gen1 = NuzlockeStarts.types(false, NuzlockeSystem.GEN1)
        assertEquals(15, gen1.size)
        assertFalse(8 in gen1, "Steel")
        assertFalse(17 in gen1, "Dark")
        assertEquals(17, NuzlockeStarts.types(false, NuzlockeSystem.GEN2).size)
        assertEquals(17, NuzlockeStarts.types(false, NuzlockeSystem.GEN4).size)
        assertEquals(17, NuzlockeStarts.types(false, NuzlockeSystem.GEN5).size)
    }

    // ---------------------------------------------------------------- why Start is off

    private fun problem(
        preset: NuzlockePreset, rules: NuzlockeRules = NuzlockeRules.forPreset(preset),
        game: Boolean = true, mode: Boolean = true, busy: Boolean = false, natDex: Boolean = false,
    ) = NuzlockeStarts.problem(preset, rules, game, mode, busy, natDex)

    @Test
    fun `Start is off until a game is picked`() {
        for (p in NuzlockePreset.entries) assertEquals("Pick a game.", problem(p, game = false), p.label)
    }

    @Test
    fun `the presets that need nothing more start as soon as there is a game`() {
        for (p in listOf(NuzlockePreset.STANDARD, NuzlockePreset.HARDCORE, NuzlockePreset.WEDLOCKE, NuzlockePreset.GENLOCKE)) {
            assertNull(problem(p, mode = false, busy = true), p.label)
        }
    }

    @Test
    fun `Monotype needs its type, and Fairy needs a Nat Dex build`() {
        val mono = NuzlockePreset.MONOTYPE
        assertEquals("Pick the type.", problem(mono, NuzlockeRules.forPreset(mono, null)))
        assertNull(problem(mono, NuzlockeRules.forPreset(mono, 10)))
        val fairy = NuzlockeRules.forPreset(mono, NuzlockeStarts.FAIRY)
        assertEquals("Fairy only exists in the Nat. Dex builds.", problem(mono, fairy, natDex = false))
        assertNull(problem(mono, fairy, natDex = true))
    }

    @Test
    fun `Randomizer needs a mode and an idle randomizer`() {
        val r = NuzlockePreset.RANDOMIZER
        assertEquals("This game has no randomizer mode to pick.", problem(r, mode = false))
        assertEquals("The randomizer is busy. Try again in a moment.", problem(r, busy = true))
        assertNull(problem(r))
    }

    // ---------------------------------------------------------------- the runs list

    @Test
    fun `a run's line in the list says where it stands`() {
        assertTrue(NuzlockeStarts.statusLine(RunStatus.ACTIVE, "", 1_800_000_000_000L).startsWith("In progress since "))
        assertEquals("In progress since an unknown day", NuzlockeStarts.statusLine(RunStatus.ACTIVE, "", 0L))
        assertEquals("Over: Whiteout", NuzlockeStarts.statusLine(RunStatus.OVER, "Whiteout", 5L))
        assertEquals("Over", NuzlockeStarts.statusLine(RunStatus.OVER, "  ", 5L))
        assertEquals("Finished. The Champion is beaten.", NuzlockeStarts.statusLine(RunStatus.COMPLETE, "", 5L))
        assertEquals("Replaced by a newer run", NuzlockeStarts.statusLine(RunStatus.ABANDONED, "", 5L))
    }
}
