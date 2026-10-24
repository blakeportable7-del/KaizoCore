package com.ironmonone.pc

import com.ironmonone.app.AutoTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A lead past Gen 3 gets its own sheet's colours (Blake, 2026-10-10: "Why don't the auto themes match the sprites
 * colors?"). AutoTheme.painted read walkingpals-nat/idle/<Nat. Dex id>.png, but that folder is keyed by national number,
 * so every lead from Gen 4 on was painted in another Pokemon's colours (Luxray, 430, in Honchkrow's; Farigiraf, 1006, in
 * Iron Valiant's green) and one with no file under that number (Dondozo, 1002) in none. It now goes through
 * natdex-map.tsv, as the walking sprites do.
 */
class AutoThemePaintedTest {
    private val painted = AutoTheme::class.java.getDeclaredMethod("painted", Int::class.javaPrimitiveType).apply { isAccessible = true }
    private fun painted(id: Int) = painted.invoke(AutoTheme, id) as String?

    /** The code's main background, the sheet's most common colour that is not a grey: its 11th field. */
    private fun main(id: Int) = painted(id)?.split(' ')?.getOrNull(10)

    @Test
    fun `each lead is painted from its own sheet`() {
        // The main colour of the right sheet, worked out by hand from each one (walkingpals-nat/idle/405.png and the rest).
        val expected = mapOf(
            430 to "3F4767",  // Luxray, its own navy (Honchkrow's 4F5F97 before)
            955 to "148B16",  // Arboliva, green (Flittle's yellow before)
            1006 to "6F4933", // Farigiraf, brown (Iron Valiant's green before)
            974 to "96936B",  // Toedscruel, from DarkusShadow's set (Cetoddle's pink before)
            1002 to "51C8F9", // Dondozo, light blue (no theme at all before)
        )
        for ((id, hex) in expected) assertEquals(hex, main(id), "Nat. Dex $id")
    }

    @Test
    fun `every Pokemon past Gen 3 has a painted theme`() {
        val missing = (412..1283).filter { painted(it) == null }
        assertTrue(missing.isEmpty(), "no painted theme for Nat. Dex ${missing.take(20)}${if (missing.size > 20) " and ${missing.size - 20} more" else ""}")
    }
}
