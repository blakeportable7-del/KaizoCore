package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.LevelCapTable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The level cap table against the games themselves (2026-09-29). The table in nuzlocke/levelcaps-gen3.tsv is the
 * research's, computed from the pret disassemblies; here the tracker reads every boss's team out of a clean dump
 * and the two must agree, number for number. That is the proof that both the data and the ROM read are right.
 *
 * Skipped for a game whose dump is not on this machine, like the other real-ROM tests.
 */
class NuzlockeRomCapsTest {

    private val roms = File("C:/Users/bepor/IronMonOne/.vendor/roms")

    private fun reader(rom: ByteArray) = MemoryReader { address, length ->
        val off = (address - 0x08000000L).toInt()
        if (address >= 0x08000000L && off >= 0 && off + length <= rom.size) rom.copyOfRange(off, off + length) else ByteArray(0)
    }

    private fun check(file: String, map: GameMap) {
        val f = File(roms, file)
        if (!f.exists()) { println("SKIP: $file is not on this machine"); return }
        val t = GbaTracker(reader(f.readBytes()), map)
        val rom = assertNotNull(t.levelCaps(), "${map.name}: no caps table")
        assertTrue(rom.fromRom, "${map.name}: some boss's team could not be read: " + rom.bosses.filter { !it.fromRom }.map { it.key })
        val table = LevelCapTable.standard(LevelCapTable.gameKey(map.routeVersion)!!)
        assertEquals(table.bosses.map { it.key }, rom.bosses.map { it.key })
        val differ = table.bosses.zip(rom.bosses).filter { (a, b) -> a.cap != b.cap }.map { (a, b) -> "${a.key} ${a.label}: table ${a.cap}, game ${b.cap}" }
        assertTrue(differ.isEmpty(), "${map.name}: " + differ.joinToString("; "))
        println("${map.name}: all ${rom.bosses.size} caps read from the ROM match the table")
    }

    @Test fun `Emerald's boss levels in the game are the table's`() = check("emerald-u.gba", GameMap.EMERALD_U)
    @Test fun `FireRed's boss levels in the game are the table's`() = check("firered-u-v10.gba", GameMap.FIRERED_U_V10)
    @Test fun `LeafGreen's boss levels in the game are the table's`() = check("leafgreen-u.gba", GameMap.LEAFGREEN_U)
    @Test fun `Ruby's boss levels in the game are the table's`() = check("ruby-u.gba", GameMap.RUBY_U)
    @Test fun `Sapphire's boss levels in the game are the table's`() = check("sapphire-u.gba", GameMap.SAPPHIRE_U)
}
