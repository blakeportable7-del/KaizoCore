package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Program.lua:601-611, HeartGold and SoulSilver: the Bug Catching area is named after the
 * contest's weekday. The IronMON patch writes the real day (versionRel + 0xDF4) only while
 * the player stands in either contest gatehouse (child maps 102 and 104), so the tracker
 * reads it there and keeps it; 2, 4 and 6 name Tuesday, Thursday and Saturday, anything
 * else (and nothing read yet) Tuesday. Our area line used to say plain "Bug Catching".
 */
class NdsBugCatchingTest {
    private val RAM = 0x02000000L
    private val globalRel = 0x100000L
    private val versionRel = 0x180000L
    private val map = NdsGameMap.HGSS
    private val ram = ByteArray(0x400000)

    init {
        putU32(map.globalPointer, globalRel)
        putU32(globalRel + NdsGameMap.VERSION_POINTER_OFFSET, versionRel)
        // A party, or read() stops before the location is looked at.
        Gen4.encodeParty(pid = 0x1234567L, species = 155, level = 12, curHp = 30, maxHp = 34,
            moves = listOf(33, 43, 0, 0)).copyInto(ram, (versionRel + map.playerBase).toInt())
    }

    private fun putU32(rel: Long, v: Long) { for (i in 0 until 4) ram[(rel + i).toInt()] = ((v shr (8 * i)) and 0xFF).toByte() }
    private fun putU16(rel: Long, v: Int) { ram[rel.toInt()] = (v and 0xFF).toByte(); ram[(rel + 1).toInt()] = (v shr 8).toByte() }
    private fun at(child: Int) = putU16(versionRel + map.childMapHeader, child)
    private fun day(d: Int) = putU16(versionRel + map.dayOfWeek, d)
    private val tracker = NdsTracker({ address, length ->
        val off = (address - RAM).toInt()
        if (off >= 0 && off + length <= ram.size) ram.copyOfRange(off, off + length) else ByteArray(0)
    }, null, map)
    private fun area(): String = tracker.read().areaName

    @Test
    fun `the contest area takes the weekday read in the gatehouse`() {
        day(4); at(104)
        assertEquals("Route 36", area(), "the gatehouse is still Route 36")
        at(487)
        assertEquals("Thurs Bug Catching", area())
        day(6)                                                // written outside a gatehouse: not read
        assertEquals("Thurs Bug Catching", area())
        at(102)
        area()
        at(487)
        assertEquals("Sat Bug Catching", area(), "read again in the Route 35 gatehouse")
    }

    @Test
    fun `before any weekday is read, and on a day without a contest, it is Tuesday`() {
        day(4); at(487)
        assertEquals("Tues Bug Catching", area(), "never stood in a gatehouse: Program.lua:86 starts at 2")
        day(3); at(104); area(); at(487)
        assertEquals("Tues Bug Catching", area(), "Wednesday has no contest name")
    }

    @Test
    fun `Platinum never reads the weekday`() {
        assertEquals(0L, NdsGameMap.PLATINUM.dayOfWeek)
        assertEquals(0L, NdsGameMap.DP.dayOfWeek)
        assertEquals(0xDF4L, map.dayOfWeek)
    }
}
