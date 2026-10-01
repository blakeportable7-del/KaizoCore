package com.ironmonone.app

import com.ironmonone.app.engine.ZxEngine
import com.ironmonone.core.Generation
import com.ironmonone.core.RomKind
import com.ironmonone.patch.Patcher
import com.ironmonone.patch.RomIdentity
import com.ironmonone.tracker.GameMap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Faster Emerald (DrMaple), bundled in two versions (2026-09-28): 1.3.2 with
 * the 6% trainer level increase, 1.2.1 without it. Runs when the pinned dump
 * is to hand (IRONMON_ROMS holding emerald-u.gba), as FasterFireRedTest does.
 */
class FasterEmeraldTest {

    private fun patched(kind: RomKind): Pair<ByteArray, ByteArray>? {
        val rom = System.getenv("IRONMON_ROMS")?.let { File(it, "emerald-u.gba") }?.takeIf { it.isFile }
        if (rom == null) { println("FasterEmeraldTest skipped: set IRONMON_ROMS (emerald-u.gba)"); return null }
        val opt = PrepOptions.forKind(RomKind.EMERALD_U).first { it.out?.id == kind.id }
        val vanilla = rom.readBytes()
        return vanilla to Patcher.apply(File("src/main/assets/patches/" + opt.asset).readBytes(), vanilla, "Emerald")
    }

    @Test
    fun `both versions are identified as their pinned kinds`() {
        for (k in listOf(RomKind.EMERALD_FASTER, RomKind.EMERALD_FASTER121)) {
            val (_, p) = patched(k) ?: return
            val id = RomIdentity.identify(p)
            assertEquals(k.id, id.kind?.id, "identified as ${id.summary}")
            assertEquals("RSE", id.kind!!.family, "it must take the RSE settings files")
        }
    }

    @Test
    fun `every ROM table the tracker reads is where vanilla keeps it`() {
        val m = GameMap.EMERALD_U
        for (k in listOf(RomKind.EMERALD_FASTER, RomKind.EMERALD_FASTER121)) {
            val (vanilla, p) = patched(k) ?: return
            // 1.3.2 edits base stats in place: 258 species gain a second
            // ability (byte 23) and one species' stats change. The tracker reads
            // both from the ROM, so it shows the patched values; what matters
            // is that the table did not move, so the stat bytes of nearly
            // every species must still match.
            val bs = (m.baseStats - 0x08000000L).toInt()
            val unmoved = (0 until 412).count { sp -> (0 until 6).all { vanilla[bs + sp * 28 + it] == p[bs + sp * 28 + it] } }
            assertTrue(unmoved >= 400, "${k.id}: base stats moved ($unmoved of 412 species unchanged)")
            val regions = listOf(
                "level-up learnsets" to (m.levelUpLearnsets to 412 * 4),
                "trainer class names" to (m.gTrainerClassNames to 66 * 13),
                "experience tables" to (m.expTables to 6 * 101 * 4),
            )
            for ((name, r) in regions) {
                val (addr, len) = r
                if (addr == 0L) continue
                val off = (addr - 0x08000000L).toInt()
                val same = (0 until len).count { vanilla[off + it] == p[off + it] }
                assertEquals(len, same, "${k.id}: $name moved, the tracker would read the wrong bytes")
            }
        }
    }

    @Test
    fun `the randomizer still loads each version and writes a run`() {
        for ((k, preset) in listOf(RomKind.EMERALD_FASTER to "RSE Kaizo.rnqs", RomKind.EMERALD_FASTER121 to "RSE Standard.rnqs")) {
            val (_, p) = patched(k) ?: return
            val src = File.createTempFile("faster", ".gba").apply { writeBytes(p) }
            val out = File.createTempFile("run", ".gba")
            try {
                ZxEngine.randomize(src, File("src/main/assets/presets/$preset"), out, 20260928L, Generation.GBA3)
                val after = out.readBytes()
                assertEquals(p.size, after.size)
                assertTrue(p.indices.count { p[it] != after[it] } > 10_000, "${k.id}: nothing was randomized")
            } finally { src.delete(); out.delete() }
        }
    }
}
