package com.ironmonone.tracker.gachamon

import kotlin.math.floor
import kotlin.random.Random

/**
 * Prize cards: after a game over, the PC tracker's "Prize card" button makes a card from the strongest Pokemon of one
 * of the defeated common trainers (GachaMonData.createPokemonDataFromDefeatedTrainers, data/GachaMonData.lua:686-831),
 * the trainer's id carried in the card's personality value. The common trainers are TrainerData.getCommonTrainers.
 */
object GachaMonPrize {

    /**
     * TrainerData.getCommonTrainers (data/TrainerData.lua:365-449) by the card's game number: rivals, gym leaders, the
     * Elite Four and champions, and a few others. Ordered as the file lists them.
     */
    fun commonTrainers(gameNumber: Int): Map<String, List<Int>> = when (gameNumber) {
        1, 4 -> RS
        2 -> EMERALD
        3, 5 -> FRLG
        else -> emptyMap()
    }

    private val RS: Map<String, List<Int>> = linkedMapOf(
        "Rival 1" to listOf(520, 523, 526, 529, 532, 535),
        "Rival 2" to listOf(521, 524, 527, 530, 533, 536),
        "Rival 3" to listOf(522, 525, 528, 531, 534, 537),
        "Rival 4" to listOf(661, 662, 663, 664, 665, 666),
        "Roxanne" to listOf(265), "Brawly" to listOf(266), "Wattson" to listOf(267), "Flannery" to listOf(268),
        "Norman" to listOf(269), "Winona" to listOf(270), "Tate Liza" to listOf(271), "Tate & Liza" to listOf(271),
        "Wallace" to listOf(272), // 8th gym leader
        "Sidney" to listOf(261), "Phoebe" to listOf(262), "Glacia" to listOf(263), "Drake" to listOf(264),
        "Steven" to listOf(335), // Elite 4 champion
        "Wally 1" to listOf(656), "Wally 2" to listOf(519),
    )

    private val EMERALD: Map<String, List<Int>> = linkedMapOf(
        "Rival 1" to listOf(520, 523, 526, 529, 532, 535),
        "Rival 2" to listOf(521, 524, 527, 530, 533, 536),
        "Rival 3" to listOf(522, 525, 528, 531, 534, 537),
        "Rival 4" to listOf(593, 592, 599, 600, 665, 666),
        "Rival 5" to listOf(661, 662, 663, 664, 768, 769),
        "Roxanne" to listOf(265), "Brawly" to listOf(266), "Wattson" to listOf(267), "Flannery" to listOf(268),
        "Norman" to listOf(269), "Winona" to listOf(270), "Tate Liza" to listOf(271), "Tate & Liza" to listOf(271),
        "Juan" to listOf(272), // 8th gym leader
        "Sidney" to listOf(261), "Phoebe" to listOf(262), "Glacia" to listOf(263), "Drake" to listOf(264),
        "Wallace" to listOf(335), // Elite 4 champion
        "Steven" to listOf(804), // Final trainer
        "Wally 1" to listOf(656), "Wally 2" to listOf(519),
    )

    private val FRLG: Map<String, List<Int>> = linkedMapOf(
        "Rival 1" to listOf(326, 327, 328), "Rival 2" to listOf(329, 330, 331), "Rival 3" to listOf(332, 333, 334),
        "Rival 4" to listOf(426, 427, 428), "Rival 5" to listOf(429, 430, 431), "Rival 6" to listOf(432, 433, 434),
        "Rival 7" to listOf(435, 436, 437),
        "Brock" to listOf(414), "Misty" to listOf(415), "Lt. Surge" to listOf(416), "Erika" to listOf(417),
        "Koga" to listOf(418), "Sabrina" to listOf(420), "Blaine" to listOf(419),
        "Giovanni Hideout" to listOf(348), "Giovanni Silph Co." to listOf(349), "Giovanni Gym" to listOf(350),
        "Dojo" to listOf(317),
        "Lorelei" to listOf(410), "Bruno" to listOf(411), "Agatha" to listOf(412), "Lance" to listOf(413),
        "Champion" to listOf(438, 439, 440),
        "Jimmy" to listOf(102), // Bonus trainer, commonly referred to as "Jimmy"
    )

    /**
     * IGachaMon.getAssociatedTrainerName: for a prize card, the trainer's name from the common trainers of the card's
     * game, its first word unless it is Lt. Surge or Tate & Liza; null for a card that is not a prize. A prize card's
     * personality is a trainer id, under 1000; a real personality value that small is about one in four million.
     */
    fun trainerName(card: GachaMonCard): String? {
        val id = card.personality
        if (id <= 0 || id >= 1000) return null
        val name = commonTrainers(card.gameVersion).entries.firstOrNull { (_, ids) -> id.toInt() in ids }?.key ?: return null
        if ("Tate" in name && "Liza" in name) return "Tate & Liza"
        if ("Surge" in name) return name
        return Regex("[A-Za-z0-9]+").find(name)?.value ?: name
    }

    /** The common trainers of [gameNumber] (1 Ruby/Sapphire, 2 Emerald, 3 FireRed/LeafGreen) that are beaten. */
    fun defeated(gameNumber: Int, isDefeated: (Int) -> Boolean): List<Int> =
        commonTrainers(gameNumber).values.flatten().filter(isDefeated)

    /** One Pokemon of a trainer's party as the ROM holds it (Program.readTrainerGameData). */
    data class TrainerMon(val species: Int, val level: Int, val ivs: Int, val moves: List<Int>)

    /** What the pick reads from the game. */
    interface Source {
        /** The trainer's party, in party order; empty when it cannot be read. */
        fun party(trainerId: Int): List<TrainerMon>
        /** PokemonData's listed base stat total (bst.tsv) for a species; 0 when unknown. */
        fun listedBst(species: Int): Int
        /** The species exists in this game (not PokemonData.BlankPokemon). */
        fun valid(species: Int): Boolean
        /** The species' level-up moves as (level, move), in learning order (PokemonData.readLevelUpMoves). */
        fun learnset(species: Int): List<Pair<Int, Int>>
        /** The species' base stats in this game. */
        fun baseStats(species: Int): SixStats?
        /** The species' first ability: trainers always use it in IronMON (abilityNum 0). */
        fun firstAbility(species: Int): Int
    }

    /** The Pokemon a prize card is made from, and the trainer it came from. */
    data class Pick(
        val trainerId: Int,
        val species: Int,
        val level: Int,
        val moves: List<Int>,
        val stats: SixStats,
        val nature: Int,
        val abilityId: Int,
    )

    /**
     * createPokemonDataFromDefeatedTrainers: two different random defeated trainers (when there are two), the one whose
     * strongest Pokemon has the higher listed base stat total, that Pokemon at its level with its moves (or the last
     * four it learned by that level), its stats estimated from its IVs, a random neutral nature. Null with fewer than two
     * beaten (the first rival fight does not count) or when the Pokemon cannot be read.
     */
    fun pick(defeated: List<Int>, source: Source, random: Random = Random.Default): Pick? {
        if (defeated.size < 2) return null
        // Sort their party by BST then level (easier to obtain legendaries/mythical this way)
        fun randomTrainer(): Pair<Int, List<TrainerMon>> {
            val id = defeated[random.nextInt(defeated.size)]
            val party = source.party(id).sortedWith { a, b ->
                val bstA = source.listedBst(a.species); val bstB = source.listedBst(b.species)
                when {
                    bstA != bstB -> bstB.compareTo(bstA)
                    else -> b.level.compareTo(a.level)
                }
            }
            return id to party
        }
        var trainer = randomTrainer()
        // Two different random defeated trainers (if able); the strongest Pokemon from either team. At most 69 tries.
        var different: Pair<Int, List<TrainerMon>>? = null
        for (attempt in 1..69) {
            val t = randomTrainer()
            if (t.first != trainer.first) { different = t; break }
        }
        if (different != null) {
            val bst1 = trainer.second.firstOrNull()?.let { source.listedBst(it.species) } ?: 0
            val bst2 = different.second.firstOrNull()?.let { source.listedBst(it.species) } ?: 0
            if (bst2 > bst1) trainer = different
        }
        val mon = trainer.second.firstOrNull() ?: return null
        if (!source.valid(mon.species)) return null
        var moves = mon.moves
        if (moves.size < 4) {
            // Pokemon forget moves in order from first learned to last, so the current moveset is the last four learned
            val learned = source.learnset(mon.species)
            val out = ArrayList<Int>()
            for (j in learned.indices.reversed()) {
                val (level, move) = learned[j]
                if (level <= mon.level) {
                    out.add(0, move)
                    if (out.size >= 4) break
                }
            }
            moves = out
        }
        val base = source.baseStats(mon.species) ?: return null
        fun estimate(baseStat: Int, hp: Boolean): Int {
            val additional = if (hp) 10 + mon.level else 5
            return floor(((mon.ivs + 2 * baseStat) * mon.level / 100.0) + additional + 0.5).toInt()
        }
        return Pick(
            trainerId = trainer.first,
            species = mon.species,
            level = mon.level,
            moves = List(4) { moves.getOrElse(it) { 0 } },
            stats = SixStats(
                hp = estimate(base.hp, true), atk = estimate(base.atk, false), def = estimate(base.def, false),
                spa = estimate(base.spa, false), spd = estimate(base.spd, false), spe = estimate(base.spe, false),
            ),
            // For now, a random neutral nature (the reference cannot read a trainer Pokemon's)
            nature = random.nextInt(0, 5) * 6,
            abilityId = source.firstAbility(mon.species),
        )
    }
}
