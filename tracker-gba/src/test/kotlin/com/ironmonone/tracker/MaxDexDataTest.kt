package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MaxDex 1.0's data, converted from Trip's tracker extension (tools/trainer-data/convert_maxdex.py). Counted and
 * spot-checked here, and held against the ROM's own name tables when the patched test ROM is to hand (IRONMON_ROMS
 * holding firered-maxdex.gba, else Blake's vendor folder): a list one place out of step reads as a confident wrong
 * name beside every Pokemon, with nothing to say so.
 */
class MaxDexDataTest {
    private fun tsv(path: String): Map<Int, List<String>> =
        javaClass.getResourceAsStream(path)!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }.associate { it[0].toInt() to it.drop(1) }

    private fun names(path: String): Map<Int, String> = tsv(path).mapValues { it.value[0] }

    private fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    @Test
    fun `species run 1 to 1280, the Legends Z-A megas last, every name its own`() {
        val s = names("/maxdex/species.tsv")
        assertEquals((1..1280).toList(), s.keys.sorted())
        val real = s.filterKeys { it !in 252..276 }.values
        assertEquals(real.size, real.toSet().size, "a name used twice")
        // Ids 1 to 1235 are Nat. Dex 1.2.1's own, Pokemon for Pokemon.
        val natDex = names("/natdex/species.tsv")
        for (i in 1..1235) assertEquals(natDex[i], s[i], "species $i")
        assertEquals("Dragonite-M", s[1236]); assertEquals("Raichu-X", s[1237]); assertEquals("Baxcalibur-M", s[1280])
        // The three the extension names like older megas.
        assertEquals("Absol-Z", s[1246]); assertEquals("Garchomp-Z", s[1248]); assertEquals("Lucario-Z", s[1249])
        assertEquals("Absol-M", s[1085])
    }

    @Test
    fun `moves run 1 to 841 and abilities 1 to 289, in MaxDex's own numbering`() {
        val m = names("/maxdex/moves.tsv")
        assertEquals((1..841).toList(), m.keys.sorted())
        assertEquals("Disarming Voice", m[355]); assertEquals("Roost", m[361]); assertEquals("Freeze-Dry", m[578])
        assertEquals("Psychic Noise", m[839]); assertEquals("Malignant Chain", m[841])
        val a = names("/maxdex/abilities.tsv")
        assertEquals((1..289).toList(), a.keys.sorted())
        assertEquals("Compound Eyes", a[14]); assertEquals("Lightning Rod", a[31]); assertEquals("Tangled Feet", a[78])
        assertEquals("Multiscale", a[129]); assertEquals("As One-SR", a[270]); assertEquals("Poison Puppeteer", a[289])
    }

    @Test
    fun `evolutions, ability scripts and random evolutions cover MaxDex's ids`() {
        val extra = tsv("/gen3/species-extra-maxdex.tsv")
        assertEquals((1..1280).toList(), extra.keys.sorted())
        // 1 to 411 keep species-extra.tsv's own names; past them, the species list's.
        assertEquals(names("/maxdex/species.tsv").filterKeys { it > 411 }, extra.filterKeys { it > 411 }.mapValues { it.value[0] })
        assertEquals("LINKING_CORD", extra[64]!![1], "Kadabra, the extension's own change")
        assertEquals("18", extra[412]!![1], "Turtwig at 18")
        val scripts = javaClass.getResourceAsStream("/gen3/abilityscripts-maxdex.tsv")!!.bufferedReader().readLines().filter { it.isNotBlank() }
        assertEquals(42, scripts.size)
        assertTrue(scripts.all { it.split('\t')[0].toLong(16) in 0x081D0000L..0x081EFFFFL }, "absolute addresses in MaxDex's battle scripts")
        val revos = tsv("/gen3/revos-maxdex.tsv")
        assertTrue(revos.size > 400)
        for ((base, row) in revos) {
            assertTrue(base in 1..1280)
            row[1].split(',').forEach { e -> assertTrue(e.substringBefore(':').toInt() in 1..1280, "$base: $e") }
        }
    }

    private val rom: File? = File(System.getenv("IRONMON_ROMS") ?: "C:/Users/bepor/IronMonOne/.vendor/roms", "firered-maxdex.gba").takeIf { it.isFile }

    /** The ROM's own tables (Game Freak header 0x144, 0x148, 0x1C0), names cut to their fixed lengths. */
    @Test
    fun `the lists name what the MaxDex ROM names, id for id`() {
        val bytes = rom?.readBytes() ?: return println("MaxDexDataTest: firered-maxdex.gba not to hand, ROM check skipped")
        fun u32(o: Int) = (bytes[o].toLong() and 255) or ((bytes[o + 1].toLong() and 255) shl 8) or ((bytes[o + 2].toLong() and 255) shl 16) or ((bytes[o + 3].toLong() and 255) shl 24)
        fun romName(table: Long, stride: Int, id: Int): String {
            val at = (table - 0x08000000L).toInt() + id * stride
            return Gen3Text.decode(bytes.copyOfRange(at, at + stride))
        }
        val species = names("/maxdex/species.tsv")
        var checked = 0
        for (i in 412..1280) {
            val r = romName(u32(0x144), 11, i)
            checked++
            // Six forms the two builds spell apart (MaxDex's "Darmanitan Z G" is Nat. Dex 1.2.1's Darmanitan-GZ): the
            // converter matched each to the extension's own name.
            if (i in setOf(1165, 1166, 1167, 1185, 1198, 1201)) continue
            assertTrue(shortened(r, species[i]!!), "species $i: ROM \"$r\", list \"${species[i]}\"")
        }
        // 1 to 354 keep Gen 3's spellings in the ROM (VICEGRIP, HI JUMP KICK); the list has the extension's.
        val moves = names("/maxdex/moves.tsv")
        for (i in 355..841) {
            val r = romName(u32(0x148), 17, i)
            assertTrue(shortened(r, moves[i]!!), "move $i: ROM \"$r\", list \"${moves[i]}\"")
        }
        val abilities = names("/maxdex/abilities.tsv")
        for (i in 78..289) {
            val r = romName(u32(0x1C0), 17, i)
            assertTrue(shortened(r, abilities[i]!!) && key(r).length >= 4, "ability $i: ROM \"$r\", list \"${abilities[i]}\"")
        }
        assertEquals(869, checked)
    }

    /** The ROM shortens a name to fit its table ("Flechinder", "BaxcalibuM"): the ROM's letters, in order, in the list's, from its first. */
    private fun shortened(rom: String, list: String): Boolean {
        val r = key(rom)
        val l = key(list)
        if (r.isEmpty() || r[0] != l.firstOrNull()) return false
        var j = 0
        for (c in l) if (j < r.length && r[j] == c) j++
        return j == r.length
    }
}
