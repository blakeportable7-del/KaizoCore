package com.ironmonone.tracker.nds

import com.ironmonone.tracker.LossCondition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * When the DS tracker calls a run over: BattleHandlerBase.checkIfRunHasEnded
 * (BattleHandlerBase.lua:226-247), on synthetic Platinum memory laid out at
 * MemoryAddresses[PLATINUM]. It checks only in a fetched battle, and it reads
 * the battle's copy of your party (playerBattleBase), not the party itself.
 */
class NdsRunOverTest {
    private val ram = ByteArray(0x400000)
    private val globalRel = 0x100000L
    private val versionRel = 0x180000L
    private val m = NdsGameMap.PLATINUM

    private fun putU32(off: Long, v: Long) { val i = off.toInt(); for (k in 0..3) ram[i + k] = (v ushr (8 * k)).toByte() }
    private fun putU16(off: Long, v: Int) { val i = off.toInt(); ram[i] = v.toByte(); ram[i + 1] = (v ushr 8).toByte() }
    private fun put(off: Long, bytes: ByteArray) = bytes.copyInto(ram, off.toInt())

    private fun mon(pid: Long, level: Int, hp: Int, maxHp: Int = 40) =
        Gen4.encodeParty(pid, species = 25, level = level, curHp = hp, maxHp = maxHp, moves = listOf(84, 0, 0, 0))

    /** Your party and the battle's copy of it, slot for slot, as (level, hp); PIDs 0x100 + slot. */
    private fun party(overworld: List<Pair<Int, Int>>, battle: List<Pair<Int, Int>>) {
        overworld.forEachIndexed { i, (lv, hp) -> put(versionRel + m.playerBase + i * 236L, mon(0x100L + i, lv, hp)) }
        battle.forEachIndexed { i, (lv, hp) -> put(versionRel + m.playerBattleBase + i * 236L, mon(0x100L + i, lv, hp)) }
    }

    private fun inBattle(on: Boolean) = putU16(m.battleStatus, if (on) 0x2100 else 0x2800)

    private fun tracker(c: LossCondition): NdsTracker {
        putU32(m.globalPointer, globalRel)
        putU32(globalRel + NdsGameMap.VERSION_POINTER_OFFSET, versionRel)
        // A wild opponent on the field, and your lead on your side.
        put(versionRel + m.enemyBase, Gen4.encodeParty(0x9000L, 16, 4, 20, 20, listOf(33, 0, 0, 0), pp = listOf(35, 0, 0, 0)))
        putU32(versionRel + m.enemyBattleMonPid, 0x9000L)
        putU32(versionRel + m.playerBattleMonPid, 0x100L)
        val reader = NdsMemoryReader { addr, len ->
            val off = (addr - 0x02000000L).toInt()
            if (off >= 0 && off + len <= ram.size) ram.copyOfRange(off, off + len) else ByteArray(0)
        }
        return NdsTracker(reader, null, m).also { it.lossCondition = c }
    }

    @Test
    fun `a faint outside battle never ends a DS run`() {
        party(overworld = listOf(10 to 0, 9 to 20), battle = listOf(10 to 0, 9 to 20))
        inBattle(false)
        val t = tracker(LossCondition.LEAD)
        assertNull(t.read().runOver, "checkIfRunHasEnded returns unless inBattleAndFetched")
    }

    @Test
    fun `the lead is the battle copy's first slot, checked during the battle`() {
        // Gen 4 writes the party back when the battle ends; in battle only the copy has the faint.
        party(overworld = listOf(10 to 40, 9 to 20), battle = listOf(10 to 0, 9 to 20))
        inBattle(true)
        val t = tracker(LossCondition.LEAD)
        val s = t.read()
        assertNotNull(s.runOver, "the lead fainted in battle")
        assertEquals(25, s.playerActive?.mon?.species, "the Pokemon on the field is the one logged")
    }

    @Test
    fun `highest level is picked at the first check, the later slot on a tie, and kept for the battle`() {
        party(overworld = listOf(10 to 40, 10 to 40), battle = listOf(10 to 40, 10 to 40))
        inBattle(true)
        val t = tracker(LossCondition.HIGHEST_LEVEL)
        assertNull(t.read().runOver)
        // pairs() visits slot 2 before the lead, and a tie keeps the first: slot 2 is watched.
        put(versionRel + m.playerBattleBase, mon(0x100L, 10, 0))
        assertNull(t.read().runOver, "the lead fainting is not the watched slot")
        put(versionRel + m.playerBattleBase + 236L, mon(0x101L, 10, 0))
        assertNotNull(t.read().runOver, "the watched slot fainted")
    }

    @Test
    fun `the watched slot is picked again after a battle ends`() {
        party(overworld = listOf(12 to 40, 10 to 40), battle = listOf(12 to 40, 10 to 40))
        inBattle(true)
        val t = tracker(LossCondition.HIGHEST_LEVEL)
        assertNull(t.read().runOver)                                   // picks the lead, 12 over 10
        put(versionRel + m.playerBattleBase + 236L, mon(0x101L, 20, 40)) // slot 2 grows mid-battle
        put(versionRel + m.playerBattleBase + 236L, mon(0x101L, 20, 0))
        assertNull(t.read().runOver, "the pick stays for the whole battle")
        inBattle(false); t.read()
        inBattle(true)
        assertNotNull(t.read().runOver, "the next battle watches the new highest, which is down")
    }

    @Test
    fun `entire party means every Pokemon in the battle copy at 0 HP`() {
        party(overworld = listOf(10 to 40, 9 to 20), battle = listOf(10 to 0, 9 to 5))
        inBattle(true)
        val t = tracker(LossCondition.ENTIRE_PARTY)
        assertNull(t.read().runOver)
        put(versionRel + m.playerBattleBase + 236L, mon(0x101L, 9, 0))
        assertNotNull(t.read().runOver)
    }

    @Test
    fun `Kaizo Doubles ends the run when either of the first two faints`() {
        party(overworld = listOf(10 to 40, 9 to 20, 8 to 30), battle = listOf(10 to 40, 9 to 20, 8 to 30))
        inBattle(true)
        val t = tracker(LossCondition.EITHER_OF_FIRST_TWO)
        assertNull(t.read().runOver)
        put(versionRel + m.playerBattleBase + 2 * 236L, mon(0x102L, 8, 0))
        assertNull(t.read().runOver, "the third slot is not one of the two")
        put(versionRel + m.playerBattleBase + 236L, mon(0x101L, 9, 0))
        assertNotNull(t.read().runOver, "the second slot fainted")
    }

    @Test
    fun `no fetch, no check`() {
        // The battle copy does not start with your lead yet: _tryToFetchBattleData refuses.
        party(overworld = listOf(10 to 40), battle = emptyList())
        put(versionRel + m.playerBattleBase, mon(0x777L, 10, 0))
        inBattle(true)
        assertNull(tracker(LossCondition.LEAD).read().runOver)
    }

    private fun trainer(id: Int) = putU16(versionRel + m.enemyTrainerId, id)

    @Test
    fun `beating the champion wins the run as the battle ends`() {
        party(overworld = listOf(40 to 40), battle = listOf(40 to 40))
        trainer(m.finalTrainerId)                // TrainerData FINAL_FIGHT_ID, 267 on Platinum
        inBattle(true)
        val t = tracker(LossCondition.LEAD)
        assertNull(t.read().runOver)
        // The champion's last Pokemon goes down; the battle's last read sees it at 0 HP.
        put(versionRel + m.enemyBase, Gen4.encodeParty(0x9000L, 16, 4, 0, 20, listOf(33, 0, 0, 0), pp = listOf(35, 0, 0, 0)))
        assertNull(t.read().runOver)
        inBattle(false)
        val s = t.read()
        assertEquals(NdsRunOver.WON, s.runOver, "setProgress(WON) then Program.onRunEnded")
        assertEquals(2, s.progress)
        assertEquals(16, s.lastBattleEnemy?.mon?.species, "the win is logged against the last opponent")
        assertEquals(25, s.lastBattlePlayer?.mon?.species)
        assertNull(t.read().runOver, "reported once, as the battle ends")
        assertEquals(2, t.read().progress)
    }

    @Test
    fun `leaving the final battle with the champion standing is not a win`() {
        // A state loaded or a restore point taken mid-fight ends the battle too.
        party(overworld = listOf(40 to 40), battle = listOf(40 to 40))
        trainer(m.finalTrainerId)
        inBattle(true)
        val t = tracker(LossCondition.LEAD)
        assertNull(t.read().runOver)
        inBattle(false)
        val s = t.read()
        assertNull(s.runOver, "the champion's Pokemon still had 20 HP")
        assertEquals(0, s.progress)
    }

    @Test
    fun `a run that has already ended is not won, and its battles count for nothing`() {
        party(overworld = listOf(40 to 40), battle = listOf(40 to 40))
        trainer(m.finalTrainerId)
        inBattle(true)
        val t = tracker(LossCondition.LEAD)
        t.runEnded = true                        // tracker.hasRunEnded()
        t.read()
        inBattle(false)
        val s = t.read()
        assertNull(s.runOver)
        assertEquals(0, s.progress)
        assertEquals(false, m.finalTrainerId in t.defeatedTrainers)
    }

    @Test
    fun `a lab rival is Past Lab, not a win`() {
        party(overworld = listOf(5 to 20), battle = listOf(5 to 20))
        trainer(m.labTrainerIds.first())
        inBattle(true)
        val t = tracker(LossCondition.LEAD)
        t.read()
        inBattle(false)
        val s = t.read()
        assertNull(s.runOver)
        assertEquals(1, s.progress)
    }

    @Test
    fun `Black 2 reads each party member's battle HP (real dump)`() {
        val dir = System.getenv("IRONMON_DUMPS")?.let { File(it) }?.takeIf { it.isDirectory } ?: return
        val f = File(dir, "b2-rand-rival-battle.bin").takeIf { it.isFile } ?: return
        val dump = f.readBytes()
        val r = NdsMemoryReader { addr, len ->
            val off = addr - 0x02000000L
            if (off < 0 || off >= dump.size) ByteArray(0) else dump.copyOfRange(off.toInt(), minOf(dump.size, (off + len).toInt()))
        }
        for (c in LossCondition.entries) {
            val t = NdsTracker(r, null, assertNotNull(NdsGameMap.detect(r))).also { it.lossCondition = c }
            val s = t.read()
            // The lead is at 8/19 in battle (19/19 in its party entry): nobody has fainted.
            assertNull(s.runOver, "$c")
            val p = assertNotNull(s.playerActive)
            assertEquals(483, p.mon.species); assertEquals(8, p.mon.curHp); assertEquals(19, p.mon.maxHp)
        }
    }
}
