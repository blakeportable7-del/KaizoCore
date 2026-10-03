package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.LevelCapTable
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Game Boy trainer tables read out of a ROM (2026-09-30). The synthetic ROMs pin the format the way pokered and
 * pokecrystal write it; the real dumps prove that the level cap tables (nuzlocke/levelcaps-gen1.tsv and -gen2.tsv,
 * computed from the disassemblies) and the tracker's reading agree number for number, on every game whose dump is on
 * this machine. A game whose dump is absent is skipped, with a line saying so.
 */
class GbTrainerRomTest {

    // ---------------------------------------------------------------- the format, on made-up ROMs

    /** A table in bank 0x0E at 0x39D3B, class 1's pointer first: [teams] is where class 1's teams start, in the bank. */
    private fun romWith(table: Int, at: Int, vararg bytes: Int): ByteArray {
        val r = ByteArray(0x50000)
        val bank = (table / 0x4000) * 0x4000
        val p = 0x4000 + (at - bank)
        r[table] = (p and 0xFF).toByte(); r[table + 1] = (p shr 8).toByte()
        bytes.forEachIndexed { i, b -> r[at + i] = b.toByte() }
        return r
    }

    @Test
    fun `Generation 1 teams are read one after the other, each ended by a 0 byte`() {
        val table = 0x39D3B
        val rom = romWith(
            table, 0x3A000,
            0xFF, 5, 1, 9, 2, 7, 3, 0,          // team 1: the pairs of a mixed team, top level 9
            12, 3, 0,                           // team 2: one level for the whole team
            0xFF, 20, 4, 18, 5, 0,              // team 3
        )
        assertEquals(9, GbTrainerRom.gen1MaxLevel(rom, table, 1, 1), "the top level of the pairs, not the last")
        assertEquals(12, GbTrainerRom.gen1MaxLevel(rom, table, 1, 2))
        assertEquals(20, GbTrainerRom.gen1MaxLevel(rom, table, 1, 3), "the teams before it are skipped, however long they are")
        assertNull(GbTrainerRom.gen1MaxLevel(rom, table, 1, 4), "past the last team there is nothing")
        assertNull(GbTrainerRom.gen1MaxLevel(rom, table, 1, 0), "party numbers start at 1")
        assertNull(GbTrainerRom.gen1MaxLevel(rom, table, 2, 1), "a class with no pointer")
        assertNull(GbTrainerRom.gen1MaxLevel(rom, table, 0, 1))
        assertNull(GbTrainerRom.gen1MaxLevel(ByteArray(16), table, 1, 1), "a ROM too small to hold the table")
    }

    @Test
    fun `a level that is not a level is not a team`() {
        val table = 0x39D3B
        assertNull(GbTrainerRom.gen1MaxLevel(romWith(table, 0x3A000, 0xFF, 101, 4, 0), table, 1, 1), "over 100")
        assertNull(GbTrainerRom.gen1MaxLevel(romWith(table, 0x3A000, 0xFF, 0), table, 1, 1), "an empty team")
        assertNull(GbTrainerRom.gen1MaxLevel(romWith(table, 0x3A000, 5, 0), table, 1, 1), "a level and no species")
    }

    @Test
    fun `Generation 2 teams have a name, a type and Pokemon of the size that type says`() {
        val table = 0x39999
        val name = intArrayOf(0x85, 0x80, 0x8B, 0x82, 0x50)      // FALC and the terminator
        fun team(type: Int, vararg mons: IntArray): IntArray = name + intArrayOf(type) + mons.flatMap { it.toList() } + 0xFF
        val bytes = team(0, intArrayOf(5, 16), intArrayOf(9, 17)) +                                    // level, species
            team(1, intArrayOf(12, 16, 1, 2, 3, 4)) +                                                   // and four moves
            team(2, intArrayOf(15, 16, 99), intArrayOf(11, 17, 99)) +                                   // and an item
            team(3, intArrayOf(30, 16, 99, 1, 2, 3, 4), intArrayOf(22, 17, 99, 1, 2, 3, 4))             // an item and four moves
        val rom = romWith(table, 0x3A800, *bytes)
        assertEquals(9, GbTrainerRom.gen2MaxLevel(rom, table, 1, 1))
        assertEquals(12, GbTrainerRom.gen2MaxLevel(rom, table, 1, 2))
        assertEquals(15, GbTrainerRom.gen2MaxLevel(rom, table, 1, 3))
        assertEquals(30, GbTrainerRom.gen2MaxLevel(rom, table, 1, 4))
        assertNull(GbTrainerRom.gen2MaxLevel(rom, table, 1, 5), "past the last team")
        assertNull(GbTrainerRom.gen2MaxLevel(rom, table, 1, 0))
        assertNull(GbTrainerRom.gen2MaxLevel(rom, table, 3, 1), "a class with no pointer")
        val bad = romWith(table, 0x3A800, *(name + intArrayOf(7, 5, 16, 0xFF)))
        assertNull(GbTrainerRom.gen2MaxLevel(bad, table, 1, 1), "a type the game does not have")
    }

    // ---------------------------------------------------------------- the real games

    private val roms: File = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory } ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")

    private fun rom(file: String): ByteArray? {
        val f = Dumps.file(roms, file)
        if (f == null) { println("SKIP: $file is not on this machine"); return null }
        return f.readBytes()
    }

    private fun checkGen1(file: String, table: Int, game: String) {
        val rom = rom(file) ?: return
        val caps = LevelCapTable.standard(game, NuzlockeSystem.GEN1)
        assertEquals(13, caps.bosses.size, game)
        val differ = ArrayList<String>()
        for (b in caps.bosses) {
            val levels = b.trainerIds.map { GbTrainerRom.gen1MaxLevel(rom, table, it shr 8, it and 0xFF) }
            assertTrue(levels.all { it != null }, "$file ${b.key}: a team could not be read: $levels")
            val level = levels.filterNotNull().max()
            if (level != b.cap) differ += "${b.key} ${b.label}: table ${b.cap}, game $level"
        }
        assertTrue(differ.isEmpty(), "$file: " + differ.joinToString("; "))
        println("$file: all ${caps.bosses.size} caps read from the ROM match the table")
    }

    @Test fun `Red's boss levels in the game are the table's`() = checkGen1("red-u.gbc", Gen1Map.RED_BLUE.trainerTable, "rb")
    @Test fun `Blue's boss levels in the game are the table's`() = checkGen1("blue-u.gbc", Gen1Map.RED_BLUE.trainerTable, "rb")
    @Test fun `Yellow's boss levels in the game are the table's`() = checkGen1("yellow-u.gbc", Gen1Map.YELLOW.trainerTable, "y")

    @Test
    fun `Crystal's boss levels in the game are the table's`() {
        val rom = rom("crystal-u.gbc") ?: return
        val caps = LevelCapTable.standard("c", NuzlockeSystem.GEN2)
        assertEquals(22, caps.bosses.size)
        val differ = ArrayList<String>()
        for (b in caps.bosses) {
            val levels = b.trainerIds.map { GbTrainerRom.gen2MaxLevel(rom, Gen2Map.CRYSTAL.trainerTable, it shr 8, it and 0xFF) }
            assertTrue(levels.all { it != null }, "${b.key}: a team could not be read: $levels")
            val level = levels.filterNotNull().max()
            if (level != b.cap) differ += "${b.key} ${b.label}: table ${b.cap}, game $level"
        }
        assertTrue(differ.isEmpty(), "Crystal: " + differ.joinToString("; "))
        println("Crystal: all ${caps.bosses.size} caps read from the ROM match the table")
    }

    /** The whole way through the tracker: the ROM in, a table out that is the game's own for every boss. */
    private fun trackerCaps(file: String, make: (ByteArray) -> TrackerState): LevelCapTable? {
        val rom = rom(file) ?: return null
        return make(rom).nuz?.gb?.caps
    }

    /** Zeros over the core's work RAM, 8 KB for Gen 1 and 32 KB for Gen 2 (rc32 audit P3 #117). */
    private class Blank(private val base: Long, private val size: Int) : MemoryReader {
        override fun read(address: Long, length: Int): ByteArray = if (address >= base && address - base + length <= size) ByteArray(length) else ByteArray(0)
    }

    @Test
    fun `the Generation 1 tracker reads every boss's level out of the ROM`() {
        for ((file, game) in listOf("red-u.gbc" to "rb", "blue-u.gbc" to "rb", "yellow-u.gbc" to "y")) {
            val caps = trackerCaps(file) { Gen1Tracker(Blank(Gen1Tracker.RAM, 0x2000), it).read() } ?: continue
            assertTrue(caps.fromRom, "$file: some team did not read: " + caps.bosses.filter { !it.fromRom }.map { it.key })
            val table = LevelCapTable.standard(game, NuzlockeSystem.GEN1)
            assertEquals(table.bosses.map { it.cap }, caps.bosses.map { it.cap }, file)
        }
    }

    @Test
    fun `the Crystal tracker reads every boss's level out of the ROM`() {
        val caps = trackerCaps("crystal-u.gbc") { GbcTracker(Blank(GbcTracker.RAM, 0x8000), it).read() } ?: return
        assertTrue(caps.fromRom, "some team did not read: " + caps.bosses.filter { !it.fromRom }.map { it.key })
        assertEquals(LevelCapTable.standard("c", NuzlockeSystem.GEN2).bosses.map { it.cap }, caps.bosses.map { it.cap })
    }

    // ---------------------------------------------------------------- the addresses, against the game's own code

    /**
     * Every WRAM address the Game Boy Nuzlocke reads is one the game's own code names: a load, a store or an address
     * loaded into a register (`ld a,[nn]`, `ld [nn],a`, `ld hl,nn`, `ld de,nn`, `ld bc,nn`). It does not prove an address
     * is the right variable, but an address the ROM never mentions is a wrong one, and every address here was worked out
     * by hand from the disassembly. Skipped for a game whose dump is absent.
     */
    private fun references(rom: ByteArray, address: Int): Int {
        val lo = (address and 0xFF).toByte(); val hi = (address shr 8).toByte()
        var n = 0
        for (i in 0 until rom.size - 2) {
            if (rom[i + 1] != lo || rom[i + 2] != hi) continue
            when (rom[i].toInt() and 0xFF) { 0xFA, 0xEA, 0x21, 0x11, 0x01 -> n++ }
        }
        return n
    }

    private fun checkAddresses(file: String, names: Map<String, Long>) {
        val rom = rom(file) ?: return
        val unused = names.filter { (_, off) -> references(rom, (0xC000 + off).toInt()) < 2 }.keys
        assertTrue(unused.isEmpty(), "$file: the game's code does not mention " + unused.joinToString())
    }

    private fun gen1Addresses(m: Gen1Map) = mapOf(
        "wPlayerID" to m.playerId, "wBattleResult" to m.battleResult, "wEscapedFromBattle" to m.escaped, "wCapturedMonSpecies" to m.captured,
        "wOptions" to m.options, "wTrainerClass" to m.trainerClass, "wTrainerNo" to m.trainerNo, "wBattleType" to m.battleType,
        "wWalkBikeSurfState" to m.surfState, "wEnemyMonDVs" to m.enemyDvs, "wPartyMonNicks" to m.nicks,
    )

    @Test fun `Red's Nuzlocke addresses are all named in its code`() = checkAddresses("red-u.gbc", gen1Addresses(Gen1Map.RED_BLUE))
    @Test fun `Blue's Nuzlocke addresses are all named in its code`() = checkAddresses("blue-u.gbc", gen1Addresses(Gen1Map.RED_BLUE))
    @Test fun `Yellow's Nuzlocke addresses are all named in its code`() = checkAddresses("yellow-u.gbc", gen1Addresses(Gen1Map.YELLOW))

    @Test
    fun `Crystal's Nuzlocke addresses are all named in its code`() {
        val m = Gen2Map.CRYSTAL
        checkAddresses(
            "crystal-u.gbc",
            mapOf(
                "wPlayerID" to m.playerId, "wBattleResult" to m.battleResult, "wBattleType" to m.battleType, "wOtherTrainerClass" to m.trainerClass,
                "wOtherTrainerID" to m.trainerNo, "wOptions" to m.options, "wPlayerState" to m.playerState, "wNumBalls" to m.numBalls,
                "wBalls" to m.balls, "wPartyMonNicknames" to m.nicks, "wEnemyMonDVs" to m.enemyDvs, "wWildMon" to m.wildMon,
            ),
        )
    }

    @Test
    fun `a wrong address is caught by that check`() {
        val rom = rom("red-u.gbc") ?: return
        // Addresses in the game's RAM that its code never mentions: the check counts them as unused, so it can fail.
        val unnamed = listOf(0xDFF7, 0xC123, 0xD3A0).map { references(rom, it) }
        assertTrue(unnamed.all { it < 2 }, "counts $unnamed")
        assertTrue(references(rom, 0xD359) >= 2, "Red's wPlayerID is named")
    }

    // ---------------------------------------------------------------- Crystal's gender ratios come out of its ROM

    @Test
    fun `Crystal's ratio byte is read from the ROM's base data for each species`() {
        val rom = rom("crystal-u.gbc") ?: return
        val party = listOf(155 to 31, 25 to 127, 100 to 255, 30 to 254, 33 to 0, 190 to 127)   // Cyndaquil, Pikachu, Voltorb, Nidorina, Nidorino, Aipom
        val w = object : MemoryReader {
            val bytes = ByteArray(0x8000)
            init {
                fun put(off: Long, v: Int) { bytes[off.toInt()] = v.toByte() }
                put(GbcTracker.PARTY_COUNT, party.size)
                party.forEachIndexed { slot, (species, _) ->
                    val b = GbcTracker.PARTY_MONS + slot * GbcTracker.PARTY_STRIDE
                    put(GbcTracker.PARTY_SPECIES + slot, species); put(b, species)
                    put(b + 31, 10); put(b + 34, 0); put(b + 35, 20); put(b + 36, 0); put(b + 37, 20)
                }
                put(GbcTracker.PARTY_SPECIES + party.size, 0xFF)
            }
            override fun read(address: Long, length: Int): ByteArray {
                val o = (address - GbcTracker.RAM).toInt()
                return if (o >= 0 && o + length <= bytes.size) bytes.copyOfRange(o, o + length) else ByteArray(0)
            }
        }
        val s = GbcTracker(w, rom).read()
        assertEquals(party.size, s.party.size)
        assertEquals(party.map { it.second }, s.party.map { assertNotNull(it.base).genderRatio }, "species ${party.map { it.first }}")
    }
}
