package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ROM-side halves of the side screens, read out of a real cartridge dump
 * instead of a running game: the trainer table behind Trainers On Route and
 * Trainer Info, the catch-rate and growth-rate bytes behind Catch Rates and
 * Team View's EXP bar, and the experience tables themselves. Needs
 * IRONMON_ROMS=<dir with emerald-u.gba, firered-u-v10.gba>; skipped without it,
 * the way PrepOptionsTest is.
 *
 * This is what can be verified without driving the emulator. The RAM-side
 * addresses these screens also use are covered by AddressAuditTest.
 */
class RomDataTest {
    /** A reader over the ROM file alone: 0x08000000.. is the cartridge, everything else reads zero. */
    private fun romReader(f: File): MemoryReader {
        val rom = f.readBytes()
        return MemoryReader { addr, len ->
            val at = addr - 0x08000000L
            if (at < 0 || at >= rom.size) ByteArray(len)
            else ByteArray(len) { i -> if (at + i < rom.size) rom[(at + i).toInt()] else 0 }
        }
    }

    private fun rom(name: String): File? =
        System.getenv("IRONMON_ROMS")?.let { File(it, name) }?.takeIf { it.isFile }

    @Test
    fun `Emerald's trainer table decodes the real gym leaders`() {
        val f = rom("emerald-u.gba") ?: return
        val t = GbaTracker(romReader(f), GameMap.EMERALD_U)

        // Wattson, Mauville's leader: the team the app showed on the demo, straight from the ROM.
        val wattson = assertNotNull(t.trainer(267))
        assertEquals("LEADER", wattson.className)
        assertEquals("WATTSON", wattson.name)
        assertEquals(listOf("VOLTORB", "ELECTRIKE", "MAGNETON", "MANECTRIC"), wattson.party.map { t.speciesName(it.species) })
        assertEquals(listOf(20, 20, 22, 24), wattson.party.map { it.level })
        assertEquals("SITRUS BERRY", t.itemName(wattson.party.last().heldItem))
        assertEquals("Smart", wattson.aiLabel)
        assertEquals(20 to 24, wattson.minLevel to wattson.maxLevel)
        assertTrue(wattson.party.all { it.moves.size == 4 }, "a gym leader carries custom moves")
        assertEquals("THUNDER WAVE", t.moveName(wattson.party[2].moves[2]))

        // Roxanne, the first leader, and Steven, the last trainer of the game.
        val roxanne = assertNotNull(t.trainer(265))
        assertEquals("ROXANNE", roxanne.name); assertEquals("LEADER", roxanne.className)
        assertTrue(roxanne.party.isNotEmpty() && roxanne.party.all { it.level <= 16 })
    }

    @Test
    fun `every Emerald trainer id in the route tables reads a sane entry`() {
        val f = rom("emerald-u.gba") ?: return
        val t = GbaTracker(romReader(f), GameMap.EMERALD_U)
        var seen = 0
        for (mapId in 0..400) {
            for (id in t.trainersOnRoute(mapId)) {
                val info = t.trainer(id) ?: continue
                seen++
                assertTrue(info.party.size in 1..6, "trainer $id has ${info.party.size} Pokemon")
                assertTrue(info.party.all { it.level in 2..100 }, "trainer $id has an impossible level")
                assertTrue(info.party.all { it.species in 1..411 }, "trainer $id has an impossible species")
                assertTrue(info.party.all { it.ivs in 0..31 }, "trainer $id has impossible IVs")
                assertTrue(info.className.isNotBlank(), "trainer $id has no class name")
            }
        }
        assertTrue(seen > 300, "only $seen trainers were read from the route tables")
    }

    @Test
    fun `catch rates and growth rates come out of the species table`() {
        val f = rom("emerald-u.gba") ?: return
        val t = GbaTracker(romReader(f), GameMap.EMERALD_U)
        // The canonical values: the starters are 45, Caterpie is 255, the legendary birds are 3.
        assertEquals(45, assertNotNull(t.baseStats(1)).catchRate)
        assertEquals(255, assertNotNull(t.baseStats(10)).catchRate)
        assertEquals(3, assertNotNull(t.baseStats(144)).catchRate)
        // Growth rates are the six the games have.
        for (species in 1..251) assertTrue(assertNotNull(t.baseStats(species)).growthRate in 0..5, "species $species has growth rate ${t.baseStats(species)?.growthRate}")
        // A Poke Ball on a full-health Caterpie is a far better bet than on Articuno.
        val caterpie = t.calcCatchRate(255, 20, 20, 5, 0, 4, false, 0, false, 0)
        val articuno = t.calcCatchRate(3, 200, 200, 50, 0, 4, false, 0, false, 0)
        assertTrue(caterpie > articuno, "Caterpie $caterpie should beat Articuno $articuno")
        assertTrue(articuno == 0, "a full-health Articuno in a Poke Ball rounds to nothing, not $articuno")
    }

    @Test
    fun `the experience tables read as real tables for every growth rate`() {
        val f = rom("emerald-u.gba") ?: return
        val t = GbaTracker(romReader(f), GameMap.EMERALD_U)
        // One species per growth rate the games use, found in the table itself.
        val perRate = (1..386).mapNotNull { sp -> t.baseStats(sp)?.let { it.growthRate to it } }.toMap()
        assertTrue(perRate.keys.all { it in 0..5 }, "growth rates outside 0..5: " + perRate.keys)
        assertTrue(perRate.size >= 4, "only " + perRate.size + " growth rates found in the species table")
        for ((rate, base) in perRate) {
            for (level in 1..99) {
                val p = assertNotNull(t.expProgress(mon(level, 0), base), "growth rate $rate has no span at level $level")
                assertTrue(p.second > 0, "growth rate $rate spans no experience at level $level")
                assertEquals(0, p.first, "a mon with no experience is at the start of its level")
            }
            // Levelling gets slower: the late levels cost far more than the early ones.
            // (Medium Slow dips in the first few levels, so compare well clear of that.)
            val early = assertNotNull(t.expProgress(mon(20, 0), base)).second
            val late = assertNotNull(t.expProgress(mon(80, 0), base)).second
            assertTrue(late > early * 3, "growth rate $rate barely rises: level 20 spans $early, level 80 spans $late")
        }
        // Level 100 has nothing after it, which is what the EXP bar keys on.
        assertNull(t.expProgress(mon(100, 0), perRate.values.first()))
    }

    private fun mon(level: Int, exp: Long) = PokemonDecoder.Mon(
        pid = 1, level = level, nickname = "", species = 1, heldItem = 0, friendship = 0,
        moves = List(4) { 0 }, pp = List(4) { 0 }, ivs = List(6) { 0 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
        abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = 1, maxHp = 1,
        atk = 0, def = 0, spe = 0, spAtk = 0, spDef = 0, exp = exp,
    )

    @Test
    fun `FireRed's own trainer table decodes too, at its own addresses`() {
        val f = rom("firered-u-v10.gba") ?: return
        val t = GbaTracker(romReader(f), GameMap.FIRERED_U_V10)
        val brock = assertNotNull(t.trainer(414))
        assertEquals("LEADER", brock.className)
        assertTrue(brock.party.isNotEmpty() && brock.party.all { it.level in 2..100 })
        assertEquals(45, assertNotNull(t.baseStats(1)).catchRate)
    }
}
