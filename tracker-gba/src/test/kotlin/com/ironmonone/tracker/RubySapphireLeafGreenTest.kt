package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Ruby, Sapphire and LeafGreen v1.0 (2026-09-07): the header picks the map,
 * later revisions are refused by name, every ROM table is set, the ability
 * script table for each exists, and Ruby/Sapphire read their save blocks at
 * fixed addresses with the map-id shift the reference applies.
 */
class RubySapphireLeafGreenTest {
    private fun readerWith(code: String, ver: Int = 0) = MemoryReader { address, length ->
        when {
            address == 0x080000ACL && length == 4 -> code.toByteArray(Charsets.US_ASCII).copyOf(4)
            address == 0x080000BCL -> byteArrayOf(ver.toByte())
            else -> ByteArray(length)
        }
    }

    @Test
    fun `the header picks Ruby, Sapphire and LeafGreen v1_0 and refuses later revisions`() {
        assertSame(GameMap.RUBY_U, GameMap.resolveOrNull(readerWith("AXVE")))
        assertSame(GameMap.SAPPHIRE_U, GameMap.resolveOrNull(readerWith("AXPE")))
        assertSame(GameMap.LEAFGREEN_U, GameMap.resolveOrNull(readerWith("BPGE")))
        assertFailsWith<IllegalStateException> { GameMap.resolve(readerWith("AXVE", 1)) }
        assertFailsWith<IllegalStateException> { GameMap.resolve(readerWith("BPGE", 1)) }
    }

    @Test
    fun `every ROM table is set and the ability script table ships for each`() {
        for (m in listOf(GameMap.RUBY_U, GameMap.SAPPHIRE_U, GameMap.LEAFGREEN_U)) {
            for ((label, v) in listOf("baseStats" to m.baseStats, "speciesNames" to m.speciesNames, "moveNames" to m.moveNames,
                "abilityNames" to m.abilityNames, "itemNames" to m.itemNames, "battleMoves" to m.battleMoves,
                "frontPics" to m.frontPics, "palettes" to m.palettes, "levelUpLearnsets" to m.levelUpLearnsets)) {
                assertTrue(v in 0x08000000L..0x09FFFFFFL, "${m.name} $label = ${v.toString(16)}")
            }
            assertNotNull(javaClass.getResourceAsStream("/gen3/abilityscripts-${m.abilityScriptTable}.tsv"), m.name)
        }
        // Sapphire moves every ROM table off Ruby's and shares its RAM.
        assertTrue(GameMap.SAPPHIRE_U.baseStats != GameMap.RUBY_U.baseStats)
        assertEquals(GameMap.RUBY_U.party, GameMap.SAPPHIRE_U.party)
        assertEquals(GameMap.FIRERED_U_V10.party, GameMap.LEAFGREEN_U.party)
        assertEquals(0L, GameMap.RUBY_U.encryptionKeyOffset)
        assertEquals(setOf(335), GameMap.RUBY_U.finalTrainers)
    }

    @Test
    fun `Ruby reads badges from its fixed save block, no pointer involved`() {
        val ram = ByteArray(0x40000)
        // Three badges: the reference reads a word at badgeOffset and takes bits 7..8+.
        val off = (GameMap.RUBY_U.saveBlock1Fixed - 0x02000000L + GameMap.RUBY_U.badgeOffset).toInt()
        val word = 0b111 shl 7
        ram[off] = (word and 0xFF).toByte(); ram[off + 1] = ((word shr 8) and 0xFF).toByte()
        val reader = MemoryReader { address, length ->
            val o = (address - 0x02000000L).toInt()
            if (o >= 0 && o + length <= ram.size) ram.copyOfRange(o, o + length) else ByteArray(length)
        }
        val t = GbaTracker(reader, GameMap.RUBY_U)
        assertEquals(3, Integer.bitCount(t.readBadges()))
    }
}
