package com.ironmonone.tracker.gachamon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The port against the PC tracker's own code. reference-cases.tsv is what Ironmon-Tracker 9.3.1's GachaMonData.lua,
 * GachaMonFileManager.lua and StructEncoder.lua answered when run (Lua 5.1, tools/gachamon/reference_fixtures.py) on
 * Blake's card and 465 generated Pokemon: every ruleset, Nat. Dex on and off, randomized move data, 0 to 5+ stars.
 * Each must rate, star, power and encode here exactly as there.
 */
class GachaMonReferenceTest {

    data class Case(
        val name: String, val input: RatingInput, val ruleset: String, val card: GachaMonCard,
        val rating: Int, val stars: Int, val power: Int, val code: String,
    )

    private fun ints(s: String) = s.split(',').map { it.trim().toInt() }

    private val cases: List<Case> by lazy {
        val text = javaClass.getResourceAsStream("/gachamon/reference-cases.tsv")!!.bufferedReader().readText()
        val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        val header = lines.first().split('\t')
        lines.drop(1).map { line ->
            val f = header.zip(line.split('\t')).toMap()
            fun i(k: String) = f.getValue(k).trim().toInt()
            val base = ints(f.getValue("base"))
            val stats = ints(f.getValue("stats"))
            val moves = f.getValue("moves").split(' ').filter { it.isNotBlank() }.map { m ->
                val p = m.split(':')
                MoveFacts(p[0].toInt(), p[1].toInt(), p[2], p[3].toInt(), MoveCategory.valueOf(p[4]))
            }
            val types = ints(f.getValue("types"))
            val six = { l: List<Int> -> SixStats(l[0], l[1], l[2], l[3], l[4], l[5]) }
            val input = RatingInput(
                abilityId = i("ability"), types = types, baseStats = six(base), listedBst = i("listedBst"),
                evolves = i("evolves") == 1, natDex = i("natDex") == 1, moves = moves, stats = six(stats), nature = i("nature"),
            )
            val date = f.getValue("date").split('-').map { it.toInt() }
            val card = GachaMonCard(
                version = 2, personality = f.getValue("personality").toLong(), pokemonId = i("species"), level = i("level"),
                abilityId = i("ability"), ratingScore = i("rating"), battlePower = i("battlePower"), favorite = i("favorite"),
                gameWinner = i("winner"), seedNumber = i("seed"), badges = i("badges"), type1 = types[0],
                type2 = types.getOrElse(1) { types[0] }, stats = six(stats), moveIds = List(4) { moves.getOrNull(it)?.id ?: 0 },
                gameVersion = i("game"), keep = i("keep"), isShiny = i("shiny"), gender = i("gender"), nature = i("nature"),
                year = date[0], month = date[1], day = date[2],
            )
            Case(f.getValue("name"), input, f.getValue("ruleset"), card, i("rating"), i("stars"), i("battlePower"), f.getValue("code"))
        }
    }

    private val rs = GachaMonRatingSystem.default

    @Test
    fun `there are enough cases, at every star count`() {
        assertTrue(cases.size > 400, "only ${cases.size} cases")
        assertEquals((0..6).toSet(), cases.map { it.stars }.toSet())
        assertTrue(cases.any { it.input.natDex } && cases.any { it.ruleset == "SurvivalRevival" })
    }

    @Test
    fun `every case rates as the PC tracker rated it`() {
        val wrong = cases.filter { rs.ratingScore(it.input, it.ruleset) != it.rating }
            .map { "${it.name}: ${rs.ratingScore(it.input, it.ruleset)} here, ${it.rating} on PC" }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    @Test
    fun `every case has the PC tracker's stars and Battle Power`() {
        val wrong = cases.filter { rs.stars(it.rating, 2) != it.stars || rs.battlePower(it.input, it.stars) != it.power }
            .map { "${it.name}: ${rs.stars(it.rating, 2)} stars and ${rs.battlePower(it.input, it.stars)} BP here, ${it.stars} and ${it.power} on PC" }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    @Test
    fun `every card makes the PC tracker's share code, and the code reads back to the card`() {
        for (c in cases) {
            assertEquals(c.code, GachaMonCodec.shareCode(c.card), c.name)
            assertEquals(c.card, GachaMonCodec.fromShareCode(c.code), c.name)
        }
    }

    @Test
    fun `the golden case is Blake's card`() {
        val g = cases.first { it.name == "golden-charizard" }
        assertEquals(69, g.rating)
        assertEquals(5, g.stars)
        assertEquals(8000, g.power)
        val decoded = assertNotNull(GachaMonCodec.fromShareCode(g.code))
        assertEquals(6, decoded.pokemonId)
        assertEquals(1220, decoded.seedNumber)
    }
}
