package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RouteData.getEncounterAreaByTerrain and the per-version route tables
 * (gen3/routeenc-<version>.tsv), 2026-09-28. The route screen reads a wild
 * battle's area from these, so a slip sends every sighting to the wrong list.
 */
class EncounterAreaTest {

    @Test
    fun `terrain and battle flags pick the reference's area`() {
        // 4 (0b100) is the plain wild-battle value.
        assertEquals("Walking", encounterAreaByTerrain(0, 4, rsFirstBattle = false))   // grass
        assertEquals("Walking", encounterAreaByTerrain(7, 4, rsFirstBattle = false))   // cave
        assertEquals("Surfing", encounterAreaByTerrain(4, 4, rsFirstBattle = false))   // water
        assertEquals("Surfing", encounterAreaByTerrain(5, 4, rsFirstBattle = false))   // pond
        assertEquals("Underwater", encounterAreaByTerrain(3, 4, rsFirstBattle = false))
        // Trainer bit (3) wins over terrain.
        assertEquals("Trainer", encounterAreaByTerrain(4, 4 or 8, rsFirstBattle = false))
        // The first battle is Static only in Ruby/Sapphire/Emerald (versiongroup 1).
        assertEquals("Static", encounterAreaByTerrain(0, 4 or 16, rsFirstBattle = true))
        assertEquals("Walking", encounterAreaByTerrain(0, 4 or 16, rsFirstBattle = false))
        // Static flags live above bit 10.
        assertEquals("Static", encounterAreaByTerrain(0, 4 or (1 shl 12), rsFirstBattle = false))
        // A Safari battle (bit 7) falls through to the terrain.
        assertEquals("Surfing", encounterAreaByTerrain(4, 4 or 128, rsFirstBattle = false))
        // Out of range terrain is no area at all.
        assertEquals(null, encounterAreaByTerrain(20, 4, rsFirstBattle = false))
    }

    private fun table(version: String): Map<Int, String> {
        val out = HashMap<Int, String>()
        javaClass.getResourceAsStream("/gen3/routeenc-$version.tsv")!!.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { !it.startsWith("#") }.forEach { l ->
                val p = l.split('\t'); out[p[0].toInt()] = p.getOrElse(2) { "" }
            }
        }
        return out
    }

    @Test
    fun `each version has its own species where the reference gives a tuple`() {
        // FireRed and LeafGreen: Route 24/25 style tuples give different species, so the
        // two files must differ somewhere, and they must share maps.
        val fr = table("firered"); val lg = table("leafgreen")
        assertEquals(fr.keys, lg.keys)
        assert(fr.keys.count { fr[it] != lg[it] } > 10) { "FireRed and LeafGreen came out identical" }
        // Ruby leads Route 101 with Zigzagoon (288), Emerald with Poochyena (286).
        assert(table("ruby").getValue(17).startsWith("Walking=288:0.45")) { table("ruby").getValue(17) }
        assert(table("emerald").getValue(17).startsWith("Walking=286:0.45")) { table("emerald").getValue(17) }
        // No national-dex Hoenn ids (252-276 are internal placeholders, never real).
        for (v in listOf("ruby", "sapphire", "emerald")) {
            val ids = table(v).values.flatMap { a -> Regex("""[=,](\d+):""").findAll(a).map { it.groupValues[1].toInt() }.toList() }
            assert(ids.none { it in 252..276 }) { "$v holds placeholder ids: ${ids.filter { it in 252..276 }.distinct()}" }
        }
    }
}
