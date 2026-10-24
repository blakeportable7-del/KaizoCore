package com.ironmonone.tracker

import com.ironmonone.tracker.HitChance.Hold
import com.ironmonone.tracker.HitChance.Move
import com.ironmonone.tracker.HitChance.Side
import com.ironmonone.tracker.HitChance.Weather
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HitChance against numbers worked by hand from pokehns-expansion's GetTotalAccuracy, DoesOHKOMoveMissTarget and
 * CalcCritChanceStage (src/battle_util.c), and its ROM side against the comfort build's own tables.
 */
class HitChanceTest {
    private val tackle = Move(100, physical = true)
    private val none = Side(ability = "Blaze")

    private fun acc(m: Move = tackle, a: Side = none, d: Side = none, w: Weather = Weather.NONE, gravity: Boolean = false, moved: Boolean = false) =
        HitChance.of(m, a, d, w, gravity = gravity, defMovedThisTurn = moved)

    @Test
    fun `stages follow gAccuracyStageRatios`() {
        assertEquals(listOf(75, 60, 50, 43, 36, 33), (1..6).map { acc(a = none.copy(accStage = -it)).chance })
        assertEquals(listOf(133, 166, 200), (1..3).map { acc(a = none.copy(accStage = it)).chance })
        assertEquals(75, acc(d = none.copy(evaStage = 1)).chance)
        assertEquals(100, acc(a = none.copy(accStage = 1), d = none.copy(evaStage = 1)).chance, "stages cancel")
        assertEquals(33, acc(a = none.copy(accStage = -6), d = none.copy(evaStage = 6)).chance, "clamped at -6")
        assertEquals(63, acc(Move(85), a = none.copy(accStage = -1)).chance, "85 x 75/100 = 63.75, floored")
    }

    @Test
    fun `abilities`() {
        assertEquals(78, acc(Move(60), a = none.copy(ability = "Compound Eyes")).chance)
        assertEquals(80, acc(a = none.copy(ability = "Hustle")).chance)
        assertEquals(100, acc(Move(100, physical = false), a = none.copy(ability = "Hustle")).chance, "Hustle only on physical")
        assertEquals(110, acc(a = none.copy(ability = "Victory Star")).chance)
        assertEquals(110, acc(a = none.copy(ally = "Victory Star")).chance)
        assertEquals(80, acc(d = none.copy(ability = "Sand Veil"), w = Weather.SAND).chance)
        assertEquals(100, acc(d = none.copy(ability = "Sand Veil")).chance, "Sand Veil needs sand")
        assertEquals(80, acc(d = none.copy(ability = "Snow Cloak"), w = Weather.HAIL_SNOW).chance)
        assertEquals(50, acc(d = none.copy(ability = "Tangled Feet", confused = true)).chance)
        assertEquals(100, acc(a = none.copy(ability = "Keen Eye"), d = none.copy(evaStage = 2)).chance, "Keen Eye ignores evasion")
        assertEquals(100, acc(d = none.copy(ability = "Unaware", evaStage = 0), a = none.copy(accStage = -2)).chance, "Unaware ignores the attacker's accuracy drop")
        assertEquals(50, acc(Move(100, status = true), d = none.copy(ability = "Wonder Skin")).chance)
        assertEquals(100, acc(tackle, d = none.copy(ability = "Wonder Skin")).chance, "Wonder Skin only on status moves")
        assertTrue(acc(Move(30), a = none.copy(ability = "No Guard")).sure)
        assertTrue(acc(Move(30), d = none.copy(ability = "No Guard")).sure)
    }

    @Test
    fun `items`() {
        assertEquals(90, acc(d = none.copy(hold = Hold.EVASION_UP, holdParam = 10)).chance)
        assertEquals(110, acc(a = none.copy(hold = Hold.WIDE_LENS, holdParam = 10)).chance)
        assertEquals(120, acc(a = none.copy(hold = Hold.ZOOM_LENS, holdParam = 20), moved = true).chance)
        assertEquals(100, acc(a = none.copy(hold = Hold.ZOOM_LENS, holdParam = 20)).chance, "Zoom Lens only moving second")
        assertEquals(120, acc(a = none.copy(usedMicleBerry = true)).chance)
        assertEquals(140, acc(a = none.copy(usedMicleBerry = true, ability = "Ripen")).chance)
        // Compound Eyes then BrightPowder, in the game's order: 70 x 1.3 = 91, x 0.9 = 81.
        assertEquals(81, acc(Move(70), a = none.copy(ability = "Compound Eyes"), d = none.copy(hold = Hold.EVASION_UP, holdParam = 10)).chance)
    }

    @Test
    fun `field and weather`() {
        assertEquals(166, acc(gravity = true).chance)
        assertEquals(60, acc(w = Weather.FOG).chance)
        val thunder = Move(70, fiftyInSun = true, sureInRain = true)
        assertEquals(50, acc(thunder, w = Weather.SUN).chance)
        assertTrue(acc(thunder, w = Weather.RAIN).sure)
        assertEquals(70, HitChance.of(thunder, none, none, Weather.RAIN, weatherOn = false).chance, "Cloud Nine")
        assertTrue(acc(Move(70, sureInHailSnow = true), w = Weather.HAIL_SNOW).sure)
        assertTrue(acc(Move(90, sureOnSameType = true, type = 3), a = none.copy(types = setOf(3))).sure, "a move flagged alwaysHitsOnSameType, from its own type")
        assertTrue(acc(Move(100, minimizeDouble = true), d = none.copy(minimized = true)).sure)
        assertTrue(acc(Move(50), a = none.copy(sureHit = true)).sure, "Lock-On")
        assertEquals(100, acc(d = none.copy(evaStage = 3, foresight = true)).chance, "Foresight")
        assertEquals(100, acc(Move(100, ignoresEvasion = true), d = none.copy(evaStage = 3)).chance)
    }

    @Test
    fun `one-hit KO moves`() {
        val fissure = Move(30, ohko = true, type = 4)
        assertEquals(35, acc(fissure, a = none.copy(level = 40), d = none.copy(level = 35)).chance)
        assertEquals(0, acc(fissure, a = none.copy(level = 30), d = none.copy(level = 31)).chance)
        assertEquals(0, acc(fissure, a = none.copy(level = 40), d = none.copy(level = 35, ability = "Sturdy")).chance)
        assertEquals(30, acc(fissure, a = none.copy(level = 40, accStage = -6), d = none.copy(level = 40)).chance, "stages ignored")
        val sheerCold = Move(30, ohko = true, type = 15, plusTenOffType = true)
        assertEquals(20, acc(sheerCold, a = none.copy(level = 40), d = none.copy(level = 40)).chance, "Sheer Cold from a non-Ice type")
        assertEquals(30, acc(sheerCold, a = none.copy(level = 40, types = setOf(15)), d = none.copy(level = 40)).chance)
    }

    @Test
    fun `critical hits use the Gen 3 odds of the Nat Dex Kaizo build`() {
        // Patch 0047: B_CRIT_CHANCE GEN_3 (2 in the expansion's numbering), so 1/16, 1/8, 1/4, 1/3, 1/2.
        assertEquals(2, HnsLayout.B_CRIT_CHANCE)
        fun crit(m: Move = tackle, a: Side = none, d: Side = none) = HitChance.crit(m, a, d).oneIn
        assertEquals(16, crit())
        assertEquals(8, crit(Move(100, critStage = 1)), "Slash")
        assertEquals(8, crit(a = none.copy(hold = Hold.SCOPE_LENS)))
        assertEquals(8, crit(a = none.copy(ability = "Super Luck")))
        assertEquals(4, crit(a = none.copy(focusEnergy = true)), "Focus Energy or Dire Hit")
        assertEquals(3, crit(Move(100, critStage = 1), a = none.copy(focusEnergy = true)))
        assertEquals(2, crit(Move(100, critStage = 1), a = none.copy(focusEnergy = true, hold = Hold.SCOPE_LENS, ability = "Super Luck")), "capped")
        assertEquals(4, crit(a = none.copy(hold = Hold.LEEK, species = "Farfetch'd")))
        assertEquals(16, crit(a = none.copy(hold = Hold.LEEK, species = "Pikachu")), "Leek on anyone else")
        assertEquals(4, crit(a = none.copy(hold = Hold.LUCKY_PUNCH, species = "Chansey")))
        assertEquals(1, crit(Move(60, alwaysCrit = true)), "Frost Breath")
        assertEquals(1, crit(a = none.copy(ability = "Merciless"), d = none.copy(poisoned = true)))
        assertEquals(0, crit(Move(60, alwaysCrit = true), d = none.copy(ability = "Shell Armor")))
        assertEquals(0, crit(d = none.copy(luckyChant = true)))
        assertEquals(0, HitChance.crit(tackle, none, none, noCrits = true).oneIn, "first rival battle")
    }

    @Test
    fun `unknown abilities and items are named`() {
        val r = HitChance.of(tackle, none, Side(hold = Hold.UNKNOWN))
        assertEquals(listOf("target's ability", "target's item"), r.unknowns)
    }

    private val layout: String? by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "app/src/main/assets/hns/layout-kaizo.json") }
            .firstOrNull { it.isFile }?.readText()
    }

    /** An enum value out of the layout JSON; each constant's name is written there once. */
    private fun id(name: String): Int = Regex("\"" + name + """"\s*:\s*(\d+)""").find(layout!!)!!.groupValues[1].toInt()

    @Test
    fun `hold effect ids are this build's`() {
        layout ?: return
        assertEquals(id("HOLD_EFFECT_EVASION_UP"), HitChance.Rom.HOLD_EVASION_UP)
        assertEquals(id("HOLD_EFFECT_SCOPE_LENS"), HitChance.Rom.HOLD_SCOPE_LENS)
        assertEquals(id("HOLD_EFFECT_LUCKY_PUNCH"), HitChance.Rom.HOLD_LUCKY_PUNCH)
        assertEquals(id("HOLD_EFFECT_LEEK"), HitChance.Rom.HOLD_LEEK)
        assertEquals(id("HOLD_EFFECT_WIDE_LENS"), HitChance.Rom.HOLD_WIDE_LENS)
        assertEquals(id("HOLD_EFFECT_ZOOM_LENS"), HitChance.Rom.HOLD_ZOOM_LENS)
    }

    @Test
    fun `the ROM's moves and items read as the game defines them`() {
        val f = Dumps.file(HnsTrackerTest.romDir(), "hns-kaizo.gba") ?: run { println("SKIP: hns-kaizo.gba is not on this machine"); return }
        val bytes = f.readBytes()
        val d = HnsData { a, n ->
            val o = (a - 0x08000000L).toInt()
            if (a < 0x08000000L || o >= bytes.size) ByteArray(0) else bytes.copyOfRange(o, minOf(bytes.size, o + n))
        }
        layout ?: return
        fun move(name: String) = HitChance.Rom.move(d, id("MOVE_$name"))!!
        fun item(name: String) = HitChance.Rom.item(d, id("ITEM_$name"))

        for (m in listOf("GUILLOTINE", "HORN_DRILL", "FISSURE", "SHEER_COLD")) assertTrue(move(m).ohko, m)
        assertTrue(!move("SHEER_COLD").plusTenOffType, "B_SHEER_COLD_ACC is GEN_3 on the Nat. Dex Kaizo build")
        assertTrue(!move("TACKLE").ohko && !move("TACKLE").plusTenOffType)
        assertEquals(1, move("SLASH").critStage)
        assertTrue(move("FROST_BREATH").alwaysCrit)
        assertTrue(move("THUNDER").sureInRain && move("THUNDER").fiftyInSun)
        assertTrue(!move("BLIZZARD").sureInHailSnow, "B_BLIZZARD_HAIL is GEN_3: Blizzard can miss in hail, as in Nat. Dex Emerald")
        assertTrue(!move("TOXIC").sureOnSameType && move("TOXIC").status, "B_TOXIC_NEVER_MISS is GEN_3 here: Toxic can miss from a Poison type")
        assertTrue(move("STOMP").minimizeDouble)
        assertTrue(move("CHIP_AWAY").ignoresEvasion)
        assertEquals(0, move("SWIFT").accuracy, "Swift never misses")
        assertEquals(Hold.EVASION_UP to 10, item("BRIGHT_POWDER"))
        assertEquals(Hold.WIDE_LENS to 10, item("WIDE_LENS"))
        assertEquals(Hold.ZOOM_LENS to 20, item("ZOOM_LENS"))
        assertEquals(Hold.SCOPE_LENS, item("SCOPE_LENS").first)
        assertEquals(Hold.LEEK, item("LEEK").first)
        assertEquals(Hold.NONE, item("POTION").first)
    }
}
