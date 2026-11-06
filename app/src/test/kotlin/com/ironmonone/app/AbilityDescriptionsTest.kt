package com.ironmonone.app

import com.ironmonone.patch.Patcher
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import com.ironmonone.tracker.nds.NdsLogData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Every ability each tracker can show has a description (Blake, 2026-11-06: abilities missing descriptions in the
 * tracker view). Walks every ability id of every game the way the card does it, the name it shows turned back into an
 * id (GbaTracker.abilityIdOf) and that id's text (abilityDescription), and fails on an empty one.
 *
 * Before this, nothing past Gen 3's 77 had a description on Nat. Dex or MaxDex: the lookup knew only Gen 3's names.
 * Heart & Soul's two As One and four Embody Aspect each have their own text in the game, but the lookup by name gave
 * every one of them the first one's; the species showing it now picks the right one.
 *
 * The GBA games need their dumps (IRONMON_ROMS); Heart & Soul's two builds are made here from hns-206.gba with the
 * bundled comfort patches (the app's, and KaizoCore PC's in desktop/assets). The DS tables need nothing.
 */
class AbilityDescriptionsTest {

    private fun reader(rom: ByteArray) = MemoryReader { address, length ->
        val o = address - 0x08000000L
        if (o < 0 || o + length > rom.size) ByteArray(0) else rom.copyOfRange(o.toInt(), (o + length).toInt())
    }

    private fun tracker(rom: ByteArray): GbaTracker = reader(rom).let { GbaTracker(it, GameMap.resolve(it)) }

    /**
     * Every id from 1 to [last]: the name the card shows finds that ability again, and it has a description that is not
     * a blank or Nat. Dex's "Not implemented yet." [skip]: empty slots no Pokemon holds.
     */
    private fun walk(what: String, t: GbaTracker, last: Int, skip: Set<Int> = emptySet(), species: (Int) -> Int? = { null }): List<String> {
        val empty = ArrayList<String>()
        for (id in 1..last) {
            if (id in skip) continue
            val name = t.abilityName(id)
            assertTrue(!name.startsWith("#"), "$what: ability $id has no name")
            val found = t.abilityIdOf(name, species(id))
            // A shared name with no species to tell it apart (an ability no Pokemon in the game has) finds the first.
            if (found != id) assertTrue(species(id) == null && t.abilityName(found!!) == name, "$what: '$name' ($id) finds $found")
            val d = t.abilityDescription(id)
            if (d.isNullOrBlank() || d.contains("Not implemented yet", ignoreCase = true)) empty += "$id $name: $d"
        }
        return empty
    }

    @Test
    fun `the five games' 77 abilities each have a description`() {
        for (game in listOf("firered-u-v10", "firered-u-v11", "leafgreen-u", "ruby-u", "sapphire-u", "emerald-u")) {
            val rom = Dumps.rom("$game.gba") ?: continue
            val t = tracker(rom.readBytes())
            assertEquals(GbaTracker.VANILLA_LAST_ABILITY, t.lastAbilityId, game)
            assertEquals(emptyList(), walk(game, t, t.lastAbilityId), game)
        }
    }

    @Test
    fun `every Nat_ Dex and MaxDex ability has a description`() {
        val nds = File("../tracker-nds/src/main/resources/nds/ability-desc.tsv").readLines().filterNot { it.startsWith("#") }
            .map { it.split('\t') }.associate { key(it[1]) to it[3].trim() }
        // MaxDex has no Multitype, Imposter or Zen Mode, so 85 of Black and White's 88 abilities past Gen 3.
        for ((game, last, gen45) in listOf(Triple("firered-natdex-121", GbaTracker.NATDEX_LAST_ABILITY, 88),
            Triple("emerald-natdex-121", GbaTracker.NATDEX_LAST_ABILITY, 88), Triple("firered-maxdex", 289, 85))) {
            val rom = Dumps.rom("$game.gba") ?: continue
            val t = tracker(rom.readBytes())
            assertEquals(last, t.lastAbilityId, game)
            // Nat. Dex 1.2.1 has two empty slots, named by their number (315, 318): no species holds them.
            val holes = (1..last).filter { t.abilityName(it).all(Char::isDigit) }.toSet()
            assertEquals(if ("natdex" in game) setOf(315, 318) else emptySet(), holes, game)
            for (s in 1..1300) t.baseStats(s)?.let { b -> assertTrue(listOf(b.ability1, b.ability2, b.ability3).none { it in holes }, "$game species $s") }
            assertEquals(emptyList(), walk(game, t, last, holes), game)
            // An ability Black and White already had reads as the DS tracker words it, past Gen 3's own 77.
            var fromDs = 0
            for (id in 78..last) {
                val ds = nds[key(t.abilityName(id))] ?: continue
                assertEquals(ds, t.abilityDescription(id), "$game $id ${t.abilityName(id)}"); fromDs++
            }
            assertEquals(gen45, fromDs, "$game: Gen 4 and 5's abilities")
            // The rest: MaxDex's own summary-screen words; on Nat. Dex, whose own words are "Not implemented yet.", Showdown's.
            val showdown = (78..last).filter { it !in holes && nds[key(t.abilityName(it))] == null }
            println("$game: ${showdown.size} abilities past Gen 5")
            if ("natdex" in game) {
                assertEquals(152, showdown.size, game)
                assertEquals("Combination of the Unnerve and Chilling Neigh Abilities.", t.abilityDescription(t.abilityIdOf("As One-I")!!))
                assertEquals("On switch-in, this Pokemon's Defense is raised by 1 stage.", t.abilityDescription(t.abilityIdOf("Embody Aspect-C")!!))
            } else assertEquals("Wandering Spirit.", t.abilityDescription(260), "MaxDex keeps its own words")
        }
    }

    private fun key(name: String) = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    @Test
    fun `both Heart & Soul builds describe every ability, the two As One and four Embody Aspect each their own`() {
        val base = Dumps.rom("hns-206.gba") ?: return println("AbilityDescriptionsTest skipped: hns-206.gba not in IRONMON_ROMS")
        // KaizoCore PC's build first, so the layout ends on the app's. gSpeciesInfo per build, as HnsLayout has it.
        for ((what, patch, speciesInfo) in listOf(
            Triple("PC build", "../desktop/assets/patches/hns-kaizo-heartsoul-206.bps", 0x087E5F60L),
            Triple("app build", "src/main/assets/patches/hns-kaizo-heartsoul-206.bps", 0x087E5C90L),
        )) {
            val out = File.createTempFile("hns-abilities", ".gba")
            try {
                Patcher.applyFiles(File(patch), base, out)
                val rom = out.readBytes()
                val plain = tracker(rom)
                val last = plain.lastAbilityId
                assertEquals(310, last, what)
                assertEquals(emptyList(), walk(what, plain, last), what)
                val shared = (1..last).groupBy { plain.abilityName(it).uppercase() }.filterValues { it.size > 1 }
                assertEquals(mapOf("AS ONE" to listOf(266, 267), "EMBODY ASPECT" to listOf(301, 302, 303, 304)), shared, what)
                for ((name, ids) in shared) assertEquals(ids.size, ids.map { plain.abilityDescription(it) }.toSet().size, "$what: each $name its own text")
                // No Pokemon in the game has these, but a run with random abilities can give one to any species: give
                // each to a species (SpeciesInfo is 268 bytes, abilities three u16 at +24), as the randomizer writes it.
                val given = mapOf(266 to 1, 267 to 4, 301 to 7, 302 to 25, 303 to 133, 304 to 150)
                for ((ability, species) in given) {
                    val o = (speciesInfo - 0x08000000L + species * 268L + 24).toInt()
                    rom[o] = (ability and 0xFF).toByte(); rom[o + 1] = (ability shr 8).toByte()
                }
                val run = tracker(rom)
                for ((ability, species) in given) {
                    assertEquals(ability, run.baseStats(species)?.ability1, "$what: species $species holds ability $ability")
                    val name = run.abilityName(ability)
                    assertEquals(ability, run.abilityIdOf(name, species), "$what: $name on species $species")
                    assertEquals(plain.abilityDescription(ability), run.abilityDescription(run.abilityIdOf(name, species)!!))
                }
                assertNotEquals(plain.abilityDescription(266), plain.abilityDescription(267))
            } finally { out.delete() }
        }
    }

    @Test
    fun `every DS ability has a description in Gen 4 and Gen 5 wording`() {
        for (id in 1..164) for (gen in listOf(4, 5)) {
            assertTrue(NdsLogData.abilityDescription(id, gen).isNotBlank(), "ability $id, Gen $gen")
        }
    }
}
