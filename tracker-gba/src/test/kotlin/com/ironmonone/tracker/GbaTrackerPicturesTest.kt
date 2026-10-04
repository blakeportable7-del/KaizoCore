package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tracker hands the card the game's picture of each Pokemon it reads (TrackedMon.picture, EnemyInfo.picture): a
 * party and a wild battle written into memory over the real FireRed 1.0 cartridge, which is skipped when the dump is not
 * here. The pictures themselves are proven in Gen3PicturesRomTest; this proves who gets which.
 */
class GbaTrackerPicturesTest {
    private val roms = System.getenv("IRONMON_ROMS")?.let(::File)?.takeIf { it.isDirectory } ?: File("C:/Users/bepor/IronMonOne/.vendor/roms")
    private val map = GameMap.FIRERED_U_V10

    /** A party struct (100 bytes) for [pid] and [otId], its substructures in the order the personality picks. */
    private fun encodeMon(pid: Long, otId: Long, species: Int, level: Int = 30, hp: Int = 50): ByteArray {
        val plain = ByteArray(48)
        val g = PokemonDecoder.SLOT_OF[(pid % 24).toInt()][0] * 12
        plain[g] = species.toByte(); plain[g + 1] = (species shr 8).toByte()
        val mon = ByteArray(100)
        mon.putU32(0, pid); mon.putU32(4, otId)
        for (w in 0 until 12) mon.putU32(0x20 + w * 4, plain.u32(w * 4) xor (pid xor otId))
        mon[0x54] = level.toByte(); mon[0x56] = hp.toByte(); mon[0x58] = hp.toByte()
        return mon
    }

    /** Shiny when the trainer id is the personality itself; never with id 0 and halves this far apart. */
    private val shinyPid = 0x12345678L
    private fun plainPid(from: Long, ok: (Long) -> Boolean = { true }) =
        (from..from + 0xFFFFFF).first { ((it xor (it ushr 16)) and 0xFFFF) >= 8 && ok(it) }

    @Test
    fun `the party and a wild opponent carry the game's picture where it is not the plain one`() {
        val f = Dumps.file(roms, "firered-u-v10.gba") ?: run { println("firered-u-v10.gba not here; skipped"); return }
        val rom = f.readBytes()
        val unownPid = plainPid(0x01000000) { Gen3Pictures.unownLetter(it) == 5 }   // F
        val deoxysPid = plainPid(0x02000000)
        val pikachuPid = plainPid(0x03000000)
        val party = encodeMon(shinyPid, shinyPid, 6) + encodeMon(unownPid, 0, Gen3Pictures.UNOWN) +
            encodeMon(deoxysPid, 0, Gen3Pictures.DEOXYS) + encodeMon(pikachuPid, 0, 25)
        val foePid = 0x0BADCAFEL
        val foe = ByteArray(map.battleMonSize).also {
            it[0] = 25; it[map.battleMonHp + 2] = 7; it[map.battleMonHp] = 18; it[map.battleMonHp + 4] = 22
            it.putU32(map.status2Offset - 8, foePid)
        }
        val mem = HashMap<Long, ByteArray>()
        mem[map.partyCount] = byteArrayOf(4)
        mem[map.party] = party
        mem[map.battlersCount] = byteArrayOf(2)
        mem[map.battleMons] = byteArrayOf(6, 0)
        mem[map.battleTypeFlags] = ByteArray(4)
        mem[map.battleMons + map.battleMonSize] = foe
        mem[map.enemyParty] = encodeMon(foePid, foePid, 25, level = 7, hp = 22) + ByteArray(500)
        val m = MemoryReader { address, length ->
            mem[address]?.let { if (it.size >= length) return@MemoryReader it.copyOf(length) }
            val o = address - 0x08000000L
            if (o >= 0 && o + length <= rom.size) rom.copyOfRange(o.toInt(), (o + length).toInt()) else ByteArray(0)
        }
        val t = GbaTracker(m, map)
        t.read()
        val s = t.read()
        assertTrue(s.inBattle && s.isWildBattle)
        val tables = assertNotNull(Gen3Pictures.tables(m, map))
        fun expected(species: Int, shiny: Boolean, pid: Long, gameForm: Boolean) =
            assertNotNull(Gen3Pictures.picture(m, tables, species, shiny, pid, gameForm), "$species")

        assertEquals(listOf(6, Gen3Pictures.UNOWN, Gen3Pictures.DEOXYS, 25), s.party.map { it.mon.species })
        assertTrue(s.party[0].mon.shiny && s.party.drop(1).none { it.mon.shiny })
        assertEquals(expected(6, true, shinyPid, true), s.party[0].picture, "a shiny Charizard in its shiny colors")
        assertEquals(expected(Gen3Pictures.UNOWN, false, unownPid, true), s.party[1].picture, "Unown in its letter, F")
        assertEquals(expected(Gen3Pictures.DEOXYS, false, deoxysPid, true), s.party[2].picture, "your Deoxys in FireRed's Attack form")
        assertNull(s.party[3].picture, "a plain Pikachu: the card's own picture")

        // The opponent: shiny by its party entry, found by its personality; the card's own picture once it is not.
        val e = assertNotNull(s.enemy)
        assertEquals(foePid, e.pid)
        assertEquals(expected(25, true, foePid, true), e.picture)
        mem[map.enemyParty] = encodeMon(foePid, 0, 25, level = 7, hp = 22) + ByteArray(500)
        assertNull(t.read().enemy!!.picture, "not shiny: nothing of its own")
        // An opposing Deoxys in its Normal form, as FireRed's battle screen draws it (rc34 showed the Attack form).
        foe[0] = Gen3Pictures.DEOXYS.toByte(); foe[1] = (Gen3Pictures.DEOXYS shr 8).toByte()
        mem[map.enemyParty] = encodeMon(foePid, 0, Gen3Pictures.DEOXYS, level = 7, hp = 22) + ByteArray(500)
        val deoxysFoe = assertNotNull(t.read().enemy)
        assertEquals(Gen3Pictures.DEOXYS, deoxysFoe.species)
        assertEquals(Gen3Pictures.picture(m, tables, Gen3Pictures.DEOXYS, false, foePid, gameForm = false), deoxysFoe.picture)
        assertNotEquals(expected(Gen3Pictures.DEOXYS, false, foePid, true), deoxysFoe.picture, "not the Attack form")
    }
}
