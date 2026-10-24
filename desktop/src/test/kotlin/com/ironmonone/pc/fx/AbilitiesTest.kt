package com.ironmonone.pc.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Only abilities that show a pop-up in Heart & Soul get an effect of their own (Blake, 2026-10-10: "compound eye ability
 * enemy gives no notification. only things revealed to the player in battle is revealed and reflected in changes on the
 * tracker for enemies"). The silent list was read off pokehns-expansion that day.
 */
class AbilitiesTest {
    @Test
    fun `silent abilities have no effect of their own`() {
        for (n in listOf("Compound Eyes", "Tinted Lens", "Victory Star", "Hustle", "Unaware", "Sand Veil", "Snow Cloak",
            "Tangled Feet", "Wonder Skin", "No Guard", "Super Luck", "Merciless", "Shell Armor", "Battle Armor")) {
            assertTrue(Abilities.silent(n), n)
            assertEquals(AbilityKind.PLAIN, Abilities.of(n).kind, "$n plays nothing but its line, and only when another pop-up names it")
        }
    }

    @Test
    fun `the silent list is the source's, all of it`() {
        val tsv = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.map { java.io.File(it, "docs/ability-popups.tsv") }
            .firstOrNull { it.isFile } ?: return
        val rows = tsv.readLines().drop(1).map { it.split('	') }.filter { it.size >= 2 }
        val no = rows.filter { it[1] == "no" }.map { it[0].removePrefix("ABILITY_") }
        val shows = rows.filter { it[1] != "no" }.map { it[0].removePrefix("ABILITY_") }
        assertEquals(112, no.size, "the audit's count")
        for (n in no) assertTrue(Abilities.silent(n), "$n never shows a pop-up")
        for (n in shows) assertFalse(Abilities.silent(n), "$n shows one")
    }

    @Test
    fun `abilities that show a pop-up keep their effect`() {
        for ((n, k) in listOf("Volt Absorb" to AbilityKind.ABSORB, "Levitate" to AbilityKind.LEVITATE, "Intimidate" to AbilityKind.INTIMIDATE,
            "Sand Stream" to AbilityKind.WEATHER, "Sturdy" to AbilityKind.STURDY, "Pressure" to AbilityKind.PRESSURE,
            "Clear Body" to AbilityKind.SHIELD, "Keen Eye" to AbilityKind.SHIELD, "Static" to AbilityKind.CONTACT,
            "Speed Boost" to AbilityKind.STAT, "Flash Fire" to AbilityKind.FLASH_FIRE)) {
            assertFalse(Abilities.silent(n), n)
            assertEquals(k, Abilities.of(n).kind, n)
        }
    }
}
