package com.ironmonone.tracker.nds

import com.ironmonone.tracker.nuzlocke.LevelCapTable
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The DS level cap tables against the games themselves (2026-09-30), the way NuzlockeRomCapsTest does it for the GBA:
 * the tables in nuzlocke/levelcaps-gen4.tsv and -gen5.tsv were computed from the games' trainer data by
 * tools/nuzlocke, and here this test reads every boss's team out of a clean dump with a reader of its own (the
 * NDS file system, the NARC archives and the two trainer record layouts) and the two must agree, number for number.
 *
 * A game whose dump is not on this machine is skipped with a line saying so. Platinum (the Diamond and Pearl and
 * HeartGold and SoulSilver tables have no dump here, their numbers come from the pret disassemblies) and Black 2 and
 * White 2 are the dumps this machine has. Nothing from a ROM is copied anywhere: the file is read where it lies.
 */
class NdsRomCapsTest {

    private val roms: File = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory } ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")

    /** An NDS ROM read in place: the file name table and file allocation table are loaded, a file is read when asked for. */
    private class Rom(file: File) : AutoCloseable {
        private val raf = RandomAccessFile(file, "r")
        private val fat: ByteBuffer
        val paths = HashMap<String, Int>()
        val code: String

        init {
            val head = bytes(0, 0x200)
            code = String(head, 12, 4, Charsets.US_ASCII)
            val h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
            val fntOff = h.getInt(0x40); val fntSize = h.getInt(0x44); val fatOff = h.getInt(0x48); val fatSize = h.getInt(0x4C)
            val fnt = ByteBuffer.wrap(bytes(fntOff.toLong(), fntSize)).order(ByteOrder.LITTLE_ENDIAN)
            fat = ByteBuffer.wrap(bytes(fatOff.toLong(), fatSize)).order(ByteOrder.LITTLE_ENDIAN)
            walk(fnt, 0xF000, "")
        }

        private fun bytes(at: Long, n: Int): ByteArray { raf.seek(at); return ByteArray(n).also { raf.readFully(it) } }

        private fun walk(fnt: ByteBuffer, dirId: Int, prefix: String) {
            val idx = dirId and 0xFFF
            var pos = fnt.getInt(8 * idx)
            var fid = fnt.getShort(8 * idx + 4).toInt() and 0xFFFF
            while (true) {
                val b = fnt.get(pos).toInt() and 0xFF
                pos++
                if (b == 0) break
                val n = b and 0x7F
                val name = String(ByteArray(n) { fnt.get(pos + it) }, Charsets.ISO_8859_1)
                pos += n
                if (b and 0x80 != 0) {
                    val child = fnt.getShort(pos).toInt() and 0xFFFF
                    pos += 2
                    walk(fnt, child, "$prefix$name/")
                } else paths[prefix + name] = fid++
            }
        }

        fun file(path: String): ByteArray {
            val id = assertNotNull(paths[path], "the ROM has no file $path")
            val start = fat.getInt(8 * id); val end = fat.getInt(8 * id + 4)
            return bytes(start.toLong(), end - start)
        }

        fun find(suffix: String): String = paths.keys.filter { it.endsWith(suffix) }.minOrNull() ?: error("no file ending in $suffix")

        override fun close() = raf.close()
    }

    /** The files of a NARC archive. */
    private fun narc(blob: ByteArray): List<ByteArray> {
        val b = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("NARC", String(blob, 0, 4, Charsets.US_ASCII))
        var pos = b.getShort(12).toInt() and 0xFFFF
        assertEquals("BTAF", String(blob, pos, 4, Charsets.US_ASCII))
        val btafSize = b.getInt(pos + 4)
        val count = b.getShort(pos + 8).toInt() and 0xFFFF
        val entries = (0 until count).map { b.getInt(pos + 12 + 8 * it) to b.getInt(pos + 16 + 8 * it) }
        pos += btafSize
        assertEquals("BTNF", String(blob, pos, 4, Charsets.US_ASCII))
        pos += b.getInt(pos + 4)
        assertEquals("GMIF", String(blob, pos, 4, Charsets.US_ASCII))
        val base = pos + 8
        return entries.map { (s, e) -> blob.copyOfRange(base + s, base + e) }
    }

    /** The highest level on each trainer's team, by trainer id (the index in the trainer archive), for a Generation 4 game. */
    private fun gen4Top(rom: Rom, memberExtra: Int): Map<Int, Int> {
        val data = narc(rom.file(rom.find("poketool/trainer/trdata.narc")))
        val party = narc(rom.file(rom.find("poketool/trainer/trpoke.narc")))
        assertEquals(data.size, party.size)
        val out = HashMap<Int, Int>()
        for (i in data.indices) {
            val h = data[i]; val p = ByteBuffer.wrap(party[i]).order(ByteOrder.LITTLE_ENDIAN)
            val type = h[0].toInt() and 0xFF; val size = h[3].toInt() and 0xFF
            var pos = 0; var top = 0
            repeat(size) {
                top = maxOf(top, p.getShort(pos + 2).toInt() and 0xFFFF)
                pos += 6 + (if (type and 2 != 0) 2 else 0) + (if (type and 1 != 0) 8 else 0) + memberExtra
            }
            if (size > 0) out[i] = top
        }
        return out
    }

    /** The same for a Generation 5 game: a member is 8 bytes and a held item and four moves may follow. */
    private fun gen5Top(rom: Rom, dataPath: String, partyPath: String): Map<Int, Int> {
        val data = narc(rom.file(dataPath))
        val party = narc(rom.file(partyPath))
        assertEquals(data.size, party.size)
        val out = HashMap<Int, Int>()
        for (i in 1 until data.size) {
            val h = data[i]; val p = ByteBuffer.wrap(party[i]).order(ByteOrder.LITTLE_ENDIAN)
            val type = h[0].toInt() and 0xFF; val count = h[3].toInt() and 0xFF
            var pos = 0; var top = 0
            repeat(count) {
                top = maxOf(top, p.getShort(pos + 2).toInt() and 0xFFFF)
                pos += 8 + (if (type and 2 != 0) 2 else 0) + (if (type and 1 != 0) 8 else 0)
            }
            assertEquals(party[i].size, pos, "trainer $i: the record is read to its end")
            if (count > 0) out[i] = top
        }
        return out
    }

    private fun check(file: String, code: String, system: NuzlockeSystem, game: String, top: (Rom) -> Map<Int, Int>) {
        val f = File(roms, file)
        if (!f.isFile) { println("SKIP: $file is not on this machine"); return }
        Rom(f).use { rom ->
            assertEquals(code, rom.code, "$file is the game it says")
            val levels = top(rom)
            val caps = LevelCapTable.standard(game, system)
            assertTrue(caps.bosses.size >= 20, "$game has its table")
            val differ = ArrayList<String>()
            for (b in caps.bosses) {
                val found = b.trainerIds.map { levels[it] }
                if (found.any { it == null }) { differ += "${b.key} ${b.label}: a team could not be read $found"; continue }
                val level = found.filterNotNull().max()
                if (level != b.cap) differ += "${b.key} ${b.label}: table ${b.cap}, game $level"
            }
            assertTrue(differ.isEmpty(), "$file: " + differ.joinToString("; "))
            println("$file: all ${caps.bosses.size} caps read from the ROM match the table")
        }
    }

    @Test
    fun `Platinum's boss levels in the game are the table's`() =
        check("platinum-u.nds", "CPUE", NuzlockeSystem.GEN4, "pt") { gen4Top(it, memberExtra = 2) }

    @Test
    fun `Black 2's boss levels in the game are the table's`() =
        check("black2-u.nds", "IREO", NuzlockeSystem.GEN5, "b2w2") { gen5Top(it, "a/0/9/1", "a/0/9/2") }

    @Test
    fun `White 2's boss levels in the game are the table's`() =
        check("white2-u.nds", "IRDO", NuzlockeSystem.GEN5, "b2w2") { gen5Top(it, "a/0/9/1", "a/0/9/2") }

    @Test
    fun `the trainer ids the tracker treats as the lab and the final fight are the ones the ROM holds`() {
        val f = File(roms, "platinum-u.nds")
        if (!f.isFile) { println("SKIP: platinum-u.nds is not on this machine"); return }
        Rom(f).use { rom ->
            val levels = gen4Top(rom, 2)
            for (id in NdsGameMap.PLATINUM.labTrainerIds + NdsGameMap.PLATINUM.finalTrainerId) assertNotNull(levels[id], "trainer $id has a team")
            assertEquals(62, levels[NdsGameMap.PLATINUM.finalTrainerId], "Cynthia, the first-run team")
        }
    }
}
