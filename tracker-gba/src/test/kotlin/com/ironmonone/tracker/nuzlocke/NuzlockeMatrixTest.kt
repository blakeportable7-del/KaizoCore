package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every preset on every family of games (2026-09-30): the engine is the same for all of them, and what changes is the data
 * it reads and the few things a game cannot say. A preset must always start and play, and where a game cannot say
 * something (no genders in Generation 1, no battle style on the DS games) the run must not break or warn for nothing.
 */
class NuzlockeMatrixTest {

    private val tables = listOf(
        NuzlockeSystem.GEN1 to "rb", NuzlockeSystem.GEN2 to "c", NuzlockeSystem.GEN3 to "e",
        NuzlockeSystem.GEN4 to "pt", NuzlockeSystem.GEN5 to "b2w2",
    )

    private fun rulesOf(preset: NuzlockePreset) = NuzlockeRules.forPreset(preset, if (preset == NuzlockePreset.MONOTYPE) WATER else null)

    /** A run of [preset] on [system]: the starter, one catch on Route 1, one dead Pokemon and a boss fight. */
    private fun play(system: NuzlockeSystem, game: String, preset: NuzlockePreset): Sim {
        val s = Sim(rulesOf(preset), system)
        s.caps = LevelCapTable.standard(game, system)
        val gendered = system.hasGender
        s.starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 5, nickname = "Shell", gender = if (gendered) Gender.MALE else null, types = listOf(WATER)))
        s.moveTo("Route 1", 12)
        s.catchIt(
            foe(100, 129, "MAGIKARP", 4, gender = if (gendered) Gender.FEMALE else null, types = listOf(WATER)),
            mon(100, 129, "MAGIKARP", 4, nickname = "Flop", gender = if (gendered) Gender.FEMALE else null, types = listOf(WATER)),
        )
        return s
    }

    @Test
    fun `every preset starts and takes its first catch on every family of games`() {
        for ((system, game) in tables) for (preset in NuzlockePreset.entries) {
            val s = play(system, game, preset)
            val where = "${system.key} ${preset.key}"
            assertTrue(s.ledger.meta.started, "$where: the rules begin")
            val enc = assertNotNull(s.enc("Route 1"), "$where: the first encounter is recorded")
            assertEquals(Outcome.CAUGHT, enc.outcome, where)
            assertEquals(2, s.ledger.roster.size, where)
            assertEquals(system, s.ledger.meta.system, where)
            if (preset == NuzlockePreset.WEDLOCKE) {
                assertEquals(s.ledger.roster.getValue(100L).partner, 1L, "$where: the two are a pair, by gender or by order")
            }
            // The ledger saves and loads whole, whatever the family.
            val back = assertNotNull(NuzlockeText.parse(NuzlockeText.format(s.ledger)), where)
            assertEquals(system, back.meta.system, where)
            assertEquals(NuzlockeText.format(s.ledger), NuzlockeText.format(back), "$where: the file is stable")
        }
    }

    @Test
    fun `a Wedlocke encounter of the wrong gender is skipped only where the game has genders`() {
        for ((system, game) in tables) {
            val s = Sim(rulesOf(NuzlockePreset.WEDLOCKE), system)
            s.caps = LevelCapTable.standard(game, system)
            val g = system.hasGender
            s.starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 5, nickname = "Shell", gender = if (g) Gender.MALE else null, types = listOf(WATER)))
            s.moveTo("Route 1", 12)
            s.wildEncounter(foe(100, 129, "MAGIKARP", 4, gender = if (g) Gender.MALE else null, types = listOf(WATER)), BattleEnd.WON)
            val rec = s.ledger.areas.getValue("Route 1")
            if (g) {
                assertEquals(ExtraKind.GENDER, rec.extras.single().kind, "${system.key}: a second male is skipped")
                assertNull(rec.encounter, "${system.key}: and the area stays open")
            } else {
                assertEquals(Outcome.FAINTED, assertNotNull(rec.encounter, system.key).outcome, "${system.key}: no genders, so nothing to skip")
            }
        }
    }

    @Test
    fun `Hardcore warns for a party over the cap of each gym leader, on every game's own table`() {
        for ((system, game) in tables) {
            val table = LevelCapTable.standard(game, system)
            for (gym in table.bosses.filter { it.kind == "gym" }) {
                for ((level, warns) in listOf(gym.cap + 1 to true, gym.cap to false)) {
                    val s = Sim(rulesOf(NuzlockePreset.HARDCORE), system)
                    s.caps = table
                    s.starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", level, hp = 90, maxHp = 90, nickname = "Shell", types = listOf(WATER)))
                    s.trainerBattle(NzOpponent(gym.trainerIds.first(), "Leader ${gym.label}", "Gym", gym.key, null), foe(900))
                    assertEquals(if (warns) 1 else 0, s.warnings(WarnKind.CAP).size, "${system.key} $game ${gym.key} (cap ${gym.cap}) at level $level")
                }
            }
        }
    }

    @Test
    fun `the preset of the run without caps warns for nothing`() {
        for ((system, game) in tables) {
            val table = LevelCapTable.standard(game, system)
            val gym = table.bosses.first { it.kind == "gym" }
            val s = Sim(rulesOf(NuzlockePreset.STANDARD), system)
            s.caps = table
            s.starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 99, hp = 90, maxHp = 90, nickname = "Shell", types = listOf(WATER)))
            s.trainerBattle(NzOpponent(gym.trainerIds.first(), "Leader ${gym.label}", "Gym", gym.key, null), foe(900))
            assertTrue(s.warnings(WarnKind.CAP).isEmpty(), "${system.key}: Standard has no level caps")
        }
    }

    @Test
    fun `the Set style reminder speaks only where the game says its style, and stays quiet where it cannot`() {
        for (system in NuzlockeSystem.entries) {
            val quiet = Sim(rulesOf(NuzlockePreset.HARDCORE), system)
            quiet.style = null
            quiet.starter()
            quiet.idle(3)
            assertFalse(quiet.ledger.meta.styleShift, "${system.key}: an unread style says nothing")
            assertTrue(NuzlockeView.panel(quiet.ledger, quiet.snapshot()).lines.none { "Battle style" in it.text }, system.key)
            val shift = Sim(rulesOf(NuzlockePreset.HARDCORE), system)
            shift.style = false
            shift.starter()
            shift.idle(3)
            assertTrue(shift.ledger.meta.styleShift, "${system.key}: Shift is seen")
            assertTrue(NuzlockeView.panel(shift.ledger, shift.snapshot()).lines.any { "Battle style is Shift" in it.text }, system.key)
        }
    }

    @Test
    fun `a Monotype run skips a wrong type on every game, and a Genlocke saves its survivors`() {
        for ((system, game) in tables) {
            val mono = Sim(rulesOf(NuzlockePreset.MONOTYPE), system)
            mono.caps = LevelCapTable.standard(game, system)
            mono.starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 5, nickname = "Shell", types = listOf(WATER)))
            mono.moveTo("Route 1", 12)
            mono.wildEncounter(foe(100, Sp.RATTATA, "RATTATA", 3, types = listOf(NORMAL)), BattleEnd.WON)
            assertEquals(ExtraKind.TYPE, mono.ledger.areas.getValue("Route 1").extras.single().kind, "${system.key}: a Normal Pokemon does not count")
            assertNull(mono.enc("Route 1"), system.key)
            val gen = Sim(rulesOf(NuzlockePreset.GENLOCKE), system)
            assertTrue(gen.ledger.meta.rules.genlocke, system.key)
        }
    }
}
