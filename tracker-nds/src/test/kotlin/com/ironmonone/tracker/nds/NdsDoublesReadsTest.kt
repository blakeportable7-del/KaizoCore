package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every Pokemon on the field in a DS double, triple, rotation or multi battle, in the DS tracker's slot order
 * (BattleHandlerGen4._readBattlePIDInfo and BattleHandlerGen5._tryToFetchBattleData), which its swap walks. Until rc34
 * only each side's first was read. Synthetic memory laid out as NdsPlayerPokemonTest (Platinum) and NdsGen5MapTest
 * (Black) do.
 */
class NdsDoublesReadsTest {

    // ------------------------------------------------------------ Platinum

    private val ram = ByteArray(0x400000)
    private val globalRel = 0x100000L
    private val versionRel = 0x180000L
    private val pt = NdsGameMap.PLATINUM

    private fun putU32(off: Long, v: Long) { val i = off.toInt(); for (k in 0..3) ram[i + k] = (v ushr (8 * k)).toByte() }
    private fun putU16(off: Long, v: Int) { val i = off.toInt(); ram[i] = v.toByte(); ram[i + 1] = (v ushr 8).toByte() }
    private fun put(off: Long, bytes: ByteArray) = bytes.copyInto(ram, off.toInt())

    private fun ptMon(pid: Long, species: Int) =
        Gen4.encodeParty(pid, species = species, level = 20, curHp = 40, maxHp = 40, moves = listOf(33, 0, 0, 0))

    /** A trainer battle: your Squirtle, Pidgey and Abra, the opponent's Geodude and Zubat; [doubles] puts Abra and Zubat out too. */
    private fun platinum(doubles: Boolean): NdsTracker {
        putU32(pt.globalPointer, globalRel)
        putU32(globalRel + NdsGameMap.VERSION_POINTER_OFFSET, versionRel)
        listOf(ptMon(0x100L, 7), ptMon(0x101L, 16), ptMon(0x102L, 63)).forEachIndexed { i, b ->
            put(versionRel + pt.playerBase + i * 236L, b)
            put(versionRel + pt.playerBattleBase + i * 236L, b)
        }
        put(versionRel + pt.enemyBase, ptMon(0x9000L, 74))
        put(versionRel + pt.enemyBase + 236L, ptMon(0x9001L, 41))
        putU16(versionRel + pt.enemyTrainerId, 5)
        putU32(versionRel + pt.playerBattleMonPid, 0x100L)
        putU32(versionRel + pt.enemyBattleMonPid, 0x9000L)
        putU32(versionRel + pt.playerBattleMonPid + 0x180, if (doubles) 0x102L else 0L)
        putU32(versionRel + pt.enemyBattleMonPid + 0x180, if (doubles) 0x9001L else 0L)
        // Stage blocks, HP ATK DEF SPE SPA SPD ACC EVA: your first +2 attack, your second -1 defense, the opponent's second +1 speed.
        byteArrayOf(6, 8, 6, 6, 6, 6, 6, 6).copyInto(ram, (versionRel + pt.statStagesPlayer).toInt())
        byteArrayOf(6, 6, 5, 6, 6, 6, 6, 6).copyInto(ram, (versionRel + pt.statStagesPlayer + 0x180).toInt())
        byteArrayOf(6, 6, 6, 6, 6, 6, 6, 6).copyInto(ram, (versionRel + pt.statStagesEnemy).toInt())
        byteArrayOf(6, 6, 6, 7, 6, 6, 6, 6).copyInto(ram, (versionRel + pt.statStagesEnemy + 0x180).toInt())
        putU16(pt.battleStatus, 0x2100)
        val reader = NdsMemoryReader { addr, len ->
            val off = (addr - 0x02000000L).toInt()
            if (off >= 0 && off + len <= ram.size) ram.copyOfRange(off, off + len) else ByteArray(0)
        }
        return NdsTracker(reader, null, pt)
    }

    @Test
    fun `a Gen 4 double battle reads each side's second, with its own stage block`() {
        val s = platinum(doubles = true).read()
        assertEquals(listOf(0x100L, 0x102L), s.playerBattlers.map { it?.mon?.pid })
        assertEquals(s.party[2], s.playerBattlers[1], "your second is your party's entry")
        assertEquals(5, s.party[2].statStages["DEF"], "its stages are the second block's")
        assertEquals(8, s.party[0].statStages["ATK"])
        assertTrue(s.party[1].statStages.isEmpty(), "Pidgey is on the bench")
        assertEquals(listOf(74, 41), s.enemyBattlers.map { it?.mon?.species })
        assertEquals(7, s.enemyBattlers[1]?.statStages?.get("SPE"))
        assertEquals(6, s.enemyBattlers[0]?.statStages?.get("SPE"))
        assertEquals(s.enemy, s.enemyBattlers[0], "the first is the one the tracker always read")
        assertFalse(s.rotation)
        assertEquals(0, s.enemyAllies)
    }

    @Test
    fun `a Gen 4 single battle has one of each`() {
        val s = platinum(doubles = false).read()
        assertEquals(listOf(0x100L), s.playerBattlers.map { it?.mon?.pid })
        assertEquals(listOf(74), s.enemyBattlers.map { it?.mon?.species })
        assertTrue(s.party[2].statStages.isEmpty())
    }

    @Test
    fun `a Gen 4 second that used Transform is still the one last matched`() {
        val t = platinum(doubles = true)
        t.read()
        putU32(versionRel + pt.playerBattleMonPid + 0x180, 0x9001L)   // Abra took Zubat's personality
        putU32(versionRel + pt.enemyBattleMonPid + 0x180, 0x102L)     // and Zubat took Abra's
        val s = t.read()
        assertEquals(0x102L, s.playerBattlers[1]?.mon?.pid)
        assertEquals(41, s.enemyBattlers[1]?.mon?.species)
    }

    @Test
    fun `outside a battle there are none`() {
        val t = platinum(doubles = true)
        putU16(pt.battleStatus, 0x2800)
        val s = t.read()
        assertTrue(s.playerBattlers.isEmpty() && s.enemyBattlers.isEmpty())
    }

    // ------------------------------------------------------------ Black

    private val RAM = 0x02000000L
    private val bw = NdsGameMap.BW

    private class Ram {
        val bytes = ByteArray(0x400000)
        fun u32(rel: Long, v: Long) { for (i in 0 until 4) bytes[(rel + i).toInt()] = ((v shr (8 * i)) and 0xFF).toByte() }
        fun u16(rel: Long, v: Int) { bytes[rel.toInt()] = (v and 0xFF).toByte(); bytes[(rel + 1).toInt()] = ((v shr 8) and 0xFF).toByte() }
        fun put(rel: Long, data: ByteArray) { data.forEachIndexed { i, b -> bytes[(rel + i).toInt()] = b } }
        fun reader(base: Long) = NdsMemoryReader { address, length ->
            val off = (address - base).toInt()
            if (off >= 0 && off + length <= bytes.size) bytes.copyOfRange(off, off + length) else ByteArray(0)
        }
    }

    /** A Gen 5 battle data block at [data] for a Pokemon at [mon]: HP, level, no status, one move, a stage block. */
    private fun Ram.battler(data: Long, mon: Long, pid: Long, species: Int, hp: Int, stages: ByteArray = ByteArray(8) { 6 }) {
        put(mon, Gen4.encodeParty(pid, species, 30, hp, 100, listOf(33, 0, 0, 0), gen5 = true))
        u32(data, RAM + mon); u32(data + 4, 0)
        u16(data + 0x0E, 100); u16(data + 0x10, hp); u16(data + 0x18, 30)
        u16(data + 0x104, 33); bytes[(data + 0x106).toInt()] = 30
        put(data + 0xFC, stages)
    }

    private val yours = listOf(0x0BAD0001L to 497, 0x0BAD0002L to 500, 0x0BAD0003L to 503)
    private val theirs = listOf(0xC0FF0001L to 509, 0xC0FF0002L to 510, 0xC0FF0003L to 519)

    /**
     * Black in a trainer battle, two battler records (yours, the opponent's) with [flag] as doubleTripleFlag: three of
     * yours and three of theirs set up, the flag saying how many are on the field.
     */
    private fun black(flag: Int): Ram {
        val r = Ram()
        r.u32(NdsGameMap.CARTRIDGE_HEADER - RAM + 0x0C, NdsGameMap.CODE_BLACK)
        yours.forEachIndexed { i, (pid, sp) -> r.put(bw.playerBase + i * 220L, Gen4.encodeParty(pid, sp, 30, 100, 100, listOf(33, 0, 0, 0), gen5 = true)) }
        r.u32(bw.playerBattleBase, yours[0].first)
        r.u32(bw.enemyBase, theirs[0].first)
        r.u16(bw.battleStatus, 0x2100)
        r.u16(bw.enemyTrainerId, 7)
        r.bytes[bw.doubleTripleFlag.toInt()] = flag.toByte()
        for (i in 0 until 3) {
            r.u32(bw.mainBattleDataPtr + 4L * i, RAM + 0x300000L + 0x1000L * i)
            r.battler(0x300000L + 0x1000L * i, 0x340000L + 0x1000L * i, yours[i].first, yours[i].second, 90 - i,
                byteArrayOf(6, 6, 6, 6, (6 + i).toByte(), 6, 6, 6))                 // SPE +i
            r.u32(bw.mainBattleDataPtr + 0x1C + 4L * i, RAM + 0x310000L + 0x1000L * i)
            r.battler(0x310000L + 0x1000L * i, 0x350000L + 0x1000L * i, theirs[i].first, theirs[i].second, 80 - i)
        }
        r.u32(bw.mainBattleDataPtr + 0x18, 1); r.u32(bw.mainBattleDataPtr + 0x1C + 0x18, 1)     // two battler records
        return r
    }

    @Test
    fun `a Gen 5 triple battle reads three a side, a double two, a single one`() {
        val s = NdsTracker(black(flag = 2).reader(RAM), null, bw).read()
        assertEquals(yours.map { it.first }, s.playerBattlers.map { it?.mon?.pid })
        assertEquals(s.party[1], s.playerBattlers[1], "your party's entries")
        assertEquals(mapOf("SPE" to 8), s.party[2].statStages.filterValues { it != 6 }, "each with its own battle data's stages")
        assertEquals(theirs.map { it.second }, s.enemyBattlers.map { it?.mon?.species })
        assertEquals(78, s.enemyBattlers[2]?.mon?.curHp, "live HP from the battle data")
        assertFalse(s.rotation)
        assertEquals(2, NdsTracker(black(flag = 1).reader(RAM), null, bw).read().enemyBattlers.size)
        val single = NdsTracker(black(flag = 0).reader(RAM), null, bw).read()
        assertEquals(1, single.playerBattlers.size)
        assertEquals(1, single.enemyBattlers.size)
    }

    @Test
    fun `a rotation battle has three a side and says so`() {
        val s = NdsTracker(black(flag = 3).reader(RAM), null, bw).read()
        assertTrue(s.rotation)
        assertEquals(3, s.playerBattlers.size)
        assertEquals(3, s.enemyBattlers.size)
    }

    @Test
    fun `a multi battle reads only your own first, and your partner's before the opponents'`() {
        // Four battler records 0x1C apart: you, the first opponent, your partner, the second opponent
        // (BattleHandlerGen5.lua:131-143).
        val r = black(flag = 1)
        val records = (0 until 4).map { bw.mainBattleDataPtr + 0x1CL * it }
        records.forEach { r.u32(it + 0x18, 1) }
        r.u32(records[2], RAM + 0x320000L)
        r.battler(0x320000L, 0x360000L, 0xA11E0001L, 531, 60)                         // your partner's Audino
        r.u32(records[3], RAM + 0x330000L)
        r.battler(0x330000L, 0x370000L, 0xC0FF0009L, 522, 50)                         // the second opponent's Blitzle
        val s = NdsTracker(r.reader(RAM), null, bw).read()
        assertEquals(listOf(yours[0].first), s.playerBattlers.map { it?.mon?.pid })
        assertEquals(listOf(531, 509, 522), s.enemyBattlers.map { it?.mon?.species })
        assertEquals(1, s.enemyAllies)
        assertEquals(509, s.enemy?.mon?.species, "the first opponent is still the one the tracker always read")
        assertNull(s.enemyBattlers.firstOrNull { it == null })
    }
}
