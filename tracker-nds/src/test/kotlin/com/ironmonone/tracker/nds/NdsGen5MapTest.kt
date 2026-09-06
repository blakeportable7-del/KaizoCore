package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Gen 5 through the tracker: absolute addresses, a 220-byte party, and the
 * battle read that follows pointers out of mainBattleDataPtr the way
 * BattleHandlerGen5 does - laid out here in a fake RAM exactly as the
 * reference describes it, so a wrong offset fails loudly.
 */
class NdsGen5MapTest {

    private val RAM = 0x02000000L
    private val map = NdsGameMap.BW

    @Test
    fun `Gen 5 codes pick their maps, and the maps are absolute`() {
        assertSame(NdsGameMap.BW, NdsGameMap.forCode(NdsGameMap.CODE_BLACK))
        assertSame(NdsGameMap.WHITE, NdsGameMap.forCode(NdsGameMap.CODE_WHITE))
        assertSame(NdsGameMap.B2W2, NdsGameMap.forCode(NdsGameMap.CODE_BLACK2))
        assertSame(NdsGameMap.WHITE2, NdsGameMap.forCode(NdsGameMap.CODE_WHITE2))
        for (m in listOf(NdsGameMap.BW, NdsGameMap.B2W2)) {
            assertTrue(m.absolute); assertEquals(5, m.generation)
            assertEquals(220, m.entrySize); assertEquals(649, m.maxSpecies)
            assertTrue(m.mainBattleDataPtr != 0L && m.playerBattleBase != 0L)
        }
        for (m in listOf(NdsGameMap.PLATINUM, NdsGameMap.HGSS)) {
            assertTrue(!m.absolute); assertEquals(236, m.entrySize)
        }
    }

    @Test
    fun `BW and B2W2 offsets match the reference GLOBAL blocks`() {
        assertEquals(0x2349B4L, NdsGameMap.BW.playerBase)
        assertEquals(0x26B254L, NdsGameMap.BW.enemyBase)
        assertEquals(0x269838L, NdsGameMap.BW.mainBattleDataPtr)
        assertEquals(0x1D0798L, NdsGameMap.BW.battleStatus)
        assertEquals(listOf(0x23CDB0L), NdsGameMap.BW.badgeOffsets)
        assertEquals(0x21E42CL, NdsGameMap.B2W2.playerBase)
        assertEquals(0x2573ACL, NdsGameMap.B2W2.mainBattleDataPtr)
        assertEquals(0x1B5138L, NdsGameMap.B2W2.battleStatus)
        assertEquals("/gen5/movelevels-b2w2.tsv", NdsGameMap.B2W2.moveLevelsResource)
    }

    // ------------------------------------------------------------ a fake Black

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

    private fun blackInBattle(): Ram {
        val r = Ram()
        // Header: this is Black.
        r.u32(NdsGameMap.CARTRIDGE_HEADER - RAM + 0x0C, NdsGameMap.CODE_BLACK)
        // Party lead: Serperior, at the absolute party base.
        val leadPid = 0x0BADF00DL
        r.put(map.playerBase, Gen4.encodeParty(leadPid, 497, 30, 90, 100, listOf(1, 2, 0, 0), gen5 = true))
        // The reference's fetch guard: battle-side PID equals the lead's, enemy side has one.
        r.u32(map.playerBattleBase, leadPid)
        r.u32(map.enemyBase, 0xC0FFEEL)
        r.u16(map.battleStatus, 0x2100)
        r.u16(map.enemyTrainerId, 0)                     // wild
        // Battler records: player at +0, opponent at +0x1C, each a pointer.
        val playerData = 0x300000L; val enemyData = 0x310000L; val enemyMon = 0x320000L
        r.u32(map.mainBattleDataPtr, RAM + playerData)
        r.u32(map.mainBattleDataPtr + 0x1C, RAM + enemyData)
        // Enemy battle data: -> Genesect's struct; live HP differs from the struct's.
        r.u32(enemyData, RAM + enemyMon)
        r.u32(enemyData + 4, 0)                          // no Illusion
        r.put(enemyMon, Gen4.encodeParty(0xC0FFEEL, 649, 40, 150, 150, listOf(1, 0, 0, 0), gen5 = true))
        r.u16(enemyData + 0x0E, 150); r.u16(enemyData + 0x10, 77)
        r.u32(enemyData + 0x20, 0)
        r.u16(enemyData + 0x104, 558); r.bytes[(enemyData + 0x106).toInt()] = 4      // Fusion Flare, 4 PP
        r.u16(enemyData + 0x104 + 14, 559); r.bytes[(enemyData + 0x106 + 14).toInt()] = 5
        for (i in 0 until 8) r.bytes[(enemyData + 0xFC + i).toInt()] = 6
        r.bytes[(enemyData + 0xFC).toInt()] = 8                                     // ATK +2
        for (i in 0 until 8) r.bytes[(playerData + 0xFC + i).toInt()] = 6
        // Three badges.
        r.bytes[map.badgeOffsets[0].toInt()] = 0b111
        return r
    }

    @Test
    fun `detect says Black and the party is read at the absolute base`() {
        val r = blackInBattle().reader(RAM)
        assertSame(NdsGameMap.BW, NdsGameMap.detect(r))
        val s = NdsTracker(r, null, map).read()
        assertTrue(s.located)
        assertEquals(497, s.party.first().mon.species)
        assertEquals("SERPERIOR", s.party.first().speciesName, "Gen 5 names were not loaded")
        assertEquals("BW", s.badgeSet)
        assertEquals(0b111, s.badges)
    }

    @Test
    fun `the opponent comes through the battler pointers with live values`() {
        val s = NdsTracker(blackInBattle().reader(RAM), null, map).read()
        assertTrue(s.inBattle); assertTrue(s.isWildBattle)
        val e = assertNotNull(s.enemy)
        assertEquals(649, e.mon.species)
        assertEquals("GENESECT", e.speciesName)
        assertEquals(77, e.mon.curHp, "live HP from battle data, not the struct's 150")
        assertEquals(listOf("Fusion Flare", "Fusion Bolt"), e.moves.map { it.name })
        assertEquals(listOf(4, 5), e.mon.pp.take(2))
        assertEquals(8, e.statStages["ATK"])
        assertEquals(6, e.statStages["SPE"])
    }

    // --------------------------------------------------- ability reveals (Gen 5)

    private fun blackWithAbilities(playerAbility: Int, enemyAbility: Int): Ram {
        val r = blackInBattle()
        r.put(map.playerBase, Gen4.encodeParty(0x0BADF00DL, 497, 30, 90, 100, listOf(1, 2, 0, 0), abilityId = playerAbility, gen5 = true))
        r.put(0x320000L, Gen4.encodeParty(0xC0FFEEL, 649, 40, 150, 150, listOf(1, 0, 0, 0), abilityId = enemyAbility, gen5 = true))
        return r
    }

    @Test
    fun `an ability trigger equal to the opponent's ability reveals it once`() {
        val r = blackWithAbilities(playerAbility = 65, enemyAbility = 88)      // Overgrow / Download
        val tracker = NdsTracker(r.reader(RAM), null, map)
        assertNull(tracker.read().abilityRevealed, "nothing has triggered")
        r.u16(map.abilityTriggerStart + 4, 88)                                 // opponent slot: Download
        assertEquals(649 to "Download", tracker.read().abilityRevealed)
        assertNull(tracker.read().abilityRevealed, "the same word again is not a new reveal")
        r.u16(map.abilityTriggerStart + 4, 0)
        assertNull(tracker.read().abilityRevealed, "zero reveals nothing")
        r.u16(map.abilityTriggerStart + 4, 88)
        // The reference returns on a zero word WITHOUT updating lastAbilityValue,
        // so 88 -> 0 -> 88 is still "the same word": no second reveal.
        assertNull(tracker.read().abilityRevealed, "a zero in between does not make the same word new (reference behaviour)")
    }

    @Test
    fun `a word that is not the battler's ability reveals nothing`() {
        val r = blackWithAbilities(playerAbility = 65, enemyAbility = 88)
        r.u16(map.abilityTriggerStart + 4, 46)                                 // Pressure: not Genesect's here
        assertNull(NdsTracker(r.reader(RAM), null, map).read().abilityRevealed)
    }

    @Test
    fun `Trace on the player's side reveals the traced opponent's ability`() {
        val r = blackWithAbilities(playerAbility = 36, enemyAbility = 88)
        r.u16(map.abilityTriggerStart, 88)                                     // player slot shows the copied ability
        assertEquals(649 to "Download", NdsTracker(r.reader(RAM), null, map).read().abilityRevealed)
        // The player's own ability activating is tracked for the player's species, as the reference does.
        val r2 = blackWithAbilities(playerAbility = 65, enemyAbility = 88)
        r2.u16(map.abilityTriggerStart, 65)
        assertEquals(497 to "Overgrow", NdsTracker(r2.reader(RAM), null, map).read().abilityRevealed)
    }

    @Test
    fun `leaving the battle forgets the last trigger words`() {
        val r = blackWithAbilities(playerAbility = 65, enemyAbility = 88)
        r.u16(map.abilityTriggerStart + 4, 88)
        val tracker = NdsTracker(r.reader(RAM), null, map)
        assertEquals(649 to "Download", tracker.read().abilityRevealed)
        r.u16(map.battleStatus, 0)                                             // battle over
        assertNull(tracker.read().abilityRevealed)
        r.u16(map.battleStatus, 0x2100)                                        // next battle, word still 88 in RAM
        assertEquals(649 to "Download", tracker.read().abilityRevealed, "a new battle starts with no memory of the old word")
    }

    // ------------------------------------------------------------- White is +0x20

    @Test
    fun `White is Black shifted by 0x20 everywhere, and reads a RAM laid out that way`() {
        val w = NdsGameMap.WHITE
        assertSame(w, NdsGameMap.forCode(NdsGameMap.CODE_WHITE))
        assertEquals(map.playerBase + 0x20, w.playerBase)
        assertEquals(map.battleStatus + 0x20, w.battleStatus)
        assertEquals(map.abilityTriggerStart + 0x20, w.abilityTriggerStart)
        assertEquals(map.badgeOffsets[0] + 0x20, w.badgeOffsets[0])
        assertEquals(0L, w.battleSubscriptMsgs, "unset fields stay unset")
        assertEquals(NdsGameMap.B2W2.playerBase + 0x80, NdsGameMap.WHITE2.playerBase)
        // The fake Black, moved up by 0x20 in RAM and stamped as White, reads as White.
        val b = blackInBattle()
        val r = Ram()
        System.arraycopy(b.bytes, 0, r.bytes, 0x20, b.bytes.size - 0x20)
        r.u32(NdsGameMap.CARTRIDGE_HEADER - RAM + 0x0C, NdsGameMap.CODE_WHITE)
        // The data blocks moved with everything else; re-aim the pointers at them.
        r.u32(w.mainBattleDataPtr, RAM + 0x300020L); r.u32(w.mainBattleDataPtr + 0x1C, RAM + 0x310020L)
        r.u32(0x310020L, RAM + 0x320020L)
        val s = NdsTracker(r.reader(RAM), null, NdsGameMap.detect(r.reader(RAM))!!).read()
        assertEquals(497, s.party.first().mon.species)
        assertEquals(649, assertNotNull(s.enemy).mon.species)
        assertEquals(77, s.enemy!!.mon.curHp)
        // And the same RAM read with Black's map is off by 0x20: no party.
        assertTrue(!NdsTracker(r.reader(RAM), null, map).read().located)
    }

    @Test
    fun `a Platinum map read against this RAM finds nothing`() {
        // The proof the map is consulted: Platinum's pointer chain is empty here.
        val s = NdsTracker(blackInBattle().reader(RAM), null, NdsGameMap.PLATINUM).read()
        assertTrue(!s.located)
    }
}
