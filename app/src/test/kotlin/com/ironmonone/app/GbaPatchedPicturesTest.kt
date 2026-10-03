package com.ironmonone.app

import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.Gen3Pictures
import com.ironmonone.tracker.MemoryReader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The patched GBA builds the Prep screen makes (Faster FireRed, Faster Emerald 1.3.2 and 1.2.1, the three Smart AI
 * patches) track on their base game's map, so their card reads its shiny and form pictures from the same tables
 * (Gen3Pictures). Each is built here from the dump with its bundled patch, into a temporary file: the tables are where
 * the base game keeps them and every picture reads the same. A base dump that is not here skips its builds.
 */
class GbaPatchedPicturesTest {
    private val roms = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory } ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")

    private fun reader(rom: ByteArray) = MemoryReader { address, length ->
        val o = address - 0x08000000L
        if (o < 0 || o + length > rom.size) ByteArray(0) else rom.copyOfRange(o.toInt(), (o + length).toInt())
    }

    /** Every picture the card can ask for: each species shiny, Unown's letters, Deoxys's form plain and shiny. */
    private fun pictures(m: MemoryReader, t: Gen3Pictures.Tables): List<IntArray?> =
        (1..411).map { Gen3Pictures.picture(m, t, it, shiny = true, personality = 0, gameForm = false)?.argb } +
            (1..27).map { k -> Gen3Pictures.picture(m, t, Gen3Pictures.UNOWN, false, (0L..0xFFFFFFL).first { Gen3Pictures.unownLetter(it) == k }, false)?.argb } +
            listOf(false, true).map { Gen3Pictures.picture(m, t, Gen3Pictures.DEOXYS, it, 0, gameForm = true)?.argb }

    @Test
    fun `every bundled GBA patch keeps the picture tables where its base game has them`() {
        val gba = RomKind.allPatched.filter { it.generation == Generation.GBA3 }
        assertTrue(gba.map { it.patchTag }.toSet().containsAll(listOf("faster", "faster121", "smartai")), gba.map { it.id }.toString())
        var built = 0
        for (kind in gba) {
            val base = RomKind.allV1.single { it.id == kind.baseId }
            val baseFile = File(roms, "${base.id}.gba").takeIf { it.isFile } ?: run { println("${base.id}.gba not here; ${kind.id} skipped"); null } ?: continue
            val opt = PrepOptions.forKind(base).first { it.out?.id == kind.id }
            val out = File.createTempFile(kind.id, ".gba")
            try {
                val crc = Patcher.applyFiles(File("src/main/assets/patches/" + opt.asset), baseFile, out)
                assertEquals("%08x".format(kind.expectedCrc), "%08x".format(crc), "the pinned build of ${kind.id}")
                val b = reader(baseFile.readBytes()); val p = reader(out.readBytes())
                val map = GameMap.resolve(p)
                assertEquals(GameMap.resolve(b).name, map.name, "${kind.id} tracks on its base game's map")
                val tb = assertNotNull(Gen3Pictures.tables(b, map)); val tp = assertNotNull(Gen3Pictures.tables(p, map), kind.id)
                assertEquals(listOf(tb.front, tb.palettes, tb.shinyPalettes, tb.ownFront), listOf(tp.front, tp.palettes, tp.shinyPalettes, tp.ownFront), kind.id)
                val before = pictures(b, tb); val after = pictures(p, tp)
                for (i in before.indices) {
                    val x = before[i]; val y = after[i]
                    assertTrue(if (x == null) y == null else y != null && x.contentEquals(y), "${kind.id}: picture $i reads the same")
                }
                // Every shiny species and every letter is there to compare (Deoxys's form is one frame on Ruby and Sapphire).
                assertTrue(after.count { it != null } >= 411 + 27 + 1, "${kind.id}: ${after.count { it != null }} pictures")
                built++
            } finally {
                out.delete()
            }
        }
        if (built == 0) println("no GBA base dump here; skipped")
    }
}
