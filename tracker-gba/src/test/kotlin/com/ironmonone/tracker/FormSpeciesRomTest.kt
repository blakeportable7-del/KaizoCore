package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Alternate forms on the GBA expansions (2026-10-06, the DS Sandy Cloak Wormadam report's check across the trackers). The
 * Nat. Dex builds, MaxDex and Heart & Soul give every form its own species id, so a Pokemon in a form carries that id and
 * the card reads that id's row of the ROM's base stats: Wormadam's Sandy Cloak is its own row, Bug/Ground. Proved here on
 * the dumps where they lie; a dump not on this machine skips its part with a line saying so.
 */
class FormSpeciesRomTest {

    private val roms: File = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory } ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")

    private fun romOnly(bytes: ByteArray) = MemoryReader { address, length ->
        val off = (address - 0x08000000L).toInt()
        if (address >= 0x08000000L && off >= 0 && off + length <= bytes.size) bytes.copyOfRange(off, off + length) else ByteArray(length)
    }

    private fun names(path: String): Map<String, Int> =
        javaClass.getResourceAsStream(path)!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }.associate { it[1] to it[0].toInt() }

    private fun BaseStats.types() = Gen3Types.name(type1).uppercase() + "/" + Gen3Types.name(type2).uppercase()

    @Test
    fun `the Nat Dex builds and MaxDex read each form's own row`() {
        for ((rom, list) in listOf("firered-natdex-121.gba" to "/natdex/species.tsv", "emerald-natdex-121.gba" to "/natdex/species.tsv",
            "firered-maxdex.gba" to "/maxdex/species.tsv")) {
            val f = Dumps.file(roms, rom) ?: run { println("SKIP: $rom is not on this machine"); null } ?: continue
            val mem = romOnly(f.readBytes())
            val t = GbaTracker(mem, assertNotNull(GameMap.resolveOrNull(mem), rom))
            val id = names(list)
            fun types(name: String) = assertNotNull(t.baseStats(id.getValue(name)), "$rom $name").types()
            assertEquals("BUG/GRASS", types("Wormadam"), rom)
            assertEquals("BUG/GROUND", types("Wormadam-S"), rom)
            assertEquals("BUG/STEEL", types("Wormadam-T"), rom)
            assertEquals("ELECTRIC/FIRE", types("Rotom-Heat"), rom)
            assertEquals("GRASS/FLYING", types("Shaymin-S"), rom)
            assertEquals("FIRE/FIRE", types("Castform-F"), rom)
            assertEquals("FIRE/PSYCHIC", types("Darmanitan-Z"), rom)
            // Every named form ("Name-X") whose row is not its first form's, from the ROM: what the card shows apart.
            val differing = id.filterKeys { '-' in it }.mapNotNull { (name, sp) ->
                val first = id[name.substringBefore('-')] ?: return@mapNotNull null
                val a = t.baseStats(sp) ?: return@mapNotNull null
                val b = t.baseStats(first) ?: return@mapNotNull null
                name.takeIf { a.types() != b.types() || a.bst != b.bst || a.ability1 != b.ability1 || a.ability2 != b.ability2 }
            }
            assertTrue(differing.size > 100, "$rom: ${differing.size}")
            println("FORMS $rom: ${differing.size} form species with rows of their own: ${differing.sorted()}")
        }
    }

    @Test
    fun `Heart and Soul reads each form's own row`() {
        val f = Dumps.file(HnsTrackerTest.romDir(), "hns-kaizo.gba") ?: run { println("SKIP: hns-kaizo.gba is not on this machine"); return }
        val bytes = f.readBytes()
        val ewram = ByteArray(0x40000); val iwram = ByteArray(0x8000)
        val mem = MemoryReader { a, n ->
            val (b, o) = when (a) {
                in 0x08000000L..0x09FFFFFFL -> bytes to (a - 0x08000000L).toInt()
                in 0x02000000L..0x0203FFFFL -> ewram to (a - 0x02000000L).toInt()
                in 0x03000000L..0x03007FFFL -> iwram to (a - 0x03000000L).toInt()
                else -> return@MemoryReader ByteArray(0)
            }
            if (o < 0 || o >= b.size) ByteArray(0) else b.copyOfRange(o, minOf(b.size, o + n))
        }
        val t = GbaTracker(mem, GameMap.resolve(mem))
        val hns = HnsData(mem)
        // Wormadam's three cloaks: three ids with national number 413, each its own types.
        val wormadam = (1..HnsLayout.NUM_SPECIES).filter { hns.natDexNum(it) == 413 }
        assertEquals(listOf("BUG/GRASS", "BUG/GROUND", "BUG/STEEL"), wormadam.map { assertNotNull(t.baseStats(it)).types() })
        // Every id sharing a national number with a lower one whose row differs from it: the forms, from the ROM.
        val byNum = (1..HnsLayout.NUM_SPECIES).groupBy { hns.natDexNum(it) }.filterKeys { it > 0 }
        val differing = byNum.values.flatMap { ids ->
            val first = t.baseStats(ids.first()) ?: return@flatMap emptyList()
            ids.drop(1).filter { sp ->
                val b = t.baseStats(sp) ?: return@filter false
                b.types() != first.types() || b.bst != first.bst || b.ability1 != first.ability1 || b.ability2 != first.ability2 || b.ability3 != first.ability3
            }
        }
        assertTrue(differing.size > 100, "${differing.size}")
        println("FORMS hns-kaizo.gba: ${differing.size} form ids with rows of their own: " + differing.joinToString { "$it ${hns.speciesName(it)}" })
    }
}
