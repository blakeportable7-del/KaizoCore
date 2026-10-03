package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MaxDex's own 32x32 icons (maxdex/sprites_mons, copied by tools/trainer-data/convert_maxdex.py), by MaxDex id from
 * 412 to 1280. Its ids match Nat. Dex 1.2.1's to 1235 and part ways after, where the 45 Legends Z-A megas sit in
 * another order, so the Nat. Dex pack drew another Pokemon there. A hole is invisible, so the pack is counted.
 */
class MaxDexSpritePackTest {
    private val pack = File("src/main/assets/gbasprites-maxdex")

    @Test
    fun `every MaxDex id from 412 to 1280 has its icon, and nothing else is there`() {
        val have = pack.listFiles { f: File -> f.name.endsWith(".png") }.orEmpty().mapNotNull { it.name.removeSuffix(".png").toIntOrNull() }.toSet()
        assertEquals((412..1280).toSet(), have)
        for (id in listOf(412, 1236, 1280)) {
            val b = File(pack, "$id.png").readBytes()
            assertTrue(b.size > 24 && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte(), "$id is a PNG")
            val w = (b[16].toInt() and 255 shl 24) or (b[17].toInt() and 255 shl 16) or (b[18].toInt() and 255 shl 8) or (b[19].toInt() and 255)
            val h = (b[20].toInt() and 255 shl 24) or (b[21].toInt() and 255 shl 16) or (b[22].toInt() and 255 shl 8) or (b[23].toInt() and 255)
            assertEquals(32 to 32, w to h, "$id")
        }
    }
}
