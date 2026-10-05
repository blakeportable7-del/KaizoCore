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
import kotlin.test.assertNotNull
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
        // All but MaxDex, which has no Nuzlocke in its first version (MaxDexPrepareTest), and a game the app only plays
        // (the official Heart & Soul 2.0.6: no tracker reads it, so its Nuzlocke is the KaizoCore build's).
        val checked = RomKind.all.filter { it.expectedCrc != RomKind.CRC_UNKNOWN && !it.isMaxDex && !it.playOnly }
        assertTrue(checked.size >= 25, "the app knows only ${checked.size} checked games")
        val got = NuzlockeStarts.plainGames((checked + RomKind.FIRERED_MAXDEX_10 + RomKind.HEARTSOUL_206).map { verified(it) }).map { it.kind }
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
        assertEquals(NuzlockeSystem.GEN4, NuzlockeStarts.systemOf(RomKind.HEARTGOLD_IRONMON))
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
        assertEquals("heartgold", NuzlockeStarts.gameKeyOf(RomKind.HEARTGOLD_IRONMON))
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
        assertEquals("Fairy only exists in the Nat. Dex builds and Heart & Soul.", problem(mono, fairy, natDex = false))
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

    @Test
    fun `a type the picked game has none of stops the start`() {
        // rc32 audit P3 #38: Steel or Dark picked with no game picked stayed on for Red, Blue or Yellow, and every wild
        // Pokemon of the run was then skipped as the wrong type.
        val mono = NuzlockePreset.MONOTYPE
        val steel = NuzlockeRules.forPreset(mono, 8)
        val gen1 = assertNotNull(NuzlockeStarts.problem(mono, steel, game = true, mode = true, busy = false, natDex = false, system = NuzlockeSystem.GEN1))
        assertEquals("This game has no Steel-type Pokémon. Pick another type.", gen1)
        assertNotNull(NuzlockeStarts.problem(mono, NuzlockeRules.forPreset(mono, 17), true, true, false, false, NuzlockeSystem.GEN1), "Dark")
        assertNull(NuzlockeStarts.problem(mono, steel, game = true, mode = true, busy = false, natDex = false, system = NuzlockeSystem.GEN2))
        assertNull(NuzlockeStarts.problem(mono, NuzlockeRules.forPreset(mono, 10), true, true, false, false, NuzlockeSystem.GEN1), "Fire is in Red")
        assertFalse('—' in gen1 || '–' in gen1)
    }

    // ---------------------------------------------------------------- the game's own save

    @Test
    fun `a DS game's save is melonDS's own file, and every other game's is its battery save`() {
        // rc32 audit P2 #37: the DS check read the .srm melonDS never writes, so "This game already has a save" never showed.
        val filesDir = kotlin.io.path.createTempDirectory("insave").toFile()
        try {
            val store = PrepStore(filesDir)
            val ds = GameSession.forLibrary(entry(RomKind.HEARTGOLD_U, RomKind.HEARTGOLD_U.expectedCrc, "heartgold.nds"))!!
            val save = NuzlockeStarts.inGameSave(filesDir, ds, store.sramFile(ds))
            assertEquals(File(filesDir, "saves/heartgold.sav"), save)
            assertFalse(SaveCheck.hasProgress(save, ds.platform), "no file yet")
            save.parentFile.mkdirs(); save.writeBytes(ByteArray(512) { if (it == 40) 7 else -1 })
            assertTrue(SaveCheck.hasProgress(save, ds.platform))
            val gba = GameSession.forLibrary(verified(RomKind.EMERALD_U))!!
            assertEquals(store.sramFile(gba), NuzlockeStarts.inGameSave(filesDir, gba, store.sramFile(gba)))
            assertEquals(File(filesDir, "saves/lib/${gba.id}.srm"), store.sramFile(gba))
        } finally { filesDir.deleteRecursively() }
    }

    // ---------------------------------------------------------------- the next game of a Genlocke

    private fun header(preset: NuzlockePreset, genlockeId: String) =
        com.ironmonone.tracker.nuzlocke.NuzlockeText.Header("nz-1", "lib-1", "Game", preset, 1L, RunStatus.COMPLETE, "", 1, genlockeId)

    @Test
    fun `any finished run started as a Genlocke offers the next game, whatever its preset`() {
        // rc32 audit P2 #39: only the Genlocke preset was asked, so a Hardcore run with the switch on never offered it.
        assertTrue(NuzlockeStarts.canContinue(header(NuzlockePreset.HARDCORE, "gl-1"), RunStatus.COMPLETE))
        assertTrue(NuzlockeStarts.canContinue(header(NuzlockePreset.WEDLOCKE, "gl-1"), RunStatus.COMPLETE))
        assertFalse(NuzlockeStarts.canContinue(header(NuzlockePreset.STANDARD, ""), RunStatus.COMPLETE))
        assertTrue(NuzlockeStarts.canContinue(header(NuzlockePreset.GENLOCKE, ""), RunStatus.COMPLETE), "a Genlocke preset run as before")
        assertFalse(NuzlockeStarts.canContinue(header(NuzlockePreset.HARDCORE, "gl-1"), RunStatus.OVER))
        // The next game keeps the chain's own preset and switches, the Genlocke switch on.
        val hardcore = NuzlockeRules.forPreset(NuzlockePreset.HARDCORE).copy(genlocke = true, dupes = false)
        assertEquals(hardcore, NuzlockeStarts.legRules(hardcore.copy(genlocke = false)), "the switches kept, the Genlocke switch on")
        assertEquals(NuzlockePreset.HARDCORE, NuzlockeStarts.legRules(hardcore).preset)
        val random = NuzlockeStarts.legRules(NuzlockeRules.forPreset(NuzlockePreset.RANDOMIZER).copy(genlocke = true))
        assertEquals(NuzlockePreset.GENLOCKE, random.preset, "the next game is played as it is")
    }
}
