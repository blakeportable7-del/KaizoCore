package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The trainer portraits on the nine vendored GBA dumps (IRONMON_ROMS; each skipped when its dump is not to hand, and a
 * failure under IRONMON_REQUIRE_DUMPS). Every build's tables were found by their shape and the pictures checked by eye
 * on 2026-10-03: the first gym leader (Brock on the five FireRed builds, Roxanne on the four Hoenn ones) and the player,
 * the boy and the girl. Their pixels are pinned here by hash, so a table or an offset that moves draws nothing new
 * unnoticed. Brock is the same picture on all five FireRed builds, and Roxanne on all four Hoenn ones.
 */
class TrainerPicturesRomTest {
    private class Build(val rom: String, val pics: Long, val count: Int, val leader: Int, val leaderHash: Int, val boy: Int, val girl: Int)

    private val brock = -1179734087
    private val roxanne = 1717129977
    private val builds = listOf(
        Build("firered-u-v10.gba", 0x0823957C, 148, 414, brock, -2088954863, 1680215201),
        Build("firered-u-v11.gba", 0x082395EC, 148, 414, brock, -2088954863, 1680215201),
        Build("leafgreen-u.gba", 0x08239558, 148, 414, brock, -2088954863, 1680215201),
        Build("emerald-u.gba", 0x08305654, 93, 265, roxanne, 458322473, -821413319),
        Build("ruby-u.gba", 0x081EC53C, 83, 265, roxanne, 417172497, -75273623),
        Build("sapphire-u.gba", 0x081EC4CC, 83, 265, roxanne, 417172497, -75273623),
        Build("firered-natdex-121.gba", 0x082313F8, 148, 414, brock, -2088954863, 1680215201),
        Build("emerald-natdex-121.gba", 0x08367694, 93, 265, roxanne, 458322473, -821413319),
        Build("firered-maxdex.gba", 0x0825C548, 148, 414, brock, -2088954863, 1680215201),
    )

    private fun reader(bytes: ByteArray) = MemoryReader { a, n ->
        val off = (a - 0x08000000L).toInt()
        if (a >= 0x08000000L && off >= 0 && off + n <= bytes.size) bytes.copyOfRange(off, off + n) else ByteArray(n)
    }

    @Test
    fun `each build draws its first gym leader and its player out of its own tables`() {
        var read = 0
        for (b in builds) {
            val file = Dumps.rom(b.rom) ?: continue
            val mem = reader(file.readBytes())
            val t = GbaTracker(mem, GameMap.resolve(mem))
            assertEquals(b.pics, t.map.trainerPics, b.rom)
            assertEquals(b.count, t.map.trainerPicCount, b.rom)
            assertEquals(b.leaderHash, assertNotNull(t.trainerPicture(b.leader), b.rom).contentHashCode(), "${b.rom}: the first gym leader")
            assertEquals(b.boy, assertNotNull(t.playerPicture(girl = false), b.rom).contentHashCode(), "${b.rom}: the boy")
            assertEquals(b.girl, assertNotNull(t.playerPicture(girl = true), b.rom).contentHashCode(), "${b.rom}: the girl")
            // Every picture in the table reads, and the slot after the last does not.
            for (slot in 0 until b.count) assertNotNull(TrainerPictures.picture(mem, t.map, slot), "${b.rom}: slot $slot")
            assertNull(TrainerPictures.picture(mem, t.map.copy(trainerPicCount = b.count + 1), b.count), "${b.rom}: past the end")
            read++
        }
        println("TrainerPicturesRomTest: $read of ${builds.size} builds read")
    }
}
