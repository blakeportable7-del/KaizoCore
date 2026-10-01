package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * MainScreen.lua:860: the held item line is ItemData.GEN_5_ITEMS[heldItem].name on every
 * DS game. Gen 4 used to read Platinum's own table, which stops at 467, so a HeartGold or
 * SoulSilver Pokemon holding an Apricorn, an Apricorn Ball, a Sport or a Park Ball
 * (468-536) showed "#485" (parity audit, 2026-09-28). A party of one is planted behind the
 * Gen 4 pointer chain and read through the tracker, so the whole path is exercised.
 */
class NdsHeldItemTest {
    private val RAM = 0x02000000L
    private val globalRel = 0x100000L
    private val versionRel = 0x180000L

    private fun heldItemOf(map: NdsGameMap, item: Int): String {
        val ram = ByteArray(0x400000)
        fun putU32(rel: Long, v: Long) { for (i in 0 until 4) ram[(rel + i).toInt()] = ((v shr (8 * i)) and 0xFF).toByte() }
        putU32(map.globalPointer, globalRel)
        putU32(globalRel + NdsGameMap.VERSION_POINTER_OFFSET, versionRel)
        Gen4.encodeParty(pid = 0x1234567L, species = 155, level = 12, curHp = 30, maxHp = 34,
            moves = listOf(33, 43, 0, 0), heldItem = item).copyInto(ram, (versionRel + map.playerBase).toInt())
        val reader = NdsMemoryReader { address, length ->
            val off = (address - RAM).toInt()
            if (off >= 0 && off + length <= ram.size) ram.copyOfRange(off, off + length) else ByteArray(0)
        }
        val s = NdsTracker(reader, null, map).read()
        return s.party.single().itemName
    }

    @Test
    fun `HeartGold items past Platinum's table have their names`() {
        assertEquals("Red Apricorn", heldItemOf(NdsGameMap.HGSS, 485))
        assertEquals("Level Ball", heldItemOf(NdsGameMap.HGSS, 493))
        assertEquals("Sport Ball", heldItemOf(NdsGameMap.HGSS, 499))
        assertEquals("Park Ball", heldItemOf(NdsGameMap.HGSS, 500))
    }

    @Test
    fun `every DS game spells held items the way GEN_5_ITEMS does`() {
        assertEquals("Paralyze Heal", heldItemOf(NdsGameMap.PLATINUM, 22), "Platinum's own table says Parlyz Heal")
        assertEquals("Leftovers", heldItemOf(NdsGameMap.DP, 234))
        assertEquals("-", heldItemOf(NdsGameMap.PLATINUM, 0), "no item; the card draws the reference's ---")
        // GEN_5_ITEMS has no 113 (a Gen 4 filler id): the reference leaves the line blank.
        assertEquals("", heldItemOf(NdsGameMap.HGSS, 113))
    }
}
