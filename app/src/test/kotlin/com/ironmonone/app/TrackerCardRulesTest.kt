package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.Gen3Types
import com.ironmonone.tracker.Gender3
import com.ironmonone.tracker.PokemonDecoder
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tracker cards' small rules, from the rc32 audit (fixed in rc34): Fairy's colour (P2 #44), "Display gender" only
 * where its switch is (P2 #97), the hidden card's heals and experience bar (P2 #98), the info screen's learn levels
 * (P2 #100) and the taps on a blank move row or a "-" ability line (P3 #43).
 */
class TrackerCardRulesTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    // ------------------------------------------------------------------ P2 #44

    @Test
    fun `Fairy is pink by its id and by its name`() {
        // Constants.MoveTypeColors' fairy (Constants.lua:97), at the id the Nat. Dex expansion gives it, 18.
        assertEquals(Color(0xFFEE99AC), pcTypeColor(18))
        assertEquals(Color(0xFFEE99AC), pcTypeColor(Gen3Types.idOf("Fairy")!!))
        assertEquals(pcTypeColor(18), pcTypeColorByName("FAIRY"))
        assertEquals(pcTypeColorByName("Fairy"), pcTypeColor(Gen3Types.idOf("Fairy")!!), "the card's chips and Coverage Calc's agree")
        assertEquals(Color(0xFF68A090), pcTypeColor(9), "the unknown type keeps its own")
        assertEquals(Color(0xFF68A090), pcTypeColor(23), "and 23 is nothing")
    }

    // ------------------------------------------------------------------ P2 #97

    @Test
    fun `gender is drawn only on a Game Boy Advance game, where its switch is`() {
        assertNull(CardGender.of(2, 127, 0x1234), "Gold, Silver and Crystal: the personality there is the species and trainer id")
        assertNull(CardGender.of(1, 127, 0x1234))
        assertEquals(Gender3.FEMALE, CardGender.of(3, 127, 0x10))
        assertEquals(Gender3.MALE, CardGender.of(3, 127, 0xF0))
        val panel = src("TrackerPanel.kt")
        val helper = panel.substringAfter("internal object CardGender {").substringBefore("\n}\n")
        assertTrue("Gender3.of(" in helper)
        assertFalse("Gender3.of(" in panel.replace(helper, ""), "both cards go through CardGender")
        assertEquals(2, Regex("""CardGender\.of\(generation,""").findAll(panel).count(), "your card and the opponent's")
        assertTrue("generation = generation,\n                        markMove = MoveRule.gbaMark(moveRules, p, state, ruleRun)," in panel, "your card is handed the generation")
        assertTrue("onBstTap = { bstSheet = Triple(enemy.base?.bst ?: 0, bstLines?.wild ?: 0, null) },\n                        generation = generation)" in panel,
            "and the opponent's")
        assertTrue("if (scope.gen3) GearToggle(\"Display gender\"" in src("TrackerGearDialog.kt"), "the switch is the GBA trackers'")
    }

    // ------------------------------------------------------------------ P2 #98

    @Test
    fun `hidden, the heals read 0 and the experience bar is empty`() {
        // DataHelper.lua:374-377 zeroes the heals while the card is hidden; the stand-in has 0 of 100 experience.
        assertEquals(Triple(0, 0, 0), HiddenCard.heals(hidden = true, percent = 62, count = 1, wholeHp = 20))
        assertEquals(Triple(62, 1, 20), HiddenCard.heals(hidden = false, percent = 62, count = 1, wholeHp = 20))
        assertEquals(0f, HiddenCard.expFraction(hidden = true, now = 1200, total = 1951))
        assertEquals(0.5f, HiddenCard.expFraction(hidden = false, now = 50, total = 100))
        val panel = src("TrackerPanel.kt")
        assertTrue("val (pct, count, whole) = HiddenCard.heals(hidden, healPercent, healCount, healHp)" in panel)
        assertTrue("PcHealsBlock(pct, count, wholeHp = whole," in panel)
        assertTrue("HiddenCard.expFraction(hidden, p.expNow, p.expTotal)" in panel)
        assertTrue("if (TrackerOptions.displayGender && !hidden) CardGender.of(" in panel, "nor its own gender")
        // P2 #99: the whole HP is the tracker's, rounded per item, not rebuilt from a truncated percent.
        // Since rc34 through the battle view, which hands over the tracker's numbers for the Pokemon shown (GbaViewState.heals).
        assertTrue("healHp = ownHeals.hp," in panel)
        assertTrue("HealTotals(state.healPercent, state.healHp, state.healCount)" in src("DoublesView.kt"))
        assertFalse("healPercent * p.mon.maxHp / 100" in panel)
    }

    // ------------------------------------------------------------------ P2 #100

    private fun tracked(species: Int, level: Int) = TrackedMon(
        PokemonDecoder.Mon(1, level, "", species, 0, 0, listOf(0, 0, 0, 0), listOf(0, 0, 0, 0), List(6) { 0 }, List(6) { 0 },
            List(4) { 0 }, 0, 0, false, 0, 20, 20, 10, 10, 10, 10, 10),
        "MON", emptyList(), null,
    )
    private fun enemy(species: Int, level: Int) = EnemyInfo(species, "FOE", level, 20, 20, 0, 0, null, emptyList())

    @Test
    fun `with no level the learn levels are plain, and an opponent in battle gives its own`() {
        // InfoScreen.lua:785-790: level 0 draws every level plain; otherwise those still ahead stand apart.
        assertEquals("1, 5, 9, 33", InfoScreenLines.learnLevels(listOf(1, 5, 9, 33), 0))
        assertEquals("1, 5, 9, [33]", InfoScreenLines.learnLevels(listOf(1, 5, 9, 33), 30))
        // DataHelper.lua:452-465: in battle, the Pokemon on the field of that species, yours first; else your lead's.
        val battle = TrackerState(1, listOf(tracked(258, 12)), inBattle = true, isWildBattle = true, enemy = enemy(263, 30))
        assertEquals(30, InfoScreenLines.viewedLevel(263, battle))
        assertEquals(12, InfoScreenLines.viewedLevel(258, battle))
        assertEquals(0, InfoScreenLines.viewedLevel(25, battle))
        val walking = TrackerState(1, listOf(tracked(258, 12)), inBattle = false, isWildBattle = false)
        assertEquals(12, InfoScreenLines.viewedLevel(258, walking))
        assertEquals(0, InfoScreenLines.viewedLevel(263, walking), "an opponent's level outside a battle is no one's")
        assertEquals(0, InfoScreenLines.viewedLevel(258, null))
        assertTrue("level = InfoScreenLines.viewedLevel(sp, state)," in src("TrackerPanel.kt"))
    }

    // ------------------------------------------------------------------ P3 #43

    @Test
    fun `a blank move row and a dash ability line open nothing`() {
        assertNull(abilityTapName("-"), "the Game Boy trackers' ability line")
        assertNull(abilityTapName("---"))
        assertNull(abilityTapName(" "))
        assertEquals("BLAZE", abilityTapName("BLAZE"))
        assertTrue("onAbilityTap = onAbilityInfo?.let { cb -> abilityTapName(p.abilityName)?.let { name -> { cb(name) } } }," in src("TrackerPanel.kt"))
        val rows = src("PcTracker.kt").substringAfter("fun PcMovesSection(").substringBefore("\n}\n")
        assertTrue("if (onMoveTap != null && !r.blank) Modifier.clickable { onMoveTap(r) }" in rows, "InfoScreen.lua:651-656 ignores move id 0")
    }
}
