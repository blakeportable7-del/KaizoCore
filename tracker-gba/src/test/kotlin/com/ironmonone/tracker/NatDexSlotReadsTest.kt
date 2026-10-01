package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Nat. Dex reads that were left at 0 although the extension makes them
 * through the ROM's slot table (NatDexExtension.lua:17651-17745 and
 * 17534-17601): the starter preview, the Poke Balls pocket, the EXP tables
 * (and the growth rate that indexes them), gTakenDmg, the Battle Details
 * addresses and offsets, the friendship threshold, and on Emerald the
 * confirm-starter task. Pinned against both real 1.2.1 ROMs, then driven.
 */
class NatDexSlotReadsTest {
    private val frFile = File("C:/Users/bepor/IronMonOne/.vendor/roms/firered-natdex-121.gba")
    private val emFile = File("C:/Users/bepor/IronMonOne/.vendor/roms/emerald-natdex-121.gba")
    private val SB1 = 0x02025000L
    private val SB2 = 0x02024000L

    private class Mem(val rom: ByteArray) {
        val ram = HashMap<Long, Byte>()
        fun put8(a: Long, v: Int) { ram[a] = v.toByte() }
        fun put16(a: Long, v: Int) { put8(a, v and 0xFF); put8(a + 1, (v shr 8) and 0xFF) }
        fun put32(a: Long, v: Long) { for (i in 0 until 4) put8(a + i, ((v shr (i * 8)) and 0xFF).toInt()) }
        fun reader() = MemoryReader { address, length ->
            if (address >= 0x08000000L) {
                val off = (address - 0x08000000L).toInt()
                if (off >= 0 && off + length <= rom.size) rom.copyOfRange(off, off + length) else ByteArray(0)
            } else ByteArray(length) { ram[address + it] ?: 0 }
        }
    }

    private fun load(f: File): Mem? = if (f.exists()) Mem(f.readBytes()) else { println("SKIP: ${f.name} missing"); null }

    /** A party record: personality = trainer id = 24, so key 0 and substructures G, A, E, M. */
    private fun stageMon(mem: Mem, at: Long, l: PokemonDecoder.Layout, species: Int, level: Int, exp: Long = 0, status: Long = 0) {
        mem.put32(at, 24); mem.put32(at + 4, 24)
        mem.put16(at + l.enc, species); mem.put32(at + l.enc + 4, exp)
        mem.put32(at + l.status, status)
        mem.put8(at + l.level, level); mem.put16(at + l.curHp, 20); mem.put16(at + l.maxHp, 20)
    }

    @Test
    fun `FireRed Nat Dex resolves every slot the extension reads`() {
        val mem = load(frFile) ?: return
        val m = GameMap.resolve(mem.reader())
        assertEquals(0x08258B04L, m.expTables)
        assertEquals(listOf(0x13, 0x10, 0x12), listOf(m.growthRateOffset, m.genderRatioOffset, m.baseFriendshipOffset))
        assertEquals(0x02024020L, m.takenDmg)
        assertEquals(0x41, m.battleResultsTurnOffset)
        assertEquals(listOf(0x02022E09L, 0x020241E4L, 0x020242B4L), listOf(m.battleTerrain, m.weather, m.battleStructPtr))
        assertEquals(listOf(0x020240C4L, 0x020240A6L, 0x020240ACL, 0x020240D4L, 0x02024080L, 0x020241E8L, 0x02024146L),
            listOf(m.statuses3, m.sideStatuses, m.sideTimers, m.disableStructs, m.lockedMoves, m.wishFutureKnock, m.paydayMoney))
        assertEquals(0x54 to 0x1A9, m.status2Offset to m.wrappedByOffset)
        assertEquals(0x0203C8FCL, m.monSummaryScreen)
        assertEquals(0x0804313DL + 0x1A9, m.friendshipRequiredAddr)
        assertEquals(0x0203707CL, m.specialVarResult)
        assertEquals(0x0203707CL, m.specialVarResultAny)
        assertEquals(0x0203C4F0L, m.specialVarItemId)
        assertEquals(0L, m.confirmStarterTask, "FireRed publishes none; its preview reads the special var")
        assertEquals(0x580L to 13, m.bagBallsOffset to m.bagBallsSlots)
        assertEquals(0x18L + 0x10, m.pokedexOwnedOffset)
    }

    @Test
    fun `Emerald Nat Dex resolves every slot the extension reads`() {
        val mem = load(emFile) ?: return
        val m = GameMap.resolve(mem.reader())
        assertEquals(0x083246A0L, m.expTables)
        assertEquals(0x020241DCL, m.takenDmg)
        assertEquals(listOf(0x02022FC5L, 0x020243B0L, 0x02024484L), listOf(m.battleTerrain, m.weather, m.battleStructPtr))
        assertEquals(listOf(0x02024290L, 0x02024272L, 0x02024278L, 0x020242A0L, 0x0202424CL, 0x020243B4L, 0x02024312L),
            listOf(m.statuses3, m.sideStatuses, m.sideTimers, m.disableStructs, m.lockedMoves, m.wishFutureKnock, m.paydayMoney))
        assertEquals(0x54 to 0x237, m.status2Offset to m.wrappedByOffset)
        assertEquals(0x0806D259L + 0x1AD, m.friendshipRequiredAddr, "Emerald's offset is 0x1AD")
        assertEquals(0x03004C90L, m.gTasks)
        assertEquals(0x0813A1ECL, m.confirmStarterTask, "the slot's thumb address - 1, as the extension stores it")
        assertEquals(0L, m.specialVarResult, "RSE previews the starter through the task")
        assertEquals(0x020373ACL, m.specialVarResultAny)
        assertEquals(0x0203CC84L, m.specialVarItemId)
        assertEquals(0x7D0L to 16, m.bagBallsOffset to m.bagBallsSlots)
        assertEquals(0x18L + 0xC, m.pokedexOwnedOffset, "Emerald Nat. Dex keeps its owned bits at +0xC")
        assertEquals(0x0203CD24L, m.monSummaryScreen)
    }

    /** The Battle Details sizes and offsets not read into the map are the vanilla ones on both ROMs. */
    @Test
    fun `the Battle Details offsets we keep as constants are the ones the ROMs publish`() {
        for (f in listOf(frFile, emFile)) {
            val mem = load(f) ?: continue
            val words = (0x0800044CL..0x08000466L step 2).map { a -> mem.reader().read(a, 2).let { it.u16(0) } }
            // sizeofStatus3, SideStatuses, SideTimers, DisableStruct; timers Reflect, LightScreen,
            // Spikes, Safeguard, Mist; wish Future counter/source, Wish counter/source, Knock Off.
            assertEquals(listOf(4, 2, 0xC, 0x1C, 0, 2, 0xA, 6, 4, 0, 4, 0x20, 0x24, 0x29), words, f.name)
        }
    }

    @Test
    fun `the EXP bar, the friendship threshold, the balls pocket and the FireRed starter preview work`() {
        val mem = load(frFile) ?: return
        val m = GameMap.resolve(mem.reader())
        mem.put32(m.saveBlock1Ptr, SB1); mem.put32(m.saveBlock2Ptr, SB2)
        mem.put8(m.partyCount, 1)
        stageMon(mem, m.party, m.monLayout, species = 1, level = 5, exp = 150)   // Bulbasaur, medium slow
        mem.put16(SB1 + 0x580, 4); mem.put16(SB1 + 0x580 + 2, 5)                   // five Poke Balls (key 0)
        val t = GbaTracker(mem.reader(), m)
        val s = t.read()
        assertEquals(3, t.baseStats(1)?.growthRate, "Bulbasaur is medium slow")
        assertEquals(50, t.baseStats(1)?.baseFriendship, "Nat. Dex base friendship")
        assertEquals(31, t.baseStats(1)?.genderRatio)
        assertEquals(15 to 44, s.party[0].expNow to s.party[0].expTotal, "Lv5 135, Lv6 179")
        assertEquals(220, t.friendshipRequired())
        assertEquals(mapOf(4 to 5), t.bagBalls())
        assertTrue(t.hasCatchRates && t.hasBattleDetails)

        // Program.checkForStarterSelection, FRLG: gSpecialVar_Result 1 (YES), the species at
        // gameVarsOffset + 4, in the lab (map 5) with no party. A Nat. Dex species past 411.
        mem.put8(m.partyCount, 0); mem.put32(m.party, 0)
        mem.put16(m.mapHeader + 0x12, 5)
        mem.put16(m.specialVarResult, 1)
        mem.put16(SB1 + m.gameVarsOffset + 4, 412)
        t.read()
        assertEquals(412, t.read().starterOffered, "Turtwig in the ball")
    }

    /** Program.lua:343-346 and the extension's updateFriendshipValues keep only 2..220. */
    @Test
    fun `a friendship byte past 219 is refused`() {
        fun required(byte: Int) = GbaTracker(MemoryReader { a, n ->
            ByteArray(n) { if (a + it == GameMap.FIRERED_U_V10.friendshipRequiredAddr) byte.toByte() else 0 }
        }, GameMap.FIRERED_U_V10).friendshipRequired()
        assertEquals(160, required(159), "MakeEvolutionsFaster's 159")
        assertEquals(220, required(219))
        assertEquals(220, required(0xFF), "256 is not a threshold")
    }

    @Test
    fun `the Emerald starter preview finds the confirm task`() {
        val mem = load(emFile) ?: return
        val m = GameMap.resolve(mem.reader())
        mem.put16(m.mapHeader + 0x12, 17)
        mem.put32(m.gTasks, 0x0813A1EDL)       // Task_HandleConfirmStarterInput running
        mem.put16(m.gTasks + 8, 1)              // the middle ball: rival 523's lead
        val t = GbaTracker(mem.reader(), m)
        t.read()
        assertEquals(280, t.read().starterOffered, "Torchic")
        assertEquals(220, t.friendshipRequired())
    }

    @Test
    fun `the last attack, Battle Details and Catch Rates read the Nat Dex battle`() {
        val mem = load(frFile) ?: return
        val m = GameMap.resolve(mem.reader())
        mem.put8(m.partyCount, 1)
        stageMon(mem, m.party, m.monLayout, species = 25, level = 12)
        mem.put8(m.battlersCount, 2)
        mem.put32(m.battleMainFunc, m.handleTurnAction)
        mem.put16(m.battleMons, 25)
        val e = m.battleMons + m.battleMonSize
        mem.put16(e, 16); mem.put8(e + 0x2A, 7); mem.put16(e + 0x28, 20); mem.put16(e + 0x2C, 20)
        mem.put32(e + 0x4C, 0x40)               // not the status in this struct: must not show PAR
        stageMon(mem, m.enemyParty, m.monLayout, species = 16, level = 7, status = 0x2)   // asleep
        mem.put32(m.battleMons + m.status2Offset, 0x1)                                       // you are confused
        mem.put8(m.battleResults + 0x41, 1)
        val t = GbaTracker(mem.reader(), m)
        t.read(); assertTrue(t.read().inBattle)
        // Pidgey's Tackle takes 12: gTakenDmg climbs while the enemy (battler 1) attacks...
        mem.put8(m.battlerAttacker, 1); mem.put16(m.battleResults + 0x24, 33); mem.put16(m.takenDmg, 12)
        t.read()
        // ...and the line appears once the next turn starts (offsetBattleResultsCurrentTurn 0x41).
        mem.put8(m.battleResults + 0x41, 2)
        val s = t.read()
        assertEquals("Tackle", s.lastAttackMove)
        assertEquals(12, s.lastAttackDamage)

        val d = assertNotNull(t.battleDetails())
        assertTrue(d.mons[0].any { it.text.startsWith("Confused") }, "status2 at 0x54: ${d.mons[0]}")
        assertEquals(3, d.turn)
        assertEquals("SLP", assertNotNull(t.catchRates()).status, "the party slot's status, as the reference reads it")
    }
}
