package com.ironmonone.tracker.nds

import com.ironmonone.tracker.nuzlocke.NuzlockeEngine
import com.ironmonone.tracker.nuzlocke.NuzlockeLedger
import com.ironmonone.tracker.nuzlocke.NuzlockePreset
import com.ironmonone.tracker.nuzlocke.NuzlockeRules
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import com.ironmonone.tracker.nuzlocke.RunMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Gen 4 reads in a synthetic Platinum RAM laid out at MemoryAddresses[PLATINUM], as NdsPlayerPokemonTest lays it out:
 * the party scan before the first Pokemon (rc32 audit P2 #144), a transformed battler (P2 #145), and a party slot that
 * does not decode for one read (P3 #119).
 */
class NdsGen4ReadsTest {
    private val ram = ByteArray(0x400000)
    private val globalRel = 0x100000L
    private val versionRel = 0x180000L
    private val m = NdsGameMap.PLATINUM
    /** Reads of a scan's size (findParty's 128 KB chunks): what a scan costs. */
    private var bigReads = 0

    private fun putU32(off: Long, v: Long) { val i = off.toInt(); for (k in 0..3) ram[i + k] = (v ushr (8 * k)).toByte() }
    private fun putU16(off: Long, v: Int) { val i = off.toInt(); ram[i] = v.toByte(); ram[i + 1] = (v ushr 8).toByte() }
    private fun put(off: Long, bytes: ByteArray) = bytes.copyInto(ram, off.toInt())

    private fun mon(pid: Long, species: Int, hp: Int = 40) =
        Gen4.encodeParty(pid, species = species, level = 20, curHp = hp, maxHp = 40, moves = listOf(33, 0, 0, 0))

    private val reader = NdsMemoryReader { addr, len ->
        if (len >= 0x10000) bigReads++
        val off = (addr - 0x02000000L).toInt()
        if (off >= 0 && off + len <= ram.size) ram.copyOfRange(off, off + len) else ByteArray(0)
    }

    private fun chain() {
        putU32(m.globalPointer, globalRel)
        putU32(globalRel + NdsGameMap.VERSION_POINTER_OFFSET, versionRel)
    }

    private fun battle(on: Boolean) = putU16(m.battleStatus, if (on) 0x2100 else 0x2800)

    // ---------------------------------------------------------------- P2 #144

    @Test
    fun `before the first Pokemon a working chain makes no scan, and a battle with no party scans on the cooldown`() {
        chain(); battle(false)
        val t = NdsTracker(reader, null, m)
        repeat(NdsTracker.SCAN_EVERY) { assertFalse(t.read().located) }
        assertEquals(0, bigReads, "the title, the intro, a New Game: the chain leads to an empty party and nothing is swept")
        assertEquals(0, t.scans)
        // A battle with nothing at the chain's address: the chain is wrong for this ROM, and the scan looks, once a cooldown.
        battle(true)
        repeat(NdsTracker.SCAN_EVERY) { t.read() }
        assertEquals(1, t.scans)
        assertTrue(bigReads in 30..40, "one 4 MB walk in 128 KB reads, not one per read: $bigReads")
        // The starter appears where the chain says: the next read has it.
        battle(false)
        put(versionRel + m.playerBase, mon(0x100L, 387))
        assertTrue(t.read().located)
    }

    @Test
    fun `a chain that cannot be followed is scanned for on the cooldown, not on every read`() {
        battle(false)
        val t = NdsTracker(reader, null, m)
        repeat(NdsTracker.SCAN_EVERY) { t.read() }
        assertEquals(1, t.scans, "the first read, then the cooldown")
        assertTrue(bigReads in 30..40, "$bigReads")
    }

    // ---------------------------------------------------------------- P2 #145

    /** Your party (Squirtle 0x100, Pidgey 0x101) and its battle copy, a trainer's Pikachu 0x9000 and Ditto 0x9001, fetched. */
    private fun trainerBattle(): NdsTracker {
        chain()
        listOf(mon(0x100L, 7), mon(0x101L, 16)).forEachIndexed { i, b ->
            put(versionRel + m.playerBase + i * 236L, b)
            put(versionRel + m.playerBattleBase + i * 236L, b)
        }
        put(versionRel + m.enemyBase, mon(0x9000L, 25))
        put(versionRel + m.enemyBase + 236L, mon(0x9001L, 132))
        putU16(versionRel + m.enemyTrainerId, 300)
        putU32(versionRel + m.enemyBattleMonPid, 0x9001L)
        putU32(versionRel + m.playerBattleMonPid, 0x101L)
        battle(true)
        return NdsTracker(reader, null, m)
    }

    @Test
    fun `a transformed opponent stays on its card, and so does your transformed Pokemon`() {
        val t = trainerBattle()
        var s = t.read()
        assertEquals(132, s.enemy?.mon?.species, "the Ditto the trainer sent out")
        assertEquals(0x101L, s.playerActive?.mon?.pid)
        // Transform: each battler now carries the other's personality, which matches nobody in its own party.
        putU32(versionRel + m.enemyBattleMonPid, 0x101L)
        putU32(versionRel + m.playerBattleMonPid, 0x9001L)
        s = t.read()
        assertEquals(0x9001L, s.enemy?.mon?.pid, "not the trainer's lead, slot 0")
        assertEquals(0x101L, s.playerActive?.mon?.pid, "your Pidgey, not nobody")
        assertEquals(1, s.party.indexOfFirst { it.statStages.isNotEmpty() }, "its stages on it, not on the lead")
        // The battle ends and the next one starts with a PID that matches nobody: no memory carried over, slot 0 as before.
        battle(false); t.read()
        putU32(versionRel + m.enemyBattleMonPid, 0x7777L)
        battle(true)
        assertEquals(25, t.read().enemy?.mon?.species)
    }

    // ---------------------------------------------------------------- P3 #119

    @Test
    fun `a party slot that does not decode for one read is passed over and counted, and the rules see no whiteout`() {
        chain(); battle(false)
        listOf(mon(0x100L, 7), mon(0x101L, 16), mon(0x102L, 25)).forEachIndexed { i, b -> put(versionRel + m.playerBase + i * 236L, b) }
        val t = NdsTracker(reader, null, m)
        val ledger = NuzlockeLedger(RunMeta("nz-ds", "ds", "Test", NuzlockeRules.forPreset(NuzlockePreset.STANDARD), 1_000L)
            .also { it.system = NuzlockeSystem.GEN4 })
        val engine = NuzlockeEngine(ledger)
        var s = t.read()
        assertEquals(3, s.partyCount); assertEquals(3, s.party.size)
        engine.update(assertNotNull(NdsNuzlocke.snapshot(s)), 10_000L)
        assertEquals(3, ledger.roster.count { it.value.inParty })
        // The lead and the third down, the second caught mid-write: its checksum fails.
        put(versionRel + m.playerBase, mon(0x100L, 7, hp = 0))
        put(versionRel + m.playerBase + 2 * 236L, mon(0x102L, 25, hp = 0))
        val broken = versionRel + m.playerBase + 236L + 6
        ram[broken.toInt()] = (ram[broken.toInt()].toInt() xor 0xFF).toByte()
        s = t.read()
        assertEquals(listOf(0x100L, 0x102L), s.party.map { it.mon.pid }, "the slot after it is still read")
        assertEquals(3, s.partyCount, "and the one that failed is still counted")
        val snap = assertNotNull(NdsNuzlocke.snapshot(s))
        assertEquals(3, snap.partyCount)
        engine.update(snap, 10_700L)
        assertEquals(3, ledger.roster.count { it.value.inParty }, "nobody went to a box")
        assertTrue(ledger.events.none { it.kind == "whiteout" })
        // An empty slot (PID 0) still ends the party.
        put(versionRel + m.playerBase + 236L, ByteArray(236))
        assertEquals(1, t.read().partyCount)
    }
}
