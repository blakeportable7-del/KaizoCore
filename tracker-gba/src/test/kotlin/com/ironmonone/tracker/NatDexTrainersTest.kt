package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Nat. Dex trainer tables. Every build was read through the Nat. Dex 1.1.3
 * addresses (gTrainers_NatDex_113) and the vanilla 40-byte layout, so on the
 * 1.2.1 ROMs every trainer screen showed garbage. The reference uses the 1.1.3
 * values only for 1.1.3 or older (CustomCode.lua:543-554) and otherwise the
 * ROM's own pointers (0x08000314, 0x08000310) and layout (0x0800042E,
 * 0x08000494-0x080004B6): a 44-byte entry with 16-byte names.
 */
class NatDexTrainersTest {
    private val fr = File("C:/Users/bepor/IronMonOne/.vendor/roms/firered-natdex-121.gba")
    private val em = File("C:/Users/bepor/IronMonOne/.vendor/roms/emerald-natdex-121.gba")

    private fun reader(bytes: ByteArray) = MemoryReader { a, n ->
        val off = (a - 0x08000000L).toInt()
        if (a >= 0x08000000L && off >= 0 && off + n <= bytes.size) bytes.copyOfRange(off, off + n) else ByteArray(n)
    }

    private fun tracker(bytes: ByteArray): GbaTracker {
        val mem = reader(bytes)
        return GbaTracker(mem, GameMap.resolve(mem))
    }

    @Test
    fun `FireRed Nat Dex 1_2_1 reads its trainers through its own slots and layout`() {
        if (!fr.exists()) { println("SKIP: Nat. Dex FireRed ROM missing"); return }
        val t = tracker(fr.readBytes())
        val m = t.map
        assertEquals(0x08211D68L, m.gTrainers)
        assertEquals(0x082116B8L, m.gTrainerClassNames)
        val l = m.trainerLayout
        assertEquals(listOf(0x2C, 16, 16), listOf(l.size, l.nameSize, l.classNameSize))
        assertEquals(listOf(0x14, 0x1C, 0x20, 0x24, 0x28), listOf(l.itemsOffset, l.doubleOffset, l.aiOffset, l.partySizeOffset, l.partyPtrOffset))

        val brock = assertNotNull(t.trainer(414))
        assertEquals("Brock", brock.name)
        assertEquals("Gym Leader", brock.className)
        assertEquals(listOf(74 to 12, 95 to 14), brock.party.map { it.species to it.level })
        assertEquals("Smart", brock.aiLabel)
        assertTrue(brock.party.all { it.moves.size == 4 }, "flags 1: a custom moveset each")

        val rick = assertNotNull(t.trainer(102))
        assertEquals("Bug Catcher Rick", "${rick.className} ${rick.name}")
        assertEquals(listOf(13 to 6, 10 to 6), rick.party.map { it.species to it.level })
    }

    @Test
    fun `Emerald Nat Dex 1_2_1 too`() {
        if (!em.exists()) { println("SKIP: Nat. Dex Emerald ROM missing"); return }
        val t = tracker(em.readBytes())
        assertEquals(0x083791C0L, t.map.gTrainers)
        assertEquals(0x0837141CL, t.map.gTrainerClassNames)
        val roxanne = assertNotNull(t.trainer(265))
        assertEquals("Gym Leader Roxanne", "${roxanne.className} ${roxanne.name}")
        assertEquals(listOf(13, 13, 0, 0), roxanne.items)
        assertEquals(listOf(74 to 12, 74 to 12, 320 to 15), roxanne.party.map { it.species to it.level })
        assertEquals(listOf(12, 12, 24), roxanne.party.map { it.ivs }, "iv * 31 / 255")
        // The three May/Brendan starters the RSE starter preview reads (Program.lua:751).
        assertEquals(listOf(277, 280, 283), listOf(520, 523, 526).map { t.trainer(it)?.party?.firstOrNull()?.species })
    }

    @Test
    fun `1_1_3 and older keep the reference's _NatDex_113 addresses and the vanilla layout`() {
        if (!fr.exists()) { println("SKIP: Nat. Dex FireRed ROM missing"); return }
        val bytes = fr.readBytes()
        fun at(major: Int, minor: Int, patch: Int): GameMap {
            val b = bytes.copyOf()
            b[0x48C] = major.toByte(); b[0x48D] = minor.toByte(); b[0x48E] = patch.toByte()
            return GameMap.resolve(reader(b))
        }
        val old = at(1, 1, 3)
        assertEquals(0x0823D818L, old.gTrainers)
        assertEquals(0x0823D2A8L, old.gTrainerClassNames)
        assertEquals(0x0829050CL, old.levelUpLearnsets)
        assertEquals(TrainerLayout.VANILLA, old.trainerLayout)
        // No version at all predates the field: old.
        assertEquals(0x0823D818L, at(0xFF, 0xFF, 0xFF).gTrainers)
        // Anything past 1.1.3 reads the slots.
        assertEquals(0x08211D68L, at(1, 1, 4).gTrainers)
        assertEquals(0x08211D68L, at(1, 2, 0).gTrainers)
    }
}
