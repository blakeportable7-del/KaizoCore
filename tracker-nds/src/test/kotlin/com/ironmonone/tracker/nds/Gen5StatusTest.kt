package com.ironmonone.tracker.nds

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Gen 5's in-battle status, read one condition word per id from battle data +0x20
 * (gen5StatusBits). Blake, 2026-09-29: read it properly rather than copy the reference,
 * which reads +0x20 for every id.
 */
class Gen5StatusTest {
    private fun words(vararg set: Pair<Int, Long>): ByteArray {
        val b = ByteArray(20)
        set.forEach { (id, v) -> for (i in 0 until 4) b[(id - 1) * 4 + i] = ((v shr (8 * i)) and 0xFF).toByte() }
        return b
    }

    @Test fun `each condition id reads as its status`() {
        assertEquals(0x40L, gen5StatusBits(words(1 to 1L)), "1 paralysis")
        assertEquals(0x01L, gen5StatusBits(words(2 to 0x31L)), "2 sleep, with its turns")
        assertEquals(0x20L, gen5StatusBits(words(3 to 1L)), "3 freeze")
        assertEquals(0x10L, gen5StatusBits(words(4 to 1L)), "4 burn")
        assertEquals(0x08L, gen5StatusBits(words(5 to 0x101L)), "5 poison, Toxic included")
        assertEquals(0L, gen5StatusBits(ByteArray(20)), "nothing set")
    }

    @Test fun `in the rival battle dump Foresight is not a status and no one is statused`() {
        // IRONMON_DUMPS: .vendor/dumps. The enemy Larvitar has Odor Sleuth's condition (17) at
        // +0x60 and nothing else; the reference's loop reads only +0x20 and would miss any status.
        val dir = System.getenv("IRONMON_DUMPS")?.let { File(it) }?.takeIf { it.isDirectory } ?: return
        val f = File(dir, "b2-rand-rival-battle.bin").takeIf { it.isFile } ?: return
        val ram = f.readBytes()
        fun u32(a: Long): Long { val o = (a - 0x02000000L).toInt(); return (0 until 4).fold(0L) { acc, i -> acc or ((ram[o + i].toLong() and 0xFF) shl (8 * i)) } }
        val ptr = 0x02000000L + 0x2573AC - 0x40
        val enemy = u32(ptr + 0x1C)
        val region = ram.copyOfRange((enemy - 0x02000000L + 0x20).toInt(), (enemy - 0x02000000L + 0x20 + 4 * 17).toInt())
        assertEquals(0xE01L, region.u32(16 * 4), "Odor Sleuth's word at +0x60")
        assertEquals(0L, gen5StatusBits(region.copyOfRange(0, 20)))
    }
}
