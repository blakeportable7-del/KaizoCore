package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tracker Setup shows each row only in the games and modes where it does something (Blake, 2026-10-01: "the setup menu
 * should be variable depending on the mode, and the game", "if you are playing emerald, and the set up menu mentions
 * fire red maps, that's a problem").
 */
class GearScopeTest {
    private fun scope(kind: RomKind, rules: PlayRules.Kind, isRun: Boolean, gameBoy: Boolean = false, ds: Boolean = false) =
        GearScope(gameBoy, ds, kind.family, kind.isNatDex, rules, isRun)

    @Test
    fun `the game and the mode decide what Tracker Setup can do`() {
        val emerald = scope(RomKind.EMERALD_U, PlayRules.Kind.IRONMON, isRun = true)
        assertTrue(emerald.gen3 && emerald.ironmon && emerald.randomized)
        assertFalse(emerald.frlg, "Emerald has no FireRed maps")
        assertTrue(scope(RomKind.FIRERED_U_V11, PlayRules.Kind.IRONMON, isRun = true).frlg)
        assertTrue(scope(RomKind.LEAFGREEN_U, PlayRules.Kind.NUZLOCKE, isRun = false).frlg)
        val frNatDex = scope(RomKind.FIRERED_NATDEX_121, PlayRules.Kind.PLAIN, isRun = false)
        assertTrue(frNatDex.frlg && frNatDex.randomized, "a Nat. Dex FireRed: FireRed's maps, and read as randomized")
        assertFalse(scope(RomKind.EMERALD_NATDEX_121, PlayRules.Kind.PLAIN, isRun = false).frlg)
        val library = scope(RomKind.EMERALD_U, PlayRules.Kind.PLAIN, isRun = false)
        assertTrue(library.plain && !library.ironmon && !library.randomized, "a clean library game")
        val nuzlocke = scope(RomKind.EMERALD_U, PlayRules.Kind.NUZLOCKE, isRun = false)
        assertFalse(nuzlocke.ironmon || nuzlocke.plain)
        assertFalse(scope(RomKind.CRYSTAL_U, PlayRules.Kind.IRONMON, isRun = true, gameBoy = true).gen3)
        assertFalse(scope(RomKind.PLATINUM_U, PlayRules.Kind.IRONMON, isRun = true, ds = true).gen3)
    }

    @Test
    fun `Tracker Setup asks the game and the mode before each row they decide`() {
        val gear = File("src/main/kotlin/com/ironmonone/app/TrackerGearDialog.kt").readText().replace("\r\n", "\n")
        fun row(label: String) = gear.lines().single { "\"$label\"" in it && ("GearToggle(" in it || "GearButton(" in it) }.trim()
        assertTrue("val scope = remember(gameBoy, ds) { GearScope.of(appFiles, gameBoy, ds) }" in gear)
        assertTrue(row("FireRed and LeafGreen dungeon maps (routes and item spots)").startsWith("if (scope.frlg) "))
        for (label in listOf("Show starter ball info", "Display pedometer", "Show nicknames", "Display gender", "Color stat numbers by nature"))
            assertTrue(row(label).startsWith("if (scope.gen3) "), label)
        for (label in listOf("Determine friendship readiness", "Count enemy PP usage", "Show last damage calcs", "Right justified numbers"))
            assertTrue(row(label).startsWith("if (!ds) "), label)
        assertTrue(row("Display repel usage").startsWith("if (!gameBoy) "))
        assertTrue(row("Show data for vanilla game").startsWith("if (scope.gen3 && !scope.natDex) "))
        assertTrue(row("RULES FOR THIS RUN").startsWith("if (!scope.plain) "))
        assertTrue("if (scope.ironmon) GearButton(DeathQuotesCopy.TITLE) { linesOpen = true }" in gear)
        assertTrue("if (scope.ironmon) onPastRuns?.let" in gear && "if (scope.ironmon) onTourney?.let" in gear)
        assertTrue("if (scope.gen3) {\n                Spacer(Modifier.height(8.dp))\n                SpriteIsMeGearSection()" in gear)
        assertTrue("if (scope.isRun) {\n                GearHead(\"New runs\")" in gear)
        assertTrue("if (!ds && TrackerOptions.allowCarouselRotation) {" in gear, "the speed only while the box rotates")
        assertTrue("if (TrackerOptions.showBothBadgeSets) GearToggle(\"Kanto badges first\"" in gear)
    }
}
