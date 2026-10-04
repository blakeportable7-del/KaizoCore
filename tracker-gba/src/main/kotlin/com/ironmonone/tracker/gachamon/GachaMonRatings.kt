package com.ironmonone.tracker.gachamon

import com.ironmonone.tracker.Gen3Types
import kotlin.math.floor
import kotlin.math.min

/*
 * GachaMon, the collectable card game in Besteon's Ironmon-Tracker (9.3.x, MIT): every Pokemon a run starts with or
 * catches becomes a card, rated by its ability, moves, base stats and nature. This file is the rating:
 * GachaMonData.calculateRatingScore, calculateStars and calculateBattlePower (data/GachaMonData.lua:250-631), read line
 * for line, with the reference's own tables (GachaMonRatingSystem.json, copied byte for byte into the resources) and
 * its versioned star thresholds (GachaMonFileManager.LegacyStarRatings), so a card rates here as it does on PC.
 *
 * Every number is a Double where the Lua's is, and every product and sum happens in the Lua's order: the rounding at the
 * end (math.floor(total + 0.5)) sees the same floating-point value.
 */

/**
 * MoveData.Categories, as the rating reads them. NONE is MoveData.TypeToCategory's for the unused Mystery type: a
 * damaging move of that type is neither physical nor special to the reference.
 */
enum class MoveCategory { PHYSICAL, SPECIAL, STATUS, NONE }

/** What the reference's MoveData holds for one move, as the rating and Battle Power read it. */
data class MoveFacts(
    val id: Int,
    /** The game's type id (Gen3Types): 9 is the unused Mystery slot, the reference's UNKNOWN, never same-type. */
    val type: Int,
    /**
     * MoveData's power text: the ROM's number ("0" for a status move), or, for a variable-power move, the reference's
     * own text ("WT", ">HP", "0"), which MoveData.buildData never overwrites from the ROM (GachaMonMoves.powerText).
     */
    val power: String,
    /** The ROM's accuracy; 0 for a move that never misses, as tonumber("---") reads in the reference. */
    val accuracy: Int,
    val category: MoveCategory,
)

/** A Pokemon's six stats, or six base stats, in the reference's names. */
data class SixStats(val hp: Int, val atk: Int, val def: Int, val spa: Int, val spd: Int, val spe: Int)

/** Everything calculateRatingScore and calculateBattlePower read about one Pokemon. */
data class RatingInput(
    val abilityId: Int,
    /** The species' types in this game (PokemonData's, read from the ROM): one entry for a single type. */
    val types: List<Int>,
    /** The species' base stats in this game (the ROM's). */
    val baseStats: SixStats,
    /** PokemonData's static base stat total (bst.tsv), which the banned-ability exceptions compare. */
    val listedBst: Int,
    /** PokemonData's evolution is not NONE. */
    val evolves: Boolean,
    /** CustomCode.RomHacks.isPlayingNatDex(). */
    val natDex: Boolean,
    /** The moves it has, blanks left out, in slot order (convertPokemonToGachaMon's Temp.MoveIds). */
    val moves: List<MoveFacts>,
    /** Its actual stats, max HP first. */
    val stats: SixStats,
    /** 0-24, personality % 25. */
    val nature: Int,
)

/** One ruleset's changes (GachaMonRatingSystem.json Rulesets). */
data class GachaMonRuleset(
    val bannedAbilities: Set<Int>,
    val exceptions: List<BanException>,
    val bannedMoves: Set<Int>,
    val adjustedMoves: Set<Int>,
) {
    data class BanException(val bstLessThan: Double, val mustEvo: Boolean, val natDexOnly: Boolean)

    companion object {
        val NONE = GachaMonRuleset(emptySet(), emptyList(), emptySet(), emptySet())
    }
}

/**
 * GachaMonData.RatingsSystem, as GachaMonFileManager.importRatingSystem builds it from the JSON, and the three
 * calculations that read it.
 */
class GachaMonRatingSystem(
    val abilities: Map<Int, Double>,
    val moves: Map<Int, Double>,
    val beneficialNatures: Map<Int, Double>,
    val detrimentalNatures: Map<Int, Double>,
    /** "Offensive", "Defensive", "Speed": (BaseStat, Rating) in the file's order, the order the loops walk. */
    val stats: Map<String, List<Pair<Double, Double?>>>,
    val categoryMaximums: Map<String, Double>,
    val adjustments: Map<String, Double>,
    /** The current version's rating-to-stars thresholds, highest first (RatingToStars). */
    val ratingToStars: List<Pair<Int, Int>>,
    val rulesets: Map<String, GachaMonRuleset>,
) {
    private fun adj(key: String): Double? = adjustments[key]
    private fun max(key: String): Double = categoryMaximums[key] ?: 999.0

    /**
     * The ruleset the ratings use for [rulesetKey], a Constants.IronmonRulesetNames KEY ("Kaizo", "SuperKaizo"), or
     * Standard. The JSON names Survival Revival with a space and the key has none, so on PC that ruleset rates as
     * Standard; kept, so a card rates as it does there.
     */
    fun ruleset(rulesetKey: String?): GachaMonRuleset = rulesets[rulesetKey ?: ""] ?: rulesets["Standard"] ?: GachaMonRuleset.NONE

    /** GachaMonData.calculateRatingScore: 0 to 255, rounded half up. */
    fun ratingScore(c: RatingInput, rulesetKey: String?): Int {
        var ratingTotal = 0.0
        val types = c.types
        val rules = ruleset(rulesetKey)

        // ABILITY
        val abilityId = c.abilityId
        var abilityRating = abilities[abilityId] ?: 0.0
        // Remove rating if banned ability, unless it qualifies for an exception
        if (abilityId in rules.bannedAbilities) {
            var exception = false
            for (bae in rules.exceptions) {
                val bstOkay = c.listedBst < bae.bstLessThan
                val evoOkay = !bae.mustEvo || c.evolves
                // "not isPlayingNatDex() or bae.NatDexOnly": off Nat. Dex every exception counts, the Nat. Dex one too.
                val natdexOkay = !c.natDex || bae.natDexOnly
                if (bstOkay && evoOkay && natdexOkay) { exception = true; break }
            }
            if (!exception) abilityRating = 0.0
        }
        // Check if the ability helps improves the Pokemon's weakness(es): a x2 or x4 weakness it covers
        val defensiveTypings = GachaMonMoves.TYPE_DEFENSIVE_ABILITIES[abilityId]
        var hasDefensiveAbility = false
        if (abilityRating > 0 && defensiveTypings != null && types.isNotEmpty()) {
            val t1 = types[0]
            val t2 = types.getOrElse(1) { t1 }
            for (weakTo in listOf(2.0, 4.0)) {
                if (hasDefensiveAbility) break
                hasDefensiveAbility = defensiveTypings.any { atk -> Gen3Types.effect(atk, t1, t2, c.natDex) == weakTo }
            }
        }
        if (hasDefensiveAbility) abilityRating *= (adj("BonusAbilityImprovesWeakness") ?: 1.0)
        // Check specific abilities generic to all rulesets
        if (abilityId == GachaMonMoves.SAND_STREAM) {
            abilityRating += if (types.any { it == GROUND || it == ROCK || it == STEEL }) (adj("BonusAbilitySandStreamSafe") ?: 0.0)
                else (adj("PenaltyAbilitySandStreamUnsafe") ?: 0.0)
        }
        // Remove the points an ability gains for protecting a type that is already protected
        if (abilityId == GachaMonMoves.IMMUNITY && types.any { it == POISON || it == STEEL }) abilityRating -= (abilities[abilityId] ?: 0.0)
        if (abilityId == GachaMonMoves.WATER_VEIL && types.any { it == FIRE }) abilityRating -= (abilities[abilityId] ?: 0.0)
        if (abilityId == GachaMonMoves.MAGMA_ARMOR && types.any { it == ICE }) abilityRating -= (abilities[abilityId] ?: 0.0)
        if (abilityId == GachaMonMoves.LEVITATE && types.any { it == FLYING }) abilityRating -= (abilities[abilityId] ?: 0.0)
        abilityRating = min(abilityRating, max("Ability"))
        ratingTotal += abilityRating

        val badWeatherTypes: Map<Int, Double?> = when (abilityId) {
            GachaMonMoves.DRIZZLE -> mapOf(FIRE to adj("PenaltyWeatherAbilityWeakensMove"))
            GachaMonMoves.DROUGHT -> mapOf(WATER to adj("PenaltyWeatherAbilityWeakensMove"))
            else -> emptyMap()
        }
        val compoundeyesBonus = if (abilityId == GachaMonMoves.COMPOUNDEYES) adj("BonusAbilityCompoundeyesHelpsMove") else null
        val rockheadBonus = if (abilityId == GachaMonMoves.ROCK_HEAD) adj("BonusAbilityRockHeadHelpsMove") else null
        val hustleBonus = if (abilityId == GachaMonMoves.HUSTLE) adj("BonusAbilityHustleHelpsNoMissMove") else null
        // Snow Warning's branch never runs on PC: AbilityData.Values has no SnowWarningId, in vanilla or Nat. Dex.
        val (weatherBallBonus, weatherBallStabType) = when (abilityId) {
            GachaMonMoves.DRIZZLE -> adj("BonusMoveWeatherBallWithAbility") to WATER
            GachaMonMoves.DROUGHT -> adj("BonusMoveWeatherBallWithAbility") to FIRE
            GachaMonMoves.SAND_STREAM -> adj("BonusMoveWeatherBallWithAbility") to ROCK
            else -> null to null
        }

        // MOVES
        var anyPhysical = false
        var anySpecial = false
        class IMove(val m: MoveFacts, val ePower: Int, var rating: Double)
        val iMoves = c.moves.map { m -> IMove(m, GachaMonMoves.expectedPower(m), moves[m.id] ?: 0.0) }
        val stabBonus = adj("BonusMoveIsSTAB") ?: 1.0
        for (im in iMoves) {
            val id = im.m.id
            // Remove rating if banned move; "adjusted moves" for now means to reduce rating by 50%
            if (id in rules.bannedMoves) im.rating = 0.0
            else if (id in rules.adjustedMoves) im.rating = im.rating * 0.5
            if (im.rating != 0.0) {
                if (im.ePower > 0) {
                    if (!anyPhysical && im.m.category == MoveCategory.PHYSICAL) anyPhysical = true
                    if (!anySpecial && im.m.category == MoveCategory.SPECIAL) anySpecial = true
                    if (badWeatherTypes.containsKey(im.m.type)) {
                        badWeatherTypes[im.m.type]?.let { im.rating = im.rating * it }
                    }
                }
                if (compoundeyesBonus != null && !GachaMonMoves.isOhko(id)) {
                    val acc = im.m.accuracy
                    if (acc > 0 && acc < 100) im.rating = im.rating * compoundeyesBonus
                }
                if (rockheadBonus != null && GachaMonMoves.isRecoil(id)) im.rating = im.rating * rockheadBonus
                if (hustleBonus != null && GachaMonMoves.isNoMissDamaging(id) && im.m.category == MoveCategory.PHYSICAL) im.rating = im.rating * hustleBonus
                if (weatherBallBonus != null && id == GachaMonMoves.WEATHER_BALL) im.rating = im.rating * weatherBallBonus
                if (GachaMonMoves.isStab(im.m, types)) {
                    im.rating = im.rating * stabBonus
                } else if (weatherBallStabType != null) {
                    // As on PC: the weather's type against the Pokemon's types, for any move, not only Weather Ball.
                    for (t in types) if (weatherBallStabType == t) { im.rating = im.rating * stabBonus; break }
                }
            }
        }
        var movesRating = 0.0
        val penaltyRepeatedMove = adj("PenaltyRepeatedMove") ?: 1.0
        // Redundant typing: the lower-rated of two damaging moves of one type is penalized, ratings changing as it goes.
        for (im in iMoves) {
            for (cm in iMoves) {
                if (im.rating < cm.rating && cm.m.type == im.m.type && cm.m.id != im.m.id && cm.ePower > 0 && im.ePower > 0) {
                    im.rating = im.rating * penaltyRepeatedMove
                    break
                }
            }
            movesRating += im.rating
        }
        movesRating = min(movesRating, max("Moves"))
        ratingTotal += movesRating

        // STATS (OFFENSIVE)
        val base = c.baseStats
        val checkPoorOffenseMin = adj("CheckPoorOffenseMin") ?: 1.0
        val penaltyNoMoveInCategory = adj("PenaltyNoMoveInCategory") ?: 1.0
        var offensiveAtk = base.atk.toDouble()
        var offensiveSpa = base.spa.toDouble()
        var offensiveRating = 0.0
        if (offensiveAtk < checkPoorOffenseMin && offensiveSpa < checkPoorOffenseMin) {
            offensiveRating += (adj("PenaltyPoorOffense") ?: 0.0)
        } else {
            for ((baseStat, rating) in stats["Offensive"].orEmpty()) {
                if (rating == null) continue
                if (offensiveAtk >= baseStat) {
                    val movePenalty = if (!anyPhysical) penaltyNoMoveInCategory else 1.0
                    offensiveRating += (rating * movePenalty)
                    offensiveAtk = 0.0
                }
                if (offensiveSpa >= baseStat) {
                    val movePenalty = if (!anySpecial) penaltyNoMoveInCategory else 1.0
                    offensiveRating += (rating * movePenalty)
                    offensiveSpa = 0.0
                }
            }
        }
        offensiveRating = min(offensiveRating, max("OffensiveStats"))
        ratingTotal += offensiveRating

        // STATS (DEFENSIVE)
        val checkPoorDefenseMin = adj("CheckPoorDefenseMin") ?: 1.0
        val penaltyPoorDefense = adj("PenaltyPoorDefense") ?: 0.0
        val hp = base.hp.toDouble().let { if (it < checkPoorDefenseMin) penaltyPoorDefense else it }
        val def = base.def.toDouble().let { if (it < checkPoorDefenseMin) penaltyPoorDefense else it }
        val spd = base.spd.toDouble().let { if (it < checkPoorDefenseMin) penaltyPoorDefense else it }
        val defensiveStats = hp + def + spd
        var defensiveRating = 0.0
        for ((baseStat, rating) in stats["Defensive"].orEmpty()) {
            if (rating != null && defensiveStats >= baseStat) { defensiveRating = rating; break }
        }
        defensiveRating = min(defensiveRating, max("DefensiveStats"))
        ratingTotal += defensiveRating

        // STATS (SPEED)
        val speedStat = base.spe.toDouble()
        var speedRating = 0.0
        for ((baseStat, rating) in stats["Speed"].orEmpty()) {
            if (rating != null && speedStat >= baseStat) { speedRating = rating; break }
        }
        speedRating = min(speedRating, max("SpeedStats"))
        ratingTotal += speedRating

        // NATURE: for now, only the best offensive stat counts
        val multiplier = natureMultiplier(bestOffensiveStat(c.stats), c.nature)
        var natureRating = 0.0
        if (multiplier > 1) natureRating = beneficialNatures[c.nature] ?: 0.0
        else if (multiplier < 1) natureRating = detrimentalNatures[c.nature] ?: 0.0
        natureRating = min(natureRating, max("Nature"))
        ratingTotal += natureRating

        val rounded = floor(ratingTotal + 0.5)
        return when {
            rounded > MAX_RATING -> MAX_RATING
            rounded < MIN_RATING -> MIN_RATING
            else -> rounded.toInt()
        }
    }

    /** GachaMonData.calculateStars: 0 to 6 (6 is the "5+" card) from a card's rating and the thresholds of its [version]. */
    fun stars(ratingScore: Int, version: Int): Int {
        if (ratingScore <= 0) return 0
        val table = if (version == GachaMonCodec.CURRENT_VERSION) ratingToStars
            else LEGACY_STAR_RATINGS[version] ?: ratingToStars
        for ((rating, stars) in table) if (ratingScore >= rating) return stars
        return 0
    }

    /** GachaMonData.calculateBattlePower: stars, the moves' expected power and a same-type move, the nature; 1000 to 15000. */
    fun battlePower(c: RatingInput, stars: Int): Int {
        var power = 0.0
        power += stars * 1000
        var totalMovePower = 0
        var hasStab = false
        for (m in c.moves) {
            totalMovePower += GachaMonMoves.expectedPower(m)
            if (!hasStab && GachaMonMoves.isStab(m, c.types)) hasStab = true
        }
        power += floor(totalMovePower / 150.0) * 1000
        if (hasStab) power += 1000
        val multiplier = natureMultiplier(bestOffensiveStat(c.stats), c.nature)
        power += floor(multiplier * 10 - 10) * 1000
        return when {
            power > MAX_BATTLE_POWER -> MAX_BATTLE_POWER
            power < MIN_BATTLE_POWER -> MIN_BATTLE_POWER
            else -> floor(power).toInt()
        }
    }

    companion object {
        const val MIN_RATING = 0
        const val MAX_RATING = 255
        const val MIN_BATTLE_POWER = 1000
        const val MAX_BATTLE_POWER = 15000

        private const val FLYING = 2
        private const val POISON = 3
        private const val GROUND = 4
        private const val ROCK = 5
        private const val STEEL = 8
        private const val FIRE = 10
        private const val WATER = 11
        private const val ICE = 15

        /**
         * GachaMonFileManager.LegacyStarRatings: every version's thresholds. The current version reads the JSON's
         * RatingToStars, which holds version 2's (77 for the 5+ card, 80 in version 1).
         */
        val LEGACY_STAR_RATINGS: Map<Int, List<Pair<Int, Int>>> = mapOf(
            1 to listOf(80 to 6, 67 to 5, 54 to 4, 40 to 3, 25 to 2, 0 to 1),
            2 to listOf(77 to 6, 67 to 5, 54 to 4, 40 to 3, 25 to 2, 0 to 1),
        )

        /** "atk" when Attack is the higher, else "spa" (a tie is Sp. Atk), as both calculations pick the stat. */
        fun bestOffensiveStat(stats: SixStats): String = if (stats.atk > stats.spa) "atk" else "spa"

        /** Utils.getNatureMultiplier: 1.1, 0.9 or 1 for [stat] ("atk", "def", "spe", "spa", "spd") under [nature]. */
        fun natureMultiplier(stat: String, nature: Int): Double {
            if (nature % 6 == 0) return 1.0
            when (stat) {
                "atk" -> { if (nature < 5) return 1.1; if (nature % 5 == 0) return 0.9 }
                "def" -> { if (nature in 5..9) return 1.1; if (nature % 5 == 1) return 0.9 }
                "spe" -> { if (nature in 10..14) return 1.1; if (nature % 5 == 2) return 0.9 }
                "spa" -> { if (nature in 15..19) return 1.1; if (nature % 5 == 3) return 0.9 }
                "spd" -> { if (nature > 19) return 1.1; if (nature % 5 == 4) return 0.9 }
            }
            return 1.0
        }

        /** The reference's own file, from the resources: loaded once. */
        val default: GachaMonRatingSystem by lazy {
            val text = GachaMonRatingSystem::class.java.getResourceAsStream("/gachamon/GachaMonRatingSystem.json")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("gachamon/GachaMonRatingSystem.json is missing from the resources")
            parse(text)
        }

        /** GachaMonFileManager.importRatingSystem, from the JSON's text. */
        @Suppress("UNCHECKED_CAST")
        fun parse(json: String): GachaMonRatingSystem {
            val d = MiniJson.parse(json) as Map<String, Any?>
            fun idMap(key: String): Map<Int, Double> =
                (d[key] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> k.trim().toIntOrNull()?.let { id -> (v as? Number)?.let { id to it.toDouble() } } }.toMap()
            val natures = d["Natures"] as? Map<String, Any?> ?: emptyMap()
            fun natureGroup(vararg keys: String): Map<Int, Double> {
                val out = LinkedHashMap<Int, Double>()
                for (k in keys) {
                    val g = natures[k] as? Map<String, Any?> ?: continue
                    val points = (g["Points"] as? Number)?.toDouble() ?: continue
                    (g["NatureIds"] as? List<Any?>).orEmpty().forEach { n -> (n as? Number)?.toInt()?.let { out[it] = points } }
                }
                return out
            }
            val stats = (d["Stats"] as? Map<String, Any?>).orEmpty().mapValues { (_, list) ->
                (list as? List<Any?>).orEmpty().mapNotNull { p ->
                    val pair = p as? Map<String, Any?> ?: return@mapNotNull null
                    // (ratingPair.BaseStat or 1)
                    ((pair["BaseStat"] as? Number)?.toDouble() ?: 1.0) to (pair["Rating"] as? Number)?.toDouble()
                }
            }
            fun doubles(key: String): Map<String, Double> =
                (d[key] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toDouble() } }.toMap()
            val ratingToStars = (d["RatingToStars"] as? List<Any?>).orEmpty().mapNotNull { p ->
                val pair = p as? Map<String, Any?> ?: return@mapNotNull null
                val stars = (pair["Stars"] as? Number)?.toInt() ?: return@mapNotNull null
                ((pair["Rating"] as? Number)?.toInt() ?: 1) to stars
            }
            fun ints(v: Any?): Set<Int> = (v as? List<Any?>).orEmpty().mapNotNull { (it as? Number)?.toInt() }.toSet()
            val rulesets = (d["Rulesets"] as? Map<String, Any?>).orEmpty().mapValues { (_, r) ->
                val m = r as? Map<String, Any?> ?: emptyMap()
                GachaMonRuleset(
                    bannedAbilities = ints(m["BannedAbilities"]),
                    exceptions = (m["BannedAbilityExceptions"] as? List<Any?>).orEmpty().mapNotNull { e ->
                        val x = e as? Map<String, Any?> ?: return@mapNotNull null
                        GachaMonRuleset.BanException(
                            bstLessThan = (x["BSTLessThan"] as? Number)?.toDouble() ?: 0.0,
                            mustEvo = x["MustEvo"] == true,
                            natDexOnly = x["NatDexOnly"] == true,
                        )
                    },
                    bannedMoves = ints(m["BannedMoves"]),
                    adjustedMoves = ints(m["AdjustedMoves"]),
                )
            }
            require(d["Abilities"] != null && d["Moves"] != null && d["Stats"] != null && d["RatingToStars"] != null) {
                "not a GachaMon rating system"
            }
            return GachaMonRatingSystem(
                abilities = idMap("Abilities"),
                moves = idMap("Moves"),
                beneficialNatures = natureGroup("BeneficialOffensive", "BeneficialDefensive", "BeneficialSpeed"),
                detrimentalNatures = natureGroup("DetrimentalOffensive", "DetrimentalDefensive", "DetrimentalSpeed"),
                stats = stats,
                categoryMaximums = doubles("CategoryMaximums"),
                adjustments = doubles("OtherAdjustments"),
                ratingToStars = ratingToStars,
                rulesets = rulesets,
            )
        }
    }
}

/**
 * The smallest JSON reader that reads the rating file: objects (insertion order kept), arrays, strings, numbers,
 * true, false and null. org.json is not on a plain JVM's classpath, which the tracker's tests run on.
 */
internal object MiniJson {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.ws()
        require(p.i == text.length) { "unexpected text at ${p.i}" }
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun value(): Any? {
            ws()
            require(i < s.length) { "unexpected end" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("unexpected '$c' at $i")
            }
        }

        private fun word(w: String, v: Any?): Any? {
            require(s.startsWith(w, i)) { "unexpected text at $i" }
            i += w.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (s[i] == '}') { i++; return out }
            while (true) {
                ws()
                val k = str()
                ws(); require(s[i] == ':') { "':' expected at $i" }; i++
                out[k] = value()
                ws()
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return out }
                    else -> error("',' or '}' expected at $i")
                }
            }
        }

        private fun arr(): List<Any?> {
            val out = ArrayList<Any?>()
            i++
            ws()
            if (s[i] == ']') { i++; return out }
            while (true) {
                out += value()
                ws()
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return out }
                    else -> error("',' or ']' expected at $i")
                }
            }
        }

        private fun str(): String {
            require(s[i] == '"') { "string expected at $i" }
            i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }
    }
}
