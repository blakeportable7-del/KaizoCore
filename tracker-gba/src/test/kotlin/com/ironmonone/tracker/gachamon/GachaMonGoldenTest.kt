package com.ironmonone.tracker.gachamon

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Blake's own PC card, collected 07/22/26 on attempt 1,220 of Emerald: Charizard, male, Impish, Lv. 5, Compoundeyes,
 * Crabhammer, Bonemerang, Eruption, Dragon Claw, HP 17 ATK 20 DEF 11 SPA 16 SPD 21 SPE 9. The PC tracker says
 * "69 points (5 stars)", 8000 BP, v2.
 *
 * The seed randomized base stats, which a card does not keep. At level 5 the stats it shows need an Attack and Sp. Atk
 * of 110 or more, HP + Def + Sp. Def under 240 and Speed under 60 (HP about 5 to 29, Atk 135 to 159, Def 35 to 59,
 * Sp. Atk 115 to 139, Sp. Def 145 to 169, Speed 25 to 49): the ones below sit in those ranges. The rating only reads
 * which side of each line a stat is on, so any base stats in them give the same numbers.
 */
class GachaMonGoldenTest {
    private val rs = GachaMonRatingSystem.default

    private fun move(id: Int, type: Int, power: String, acc: Int, cat: MoveCategory) = MoveFacts(id, type, power, acc, cat)

    private val charizard = GachaMonMaker.Mon(
        personality = 0x12345678L, species = 6, level = 5, abilityId = GachaMonMoves.COMPOUNDEYES,
        stats = SixStats(hp = 17, atk = 20, def = 11, spa = 16, spd = 21, spe = 9),
        moves = listOf(
            move(152, 11, "90", 85, MoveCategory.SPECIAL),    // Crabhammer, Water
            move(155, 4, "50", 90, MoveCategory.PHYSICAL),    // Bonemerang, Ground, hits twice
            move(284, 10, ">HP", 100, MoveCategory.SPECIAL),  // Eruption, Fire, the reference's text for a variable move
            move(337, 16, "80", 100, MoveCategory.SPECIAL),   // Dragon Claw, Dragon
        ),
        nature = 8, gender = 1, shiny = false, types = listOf(10, 2),
        baseStats = SixStats(hp = 20, atk = 140, def = 45, spa = 125, spd = 155, spe = 35),
        listedBst = 534, evolves = false,
    )

    @Test
    fun `Blake's Charizard is 69 points, 5 stars, 8000 BP, v2`() {
        val card = GachaMonMaker.make(charizard, natDex = false, gameVersion = 2, seed = 1220, year = 2026, month = 7, day = 22,
            rulesetKey = "Kaizo", rs = rs, random = Random(1))
        assertEquals(69, card.ratingScore)
        assertEquals(5, card.stars(rs))
        assertEquals(8000, card.battlePower)
        assertEquals(2, card.version)
        assertEquals("Emerald", card.gameName)
        assertEquals(1220, card.seedNumber)
        // Five stars or more is always made shiny, and so kept (convertPokemonToGachaMon).
        assertEquals(1, card.isShiny)
        assertEquals(1, card.keep)
        // The rating is the same under every ruleset that bans none of it (Survival Revival rates as Standard on PC).
        for (r in listOf("Standard", "Ultimate", "Survival", "SurvivalRevival", "SuperKaizo", "Subpar"))
            assertEquals(69, rs.ratingScore(GachaMonMaker.ratingInput(charizard, false), r), r)
    }

    @Test
    fun `where the 69 comes from`() {
        // 5 for Compoundeyes; the moves 8.39 x 1.1 (Compoundeyes on 85% Crabhammer) + 10.02 x 1.1 (90% Bonemerang)
        // + 9.94 x 1.5 (Eruption, same type) + 8.99; 20 for Attack and Sp. Atk, capped; nothing for bulk, speed or
        // an Impish nature on Attack: 69.151, rounded to 69.
        val input = GachaMonMaker.ratingInput(charizard, false)
        assertEquals(69, rs.ratingScore(input, "Kaizo"))
        // Without the same-type Eruption it is 64, four stars: the Fire typing is part of the card.
        assertEquals(64, rs.ratingScore(input.copy(types = listOf(2)), "Kaizo"))
        // 5 stars x 1000, (90 + 2 x 50 + 150 + 80) / 150 = 2 x 1000, a same-type move 1000, a neutral nature on Attack.
        assertEquals(8000, rs.battlePower(input, 5))
    }

    @Test
    fun `stars follow the card's own version`() {
        // Version 2 lowered the 5+ card from 80 points to 77.
        assertEquals(6, rs.stars(77, 2))
        assertEquals(5, rs.stars(77, 1))
        assertEquals(6, rs.stars(80, 1))
        assertEquals(5, rs.stars(67, 2))
        assertEquals(4, rs.stars(66, 2))
        assertEquals(1, rs.stars(1, 2))
        assertEquals(0, rs.stars(0, 2))
        // A version the reference has no table for reads the current one.
        assertEquals(6, rs.stars(77, 9))
    }

    @Test
    fun `the share code goes to the PC tracker and back`() {
        val card = GachaMonMaker.make(charizard, natDex = false, gameVersion = 2, seed = 1220, year = 2026, month = 7, day = 22,
            rulesetKey = "Kaizo", rs = rs, random = Random(1)).copy(favorite = 1, badges = 0b101, gameWinner = 1)
        val code = GachaMonCodec.shareCode(card)
        assertEquals(44, code.length)
        assertEquals(card, GachaMonCodec.fromShareCode(code))
        // Pasted with spaces and a line break, as a chat app hands it over.
        assertEquals(card, GachaMonCodec.fromShareCode(" " + code.chunked(10).joinToString("\n ") + " "))
        assertNull(GachaMonCodec.fromShareCode("not a card"))
        assertNull(GachaMonCodec.fromShareCode(""))
    }

    @Test
    fun `a code made by the PC tracker reads as Blake's card`() {
        // GachaMonData.getShareablyCode on the PC tracker's own code (tools/gachamon/reference_fixtures.py) for the
        // golden card, personality 0x9E3779B1: the card's record in the reference's format.
        val card = assertNotNull(GachaMonCodec.fromShareCode("ArF5N54GQAUORcQEAAoCEVCwABBUkACYNnGMqkP2NA=="))
        assertEquals(2, card.version)
        assertEquals(0x9E3779B1L, card.personality)
        assertEquals(6, card.pokemonId)
        assertEquals(5, card.level)
        assertEquals(14, card.abilityId)
        assertEquals(69, card.ratingScore)
        assertEquals(5, card.stars(rs))
        assertEquals(8000, card.battlePower)
        assertEquals(1220, card.seedNumber)
        assertEquals(listOf(152, 155, 284, 337), card.moveIds)
        assertEquals(SixStats(17, 20, 11, 16, 21, 9), card.stats)
        assertEquals(2, card.gameVersion)
        assertEquals(8, card.nature)
        assertEquals(1, card.gender)
        assertEquals(listOf(2026, 7, 22), listOf(card.year, card.month, card.day))
        assertEquals(10 to 2, card.type1 to card.type2)
    }

    @Test
    fun `a Nat Dex move past nine bits goes as no move, never as another`() {
        val card = GachaMonMaker.make(charizard, natDex = true, gameVersion = 3, seed = 1, year = 2026, month = 10, day = 3,
            rulesetKey = "Standard", rs = rs, random = Random(1)).copy(moveIds = listOf(152, 600, 284, 337), abilityId = 300)
        val back = assertNotNull(GachaMonCodec.fromShareCode(GachaMonCodec.shareCode(card)))
        assertEquals(listOf(152, 0, 284, 337), back.moveIds)
        assertEquals(0, back.abilityId)
    }

    @Test
    fun `a card's own shiny chance, and a shiny card is kept`() {
        val weak = charizard.copy(abilityId = 0, moves = charizard.moves.take(1), baseStats = SixStats(30, 30, 30, 30, 30, 30))
        // Random that answers 0.0 first: under one in 213, so the card is shiny and kept.
        val lucky = object : Random() { override fun nextBits(bitCount: Int) = 0 }
        val shiny = GachaMonMaker.make(weak, false, 3, 1, 2026, 10, 3, "Standard", rs, lucky)
        assertEquals(1, shiny.isShiny)
        assertEquals(1, shiny.keep)
        val plain = GachaMonMaker.make(weak, false, 3, 1, 2026, 10, 3, "Standard", rs, object : Random() { override fun nextBits(bitCount: Int) = -1 ushr (32 - bitCount) })
        assertEquals(0, plain.isShiny)
        assertEquals(0, plain.keep)
    }

    @Test
    fun `a prize card's trainer, by the card's game`() {
        val base = GachaMonMaker.make(charizard, false, 3, 1, 2026, 10, 3, "Standard", rs, Random(1))
        assertEquals("Brock", GachaMonPrize.trainerName(base.copy(personality = 414, gameVersion = 3)))
        assertEquals("Lt. Surge", GachaMonPrize.trainerName(base.copy(personality = 416, gameVersion = 5)))
        assertEquals("Rival", GachaMonPrize.trainerName(base.copy(personality = 327, gameVersion = 3)))
        assertEquals("Tate & Liza", GachaMonPrize.trainerName(base.copy(personality = 271, gameVersion = 2)))
        assertEquals("Steven", GachaMonPrize.trainerName(base.copy(personality = 804, gameVersion = 2)))
        assertEquals("Wallace", GachaMonPrize.trainerName(base.copy(personality = 272, gameVersion = 1)))
        assertNull(GachaMonPrize.trainerName(base.copy(personality = 414, gameVersion = 2)))
        assertNull(GachaMonPrize.trainerName(base.copy(personality = 0x12345678L)))
    }
}
