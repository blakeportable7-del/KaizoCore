package com.ironmonone.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ironmonone.tracker.nuzlocke.LevelCapTable
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem

/**
 * Banned moves, per game and per mode, marked on your own Pokemon's moves with the BST rule's X (Blake, 2026-10-02:
 * "just like the x on bst of 600+ need to highlight banned moves per game and per mode and notate that on the tracker
 * with the X"). The tracker states the rule and nothing more (the IronMON dev team, 2026-09-30): the X sits on a move
 * while this run's rules forbid it in the battle you are in, and the move's card says why.
 *
 * Sources, all in C:/Users/bepor/ironmon-ref, read 2026-10-02: the banned-moves list every ruleset links
 * (modes-scratch/gists/psydetrack-banned-moves.md), the rules gist (modes-scratch/gists/IronMon-Rules.current.md), each
 * mode's own changes in the rulesets the app ships (assets/rulesets), Chaos Kaizo's own list (modes-scratch/gists/chaos-
 * Chaos Kaizo Ironmon Rules.md) and the Nat. Dex ruleset page (NatDexExtension.wiki/Nat.-Dex-Ruleset-Changes.md).
 *
 * - Where the list and a ruleset's own words differ, the ruleset wins: Survival allows Leech Seed in trainer battles
 *   ("Leech Seed/Giga Drain/Aromatherapy etc.") and keeps Kaizo's Spore ban, though the list bans the one and leaves out
 *   the other.
 * - The HM moves are each game's own HMs, the list's reason ("HM Move"); its generation column is looser than that
 *   (Rock Smash is a TM in Gold, Silver and Crystal). Nat. Dex allows an HM move in battle unless the HM item taught it,
 *   which the tracker cannot see, so there the card says the rule and the row has no X.
 * - Moves the Nat. Dex build has past the list's Gen VII are banned by the rule's own words ("Healing Moves of any kind",
 *   the switching moves' reason). Teleport is banned only from Gen VII, where it switches; in these games it ends a wild
 *   battle. Pollen Puff is banned only when aimed at an ally, which a mark on the move cannot say, so it gets none.
 */
object MoveRule {
    /** When a ban holds. */
    enum class When {
        ALWAYS,
        /** Against wild Pokemon only: trainer battles allow it. */
        WILD,
        /** Against gym leaders, the Elite Four and the champion (and the game's other final trainers). */
        BOSS,
        /** Against the Elite Four and the champion. */
        ELITE,
        /** In trainer battles, which a Kaizo Doubles run fights two on two. */
        TRAINER,
        /** In a case the tracker cannot see (Nat. Dex: an HM move the HM item taught): the card says it, no X. */
        UNSEEN,
    }

    /** Why a move is banned and when. [overAccuracy]: only while its accuracy is above that (Chaos Kaizo). */
    data class Ban(val reason: String, val whenBanned: When = When.ALWAYS, val overAccuracy: Int? = null) {
        /** The move card's sentence. */
        val line: String get() = when (whenBanned) {
            When.ALWAYS -> if (overAccuracy != null) "Banned in this run while its accuracy is over $overAccuracy: $reason."
                else "Banned in this run: $reason."
            When.WILD -> "Banned against wild Pokemon in this run: $reason. Trainer battles allow it."
            When.BOSS -> "Banned against gym leaders, the Elite Four and the champion in this run: $reason."
            When.ELITE -> "Banned against the Elite Four and the champion in this run: $reason."
            When.TRAINER -> "Banned in this run's double battles: $reason."
            When.UNSEEN -> "Banned in battle in this run when $reason."
        }
    }

    /** The battle the tracker reads now. [boss]: a gym leader, the Elite Four or a final trainer; [elite]: the Elite Four or the champion. */
    data class Battle(val inBattle: Boolean = false, val wild: Boolean = false, val boss: Boolean = false, val elite: Boolean = false)

    /** Whether the X shows in [battle]: a conditional ban only in the battle it names. */
    fun shows(ban: Ban, battle: Battle): Boolean = when (ban.whenBanned) {
        When.ALWAYS -> true
        When.WILD -> battle.inBattle && battle.wild
        When.BOSS -> battle.inBattle && !battle.wild && battle.boss
        When.ELITE -> battle.inBattle && !battle.wild && battle.elite
        When.TRAINER -> battle.inBattle && !battle.wild
        When.UNSEEN -> false
    }

    /** A move's name as every game spells it: "SOFTBOILED", "Softboiled" and "Soft-Boiled" are one move. */
    fun key(name: String): String = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    // ---- The lists ----

    private val HMS = mapOf(
        "RBY" to listOf("Cut", "Fly", "Surf", "Strength", "Flash"),
        "GSC" to listOf("Cut", "Fly", "Surf", "Strength", "Flash", "Whirlpool", "Waterfall"),
        "RSE" to listOf("Cut", "Fly", "Surf", "Strength", "Flash", "Rock Smash", "Waterfall", "Dive"),
        "FRLG" to listOf("Cut", "Fly", "Surf", "Strength", "Flash", "Rock Smash", "Waterfall"),
        "DPPt" to listOf("Cut", "Fly", "Surf", "Strength", "Defog", "Rock Smash", "Waterfall", "Rock Climb"),
        "HGSS" to listOf("Cut", "Fly", "Surf", "Strength", "Whirlpool", "Rock Smash", "Waterfall", "Rock Climb"),
        "BW" to listOf("Cut", "Fly", "Surf", "Strength", "Waterfall", "Dive"),
        "B2W2" to listOf("Cut", "Fly", "Surf", "Strength", "Waterfall", "Dive"),
        // Heart & Soul's own eight (HM01-HM08 in its TM table, as the run's log lists them).
        "HnS" to listOf("Cut", "Fly", "Surf", "Strength", "Flash", "Rock Smash", "Waterfall", "Whirlpool"),
    )

    internal val HP_HEALING = listOf(
        "Recover", "Soft-Boiled", "Rest", "Milk Drink", "Pain Split", "Morning Sun", "Synthesis", "Moonlight", "Swallow",
        "Wish", "Ingrain", "Slack Off", "Roost", "Aqua Ring", "Heal Order", "Heal Pulse", "Grassy Terrain",
        "Floral Healing", "Purify", "Shore Up",
        // The Nat. Dex build's healing moves past the list.
        "Strength Sap", "Life Dew", "Jungle Healing", "Lunar Blessing",
    )
    internal val DRAINING = listOf(
        "Absorb", "Mega Drain", "Leech Life", "Dream Eater", "Giga Drain", "Drain Punch", "Horn Leech", "Parabolic Charge",
        "Draining Kiss", "Oblivion Wing", "Bitter Blade", "Matcha Gotcha",
        // Let's Go moves the Nat. Dex build has, which drain as they hit (MaxDex's own rule page named them, 2026-06-19
        // to 07-01, before it settled on "Same Rules as NatDex").
        "Bouncy Bubble", "Sappy Seed",
    )
    internal val STATUS_HEALING = listOf("Heal Bell", "Refresh", "Aromatherapy", "Psycho Shift", "Take Heart", "Sparkly Swirl")
    internal val SWITCHING = listOf("Baton Pass", "U-turn", "Volt Switch", "Parting Shot", "Flip Turn", "Shed Tail", "Chilly Reception")

    /** The list's setup moves, banned against bosses in Super Kaizo and the modes that copy its rule. */
    internal val SETUP = listOf(
        "Swords Dance", "Growth", "Meditate", "Double Team", "Harden", "Minimize", "Withdraw", "Defense Curl", "Barrier",
        "Focus Energy", "Amnesia", "Acid Armor", "Sharpen", "Curse", "Belly Drum", "Stockpile", "Charge", "Tail Glow",
        "Cosmic Power", "Iron Defense", "Howl", "Bulk Up", "Calm Mind", "Dragon Dance", "Acupressure", "Rock Polish",
        "Nasty Plot", "Defend Order", "Hone Claws", "Autotomize", "Quiver Dance", "Coil", "Shell Smash", "Shift Gear",
        "Work Up", "Cotton Guard", "Rototiller", "Aromatic Mist", "Geomancy", "Magnetic Flux", "Gear Up",
    )

    /** Chaos Kaizo's own list (its rules say anything not on it is allowed, HM moves included). */
    internal val CHAOS = listOf(
        "Recover", "Milk Drink", "Soft-Boiled", "Slack Off", "Morning Sun", "Synthesis", "Moonlight", "Rest", "Wish",
        "Assist", "Aromatherapy", "Heal Bell", "Refresh",
    )
    /** Chaos Kaizo's sleep and one-hit KO moves, legal at an accuracy of 90 or less. */
    internal val CHAOS_BY_ACCURACY = listOf(
        "Sing", "Sleep Powder", "Hypnosis", "Lovely Kiss", "Spore", "Grass Whistle", "Guillotine", "Horn Drill", "Fissure",
        "Sheer Cold",
    )

    /** Super Kaizo: "No Guard is okay but you can't use any OHKO move or Sleep moves that have accuracy checks". */
    internal val OHKO = listOf("Guillotine", "Horn Drill", "Fissure", "Sheer Cold")
    internal val SLEEP_WITH_ACCURACY = listOf("Sing", "Sleep Powder", "Hypnosis", "Lovely Kiss", "Grass Whistle", "Spore", "Dark Void")

    /** Red, Blue and Yellow, Kaizo and harder (RBY/kaizo.md, RBY/survival.md). */
    internal val RBY_TRAPPING = listOf("Wrap", "Bind", "Fire Spin", "Clamp")

    /** Every name the rules use, for the test that each one is a move some game has. */
    internal val ALL_NAMES: List<String> get() = (HMS.values.flatten() + HP_HEALING + DRAINING + STATUS_HEALING + SWITCHING +
        SETUP + CHAOS + CHAOS_BY_ACCURACY + OHKO + SLEEP_WITH_ACCURACY + RBY_TRAPPING + listOf("Leech Seed", "Spore", "Assist", "Recycle", "Dark Void")).distinct()

    private val OHKO_KEYS = OHKO.map(::key).toSet()
    private val SLEEP_KEYS = SLEEP_WITH_ACCURACY.map(::key).toSet()
    private val TRAP_KEYS = RBY_TRAPPING.map(::key).toSet()

    /** One run's rules: the bans, the boss list the conditional ones read, and the mode's exception for the card. */
    class Rules internal constructor(
        val mode: String,
        private val bans: Map<String, Ban>,
        /** Super Kaizo: No Guard bans the one-hit KO moves and the sleep moves that check accuracy. */
        private val noGuardBan: Boolean,
        /** Kaizo Doubles: a move both your Pokemon know is banned for both. */
        private val sharedBan: Boolean,
        /** Red, Blue and Yellow Survival: the trapping moves are legal for 351 BST or less while it can still evolve. */
        private val trapExemption: Boolean,
        /** The mode's exception, added to the card's line. */
        val note: String?,
        /** Trainer id to its kind in the game's boss table (gym, e4, champion, post), for the boss-only bans. */
        private val bosses: Map<Int, String>,
    ) {
        /** The ban on your Pokemon's [name], or null. [shared]: the other of your two Pokemon knows it (Kaizo Doubles). */
        fun banOf(name: String, accuracy: Int? = null, ability: String? = null, shared: Boolean = false,
                  bst: Int? = null, canEvolve: Boolean = false): Ban? {
            val k = key(name)
            if (k.isEmpty()) return null
            bans[k]?.let { b ->
                if (trapExemption && k in TRAP_KEYS && bst != null && bst in 1..351 && canEvolve) return null
                if (b.overAccuracy != null) {
                    // 0 is "never misses", the highest accuracy there is; an unknown accuracy is not marked.
                    val acc = accuracy ?: return null
                    if (acc != 0 && acc <= b.overAccuracy) return null
                }
                return b
            }
            if (noGuardBan && key(ability ?: "") == "noguard") {
                if (k in OHKO_KEYS) return Ban("a one-hit KO move with No Guard")
                if (k in SLEEP_KEYS) return Ban("a sleep move with No Guard")
            }
            if (sharedBan && shared) return Ban("both your Pokemon know it")
            return null
        }

        /** The battle the tracker reads, its opponent sorted by this game's boss table. */
        fun battle(inBattle: Boolean, wild: Boolean, trainerId: Int?): Battle {
            val kind = if (inBattle && !wild) trainerId?.let { bosses[it] } else null
            return Battle(inBattle, wild, boss = kind in BOSS_KINDS, elite = kind == "e4" || kind == "champion")
        }
    }

    /** A gym leader, the Elite Four, the champion, and a fight after the champion (HGSS's Kanto gyms and Red, Emerald's Steven). */
    private val BOSS_KINDS = setOf("gym", "e4", "champion", "post")

    /**
     * The rules for a run of [mode] ("kaizo", "superkaizo", ...) on [family] ("FRLG", "DPPt", ...), null where nothing
     * is banned. [kindId] picks the boss table (Emerald's or Ruby and Sapphire's, Platinum's or Diamond and Pearl's).
     */
    fun rules(mode: String?, family: String?, natDex: Boolean, kindId: String? = null): Rules? {
        if (mode == null || family == null) return null
        val hms = HMS[family] ?: return null
        val t = LinkedHashMap<String, Ban>()
        fun put(names: Iterable<String>, reason: String, w: When = When.ALWAYS, overAccuracy: Int? = null) =
            names.forEach { t[key(it)] = Ban(reason, w, overAccuracy) }
        fun hm(except: Set<String> = emptySet()) {
            // Nat. Dex: "HM moves are allowed to be used in battle, as long as the moves are not taught with the HM items."
            if (natDex) put(hms.filter { it !in except }, "the HM item taught it", When.UNSEEN)
            else put(hms.filter { it !in except }, "an HM move")
        }
        // Kaizo's list, with its draining and status healing moves when [drainWild] moves them to wild battles only.
        fun kaizo(drainLegal: Boolean = false, drainWild: Boolean = false, leechSeedWild: Boolean = false, statusWild: Boolean = false) {
            put(HP_HEALING, "a healing move")
            if (!drainLegal) put(DRAINING, "a draining move", if (drainWild) When.WILD else When.ALWAYS)
            put(listOf("Leech Seed"), "a draining move", if (leechSeedWild) When.WILD else When.ALWAYS)
            put(STATUS_HEALING, "a status healing move", if (statusWild) When.WILD else When.ALWAYS)
            put(SWITCHING, "a switching move")
            put(listOf("Spore"), "a sleep move that never misses")
            put(listOf("Assist"), "it uses your other Pokemon's moves")
        }
        var note: String? = KAIZO_NOTE
        var noGuard = false
        var shared = false
        when (mode) {
            "ultimate" -> { hm(); note = null }
            "kaizo", "ironmonjourney" -> { hm(); kaizo() }
            "kaizodoubles" -> {
                hm(); kaizo(); shared = true
                put(listOf("Dark Void"), "Dark Void", When.TRAINER)
            }
            "superkaizo" -> {
                // "Attacking moves with HP draining effects such as Giga Drain are legal; you can't exploit healing off
                // of wild Pokemon. Non-attacking moves such as Pain Split, Leech Seed, etc. are still banned."
                hm(); kaizo(drainWild = true)
                put(SETUP, "a setup move", When.BOSS)
                noGuard = true
            }
            "survival" -> {
                // "Cut is no longer banned"; draining and status-only healing moves in trainer battles only.
                hm(except = setOf("Cut")); kaizo(drainWild = true, leechSeedWild = true, statusWild = true)
            }
            "survivalrevival" -> {
                hm(); kaizo(drainWild = true, leechSeedWild = true, statusWild = true)
                put(listOf("Recycle"), "Recycle", When.WILD)
                put(SETUP, "a move that raises stats without doing damage", When.BOSS)
                note = "All banned moves are allowed while you lab and pivot, until your first battle that is not against your rival."
            }
            "evokaizo" -> {
                // "Draining Healing Moves are Legal"; "Set Up Moves are banned on the Elite 4 + Champion Fights".
                hm(); kaizo(drainLegal = true)
                put(SETUP, "a setup move", When.ELITE)
            }
            "chaoskaizo" -> {
                put(CHAOS, "on Chaos Kaizo's list")
                put(CHAOS_BY_ACCURACY, "a sleep or one-hit KO move", overAccuracy = 90)
                note = "In the first fight, against your lab rival, any banned move is allowed."
            }
            else -> return null
        }
        val kaizoUp = mode != "ultimate" && mode != "chaoskaizo"
        if (family == "RBY" && kaizoUp) put(RBY_TRAPPING, "a trapping move, banned in Red, Blue and Yellow")
        if (family == "BW" && kaizoUp) note = "Banned moves are allowed in the first three battles, N's included."
        return Rules(mode, t, noGuard, shared, trapExemption = family == "RBY" && mode == "survival", note, bossesOf(family, kindId))
    }

    private const val KAIZO_NOTE = "Your starter may use it in the lab fight. Metronome and a Mimic copy are allowed."

    /** The game's boss table, trainer id to kind; empty for the Game Boy games, whose modes have no boss-only bans. */
    private fun bossesOf(family: String, kindId: String?): Map<Int, String> {
        val id = kindId ?: ""
        val key = when (family) {
            "FRLG" -> "frlg"
            "RSE" -> if (id.startsWith("ruby") || id.startsWith("sapphire")) "rs" else "e"
            "DPPt" -> if (id.startsWith("diamond") || id.startsWith("pearl")) "dp" else "pt"
            "HGSS" -> "hgss"
            "BW" -> "bw"
            "B2W2" -> "b2w2"
            // Heart & Soul's gyms, League and Kanto in levelcaps-gen3.tsv, by its own trainer ids.
            com.ironmonone.core.RomKind.HNS_FAMILY -> "hns"
            else -> return emptyMap()
        }
        val system = when (family) { "FRLG", "RSE", com.ironmonone.core.RomKind.HNS_FAMILY -> NuzlockeSystem.GEN3; "BW", "B2W2" -> NuzlockeSystem.GEN5; else -> NuzlockeSystem.GEN4 }
        return runCatching {
            LevelCapTable.standard(key, system).bosses.flatMap { b -> b.trainerIds.map { it to b.kind } }.toMap()
        }.getOrDefault(emptyMap())
    }

    /**
     * [m] with this run's mark: the X while the ban holds in [battle], and the card's line whenever there is a ban.
     * A blank slot is never marked.
     */
    fun mark(m: PcMove, ban: Ban?, battle: Battle, note: String?): PcMove =
        if (ban == null || m.blank) m
        else m.copy(banned = shows(ban, battle), banLine = listOfNotNull(ban.line, note).joinToString(" "))

    /** The move keys both of your two Pokemon know (Kaizo Doubles), from their move names; blanks never match. */
    fun sharedKeys(first: List<String>, second: List<String>): Set<String> {
        fun keys(names: List<String>) = names.map(::key).filter { it.isNotEmpty() && it != "none" }.toSet()
        return keys(first) intersect keys(second)
    }

    /**
     * Kaizo Doubles: the moves [pid] shares with the other of your first two Pokemon ([party]: personality value and
     * move names, eggs left out, as the DS tracker's doubles loss rule counts them). Empty when [pid] is not one of them.
     */
    fun sharedFor(pid: Long, party: List<Pair<Long, List<String>>>): Set<String> {
        val two = party.take(2)
        if (two.size < 2 || two.none { it.first == pid }) return emptySet()
        return sharedKeys(two[0].second, two[1].second)
    }

    /** The GBA and Game Boy panel's mark for your Pokemon [p]'s moves while the tracker reads [state]. */
    internal fun gbaMark(rules: Rules?, p: com.ironmonone.tracker.TrackedMon, state: com.ironmonone.tracker.TrackerState,
                         run: RuleMarks.Run? = null, on: Boolean = TrackerOptions.ruleMarks): (PcMove) -> PcMove {
        if (rules == null || !on) return { it }
        val battle = rules.battle(state.inBattle, state.isWildBattle, state.opponentTrainerId)
        val shared = sharedFor(p.mon.pid, state.party.filter { !it.mon.isEgg }.map { it.mon.pid to it.moveNames })
        val power = RuleMarks.physicalBan(run, p.abilityName, p.base?.bst, canEvolve = run?.canEvolve(p.speciesName, p.evo != null) == true)
        return { mv ->
            // An accuracy the row hides ("Reveal info if randomized") is not read for Chaos Kaizo's rule either.
            mark(mv, rules.banOf(mv.name, mv.acc.takeIf { mv.accText != "?" }, p.abilityName, key(mv.name) in shared,
                p.base?.bst, canEvolve = p.evo != null) ?: physical(mv, power), battle, rules.note)
        }
    }

    /**
     * Kaizo's "Using any physical move is banned while having Huge Power or Pure Power": [reason] from
     * [RuleMarks.physicalBan], on a move the card shows as physical. Special and status moves never.
     */
    internal fun physical(mv: PcMove, reason: String?): Ban? =
        if (reason != null && !mv.blank && mv.category == "PHY") Ban(reason) else null

    /** The DS panel's mark for your Pokemon [p]'s moves while the tracker reads [state]. */
    internal fun ndsMark(rules: Rules?, p: com.ironmonone.tracker.nds.NdsTrackedMon, state: com.ironmonone.tracker.nds.NdsTrackerState,
                         run: RuleMarks.Run? = null, on: Boolean = TrackerOptions.ruleMarks): (PcMove) -> PcMove {
        if (rules == null || !on) return { it }
        val battle = rules.battle(state.inBattle, state.isWildBattle, state.enemyTrainerId.takeIf { it != 0 })
        val shared = sharedFor(p.mon.pid, state.party.filter { !it.mon.isEgg }.map { it.mon.pid to it.moves.map { m -> m.name } })
        val power = RuleMarks.physicalBan(run, p.abilityName, p.info?.bst, run?.canEvolve(p.speciesName, ndsCanEvolve(p.mon)) == true)
        return { mv ->
            mark(mv, rules.banOf(mv.name, mv.acc.takeIf { mv.accText != "?" }, p.abilityName, key(mv.name) in shared,
                p.info?.bst) ?: physical(mv, power), battle, rules.note)
        }
    }

    /** A DS Pokemon that will evolve further: the DS tracker's evolution line for it is not empty (ndsEvoLabel). */
    internal fun ndsCanEvolve(m: com.ironmonone.tracker.nds.Gen4.Mon): Boolean =
        com.ironmonone.tracker.nds.NdsLogData.evoFor(m.species, m.isFemale).isNotEmpty()
}

/** The rules for the game in Play: a Kaizo IronMON run on any console; null otherwise, as for the BST X. */
@Composable
fun moveRulesInPlay(attempt: Int): MoveRule.Rules? {
    val context = LocalContext.current
    return remember(attempt) {
        runCatching {
            val filesDir = context.applicationContext.filesDir
            val store = PrepStore(filesDir)
            val session = store.session()
            val kind = session.kind
            if (PlayRules.kind(session, filesDir) != PlayRules.Kind.IRONMON || kind == null) null
            else MoveRule.rules(FavoriteBall.modeOf(store), kind.family, HnsPool.rulesNatDex(kind, filesDir), kind.baseId ?: kind.id)
        }.getOrNull()
    }
}
