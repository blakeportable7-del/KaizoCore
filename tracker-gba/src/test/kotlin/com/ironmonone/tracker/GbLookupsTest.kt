package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Game Boy trackers' answers to the panel's lookups (GbLookups): learn
 * levels out of the ROM, move summaries, weight and evolution from the
 * references' tables, weaknesses on each generation's chart. The panel used
 * to get none of these on a Game Boy game (trackerRef is Gen 3 only).
 *
 * The fixtures write learnsets where the game keeps them, behind a pointer
 * table, so a tracker that read the wrong table, skipped the evolution
 * entries wrongly or looked a Gen 1 species up by its dex number instead of
 * its internal id reads the wrong list. The real-dump half needs
 * IRONMON_ROMS=<dir with red-u.gbc, yellow-u.gbc, crystal-u.gbc, gold-u.gbc>
 * and is skipped without it, as RomDataTest is.
 */
class GbLookupsTest {

    private class Wram(size: Int) : MemoryReader {
        val bytes = ByteArray(size)
        fun put(off: Long, v: Int) { bytes[off.toInt()] = v.toByte() }
        fun be16(off: Long, v: Int) { put(off, v shr 8); put(off + 1, v and 0xFF) }
        override fun read(address: Long, length: Int): ByteArray {
            val o = (address - GbcTracker.RAM).toInt()
            return if (o >= 0 && o + length <= bytes.size) bytes.copyOfRange(o, o + length) else ByteArray(0)
        }
    }

    private fun header(r: ByteArray, title: String, cgb: Int) {
        intArrayOf(0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0D).forEachIndexed { i, b -> r[0x104 + i] = b.toByte() }
        title.forEachIndexed { i, c -> r[0x134 + i] = c.code.toByte() }
        r[0x143] = cgb.toByte()
    }

    /** A pointer-table entry at [table] + 2*[index] to a bank-local [pointer], and the bytes it points to. */
    private fun learnset(r: ByteArray, table: Int, index: Int, pointer: Int, bytes: List<Int>) {
        val at = table + index * 2
        r[at] = (pointer and 0xFF).toByte(); r[at + 1] = (pointer shr 8).toByte()
        val off = (pointer % 0x4000) + (table / 0x4000) * 0x4000
        bytes.forEachIndexed { i, b -> r[off + i] = b.toByte() }
    }

    // ------------------------------------------------------------------ Gen 1

    /** Red: internal 0xB1 is dex 4 (Charmander) here, and Charmander's learnset sits behind 0xB1's pointer. */
    private fun redRom(): ByteArray {
        val map = Gen1Map.RED_BLUE
        val r = ByteArray(0x50000)
        header(r, "POKEMON RED", 0x00)
        r[map.dexOrder + 0xB1 - 1] = 4
        r[map.dexOrder + 0x04 - 1] = 64          // internal 0x04: Kadabra, Psychic
        val b = map.baseStats + (4 - 1) * Gen1Tracker.BASE_STRIDE
        r[b] = 4; r[b + 1] = 39; r[b + 2] = 52; r[b + 3] = 43; r[b + 4] = 65; r[b + 5] = 50; r[b + 6] = 20; r[b + 7] = 20
        val k = map.baseStats + (64 - 1) * Gen1Tracker.BASE_STRIDE
        r[k] = 64; r[k + 6] = 24; r[k + 7] = 24                                        // Psychic / Psychic
        val m = map.moves + (52 - 1) * Gen1Tracker.MOVE_STRIDE
        r[m] = 52; r[m + 2] = 40; r[m + 3] = 20; r[m + 4] = 255.toByte(); r[m + 5] = 25
        // Charmander: evolves at 16 (type 1, 3 bytes), then Ember... at 9, 15, 22.
        learnset(r, map.movesets, 0xB1 - 1, 0x7E00, listOf(1, 16, 0xB0, 0, 9, 52, 15, 43, 22, 99, 0))
        // Dex 4's own slot (internal 0x04 - 1) holds a different list: reading by dex number would find it.
        learnset(r, map.movesets, 4 - 1, 0x7F00, listOf(2, 0x21, 1, 0x26, 0, 30, 33, 40, 34, 0))
        return r
    }

    private fun redWram(level: Int): Wram {
        val map = Gen1Map.RED_BLUE
        val w = Wram(0x2000)
        w.put(map.partyCount, 1); w.put(map.partySpecies, 0xB1); w.put(map.partySpecies + 1, 0xFF)
        val p = map.partyMons
        w.put(p, 0xB1); w.be16(p + 1, 30); w.put(p + 8, 52); w.put(p + 29, 20)
        w.put(p + 33, level); w.be16(p + 34, 40)
        return w
    }

    @Test
    fun `Gen 1 learn levels come from the ROM's pointer table, by internal id, past the evolutions`() {
        val t = Gen1Tracker(redWram(12), redRom())
        assertEquals(listOf(9, 15, 22), t.learnLevels(4))
        // Internal 0x04 (Kadabra here): an item evolution is four bytes long.
        assertEquals(listOf(30, 40), t.learnLevels(64))
        // The panel's header on your lead: "Moves 1/3 (15)" at Lv.12.
        val lead = t.read().party.single()
        assertEquals(Triple(1, 3, 15), Triple(lead.movesLearned, lead.movesTotal, lead.nextMoveLevel))
        assertEquals(emptyList(), t.learnLevels(151), "a dex number no internal id maps to has no list")
    }

    @Test
    fun `Gen 1 summaries, weight and evolution are the Gen 1 reference's, and its chart makes Ghost miss Psychic`() {
        val t = Gen1Tracker(redWram(12), redRom())
        assertEquals("30% chance to make the target flinch. .", t.moveDescription(67), "MoveData.lua Low Kick, Gen 1's")
        assertNull(t.moveDescription(166), "past Gen 1's 165 moves")
        assertEquals("8.5", t.weight(4)); assertEquals("16", t.evolution(4))
        assertNull(t.evolution(6), "Charizard does not evolve")
        assertEquals("37", t.evolution(64), "Kadabra's level, without the table's Lua comment")
        assertEquals("EEVEE_STONES", t.evolution(133)); assertEquals("THUNDER", t.evolution(25))
        assertNull(t.weight(152), "Gen 1 stops at 151")
        // Kadabra, typed Psychic in this ROM: in the Gen 1 chart Ghost does nothing to it and Bug is 2x.
        val kadabra = t.effectivenessAgainst(64)
        assertTrue("Ghost" in kadabra.getValue(0.0), kadabra.toString())
        assertTrue("Bug" in kadabra.getValue(2.0), kadabra.toString())
        assertEquals(1, t.generation)
    }

    // ------------------------------------------------------------------ Gen 2

    /** Crystal: Cyndaquil (Fire) and Abra (Psychic), Cyndaquil's learnset with Lv.1 moves and an EVOLVE_STAT entry. */
    private fun crystalRom(): ByteArray {
        val r = ByteArray(0x60000)
        header(r, "PM_CRYSTAL", 0xC0)
        fun base(species: Int, t1: Int, t2: Int) {
            val b = GbcTracker.BASE_STATS + (species - 1) * GbcTracker.BASE_STRIDE
            r[b] = species.toByte(); r[b + 1] = 39; r[b + 7] = t1.toByte(); r[b + 8] = t2.toByte()
        }
        base(155, 20, 20); base(63, 24, 24)
        val m = GbcTracker.MOVES + (52 - 1) * GbcTracker.MOVE_STRIDE
        r[m + 2] = 40; r[m + 3] = 20; r[m + 4] = 255.toByte(); r[m + 5] = 25
        // Cyndaquil: an EVOLVE_STAT entry (4 bytes) then a level one, then two Lv.1 moves and three learned.
        learnset(r, GbcTracker.EVOS_ATTACKS, 155 - 1, 0x7000, listOf(5, 20, 1, 237, 1, 14, 156, 0, 1, 33, 1, 43, 6, 108, 12, 52, 19, 98, 0))
        return r
    }

    private fun crystalWram(level: Int): Wram {
        val w = Wram(0x8000)
        w.put(GbcTracker.PARTY_COUNT, 1); w.put(GbcTracker.PARTY_SPECIES, 155); w.put(GbcTracker.PARTY_SPECIES + 1, 0xFF)
        val b = GbcTracker.PARTY_MONS
        w.put(b, 155); w.put(b + 2, 52); w.put(b + 23, 20); w.put(b + 31, level); w.be16(b + 34, 30); w.be16(b + 36, 40)
        return w
    }

    @Test
    fun `Gen 2 learn levels skip the evolutions and the Lv 1 moves, as the reference's updatemoves does`() {
        val t = GbcTracker(crystalWram(12), crystalRom())
        assertEquals(listOf(6, 12, 19), t.learnLevels(155))
        val lead = t.read().party.single()
        assertEquals(Triple(2, 3, 19), Triple(lead.movesLearned, lead.movesTotal, lead.nextMoveLevel))
        assertEquals(emptyList(), t.learnLevels(252))
    }

    @Test
    fun `Gen 2 summaries, weight and evolution are the Gen 2 reference's, on Gen 3's chart`() {
        val t = GbcTracker(crystalWram(12), crystalRom())
        assertEquals("7.9", t.weight(155)); assertEquals("14", t.evolution(155))
        assertEquals("FRIEND", t.evolution(42), "Golbat to Crobat")
        assertEquals("SUN", t.evolution(191), "Sunkern")
        assertTrue(t.moveDescription(251)!!.contains("Pokémon"), "Constants.Words.POKEMON spelled out")
        // Abra, Psychic: Gen 2's chart is Gen 3's, so Ghost is 2x and Dark is 2x.
        val abra = t.effectivenessAgainst(63)
        assertTrue("Ghost" in abra.getValue(2.0) && "Dark" in abra.getValue(2.0), abra.toString())
        assertEquals(2, t.generation)
    }

    /**
     * The fixtures above write their learnsets through the maps' own offsets,
     * so they cannot catch a wrong offset. These are the randomizer's own
     * (PokemonMovesetsTableOffset in the engine's gen1_offsets.ini and
     * gen2_offsets.ini), read from the files it randomizes with.
     */
    @Test
    fun `the learnset tables are where the randomizer's offsets put them`() {
        fun offset(ini: String, section: String): Int? {
            val f = File("../engine-zx/src/com/dabomstew/pkrandomzx/config/$ini").takeIf { it.isFile } ?: return null
            val lines = f.readLines()
            val start = lines.indexOfFirst { it.trim() == "[$section]" }
            return lines.drop(start + 1).takeWhile { !it.startsWith("[") }
                .firstOrNull { it.startsWith("PokemonMovesetsTableOffset=") }?.substringAfter("=0x")?.trim()?.toInt(16)
        }
        assertEquals(offset("gen1_offsets.ini", "Red (U)") ?: return, Gen1Map.RED_BLUE.movesets)
        assertEquals(offset("gen1_offsets.ini", "Yellow (U)"), Gen1Map.YELLOW.movesets)
        assertEquals(offset("gen2_offsets.ini", "Crystal (U)"), GbcTracker.EVOS_ATTACKS)
        assertEquals(offset("gen2_offsets.ini", "Gold (U)"), Gen2Map.GS.evosAttacks)
    }

    // ------------------------------------------------------------------ real dumps

    private fun dump(name: String): ByteArray? = Dumps.rom(name)?.readBytes()

    private val none = MemoryReader { _, _ -> ByteArray(0) }

    @Test
    fun `real dumps give the games' own learn levels`() {
        dump("red-u.gbc")?.let { rom ->
            val t = Gen1Tracker(none, rom)
            assertEquals(listOf(5, 12, 19, 28, 36, 44), t.learnLevels(16), "Pidgey: Sand-Attack at 5, Quick Attack at 12 ...")
            assertEquals(listOf(7, 13, 20, 27, 34, 41, 48), t.learnLevels(1), "Bulbasaur")
            assertEquals(emptyList(), t.learnLevels(10), "Caterpie learns nothing by level")
            assertTrue((1..151).all { sp -> t.learnLevels(sp).all { it in 2..100 } })
            // The power the Gen 1 reference shows for Low Kick (it reads Gen 1 move powers from the ROM).
            val lowKick = t.moveRowFor(67)!!
            assertEquals(50 to 90, lowKick.power to lowKick.acc, "Red's own Low Kick")
            // Blizzard at 90%: what makes the Gen 1 reference call every ROM randomized (MoveData.lua:178).
            assertEquals(90, t.moveRowFor(59)!!.acc)
        }
        dump("yellow-u.gbc")?.let { rom ->
            val t = Gen1Tracker(none, rom)
            assertEquals(listOf(6, 8, 11, 15, 20, 26, 33, 41, 50), t.learnLevels(25), "Pikachu")
            assertEquals(listOf(7, 13, 20, 27, 34, 41, 48), t.learnLevels(1), "Bulbasaur, once each")
        }
        dump("crystal-u.gbc")?.let { rom ->
            val t = GbcTracker(none, rom)
            assertEquals(listOf(6, 12, 19, 27, 36, 46), t.learnLevels(155), "Cyndaquil")
            assertEquals(listOf(8, 12, 15, 22, 29, 36, 43, 50), t.learnLevels(152), "Chikorita")
            // The row MoveRules.basePower(67, power, 2) shows: the Gen 2 reference's 50 and 90 (MoveData.lua:938-943).
            val lowKick = t.moveRowFor(67)!!
            assertEquals(50 to 90, lowKick.power to lowKick.acc, "Crystal's own Low Kick")
            assertTrue((1..251).all { sp -> t.learnLevels(sp).all { it in 2..100 } })
        }
        dump("gold-u.gbc")?.let { rom ->
            assertEquals(listOf(6, 12, 19, 27, 36, 46), GbcTracker(none, rom).learnLevels(155), "Cyndaquil at Gold's table")
        }
    }
}
