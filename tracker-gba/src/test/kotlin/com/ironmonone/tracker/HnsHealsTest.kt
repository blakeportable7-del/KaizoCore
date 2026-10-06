package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Heart & Soul's heals outside a Pokemon Center (HnsHeals): Survival counts Elm's lab heal machine, the National Park
 * teacher and every free bed or healer outside a dungeon as a Pokemon Center heal, and none of them bumps the game's
 * heal statistics. The script engine's context is written into synthetic IWRAM with the build's own offsets (HnsLayout,
 * from the ELF and the compiled struct); the labels are checked against the real build's bytes where it is present.
 */
class HnsHealsTest {
    private class Mem(val rom: ByteArray = ByteArray(0)) : MemoryReader {
        val iwram = ByteArray(0x8000)
        override fun read(address: Long, length: Int): ByteArray = when (address) {
            in 0x03000000L..0x03007FFFL -> {
                val o = (address - 0x03000000L).toInt(); iwram.copyOfRange(o, minOf(iwram.size, o + length))
            }
            in 0x08000000L..0x09FFFFFFL -> {
                val o = (address - 0x08000000L).toInt()
                if (o >= rom.size) ByteArray(0) else rom.copyOfRange(o, minOf(rom.size, o + length))
            }
            else -> ByteArray(0)
        }
        private fun w32(a: Long, v: Long) { val o = (a - 0x03000000L).toInt(); for (i in 0 until 4) iwram[o + i] = (v ushr (8 * i)).toByte() }

        /** The script engine running [ptr] with [stack] as its call stack, innermost last. */
        fun script(ptr: Long, vararg stack: Long) {
            val c = HnsLayout.sGlobalScriptContext
            val f = HnsLayout.ScriptContext
            iwram.fill(0, (c - 0x03000000L).toInt(), (c - 0x03000000L).toInt() + HnsLayout.ScriptContext.SIZE)
            iwram[(c - 0x03000000L).toInt() + f.stackDepth.offset] = stack.size.toByte()
            w32(c + f.scriptPtr.offset, ptr)
            stack.forEachIndexed { i, a -> w32(c + f.stack.offset + 4L * i, a) }
        }
    }

    private val heal = HnsLayout.Common_EventScript_OutOfCenterPartyHeal
    private fun mid(r: LongRange) = r.first + 2

    @Test
    fun `the shared heal counts when one of the heal stations called it, and only then`() {
        val m = Mem()
        for (caller in HnsHeals.CALLERS) {
            m.script(mid(heal), mid(caller))
            assertTrue(HnsHeals.healing(m), "called from ${caller.first.toString(16)}")
        }
        // The mom's call (her own script, not a station): statistic 16 counts her already.
        m.script(mid(heal), 0x083B0000L)
        assertFalse(HnsHeals.healing(m), "a caller not on the list (the mom, Lance in the Rocket Hideout)")
        m.script(mid(heal))
        assertFalse(HnsHeals.healing(m), "no caller at all")
        // A station's own script before or after the heal (the teacher's message) is not the heal.
        m.script(mid(HnsLayout.NationalPark_Normal_EventScript_Teacher2))
        assertFalse(HnsHeals.healing(m))
        // The benches heal in their own script.
        for (bench in HnsHeals.DIRECT) { m.script(mid(bench)); assertTrue(HnsHeals.healing(m)) }
        m.script(0L)
        assertFalse(HnsHeals.healing(m), "no script running")
    }

    @Test
    fun `a heal is counted once however many reads see it, and the next one again`() {
        val m = Mem()
        val w = HnsHeals.Watch()
        val start = HnsHeals.total
        m.script(0L); w.poll(m)
        m.script(mid(heal), mid(HnsLayout.NewBarkTown_Lab_EventScript_HealingMachine2))
        repeat(4) { w.poll(m) }
        assertEquals(start + 1, HnsHeals.total, "about three seconds of reads, one heal")
        m.script(mid(HnsLayout.NewBarkTown_Lab_EventScript_HealingMachine2)); w.poll(m)
        m.script(mid(heal), mid(HnsLayout.NewBarkTown_Lab_EventScript_HealingMachine2)); w.poll(m)
        assertEquals(start + 2, HnsHeals.total, "the machine used again")
        assertEquals(start + 2, w.poll(Mem()), "what the tracker adds to the heal statistics")
    }

    @Test
    fun `the tracker adds them to the heal statistics on Heart & Soul only`() {
        val src = File("src/main/kotlin/com/ironmonone/tracker/GbaTracker.kt").readText().replace("\r\n", "\n")
        assertTrue("centerHealsStat = readGameStat(15) + readGameStat(16) + (hnsHeals?.poll(memory) ?: 0)," in src)
        assertTrue("private val hnsHeals: HnsHeals.Watch? = if (map.hns) HnsHeals.Watch() else null" in src)
    }

    /**
     * On the real build: the shared heal plays the heal jingle and heals the party (special 0, HealPlayerParty), and every
     * station on the list calls it; a bench's own heal script is the heal special. So the layout's labels are the scripts
     * this counts.
     */
    @Test
    fun `the labels are the build's heal scripts`() {
        // The build this layout was exported from (a newer build moves every label): hns-kaizo.gba, or its copy by CRC.
        val crc = "%08X".format(HnsLayout.BUILD_CRC)
        // The release gate requires hns-kaizo.gba only: the copy by CRC is an optional extra, never demanded.
        val dir = HnsTrackerTest.romDir()
        val rom = listOfNotNull(Dumps.file(dir, "hns-kaizo.gba"), dir?.let { java.io.File(it, "hns-kaizo-$crc.gba") }?.takeIf { it.isFile })
            .map { it.readBytes() }
            .firstOrNull { java.util.zip.CRC32().apply { update(it) }.value == HnsLayout.BUILD_CRC } ?: return
        fun at(a: Long, n: Int) = rom.copyOfRange((a - 0x08000000L).toInt(), (a - 0x08000000L).toInt() + n)
        val body = at(heal.first, (heal.last - heal.first + 1).toInt())
        val text = body.joinToString("") { "%02x".format(it) }
        assertTrue("317001" + "32" in text, "playfanfare MUS_HEAL, waitfanfare: $text")
        assertTrue("250000" in text, "special HealPlayerParty: $text")
        val call = ByteArray(5).also { it[0] = 0x04; for (i in 0 until 4) it[1 + i] = (heal.first ushr (8 * i)).toByte() }
        for (caller in HnsHeals.CALLERS) {
            val b = at(caller.first, (caller.last - caller.first + 1).toInt())
            assertTrue(b.toList().windowed(5).any { it == call.toList() }, "${caller.first.toString(16)} calls the shared heal")
        }
        for (bench in HnsHeals.DIRECT) {
            val b = at(bench.first, (bench.last - bench.first + 1).toInt()).joinToString("") { "%02x".format(it) }
            assertTrue("250000" in b, "${bench.first.toString(16)} heals the party: $b")
        }
    }
}
