package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three starter balls, left to right, read from each Gen 3 ROM (2026-10-01, for the favorite-in-ball note; Blake:
 * "As long as it fits in the rules, I would say do it for the modes that allow favorites"). The species are the clean
 * games' own starters, and the ball each is in is the ROM's own answer (FireRed's lab scripts, Hoenn's starter screen),
 * so a table read in its stored order fails here: FireRed stores Bulbasaur, Charmander, Squirtle.
 */
class StarterBallsTest {
    private val roms = File("C:/Users/bepor/IronMonOne/.vendor/roms")

    private fun bytesOf(name: String): ByteArray? = File(roms, name).takeIf { it.exists() }?.readBytes()

    private fun tracker(bytes: ByteArray): GbaTracker {
        val mem = MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off >= 0 && off + length <= bytes.size) bytes.copyOfRange(off, off + length)
            else ByteArray(0)
        }
        // resolve() is what production runs, the Nat. Dex builds included.
        return GbaTracker(mem, GameMap.resolve(mem))
    }

    private fun balls(bytes: ByteArray) = tracker(bytes).starters().map { it.ball to it.species }

    @Test
    fun `FireRed and LeafGreen stand Bulbasaur, Squirtle and Charmander from the left`() {
        var read = 0
        for (rom in listOf("firered-u-v10.gba", "firered-u-v11.gba", "leafgreen-u.gba", "firered-natdex-121.gba")) {
            val bytes = bytesOf(rom) ?: run { println("SKIP: $rom missing"); null } ?: continue
            assertEquals(listOf("LEFT" to 1, "MIDDLE" to 7, "RIGHT" to 4), balls(bytes), rom)
            read++
        }
        println("Kanto ROMs read: $read")
    }

    @Test
    fun `Ruby, Sapphire and Emerald stand Treecko, Torchic and Mudkip from the left`() {
        var read = 0
        for (rom in listOf("emerald-u.gba", "ruby-u.gba", "sapphire-u.gba", "emerald-natdex-121.gba")) {
            val bytes = bytesOf(rom) ?: run { println("SKIP: $rom missing"); null } ?: continue
            // The Gen 3 build's own ids, which the Nat. Dex builds keep for the first 411.
            assertEquals(listOf("LEFT" to 277, "MIDDLE" to 280, "RIGHT" to 283), balls(bytes), rom)
            read++
        }
        println("Hoenn ROMs read: $read")
    }

    @Test
    fun `bytes that are not the starter code give no balls, never three guesses`() {
        val zeros = MemoryReader { _, n -> ByteArray(n) }
        for (map in listOf(GameMap.EMERALD_U, GameMap.RUBY_U, GameMap.SAPPHIRE_U, GameMap.FIRERED_U_V10, GameMap.FIRERED_U_V11, GameMap.LEAFGREEN_U))
            assertTrue(GbaTracker(zeros, map).starters().isEmpty(), map.name)
        // The real ROMs with the one thing that says which ball is which broken: Emerald's ball places, and the
        // setvar before FireRed's left ball.
        bytesOf("emerald-u.gba")?.let { b ->
            val broken = b.copyOf().also { it[0x5B1DF8 - 12] = 0 }
            assertTrue(tracker(broken).starters().isEmpty(), "Emerald without its ball places")
        }
        bytesOf("firered-u-v10.gba")?.let { b ->
            val broken = b.copyOf().also { it[0x169BB5 - 8] = 0 }
            assertTrue(tracker(broken).starters().isEmpty(), "FireRed without its left ball's setvar")
        }
        // A ball number out of place: two balls claiming the middle.
        bytesOf("firered-u-v10.gba")?.let { b ->
            val broken = b.copyOf().also { it[0x169BB5 - 5] = 1 }
            assertTrue(tracker(broken).starters().isEmpty(), "FireRed with two middle balls")
        }
    }
}
