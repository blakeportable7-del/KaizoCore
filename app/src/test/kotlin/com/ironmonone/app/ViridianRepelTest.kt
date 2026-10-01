package com.ironmonone.app

import com.ironmonone.app.engine.NatDexEngine
import com.ironmonone.app.engine.Randomizers
import com.ironmonone.app.engine.ViridianRepel
import com.ironmonone.core.RomKind
import com.ironmonone.patch.RomIdentity
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Both Game Boy reference trackers write Repel (0x1E) over the Viridian
 * Mart's second item when they start (Main.lua:279-285): ROM 0x2445 in Red
 * and Blue, the Antidote; 0x233E in Yellow, the Potion (Yellow's Mart lists
 * a Potion before the Antidote). The core's ROM is not writable here, so a Gen 1
 * run gets the byte when it is randomized, and nothing else does: the
 * prepared ROM is identified by its CRC and must keep it.
 */
class ViridianRepelTest {
    private val dir = Files.createTempDirectory("repel").toFile()

    /** A header and the Mart's list as the game has it: 0xFE, the count, the items, 0xFF. */
    private fun rom(title: String, list: Int, items: List<Int>): ByteArray {
        val r = ByteArray(0x8000)
        title.forEachIndexed { i, c -> r[0x134 + i] = c.code.toByte() }
        (listOf(0xFE, items.size) + items + 0xFF).forEachIndexed { i, b -> r[list + i] = b.toByte() }
        return r
    }

    private data class Mart(val title: String, val list: Int, val slot: Int, val items: List<Int>)

    @Test
    fun `Red and Blue sell Repel for Antidote, Yellow for Potion, and nothing else changes`() {
        for (m in listOf(
            Mart("POKEMON RED", 0x2442, 0x2445, listOf(0x04, 0x0B, 0x0F, 0x0C)),     // Poke Ball, Antidote, Parlyz Heal, Burn Heal
            Mart("POKEMON BLUE", 0x2442, 0x2445, listOf(0x04, 0x0B, 0x0F, 0x0C)),
            Mart("POKEMON YELLOW", 0x233B, 0x233E, listOf(0x04, 0x14, 0x0B, 0x0F, 0x0C)),   // Poke Ball, Potion, Antidote ...
        )) {
            val r = rom(m.title, m.list, m.items)
            val before = r.copyOf()
            assertTrue(ViridianRepel.apply(r), m.title)
            assertEquals(ViridianRepel.REPEL, r[m.slot].toInt() and 0xFF, m.title)
            assertEquals(listOf(m.slot), r.indices.filter { r[it] != before[it] }, "${m.title}: one byte")
            assertTrue(ViridianRepel.apply(r), "${m.title}: a second run finds it done")
        }
    }

    @Test
    fun `a Mart that is not where the references write it is left alone`() {
        val moved = rom("POKEMON RED", 0x2442, listOf(0x04, 0x14, 0x0F, 0x0C))   // no Antidote in that slot
        val before = moved.copyOf()
        assertFalse(ViridianRepel.apply(moved)); assertContentEquals(before, moved)
        val crystal = rom("PM_CRYSTAL", 0x2442, listOf(0x04, 0x0B, 0x0F, 0x0C))  // the Gen 2 reference skips Crystal
        assertFalse(ViridianRepel.apply(crystal)); assertEquals(0x0B, crystal[0x2445].toInt())
    }

    @Test
    fun `a Gen 1 run gets it after PART 2, and the prepared ROM it came from keeps its bytes`() {
        val prepared = File(dir, "red-u-pf.gbc").apply { writeBytes(rom("POKEMON RED", 0x2442, listOf(0x04, 0x0B, 0x0F, 0x0C))) }
        val kept = prepared.readBytes()
        val part1 = File(dir, "RBY Kaizo.rnqs").apply { writeText("part1") }
        val part2 = File(dir, Randomizers.GEN1_SECOND_PASS).apply { writeText("part2") }
        val dest = File(dir, "current.gbc")
        Randomizers.twoPass(prepared, part1, part2, dest, 7L) { src, _, d, sd -> d.writeBytes(src.readBytes()); NatDexEngine.Outcome(sd, "") }
        assertEquals(ViridianRepel.REPEL, dest.readBytes()[0x2445].toInt())
        assertContentEquals(kept, prepared.readBytes(), "the prepared ROM is identified by its CRC and is never written")
        // A patched build's run is PART 1 alone (Randomizers.gen1), and the Repel is there all the same.
        dest.delete()
        Randomizers.gen1(prepared, part1, null, dest, 7L) { src, _, d, sd -> d.writeBytes(src.readBytes()); NatDexEngine.Outcome(sd, "") }
        assertEquals(ViridianRepel.REPEL, dest.readBytes()[0x2445].toInt())
    }

    /** Real dumps, and the Kaizo builds made from them with the bundled patches. Needs IRONMON_ROMS, as PrepOptionsTest. */
    @Test
    fun `real dumps and their pseudo-fluctuating builds take the Repel at the references' offsets`() {
        val roms = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory } ?: return
        for ((kind, slot, item) in listOf(Triple(RomKind.RED_U, 0x2445, ViridianRepel.ANTIDOTE),
                Triple(RomKind.BLUE_U, 0x2445, ViridianRepel.ANTIDOTE), Triple(RomKind.YELLOW_U, 0x233E, ViridianRepel.POTION))) {
            val clean = File(roms, kind.id + "." + kind.fileExtension).takeIf { it.isFile } ?: continue
            val pf = RomKind.allPatched.first { it.baseId == kind.id && it.patchTag == "pseudofluct" }
            val built = File(dir, pf.id + ".gbc")
            com.ironmonone.patch.Patcher.applyFiles(File("src/main/assets/patches/pseudofluct-${kind.id}.bps"), clean, built)
            assertEquals(pf, RomIdentity.identify(built.readBytes()).kind, "the prepared build is known by its CRC")
            for (f in listOf(clean.copyTo(File(dir, kind.id + ".gbc"), overwrite = true), built)) {
                val before = f.readBytes()
                assertTrue(ViridianRepel.apply(f), f.name)
                val after = f.readBytes()
                assertEquals(listOf(slot), after.indices.filter { after[it] != before[it] }, f.name)
                assertEquals(item to ViridianRepel.REPEL, (before[slot].toInt() and 0xFF) to (after[slot].toInt() and 0xFF), f.name)
            }
        }
    }
}
