package com.ironmonone.pcui

import androidx.compose.ui.graphics.Color
import com.ironmonone.app.PcMove
import com.ironmonone.app.TrackerOptions
import com.ironmonone.tracker.HitChance
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The card's real hit chance and move power (Blake, 2026-10-10: "can we do it for accuracy and for move power? With an
 * option toggle for both?"): each change the game makes, only from what the player knows, and nothing with the options off.
 */
class PcRealTest {
    private var hit = true
    private var power = true

    @BeforeTest fun keep() { hit = TrackerOptions.realHitChance; power = TrackerOptions.realMovePower; TrackerOptions.realHitChance = true; TrackerOptions.realMovePower = true }
    @AfterTest fun back() { TrackerOptions.realHitChance = hit; TrackerOptions.realMovePower = power }

    private val ember = PcMove(id = 52, name = "Ember", pp = 25, ppMax = 25, power = 40, acc = 100, color = Color.White, category = "SPE", type = 10, powerText = "40", accText = "100")
    private val waterGun = PcMove(id = 55, name = "Water Gun", pp = 25, ppMax = 25, power = 40, acc = 100, color = Color.White, category = "SPE", type = 11, powerText = "40", accText = "100", weatherMark = 1)
    private val hypnosis = PcMove(id = 95, name = "Hypnosis", pp = 20, ppMax = 20, power = 0, acc = 60, color = Color.White, category = "STA", type = 14, powerText = "0", accText = "60")
    private val swift = PcMove(id = 129, name = "Swift", pp = 20, ppMax = 20, power = 60, acc = 0, color = Color.White, category = "SPE", type = 0, powerText = "60", accText = "0")
    private val rows = listOf(ember, waterGun, hypnosis, swift)
    private val moves = mapOf(52 to HitChance.Move(100, type = 10), 55 to HitChance.Move(100, type = 11), 95 to HitChance.Move(60, status = true, type = 14), 129 to HitChance.Move(0, type = 0))
    private fun facts(own: Set<String> = emptySet(), foe: Set<String> = emptySet()) = PcBattleFacts(own, foe, move = { moves[it] }, hold = { HitChance.Hold.NONE to 0 })

    private val battle = Samples.enemy
    private val lead = battle.party[0].copy(itemName = "-")
    private fun real(st: com.ironmonone.tracker.TrackerState = battle, p: com.ironmonone.tracker.TrackedMon = lead, f: PcBattleFacts = facts(), foe: List<String> = emptyList()) =
        PcReal.own(rows, st, p, f, foe)

    @Test
    fun `nothing changes the odds or the power, the card's own numbers`() {
        val r = real()
        assertEquals(listOf("100", "100", "60", "0"), r.map { it.accText })
        assertEquals(listOf("40", "40", "0", "60"), r.map { it.powerText })
    }

    @Test
    fun `accuracy, a stage down, Lock-On, and a move that never misses`() {
        val down = real(p = lead.copy(statStages = lead.statStages + ("ACC" to 5)))
        assertEquals(listOf("75", "75", "45", "0"), down.map { it.accText }, "Sand-Attack once: 100 shows 75, Hypnosis 60 shows 45, Swift still never misses")
        val evasive = real(st = battle.copy(enemy = battle.enemy!!.copy(statStages = mapOf("EVA" to 8))))
        assertEquals("60", evasive[0].accText, "Double Team twice on the opponent: 100 shows 60")
        val locked = real(f = facts(foe = setOf("Lock-On")))
        assertEquals(listOf("0", "0", "0", "0"), locked.map { it.accText }, "Lock-On on the opponent: every move prints the card's dash")
    }

    @Test
    fun `an ability counts only once the game has shown it, and never when two are seen`() {
        val sand = battle.copy(weather = "SANDSTORM")
        assertEquals("100", real(st = sand)[0].accText, "Sand Veil not shown: no change")
        assertEquals("80", real(st = sand, foe = listOf("Sand Veil"))[0].accText, "Sand Veil shown: 100 shows 80")
        assertEquals("100", real(st = sand, foe = listOf("Sand Veil", "Pressure"))[0].accText, "two abilities seen: not known which")
        assertEquals("20", real(foe = listOf("Thick Fat"))[0].powerText, "Thick Fat shown: Ember 40 shows 20")
        assertEquals("40", real(foe = listOf("Thick Fat", "Oblivious"))[0].powerText, "two seen: no change")
    }

    @Test
    fun `power, weather, pinch ability, type item, Charge`() {
        val sun = real(st = battle.copy(weather = "SUN"))
        assertEquals(listOf("60", "20"), sun.take(2).map { it.powerText }, "sun: Ember 60, Water Gun 20")
        assertEquals(0, sun[1].weatherMark, "the weather arrow goes: the number has the weather in it")
        assertEquals(listOf("40", "40"), real(st = battle.copy(weather = "SUN", weatherSuppressed = true)).take(2).map { it.powerText }, "Cloud Nine: no weather")
        val pinch = lead.copy(abilityName = "Blaze", mon = lead.mon.copy(curHp = 9, maxHp = 29))
        assertEquals("60", real(p = pinch)[0].powerText, "Blaze at a third of HP: Ember 60")
        assertEquals("40", real(p = lead.copy(abilityName = "Blaze"))[0].powerText, "Blaze at full HP: no change")
        assertEquals("44", real(p = lead.copy(itemName = "Charcoal"))[0].powerText, "Charcoal: x1.1 (Gen 3)")
    }

    @Test
    fun `the options off, the card's own numbers, weather arrow kept`() {
        TrackerOptions.realHitChance = false
        TrackerOptions.realMovePower = false
        val r = real(st = battle.copy(weather = "SUN"), p = lead.copy(statStages = lead.statStages + ("ACC" to 5)))
        assertEquals(rows, r)
        TrackerOptions.realMovePower = true
        val only = real(p = lead.copy(statStages = lead.statStages + ("ACC" to 5)))
        assertEquals("100", only[0].accText, "hit chance off: Acc as printed while power is on")
    }
}
