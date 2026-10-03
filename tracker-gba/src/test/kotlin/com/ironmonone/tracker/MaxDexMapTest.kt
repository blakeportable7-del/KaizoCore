package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MaxDex 1.0's tracker map (GameMap.MAXDEX_FR_10). It publishes no slot table, so its addresses are the ones its own
 * tracker extension hardcodes; a ROM is given them only when its Game Freak header says it is this build. Without
 * the map a MaxDex game got FireRed 1.1's and read garbage. The decoders it needs are checked on Blake's patched test
 * ROM when it is to hand (IRONMON_ROMS holding firered-maxdex.gba, else the vendor folder), skipped otherwise.
 */
class MaxDexMapTest {

    /** A header and nothing else: FireRed 1.1's code and version, a species count and the three tables. */
    private fun header(species: Long = 1255, names: Long = 0x08246018, stats: Long = 0x08270988, moves: Long = 0x08268000,
                       code: String = "BPRE", version: Int = 1): MemoryReader {
        val rom = ByteArray(0x400)
        code.forEachIndexed { i, c -> rom[0xAC + i] = c.code.toByte() }
        rom[0xBC] = version.toByte()
        fun put(at: Int, v: Long) { for (i in 0..3) rom[at + i] = (v shr (8 * i)).toByte() }
        put(0x170, species); put(0x144, names); put(0x1BC, stats); put(0x1CC, moves)
        return MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off >= 0 && off + length <= rom.size) rom.copyOfRange(off, off + length) else ByteArray(0)
        }
    }

    @Test
    fun `a ROM is given the MaxDex map by its header, and refused when any of it is wrong`() {
        assertEquals(GameMap.MAXDEX_FR_10, GameMap.resolve(header()))
        assertEquals("MaxDex 1.0", GameMap.resolve(header()).name)
        // 1255 species with another table, another game or another version is not this build: refused, never guessed.
        for (wrong in listOf(header(stats = 0x08254784), header(names = 0x08245F50), header(moves = 0x08250C74),
            header(code = "BPEE"), header(version = 0))) {
            assertFailsWith<IllegalArgumentException> { GameMap.resolve(wrong) }
            assertNull(GameMap.resolveOrNull(wrong))
        }
        // FireRed 1.1 itself keeps its own map.
        assertEquals(GameMap.FIRERED_U_V11, GameMap.resolve(header(species = 0x12345678)))
    }

    @Test
    fun `the map carries MaxDex's layouts`() {
        val m = GameMap.MAXDEX_FR_10
        assertEquals("maxdex", m.nameSet)
        assertTrue(m.learnsetJambo && m.moveCategoryByte && m.abilitiesAreU16 && m.expandedSpeciesIds)
        assertEquals(0x20, m.baseStatsStride)
        assertEquals(0x5C, m.battleMonSize)
        assertEquals(listOf(0x22, 0x25, 0x2A, 0x54), listOf(m.battleMonTypes, m.battleMonPp, m.battleMonHp, m.status2Offset))
        assertEquals(PokemonDecoder.Layout.VANILLA, m.monLayout)
        assertEquals("maxdex", m.abilityScriptTable)
        // Vanilla's offsets are what every other map keeps.
        val v = GameMap.FIRERED_U_V11
        assertEquals(listOf(0x21, 0x24, 0x28, ""), listOf(v.battleMonTypes, v.battleMonPp, v.battleMonHp, v.nameSet))
        // MaxDexExtension.lua 1.0's FireRed BattleScript_* lines, symbol plus offset.
        assertEquals(MoveScripts(0x081DFA2AL + 0x10, 0x081DF9D7L + 0xA, 0x081DFB15L + 0x3, 0x081DFB15L + 0xC, 0x081DFB58L + 0x3,
            0x081DFA4BL + 0xE, 0x081DFB7FL + 0x3, 0x081DFB7FL + 0xC, 0x081DFA95L + 0x3, 0x081DFA95L + 0x6, 0x081DFA95L + 0x8,
            0x081DFAA2L + 0x5, 0x081DFAA2L + 0xA), m.moveScripts)
        // Play as your Pokemon reads MaxDex's overworld out of its own code (OverworldScanTest, on the real build). A ROM that
        // is a header and nothing else has no code to read: refused, in MaxDex's own words.
        assertNull(Overworld.resolve(m, header()))
        assertEquals("Play as your Pokemon could not find its way around this MaxDex build.", Overworld.whyNot(m))
    }

    private val rom: ByteArray? = File(System.getenv("IRONMON_ROMS") ?: "C:/Users/bepor/IronMonOne/.vendor/roms", "firered-maxdex.gba")
        .takeIf { it.isFile }?.readBytes()

    private fun tracker(bytes: ByteArray): GbaTracker {
        val mem = MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off >= 0 && off + length <= bytes.size) bytes.copyOfRange(off, off + length) else ByteArray(0)
        }
        return GbaTracker(mem, GameMap.resolve(mem))
    }

    @Test
    fun `on the MaxDex ROM the tracker reads its names, stats, moves, learnsets, balls and trainers`() {
        val bytes = rom ?: return println("MaxDexMapTest: firered-maxdex.gba not to hand, ROM checks skipped")
        val t = tracker(bytes)
        assertEquals("maxdex", t.nameSet)
        assertEquals("Bulbasaur", t.speciesName(1))
        assertEquals("Dragonite-M", t.speciesName(1236))
        assertEquals("Baxcalibur-M", t.speciesName(1280))
        assertEquals("Malignant Chain", t.moveName(841))
        assertEquals("Poison Puppeteer", t.abilityName(289))
        assertEquals("Overgrow", t.abilityName(65))

        // 32-byte species entries with u16 abilities: Bulbasaur and the last Z-A mega.
        val bulbasaur = assertNotNull(t.baseStats(1))
        assertEquals(listOf(45, 49, 49, 45, 65, 65), listOf(bulbasaur.hp, bulbasaur.atk, bulbasaur.def, bulbasaur.spe, bulbasaur.spAtk, bulbasaur.spDef))
        assertEquals(12 to 3, bulbasaur.type1 to bulbasaur.type2)
        assertEquals(65 to 34, bulbasaur.ability1 to bulbasaur.ability2)
        assertEquals(700, assertNotNull(t.baseStats(1280)).bst, "Baxcalibur-M")

        // The move table's own category byte: Fire Punch is physical, though Gen 3's rule would make a Fire move special.
        val firePunch = assertNotNull(t.moveRowFor(7))
        assertEquals(listOf(75, 10, 100, 15), listOf(firePunch.power, firePunch.type, firePunch.acc, firePunch.ppMax ?: firePunch.pp))
        assertEquals("PHY", firePunch.category)
        assertEquals("SPE", assertNotNull(t.moveRowFor(53)).category, "Flamethrower")
        assertEquals("STA", assertNotNull(t.moveRowFor(361)).category, "Roost")
        val freezeDry = assertNotNull(t.moveRowFor(578))
        assertEquals("Freeze-Dry", freezeDry.name)
        assertEquals(listOf(70, 15, "SPE"), listOf(freezeDry.power, freezeDry.type, freezeDry.category))
        assertEquals(1, assertNotNull(t.moveRowFor(98)).priority, "Quick Attack's +1 at +9")

        // Jambo's learnsets, the build's empty slots skipped.
        assertEquals(listOf(1 to 33, 1 to 45, 3 to 22, 6 to 74, 9 to 73, 12 to 75, 15 to 77, 15 to 79, 21 to 36, 24 to 230, 27 to 235, 36 to 76),
            t.learnset(1))

        // The lab's balls stand Bulbasaur, Squirtle and Charmander from the left, as in FireRed.
        assertEquals(listOf("LEFT" to 1, "MIDDLE" to 7, "RIGHT" to 4), t.starters().map { it.ball to it.species })
        // Brock is trainer 414 in MaxDex's 40-byte entries.
        val brock = assertNotNull(t.trainer(414))
        assertEquals("BROCK", brock.name.uppercase())
        assertTrue(brock.party.isNotEmpty())
        // Friendship evolution: the ROM's byte reads 219, so 220.
        assertEquals(220, t.friendshipRequired())
        // MaxDex's own evolutions and random evolutions.
        assertEquals("18", t.evolution(412))
        assertTrue(t.hasRandomEvos(1))
        assertTrue("Fairy" in t.typeNames)
        // The log's names are the ROM's, cut to ten letters, and find their Pokemon.
        val logNames = t.logSpeciesIds()
        assertEquals(1236, logNames["DRAGONITEM"])
        assertEquals(687, logNames["FLECHINDER"])
        assertEquals(1246, logNames["ABSOLZ"])
        assertEquals(1, logNames["BULBASAUR"])
    }

    @Test
    fun `only MaxDex has log names of its own`() {
        val fireRed = header(species = 0x12345678)
        assertTrue(GbaTracker(fireRed, GameMap.resolve(fireRed)).logSpeciesIds().isEmpty())
    }
}
