package com.ironmonone.tracker.nds

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The DS Nuzlocke adapter over a real dump of DS main RAM (2026-09-30): the tracker reads the Kaizo-randomized Black 2
 * that IRONMON_DUMPS holds (the rival's first battle in Aspertia City, starter Dialga against Larvitar), and what the
 * adapter makes of that state is checked against what is known of it. Skipped without the dump.
 */
class NdsDumpNuzlockeTest {
    private fun reader(f: File): NdsMemoryReader {
        val ram = f.readBytes()
        return NdsMemoryReader { addr, len ->
            val off = addr - 0x02000000L
            if (off < 0 || off >= ram.size) ByteArray(0) else ram.copyOfRange(off.toInt(), minOf(ram.size, (off + len).toInt()))
        }
    }

    private fun dump(name: String): File? {
        val f = Dumps.dump(name)
        if (f == null) println("SKIP: $name is not on this machine (IRONMON_DUMPS)")
        return f
    }

    @Test
    fun `a real Black 2 rival battle is a Generation 5 trainer fight of the first rival`() {
        val r = reader(dump("b2-rand-rival-battle.bin") ?: return)
        val s = NdsTracker(r, null, assertNotNull(NdsGameMap.detect(r))).read()
        assertEquals("Pokemon Black 2", s.gameName)
        val snap = assertNotNull(NdsNuzlocke.snapshot(s))
        assertEquals("Aspertia City", snap.area.name)
        assertTrue(snap.inBattle); assertFalse(snap.wild)
        val dialga = snap.party.single()
        assertEquals(483, dialga.species); assertEquals(5, dialga.level); assertEquals(19, dialga.hp)
        assertNull(dialga.gender, "Dialga has no gender")
        assertEquals(listOf(8, 16), dialga.types, "Steel and Dragon, from the species table")
        val larvitar = assertNotNull(snap.enemy)
        assertEquals(246, larvitar.species); assertEquals(8, larvitar.level)
        assertEquals(listOf(5, 4), larvitar.types, "Rock and Ground")
        val opp = assertNotNull(snap.opponent)
        assertTrue(opp.trainerId in listOf(161, 162, 163), "the lab fight's ids, whichever starter: ${opp.trainerId}")
        assertEquals("hugh1", opp.bossKey)
        assertEquals("Rival", opp.group)
        assertTrue(opp.label.startsWith("Rival Hugh"), opp.label)
        assertEquals("b2w2", snap.caps!!.game)
        assertNull(snap.ballCount, "no ball count on a DS game")
    }

    @Test
    fun `a real Black 2 before the starter gives the adapter nothing to say`() {
        val r = reader(dump("b2-clean-intro.bin") ?: return)
        val s = NdsTracker(r, null, assertNotNull(NdsGameMap.detect(r))).read()
        assertNull(NdsNuzlocke.snapshot(s), "no party located: no snapshot")
    }
}
