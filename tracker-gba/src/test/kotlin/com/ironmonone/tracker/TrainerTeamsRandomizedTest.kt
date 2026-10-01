package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * TrainerData.checkIfDataIsRandomized's teamPokemon (TrainerData.lua:147-259): the first two gym
 * leaders' Pokemon against their vanilla species. Trainer Info shows a team before the fight only
 * when this is false (TrainerData.canShowUnknownTrainerTeams).
 */
class TrainerTeamsRandomizedTest {
    private val G = 0x08300000L; private val PARTY = 0x08320000L

    /** Brock (414) and Misty (415) in the default-moves layout, with the given species in order. */
    private fun frlg(brock: List<Int>, misty: List<Int>): GbaTracker {
        val rom = HashMap<Long, Byte>()
        fun put(a: Long, vararg b: Int) { b.forEachIndexed { i, v -> rom[a + i] = v.toByte() } }
        listOf(414 to brock, 415 to misty).forEachIndexed { n, (id, species) ->
            val t = G + id * 0x28L
            val party = PARTY + n * 0x100L
            put(t + 0x20, species.size)
            put(t + 0x24, (party and 0xFF).toInt(), ((party shr 8) and 0xFF).toInt(), ((party shr 16) and 0xFF).toInt(), 0x08)
            species.forEachIndexed { i, sp -> put(party + i * 8L, 0, 0, 12, 0, sp and 0xFF, sp shr 8, 0, 0) }
        }
        val map = GameMap.FIRERED_U_V10.copy(gTrainers = G)
        return GbaTracker(MemoryReader { a, n -> ByteArray(n) { rom[a + it] ?: 0 } }, map)
    }

    @Test
    fun `Brock and Misty's vanilla teams read as unrandomized, any other species as randomized`() {
        assertEquals(false, frlg(listOf(74, 95), listOf(120, 121)).trainerTeamsRandomized())
        assertEquals(true, frlg(listOf(74, 95), listOf(120, 1)).trainerTeamsRandomized(), "Misty's Starmie replaced")
        assertEquals(true, frlg(listOf(152), listOf(120, 121)).trainerTeamsRandomized(), "Brock's Geodude replaced")
        // A slot the leader does not have is not a difference, as `party[2] and ...` skips it.
        assertEquals(false, frlg(listOf(74), listOf(120)).trainerTeamsRandomized())
    }

    @Test
    fun `nothing readable is unknown, which callers treat as randomized`() {
        assertNull(frlg(emptyList(), emptyList()).trainerTeamsRandomized())
        assertNull(GbaTracker(MemoryReader { _, n -> ByteArray(n) }, GameMap.FIRERED_U_V10.copy(gTrainers = 0L)).trainerTeamsRandomized())
    }

    /** A reader over a ROM file alone, as RomDataTest reads it. */
    private fun romReader(f: File): MemoryReader {
        val rom = f.readBytes()
        return MemoryReader { addr, len ->
            val at = addr - 0x08000000L
            if (at < 0 || at >= rom.size) ByteArray(len)
            else ByteArray(len) { i -> if (at + i < rom.size) rom[(at + i).toInt()] else 0 }
        }
    }

    @Test
    fun `Blake's vanilla dumps all read as unrandomized teams`() {
        val dir = System.getenv("IRONMON_ROMS") ?: return
        val games = listOf("ruby-u.gba" to GameMap.RUBY_U, "sapphire-u.gba" to GameMap.SAPPHIRE_U, "emerald-u.gba" to GameMap.EMERALD_U,
            "firered-u-v10.gba" to GameMap.FIRERED_U_V10, "leafgreen-u.gba" to GameMap.LEAFGREEN_U)
        for ((file, map) in games) {
            val f = File(dir, file).takeIf { it.isFile } ?: continue
            assertEquals(false, GbaTracker(romReader(f), map).trainerTeamsRandomized(), file)
        }
    }
}
