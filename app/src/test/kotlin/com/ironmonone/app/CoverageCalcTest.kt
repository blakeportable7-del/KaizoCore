package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals

/** The reference seeds the calculator from the lead's damaging moves, minus fixed-damage moves; Hidden Power once its type is set. */
class CoverageCalcTest {
    @Test
    fun `status, fixed-damage and an untyped Hidden Power are not coverage`() {
        val moves = listOf(
            Triple(33, "PHY", "NORMAL"),     // Tackle
            Triple(69, "PHY", "FIGHTING"),   // Seismic Toss: fixed damage, out
            Triple(237, "SPE", "NORMAL"),    // Hidden Power: out
            Triple(45, "STA", "NORMAL"),     // Growl: status, out
            Triple(52, "SPE", "FIRE"),       // Ember
            Triple(53, "SPE", "FIRE"),       // Flamethrower: same type, once
        )
        assertEquals(listOf("NORMAL", "FIRE"), CoverageCalc.seedTypes(moves))
        assertEquals(6, CoverageCalc.seedTypes((1..8).map { Triple(it + 300, "PHY", "T$it") }).size)
    }

    /**
     * rc32 audit P2 #17: the Gen 3 seed dropped Hidden Power even with its type set on the info screen, which the PC
     * tracker adds (CoverageCalcScreen.lua:483-487) and the DS seed already did.
     */
    @Test
    fun `Hidden Power counts with the type the player set, in its place`() {
        val dir = java.nio.file.Files.createTempDirectory("hpseed").toFile()
        val pid = 0x2A6B41C7L
        java.io.File(dir, "hp.txt").writeText("$pid=10\n")   // Fire
        HiddenPowerTypes.load(java.io.File(dir, "hp.txt"))
        fun lead(p: Long) = com.ironmonone.tracker.TrackedMon(
            mon = com.ironmonone.tracker.PokemonDecoder.Mon(pid = p, level = 20, nickname = "", species = 280, heldItem = 0, friendship = 70,
                moves = listOf(33, 237, 0, 0), pp = listOf(35, 15, 0, 0), ivs = List(6) { 0 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
                abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = 50, maxHp = 50, atk = 5, def = 5, spe = 5, spAtk = 5, spDef = 5),
            speciesName = "Ralts", moveNames = listOf("Tackle", "Hidden Power"), base = null,
            moveRows = listOf(
                com.ironmonone.tracker.MoveRow(33, "Tackle", 35, 35, 35, 95, 0, "PHY"),
                com.ironmonone.tracker.MoveRow(237, "Hidden Power", 15, 15, 1, 100, 0, "PHY"),   // the ROM's own type: Normal
            ),
        )
        try {
            assertEquals(listOf("Normal", "Fire"), CoverageCalc.gen3Seed(lead(pid)))
            assertEquals(listOf("Normal"), CoverageCalc.gen3Seed(lead(pid + 1)), "a type not set yet leaves it out")
            assertEquals(emptyList(), CoverageCalc.gen3Seed(null))
        } finally { HiddenPowerTypes.load(java.io.File(dir, "none.txt")) }   // the picks are app-wide: none left for other tests
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        // The lead skips an Egg in slot 1, as Tracker.getPokemon(1, true) does (RC35-NOTICED N #34).
        kotlin.test.assertTrue("seed = CoverageCalc.gen3Seed(trackerState?.lead)," in play)
        kotlin.test.assertTrue("val leadMoveTypes = trackerState?.lead?.moveRows" in play)
    }
}
