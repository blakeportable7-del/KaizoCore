package com.ironmonone.tracker.nds

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Black 2 and White 2 read through the pointer at 0x24 (NDS-Ironmon-Tracker 6.3.11, commit 4a45e1aa): the
 * table's numbers, the mask, and the tracker following a heap wherever it is, in a fake RAM laid out as
 * the reference describes it. The real 4 MB dumps are NdsDumpReplayTest's. The whole table against the
 * reference's own file is NdsAddressAuditTest's.
 *
 * Each path the tracker can find its heap by (the pointer, the scan when there is no pointer, the fixed
 * addresses) is asserted through NdsTracker.baseSource and scans, not only through the party it read: with three
 * ways to find it, "the party was read" passes with any one of them removed.
 */
class NdsPointerTest {
    private val RAM = 0x02000000L
    private val B2 = NdsGameMap.B2W2
    private val W2 = NdsGameMap.WHITE2

    // ------------------------------------------------------------------ the map

    @Test
    fun `the fixed addresses are the ones this app read before 6_3_11`() {
        // Typed here from the old NdsGameMap and the old MemoryAddresses.lua (reference 6.3.10), so the table and
        // NdsGameMap.B2W2's derivation are checked against numbers that were verified when they were written.
        assertEquals(0x246860L, B2.childMapHeader); assertEquals(0x246848L, B2.parentMapHeader)
        assertEquals(0x21E42CL, B2.playerBase); assertEquals(0x258874L, B2.enemyBase); assertEquals(0x257332L, B2.enemyTrainerId)
        assertEquals(0x2968D4L, B2.playerBattleMonPid); assertEquals(0x296930L, B2.enemyBattleMonPid)
        assertEquals(0x25B320L, B2.statStagesPlayer); assertEquals(0L, B2.statStagesEnemy)
        assertEquals(0x21E1FCL, B2.itemStartNoBattle); assertEquals(0x21E1FCL, B2.itemStartBattle)
        assertEquals(0x21E2BCL, B2.berryBagStart); assertEquals(0x21E2BCL, B2.berryBagStartBattle)
        assertEquals(listOf(0x226728L), B2.badgeOffsets); assertEquals(0x226F51L, B2.repelSteps)
        assertEquals(0x2573ACL, B2.mainBattleDataPtr); assertEquals(0x294DA4L, B2.doubleTripleFlag)
        assertEquals(0x294E08L, B2.abilityTriggerStart); assertEquals(0x21E428L, B2.totalMonsParty)
        assertEquals(0x258314L, B2.playerBattleBase); assertEquals(0x1B5138L, B2.battleStatus)
        // White 2: Black 2 + 0x80 everywhere but the battle flag, which is 0x1B5178 (the reference's GLOBAL).
        assertEquals(0x2468E0L, W2.childMapHeader); assertEquals(0x2468C8L, W2.parentMapHeader)
        assertEquals(0x21E4ACL, W2.playerBase); assertEquals(0x2588F4L, W2.enemyBase); assertEquals(0x2573B2L, W2.enemyTrainerId)
        assertEquals(0x296954L, W2.playerBattleMonPid); assertEquals(0x2969B0L, W2.enemyBattleMonPid)
        assertEquals(0x25B3A0L, W2.statStagesPlayer); assertEquals(0L, W2.statStagesEnemy)
        assertEquals(0x21E27CL, W2.itemStartNoBattle); assertEquals(0x21E33CL, W2.berryBagStart)
        assertEquals(listOf(0x2267A8L), W2.badgeOffsets); assertEquals(0x226FD1L, W2.repelSteps)
        assertEquals(0x25742CL, W2.mainBattleDataPtr); assertEquals(0x294E24L, W2.doubleTripleFlag)
        assertEquals(0x294E88L, W2.abilityTriggerStart); assertEquals(0x21E4A8L, W2.totalMonsParty)
        assertEquals(0x258394L, W2.playerBattleBase); assertEquals(0x1B5178L, W2.battleStatus)
    }

    @Test
    fun `atPointerBase puts every field the table covers at base plus its offset, and moves nothing else`() {
        val base = 0x204C90L
        for (game in listOf(B2, W2)) {
            val o = assertNotNull(game.pointerOffsets).offsets
            val at = game.atPointerBase(base)
            fun off(name: String) = base + o.getValue(name)
            assertEquals(off("playerBase"), at.playerBase); assertEquals(off("enemyBase"), at.enemyBase)
            assertEquals(off("enemyTrainerID"), at.enemyTrainerId)
            assertEquals(off("playerBattleMonPID"), at.playerBattleMonPid); assertEquals(off("enemyBattleMonPID"), at.enemyBattleMonPid)
            assertEquals(off("statStagesStart"), at.statStagesPlayer)
            assertEquals(off("itemStartNoBattle"), at.itemStartNoBattle); assertEquals(off("itemStartBattle"), at.itemStartBattle)
            assertEquals(off("berryBagStart"), at.berryBagStart); assertEquals(off("berryBagStartBattle"), at.berryBagStartBattle)
            assertEquals(listOf(off("badges")), at.badgeOffsets); assertEquals(off("repelSteps"), at.repelSteps)
            assertEquals(off("mainBattleDataPtr"), at.mainBattleDataPtr); assertEquals(off("doubleTripleFlag"), at.doubleTripleFlag)
            assertEquals(off("abilityTriggerStart"), at.abilityTriggerStart); assertEquals(off("totalMonsParty"), at.totalMonsParty)
            assertEquals(off("playerBattleBase"), at.playerBattleBase)
            assertEquals(off("childMapHeader"), at.childMapHeader); assertEquals(off("parentMapHeader"), at.parentMapHeader)
            // Not in the table: stays as it is. battleStatus above all (the reference's GLOBAL).
            assertEquals(game.battleStatus, at.battleStatus, "${game.name}: the battle flag does not move with the heap")
            assertEquals(0L, at.statStagesEnemy); assertEquals(0L, at.battleSubscriptMsgs)
            assertEquals(game.name, at.name); assertEquals(game.gameCodes, at.gameCodes)
            assertEquals(game.locationsResource, at.locationsResource); assertEquals(game.moveLevelsResource, at.moveLevelsResource)
        }
        // The two games have one table; only the fixed battle flag and the fixed addresses differ.
        assertSame(B2.pointerOffsets, W2.pointerOffsets)
        assertEquals(0x24L, B2.pointerOffsets?.pointer)
        // A map with no table is itself.
        assertSame(NdsGameMap.BW, NdsGameMap.BW.atPointerBase(base))
        assertSame(NdsGameMap.PLATINUM, NdsGameMap.PLATINUM.atPointerBase(base))
    }

    @Test
    fun `the pointer word is masked with 0xFFFFFF, and a word that is not a base is refused`() {
        val t = NdsGameMap.B2W2_POINTER
        // What both real dumps hold, and the base the fixed addresses were written for.
        assertEquals(0x204CC4L, t.baseOf(0x02204CC4L))
        assertEquals(0x204D04L, t.baseOf(0x02204D04L))
        // Not a base: nothing has set it, or it is not a main RAM address, or the table would not fit.
        assertNull(t.baseOf(0L), "not set yet")
        assertNull(t.baseOf(0x00204CC4L), "the top byte is not 0x02")
        assertNull(t.baseOf(0x12204CC4L)); assertNull(t.baseOf(0xFFFFFFFFL))
        assertNull(t.baseOf(0x02000000L), "a base of zero is nothing")
        assertNull(t.baseOf(0x023FFFFCL), "the table would run past the 4 MB")
        // The mask is the reference's 24 bits: a mirror of main RAM keeps bit 22, 0x604CC4, which is out of range,
        // where a 22-bit mask would have taken it for 0x204CC4.
        assertNull(t.baseOf(0x02604CC4L))
        // The last base the table still fits under (its largest offset is enemyBattleMonPID's 0x91C2C, and a u32 read there).
        val last = NdsPointerOffsets.MAIN_RAM_SIZE - 0x91C2CL - 4
        assertEquals(last, t.baseOf(0x02000000L or last))
        assertNull(t.baseOf(0x02000000L or (last + 1)))
    }

    // ------------------------------------------------------------- a fake RAM

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

    /**
     * [game] in a wild battle against Genesect, its heap at [base] (null: the game's own fixed addresses). The
     * pointer word is [pointerWord], which is the base by default; 0 leaves it unset. The battle flag is at the
     * game's fixed address whatever the heap is doing, as it is in the real game.
     */
    private fun world(game: NdsGameMap, base: Long?, pointerWord: Long? = base?.let { RAM + it }): Ram {
        val m = if (base != null) game.atPointerBase(base) else game
        val r = Ram()
        r.u32(NdsGameMap.CARTRIDGE_HEADER - RAM + 0x0C, game.gameCodes.first())
        pointerWord?.let { r.u32(game.pointerOffsets!!.pointer, it) }
        val leadPid = 0x0BADF00DL
        r.put(m.playerBase, Gen4.encodeParty(leadPid, 483, 5, 19, 100, listOf(33, 0, 0, 0), gen5 = true))
        r.u32(m.playerBattleBase, leadPid)
        r.u32(m.enemyBase, 0xC0FFEEL)
        r.u16(game.battleStatus, 0x2100)
        r.u16(m.enemyTrainerId, 0)                                          // wild
        val playerData = 0x300000L; val enemyData = 0x310000L; val enemyMon = 0x320000L
        r.u32(m.mainBattleDataPtr, RAM + playerData)
        r.u32(m.mainBattleDataPtr + 0x1C, RAM + enemyData)
        r.u32(enemyData, RAM + enemyMon); r.u32(enemyData + 4, 0)
        r.put(enemyMon, Gen4.encodeParty(0xC0FFEEL, 649, 40, 150, 150, listOf(1, 0, 0, 0), gen5 = true))
        r.u16(enemyData + 0x0E, 150); r.u16(enemyData + 0x10, 77)
        r.u32(enemyData + 0x20, 0)
        for (i in 0 until 8) { r.bytes[(enemyData + 0xFC + i).toInt()] = 6; r.bytes[(playerData + 0xFC + i).toInt()] = 6 }
        r.u16(m.childMapHeader, 427); r.u16(m.parentMapHeader, 427)
        r.u32(m.itemStartNoBattle, 17L or (2L shl 16))                      // two Potions
        r.bytes[m.badgeOffsets[0].toInt()] = 0b11
        return r
    }

    /** What [world] reads back as, from wherever its heap was. */
    private fun assertWorld(s: NdsTrackerState) {
        assertTrue(s.located, "party located")
        assertEquals(483, s.party.first().mon.species); assertEquals(5, s.party.first().mon.level)
        assertTrue(s.inBattle, "the battle flag is at its fixed address"); assertTrue(s.isWildBattle)
        val e = assertNotNull(s.enemy, "enemy card")
        assertEquals(649, e.mon.species); assertEquals(77, e.mon.curHp, "live HP from the battle data")
        assertEquals(427, s.mapId); assertEquals("Aspertia City", s.areaName)
        assertEquals(40 to 2, s.healPercent to s.healCount, "two Potions on a 100 HP lead, from the bag at the heap's own address")
        assertEquals(0b11, s.badges)
    }

    // ------------------------------------------------------------ the pointer path

    @Test
    fun `Black 2 with its heap in different places is found through the pointer each time, without a scan`() {
        for (base in listOf(0x204D04L, 0x204CC4L, 0x204C90L, 0x2050A0L)) {
            val t = NdsTracker(world(B2, base).reader(RAM), null, B2)
            assertWorld(t.read())
            assertEquals(NdsTracker.BaseSource.POINTER, t.baseSource, "base 0x%X".format(base))
            assertEquals(base, t.pointerBase)
            assertEquals(0, t.scans, "the pointer names the heap; a scan is never started")
            assertEquals(base + 0x19728L, t.live.playerBase)
        }
    }

    @Test
    fun `a heap that moves under a running tracker is followed on the next read`() {
        var now = world(B2, 0x204D04L)
        val t = NdsTracker(NdsMemoryReader { a, l -> now.reader(RAM).read(a, l) }, null, B2)
        assertWorld(t.read())
        assertEquals(0x204D04L + 0x19728L, t.live.playerBase)
        // A soft reset, a new game, a load: the game's heap is somewhere else and the old bytes are gone.
        now = world(B2, 0x204C50L)
        assertWorld(t.read())
        assertEquals(0x204C50L + 0x19728L, t.live.playerBase, "nothing was latched")
        assertEquals(NdsTracker.BaseSource.POINTER, t.baseSource)
        assertEquals(0, t.scans)
    }

    @Test
    fun `White 2 is read through the same pointer, and its battle flag is its own fixed address`() {
        val ram = world(W2, 0x204DC4L)
        // world() set White 2's flag at 0x1B5178 and nothing at Black 2's 0x1B5138.
        assertEquals(0x21, ram.bytes[W2.battleStatus.toInt() + 1].toInt())
        assertEquals(0, ram.bytes[B2.battleStatus.toInt() + 1].toInt())
        val r = ram.reader(RAM)
        assertSame(W2, NdsGameMap.detect(r))
        val t = NdsTracker(r, null, W2)
        assertWorld(t.read())
        assertEquals("Pokemon White 2", t.gameName)
        assertEquals(NdsTracker.BaseSource.POINTER, t.baseSource)
        assertEquals(0, t.scans)
        assertEquals(0x204DC4L + 0x19728L, t.live.playerBase)
    }

    @Test
    fun `the battle flag is read at its fixed address, never at the heap's shifted one`() {
        val ram = world(B2, 0x204CC4L)                       // the heap 0x40 low, the flag at 0x1B5138 as always
        ram.u16(B2.battleStatus, 0)                           // no battle there ...
        ram.u16(B2.battleStatus - 0x40, 0x2100)               // ... and "on" where a read that followed the heap would look
        val s = NdsTracker(ram.reader(RAM), null, B2).read()
        assertTrue(s.located)
        assertEquals(false, s.inBattle)
    }

    // ---------------------------------------------------- the paths without the pointer

    @Test
    fun `with no pointer word the fixed addresses are read where they are, and a heap that is elsewhere is scanned for`() {
        // The ROM the fixed addresses describe: nothing to scan for.
        val fixed = NdsTracker(world(B2, null).reader(RAM), null, B2)
        assertWorld(fixed.read())
        assertEquals(NdsTracker.BaseSource.STATIC, fixed.baseSource)
        assertEquals(0, fixed.scans)
        // The heap 0x40 low and no pointer to say so: the scan finds the party and the whole map follows its shift.
        val t = NdsTracker(world(B2, 0x204D04L - 0x40, pointerWord = 0L).reader(RAM), null, B2)
        assertWorld(t.read())
        assertEquals(NdsTracker.BaseSource.SCAN, t.baseSource)
        assertEquals(-0x40L, t.scanShift)
        assertEquals(1, t.scans)
        assertNull(t.pointerBase)
    }

    @Test
    fun `a pointer that becomes usable after a scan takes over from it`() {
        val ram = world(B2, 0x204CC4L, pointerWord = 0L)
        val t = NdsTracker(ram.reader(RAM), null, B2)
        assertWorld(t.read())
        assertEquals(NdsTracker.BaseSource.SCAN, t.baseSource)
        assertEquals(1, t.scans)
        ram.u32(B2.pointerOffsets!!.pointer, RAM + 0x204CC4L)
        assertWorld(t.read())
        assertEquals(NdsTracker.BaseSource.POINTER, t.baseSource, "the game's own pointer outranks a search")
        assertEquals(1, t.scans)
    }

    @Test
    fun `the real White 2 and Black 2 headers pick the maps that read through the pointer`() {
        // IRONMON_ROMS: the player's own dumps, whose cartridge header the DS copies to 0x023FFE00 in main RAM.
        val dir = System.getenv("IRONMON_ROMS")?.let { File(it) }?.takeIf { it.isDirectory } ?: return
        var seen = 0
        for ((file, expected) in listOf("white2-u.nds" to W2, "black2-u.nds" to B2)) {
            val rom = File(dir, file).takeIf { it.isFile } ?: continue
            val ram = Ram()
            ram.put(NdsGameMap.CARTRIDGE_HEADER - RAM, rom.inputStream().use { it.readNBytes(0x200) })
            val map = assertNotNull(NdsGameMap.detect(ram.reader(RAM)), file)
            assertSame(expected, map, file)
            assertNotNull(map.pointerOffsets, "$file is read through the pointer")
            seen++
        }
        println("POINTER_HEADERS checked=$seen")
    }

    @Test
    fun `the games with no pointer table are read as they always were`() {
        // Black (1): fixed addresses, and the scan when the fixed party is not there.
        val bw = NdsGameMap.BW
        val r = Ram()
        r.u32(NdsGameMap.CARTRIDGE_HEADER - RAM + 0x0C, NdsGameMap.CODE_BLACK)
        r.put(bw.playerBase, Gen4.encodeParty(0x0BADF00DL, 497, 30, 90, 100, listOf(1, 2, 0, 0), gen5 = true))
        val t = NdsTracker(r.reader(RAM), null, bw)
        assertTrue(t.read().located)
        assertEquals(NdsTracker.BaseSource.STATIC, t.baseSource)
        assertNull(t.pointerBase)
        assertEquals(0, t.scans)
    }
}
