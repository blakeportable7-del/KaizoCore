package com.ironmonone.app

import com.ironmonone.tracker.Gen3Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The move rows' type symbols (TypeSymbol, PcTracker's move table): every type a GBA game's moves can carry has one,
 * Fairy included (Moonblast's row on Nat. Dex, MaxDex and Heart & Soul had none, 2026-10-06), and each symbol fits the
 * square the row holds for it with every pixel naming a colour it has.
 */
class TypeSymbolsTest {
    @Test
    fun `every type a Gen 3 tracker names has a symbol, Fairy and Mystery included`() {
        // The names the move rows are given (MoveDecor: Gen3Types.name), Fairy on the Nat. Dex charts, 9 the Mystery slot.
        for (id in Gen3Types.typesFor(natDex = true) + 9) {
            val name = Gen3Types.name(id)
            assertNotNull(TypeSymbols.of(name), "no symbol for $name ($id)")
            assertNotNull(TypeSymbols.of(name.uppercase()), "no symbol for ${name.uppercase()}")
        }
        assertNotNull(TypeSymbols.of("???"), "the DS and log names for the Mystery type")
        assertNull(TypeSymbols.of("UNKNOWN")); assertNull(TypeSymbols.of(null))
    }

    @Test
    fun `each symbol fits its slot and uses only its own colours`() {
        for ((name, s) in TypeSymbols.BY_TYPE) {
            assertTrue(s.rows.all { it.length == s.width }, "$name: ragged rows")
            assertTrue(s.width <= TypeSymbols.SLOT && s.height <= TypeSymbols.SLOT, "$name: ${s.width}x${s.height} over ${TypeSymbols.SLOT}")
            val used = s.rows.flatMap { r -> r.map { it - '0' } }.filter { it > 0 }.toSet()
            assertTrue(used.all { it in 1..s.palette.size }, "$name: a pixel names a colour it does not have")
            // KaizoCore's own two use every colour they list (the DS set's Dragon carries a fifth it never draws).
            if (name == "FAIRY" || name == "MYSTERY") assertEquals((1..s.palette.size).toSet(), used, "$name: a colour no pixel uses")
        }
    }
}
