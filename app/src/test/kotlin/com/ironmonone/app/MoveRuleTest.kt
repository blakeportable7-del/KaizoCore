package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Banned moves per game and per mode, marked with the BST rule's X (Blake, 2026-10-02). The lists are the IronMON
 * banned-moves reference and each mode's own rules; see MoveRule.
 */
class MoveRuleTest {
    private fun rules(mode: String, family: String = "FRLG", natDex: Boolean = false, kind: String? = null) =
        assertNotNull(MoveRule.rules(mode, family, natDex, kind), "$mode on $family")

    private val wild = MoveRule.Battle(inBattle = true, wild = true)
    private val trainer = MoveRule.Battle(inBattle = true, wild = false)
    private val walking = MoveRule.Battle()

    @Test
    fun `Standard bans nothing, and Ultimate only each game's own HM moves`() {
        assertNull(MoveRule.rules("standard", "FRLG", false))
        assertNull(MoveRule.rules(null, "FRLG", false))
        val u = rules("ultimate")
        assertNotNull(u.banOf("SURF"))
        assertNull(u.banOf("RECOVER"), "Ultimate keeps healing moves")
        fun hm(family: String, move: String) = rules("ultimate", family).banOf(move) != null
        assertTrue(hm("RSE", "Dive")); assertFalse(hm("FRLG", "Dive"), "FireRed has no Dive HM")
        assertTrue(hm("FRLG", "Rock Smash")); assertFalse(hm("GSC", "Rock Smash"), "a TM in Gold, Silver and Crystal")
        assertTrue(hm("GSC", "Whirlpool")); assertTrue(hm("HGSS", "Whirlpool")); assertFalse(hm("DPPt", "Whirlpool"))
        assertTrue(hm("DPPt", "Defog")); assertFalse(hm("HGSS", "Defog"))
        assertTrue(hm("DPPt", "Rock Climb")); assertTrue(hm("HGSS", "Rock Climb"))
        for (f in listOf("RBY", "GSC", "RSE", "FRLG")) assertTrue(hm(f, "Flash"), f)
        for (f in listOf("DPPt", "HGSS", "BW", "B2W2")) assertFalse(hm(f, "Flash"), f)
        assertFalse(hm("RBY", "Waterfall"), "not an HM in Red, Blue and Yellow")
    }

    @Test
    fun `Kaizo bans healing, draining, status healing, switching, Spore and Assist, however the game spells them`() {
        val k = rules("kaizo")
        for (name in listOf("SOFTBOILED", "Softboiled", "Soft-Boiled", "RECOVER", "REST", "PAIN SPLIT", "GIGA DRAIN",
            "LEECH SEED", "AROMATHERAPY", "HEAL BELL", "BATON PASS", "U-turn", "SPORE", "ASSIST", "SURF")) {
            assertEquals(MoveRule.When.ALWAYS, k.banOf(name)?.whenBanned, name)
            assertTrue(MoveRule.shows(k.banOf(name)!!, walking) && MoveRule.shows(k.banOf(name)!!, trainer), name)
        }
        for (name in listOf("METRONOME", "MIMIC", "SNATCH", "TELEPORT", "POLLEN PUFF", "SWORDS DANCE", "TACKLE", ""))
            assertNull(k.banOf(name), name)
        assertEquals(k.banOf("RECOVER"), rules("ironmonjourney").banOf("Recover"), "Journey is Kaizo's list")
    }

    @Test
    fun `Survival allows Cut, and draining and status healing moves in trainer battles only`() {
        val s = rules("survival")
        assertNull(s.banOf("CUT"), "Cut is no longer banned")
        assertNotNull(s.banOf("SURF"))
        for (name in listOf("GIGA DRAIN", "ABSORB", "LEECH SEED", "AROMATHERAPY", "REFRESH")) {
            val b = assertNotNull(s.banOf(name), name)
            assertEquals(MoveRule.When.WILD, b.whenBanned, name)
            assertTrue(MoveRule.shows(b, wild), name)
            assertFalse(MoveRule.shows(b, trainer), "$name is allowed against a trainer")
            assertFalse(MoveRule.shows(b, walking), "$name: no X outside a battle")
        }
        for (name in listOf("SPORE", "PAIN SPLIT", "RECOVER", "BATON PASS"))
            assertEquals(MoveRule.When.ALWAYS, s.banOf(name)?.whenBanned, name)
    }

    @Test
    fun `Super Kaizo bans draining attacks in wild battles, setup moves against bosses, and No Guard's sure hits`() {
        val sk = rules("superkaizo")
        assertEquals(MoveRule.When.WILD, sk.banOf("GIGA DRAIN")?.whenBanned)
        assertEquals(MoveRule.When.ALWAYS, sk.banOf("LEECH SEED")?.whenBanned, "a non-attacking drain stays banned")
        assertEquals(MoveRule.When.ALWAYS, sk.banOf("HEAL BELL")?.whenBanned)
        val dance = assertNotNull(sk.banOf("SWORDS DANCE"))
        assertEquals(MoveRule.When.BOSS, dance.whenBanned)
        val brock = sk.battle(inBattle = true, wild = false, trainerId = 414)
        val lorelei = sk.battle(true, false, 410)
        val champion = sk.battle(true, false, 438)
        val youngster = sk.battle(true, false, 89)
        assertTrue(brock.boss && !brock.elite); assertTrue(lorelei.elite); assertTrue(champion.elite && champion.boss)
        assertFalse(youngster.boss)
        assertTrue(MoveRule.shows(dance, brock)); assertFalse(MoveRule.shows(dance, youngster)); assertFalse(MoveRule.shows(dance, wild))
        assertNotNull(sk.banOf("SHEER COLD", ability = "NO GUARD")); assertNotNull(sk.banOf("Hypnosis", ability = "No Guard"))
        assertNull(sk.banOf("SHEER COLD", ability = "STURDY")); assertNull(sk.banOf("HYPNOSIS"))
        assertNull(rules("kaizo").banOf("SHEER COLD", ability = "NO GUARD"), "Super Kaizo's rule only")
    }

    @Test
    fun `the DS games' bosses come from their own tables`() {
        val pt = rules("superkaizo", "DPPt", kind = "platinum-u")
        assertTrue(pt.battle(true, false, 267).elite, "Cynthia")
        val hg = rules("superkaizo", "HGSS", kind = "heartgold-u")
        assertTrue(hg.battle(true, false, 260).boss, "Red, the final trainer")
        assertFalse(hg.battle(true, false, 485).boss, "Archer is a Team Rocket admin, not a boss of the rule")
    }

    @Test
    fun `Evo Kaizo allows draining moves and bans setup moves against the Elite Four and the champion only`() {
        val e = rules("evokaizo")
        assertNull(e.banOf("GIGA DRAIN")); assertNotNull(e.banOf("LEECH SEED"))
        val dance = assertNotNull(e.banOf("BULK UP"))
        assertEquals(MoveRule.When.ELITE, dance.whenBanned)
        assertFalse(MoveRule.shows(dance, e.battle(true, false, 414)), "a gym leader")
        assertTrue(MoveRule.shows(dance, e.battle(true, false, 411)), "Bruno")
    }

    @Test
    fun `Survival Revival bans Recycle in wild battles and setup moves against bosses, keeps Cut banned`() {
        val sr = rules("survivalrevival")
        assertEquals(MoveRule.When.WILD, sr.banOf("RECYCLE")?.whenBanned)
        assertEquals(MoveRule.When.BOSS, sr.banOf("CALM MIND")?.whenBanned)
        assertNotNull(sr.banOf("CUT"))
        assertTrue("lab" in sr.note.orEmpty())
    }

    @Test
    fun `Chaos Kaizo has its own list, and sleep and OHKO moves are banned only above 90 accuracy`() {
        val c = rules("chaoskaizo")
        assertNotNull(c.banOf("RECOVER")); assertNull(c.banOf("SURF"), "no HM rule"); assertNull(c.banOf("GIGA DRAIN"))
        assertNotNull(c.banOf("SPORE", accuracy = 100)); assertNull(c.banOf("SPORE", accuracy = 90))
        assertNotNull(c.banOf("SPORE", accuracy = 0), "never misses")
        assertNull(c.banOf("SPORE", accuracy = null), "an accuracy it cannot read is not marked")
        assertNull(c.banOf("SHEER COLD", accuracy = 30)); assertNotNull(c.banOf("HYPNOSIS", accuracy = 95))
        assertTrue("first fight" in c.note.orEmpty())
    }

    @Test
    fun `Kaizo Doubles bans a move both your Pokemon know, and Dark Void in trainer battles`() {
        val d = rules("kaizodoubles", "DPPt", kind = "platinum-u")
        assertNotNull(d.banOf("Water Spout", shared = true)); assertNull(d.banOf("Water Spout", shared = false))
        assertNull(rules("kaizo").banOf("Water Spout", shared = true), "Kaizo Doubles only")
        val party = listOf(1L to listOf("Water Spout", "Tackle"), 2L to listOf("WATER SPOUT", "Ember"), 3L to listOf("Tackle"))
        assertEquals(setOf("waterspout"), MoveRule.sharedFor(1L, party))
        assertEquals(setOf("waterspout"), MoveRule.sharedFor(2L, party))
        assertTrue(MoveRule.sharedFor(3L, party).isEmpty(), "only the first two fight")
        val dark = assertNotNull(d.banOf("Dark Void"))
        assertTrue(MoveRule.shows(dark, trainer)); assertFalse(MoveRule.shows(dark, wild))
    }

    @Test
    fun `Red, Blue and Yellow ban the trapping moves, and Survival frees them for 351 BST or less that can evolve`() {
        assertEquals(MoveRule.When.ALWAYS, rules("kaizo", "RBY").banOf("WRAP")?.whenBanned)
        assertNull(rules("ultimate", "RBY").banOf("WRAP")); assertNull(rules("kaizo", "GSC").banOf("WRAP"))
        val sv = rules("survival", "RBY")
        assertNull(sv.banOf("CLAMP", bst = 325, canEvolve = true))
        assertNotNull(sv.banOf("CLAMP", bst = 400, canEvolve = true)); assertNotNull(sv.banOf("CLAMP", bst = 325, canEvolve = false))
        assertNotNull(rules("kaizo", "RBY").banOf("CLAMP", bst = 325, canEvolve = true), "Survival's exception only")
    }

    @Test
    fun `Nat Dex states the HM rule on the card with no X, and bans its newer healing, draining and switching moves`() {
        val n = rules("kaizo", "FRLG", natDex = true)
        val surf = assertNotNull(n.banOf("Surf"))
        assertEquals(MoveRule.When.UNSEEN, surf.whenBanned)
        assertFalse(MoveRule.shows(surf, trainer)); assertTrue("HM item" in surf.line)
        for (name in listOf("Strength Sap", "Life Dew", "Bitter Blade", "Matcha Gotcha", "Flip Turn", "Shed Tail", "Parting Shot"))
            assertEquals(MoveRule.When.ALWAYS, n.banOf(name)?.whenBanned, name)
        assertNull(n.banOf("Teleport"), "it ends a wild battle in these games")
    }

    @Test
    fun `the card says the mode's exception`() {
        assertTrue("lab fight" in rules("kaizo").note.orEmpty())
        assertTrue("first three battles" in rules("kaizo", "BW").note.orEmpty())
        assertNull(rules("ultimate").note)
    }

    @Test
    fun `mark puts the X on only while the ban holds and the card's line on whenever there is one`() {
        val s = rules("survival")
        val drain = PcMove(name = "GIGA DRAIN", pp = 10, ppMax = 10, power = 60, acc = 100, color = Color.Green, category = "SPE")
        val inWild = MoveRule.mark(drain, s.banOf(drain.name), wild, s.note)
        assertTrue(inWild.banned); assertTrue("wild Pokemon" in inWild.banLine.orEmpty() && "lab fight" in inWild.banLine.orEmpty())
        val vsTrainer = MoveRule.mark(drain, s.banOf(drain.name), trainer, s.note)
        assertFalse(vsTrainer.banned); assertNotNull(vsTrainer.banLine)
        val blank = drain.copy(name = "---", blank = true)
        assertEquals(blank, MoveRule.mark(blank, s.banOf("GIGA DRAIN"), wild, s.note))
        assertEquals(drain, MoveRule.mark(drain, null, wild, s.note))
        assertEquals(inWild.banLine, detailOf(inWild, null).banLine, "the move's card carries it")
        for (line in listOf(inWild.banLine.orEmpty()) + MoveRule.When.values().map { MoveRule.Ban("a healing move", it).line })
            assertFalse('—' in line || '–' in line, line)
    }

    @Test
    fun `every move the rules name is a move some game has`() {
        val tables = listOf(
            "../tracker-gba/src/main/resources/gen1/movedesc.tsv", "../tracker-gba/src/main/resources/gen2/moves.tsv",
            "../tracker-gba/src/main/resources/gen3/movedesc.tsv", "../tracker-gba/src/main/resources/natdex/moves.tsv",
            "../tracker-nds/src/main/resources/gen4/moves.tsv", "../tracker-nds/src/main/resources/gen5/moves.tsv",
        )
        val known = tables.flatMap { p ->
            File(p).readLines(Charsets.UTF_8).mapNotNull { it.split('\t').getOrNull(1) }.map(MoveRule::key)
        }.toSet()
        assertTrue(known.size > 800, "the tables were read")
        for (name in MoveRule.ALL_NAMES) assertTrue(MoveRule.key(name) in known, "$name is no game's move")
    }

    @Test
    fun `both panels mark your own moves, and the row and card show it`() {
        fun read(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")
        val gba = read("TrackerPanel.kt")
        assertTrue("p.moveRows.map { markMove(it.toPcMove(moveCtx)) }" in gba)
        assertTrue("markMove = MoveRule.gbaMark(moveRules, p, state))" in gba)
        assertTrue("moveRules: MoveRule.Rules? = moveRulesInPlay(attempt)," in gba)
        assertTrue("banLine = mv.banLine," in gba, "detailOf carries the line")
        val ds = read("NdsTrackerPanel.kt")
        assertTrue("movesOf(p, inBattle, moveCtx, own = true, hiddenPowerType, hiddenPowerJustChanged).map(markMove)" in ds)
        assertTrue("markMove = MoveRule.ndsMark(moveRules, p, state))" in ds)
        assertTrue("listOfNotNull(r.banLine, ndsMoveDescription(r, gen))" in ds, "the DS card opens with why")
        val rows = read("PcTracker.kt").substringAfter("fun PcMovesSection(").substringBefore("\n}\n")
        assertTrue("r.banned -> Pc.Negative" in rows && "RuleCross(\"Banned in this run\")" in rows, "red name, then the X")
        assertTrue("d.banLine?.let { line ->" in read("PcMoveInfo.kt"))
    }
}
