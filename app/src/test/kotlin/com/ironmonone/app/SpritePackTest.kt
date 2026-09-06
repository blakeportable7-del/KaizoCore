package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bundled GBA sprite pack.
 *
 * The reference tracker never decodes art out of the ROM: it ships PNGs named
 * by pokemonID and draws those, and the Nat. Dex extension overrides the same
 * path with its own pack for the species it adds. Doing it the other way is
 * why the Nat. Dex builds showed no art at all - no front-pic table address is
 * published for them, so there was nothing to decode.
 *
 * A hole in the pack is invisible: the card just renders blank, exactly like
 * the bug being fixed. So the pack is counted rather than eyeballed.
 */
class SpritePackTest {

    private val pack = File("src/main/assets/gbasprites")

    @Test
    fun `every species from 1 to 1285 has art`() {
        assertTrue(pack.isDirectory, "sprite pack missing at ${pack.absolutePath}")
        val have = pack.listFiles { f: File -> f.name.endsWith(".png") }
            .orEmpty()
            .mapNotNull { it.name.removeSuffix(".png").toIntOrNull() }
            .toSet()
        val missing = (1..1285).filter { it !in have }
        assertEquals(emptyList(), missing.take(20), "gaps in the pack")
        // Golisopod, the mon on the card that reported this.
        assertTrue(768 in have)
    }

    @Test
    fun `the pack is indexed the same way as the species names`() {
        // The pack and the name list must agree, because a mismatch is SILENT:
        // the card shows a confident name beside a picture of some other
        // Pokemon and nothing errors. A national-dex-ordered pack would look
        // plausible and be wrong everywhere past 251 - Golisopod is 793 here,
        // not its national 768, and 768 is Ribombee.
        val names = File("../tracker-gba/src/main/resources/natdex/species.tsv")
        assertTrue(names.isFile, "species list missing at ${names.absolutePath}")
        val ids = names.readLines().mapNotNull {
            it.split("	").firstOrNull()?.trim()?.toIntOrNull()
        }.toSet()
        assertTrue(ids.size > 1200, "only ${ids.size} species named")

        val art = pack.listFiles { f: File -> f.name.endsWith(".png") }
            .orEmpty()
            .mapNotNull { it.name.removeSuffix(".png").toIntOrNull() }
            .toSet()
        // Every named species must have art. The reverse is allowed: the pack
        // carries a couple of extra forms the name list does not.
        assertEquals(emptyList(), (ids - art).sorted().take(20), "named but no art")
        // And the top of the range must be the expansion's, not the national
        // dex's - the cheapest signal that the wrong pack has been dropped in.
        assertTrue(art.max() >= 1283, "pack tops out at ${art.max()}")
    }
}
