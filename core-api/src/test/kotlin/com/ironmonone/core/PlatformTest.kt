package com.ironmonone.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The game descriptor: one lookup instead of a branch per console.
 *
 * These pin the facts the rest of the app now trusts without checking: the
 * core file a platform loads, the turbo cap melonDS needs, and that every
 * RomKind's own extension agrees with its platform - the check that fails the
 * moment somebody adds a kind with the wrong generation.
 */
class PlatformTest {

    @Test
    fun `every kind's extension agrees with its platform`() {
        for (k in RomKind.all) {
            assertEquals(k.platform.romExtension, k.fileExtension,
                "${k.id}: extension does not match platform ${k.platform}")
        }
    }

    @Test
    fun `ids are unique and round-trip through byId`() {
        val ids = RomKind.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate RomKind ids: $ids")
        for (k in RomKind.all) assertEquals(k, RomKind.byId(k.id))
        assertNull(RomKind.byId("no-such-kind"))
        assertNull(RomKind.byId(null))
    }

    @Test
    fun `the platform decides the core and the turbo cap`() {
        assertEquals("libmgba_libretro_android.so", RomKind.FIRERED_U_V11.platform.core)
        assertEquals("libmelonds_libretro_android.so", RomKind.PLATINUM_U.platform.core)
        // melonDS crashes above 4x; that fact lives here, not in the speed button.
        assertEquals(4, Platform.NDS.maxTurbo)
        assertEquals(16, Platform.GBA.maxTurbo)
        // melonDS keeps its own .sav; mGBA hands SRAM to the app to persist.
        assertEquals(true, Platform.NDS.coreOwnsSaves)
        assertEquals(false, Platform.GBA.coreOwnsSaves)
    }

    @Test
    fun `the engine follows the ROM, never the user`() {
        assertEquals(Engine.ZX, RomKind.FIRERED_U_V11.engine)
        assertEquals(Engine.ZX, RomKind.PLATINUM_U.engine)
        assertEquals(Engine.NATDEX, RomKind.FIRERED_NATDEX_121.engine)
        assertEquals(Engine.NATDEX, RomKind.EMERALD_NATDEX_121.engine)
    }

    @Test
    fun `every kind names a preset family, and a Nat Dex kind keeps its base family`() {
        val known = setOf("FRLG", "RSE", "DPPt", "HGSS", "BW", "B2W2", "GSC", "RBY")
        assertEquals("GSC", RomKind.CRYSTAL_U.family)
        assertEquals("libgambatte_libretro_android.so", RomKind.CRYSTAL_U.platform.core)
        assertEquals(160f / 144f, Platform.GBC.aspect)
        for (k in RomKind.all) assert(k.family in known) { "${k.id}: family ${k.family}" }
        assertEquals("FRLG", RomKind.FIRERED_U_V11.family)
        assertEquals("FRLG", RomKind.FIRERED_U_V10.family)
        assertEquals("RSE", RomKind.EMERALD_U.family)
        assertEquals("DPPt", RomKind.PLATINUM_U.family)
        assertEquals(RomKind.FIRERED_U_V11.family, RomKind.FIRERED_NATDEX_121.family)
        assertEquals(RomKind.EMERALD_U.family, RomKind.EMERALD_NATDEX_121.family)
    }

    @Test
    fun `extension fallback is only for unknown files`() {
        assertEquals(Platform.NDS, Platform.fromExtension("NDS"))
        assertEquals(Platform.GBA, Platform.fromExtension("gba"))
        assertNull(Platform.fromExtension("zip"))
        assertNotNull(RomKind.byId(RomKind.EMERALD_U.id)?.platform)
    }
}
