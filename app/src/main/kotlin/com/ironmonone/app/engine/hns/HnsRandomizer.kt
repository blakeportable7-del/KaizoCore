package com.ironmonone.app.engine.hns

import com.ironmonone.app.engine.HnsEngine
import com.ironmonone.app.engine.hns.HnsGame.Evo
import com.ironmonone.app.engine.hns.HnsGame.LevelMove
import com.ironmonone.app.engine.hns.HnsGame.Mon
import java.util.Locale
import java.util.Random

/**
 * The UPR randomizer's steps, in Randomizer.randomize's order, ported to Heart & Soul's tables: each one reads the same
 * option and follows the same algorithm as the vendored engine-natdex (AbstractRomHandler and Gen3RomHandler), adapted
 * where the data differs (pokeemerald-expansion learnsets, evolutions with condition lists, trainer parties of
 * TrainerMon, wild headers with four times of day). What could not be done on this data is said in the log's
 * "--KaizoCore Notes--" section and in docs/HNS-KAIZO.md.
 */
class HnsRandomizer(
    private val game: HnsGame,
    private val o: HnsOptions,
    private val seed: Long,
    private val pool: HnsEngine.Pool,
    /** KaizoCore's BST line for the mode (BstRule): a starter or wild Pokemon at this BST or above is not placed. */
    private val bstLine: Int?,
    /**
     * The items of the game the pool models, as [itemKey]s (HnsEngine.poolItemKeys: vanilla Emerald's for VANILLA,
     * Nat. Dex Emerald 1.2.1's for NATDEX), or null for every Heart & Soul item. Every item a roll may land on is one
     * of them; the bad-item rules apply on top. Battle rules stay Heart & Soul's in both (Blake, 2026-10-05).
     */
    private val poolItems: Set<String>? = null,
    /** The source game's UPR "non-bad" items, as [itemKey]s (HnsEngine.poolNonBadKeys), or null for the rules below. */
    private val poolNonBad: Set<String>? = null,
    /**
     * The rule sheet's line for a wild Pokemon of the mode (BstRule.Lines.wild): none is placed at or over it. Most modes
     * draw one line for both; Evo Kaizo's own line is 601 and its wild line Kaizo's, 599 on Vanilla and 600 on Nat. Dex.
     */
    private val wildLine: Int? = bstLine,
    /** The source game's UPR allowed items (Gen3Constants.allowedItems), as [itemKey]s (HnsEngine.poolAllowedKeys), or null. */
    private val poolAllowed: Set<String>? = null,
) {
    companion object {
        /** Gen 3's shortened names, as vanilla Emerald spells them, to Heart & Soul's (both as [itemKey]s). */
        private val GEN3_NAMES = mapOf("PARLYZHEAL" to "PARALYZEHEAL", "XDEFEND" to "XDEFENSE", "XSPECIAL" to "XSPATK", "STICK" to "LEEK")

        /**
         * The forms AbstractRomHandler.randomizeAbilities gives their base's abilities after rolling (Pikachu's caps,
         * Castform, Deoxys, Rotom, the Origin forms, Zen Darmanitan, Aegislash's Blade...), as Heart & Soul's species
         * constants (without SPECIES_), form to base.
         */
        val FORM_ABILITIES_FROM_BASE: List<Pair<String, String>> = buildList {
            for (c in listOf("ORIGINAL", "HOENN", "SINNOH", "UNOVA", "KALOS", "ALOLA", "PARTNER", "WORLD")) add("PIKACHU_$c" to "PIKACHU")
            add("PICHU_SPIKY_EARED" to "PICHU")
            for (c in listOf("SUNNY", "RAINY", "SNOWY")) add("CASTFORM_$c" to "CASTFORM_NORMAL")
            for (c in listOf("ATTACK", "DEFENSE", "SPEED")) add("DEOXYS_$c" to "DEOXYS_NORMAL")
            for (c in listOf("SANDY", "TRASH")) { add("BURMY_$c" to "BURMY_PLANT"); add("WORMADAM_$c" to "WORMADAM_PLANT") }
            add("CHERRIM_SUNSHINE" to "CHERRIM_OVERCAST")
            for (c in listOf("HEAT", "WASH", "FROST", "FAN", "MOW")) add("ROTOM_$c" to "ROTOM")
            add("DIALGA_ORIGIN" to "DIALGA"); add("PALKIA_ORIGIN" to "PALKIA")
            add("DARMANITAN_ZEN" to "DARMANITAN_STANDARD"); add("DARMANITAN_GALAR_ZEN" to "DARMANITAN_GALAR_STANDARD")
            add("MELOETTA_PIROUETTE" to "MELOETTA_ARIA"); add("GRENINJA_ASH" to "GRENINJA_BOND")
            add("FLOETTE_ETERNAL" to "FLOETTE_RED"); add("MEOWSTIC_F_MEGA" to "MEOWSTIC_M_MEGA")
            add("AEGISLASH_BLADE" to "AEGISLASH_SHIELD")
            for (c in listOf("SMALL", "LARGE", "SUPER")) { add("PUMPKABOO_$c" to "PUMPKABOO_AVERAGE"); add("GOURGEIST_$c" to "GOURGEIST_AVERAGE") }
            add("ZYGARDE_10_AURA_BREAK" to "ZYGARDE_50"); add("ZYGARDE_10_POWER_CONSTRUCT" to "ZYGARDE_50_POWER_CONSTRUCT")
            add("HOOPA_UNBOUND" to "HOOPA_CONFINED")
            for (c in listOf("POM_POM", "PAU", "SENSU")) add("ORICORIO_$c" to "ORICORIO_BAILE")
            add("WISHIWASHI_SCHOOL" to "WISHIWASHI_SOLO")
            for (c in listOf("RED", "ORANGE", "YELLOW", "GREEN", "BLUE", "INDIGO", "VIOLET")) add("MINIOR_CORE_$c" to "MINIOR_METEOR_$c")
            add("NECROZMA_DUSK_MANE" to "NECROZMA"); add("NECROZMA_DAWN_WINGS" to "NECROZMA")
            add("EISCUE_NOICE" to "EISCUE_ICE"); add("MORPEKO_HANGRY" to "MORPEKO_FULL_BELLY")
            add("ZACIAN_CROWNED" to "ZACIAN_HERO"); add("ZAMAZENTA_CROWNED" to "ZAMAZENTA_HERO")
            add("ETERNATUS_ETERNAMAX" to "ETERNATUS"); add("BASCULEGION_F" to "BASCULEGION_M")
            add("URSHIFU_RAPID_STRIKE" to "URSHIFU_SINGLE_STRIKE"); add("PALAFIN_HERO" to "PALAFIN_ZERO")
        }

        /** An item name as the pools compare it: upper case, an accented E as E, letters and digits only, Gen 3's short forms spelled out. */
        fun itemKey(name: String): String {
            val k = name.uppercase(Locale.ROOT).replace('\u00C9', 'E').filter { it in 'A'..'Z' || it in '0'..'9' }
            return GEN3_NAMES[k] ?: k
        }
    }

    private val L = game.L
    private val rom = game.rom
    private val random = Random(seed)
    /** UPR keeps names on a cosmetic random source, so a name list never moves a gameplay roll. */
    private val cosmetic = Random(seed xor 0x434F534D4554L)
    private val out = StringBuilder()
    val notes = ArrayList<String>()

    private val mons = game.mons
    private val eligible: List<Mon> = mons.filterNotNull().filter { it.eligible }
    /** UPR's mainPokemonList: the pool's species. */
    private val mainList: List<Mon> = eligible.filter { pool == HnsEngine.Pool.NATDEX || inVanillaEmerald(it) }

    /**
     * The VANILLA pool is vanilla Emerald's Pokemon exactly (Blake, 2026-10-05, after a wild Alolan Geodude in a
     * Vanilla run): National Dex 1 to 386, each in its base form only, so no regional or other form and no later
     * generation's evolution or baby (Heart & Soul's own Gen 1-3 scope, gen13Scope, takes those in).
     */
    private fun inVanillaEmerald(m: Mon): Boolean = m.natDex in 1..386 && !m.regional && m.baseOf == m.id
    private val inMain = BooleanArray(game.numSpecies).also { a -> mainList.forEach { a[it.id] = true } }
    /** Every species whose data is randomized: enabled, not the egg, not a cosmetic copy (those copy their base). */
    private val traitMons: List<Mon> = mons.filterNotNull().filter { it.enabled && it.id != game.speciesEgg && it.cosmeticOf == 0 }
    private val weights: Map<Int, Double>

    init {
        // UPR weighs a species' forms so their weights add to one (initializeSpeciesWeights): one group per Dex number,
        // a regional form its own.
        val groups = mainList.groupBy { if (it.regional) "r${it.id}" else "d${it.natDex}" }
        weights = groups.values.flatMap { g -> g.map { it.id to 1.0 / g.size } }.toMap()
    }

    private fun ability(name: String): Int? = L.enumValue("ABILITY", "ABILITY_$name")
    private fun item(name: String): Int? = L.enumValue("ITEM", "ITEM_$name")
    private fun move(name: String): Int? = L.enumValue("MOVE", "MOVE_$name")
    private fun abilitySet(vararg names: String): Set<Int> = names.mapNotNull { ability(it) }.toSet()

    private fun log(s: String = "") { out.append(s).append('\n') }
    private fun fmt(f: String, vararg a: Any?): String = String.format(Locale.ROOT, f, *a)

    private fun weighted(list: List<Mon>): Mon {
        var total = 0.0
        for (m in list) total += weights[m.id] ?: 1.0
        val r = random.nextDouble() * total
        var acc = 0.0
        for (m in list) {
            acc += weights[m.id] ?: 1.0
            if (r < acc) return m
        }
        return list.last()
    }

    private fun nm(id: Int) = game.speciesName(id)

    // ------------------------------------------------------------------------------------------------ evolution graph

    private fun evosFrom(m: Mon): List<Mon> = m.evos.mapNotNull { mons.getOrNull(it.target) }.filter { it.enabled }
    private fun evosTo(): Map<Int, List<Mon>> {
        val map = HashMap<Int, MutableList<Mon>>()
        for (m in mons) if (m != null && m.enabled) for (e in m.evos) if (e.target in 1 until game.numSpecies) map.getOrPut(e.target) { ArrayList() }.add(m)
        return map
    }

    /**
     * AbstractRomHandler.copyUpEvolutionsHelper: [base] on every species with no pre-evolution, then [evo] down each
     * evolution line; a species only reached through a cycle is treated as a base.
     */
    private fun copyUp(list: List<Mon>, base: (Mon) -> Unit, evo: (Mon, Mon, Boolean) -> Unit) {
        val inList = list.map { it.id }.toHashSet()
        val to = evosTo()
        val done = HashSet<Int>()
        fun walk(m: Mon) {
            for (t in evosFrom(m)) {
                if (t.id !in inList || !done.add(t.id)) continue
                evo(m, t, evosFrom(t).isEmpty())
                walk(t)
            }
        }
        for (m in list) {
            if (to[m.id].orEmpty().any { it.id in inList }) continue
            if (done.add(m.id)) { base(m); walk(m) }
        }
        for (m in list) if (done.add(m.id)) { base(m); walk(m) }
    }

    // ------------------------------------------------------------------------------------------------ the run

    fun run(): String {
        log("Randomizer Version: ${HnsEngine.ID}")
        log("Random Seed: $seed")
        log("Settings String: ${o.settingsString.ifEmpty { o.modeName }}")
        log()
        unsupported()

        // A Vanilla run never evolves out of vanilla Emerald (Magneton into Magnezone), whatever the settings say.
        if (o.limitPokemon || pool == HnsEngine.Pool.VANILLA) removeEvosForPool()
        val movesChanged = moveData()
        if (o.standardizeExpCurves) standardizeExpCurves()
        if (o.typesMod != "UNCHANGED") randomizeTypes()
        if (o.randomizeWildHeldItems) randomizeWildHeldItems()
        when (o.evolutionsMod) {
            "RANDOM" -> { randomizeEvolutions(); logEvolutions() }
            "RANDOM_EVERY_LEVEL" -> { randomizeEvolutionsEveryLevel(); logEvolutions() }
        }
        when (o.baseStatsMod) {
            "RANDOM" -> randomizeStats()
            "SHUFFLE" -> shuffleStats()
        }
        if (o.abilitiesMod == "RANDOMIZE") randomizeAbilities()
        copyCosmetic()
        if (o.baseStatsMod != "UNCHANGED" || o.abilitiesMod == "RANDOMIZE" || o.typesMod != "UNCHANGED" || o.randomizeWildHeldItems) {
            logTraits()
        } else log("Pokemon base stats & type: unchanged\n")
        // Always, whatever changeImpossibleEvolutions says: Emerald Nat. Dex 1.2.1's own data has no trade evolution left
        // (its presets leave the box off for that reason), so a run in line with it never asks for a trade.
        val impossible = removeImpossibleEvolutions()
        val easier = if (o.makeEvolutionsEasier) makeEvolutionsEasier() else emptyList()
        val timed = if (o.removeTimeBasedEvolutions) removeTimeBasedEvolutions() else emptyList()
        if (o.changeImpossibleEvolutions || impossible.isNotEmpty()) { log("--Removing Impossible Evolutions--"); impossible.forEach { log(it) }; log() }
        if (o.makeEvolutionsEasier) {
            log("--Making Evolutions Easier--"); log("Friendship evolutions now take 160 happiness (was 220).")
            easier.forEach { log(it) }; log()
        }
        if (o.removeTimeBasedEvolutions) { log("--Removing Timed-Based Evolutions--"); timed.forEach { log(it) }; log() }

        randomizeStarters()
        if (o.randomizeStarterHeldItems) starterHeldItem()
        if (movesChanged) logMoveData() else log("Move Data: Unchanged.\n")
        when (o.movesetsMod) {
            "COMPLETELY_RANDOM", "RANDOM_PREFER_SAME_TYPE" -> { randomizeMovesets(); randomizeEggMoves(); logMovesets() }
            else -> log("Pokemon Movesets: Unchanged.\n")
        }
        if (o.tmsMod == "RANDOM") { randomizeTMs(); logTMs() } else log("TM Moves: Unchanged.\n")
        tmCompat()
        tutorCompat()
        trainers()
        statics()
        wild()
        trades()
        fieldItems()
        if (o.pickupItemsMod == "RANDOM") pickupItems()
        if (o.fieldItemsMod != "UNCHANGED") pcItem()
        if (o.shopItemsMod != "UNCHANGED") notes += "Shop items were not randomized: the marts are not in the layout data yet."

        game.write()
        writeMachines()
        game.writeTrainers(L.struct("Trainer").fields["aiFlags"])
        game.writeWild()
        game.writeTrades()
        game.writeFieldItems()
        game.writeItems()
        writeStatics()

        if (notes.isNotEmpty()) {
            log("--KaizoCore Notes--")
            notes.forEach { log(it) }
            log()
        }
        log("------------------------------------------------------------------")
        log("Randomization of ${HnsEngine.GAME_NAME} completed.")
        log("Free space used: ${rom.freeUsed()} bytes")
        log("------------------------------------------------------------------")
        return out.toString()
    }

    /** Options the RSE presets never set and this engine does not do: said in the log, never silently dropped. */
    private fun unsupported() {
        val n = ArrayList<String>()
        if (o.updateBaseStats) n += "Update base stats to a later generation"
        if (o.updateMoves) n += "Update moves to a later generation"
        if (o.movesetsMod == "METRONOME_ONLY") n += "Metronome only"
        if (o.trainersMod !in setOf("UNCHANGED", "RANDOM")) n += "Trainers mode ${o.trainersMod} (done as RANDOM)"
        if (o.trainersMatchTypingDistribution) n += "Trainers match typing distribution"
        if (o.betterTrainerMovesets) n += "Better trainer movesets"
        if (o.allowTrainerAltFormes) n += "Trainer alternate forms"
        if (o.wildMod !in setOf("UNCHANGED", "AREA_MAPPING", "RANDOM")) n += "Wild mode ${o.wildMod}"
        if (o.wildRestrictionMod !in setOf("NONE", "SIMILAR_STRENGTH")) n += "Wild restriction ${o.wildRestrictionMod}"
        if (o.useTimeBasedEncounters) n += "Time-based wild encounters (times share one mapping)"
        if (o.staticMod !in setOf("UNCHANGED", "COMPLETELY_RANDOM")) n += "Static mode ${o.staticMod} (done as completely random)"
        if (o.keepFieldMoveTMs) n += "Keep field move TMs"
        if (o.tmLevelUpMoveSanity || o.tmsFollowEvolutions) n += "TM compatibility sanity and follow evolutions"
        if (o.tutorLevelUpMoveSanity || o.tutorFollowEvolutions) n += "Tutor compatibility sanity and follow evolutions"
        if (o.reorderDamagingMoves) n += "Reorder damaging moves"
        if (o.tmsForceGoodDamaging) n += "TMs forced good damaging"
        // Misc tweaks: Ban Lucky Egg is applied (itemList), the PC potion is rolled with the field items (pcItem); any other
        // one the file sets is named here. The Nat. Dex fork repurposed bit 22 as Hidden Item Sparkles, which its Emerald
        // presets set, so each fork's own list names the bits.
        val handled = com.dabomstew.pkrandom.MiscTweak.BAN_LUCKY_EGG.value or
            (if (o.fieldItemsMod != "UNCHANGED") com.dabomstew.pkrandom.MiscTweak.RANDOMIZE_PC_POTION.value else 0)
        val tweaks: List<Pair<Int, String>> = if (o.zxRules) com.dabomstew.pkrandomzx.MiscTweak.allTweaks.map { it.value to it.tweakName }
            else com.dabomstew.pkrandom.MiscTweak.allTweaks.map { it.value to it.tweakName }
        for ((bit, name) in tweaks) if (o.miscTweaks and bit != 0 && bit and handled == 0) n += "misc tweak \"$name\""
        for (x in n) notes += "Not applied, this engine does not do it yet: $x."
    }

    // ------------------------------------------------------------------------------------------------ pool

    /** AbstractRomHandler.removeEvosForPokemonPool: an evolution out of the pool is dropped. */
    private fun removeEvosForPool() {
        for (m in mainList) {
            val keep = m.evos.filter { e -> mons.getOrNull(e.target)?.let { t -> inMain[t.id] || (t.cosmeticOf != 0 && inMain[t.cosmeticOf]) || !t.eligible && inMain[t.baseOf] } == true }
            if (keep.size != m.evos.size) { m.evos = keep.toMutableList(); m.evosChanged = true }
        }
    }

    // ------------------------------------------------------------------------------------------------ moves

    private val struggle = move("STRUGGLE") ?: 165

    private fun moveData(): Boolean {
        if (!(o.randomizeMovePowers || o.randomizeMovePPs || o.randomizeMoveAccuracies || o.randomizeMoveTypes || o.randomizeMoveCategory)) return false
        val list = game.moves.filterNotNull().filter { it.id < game.movesCount && it.id != struggle }
        if (o.randomizeMovePowers) for (mv in list) if (mv.power >= 10) {
            mv.power = if (random.nextInt(3) != 2) random.nextInt(11) * 5 + 50 else random.nextInt(27) * 5 + 20
            repeat(2) { if (random.nextInt(100) == 0) mv.power += 50 }
            val hits = hitCount(mv)
            if (hits != 1.0) { mv.power = (Math.round(mv.power / hits / 5) * 5).toInt(); if (mv.power == 0) mv.power = 5 }
            mv.power = mv.power.coerceAtMost(511); mv.changed = true
        }
        if (o.randomizeMovePPs) for (mv in list) {
            mv.pp = if (random.nextInt(3) != 2) random.nextInt(3) * 5 + 15 else random.nextInt(8) * 5 + 5
            mv.changed = true
        }
        if (o.randomizeMoveAccuracies) for (mv in list) if (mv.accuracy >= 5) {
            mv.accuracy = when {
                mv.accuracy <= 50 -> (random.nextInt(7) * 5 + 20).let { if (random.nextInt(10) == 0) (it * 3 / 2) / 5 * 5 else it }
                mv.accuracy < 90 -> { var a = 100; while (a > 20) { if (random.nextInt(10) < 2) break; a -= 5 }; a }
                else -> { var a = 100; while (a > 20) { if (random.nextInt(10) < 4) break; a -= 5 }; a }
            }
            mv.changed = true
        }
        if (o.randomizeMoveTypes) {
            val types = randomTypes()
            for (mv in list) if (mv.type != 0) { mv.type = types[random.nextInt(types.size)]; mv.changed = true }
        }
        if (o.randomizeMoveCategory) for (mv in list) if (mv.category != 2 && random.nextInt(2) == 0) { mv.category = 1 - mv.category; mv.changed = true }
        return true
    }

    private fun hitCount(mv: HnsGame.MoveData): Double = if (mv.multiHit) 3.0 else mv.strikeCount.toDouble()

    private fun logMoveData() {
        log("--Move Data--")
        log("NUM|NAME           |TYPE    |POWER|ACC.|PP |CATEGORY")
        for (mv in game.moves) {
            if (mv == null || mv.id >= game.movesCount) continue
            log(fmt("%3d|%-15s|%-8s|%5d|%4d|%3d| %s", mv.id, mv.name, game.typeName(mv.type), mv.power, mv.accuracy, mv.pp,
                listOf("PHYSICAL", "SPECIAL", "STATUS").getOrElse(mv.category) { "?" }))
        }
        log()
    }

    /** The eighteen battle types: not NONE, MYSTERY or STELLAR. */
    private fun randomTypes(): List<Int> = game.typeNames.entries
        .filter { it.value !in setOf("NONE", "MYSTERY", "STELLAR") }.map { it.key }.sorted()

    // ------------------------------------------------------------------------------------------------ growth, types, items

    private val growthByCurve = mapOf(
        "SLOW" to "GROWTH_SLOW", "MEDIUM_SLOW" to "GROWTH_MEDIUM_SLOW", "MEDIUM_FAST" to "GROWTH_MEDIUM_FAST",
        "FAST" to "GROWTH_FAST", "ERRATIC" to "GROWTH_ERRATIC", "FLUCTUATING" to "GROWTH_FLUCTUATING",
    )

    /** AbstractRomHandler.standardizeEXPCurves, over every species; a form keeps its base's curve so a Mega never changes level. */
    private fun standardizeExpCurves() {
        val chosen = L.enumValue("GROWTH", growthByCurve[o.expCurve] ?: "GROWTH_MEDIUM_FAST") ?: 0
        val slow = L.enumValue("GROWTH", "GROWTH_SLOW") ?: 5
        for (m in mons) {
            if (m == null || !m.enabled || m.id == game.speciesEgg) continue
            m.growth = when (o.expCurveMod) {
                "LEGENDARIES" -> if (m.isLegendary) slow else chosen
                "STRONG_LEGENDARIES" -> if (m.isStrongLegendary) slow else chosen
                else -> chosen
            }
        }
        for (m in mons) if (m != null && m.enabled && m.baseOf != 0 && m.baseOf != m.id) m.growth = mons[m.baseOf]!!.growth
    }

    /** AbstractRomHandler.randomizePokemonTypes. */
    private fun randomizeTypes() {
        val types = randomTypes()
        fun rt() = types[random.nextInt(types.size)]
        fun second(m: Mon, chance: Double) {
            if (random.nextDouble() < chance || o.dualTypeOnly) {
                var t = rt(); while (t == m.types[0]) t = rt(); m.types[1] = t
            } else m.types[1] = m.types[0]
        }
        if (o.typesMod == "RANDOM_FOLLOW_EVOLUTIONS") {
            copyUp(traitMons, { m ->
                m.types[0] = rt()
                second(m, if (m.evos.size == 1) 0.35 else 0.5)
            }, { from, to, final ->
                to.types[0] = from.types[0]; to.types[1] = from.types[1]
                if (to.types[1] == to.types[0]) second(to, if (final) 0.25 else 0.15)
            })
        } else {
            for (m in traitMons) { m.types[0] = rt(); second(m, 0.5) }
        }
    }

    // Item lists, UPR's Gen 3 lists by item constant (Gen3Constants), with HnS's later items added in the same spirit.
    private val keyPocket = L.enumValue("POCKET", "POCKET_KEY_ITEMS") ?: 5
    private val tmPocket = L.enumValue("POCKET", "POCKET_TM_HM") ?: 3
    private val berryPocket = L.enumValue("POCKET", "POCKET_BERRIES") ?: 4
    private val hmItems: Set<Int> = L.machines.filter { it.kind == "HM" }.map { it.item }.toSet()
    /** The HM moves (Cut, Fly, Surf, Strength, Flash, Rock Smash, Waterfall, Whirlpool in Heart & Soul). */
    private val hmMoves: Set<Int> = L.machines.filter { it.kind == "HM" }.map { it.move }.toSet()
    private val tmItems: List<Int> = L.machines.filter { it.kind == "TM" }.map { it.item }

    /** Whether item [id] is in the pool's game (see [poolItems]). */
    private fun inPool(id: Int): Boolean = poolItems == null || game.items.getOrNull(id)?.name?.let { itemKey(it) in poolItems } == true

    /** The TMs a randomized TM may become: the pool's (vanilla Emerald has TM01 to TM50, Heart & Soul more). */
    private val poolTmItems: List<Int> by lazy { tmItems.filter { inPool(it) } }

    /** UPR's allowedItems: everything a player can hold that is not a key item, an HM or an unused slot. */
    private val allowedItems: List<Int> by lazy {
        val machineItems = L.machines.map { it.item }.toSet()
        val tmLike = L.enum("ITEM").filter { it.key.matches(Regex("ITEM_(TM|HM)\\d+")) }.values.toSet()
        game.items.filterNotNull().filter { d ->
            d.id != 0 && d.name.isNotEmpty() && !d.name.startsWith("?") && d.pocket != keyPocket &&
                d.id !in hmItems && !(d.pocket == tmPocket && d.id !in machineItems) && !(d.id in tmLike && d.id !in machineItems) &&
                pictured(d) && numberedBerry(d) && firstOfName(d) && inPool(d.id) && (poolAllowed == null || itemKey(d.name) in poolAllowed)
        }.map { it.id }
    }

    /**
     * An item the bag can show: not drawn with ITEM_NONE's question mark. Heart & Soul's unused slots carry it, among
     * them ITEM_UNUSED_BERRY_1 (897), named SITRUS BERRY like the real one (523), so the name match let it into the
     * pools and a Kaizo run handed it out: the bag showed "No?4 SITRUS BERRY" with a "?" (Blake, rc37).
     */
    private fun pictured(d: HnsGame.ItemData): Boolean = d.iconPic != game.items[0]!!.iconPic

    /** A berry pocket item is one of the numbered berries (the bag numbers it id - CHERI + 1, in two digits). */
    private val berryRange: IntRange? by lazy {
        val first = item("CHERI_BERRY"); val last = item("ENIGMA_BERRY_E_READER") ?: item("ENIGMA_BERRY")
        if (first != null && last != null) first..last else null
    }
    private fun numberedBerry(d: HnsGame.ItemData): Boolean = d.pocket != berryPocket || berryRange?.contains(d.id) != false

    /**
     * The first item of its name: the pools take items by name, and a later item of the same name is a copy the game
     * treats otherwise (ITEM_ENIGMA_BERRY_E_READER, 581, reads its data from the save's e-Reader berry; 897 above).
     */
    private val firstIdOfName: Map<String, Int> by lazy {
        game.items.filterNotNull().filter { it.name.isNotEmpty() }.groupBy { it.name }.mapValues { e -> e.value.minOf { it.id } }
    }
    private fun firstOfName(d: HnsGame.ItemData): Boolean = firstIdOfName[d.name] == d.id

    /** UPR's nonBadItems: allowed, less mail, berries, valuables, one species' items, contest scarves (Gen3Constants), and the like. */
    private val nonBadItems: List<Int> by lazy {
        val holdBad = setOf("HOLD_EFFECT_LIGHT_BALL", "HOLD_EFFECT_SOUL_DEW", "HOLD_EFFECT_LUCKY_PUNCH", "HOLD_EFFECT_METAL_POWDER",
            "HOLD_EFFECT_THICK_CLUB", "HOLD_EFFECT_LEEK", "HOLD_EFFECT_DEEP_SEA_TOOTH", "HOLD_EFFECT_DEEP_SEA_SCALE",
            "HOLD_EFFECT_QUICK_POWDER", "HOLD_EFFECT_ADAMANT_ORB", "HOLD_EFFECT_LUSTROUS_ORB", "HOLD_EFFECT_GRISEOUS_ORB",
            "HOLD_EFFECT_MEGA_STONE", "HOLD_EFFECT_PRIMAL_ORB", "HOLD_EFFECT_Z_CRYSTAL", "HOLD_EFFECT_MEMORY", "HOLD_EFFECT_DRIVE",
            "HOLD_EFFECT_PLATE", "HOLD_EFFECT_OGERPON_MASK", "HOLD_EFFECT_BERSERK_GENE")
            .mapNotNull { L.enumValue("HOLD_EFFECT", it) }.toSet()
        val names = setOf("PEARL", "BIG_PEARL", "STARDUST", "STAR_PIECE", "NUGGET", "BIG_NUGGET", "PEARL_STRING", "COMET_SHARD",
            "BALM_MUSHROOM", "TINY_MUSHROOM", "BIG_MUSHROOM", "RED_SHARD", "BLUE_SHARD", "YELLOW_SHARD", "GREEN_SHARD",
            "HEART_SCALE", "SHOAL_SALT", "SHOAL_SHELL", "RED_SCARF", "BLUE_SCARF", "PINK_SCARF", "GREEN_SCARF", "YELLOW_SCARF",
            "RARE_BONE", "RELIC_COPPER", "RELIC_SILVER", "RELIC_GOLD", "RELIC_VASE", "RELIC_BAND", "RELIC_STATUE", "RELIC_CROWN",
            "ODD_KEYSTONE", "BOTTLE_CAP", "GOLD_BOTTLE_CAP", "HONEY", "SLOWPOKE_TAIL", "SOOT_SACK")
            .mapNotNull { item(it) }.toSet()
        if (poolNonBad != null) return@lazy allowedItems.filter { id ->
            itemKey(game.items[id]!!.name) in poolNonBad && !(o.banLuckyEgg && id == item("LUCKY_EGG"))
        }
        allowedItems.filter { id ->
            val d = game.items[id]!!
            d.pocket != berryPocket && !d.name.endsWith(" MAIL") && !d.name.endsWith("MAIL") && d.holdEffect !in holdBad && id !in names &&
                !(o.banLuckyEgg && id == item("LUCKY_EGG"))
        }
    }

    private fun itemList(banBad: Boolean): List<Int> {
        val l = if (banBad) nonBadItems else allowedItems
        return if (o.banLuckyEgg) l.filter { it != item("LUCKY_EGG") } else l
    }

    /** For tests: the list every item roll of [banBad] draws from, and one starter-item roll on this seed. */
    internal fun itemPoolForTest(banBad: Boolean): List<Int> = itemList(banBad)
    internal fun starterItemRollForTest(): Int = randomItem(itemList(o.banBadStarterHeldItems))

    /** UPR's ItemList.randomItem: any item of the list, TMs included. */
    private fun randomItem(list: List<Int>) = list[random.nextInt(list.size)]

    /** AbstractRomHandler.randomizeWildHeldItems, the branch for a game with no guaranteed held item field (Gen 3). */
    private fun randomizeWildHeldItems() {
        val items = itemList(o.banBadWildHeldItems)
        for (m in traitMons) {
            val d = random.nextDouble()
            when {
                d < 0.5 -> { m.itemCommon = 0; m.itemRare = 0 }
                d < 0.65 -> { m.itemCommon = 0; m.itemRare = randomItem(items) }
                d < 0.8 -> { m.itemCommon = randomItem(items); m.itemRare = 0 }
                else -> {
                    m.itemCommon = randomItem(items); m.itemRare = randomItem(items)
                    while (m.itemRare == m.itemCommon) m.itemRare = randomItem(items)
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ evolutions

    private val pickedForEvo = HashSet<Int>()

    /**
     * AbstractRomHandler.randomizeEvolutions. A species keeps how many evolutions it has and how each one happens
     * (method, parameter, conditions); only the target changes. Forms an evolution reaches by a cosmetic choice
     * (Milcery's sixty-three Alcremie) are one evolution here, as UPR treats a cosmetic form as its base.
     */
    private fun randomizeEvolutions() {
        val stageLimit = if (o.evosMaxThreeStages) 3 else 10
        val poolList = mainList.toMutableList()
        val original = HashMap<Int, List<Evo>>()
        for (m in poolList) {
            val seenTargets = HashSet<Int>()
            original[m.id] = m.evos.filter { e ->
                val t = mons.getOrNull(e.target) ?: return@filter false
                seenTargets.add(if (t.cosmeticOf != 0) t.cosmeticOf else t.id)
            }.map { it.copy() }
        }
        val oldPairs = HashSet<Long>()
        if (o.evosForceChange) for (m in poolList) for (e in original[m.id]!!) oldPairs += pair(m.id, e.target)
        val banned = HashSet<Int>()
        for (m in poolList) { m.evos = ArrayList(); m.evosChanged = true }
        poolList.shuffle(random)
        val newPairs = HashSet<Long>()
        for (from in poolList) {
            for (ev in original[from.id]!!) {
                val origTo = mons[ev.target]!!
                var repl = ArrayList<Mon>()
                for (pk in mainList) {
                    if (pk === from || pk.growth != from.growth || pk.id in banned) continue
                    if (pair(from.id, pk.id) in newPairs) continue
                    if (o.evosForceChange && pair(from.id, pk.id) in oldPairs) continue
                    if (cycle(from, pk)) continue
                    if (exceedsStages(from, pk, stageLimit, original)) continue
                    repl.add(pk)
                }
                if (repl.isEmpty()) continue
                if (repl.size > 1 && o.evosSameTyping) {
                    val typed = repl.filter { pk ->
                        if (o.evosMatchPostEvoTyping) sharesType(pk, origTo) else sharesType(pk, from)
                    }
                    if (typed.isNotEmpty()) repl = ArrayList(typed)
                }
                if (!o.evosSimilarStrength && !pickedForEvo.containsAll(repl.map { it.id })) repl.removeAll { it.id in pickedForEvo }
                val picked = when {
                    repl.size == 1 -> repl[0]
                    o.evosSimilarStrength -> powerReplacement(repl, origTo)
                    else -> repl[random.nextInt(repl.size)]
                }
                pickedForEvo += picked.id
                from.evos.add(Evo(ev.method, ev.param, picked.id, ev.conds.map { it.copyOf() }.toMutableList(), ev.paramsPtr, ev.paramsOk, ev.condsChanged))
                newPairs += pair(from.id, picked.id)
            }
        }
    }

    private fun pair(a: Int, b: Int) = (a.toLong() shl 32) or b.toLong()

    private fun sharesType(a: Mon, b: Mon): Boolean =
        a.types[0] == b.types[0] || a.types[0] == b.types[1] || a.types[1] == b.types[0] || a.types[1] == b.types[1]

    /** Would from -> to close a loop? */
    private fun cycle(from: Mon, to: Mon): Boolean {
        val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.add(to.id)
        while (stack.isNotEmpty()) {
            val x = stack.removeLast()
            if (x == from.id) return true
            if (!seen.add(x)) continue
            mons[x]?.evos?.forEach { stack.add(it.target) }
        }
        return false
    }

    /** The stage limit check of randomizeEvolutions: no species of the joined family may sit [limit] stages deep. */
    private fun exceedsStages(from: Mon, to: Mon, limit: Int, original: Map<Int, List<Evo>>): Boolean {
        if (limit >= 10) return false
        val temp = Evo(0, 0, to.id, ArrayList(), 0, true)
        from.evos.add(temp)
        try {
            val parents = HashMap<Int, MutableList<Int>>()
            val family = HashSet<Int>()
            val queue = ArrayDeque<Int>(); queue.add(from.id)
            val all = mons.filterNotNull().filter { it.enabled }
            for (m in all) for (e in m.evos) parents.getOrPut(e.target) { ArrayList() }.add(m.id)
            while (queue.isNotEmpty()) {
                val x = queue.removeFirst()
                if (!family.add(x)) continue
                mons[x]?.evos?.forEach { queue.add(it.target) }
                parents[x]?.forEach { queue.add(it) }
            }
            fun depth(x: Int, seen: Set<Int>): Int = parents[x].orEmpty().filter { it !in seen }.maxOfOrNull { 1 + depth(it, seen + x) } ?: 0
            for (x in family) {
                val d = depth(x, emptySet())
                if (d >= limit) return true
                if (d == limit - 1 && mons[x]!!.evos.isEmpty() && original[x].orEmpty().isNotEmpty()) return true
            }
            return false
        } finally {
            from.evos.remove(temp)
        }
    }

    /** AbstractRomHandler.pickEvoPowerLvlReplacement, on the original BSTs (evolutions are drawn before stats change). */
    private fun powerReplacement(list: List<Mon>, current: Mon): Mon {
        val bst = current.origBst
        var min = bst - bst / 10
        var max = bst + bst / 10
        val canPick = ArrayList<Mon>()
        val emergency = ArrayList<Mon>()
        var rounds = 0
        while (canPick.isEmpty() || (canPick.size < 3 && rounds < 3)) {
            for (pk in list) {
                if (pk.origBst in min..max && pk !in canPick && pk !in emergency) {
                    if (pk.id in pickedForEvo) emergency.add(pk) else canPick.add(pk)
                }
            }
            if (rounds >= 2 && canPick.isEmpty()) canPick.addAll(emergency)
            min -= bst / 20; max += bst / 20; rounds++
            if (rounds > 60) { if (canPick.isEmpty()) canPick.addAll(list); break }
        }
        return canPick[random.nextInt(canPick.size)]
    }

    /**
     * Evo Kaizo. The Nat. Dex fork clears every evolution of the pool and sets a script flag
     * (Gen3RomHandler.setEvolutionEveryLevelFlag) so the game rolls a new species at each level-up; Heart & Soul has no
     * such flag, so the evolutions are written as data: every species of the pool evolves at its next level (EVO_LEVEL 1,
     * one evolution, no split and no condition) into the next species of one random line through its growth group (the
     * same curve, so its level never jumps). A line never comes back to a species, so there are no evolution loops (Blake,
     * 2026-10-06, as the Nat. Dex Evo Kaizo rules have it: "There are no evo loops"); the last species of each line evolves
     * no more. The line is drawn one species at a time from those not on it yet: a shared type kept when asked, an old
     * evolution avoided when asked, any one left when nothing fits.
     */
    private fun randomizeEvolutionsEveryLevel() {
        val levelMethod = L.enumValue("EVO", "EVO_LEVEL") ?: 1
        val oldPairs = HashSet<Long>()
        if (o.evosForceChange) for (m in mainList) for (e in m.evos) oldPairs += pair(m.id, e.target)
        for (m in mainList) { m.evos = ArrayList(); m.evosChanged = true }
        for (group in mainList.groupBy { it.growth }.values) {
            val left = group.toMutableList()
            var from = left.removeAt(random.nextInt(left.size))
            while (left.isNotEmpty()) {
                var cand: List<Mon> = left
                if (o.evosForceChange) cand.filter { pair(from.id, it.id) !in oldPairs }.takeIf { it.isNotEmpty() }?.let { cand = it }
                if (o.evosSameTyping) cand.filter { sharesType(it, from) }.takeIf { it.isNotEmpty() }?.let { cand = it }
                val next = cand[random.nextInt(cand.size)]
                left.remove(next)
                from.evos.add(Evo(levelMethod, 1, next.id, ArrayList(), 0, true, condsChanged = true))
                from = next
            }
        }
    }

    private fun evoName(e: Evo) = nm(e.target)

    private fun logEvolutions() {
        log("--Randomized Evolutions--")
        for (m in mons) {
            if (m == null || !m.enabled || m.cosmeticOf != 0 || m.evos.isEmpty()) continue
            val names = m.evos.map { evoName(it) }
            val s = if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " and " + names.last()
            log(fmt("%-15s -> %-15s", m.displayName, s))
        }
        log()
    }

    // ------------------------------------------------------------------------------------------------ stats, abilities

    /** Pokemon.randomizeStatsWithinBST, Shedinja's rule for any species of 1 HP. */
    private fun randomizeStats() {
        if (o.baseStatsFollowEvolutions) {
            copyUp(traitMons, { m -> statsWithinBst(m) }, { from, to, _ -> copyStatsUp(from, to) })
        } else {
            for (m in traitMons) statsWithinBst(m)
        }
    }

    private fun statsWithinBst(m: Mon) {
        while (true) {
            val s = IntArray(6)
            if (m.origStats[0] == 1) {
                val bst = m.origBst - 51
                val w = DoubleArray(5) { random.nextDouble() }
                val tot = w.sum()
                s[0] = 1
                // atk, def, spa, spd, spe in UPR's draw order; SpeciesInfo stores atk, def, spe, spa, spd.
                val v = IntArray(5) { (maxOf(1L, Math.round(w[it] / tot * bst))).toInt() + 10 }
                s[1] = v[0]; s[2] = v[1]; s[4] = v[2]; s[5] = v[3]; s[3] = v[4]
            } else {
                val bst = m.origBst - 70
                val w = DoubleArray(6) { random.nextDouble() }
                val tot = w.sum()
                s[0] = (maxOf(1L, Math.round(w[0] / tot * bst))).toInt() + 20
                val v = IntArray(5) { (maxOf(1L, Math.round(w[it + 1] / tot * bst))).toInt() + 10 }
                s[1] = v[0]; s[2] = v[1]; s[4] = v[2]; s[5] = v[3]; s[3] = v[4]
            }
            if (s.all { it <= 255 }) { s.copyInto(m.stats); return }
        }
    }

    private fun copyStatsUp(from: Mon, to: Mon) {
        val ratio = to.origBst.toDouble() / from.origBst
        for (k in 0 until 6) to.stats[k] = Math.round(from.stats[k] * ratio).toInt().coerceIn(1, 255)
    }

    private fun shuffleStats() {
        for (m in traitMons) {
            if (m.origStats[0] == 1) continue
            val l = m.stats.toMutableList(); l.shuffle(random)
            for (k in 0 until 6) m.stats[k] = l[k]
        }
    }

    private val wonderGuard = ability("WONDER_GUARD") ?: 25

    /**
     * The last ability id a roll may land on, by pool (Blake, 2026-10-07; Heart & Soul numbers abilities the official
     * way, so the ids are the games' own):
     * - NATDEX: Black 2 / White 2 Kaizo's, the newest game with official IronMON rules: Gen5Constants.highestAbilityIndex,
     *   164, Teravolt.
     * - VANILLA: vanilla Emerald Kaizo's, ZX's Gen3Constants.highestAbilityIndex: through Air Lock (Gen 3's 77, 76 in
     *   the official numbering Heart & Soul uses; Gen 3's unused Cacophony, which UPR leaves out, is not in the game).
     */
    internal val lastAbility: Int by lazy {
        if (pool == HnsEngine.Pool.NATDEX) ability("TERAVOLT") ?: 164 else ability("AIR_LOCK") ?: 76
    }

    /**
     * AbstractRomHandler.randomizeAbilities (engine-zx AbstractRomHandler.java:505) on abilities 1 to [lastAbility]: the
     * game's useless ones out, then
     * the bans the settings ask for, the variations of the source game when duplicates are weighed together (Gen 5 adds
     * Filter/Solid Rock, Rough Skin/Iron Barbs, Mold Breaker/Turboblaze/Teravolt). Gen 5 rolls its hidden ability too;
     * Emerald has none, so on the Vanilla pool Heart & Soul's hidden slot takes the rolled first ability.
     */
    private fun randomizeAbilities() {
        val maxAbility = minOf(lastAbility, game.abilityNames.size - 1)
        val banned = HashSet<Int>()
        banned += 0
        // An id the build has no real ability for (no name, or ITEM_NONE's "-------") is never rolled.
        for (id in 1..maxAbility) if (game.abilityNames[id].isEmpty() || game.abilityNames[id].startsWith("-")) banned += id
        // The source game's getUselessAbilities, banned whatever the settings say: ZX Gen5Constants.uselessAbilities
        // (Gen5Constants.java:134) on Nat. Dex, ZX Gen3Constants.uselessAbilities (Gen3Constants.java:190: Forecast and
        // Cacophony, which Heart & Soul has not) on Vanilla.
        banned += if (pool == HnsEngine.Pool.NATDEX) abilitySet("FORECAST", "MULTITYPE", "FLOWER_GIFT", "ZEN_MODE") else abilitySet("FORECAST", "CACOPHONY")
        if (!o.allowWonderGuard) banned += wonderGuard
        if (o.banTrappingAbilities) banned += abilitySet("SHADOW_TAG", "MAGNET_PULL", "ARENA_TRAP")
        if (o.banNegativeAbilities) banned += abilitySet("DEFEATIST", "SLOW_START", "TRUANT", "KLUTZ", "STALL")
        if (o.banBadAbilities) {
            banned += abilitySet("MINUS", "PLUS", "ANTICIPATION", "FOREWARN", "FRISK", "HONEY_GATHER", "AURA_BREAK", "RECEIVER", "POWER_OF_ALCHEMY")
            if (!o.doubleBattleMode) banned += abilitySet("FRIEND_GUARD", "HEALER", "TELEPATHY", "SYMBIOSIS", "BATTERY")
        }
        val variations = LinkedHashMap<Int, List<Int>>()
        if (o.weighDuplicateAbilities) {
            banned += abilitySet("VITAL_SPIRIT", "WHITE_SMOKE", "PURE_POWER", "SHELL_ARMOR", "AIR_LOCK", "SOLID_ROCK", "IRON_BARBS",
                "TURBOBLAZE", "TERAVOLT", "EMERGENCY_EXIT", "DAZZLING", "TANGLING_HAIR", "POWER_OF_ALCHEMY", "FULL_METAL_BODY",
                "SHADOW_SHIELD", "PRISM_ARMOR", "LIBERO", "STALWART")
            val table = mutableListOf(listOf("INSOMNIA", "VITAL_SPIRIT"), listOf("CLEAR_BODY", "WHITE_SMOKE"),
                listOf("HUGE_POWER", "PURE_POWER"), listOf("BATTLE_ARMOR", "SHELL_ARMOR"), listOf("CLOUD_NINE", "AIR_LOCK"))
            if (pool == HnsEngine.Pool.NATDEX) table += listOf(listOf("FILTER", "SOLID_ROCK"), listOf("ROUGH_SKIN", "IRON_BARBS"),
                listOf("MOLD_BREAKER", "TURBOBLAZE", "TERAVOLT"))
            for (names in table) {
                val ids = names.mapNotNull { ability(it) }.filter { it <= maxAbility }
                if (ids.size == names.size) variations[ids[0]] = ids
            }
        }
        fun pick(vararg already: Int): Int {
            while (true) {
                var a = random.nextInt(maxAbility) + 1
                if (a in banned || already.contains(a)) continue
                variations[a]?.let { a = it[random.nextInt(it.size)] }
                return a
            }
        }
        fun randomize(m: Mon) {
            if (m.abilities.contains(wonderGuard)) return
            m.abilities[0] = pick()
            m.abilities[1] = if (o.ensureTwoAbilities || random.nextDouble() < 0.5) pick(m.abilities[0]) else 0
            // Emerald has no hidden ability: on the Vanilla pool a hidden-ability Pokemon has its first ability, so it
            // behaves as any other (Blake, 2026-10-07: "i don't want to break the game"). Gen 5 rolls its third slot.
            m.abilities[2] = if (pool == HnsEngine.Pool.VANILLA) m.abilities[0] else pick(m.abilities[0], m.abilities[1])
        }
        if (o.abilitiesFollowEvolutions) {
            copyUp(traitMons, { randomize(it) }, { from, to, _ ->
                if (!to.abilities.contains(wonderGuard)) from.abilities.copyInto(to.abilities)
            })
        } else traitMons.forEach { randomize(it) }
        // AbstractRomHandler.randomizeAbilities's "cases for certain alt forms": these forms take their base's abilities.
        for ((form, base) in FORM_ABILITIES_FROM_BASE) {
            val f = speciesByConst["SPECIES_$form"]?.let { mons[it] } ?: continue
            val b = speciesByConst["SPECIES_$base"]?.let { mons[it] }?.let { if (it.cosmeticOf != 0) mons[it.cosmeticOf] else it } ?: continue
            if (f.enabled && b.enabled) b.abilities.copyInto(f.abilities)
        }
    }

    private val speciesByConst: Map<String, Int> by lazy { mons.filterNotNull().associate { it.const to it.id } }

    /** A cosmetic form (Furfrou's trims, Minior's colors) is its base again: UPR's copyBaseForme*. */
    private fun copyCosmetic() {
        for (m in mons) {
            if (m == null || m.cosmeticOf == 0) continue
            val b = mons[m.cosmeticOf]!!
            b.stats.copyInto(m.stats); b.types.copyInto(m.types); b.abilities.copyInto(m.abilities)
            m.growth = b.growth; m.itemCommon = b.itemCommon; m.itemRare = b.itemRare
        }
    }

    private fun typeString(m: Mon): String =
        if (m.types[1] == m.types[0] || m.types[1] == 0) game.typeName(m.types[0]) else game.typeName(m.types[0]) + "/" + game.typeName(m.types[1])

    private fun logTraits() {
        log("--Pokemon Base Stats & Types--")
        log("NUM|NAME            |TYPE             |  HP| ATK| DEF|SATK|SDEF| SPD|ABILITY1        |ABILITY2        |ABILITY3        |ITEM")
        for (m in mons) {
            if (m == null || !m.enabled || m.cosmeticOf != 0 || m.id == game.speciesEgg) continue
            val sb = StringBuilder(fmt("%3d|%-16s|%-17s|%4d|%4d|%4d|%4d|%4d|%4d", m.id, m.displayName, typeString(m),
                m.stats[0], m.stats[1], m.stats[2], m.stats[4], m.stats[5], m.stats[3]))
            sb.append(fmt("|%-16s|%-16s|%-16s", abilityName(m.abilities[0]),
                if (m.abilities[1] == m.abilities[0]) "--" else abilityName(m.abilities[1]), abilityName(m.abilities[2])))
            sb.append('|')
            if (m.itemCommon != 0 && m.itemCommon == m.itemRare) sb.append(game.itemName(m.itemCommon) + " (100%)")
            else {
                val parts = ArrayList<String>()
                if (m.itemCommon > 0) parts += game.itemName(m.itemCommon) + " (common)"
                if (m.itemRare > 0) parts += game.itemName(m.itemRare) + " (rare)"
                sb.append(parts.joinToString(", "))
            }
            log(sb.toString())
        }
        log()
    }

    private fun abilityName(a: Int) = if (a == 0) "-------" else game.abilityNames.getOrElse(a) { "?" }

    // ------------------------------------------------------------------------------------------------ evolution fixes

    private val ifTime = L.enumValue("IF", "IF_TIME") ?: 1
    private val ifNotTime = L.enumValue("IF", "IF_NOT_TIME") ?: 2
    private val ifFriendship = L.enumValue("IF", "IF_MIN_FRIENDSHIP") ?: 3
    private val ifTradePartner = L.enumValue("IF", "IF_TRADE_PARTNER_SPECIES") ?: 20
    private val evoLevel = L.enumValue("EVO", "EVO_LEVEL") ?: 1
    private val evoItem = L.enumValue("EVO", "EVO_ITEM") ?: 3
    private val evoTrade = L.enumValue("EVO", "EVO_TRADE") ?: 2
    private val timeNight = L.enumValue("TIME", "TIME_NIGHT") ?: 3

    private fun evoLine(from: Mon, e: Evo, how: String) = fmt("%-15s -> %-15s %s", from.displayName, nm(e.target), how)

    private val ifHoldItem = L.enumValue("IF", "IF_HOLD_ITEM")

    /**
     * No evolution asks for a trade, in every mode and pool (Blake, 2026-10-06: "trades are by level up using the
     * emerald natl dex rules"). Emerald Nat. Dex 1.2.1's own evolution table has no trade method at all (the hack made
     * each one an item used like a stone: a Linking Cord, or the item the trade asked to hold), and the Nat. Dex fork
     * keeps an evolution's method when it changes the target (AbstractRomHandler.randomizeEvolutions), so no Nat. Dex
     * seed has one. Heart & Soul's table still has EVO_TRADE, listed first beside its own way (Kadabra: trade, or level
     * 42), and randomizeEvolutions keeps the first per target, which left about eleven species a seed that could only
     * evolve by trade. Now:
     * - A trade evolution whose target the species also reaches another way (Heart & Soul's own, untouched data) is
     *   dropped; that other way stays.
     * - Any other (the one a randomized species kept) takes Gen3RomHandler.removeImpossibleEvolutions's numbers, the
     *   rule the vanilla IronMON settings files turn on: a trade (or a trade for a given partner, Karrablast and
     *   Shelmet) at level 37; a trade holding an item at level 30, except Poliwhirl level 37, Seadra level 40, Slowpoke
     *   a Water Stone, Clamperl level 30 with the Deep Sea Tooth and a Water Stone with the Deep Sea Scale.
     * Logged under --Removing Impossible Evolutions-- in the fork's words.
     */
    private fun removeImpossibleEvolutions(): List<String> {
        val lines = ArrayList<String>()
        val water = item("WATER_STONE")
        val tooth = item("DEEP_SEA_TOOTH")
        val scale = item("DEEP_SEA_SCALE")
        for (m in mons) {
            if (m == null) continue
            if (m.evos.none { it.method == evoTrade }) continue
            val others = m.evos.filter { it.method != evoTrade }.map { it.target }.toSet()
            if (m.evos.removeAll { it.method == evoTrade && it.target in others }) m.evosChanged = true
            for (e in m.evos) {
                if (e.method != evoTrade) continue
                val held = if (ifHoldItem == null) null else e.conds.firstOrNull { it[0] == ifHoldItem }?.get(1)
                val stone: Int?
                val level: Int
                when {
                    held == null -> { stone = null; level = 37 }
                    m.natDex == 61 -> { stone = null; level = 37 }                    // Poliwhirl
                    m.natDex == 79 && water != null -> { stone = water; level = 0 }    // Slowpoke
                    m.natDex == 117 -> { stone = null; level = 40 }                   // Seadra
                    m.natDex == 366 && held == scale && water != null -> { stone = water; level = 0 }
                    m.natDex == 366 && held == tooth -> { stone = null; level = 30 }
                    else -> { stone = null; level = 30 }                              // Onix, Scyther, Porygon and the rest
                }
                if (stone != null) { e.method = evoItem; e.param = stone } else { e.method = evoLevel; e.param = level }
                // Conditions read to their end are rewritten without the trade's (HnsGame.writeEvos keeps the rest).
                if (e.paramsOk) { e.conds.removeAll { it[0] == ifTradePartner || it[0] == ifHoldItem }; e.condsChanged = true }
                m.evosChanged = true
                if (m.enabled) lines += evoLine(m, e, if (stone != null) "using a ${game.itemName(stone)}" else "at level $level")
            }
        }
        return lines
    }

    /** condenseLevelEvolutions(40, 30) and Gen3RomHandler.makeEvolutionsEasier's 160 happiness, as data. */
    private fun makeEvolutionsEasier(): List<String> {
        val lines = ArrayList<String>()
        for (m in mons) {
            if (m == null || !m.enabled) continue
            for (e in m.evos) {
                if (e.method == evoLevel && e.param > 0) {
                    val intermediate = mons.getOrNull(e.target)?.evos?.isNotEmpty() == true
                    val cap = if (intermediate) 30 else 40
                    if (e.param > cap) { e.param = cap; m.evosChanged = true; lines += evoLine(m, e, "at level $cap") }
                }
                if (e.paramsOk) for (c in e.conds) if (c[0] == ifFriendship && c[1] > 160) { c[1] = 160; e.condsChanged = true; m.evosChanged = true }
            }
        }
        return lines
    }

    /**
     * Gen3RomHandler.removeTimeBasedEvolutions on HnS data: a time of day condition is dropped. Where that leaves two
     * evolutions of one species asking the same thing (Eevee's Espeon and Umbreon), the later one becomes a stone
     * evolution as UPR does it: night a Moon Stone, day a Sun Stone.
     */
    private fun removeTimeBasedEvolutions(): List<String> {
        val lines = ArrayList<String>()
        val sun = item("SUN_STONE") ?: 0
        val moon = item("MOON_STONE") ?: 0
        for (m in mons) {
            if (m == null || !m.enabled) continue
            for ((k, e) in m.evos.withIndex()) {
                if (!e.paramsOk) continue
                val time = e.conds.firstOrNull { it[0] == ifTime || it[0] == ifNotTime } ?: continue
                val night = time[0] == ifTime && time[1] == timeNight
                e.conds.removeAll { it[0] == ifTime || it[0] == ifNotTime }
                e.condsChanged = true; m.evosChanged = true
                val clash = m.evos.subList(0, k).any { p -> p.method == e.method && p.param == e.param && sameConds(p, e) }
                if (clash && sun != 0 && moon != 0) {
                    e.method = evoItem; e.param = if (night) moon else sun; e.conds.clear()
                    lines += evoLine(m, e, "using a ${game.itemName(e.param)}")
                } else lines += evoLine(m, e, "at any time of day")
            }
        }
        return lines
    }

    private fun sameConds(a: Evo, b: Evo): Boolean =
        a.conds.size == b.conds.size && a.conds.zip(b.conds).all { (x, y) -> x.contentEquals(y) }

    // ------------------------------------------------------------------------------------------------ starters

    val starters = ArrayList<Mon>()
    private val starterTable = if (L.hasSym("sStarterMon")) L.sym("sStarterMon") else 0
    private val origStarters: List<Int> = (0 until 3).map { if (starterTable != 0) rom.u16(starterTable + 2 * it) else 0 }

    /** AbstractRomHandler.randomizeStarters / randomizeBasicTwoEvosStarters, under KaizoCore's BST line for the mode. */
    private fun randomizeStarters() {
        if (o.startersMod == "UNCHANGED" || starterTable == 0) return
        val under = { m: Mon -> bstLine == null || m.bst < bstLine }
        val candidates: List<Mon> = when (o.startersMod) {
            "RANDOM_WITH_TWO_EVOLUTIONS" -> {
                val to = evosTo()
                mainList.filter { pk -> to[pk.id].orEmpty().none { it.eligible } && evosFrom(pk).any { evosFrom(it).isNotEmpty() } }
            }
            "CUSTOM" -> emptyList()
            else -> mainList
        }.filter(under)
        if (o.startersMod == "CUSTOM") {
            // UPR stores a custom starter as its dex position plus one; HnS has no such list, so the game's own stay.
            notes += "Custom starters were not applied: pick them in Build your own once Heart & Soul has it."
            return
        }
        for (i in 0 until 3) {
            var pk = weighted(candidates)
            while (pk in starters) pk = weighted(candidates)
            starters += pk
        }
        for (i in 0 until 3) rom.w16(starterTable + 2 * i, starters[i].id)
        // The lab's choice scripts (setvar PLAYER_STARTER_SPECIES) by the species they gave.
        for (m in L.scriptMons) {
            if (m.kind != "setvar-species" || m.usedBy.none { it.contains("GiveStarter") }) continue
            val v = m.operands["value"] ?: continue
            val idx = origStarters.indexOf(v.value)
            if (idx >= 0) rom.w16(v.addr, starters[idx].id)
        }
        log(when (o.startersMod) { "RANDOM_WITH_TWO_EVOLUTIONS" -> "--Random 2-Evolution Starters--"; else -> "--Random Starters--" })
        starters.forEachIndexed { i, s -> log("Set starter ${i + 1} to ${s.displayName}") }
        log()
    }

    // ------------------------------------------------------------------------------------------------ movesets

    private val rules = HnsUprRules(o.zxRules)
    private val moveConsts: Map<Int, Set<String>> = L.enum("MOVE").entries.groupBy({ it.value }, { it.key.removePrefix("MOVE_") }).mapValues { it.value.toSet() }

    /**
     * The moves a random pick may land on: real moves of this build (not Z or Max moves, not Struggle, not ones HnS's own
     * randomizer refuses), less those the settings file's UPR fork never rolls (HnsUprRules).
     */
    private val validMoves: List<HnsGame.MoveData> by lazy {
        game.moves.filterNotNull().filter { mv ->
            val names = moveConsts[mv.id].orEmpty()
            mv.id in 1 until game.movesCount && mv.id != struggle && !mv.randomizerInvalid && mv.name.isNotEmpty() &&
                names.none { it in rules.bannedMoves } && (rules.onlyMoves == null || names.any { it in rules.onlyMoves })
        }
    }

    private val badDamaging: Set<Int> by lazy {
        moveConsts.filter { (_, names) -> names.any { it in rules.badDamagingMoves } }.keys
    }

    /** Move.isGoodDamaging with HnS's never-miss accuracy of 0 as the perfect accuracy. */
    private fun goodDamaging(mv: HnsGame.MoveData): Boolean {
        if (mv.category == 2 || mv.id in badDamaging) return false
        val p = mv.power * hitCount(mv)
        return p >= 100 || (p >= 50 && (mv.accuracy >= 90 || mv.accuracy == 0))
    }

    /**
     * AbstractRomHandler.randomizeMovesLearnt. No HM move is ever rolled into a learnset, evolution moves included, so
     * no Pokemon learns one by level or knows one when met (Blake, 2026-10-05: "starters can't know hm moves", "can't
     * learn hm moves either"). engine-zx's createSetsOfMoves bans getHMMoves(); engine-natdex has that line commented
     * out; both pools follow the vanilla rule here.
     */
    private fun randomizeMovesets() {
        val typed = o.movesetsMod == "RANDOM_PREFER_SAME_TYPE"
        val validMoves = validMoves.filter { it.id !in hmMoves }
        val damaging = validMoves.filter { goodDamaging(it) }
        val byType = validMoves.groupBy { it.type }
        val damagingByType = damaging.groupBy { it.type }
        val goodPct = if (o.movesetsForceGoodDamaging) o.movesetsGoodDamagingPercent / 100.0 else 0.0
        val done = HashMap<Int, MutableList<LevelMove>>()
        for (m in traitMons) {
            // Species that share one learnset in the build (a form and its base) share the new one too.
            val shared = if (m.learnsetPtr != 0) done[m.learnsetPtr] else null
            if (shared != null) { m.learnset = shared.map { LevelMove(it.level, it.move) }.toMutableList(); m.learnsetChanged = true; continue }
            run {
                val moves = m.learnset.map { LevelMove(it.level, it.move) }.toMutableList()
                // Expansion learnsets list their evolution moves (level 0) first, sometimes several. UPR's lists have at
                // most one, so its index arithmetic is redone here around however many lead the list.
                var start = 0
                while (start < moves.size && moves[start].level == 0) start++
                if (o.startWithGuaranteedMoves) {
                    val lv1 = moves.count { it.level == 1 }
                    repeat(maxOf(0, o.guaranteedMoveCount - lv1)) { moves.add(start, LevelMove(1, 0)) }
                }
                if (o.evolutionMovesForAll && start == 0) { moves.add(0, LevelMove(0, 0)); start = 1 }
                if (moves.size == start) moves.add(LevelMove(1, 0))
                // The game reads at most MAX_LEVEL_UP_MOVES (40) entries in places (move_relearner.c, pokemon.c).
                while (moves.size > game.maxLevelUpMoves) moves.removeAt(moves.size - 1)
                var lv1index = start
                while (lv1index < moves.size && moves[lv1index].level == 1) lv1index++
                lv1index = (lv1index - 1).coerceIn(minOf(start, moves.size - 1), moves.size - 1)
                var goodLeft = Math.round(goodPct * moves.size).toInt()
                val atkRatio = m.stats[1].toDouble() / (m.stats[1] + m.stats[4]).coerceAtLeast(1)
                val learnt = ArrayList<Int>()
                var lv1Attack = 0
                for (i in moves.indices) {
                    val attemptDamaging = i == lv1index || goodLeft > 0
                    var typeOfMove: Int? = null
                    if (typed) {
                        val r = random.nextDouble()
                        val t0 = m.types[0]; val t1 = if (m.types[1] == m.types[0]) null else m.types[1]
                        val normal = L.enumValue("TYPE", "TYPE_NORMAL")
                        typeOfMove = when {
                            (t0 == normal && t1 != null) || t1 == normal -> { val other = if (t0 == normal) t1 else t0; if (r < 0.1) normal else if (r < 0.4) other else null }
                            t1 != null -> if (r < 0.2) t0 else if (r < 0.4) t1 else null
                            else -> if (r < 0.4) t0 else null
                        }
                    }
                    var pick: List<HnsGame.MoveData> = validMoves
                    if (attemptDamaging) {
                        if (typeOfMove != null && damagingByType[typeOfMove]?.any { it.id !in learnt } == true) pick = damagingByType[typeOfMove]!!
                        else if (damaging.any { it.id !in learnt }) pick = damaging
                        val cat = if (random.nextDouble() < atkRatio) 0 else 1
                        val filtered = pick.filter { it.category == cat }
                        if (filtered.any { it.id !in learnt }) pick = filtered
                    } else if (typeOfMove != null && byType[typeOfMove]?.any { it.id !in learnt } == true) pick = byType[typeOfMove]!!
                    var mv = pick[random.nextInt(pick.size)]
                    while (mv.id in learnt) mv = pick[random.nextInt(pick.size)]
                    if (i == lv1index) lv1Attack = mv.id else goodLeft--
                    learnt.add(mv.id)
                }
                learnt.shuffle(random)
                if (learnt[lv1index] != lv1Attack) {
                    val j = learnt.indexOf(lv1Attack)
                    if (j >= 0) { learnt[j] = learnt[lv1index]; learnt[lv1index] = lv1Attack }
                }
                for (i in learnt.indices) { moves[i].move = learnt[i]; if (i == lv1index) moves[i].level = 1 }
                m.learnset = moves
                m.learnsetChanged = true
                if (m.learnsetPtr != 0) done[m.learnsetPtr] = moves
            }
        }
        for (m in mons) {
            if (m == null || m.cosmeticOf == 0) continue
            val b = mons[m.cosmeticOf]!!
            if (b.learnsetChanged) { m.learnset = b.learnset.map { LevelMove(it.level, it.move) }.toMutableList(); m.learnsetChanged = true }
        }
    }

    /** AbstractRomHandler.randomizeEggMoves: as many egg moves as before, each a random valid move. */
    private fun randomizeEggMoves() {
        val done = HashMap<Int, MutableList<Int>>()
        for (m in traitMons) {
            if (m.eggMoves.isEmpty()) continue
            val shared = if (m.eggPtr != 0) done[m.eggPtr] else null
            if (shared != null) { m.eggMoves = shared.toMutableList(); m.eggChanged = true; continue }
            val picked = ArrayList<Int>()
            repeat(m.eggMoves.size.coerceAtMost(validMoves.size)) {
                var mv = validMoves[random.nextInt(validMoves.size)].id
                while (mv in picked) mv = validMoves[random.nextInt(validMoves.size)].id
                picked += mv
            }
            m.eggMoves = picked; m.eggChanged = true
            if (m.eggPtr != 0) done[m.eggPtr] = picked
        }
    }

    private fun logMovesets() {
        log("--Pokemon Movesets--")
        val blocks = ArrayList<String>()
        var i = 1
        for (m in mons) {
            if (m == null || !m.enabled || m.cosmeticOf != 0 || m.id == game.speciesEgg) continue
            val evo = m.evos.firstOrNull()?.let { " -> " + nm(it.target) } ?: " (no evolution)"
            val sb = StringBuilder()
            sb.append(fmt("%03d %s", i, m.displayName)).append(evo).append('\n')
            sb.append(fmt("HP  %-3d", m.stats[0])).append('\n').append(fmt("ATK %-3d", m.stats[1])).append('\n')
            sb.append(fmt("DEF %-3d", m.stats[2])).append('\n').append(fmt("SPA %-3d", m.stats[4])).append('\n')
            sb.append(fmt("SPD %-3d", m.stats[5])).append('\n').append(fmt("SPE %-3d", m.stats[3])).append('\n')
            i++
            for (lm in m.learnset) sb.append("Level ").append(fmt("%-2d", lm.level)).append(": ").append(game.moveName(lm.move)).append('\n')
            if (m.eggMoves.isNotEmpty()) {
                sb.append("Egg Moves:").append('\n')
                for (mv in m.eggMoves) sb.append(" - ").append(game.moveName(mv)).append('\n')
            }
            blocks += sb.toString()
        }
        blocks.sort()
        for (b in blocks) log(b)
        log()
    }

    // ------------------------------------------------------------------------------------------------ TMs and tutors

    private var tmTablesWritten = 0

    /** AbstractRomHandler.randomizeTMMoves: TM moves only; the HMs stay. */
    private fun randomizeTMs() {
        val usable = validMoves.toMutableList()
        val picked = ArrayList<Int>()
        repeat(game.tmCount) {
            val mv = usable[random.nextInt(usable.size)]
            picked += mv.id; usable.remove(mv)
        }
        picked.shuffle(random)
        for (i in 0 until game.tmCount) game.machineMoves[i] = picked[i]
    }

    private fun logTMs() {
        log("--TM Moves--")
        for (i in 0 until game.tmCount) log(fmt("TM%02d %s", i + 1, game.moveName(game.machineMoves[i])))
        log()
    }

    /**
     * The new TM moves go where the game reads them: gTMHMItemMoveIds, and the lookup tables the compiler made of
     * GetItemTMHMMoveId's switch (include/item.h; three in this build, found as the exact run of the old TM moves). A
     * build whose tables cannot be found keeps its TMs, and the log says so.
     */
    private fun writeMachines() {
        if (game.machineMoves.contentEquals(game.origMachineMoves)) return
        val key = L.struct("TmHmIndexKey")
        for (i in 0 until game.tmCount) rom.set(key.f("moveId"), L.rec("gTMHMItemMoveIds", i + 1), game.machineMoves[i])
        val old = ByteArray(game.tmCount * 2).also { b -> for (i in 0 until game.tmCount) { b[2 * i] = game.origMachineMoves[i].toByte(); b[2 * i + 1] = (game.origMachineMoves[i] shr 8).toByte() } }
        val hits = ArrayList<Int>()
        val bytes = rom.bytes
        var o = 0
        outer@ while (o + old.size <= bytes.size) {
            for (k in old.indices) if (bytes[o + k] != old[k]) { o++; continue@outer }
            hits += o; o += old.size
        }
        for (h in hits) for (i in 0 until game.tmCount) rom.w16(L.romBase + h + 2 * i, game.machineMoves[i])
        tmTablesWritten = hits.size
        if (hits.isEmpty()) notes += "The TMs' item code was not found in this build, so a TM may teach its old move."
    }

    /** Every species' TM/HM flags, by TM number as UPR keeps them, read from its teachable list. */
    private fun tmCompat() {
        val n = game.machineMoves.size
        val origMachine = game.origMachineMoves
        val flags = HashMap<Int, BooleanArray>()
        for (m in traitMons) {
            val have = m.teachable.toHashSet()
            flags[m.id] = BooleanArray(n) { origMachine[it] in have }
        }
        val mode = o.tmCompatMod
        val early = setOf(move("CUT"), move("ROCK_SMASH")).filterNotNull().toSet()
        if (mode == "COMPLETELY_RANDOM" || mode == "RANDOM_PREFER_TYPE") {
            for (m in traitMons) {
                val f = flags[m.id]!!
                for (i in 0 until n) {
                    val mv = game.moves[game.machineMoves[i]]!!
                    var p = 0.5
                    if (mode == "RANDOM_PREFER_TYPE") p = if (m.hasType(mv.type)) 0.9 else if (mv.type == L.enumValue("TYPE", "TYPE_NORMAL")) 0.5 else 0.25
                    if (i >= game.tmCount && game.machineMoves[i] in early) p = minOf(1.0, p * 1.8)
                    f[i] = random.nextDouble() < p
                }
            }
        } else if (mode == "FULL") flags.values.forEach { it.fill(true) }
        if (o.fullHMCompat) flags.values.forEach { for (i in game.tmCount until n) it[i] = true }
        val changed = mode != "UNCHANGED" || o.fullHMCompat || o.tmsMod == "RANDOM"
        if (!changed) return
        // The new teachable list: the machine moves it now has, then the tutor moves it had.
        val machineSet = origMachine.toHashSet()
        for (m in traitMons) {
            val f = flags[m.id]!!
            val tutors = m.teachable.filter { it !in machineSet }
            val list = LinkedHashSet<Int>()
            for (i in 0 until n) if (f[i]) list += game.machineMoves[i]
            list += tutors
            m.teachable = list.toMutableList(); m.teachableChanged = true
        }
        copyCosmeticLists()
        log("--TM Compatibility--")
        for (m in mons) {
            if (m == null || !m.enabled || m.cosmeticOf != 0 || m.id == game.speciesEgg) continue
            val f = flags[m.id] ?: continue
            val sb = StringBuilder(fmt("%3d %-14s", m.id, m.displayName + " "))
            for (i in 0 until n) {
                val name = game.moveName(game.machineMoves[i]).ifEmpty { "(BLANK)" }
                val len = name.length
                if (f[i]) {
                    sb.append(if (i < game.tmCount) fmt("|TM%02d %" + len + "s ", i + 1, name) else fmt("|HM%02d %" + len + "s ", i + 1 - game.tmCount, name))
                } else sb.append(fmt("| %" + (len + 4) + "s ", "-"))
            }
            sb.append("|")
            log(sb.toString())
        }
        log("")
    }

    private fun copyCosmeticLists() {
        for (m in mons) {
            if (m == null || m.cosmeticOf == 0) continue
            val b = mons[m.cosmeticOf]!!
            if (b.teachableChanged) { m.teachable = b.teachable.toMutableList(); m.teachableChanged = true }
        }
    }

    /**
     * Move tutor compatibility on the teachable lists' tutor moves (every move in them that no TM or HM teaches). The
     * tutors' own moves are in event scripts the layout does not export, so "random tutor moves" is said in the notes.
     */
    private fun tutorCompat() {
        if (o.tutorMovesMod == "RANDOM") notes += "Move tutor moves were not randomized: the tutors' moves are event script data the layout does not export yet."
        val mode = o.tutorCompatMod
        if (mode == "UNCHANGED") return
        val machineSet = (game.origMachineMoves.toList() + game.machineMoves.toList()).toHashSet()
        val tutorMoves = traitMons.flatMap { m -> m.teachable.filter { it !in machineSet } }.toSortedSet().toList()
        if (tutorMoves.isEmpty()) return
        for (m in traitMons) {
            val machine = m.teachable.filter { it in machineSet }
            val tutors = when (mode) {
                "FULL" -> tutorMoves
                "RANDOM_PREFER_TYPE" -> tutorMoves.filter { mv ->
                    val t = game.moves[mv]!!.type
                    random.nextDouble() < (if (m.hasType(t)) 0.9 else if (t == L.enumValue("TYPE", "TYPE_NORMAL")) 0.5 else 0.25)
                }
                else -> tutorMoves.filter { random.nextDouble() < 0.5 }
            }
            m.teachable = (machine + tutors).distinct().toMutableList(); m.teachableChanged = true
        }
        copyCosmeticLists()
    }

    // ------------------------------------------------------------------------------------------------ trainers

    private val classIds = L.enum("TRAINER_CLASS")
    private fun classIs(t: HnsGame.TrainerData, vararg names: String) = names.any { classIds["TRAINER_CLASS_$it"] == t.trainerClass }

    /** UPR's trainer tags, from HnS's classes and trainer constants (Trainer.isBoss / isImportant / skipImportant). */
    private fun tag(t: HnsGame.TrainerData): String {
        val rival = Regex("TRAINER_RIVAL_([A-Z]+)_(\\d+)_HNS").matchEntire(t.constName)
        return when {
            rival != null -> {
                val idx = origStarters.indexOf(L.enumValue("SPECIES", "SPECIES_" + rival.groupValues[1]) ?: -1)
                "RIVAL${rival.groupValues[2]}-${if (idx < 0) 0 else idx}"
            }
            classIs(t, "LEADER_HNS", "LEADER_KANTO_HNS") -> "GYM-LEADER"
            classIs(t, "ELITE_FOUR_HNS") -> "ELITE"
            classIs(t, "CHAMPION_HNS") -> "CHAMPION"
            t.constName.startsWith("TRAINER_RED_") -> "UBER"
            else -> ""
        }
    }

    private fun isBoss(t: HnsGame.TrainerData) = t.tag.startsWith("ELITE") || t.tag.startsWith("CHAMPION") || t.tag.startsWith("UBER") || t.tag.endsWith("LEADER")
    private fun isImportant(t: HnsGame.TrainerData) = t.tag.startsWith("RIVAL")
    private fun skipImportant(t: HnsGame.TrainerData) = t.tag.startsWith("RIVAL1-")

    private fun copyMon(src: HnsGame.TrainerMonData): HnsGame.TrainerMonData {
        val c = HnsGame.TrainerMonData(src.raw.copyOf())
        c.species = src.species; c.level = src.level; c.heldItem = 0; c.shiny = src.shiny
        c.origSpecies = src.origSpecies; c.origLevel = src.origLevel
        return c
    }

    private fun trainers() {
        for (t in game.trainers) t.tag = tag(t)
        val changed = o.trainersMod != "UNCHANGED" || o.trainersLevelModified || o.additionalBossPokemon + o.additionalImportantPokemon +
            o.additionalRegularPokemon > 0 || o.doubleBattleMode || o.randomizeTrainerNames || o.randomizeTrainerClassNames
        if (!changed) { log("Trainers: Unchanged.\n"); return }
        // 1. Added Pokemon (AbstractRomHandler.addTrainerPokemon).
        for (t in game.trainers) {
            if (t.poolSize != 0) continue
            val add = when {
                isBoss(t) -> o.additionalBossPokemon
                isImportant(t) -> if (skipImportant(t)) 0 else o.additionalImportantPokemon
                else -> o.additionalRegularPokemon
            }
            if (add == 0) continue
            val lowest = t.mons.minOf { it.level }
            val potential = t.mons.filter { it.level == lowest }
            for (i in 0 until add) {
                if (t.mons.size >= 6) break
                t.mons.add(t.mons.size - 1, copyMon(potential[i % potential.size]))
                t.partyGrew = true
            }
        }
        // 2. Double battles (doubleBattleMode and Gen3RomHandler.setTrainers): one Pokemon becomes two, and the trainer
        // battles in doubles; the first rival battle stays single as UPR's skipImportant has it.
        if (o.doubleBattleMode) for (t in game.trainers) {
            if (skipImportant(t) || t.poolSize != 0) continue
            if (t.mons.size == 1) { t.mons.add(copyMon(t.mons[0])); t.partyGrew = true }
            t.doubleSet = true
        }
        // 3. Smart AI (Super Kaizo's "swap mega evos" box is UPR Gen 3's smartAiMode: AI flags | 7).
        if (o.smartAi) {
            if (L.struct("Trainer").fields["aiFlags"] != null) game.trainers.forEach { it.smartAi = true }
            else notes += "Smarter trainer AI was not set: Trainer.aiFlags is not in the layout data yet."
        }
        if (o.trainersMod != "UNCHANGED") randomizeTrainerPokes()
        else if (o.trainersLevelModified) for (t in game.trainers) for (tm in t.mons) tm.level = modLevel(tm.level, o.trainersLevelModifier)
        if ((o.trainersMod != "UNCHANGED" || o.startersMod != "UNCHANGED") && o.rivalCarriesStarter && starters.size == 3) rivalCarriesStarter()
        if (o.trainersForceFullyEvolved) forceFullyEvolved()
        if (o.heldItemsBoss || o.heldItemsImportant || o.heldItemsRegular) trainerHeldItems()
        if (o.randomizeTrainerClassNames) randomizeClassNames()
        if (o.randomizeTrainerNames) randomizeTrainerNames()
        logTrainers()
    }

    private fun modLevel(level: Int, mod: Int) = minOf(100, Math.round(level * (1 + mod / 100.0)).toInt())

    private fun hasWonderGuard(m: Mon) = m.abilities.contains(wonderGuard)

    /** AbstractRomHandler.randomizeTrainerPokes for RANDOM, with its Elite Four unique Pokemon and similar strength. */
    private fun randomizeTrainerPokes() {
        val allList = (if (o.trainersBlockLegendaries) mainList.filter { !it.isLegendary } else mainList)
        val scrambled = game.trainers.toMutableList()
        scrambled.shuffle(random)
        val eliteFour = game.trainers.filter { it.tag == "ELITE" || it.tag == "CHAMPION" }.toSet()
        if (o.eliteFourUniquePokemon > 0) scrambled.sortBy { if (it in eliteFour) 0 else 1 }
        val uniqueUsed = HashSet<Int>()
        for (t in scrambled) {
            for (tm in t.mons) tm.level = if (o.trainersLevelModified) modLevel(tm.level, o.trainersLevelModifier) else tm.level
            val order = if (o.eliteFourUniquePokemon > 0 && t in eliteFour) t.mons.sortedByDescending { it.level } else t.mons
            for ((rank, tm) in order.withIndex()) {
                val wgAllowed = !o.trainersBlockEarlyWonderGuard || tm.level >= 20
                val unique = o.eliteFourUniquePokemon > 0 && t in eliteFour && rank < o.eliteFourUniquePokemon
                val willEvolve = o.trainersForceFullyEvolved && tm.level >= o.trainersForceFullyEvolvedLevel
                var pickFrom = allList.filter { it.id !in uniqueUsed && (wgAllowed || !hasWonderGuard(it)) }
                if (unique && willEvolve) pickFrom = pickFrom.filter { evosFrom(it).isEmpty() }
                if (pickFrom.isEmpty()) pickFrom = allList
                val old = mons[tm.species]
                val newPk = if (o.trainersSimilarStrength && old != null) trainerPowerReplacement(pickFrom, old) else weighted(pickFrom)
                if (unique) uniqueUsed += newPk.id
                tm.species = newPk.id
                tm.reset = true
                if (o.shinyChance) tm.shiny = random.nextInt(256) == 0
            }
        }
    }

    /** pickTrainerPokeReplacement's similar strength: within 10% of the old BST, widened 5% a round until three fit. */
    private fun trainerPowerReplacement(list: List<Mon>, current: Mon): Mon {
        val bst = current.origBst
        var min = bst - bst / 10; var max = bst + bst / 10
        var can = list.filter { it.origBst in min..max }
        var rounds = 0
        while (can.isEmpty() || (can.size < 3 && rounds < 2)) {
            min -= bst / 20; max += bst / 20; rounds++
            can = list.filter { it.origBst in min..max }
            if (rounds > 60) { can = list; break }
        }
        return can[random.nextInt(can.size)]
    }

    private fun numEvolutions(m: Mon, depth: Int): Int {
        if (depth == 0) return 0
        val next = evosFrom(m)
        if (next.isEmpty()) return 0
        return 1 + next.maxOf { numEvolutions(it, depth - 1) }
    }

    private fun randomEvolutionOf(m: Mon, mustEvolveItself: Boolean): Mon {
        val c = evosFrom(m).filter { !mustEvolveItself || evosFrom(it).isNotEmpty() }
        return if (c.isEmpty()) m else weighted(c)
    }

    /** The slot carrying the starter in a rival party: the one whose original species was of that starter's line. */
    private fun carriedSlot(t: HnsGame.TrainerData, starterIndex: Int): Int {
        val origStarter = origStarters.getOrNull(starterIndex) ?: return -1
        val family = origFamily[origStarter] ?: setOf(origStarter)
        val slots = t.mons.withIndex().filter { it.value.origSpecies in family }
        if (slots.isEmpty()) {
            // UPR's getCarriedStarterIndex: the highest level, the last Pokemon counting two levels more.
            var best = 0
            for (i in 1 until t.mons.size) {
                val bonus = if (i == t.mons.size - 1) 2 else 0
                if (t.mons[i].level + bonus > t.mons[best].level) best = i
            }
            return best
        }
        return slots.maxByOrNull { it.value.origLevel * 10 + it.index }!!.index
    }

    /** Each starter's vanilla evolution line, read once before anything changed. */
    private val origFamily: Map<Int, Set<Int>> = run {
        val map = HashMap<Int, Set<Int>>()
        for (s in origStarters) {
            val fam = HashSet<Int>()
            fun add(id: Int) { if (fam.add(id)) game.mons.getOrNull(id)?.evos?.forEach { add(it.target) } }
            add(s)
            map[s] = fam
        }
        map
    }

    /** AbstractRomHandler.rivalCarriesStarterUpdate: the rival named for a starter carries the new one, evolving by level. */
    private fun rivalCarriesStarter() {
        val byIdx = game.trainers.filter { it.tag.startsWith("RIVAL") }.groupBy { it.tag.substringAfter('-').toInt() }
        for ((idx, list) in byIdx) {
            val battles = list.sortedBy { it.tag.substringAfter("RIVAL").substringBefore('-').toInt() }
            var starter = starters[idx]
            val times = numEvolutions(starter, 2)
            fun levelOf(t: HnsGame.TrainerData) = t.mons.getOrNull(carriedSlot(t, idx))?.level ?: 0
            var j = 0
            fun setFor(t: HnsGame.TrainerData, m: Mon) {
                val slot = carriedSlot(t, idx)
                if (slot >= 0) { t.mons[slot].species = m.id; t.mons[slot].reset = true; t.mons[slot].shiny = false }
                t.carriedSlot = slot
            }
            when (times) {
                0 -> battles.forEach { setFor(it, starter) }
                1 -> {
                    while (j < battles.size / 2 && levelOf(battles[j]) < 30) setFor(battles[j++], starter)
                    starter = randomEvolutionOf(starter, false)
                    while (j < battles.size) setFor(battles[j++], starter)
                }
                else -> {
                    while (j < battles.size && levelOf(battles[j]) < 16) setFor(battles[j++], starter)
                    starter = randomEvolutionOf(starter, true)
                    while (j < battles.size && levelOf(battles[j]) < 36) setFor(battles[j++], starter)
                    starter = randomEvolutionOf(starter, false)
                    while (j < battles.size) setFor(battles[j++], starter)
                }
            }
        }
    }

    private var fullyEvolvedSeed = -1

    /** AbstractRomHandler.fullyEvolve: down the line, a split chosen by the run's seed and the trainer's number. */
    private fun fullyEvolve(m: Mon, trainerIndex: Int): Mon {
        if (fullyEvolvedSeed == -1) fullyEvolvedSeed = random.nextInt(8)
        var p = m
        val seen = hashSetOf(p.id)
        while (true) {
            val next = evosFrom(p)
            if (next.isEmpty() || next.any { it.id in seen }) break
            p = next[(fullyEvolvedSeed + trainerIndex) % next.size]
            seen += p.id
        }
        return p
    }

    /** AbstractRomHandler.forceFullyEvolvedTrainerPokes: at the level, a Pokemon that can still evolve becomes a random final one. */
    private fun forceFullyEvolved() {
        val finals = mainList.filter { evosFrom(it).isEmpty() }.ifEmpty { mainList }
        for (t in game.trainers) {
            val carried = if (o.rivalCarriesStarter && t.tag.startsWith("RIVAL")) t.carriedSlot else -1
            for ((k, tm) in t.mons.withIndex()) {
                if (tm.level < o.trainersForceFullyEvolvedLevel) continue
                val m = mons[tm.species] ?: continue
                if (evosFrom(m).isEmpty()) continue
                tm.species = if (k == carried) fullyEvolve(m, t.id).id else weighted(finals).id
                tm.reset = true
            }
        }
    }

    // Gen3Constants' held item lists, by item constant.
    private fun items(vararg n: String) = n.mapNotNull { item(it) }.filter { inPool(it) }
    private val consumableHeld by lazy {
        items("CHERI_BERRY", "CHESTO_BERRY", "PECHA_BERRY", "RAWST_BERRY", "RAWST_BERRY", "LEPPA_BERRY", "ORAN_BERRY", "PERSIM_BERRY",
            "LUM_BERRY", "SITRUS_BERRY", "FIGY_BERRY", "WIKI_BERRY", "MAGO_BERRY", "AGUAV_BERRY", "IAPAPA_BERRY", "LIECHI_BERRY",
            "GANLON_BERRY", "SALAC_BERRY", "PETAYA_BERRY", "APICOT_BERRY", "LANSAT_BERRY", "STARF_BERRY", "BERRY_JUICE",
            "WHITE_HERB", "MENTAL_HERB")
    }
    private val allHeld by lazy {
        items("BRIGHT_POWDER", "QUICK_CLAW", "CHOICE_BAND", "KINGS_ROCK", "SILVER_POWDER", "FOCUS_BAND", "SCOPE_LENS", "METAL_COAT",
            "LEFTOVERS", "SOFT_SAND", "HARD_STONE", "MIRACLE_SEED", "BLACK_GLASSES", "BLACK_BELT", "MAGNET", "MYSTIC_WATER",
            "SHARP_BEAK", "POISON_BARB", "NEVER_MELT_ICE", "SPELL_TAG", "TWISTED_SPOON", "CHARCOAL", "DRAGON_FANG", "SILK_SCARF",
            "SHELL_BELL", "SEA_INCENSE", "LAX_INCENSE", "RAZOR_CLAW", "RAZOR_FANG") + consumableHeld
    }
    private val generalConsumable by lazy {
        items("CHERI_BERRY", "CHESTO_BERRY", "PECHA_BERRY", "RAWST_BERRY", "ASPEAR_BERRY", "LEPPA_BERRY", "ORAN_BERRY", "PERSIM_BERRY",
            "LUM_BERRY", "SITRUS_BERRY", "GANLON_BERRY", "SALAC_BERRY", "APICOT_BERRY", "LANSAT_BERRY", "STARF_BERRY", "BERRY_JUICE",
            "WHITE_HERB", "MENTAL_HERB")
    }
    private val generalItems by lazy {
        items("BRIGHT_POWDER", "QUICK_CLAW", "KINGS_ROCK", "FOCUS_BAND", "SCOPE_LENS", "LEFTOVERS", "SHELL_BELL", "LAX_INCENSE",
            "RAZOR_CLAW", "RAZOR_FANG")
    }
    private val typeBoost: Map<Int, List<Int>> by lazy {
        mapOf("BUG" to "SILVER_POWDER", "DARK" to "BLACK_GLASSES", "DRAGON" to "DRAGON_FANG", "ELECTRIC" to "MAGNET",
            "FIGHTING" to "BLACK_BELT", "FIRE" to "CHARCOAL", "FLYING" to "SHARP_BEAK", "GHOST" to "SPELL_TAG",
            "GRASS" to "MIRACLE_SEED", "GROUND" to "SOFT_SAND", "ICE" to "NEVER_MELT_ICE", "NORMAL" to "SILK_SCARF",
            "POISON" to "POISON_BARB", "PSYCHIC" to "TWISTED_SPOON", "ROCK" to "HARD_STONE", "STEEL" to "METAL_COAT",
            "WATER" to "MYSTIC_WATER,SEA_INCENSE", "FAIRY" to "FAIRY_FEATHER")
            .mapNotNull { (t, i) -> val tid = L.enumValue("TYPE", "TYPE_$t"); val l = items(*i.split(',').toTypedArray()); if (tid != null && l.isNotEmpty()) tid to l else null }.toMap()
    }
    /** Gen3Constants.speciesBoostingItems, by National Dex number (a form takes its species' item, as UPR's number does). */
    private val speciesBoost: Map<Int, List<Int>> by lazy {
        mapOf(380 to "SOUL_DEW", 381 to "SOUL_DEW", 366 to "DEEP_SEA_TOOTH,DEEP_SEA_SCALE", 25 to "LIGHT_BALL", 113 to "LUCKY_PUNCH",
            132 to "METAL_POWDER", 104 to "THICK_CLUB", 105 to "THICK_CLUB", 83 to "LEEK")
            .mapValues { (_, i) -> items(*i.split(',').toTypedArray()) }.filterValues { it.isNotEmpty() }
    }

    /** The four moves a trainer Pokemon gets with no moves set: its last four level-up moves at its level. */
    private fun movesAt(m: Mon, level: Int): List<Int> {
        val out = ArrayList<Int>()
        for (lm in m.learnset.asReversed()) if (lm.level in 1..level && lm.move !in out) { out.add(0, lm.move); if (out.size >= 4) break }
        return out
    }

    /** AbstractRomHandler.randomizeTrainerHeldItems with Gen3RomHandler.getSensibleHeldItemsFor, physical by category. */
    private fun trainerHeldItems() {
        for (t in game.trainers) {
            if (isBoss(t) && !o.heldItemsBoss) continue
            if (isImportant(t) && !o.heldItemsImportant) continue
            if (!isBoss(t) && !isImportant(t) && !o.heldItemsRegular) continue
            val targets = if (o.highestLevelGetsItems) listOfNotNull(t.mons.maxByOrNull { it.level }) else t.mons
            for (tm in targets) {
                val m = mons[tm.species] ?: continue
                val list: List<Int> = when {
                    o.sensibleItemsOnly -> {
                        val l = ArrayList(generalConsumable)
                        if (!o.consumableItemsOnly) l += generalItems
                        for (mvId in movesAt(m, tm.level)) {
                            val mv = game.moves.getOrNull(mvId) ?: continue
                            if (mv.power <= 0 || mv.category == 2) continue
                            if (mv.category == 0) { l += items("LIECHI_BERRY"); if (!o.consumableItemsOnly) { typeBoost[mv.type]?.let { l += it }; l += items("CHOICE_BAND") } }
                            else { l += items("PETAYA_BERRY"); if (!o.consumableItemsOnly) typeBoost[mv.type]?.let { l += it } }
                        }
                        // "Increase the likelihood of using species specific items": six times over.
                        if (!o.consumableItemsOnly) speciesBoost[m.natDex]?.let { sp -> repeat(6) { l += sp } }
                        l
                    }
                    o.consumableItemsOnly -> consumableHeld
                    else -> allHeld
                }
                if (list.isNotEmpty()) tm.heldItem = list[random.nextInt(list.size)]
            }
        }
    }

    private fun encodable(s: String, max: Int) = s.length <= max && rom.encodeText(s) != null

    /** AbstractRomHandler.randomizeTrainerClassNames: each class name to one custom name that fits (12 characters). */
    private fun randomizeClassNames() {
        val max = L.struct("TrainerClass").f("name").size - 1
        val singles = o.trainerClassNames.filter { encodable(it, max) }
        val doubles = o.doublesTrainerClassNames.filter { encodable(it, max) }
        val doubleClasses = setOf("TWINS_HNS", "YOUNG_COUPLE_HNS", "TWINS", "YOUNG_COUPLE", "OLD_COUPLE", "SIS_AND_BRO").mapNotNull { classIds["TRAINER_CLASS_$it"] }.toSet()
        val used = game.trainers.map { it.trainerClass }.toSortedSet()
        val translation = HashMap<String, String>()
        for (c in used) {
            val old = game.trainerClassNames[c] ?: continue
            if (old.isEmpty() || old.any { it == '?' }) continue
            val from = if (c in doubleClasses) doubles else singles
            if (from.isEmpty()) continue
            game.trainerClassNames[c] = translation.getOrPut(old) { from[cosmetic.nextInt(from.size)] }
        }
    }

    /** AbstractRomHandler.randomizeTrainerNames: each name to one custom name of ten characters or fewer; GRUNT and its kind each their own. */
    private fun randomizeTrainerNames() {
        val max = L.const("TRAINER_NAME_LENGTH")
        val singles = o.trainerNames.filter { encodable(it, max) }
        val doubles = o.doublesTrainerNames.filter { encodable(it, max) }
        val repeated = setOf("GRUNT", "EXECUTIVE", "SHADOW", "ADMIN", "GOON", "EMPLOYEE")
        val translation = HashMap<String, String>()
        for (t in game.trainers) {
            val n = t.origName
            // A name made of the rival's placeholder or the game's question marks is not a name to change.
            if (n.isBlank() || n.any { it == '?' } || rom.encodeText(n) == null) continue
            val from = if (n.contains("&")) doubles else singles
            if (from.isEmpty()) continue
            t.name = if (n.uppercase() !in repeated && translation.containsKey(n)) translation[n]!!
            else from[cosmetic.nextInt(from.size)].also { translation[n] = it }
        }
    }

    private fun logTrainers() {
        log("--Trainers Pokemon--")
        for (t in game.trainers.sortedBy { it.id }) {
            val orig = (game.origTrainerClassNames[t.trainerClass] ?: "") + " " + t.origName
            val now = (game.trainerClassNames[t.trainerClass] ?: "") + " " + t.name
            val names = if (o.randomizeTrainerNames || o.randomizeTrainerClassNames) "($orig => $now)" else "($now)"
            val party = t.mons.joinToString(", ") { tm ->
                nm(tm.species) + (if (tm.heldItem != 0) "@" + game.itemName(tm.heldItem) else "") + " Lv" + tm.level
            }
            log("#${t.id} $names@${Integer.toHexString(t.addr - L.romBase).uppercase()} - $party")
        }
        log()
    }

    // ------------------------------------------------------------------------------------------------ statics

    /** One static encounter or gift: the addresses that hold its species, and the script lines that name it. */
    private class Static(val label: String, val file: String, val origSpecies: Int, val addrs: MutableList<Int>, val egg: Boolean) {
        var newSpecies = 0
        val refAddrs = ArrayList<Int>()
    }

    private val staticList = ArrayList<Static>()

    private fun collectStatics() {
        val kinds = setOf("givemon", "giveegg", "setwildbattle", "setwildbattleshiny", "setwildbossbattle", "seteventmon")
        val sp = { v: Int -> v in 1 until game.numSpecies && mons[v]?.enabled == true }
        for (m in L.scriptMons) {
            if (m.label.startsWith("Debug_") || m.label.contains("_Test")) continue
            if (m.kind in kinds) {
                val s = m.operands["species"]
                if (s != null && sp(s.value) && m.text.contains("SPECIES_")) staticList += Static(m.label, m.file, s.value, mutableListOf(s.addr), m.kind == "giveegg")
                val s2 = m.operands["species2"]
                if (s2 != null && sp(s2.value)) staticList += Static(m.label, m.file, s2.value, mutableListOf(s2.addr), false)
            } else if (m.kind == "setvar-species" && m.usedBy.isNotEmpty() && m.usedBy.none { it.contains("GiveStarter") }) {
                val v = m.operands["value"] ?: continue
                if (sp(v.value)) staticList += Static(m.label, m.file, v.value, mutableListOf(v.addr), false)
            }
        }
        // The comfort build's tables of species the C code gives (patch 0010): roamers and named gifts.
        for ((sym, label) in listOf("gHnsRoamerSpecies" to "Roamer", "gHnsNamedGiftSpecies" to "Named gift")) {
            if (!L.hasSym(sym)) continue
            val n = L.tables[sym]?.count ?: (L.symbols[sym]!!.second / 2)
            for (i in 0 until n) {
                val a = L.sym(sym) + 2 * i
                val v = rom.u16(a)
                if (sp(v)) staticList += Static("$label ${i + 1}", sym, v, mutableListOf(a), false)
            }
        }
        if (L.hasSym("sOddEggSpecies")) {
            val n = L.tables["sOddEggSpecies"]?.count ?: 0
            for (i in 0 until n) {
                val a = L.sym("sOddEggSpecies") + 2 * i
                val v = rom.u16(a)
                if (sp(v)) staticList += Static("Odd Egg ${i + 1}", "sOddEggSpecies", v, mutableListOf(a), true)
            }
        }
        // Script lines that name the same species beside a static: in its own script, or, for a species only one static
        // of its map gives, anywhere in that map's script file. A Game Corner `case` is how its prize is paid out.
        val cosmeticKinds = setOf("bufferspeciesname", "setvar", "showmonpic", "playmoncry", "case", "removegenericmon")
        val perFile = staticList.groupBy { it.file to it.origSpecies }
        for (r in L.speciesRefs) {
            if (r.kind !in cosmeticKinds) continue
            for ((species, addr) in r.refs) {
                val sameLabel = staticList.firstOrNull { it.label == r.label && it.origSpecies == species }
                val target = sameLabel ?: perFile[r.file to species]?.singleOrNull()
                target?.refAddrs?.add(addr)
            }
        }
    }

    /** AbstractRomHandler.randomizeStaticPokemon, completely random: drawn without repeats from the pool until it runs out. */
    private fun statics() {
        collectStatics()
        if (o.staticMod == "UNCHANGED") { log("Static Pokemon: Unchanged.\n"); staticList.forEach { it.newSpecies = it.origSpecies }; return }
        val poolList = mainList.toMutableList()
        val left = poolList.toMutableList()
        for (s in staticList) {
            val pk = left.removeAt(random.nextInt(left.size))
            s.newSpecies = pk.id
            if (left.isEmpty()) left.addAll(poolList)
        }
        if (o.staticLevelModified) notes += "Static levels were not changed: their level operands vary in size and are left as the game has them."
        log("--Static Pokemon--")
        val seen = HashMap<String, Int>()
        for (s in staticList) {
            val old = nm(s.origSpecies) + if (s.egg) " (egg)" else ""
            val n = (seen[old] ?: 0) + 1
            seen[old] = n
            log(old + (if (n > 1) "($n)" else "") + " => " + nm(s.newSpecies) + if (s.egg) " (egg)" else "")
        }
        log()
        notes += "Static and gift Pokemon from scripts, the Odd Egg, the roamers and the named gifts are randomized; " +
            "checks a script makes of the party (checkspecies, getcaughtmon) still ask for the old species."
    }

    private fun writeStatics() {
        for (s in staticList) {
            if (s.newSpecies == 0 || s.newSpecies == s.origSpecies) continue
            for (a in s.addrs) rom.w16(a, s.newSpecies)
            for (a in s.refAddrs) rom.w16(a, s.newSpecies)
        }
    }

    // ------------------------------------------------------------------------------------------------ wild

    /** UPR's wild pool: legendaries out as wildBSTLimit says, BST at or under the limit, under KaizoCore's wild line. */
    private fun wildPool(): List<Mon> {
        var list = mainList.filter { m ->
            (o.wildPokemonBSTLimit <= 0 || (m.origBst <= o.wildPokemonBSTLimit && m.bst <= o.wildPokemonBSTLimit)) &&
                (wildLine == null || m.bst < wildLine || o.wildPokemonBSTLimit <= 0)
        }
        if (o.blockWildLegendaries) list = list.filter { m ->
            when (o.wildBSTLimitMode) {
                2 -> !m.isStrongLegendary
                1 -> !m.isLegendary
                else -> !m.isLegendary && !m.ultraBeast && !m.paradox
            }
        }
        return list
    }

    /** Catch rates (changeCatchRates), then AbstractRomHandler.area1to1Encounters, then the level modifier. */
    private fun wild() {
        if (o.useMinimumCatchRate) {
            val (normal, legend) = when (o.minimumCatchRateLevel) { 2 -> 128 to 64; 3 -> 200 to 100; 4, 5 -> 255 to 255; else -> 75 to 37 }
            for (m in mons) if (m != null && m.enabled) m.catchRate = maxOf(m.catchRate, if (m.isLegendary) legend else normal)
            if (o.minimumCatchRateLevel == 5) notes += "Guaranteed catching is a code patch in UPR; here every catch rate is 255, the most the data holds."
        }
        when (o.wildMod) {
            "AREA_MAPPING", "RANDOM" -> areaMapping(o.wildMod == "RANDOM")
            "UNCHANGED" -> if (!o.wildLevelsModified) { log("Wild Pokemon: Unchanged.\n"); return }
        }
        if (o.wildLevelsModified) for (set in game.wildSets) for (s in set.slots) {
            if (s.species == 0) continue
            s.minLevel = modLevel(s.minLevel, o.wildLevelModifier).coerceAtLeast(1)
            s.maxLevel = modLevel(s.maxLevel, o.wildLevelModifier).coerceAtLeast(s.minLevel)
        }
        logWild()
    }

    /**
     * Area 1-to-1: each species of an area (a map's encounter type, its times of day taken together as UPR does without
     * time-based encounters) becomes one random species, no two the same in the area. [perSlot] is UPR's plain RANDOM.
     */
    private fun areaMapping(perSlot: Boolean) {
        val allowed = wildPool()
        val similar = o.wildRestrictionMod == "SIMILAR_STRENGTH"
        val areas = game.wildSets.groupBy { it.areaKey }.values.toMutableList()
        areas.shuffle(random)
        for (sets in areas) {
            val slots = sets.flatMap { it.slots }.filter { it.species != 0 }
            // UPR's randomEncounters: with similar strength each slot is pickWildPowerLvlReplacement of the one it replaces.
            if (perSlot) { for (s in slots) s.species = (if (similar) wildPowerReplacement(allowed, mons[s.species], emptyList()) else weighted(allowed)).id; continue }
            val inArea = slots.map { it.species }.distinct()
            val map = HashMap<Int, Int>()
            val used = ArrayList<Int>()
            for (sp in inArea) {
                val picked = if (similar) wildPowerReplacement(allowed, mons[sp], used)
                else {
                    var p = allowed[random.nextInt(allowed.size)]
                    var guard = 0
                    while (p.id in map.values && guard++ < 1000) p = allowed[random.nextInt(allowed.size)]
                    p
                }
                map[sp] = picked.id; used += picked.id
            }
            for (s in slots) s.species = map[s.species] ?: s.species
        }
    }

    /** AbstractRomHandler.pickWildPowerLvlReplacement: within 10% of the old BST, widening, not used yet in the area. */
    private fun wildPowerReplacement(list: List<Mon>, current: Mon?, used: List<Int>): Mon {
        val bst = current?.origBst ?: 300
        var min = bst - bst / 10; var max = bst + bst / 10
        var can = list.filter { it.origBst in min..max && it.id !in used }
        var rounds = 0
        while (can.isEmpty() || (can.size < 3 && rounds < 2)) {
            min -= bst / 20; max += bst / 20; rounds++
            can = list.filter { it.origBst in min..max && it.id !in used }
            if (rounds > 60) { can = list; break }
        }
        return can[random.nextInt(can.size)]
    }

    private fun logWild() {
        log("--Wild Pokemon--")
        var idx = 0
        for (set in game.wildSets) {
            idx++
            log("Set #$idx - ${set.name} (rate=${set.rate})")
            for (s in set.slots) {
                if (s.species == 0) continue
                val m = mons[s.species]!!
                val lv = if (s.maxLevel > 0 && s.maxLevel != s.minLevel) "s ${s.minLevel}-${s.maxLevel}" else "${s.minLevel}"
                log(fmt("%-25s", m.displayName + " Lv" + lv) + fmt("HP %-3d ATK %-3d DEF %-3d SPATK %-3d SPDEF %-3d SPEED %-3d",
                    m.stats[0], m.stats[1], m.stats[2], m.stats[4], m.stats[5], m.stats[3]))
            }
            log()
        }
        log()
    }

    // ------------------------------------------------------------------------------------------------ trades and items

    /** AbstractRomHandler.randomizeIngameTrades. */
    private fun trades() {
        if (o.tradesMod == "UNCHANGED" || game.trades.isEmpty()) return
        val st = L.struct("InGameTrade")
        val maxNick = minOf(L.const("POKEMON_NAME_LENGTH"), st.f("nickname").size - 1)
        val maxOt = minOf(L.const("PLAYER_NAME_LENGTH"), st.f("otName").size - 1)
        val nicknames = o.pokemonNicknames.filter { encodable(it, maxNick) }.distinct()
        val otNames = o.trainerNames.filter { encodable(it, maxOt) }.distinct()
        val usedGiven = HashSet<Int>(); val usedReq = HashSet<Int>(); val usedNick = HashSet<String>(); val usedOt = HashSet<String>()
        val old = game.trades.map { Triple(it.requested, it.nickname, it.species) }
        for (t in game.trades) {
            val oldGiven = t.species
            var given = weighted(mainList)
            while (given.id in usedGiven) given = weighted(mainList)
            usedGiven += given.id
            t.species = given.id
            if (oldGiven == t.requested) t.requested = given.id
            else if (o.tradesMod == "RANDOMIZE_GIVEN_AND_REQUESTED" && t.requested != 0) {
                var r = weighted(mainList)
                while (r.id in usedReq || r.id == given.id) r = weighted(mainList)
                usedReq += r.id; t.requested = r.id
            }
            if (o.randomizeTradeNicknames && nicknames.size > usedNick.size) {
                var n = nicknames[random.nextInt(nicknames.size)]
                while (n in usedNick) n = nicknames[random.nextInt(nicknames.size)]
                usedNick += n; t.nickname = n
            } else if (t.nickname.equals(mons[oldGiven]?.name, ignoreCase = true)) {
                given.name.takeIf { encodable(it, maxNick) }?.let { t.nickname = it }
            }
            if (o.randomizeTradeOTs && otNames.size > usedOt.size) {
                var n = otNames[random.nextInt(otNames.size)]
                while (n in usedOt) n = otNames[random.nextInt(otNames.size)]
                usedOt += n; t.otName = n
                t.otId = (t.otId and 0xFFFF0000L) or random.nextInt(65536).toLong()
            }
            if (o.randomizeTradeIVs) for (k in 0 until 6) rom.setElem("InGameTrade", t.addr, "ivs", k, random.nextInt(32))
            if (o.randomizeTradeItems) rom.set(st.f("heldItem"), t.addr, randomItem(allowedItems))
        }
        log("--In-Game Trades--")
        game.trades.forEachIndexed { i, t ->
            val (req, nick, giv) = old[i]
            log(fmt("Trade %-11s -> %-11s the %-11s        ->      %-11s -> %-15s the %s",
                if (req != 0) nm(req) else "Any", nick, nm(giv), if (t.requested != 0) nm(t.requested) else "Any", t.nickname, nm(t.species)))
        }
        log()
    }

    /**
     * AbstractRomHandler.randomizeFieldItems over the item balls and the hidden items (UPR's "Item balls and hidden
     * items"): an item stays an item (randomNonTM) and a TM stays a TM; key items and HMs never move. Every roll is an
     * item of the pool's game. One rule of our own (Blake, 2026-10-05): a hidden item is never a TM. Heart & Soul hides
     * three TMs (Rock Polish, Pluck, Torment); they get a regular item like every other hidden item, and only item
     * balls keep UPR's "a TM stays a TM".
     */
    private fun fieldItems() {
        if (o.fieldItemsMod == "UNCHANGED") return
        val list = game.fieldItems.filter { f ->
            val d = game.items.getOrNull(f.item)
            d != null && d.pocket != keyPocket && f.item !in hmItems && (d.pocket != tmPocket || f.item in tmItems)
        }
        val regular = list.filter { it.item !in tmItems || it.hidden != null }
        val tms = list.filter { it.item in tmItems && it.hidden == null }
        val hiddenTms = list.filter { it.item in tmItems && it.hidden != null }
        val possible = itemList(o.banBadFieldItems).filter { it !in tmItems }
        if (o.fieldItemsMod == "SHUFFLE") {
            val plain = regular - hiddenTms.toSet()
            val a = plain.map { it.item }.shuffled(random); plain.forEachIndexed { i, f -> f.item = a[i] }
            val b = tms.map { it.item }.shuffled(random); tms.forEachIndexed { i, f -> f.item = b[i] }
            hiddenTms.forEach { it.item = possible[random.nextInt(possible.size)] }
        } else {
            val newItems = regular.map { possible[random.nextInt(possible.size)] }.toMutableList()
            // UPR draws each field TM once; past the pool's TM count (vanilla Emerald's 50) the draw starts over.
            val newTms = ArrayList<Int>()
            val left = ArrayList<Int>()
            repeat(tms.size) {
                if (left.isEmpty()) left.addAll(poolTmItems)
                if (left.isNotEmpty()) newTms += left.removeAt(random.nextInt(left.size))
            }
            newItems.shuffle(random); newTms.shuffle(random)
            regular.forEachIndexed { i, f -> f.item = newItems[i] }
            tms.forEachIndexed { i, f -> f.item = newTms.getOrElse(i) { f.origItem } }
        }
        log("--Field Items--")
        for (f in list) {
            val where = f.hidden?.let { h -> "${HnsGame.prettyMap(h.map)}, hidden item (${h.x}, ${h.y})" }
                ?: "Item ball at ${Integer.toHexString(f.addr).uppercase()}"
            log("$where: ${game.itemName(f.origItem)} => ${game.itemName(f.item)}")
        }
        log()
        val hidden = list.count { it.hidden != null }
        notes += "Field items: ${list.size - hidden} item balls and $hidden hidden items randomized (${tms.size} of them TMs)." +
            if (hiddenTms.isNotEmpty()) " The ${hiddenTms.size} hidden TMs became regular items: a hidden item is never a TM." else ""
    }

    /** AbstractRomHandler.randomizeStarterHeldItems: one item for the starter, whichever is picked, TMs included. */
    private fun starterHeldItem() {
        if (game.starterItemOperands.isEmpty()) {
            notes += "Starter held items were not set: this build's lab givemon has no item operand."
            return
        }
        game.starterItem = randomItem(itemList(o.banBadStarterHeldItems))
        log("--Starter Held Item--")
        log("The starter holds ${game.itemName(game.starterItem)}.")
        log()
    }

    /**
     * Gen3RomHandler.randomizePCPotion (the presets' "Randomize PC Potion" tweak): getNonBadItems().randomNonTM, never a
     * TM. Heart & Soul keeps it in Elm's lab trash can (gHnsLabTrashItem), and rolls it in every mode that randomizes
     * field items, as the other Kaizo games do (Blake, 2026-10-05).
     */
    private fun pcItem() {
        if (game.labTrashAddr == null) return
        val possible = nonBadItems.filter { it !in tmItems && (!o.banLuckyEgg || it != item("LUCKY_EGG")) }
        if (possible.isEmpty()) return
        game.labTrashItem = possible[random.nextInt(possible.size)]
        log("--PC Item--")
        log("Elm's lab trash can: ${game.itemName(game.origLabTrashItem)} => ${game.itemName(game.labTrashItem)}")
        log()
    }

    /**
     * AbstractRomHandler.randomizePickupItems: each row of sPickupTable a random item of the list (TMs allowed in Gen
     * 3), its percentages kept; logged as UPR logs it.
     */
    private fun pickupItems() {
        if (game.pickup.isEmpty()) {
            notes += "Pickup items were not randomized: this build has no Pickup table in its layout."
            return
        }
        val list = itemList(o.banBadPickupItems)
        for (p in game.pickup) p.item = randomItem(list)
        log("--Pickup Items--")
        for (band in 0 until 10) {
            log("Level ${band * 10 + 1}-${(band + 1) * 10}")
            val byPct = java.util.TreeMap<Int, MutableList<String>>(Comparator.reverseOrder())
            for (p in game.pickup) {
                val pct = p.percentages.getOrElse(band) { 0 }
                if (pct > 0) byPct.getOrPut(pct) { ArrayList() } += game.itemName(p.item)
            }
            for ((pct, names) in byPct) log("$pct%: ${names.joinToString(", ")}")
            log()
        }
        log()
    }
}
