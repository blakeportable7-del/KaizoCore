package com.ironmonone.pc.fx

import androidx.compose.ui.graphics.toAwtImage
import com.ironmonone.pc.BridgeReader
import com.ironmonone.pc.PcHns
import com.ironmonone.pcui.PcPanel
import com.ironmonone.pcui.Samples
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.HitChance
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * The scenario sandbox (Blake, 2026-10-10: "a sandbox to visually see the tracker without playing the game"): a short
 * text file says what happens, and the real card, drawn as the tracker draws it, plays it with the effects over it, into
 * a GIF, a contact sheet and the last frame. Species, moves, abilities and items are the game's own, read from the build
 * KaizoCore PC makes from the official 2.0.6, so a card looks as it would in a run.
 *
 *   ./gradlew :desktop:sandbox -Pscenario=path/to/fight.txt     (writes build/sandbox/<name>.gif and friends)
 *
 * The scenario, one step a line (# starts a comment; names as the game spells them, any case):
 *
 *   lead Golisopod lv 30 hp 80/104 ability Emergency Exit item Leftovers moves Pin Missile, Liquidation, Taunt, Spikes
 *   foe Suicune lv 42 hp 150/155 moves Surf, Sand Attack       (starts a trainer battle; "wild foe ..." for a wild one)
 *   view own | view foe                                        (which card the tracker shows)
 *   foe status PAR | own status none                           (BRN PSN TOX PAR FRZ SLP, CONFUSE ATTRACT as lasting looks)
 *   own stage ACC -1 | foe stage ATK +2                        (ATK DEF SPA SPD SPE ACC EVA)
 *   own damage 23 | foe heal 40 | own hp 12                    (the HP line moves; damage and faint play)
 *   own use Full Restore | own use X Accuracy                  (the item's own icon and its use, from the ROM)
 *   own substitute on | own substitute off
 *   Lasting effects stay until a step ends them ("... off"), as the game keeps them: on a Pokemon (taunt 3, disable
 *   Surf 4, encore Tackle 3, leech seed, perish 3, confuse, attract, wrap, curse, nightmare, ingrain, focus energy,
 *   yawn, future sight 2, wish 1, lock on, stockpile 2, charge, destiny bond, torment, recharging, locked in,
 *   substitute), on its side (spikes 2, reflect 5, light screen 5, safeguard 5, mist 5), or on the field (rain, hail,
 *   sun, sand). "own spikes 2" is on your side of the field. Only what the Nat. Dex Kaizo pool can roll.
 *   levelup | badge 3 | faint foe
 *   own learn Flamethrower over Ember [levelup]                ("NEW MOVE!"; levelup learns it at a level, in battle)
 *   own use Protein | own use Rare Candy                       (outside battle: the effort values or the level change)
 *   sun | sand | fog | gravity | sun off                       (the weather and field the hit chance reads)
 *   foe ability Volt Absorb | foe item Bright Powder by Frisk  (the game shows them: they count from then on)
 *     An opponent's ability that never shows a pop-up (Compound Eyes, Sand Veil: Abilities.SILENT) and any opponent's
 *     held item need the pop-up or message that named them: "by Trace", "by Frisk", "by Knock Off" ... The sandbox
 *     refuses a reveal the game could not make.
 *   own foresight | own lockon | foe minimize | own focus energy | own micle   ("... off" ends one)
 *   settings battle | settings off                             (the card's Settings open on a tab: battle, display)
 *   wait 1.5                                                   (seconds; each step otherwise holds 1.2)
 */
class Sandbox(build: ByteArray) {
    private val reader = BridgeReader(build)
    private val hitMove: (Int) -> HitChance.Move? = { id -> runCatching { HitChance.move(reader, id) }.getOrNull() }
    private val hitHold: (Int) -> Pair<HitChance.Hold, Int> = { id -> runCatching { HitChance.item(reader, id) }.getOrDefault(HitChance.Hold.UNKNOWN to 0) }
    val tracker = GbaTracker(reader, GameMap.resolve(reader))
    val art = FxRomArt.forBuild(build)

    private fun table(n: Int, name: (Int) -> String): Map<String, Int> {
        val m = HashMap<String, Int>()
        for (i in 1 until n) runCatching { name(i) }.getOrNull()?.takeIf { it.isNotBlank() && !it.startsWith("?") }?.let { m.putIfAbsent(key(it), i) }
        return m
    }
    /** A name's lookup key: letters and digits, with the gender signs kept as m and f (Nidoran♂ is not Nidoran♀). */
    private fun key(s: String) = s.lowercase().replace("♂", "m").replace("♀", "f").replace(Regex("[^a-z0-9]"), "")
    private val species by lazy { table(1574) { tracker.speciesName(it) } }
    private val moves by lazy { table(1000) { tracker.moveName(it) } }
    private val items by lazy { table(901) { tracker.itemName(it) } }

    fun speciesId(name: String) = species[key(name)] ?: error("no species called \"$name\" in this game")
    fun moveId(name: String) = moves[key(name)] ?: error("no move called \"$name\" in this game")
    fun itemId(name: String) = items[key(name)] ?: error("no item called \"$name\" in this game")

    /** A stat at [level] from its base, as the game computes it with IVs of 20 and no EVs (close enough for a picture). */
    private fun stat(base: Int, level: Int, hp: Boolean) = if (hp) (2 * base + 20) * level / 100 + level + 10 else (2 * base + 20) * level / 100 + 5

    private fun row(id: Int): MoveRow = tracker.moveRowFor(id) ?: MoveRow(id, tracker.moveName(id), 10, 10, 0, 100, 0, "STA")

    /** [stats]: base stats rolled by a randomizer (HP, Atk, Def, SpA, SpD, Spe), else the game's own. */
    fun lead(name: String, level: Int, hp: Pair<Int, Int>?, ability: String?, item: String?, moveNames: List<String>, stats: List<Int>? = null,
             nature: Int? = null): TrackedMon {
        val id = speciesId(name)
        val base = tracker.baseStats(id)?.let { b ->
            if (stats == null) b else b.copy(hp = stats[0], atk = stats[1], def = stats[2], spAtk = stats[3], spDef = stats[4], spe = stats[5])
        }
        val max = hp?.second ?: base?.let { stat(it.hp, level, true) } ?: 50
        val t = Samples.tracked(tracker.speciesName(id), id, level)
        // No effort yet: the stats below are the level's own, and a vitamin or a level works them out from the same start.
        val mon = t.mon.copy(species = id, level = level, curHp = hp?.first ?: max, maxHp = max, evs = List(6) { 0 }, nature = nature ?: t.mon.nature,
            atk = base?.let { stat(it.atk, level, false) } ?: t.mon.atk, def = base?.let { stat(it.def, level, false) } ?: t.mon.def,
            spe = base?.let { stat(it.spe, level, false) } ?: t.mon.spe, spAtk = base?.let { stat(it.spAtk, level, false) } ?: t.mon.spAtk,
            spDef = base?.let { stat(it.spDef, level, false) } ?: t.mon.spDef)
        val rows = if (moveNames.isEmpty()) t.moveRows else moveNames.map { row(moveId(it)) }
        // The mon's own move ids match its rows, so a learned move is a change the card can see.
        val made = t.copy(mon = mon.copy(moves = rows.map { it.id }), speciesName = tracker.speciesName(id), base = base, moveRows = rows, moveNames = rows.map { it.name },
            abilityName = ability ?: base?.let { tracker.abilityName(it.ability1) } ?: t.abilityName, itemName = item ?: "",
            movesLearned = rows.size, nextMoveLevel = null)
        // The stats the game would give at [level] (its nature, IVs and no effort yet); "hp" on the line sets the HP itself.
        val leveled = restat(made, level, List(6) { 0 })
        return leveled.copy(mon = leveled.mon.copy(maxHp = hp?.second ?: leveled.mon.maxHp, curHp = hp?.first ?: hp?.second ?: leveled.mon.maxHp))
    }

    fun foe(name: String, level: Int, hp: Pair<Int, Int>?, moveNames: List<String>): EnemyInfo {
        val id = speciesId(name)
        val base = tracker.baseStats(id)
        val max = hp?.second ?: base?.let { stat(it.hp, level, true) } ?: 50
        val rows = moveNames.map { row(moveId(it)).copy(ppMax = null) }
        return Samples.enemy.enemy!!.copy(species = id, speciesName = tracker.speciesName(id), level = level, curHp = hp?.first ?: max, maxHp = max,
            type1 = base?.type1 ?: 0, type2 = base?.type2 ?: 0, base = base, movesSeen = rows.map { it.name }, moveRows = rows,
            abilityGuess = "?", statusCondition = "", statStages = emptyMap(), pid = id.toLong() * 7919)
    }

    /** What one side brings to the hit chance beyond the card: its known ability and item (null: not shown yet) and the states. */
    data class Flags(
        val ability: String? = null, val item: Int? = null, val confused: Boolean = false, val minimized: Boolean = false,
        val foresight: Boolean = false, val sureHit: Boolean = false, val focusEnergy: Boolean = false, val micle: Boolean = false,
    )

    /** The weather and field, and both sides' flags. */
    data class Field(val weather: HitChance.Weather = HitChance.Weather.NONE, val gravity: Boolean = false, val own: Flags = Flags(), val foe: Flags = Flags())

    /** One moment of the scenario: the state, the card side shown, what was scripted beside the state's own changes. */
    class Step(val at: Double, val state: TrackerState, val showOwn: Boolean, val extra: List<FxEvent>, val field: Field = Field(),
               /** The opponents' abilities the game has shown so far (species, name), for the tracker's notes. */
               val revealed: List<Pair<Int, String>> = emptyList(),
               /** The Settings tab open over the card, or null. */
               val settings: String? = null,
               /** The lasting effects the game would list now, per side (the field's on both), as FxLasting.from gives them. */
               val lasting: Map<FxEvent.Side, List<LastingSpec>> = emptyMap(),
               /** The reader's caption under the card ("say ..."): what the effect on screen is, in plain words. */
               val say: String? = null)

    private fun side(st: TrackerState, f: Field, own: Boolean): HitChance.Side? {
        fun stage(m: Map<String, Int>, k: String) = (m[k] ?: 6) - 6
        val flags = if (own) f.own else f.foe
        val (hold, param) = when (val i = flags.item) { null -> HitChance.Hold.UNKNOWN to 0; 0 -> HitChance.Hold.NONE to 0; else -> HitChance.item(reader, i) }
        return if (own) {
            val m = st.party.firstOrNull() ?: return null
            HitChance.Side(stage(m.statStages, "ACC"), stage(m.statStages, "EVA"), flags.ability ?: m.abilityName, hold, param,
                setOfNotNull(m.base?.type1, m.base?.type2), m.mon.level, m.speciesName, flags.confused, flags.minimized, flags.foresight,
                flags.sureHit, flags.micle, focusEnergy = flags.focusEnergy, poisoned = m.statusCondition in setOf("PSN", "TOX"))
        } else {
            val e = st.enemy ?: return null
            HitChance.Side(stage(e.statStages, "ACC"), stage(e.statStages, "EVA"), flags.ability, hold, param,
                setOf(e.type1, e.type2), e.level, e.speciesName, flags.confused, flags.minimized, flags.foresight,
                flags.sureHit, flags.micle, focusEnergy = flags.focusEnergy, poisoned = e.statusCondition in setOf("PSN", "TOX"))
        }
    }

    /** Each shown move's hit chance and critical-hit odds, [own] side attacking the other. */
    fun odds(step: Step, own: Boolean): List<Triple<MoveRow, HitChance.Result, HitChance.Crit?>> {
        val atk = side(step.state, step.field, own) ?: return emptyList()
        val def = side(step.state, step.field, !own) ?: return emptyList()
        val rows = (if (own) step.state.party.firstOrNull()?.moveRows else step.state.enemy?.moveRows).orEmpty()
        return rows.mapNotNull { row ->
            val m = HitChance.move(reader, row.id) ?: return@mapNotNull null
            Triple(row, HitChance.of(m, atk, def, step.field.weather, weatherOn = !step.state.weatherSuppressed, gravity = step.field.gravity), if (m.status) null else HitChance.crit(m, atk, def))
        }
    }

    /** The steps of [text]. */
    fun steps(text: String): List<Step> {
        var st = Samples.party.copy(badgeSet = "GSC", badges = 0, routeName = "Route 29", healPercent = 0, healCount = 0)
        var showOwn = true
        var field = Field()
        val revealed = ArrayList<Pair<Int, String>>()
        var settings: String? = null
        var say: String? = null
        // The lasting effects: each side's (its Pokemon's and its side of the field's) and the field's.
        val sideFx = mapOf(FxEvent.Side.OWN to LinkedHashMap<String, LastingSpec>(), FxEvent.Side.FOE to LinkedHashMap<String, LastingSpec>())
        val fieldFx = LinkedHashMap<String, LastingSpec>()
        fun lastingNow() = sideFx.mapValues { (_, m) -> m.values.toList() + fieldFx.values }
        fun setFx(who: FxEvent.Side, spec: LastingSpec, on: Boolean) {
            val m = if (spec.name in FxLasting.FIELD) fieldFx else sideFx.getValue(who)
            if (on) m[spec.name] = spec else m.remove(spec.name)
        }
        /** A Pokemon left the field (fainted, or the next opponent came out): its own effects go, its side's stay. */
        fun leaves(who: FxEvent.Side) { sideFx.getValue(who).keys.retainAll(FxLasting.SIDE) }
        var at = 0.0
        val out = ArrayList<Step>()
        out += Step(0.0, st, true, emptyList(), field)
        fun side(w: String) = if (w == "foe") FxEvent.Side.FOE else FxEvent.Side.OWN
        fun withLead(f: (TrackedMon) -> TrackedMon) { st = st.copy(party = listOf(f(st.party[0])) + st.party.drop(1)) }
        fun withFoe(f: (EnemyInfo) -> EnemyInfo) { st.enemy?.let { e -> st = st.copy(enemy = f(e)) } }
        fun flags(w: String, f: (Flags) -> Flags) { field = if (w == "foe") field.copy(foe = f(field.foe)) else field.copy(own = f(field.own)) }
        fun hp(w: String, f: (Int, Int) -> Int) {
            if (w == "foe") withFoe { e -> e.copy(curHp = f(e.curHp, e.maxHp).coerceIn(0, e.maxHp)) }
            else withLead { m -> m.copy(mon = m.mon.copy(curHp = f(m.mon.curHp, m.mon.maxHp).coerceIn(0, m.mon.maxHp))) }
        }
        for ((n, raw) in text.lines().withIndex()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val w = line.split(Regex("\\s+"))
            val head = w[0].lowercase()
            val extra = ArrayList<FxEvent>()
            var hold = 1.2
            fun bad(): Nothing = error("line ${n + 1}: \"$line\" is not a step the sandbox knows")
            fun opt(k: String): String? {
                val i = w.indexOfFirst { it.equals(k, true) }.takeIf { it >= 0 } ?: return null
                val stops = setOf("lv", "hp", "ability", "item", "moves", "move", "turns", "base", "nature")
                return w.drop(i + 1).takeWhile { it.lowercase() !in stops }.joinToString(" ").ifBlank { null }
            }
            fun hpPair(s: String?) = s?.split('/')?.let { p -> p[0].toInt() to p.getOrElse(1) { p[0] }.toInt() }
            when {
                head == "wait" -> { at += w.getOrNull(1)?.toDoubleOrNull() ?: bad(); continue }
                // "say <words>": the reader's caption under the card from now on; "say off" clears it. It takes no time.
                // "route <name>": where the battle is, as the card names it under a wild encounter. It takes no time.
                head == "route" -> { st = st.copy(routeName = line.substringAfter(' ', "").trim()); hold = 0.0 }
                head == "say" -> { say = line.substringAfter(' ', "").trim().takeIf { it.isNotEmpty() && !it.equals("off", true) }; hold = 0.0 }
                head == "lead" -> {
                    val name = w.drop(1).takeWhile { !it.equals("lv", true) }.joinToString(" ")
                    // "base 80/120/79/95/79/70": base stats as a randomizer rolled them (HP, Atk, Def, SpA, SpD, Spe).
                    val stats = opt("base")?.split('/')?.map { it.trim().toInt() }?.also { if (it.size != 6) bad() }
                    val lead = lead(name, opt("lv")?.toInt() ?: 5, hpPair(opt("hp")), opt("ability"), opt("item"), opt("moves")?.split(',')?.map { it.trim() }.orEmpty(), stats,
                        opt("nature")?.let { n -> NATURES.indexOfFirst { it.equals(n, true) }.takeIf { it >= 0 } ?: bad() })
                    st = st.copy(party = listOf(lead), partyCount = 1); hold = 0.6
                    field = field.copy(own = Flags(ability = lead.abilityName, item = opt("item")?.let { itemId(it) } ?: 0))
                }
                // A new opponent names its level ("foe Miltank lv 25"); "foe status PAR" and the rest are steps for it.
                (head == "foe" && w.any { it.equals("lv", true) }) || (head == "wild" && w.getOrNull(1)?.lowercase() == "foe") -> {
                    val rest = if (head == "wild") w.drop(2) else w.drop(1)
                    val name = rest.takeWhile { !it.equals("lv", true) }.joinToString(" ")
                    val foe = foe(name, opt("lv")?.toInt() ?: 5, hpPair(opt("hp")), opt("moves")?.split(',')?.map { it.trim() }.orEmpty())
                    st = st.copy(inBattle = true, isWildBattle = head == "wild", enemy = foe, enemyTeam = if (head == "wild") emptyList() else listOf(true, true, true))
                    showOwn = false
                    leaves(FxEvent.Side.FOE)
                    st = st.copy(weatherSuppressed = false)
                    // A new opponent's ability and item are unknown until the game shows them (or the line names them).
                    val ab = opt("ability")
                    if (ab != null) withFoe { it.copy(abilityGuess = ab) }
                    // The opponent's line may name its ability and item only for the scenario's own bookkeeping
                    // ("as if seen before"); a silent ability or any item never comes from here.
                    if (ab != null && Abilities.silent(ab)) error("line ${n + 1}: $ab never shows a pop-up; reveal it with \"foe ability $ab by Trace\"")
                    if (opt("item") != null) error("line ${n + 1}: an opponent's item is unknown until a message names it; use \"foe item <name> by Frisk\"")
                    field = field.copy(foe = Flags(ability = ab, item = null),
                        own = field.own.copy(sureHit = false, foresight = false, minimized = false, confused = false, focusEnergy = false, micle = false))
                }
                head == "view" -> { showOwn = w.getOrNull(1)?.lowercase() == "own"; hold = 0.8 }
                head == "settings" -> { settings = w.getOrNull(1)?.lowercase()?.takeIf { it != "off" }; hold = 1.5 }
                // Every stat is worked out again at the new level, as the game does; the HP gained is added.
                head == "levelup" -> withLead { m -> withLevel(m, m.mon.level + 1) }
                head == "badge" -> { val b = (w.getOrNull(1)?.toInt() ?: bad()) - 1; st = st.copy(badges = st.badges or (1 shl b)); hold = 3.8 }
                head == "faint" -> { val who = w.getOrNull(1)?.lowercase() ?: "own"; hp(who) { _, _ -> 0 }; leaves(side(who)) }
                head in setOf("rain", "hail", "sun", "sand") -> {
                    val on = w.getOrNull(1)?.lowercase() != "off"
                    // One weather at a time, as the game has it.
                    fieldFx.keys.removeAll(FxLasting.FIELD)
                    val look = when (head) { "rain" -> "Rain"; "hail" -> "Hail"; "sun" -> "Sun"; else -> "Sandstorm" }
                    if (on) fieldFx[look] = LastingSpec(look)
                    st = st.copy(weather = if (on) mapOf("Rain" to "RAIN", "Hail" to "HAIL", "Sun" to "SUN", "Sandstorm" to "SANDSTORM")[look] else null)
                    val kind = when (head) { "rain" -> HitChance.Weather.RAIN; "hail" -> HitChance.Weather.HAIL_SNOW; "sun" -> HitChance.Weather.SUN; else -> HitChance.Weather.SAND }
                    field = field.copy(weather = if (on) kind else HitChance.Weather.NONE)
                }
                head == "own" || head == "foe" -> {
                    val verb = w.getOrNull(1)?.lowercase() ?: bad()
                    val arg = w.drop(2).joinToString(" ")
                    when (verb) {
                        "status" -> {
                            val k = arg.uppercase().let { if (it == "NONE") "" else it }
                            // Confusion and infatuation are not a major status: they last beside one, as the game keeps them.
                            if (k == "CONFUSE") { flags(head) { it.copy(confused = true) }; setFx(side(head), LastingSpec("Confused"), true) }
                            else if (k == "ATTRACT") setFx(side(head), LastingSpec("Infatuated"), true)
                            else if (head == "foe") withFoe { it.copy(statusCondition = k) } else withLead { it.copy(statusCondition = k) }
                        }
                        "stage" -> {
                            val stat = w.getOrNull(2)?.uppercase() ?: bad(); val d = w.getOrNull(3)?.toInt() ?: bad()
                            if (head == "foe") withFoe { e -> e.copy(statStages = e.statStages + (stat to ((e.statStages[stat] ?: 6) + d).coerceIn(0, 12))) }
                            else withLead { m -> m.copy(statStages = m.statStages + (stat to ((m.statStages[stat] ?: 6) + d).coerceIn(0, 12))) }
                        }
                        "damage" -> hp(head) { c, _ -> c - (arg.toIntOrNull() ?: bad()) }
                        "heal" -> hp(head) { c, _ -> c + (arg.toIntOrNull() ?: bad()) }
                        "hp" -> hp(head) { _, _ -> arg.toIntOrNull() ?: bad() }
                        "use" -> {
                            val item = itemId(arg)
                            val ev = effortOf(arg)
                            if (ev != null || key(arg) in CANDIES) {
                                // Outside battle: the game changes the Pokemon, and the card reacts to that change.
                                if (st.inBattle) error("line ${n + 1}: $arg is not used in battle")
                                if (head != "own") error("line ${n + 1}: only your own Pokemon takes a $arg")
                                if (ev != null) withLead { m -> withEffort(m, ev.first, ev.second) }
                                else withLead { m -> withLevel(m, m.mon.level + 1) }
                                hold = 2.2
                            } else {
                            extra += FxEvent.ItemUsed(side(head), item)
                            // A held berry the Pokemon eats is gone from its card.
                            if (head == "own" && st.party.firstOrNull()?.itemName?.let { key(it) == key(arg) } == true) withLead { it.copy(itemName = "") }
                            // What the item does to the card, as the game would: HP back, a status cured, a stat up.
                            when (art.itemUsage(item)) {
                                1 -> hp(head) { c, m -> c + healOf(arg, m) }
                                2 -> if (head == "foe") withFoe { it.copy(statusCondition = "") } else withLead { it.copy(statusCondition = "") }
                                3 -> { hp(head) { _, m -> m }; if (head == "foe") withFoe { it.copy(statusCondition = "") } else withLead { it.copy(statusCondition = "") } }
                                6 -> flags(head) { it.copy(focusEnergy = true) }
                                4 -> xStat(arg)?.let { s -> if (head == "foe") withFoe { e -> e.copy(statStages = e.statStages + (s to ((e.statStages[s] ?: 6) + 2).coerceAtMost(12))) }
                                    else withLead { m -> m.copy(statStages = m.statStages + (s to ((m.statStages[s] ?: 6) + 2).coerceAtMost(12))) } }
                            }
                            hold = 2.0
                            }
                        }
                        "learn" -> {
                            // own learn Flamethrower over Ember [levelup]
                            if (head != "own") error("line ${n + 1}: only your own Pokemon's moves change on the card")
                            val words = w.drop(2)
                            val atLevel = words.lastOrNull()?.equals("levelup", true) == true
                            val body = words.dropLast(if (atLevel) 1 else 0).joinToString(" ")
                            val name = body.substringBefore(" over ").trim()
                            val over = body.substringAfter(" over ", "").trim().ifEmpty { null }
                            withLead { m -> learn(m, moveId(name), over, n) }
                            if (atLevel) withLead { m -> withLevel(m, m.mon.level + 1) }
                            hold = 2.2
                        }
                        "substitute" -> setFx(side(head), LastingSpec("Substitute"), w.getOrNull(2)?.lowercase() != "off")
                        "ability" -> {
                            // The game shows the ability: it counts from now on, the card plays it, and what it does happens.
                            // An opponent's silent ability is shown only by another pop-up (your Trace), which names it.
                            val by = arg.substringAfter(" by ", "").trim()
                            val arg = arg.substringBefore(" by ").trim()
                            if (head == "foe" && Abilities.silent(arg) && by.isEmpty())
                                error("line ${n + 1}: $arg never shows a pop-up in Heart & Soul, so the card cannot learn it from the opponent; name what showed it (\"foe ability $arg by Trace\")")
                            flags(head) { it.copy(ability = arg) }
                            if (head == "foe") withFoe { it.copy(abilityGuess = arg) } else withLead { it.copy(abilityName = arg) }
                            // A silent ability has no pop-up of its own to play with: your own is simply known.
                            if (head == "foe" || !Abilities.silent(arg)) extra += FxEvent.Ability(side(head), arg)
                            if (head == "foe") st.enemy?.let { revealed += it.species to arg }
                            val look = Abilities.of(arg)
                            val other = if (head == "foe") "own" else "foe"
                            // Cloud Nine and Air Lock: the weather stays up and changes nothing while they are out.
                            if (key(arg) == "cloudnine" || key(arg) == "airlock") st = st.copy(weatherSuppressed = true)
                            if (look.heal) hp(head) { c, m -> c + m / 4 }
                            if (look.toOneHp) hp(head) { _, _ -> 1 }
                            fun stages(w: String, d: Map<String, Int>) = d.forEach { (stat, n) ->
                                if (w == "foe") withFoe { e -> e.copy(statStages = e.statStages + (stat to ((e.statStages[stat] ?: 6) + n).coerceIn(0, 12))) }
                                else withLead { m -> m.copy(statStages = m.statStages + (stat to ((m.statStages[stat] ?: 6) + n).coerceIn(0, 12))) }
                            }
                            stages(head, look.stages); stages(other, look.otherStages)
                            if (look.cures) { if (head == "foe") withFoe { it.copy(statusCondition = "") } else withLead { it.copy(statusCondition = "") } }
                            look.otherStatus?.let { k ->
                                if (k == "ATTRACT") setFx(side(other), LastingSpec("Infatuated"), true)
                                else if (other == "foe") withFoe { it.copy(statusCondition = k) } else withLead { it.copy(statusCondition = k) }
                            }
                            look.weather?.let { wname ->
                                fieldFx.keys.removeAll(FxLasting.FIELD)
                                fieldFx[wname] = LastingSpec(wname)
                                st = st.copy(weather = mapOf("Rain" to "RAIN", "Hail" to "HAIL", "Sun" to "SUN", "Sandstorm" to "SANDSTORM")[wname])
                                field = field.copy(weather = when (wname) { "Rain" -> HitChance.Weather.RAIN; "Sun" -> HitChance.Weather.SUN; "Sandstorm" -> HitChance.Weather.SAND; else -> HitChance.Weather.HAIL_SNOW })
                            }
                            hold = 2.2
                        }
                        "item" -> {
                            // An opponent's held item is known only from a message that names it (Frisk, Knock Off,
                            // Trick, Thief, a berry eaten, Leftovers): BrightPowder and the lenses never announce themselves.
                            val by = arg.substringAfter(" by ", "").trim()
                            val name = arg.substringBefore(" by ").trim()
                            if (head == "foe" && by.isEmpty())
                                error("line ${n + 1}: an opponent's item shows only through a message that names it; say which (\"foe item $name by Frisk\")")
                            flags(head) { it.copy(item = if (name.equals("none", true)) 0 else itemId(name)) }
                        }
                        "foresight", "lockon", "minimize", "focus", "micle" -> {
                            val on = w.last().lowercase() != "off"
                            if (verb == "lockon") setFx(side(head), LastingSpec("Lock-On"), on)
                            if (verb == "focus") setFx(side(head), LastingSpec("Focus Energy"), on)
                            flags(head) {
                                when (verb) {
                                    "foresight" -> it.copy(foresight = on); "lockon" -> it.copy(sureHit = on); "minimize" -> it.copy(minimized = on)
                                    "focus" -> it.copy(focusEnergy = on); else -> it.copy(micle = on)
                                }
                            }
                        }
                        else -> {
                            // A lasting effect: taunt 3, disable Surf 4, encore Tackle 3, leech seed, spikes 2, ... off
                            val words = w.drop(1)
                            val off = words.lastOrNull()?.lowercase() == "off"
                            val num = words.lastOrNull()?.toIntOrNull()
                            val body = words.dropLast(if (off || num != null) 1 else 0).joinToString(" ").lowercase()
                            val (alias, name) = LASTING_ALIASES.firstOrNull { body == it.first || body.startsWith(it.first + " ") } ?: bad()
                            val move = body.removePrefix(alias).trim().ifBlank { null }?.let { m -> row(moveId(m)).name }
                            setFx(side(head), LastingSpec(name, num, move), !off)
                            if (name == "Confused") flags(head) { it.copy(confused = !off) }
                        }
                    }
                }
                else -> bad()
            }
            out += Step(at, st, showOwn, extra, field, revealed.toList(), settings, lastingNow(), say)
            at += hold
        }
        out += Step(at, st, showOwn, emptyList(), field, revealed.toList(), settings, lastingNow(), say)
        return out
    }

    private val CAPTION_LINES = 8
    private val SAY_LINES = 3

    /** The sandbox's words for the lasting effects, longest first, and the names FxLasting gives them. */
    private val LASTING_ALIASES = listOf(
        "light screen" to "Light Screen", "leech seed" to "Leech Seed", "focus energy" to "Focus Energy", "future sight" to "Future Sight",
        "doom desire" to "Future Sight", "lock on" to "Lock-On", "mind reader" to "Lock-On", "locked in" to "Must Attack",
        "destiny bond" to "Destiny Bond", "fire spin" to "Trapped", "sand tomb" to "Trapped", "perish song" to "Perish Song",
        "perish" to "Perish Song", "taunt" to "Taunt", "disable" to "Disable", "encore" to "Encore", "spikes" to "Spikes",
        "reflect" to "Reflect", "safeguard" to "Safeguard", "mist" to "Mist", "confused" to "Confused", "confuse" to "Confused",
        "attract" to "Infatuated", "infatuated" to "Infatuated", "wrap" to "Trapped", "bind" to "Trapped", "whirlpool" to "Trapped",
        "clamp" to "Trapped", "trapped" to "Trapped", "curse" to "Curse", "nightmare" to "Nightmare", "ingrain" to "Ingrain",
        "yawn" to "Drowsy", "drowsy" to "Drowsy", "wish" to "Wish", "stockpile" to "Stockpile", "charge" to "Charge",
        "torment" to "Torment", "recharging" to "Recharging", "recharge" to "Recharging", "outrage" to "Must Attack",
        "thrash" to "Must Attack", "petal dance" to "Must Attack",
    )

    private val CANDIES = setOf("rarecandy")

    /** Gen 3's natures in the game's order ("lead ... nature Modest"). */
    private val NATURES = listOf("Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile", "Relaxed", "Impish", "Lax",
        "Timid", "Hasty", "Serious", "Jolly", "Naive", "Modest", "Mild", "Quiet", "Bashful", "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky")

    /** The card's stat a vitamin, feather or effort berry moves, and by how much (the game's own amounts). */
    private fun effortOf(name: String): Pair<String, Int>? = when (key(name)) {
        "hpup" -> "HP" to 10; "protein" -> "ATK" to 10; "iron" -> "DEF" to 10; "calcium" -> "SPA" to 10; "zinc" -> "SPD" to 10; "carbos" -> "SPE" to 10
        "healthfeather", "healthwing" -> "HP" to 1; "musclefeather", "musclewing" -> "ATK" to 1; "resistfeather", "resistwing" -> "DEF" to 1
        "geniusfeather", "geniuswing" -> "SPA" to 1; "cleverfeather", "cleverwing" -> "SPD" to 1; "swiftfeather", "swiftwing" -> "SPE" to 1
        "pomegberry" -> "HP" to -10; "kelpsyberry" -> "ATK" to -10; "qualotberry" -> "DEF" to -10
        "hondewberry" -> "SPA" to -10; "grepaberry" -> "SPD" to -10; "tamatoberry" -> "SPE" to -10
        else -> null
    }

    /** The item for an effort change on [stat] by [delta], by name from the game (for the effect's icon). */
    fun effortItem(stat: String, delta: Int): Int? {
        val name = when {
            delta >= 10 -> mapOf("HP" to "HP Up", "ATK" to "Protein", "DEF" to "Iron", "SPA" to "Calcium", "SPD" to "Zinc", "SPE" to "Carbos")[stat]
            delta > 0 -> mapOf("HP" to "Health Feather", "ATK" to "Muscle Feather", "DEF" to "Resist Feather", "SPA" to "Genius Feather", "SPD" to "Clever Feather", "SPE" to "Swift Feather")[stat]
            else -> mapOf("HP" to "Pomeg Berry", "ATK" to "Kelpsy Berry", "DEF" to "Qualot Berry", "SPA" to "Hondew Berry", "SPD" to "Grepa Berry", "SPE" to "Tamato Berry")[stat]
        } ?: return null
        return items[key(name)]
    }

    private val EV_INDEX = mapOf("HP" to 0, "ATK" to 1, "DEF" to 2, "SPE" to 3, "SPA" to 4, "SPD" to 5)

    /** A stat from its base at [level] with [iv] (20 unless given) and [ev] effort values, before the nature. */
    private fun statEv(base: Int, level: Int, ev: Int, hp: Boolean, iv: Int = 20) =
        if (hp) (2 * base + iv + ev / 4) * level / 100 + level + 10 else (2 * base + iv + ev / 4) * level / 100 + 5

    /** The nature's change to a stat, in tenths (Gen 3's order: Atk, Def, Spe, SpA, SpD; nature / 5 raises, nature % 5 lowers). */
    private fun natureTenths(nature: Int, stat: Int) = when {
        nature / 5 == nature % 5 -> 10
        nature / 5 == stat -> 11
        nature % 5 == stat -> 9
        else -> 10
    }

    /** [m]'s stats at [level] with [evs], as the game works them out: base, IVs, effort, then the nature. */
    private fun restat(m: TrackedMon, level: Int, evs: List<Int>): TrackedMon {
        val b = m.base ?: return m.copy(mon = m.mon.copy(level = level, evs = evs))
        val iv = m.mon.ivs.ifEmpty { List(6) { 20 } }
        val n = m.mon.nature
        fun other(base: Int, i: Int, ev: Int, natureStat: Int) = statEv(base, level, ev, false, iv[i]) * natureTenths(n, natureStat) / 10
        val maxHp = statEv(b.hp, level, evs[0], true, iv[0])
        return m.copy(mon = m.mon.copy(level = level, evs = evs, maxHp = maxHp, curHp = (m.mon.curHp + maxHp - m.mon.maxHp).coerceIn(1, maxHp),
            atk = other(b.atk, 1, evs[1], 0), def = other(b.def, 2, evs[2], 1), spe = other(b.spe, 3, evs[3], 2),
            spAtk = other(b.spAtk, 4, evs[4], 3), spDef = other(b.spDef, 5, evs[5], 4)))
    }

    private fun withEffort(m: TrackedMon, stat: String, delta: Int): TrackedMon {
        val evs = m.mon.evs.ifEmpty { List(6) { 0 } }.toMutableList()
        val i = EV_INDEX.getValue(stat)
        evs[i] = (evs[i] + delta).coerceIn(0, 252)
        return restat(m, m.mon.level, evs)
    }

    private fun withLevel(m: TrackedMon, level: Int) = restat(m, level, m.mon.evs.ifEmpty { List(6) { 0 } })

    /** [m] with [move] learned: into the slot of [over], else the first empty one. */
    private fun learn(m: TrackedMon, move: Int, over: String?, line: Int): TrackedMon {
        val ids = m.mon.moves.let { it + List(maxOf(0, 4 - it.size)) { 0 } }.toMutableList()
        val slot = if (over != null) ids.indexOf(moveId(over)).takeIf { it >= 0 } ?: error("line ${line + 1}: it does not know $over")
            else ids.indexOf(0).takeIf { it >= 0 } ?: error("line ${line + 1}: four moves known; say which it forgets (\"over <move>\")")
        ids[slot] = move
        val rows = ids.filter { it != 0 }.map { row(it) }
        return m.copy(mon = m.mon.copy(moves = ids), moveRows = rows, moveNames = rows.map { it.name }, movesLearned = rows.size)
    }

    /** One line per move whose odds the game moves off what the card prints, and the critical-hit odds when raised. */
    private fun caption(odds: List<Triple<MoveRow, HitChance.Result, HitChance.Crit?>>): List<String> {
        val out = ArrayList<String>()
        for ((row, r, _) in odds) {
            val base = row.acc ?: continue
            if (base == 0 || (!r.sure && r.chance == base)) continue
            val to = if (r.sure) "sure" else "${r.chance.coerceAtMost(100)}"
            out += "${row.name}: $base -> $to   ${r.why.joinToString(", ")}"
        }
        odds.firstNotNullOfOrNull { it.third }?.takeIf { it.oneIn != 24 }?.let { c ->
            val odds = when (c.oneIn) { 0 -> "none"; 1 -> "every hit"; else -> "1 in ${c.oneIn}" }
            out += "Crit: $odds   ${c.why.joinToString(", ")}"
        }
        return out
    }

    /** The HP an HP item gives, by its name as the game's items give it (Potion 20, Lemonade 70 in Gen 7+, ...). */
    private fun healOf(name: String, max: Int): Int = when (key(name)) {
        "potion" -> 20; "superpotion" -> 60; "hyperpotion" -> 120; "maxpotion" -> max; "freshwater" -> 30; "sodapop" -> 50
        "lemonade" -> 70; "moomoomilk" -> 100; "energypowder" -> 60; "energyroot" -> 120; "berryjuice" -> 20; "oranberry" -> 10
        // Heart & Soul plays Emerald's rules: Sitrus gives 30 HP, as in Gen 3.
        "sitrusberry" -> 30; else -> 30
    }

    /** The stat an X item raises, by its name. */
    private fun xStat(name: String): String? = when (key(name)) {
        "xattack" -> "ATK"; "xdefense", "xdefend" -> "DEF"; "xspatk", "xspecial" -> "SPA"; "xspdef" -> "SPD"; "xspeed" -> "SPE"; "xaccuracy" -> "ACC"; else -> null
    }

    /**
     * Plays [steps] through the real card and the effects: frames every 40 ms. [effects] false draws the card alone,
     * [edges] false the effects with no edge (FxEdgeContrastTest compares the three).
     */
    fun frames(steps: List<Step>, scale: Float = 3f, captions: Boolean = true, effects: Boolean = true, edges: Boolean = true,
               strokes: MutableList<BooleanArray?>? = null, solid: MutableList<BooleanArray?>? = null,
               autoTheme: Boolean = false): List<BufferedImage> {
        // [autoTheme]: each card in the colours of the Pokemon it shows (Blake, 2026-10-10: "the foe card would be the foes
        // colors"), the auto theme's table and, for a Pokemon it does not name, its painted theme; the user's theme after.
        val userTheme = com.ironmonone.app.ThemeStore.snapshot()
        val painted = com.ironmonone.app.AutoTheme::class.java.getDeclaredMethod("painted", Int::class.javaPrimitiveType).apply { isAccessible = true }
        fun themeFor(species: Int) {
            val pack = com.ironmonone.app.HnsNumbers.toPack(species) ?: species
            val code = com.ironmonone.app.AutoTheme.gbaTheme(pack) ?: painted.invoke(com.ironmonone.app.AutoTheme, pack) as String?
            com.ironmonone.app.ThemeStore.show(code?.let { com.ironmonone.app.ThemeCodes.gen3(it) } ?: userTheme)
        }
        // The layer walks the sprite while this plays; the card draws its own again after, for whatever renders next.
        val walking = com.ironmonone.pcui.PcPictures.cardDrawsWalking
        try { return framesThemed(steps, scale, captions, effects, edges, strokes, solid, if (autoTheme) ::themeFor else null) }
        finally { com.ironmonone.app.ThemeStore.show(userTheme); com.ironmonone.pcui.PcPictures.cardDrawsWalking = walking }
    }

    private fun framesThemed(steps: List<Step>, scale: Float, captions: Boolean, effects: Boolean, edges: Boolean,
                             strokes: MutableList<BooleanArray?>?, solid: MutableList<BooleanArray?>?, themeFor: ((Int) -> Unit)?): List<BufferedImage> {
        val font = com.ironmonone.pc.TrackerWindow.cardFont()
        val layer = FxLayer(font, art)
        layer.edges = edges
        layer.effects = effects
        layer.itemName = { tracker.itemName(it) }
        layer.effortItem = { stat, d -> effortItem(stat, d) }
        layer.candyItem = { items[key("Rare Candy")] }
        layer.badgeName = { i -> com.ironmonone.pcui.HNS_BADGES.getOrNull(i)?.first ?: "Badge ${i + 1}" }
        layer.leaderOf = { i -> com.ironmonone.pcui.HNS_BADGES.getOrNull(i)?.second?.substringBefore(",") }
        layer.badgeArt = { i, _ -> com.ironmonone.app.PcAssets.badge(com.ironmonone.app.pcDesktopCtx, com.ironmonone.app.PcAssets.HNS_BADGES, i + 1, true)?.toAwtImage() }
        val files = File(System.getProperty("java.io.tmpdir"), "kc-sandbox").apply { mkdirs() }
        // The tracker's notes for this run: an opponent's ability shows on its card once the game has revealed it.
        File(files, "abilities.txt").delete(); File(files, "marks.txt").delete()
        val marks = com.ironmonone.app.StatMarks(File(files, "marks.txt"))
        // The layer walks the sprite on the scenario's clock; the card leaves its slot to it.
        com.ironmonone.pcui.PcPictures.cardDrawsWalking = false
        val out = ArrayList<BufferedImage>()
        PcPanel.Renderer(files, PcPanel.Input(null, 0), scale).use { r ->
            val s = scale.toDouble()
            var shown: Step? = null
            var card: BufferedImage? = null
            var caption: List<String> = emptyList()
            var say: String? = null
            // The reader's strip at the very bottom, when the scenario says anything: three lines of plain words.
            val sayFont = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 17)
            val sayStrip = if (steps.any { it.say != null }) SAY_LINES * 22 + 16 else 0
            var next = 0
            val end = steps.last().at + 0.8
            var t = 0.0
            while (t <= end) {
                while (next < steps.size && steps[next].at <= t) {
                    val step = steps[next]
                    val now = (t * 1e9).toLong()
                    val prev = shown
                    val side = if (step.showOwn || !step.state.inBattle) FxEvent.Side.OWN else FxEvent.Side.FOE
                    if (prev != null && prev.showOwn != step.showOwn) layer.showing(side, if (side == FxEvent.Side.OWN) step.state.party[0].statusCondition else step.state.enemy?.statusCondition.orEmpty(), now)
                    layer.walker = {
                        if (side == FxEvent.Side.OWN) step.state.party.firstOrNull()?.let { m -> FxLayer.Walker(m.mon.species, true, if (m.mon.curHp <= 0) "FNT" else m.statusCondition, (m.statStages["EVA"] ?: 6) - 6, (m.statStages["ACC"] ?: 6) - 6) }
                        else step.state.enemy?.let { e -> FxLayer.Walker(e.species, true, if (e.curHp <= 0) "FNT" else e.statusCondition, (e.statStages["EVA"] ?: 6) - 6, (e.statStages["ACC"] ?: 6) - 6) }
                    }
                    // Each shown move's chance to hit, as the game works it out (HitChance), against the other side.
                    val odds = if (step.state.inBattle) odds(step, side == FxEvent.Side.OWN) else emptyList()
                    // The card prints the real hit chance itself now (PcReal, "Show real hit chance"): no count over it.
                    layer.accuracy = { null }
                    caption = caption(odds)
                    say = step.say
                    layer.sync(step.lasting, now)
                    layer.rows = {
                        val rows = if (side == FxEvent.Side.OWN) step.state.party.firstOrNull()?.moveRows else step.state.enemy?.moveRows
                        rows.orEmpty().map { it.name to (it.category == "STA") }
                    }
                    layer.play(FxEvents.between(prev?.state, step.state) + step.extra, side, now)
                    for ((sp, name) in step.revealed) marks.revealAbility(sp, name)
                    layer.abilityRow = { who, name ->
                        val sp = step.state.enemy?.species ?: 0
                        if (who == FxEvent.Side.OWN) Rect(36.0, 45.0, 60.0, 9.0)
                        else if (marks.secondAbilityFor(sp) == name) Rect(36.0, 45.0, 60.0, 9.0) else Rect(36.0, 35.0, 60.0, 9.0)
                    }
                    r.settings.open = step.settings != null
                    layer.onCard = { step.settings == null }
                    step.settings?.let { tab -> com.ironmonone.pcui.PcSettingsScreen.Tab.entries.firstOrNull { it.name.equals(tab, true) }?.let { r.settings.tab = it } }
                    themeFor?.let { f -> (if (side == FxEvent.Side.OWN) step.state.party.firstOrNull()?.mon?.species else step.state.enemy?.species)?.let(f) }
                    // The card's real hit chance and move power from the scenario's lasting effects, as the session gives them.
                    // The game lists Lock-On on its target; a scenario puts it on the side that used it ("own lockon").
                    fun names(side: FxEvent.Side, other: FxEvent.Side) = step.lasting[side].orEmpty().map { it.name }.filter { it != "Lock-On" }.toSet() +
                        setOfNotNull("Lock-On".takeIf { step.lasting[other].orEmpty().any { it.name == "Lock-On" } })
                    val battle = if (!step.state.inBattle) null else com.ironmonone.pcui.PcBattleFacts(
                        own = names(FxEvent.Side.OWN, FxEvent.Side.FOE), foe = names(FxEvent.Side.FOE, FxEvent.Side.OWN), move = hitMove, hold = hitHold)
                    card = r.renderImage(PcPanel.Input(step.state, 1, marks = marks, routeName = step.state.routeName, showOwn = step.showOwn, version = step.revealed.size, battle = battle))
                    shown = step
                    next++
                }
                val c = card ?: break
                val now = (t * 1e9).toLong()
                val strip = if (captions) CAPTION_LINES * 15 + 8 else 0
                val img = BufferedImage(c.width, c.height + strip + sayStrip, BufferedImage.TYPE_INT_RGB)
                val g = img.createGraphics()
                g.color = Color.BLACK; g.fillRect(0, 0, img.width, img.height)
                if (captions) {
                    // Below the card, never on it: the sum behind each odd that moved, for checking the math by eye.
                    g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
                    g.color = Color(220, 220, 210)
                    val fm = g.fontMetrics
                    val wrapped = caption.flatMap { line ->
                        val out = ArrayList<String>(); var cur = ""
                        for (word in line.split(" ")) {
                            val next = if (cur.isEmpty()) word else "$cur $word"
                            if (fm.stringWidth(next) > c.width - 12 && cur.isNotEmpty()) { out += cur; cur = "    $word" } else cur = next
                        }
                        out + cur
                    }
                    for ((k, line) in wrapped.take(CAPTION_LINES).withIndex()) g.drawString(line, 6, c.height + 15 + k * 15)
                }
                if (sayStrip > 0) {
                    val top = c.height + strip
                    g.color = Color(28, 27, 23); g.fillRect(0, top, img.width, sayStrip)
                    g.color = Color(95, 130, 102); g.fillRect(0, top, img.width, 3)
                    say?.let { words ->
                        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                        g.font = sayFont
                        val fm = g.fontMetrics
                        val lines = ArrayList<String>(); var cur = ""
                        for (word in words.split(" ")) {
                            val nx = if (cur.isEmpty()) word else "$cur $word"
                            if (fm.stringWidth(nx) > img.width - 24 && cur.isNotEmpty()) { lines += cur; cur = word } else cur = nx
                        }
                        lines += cur
                        val shown = lines.take(SAY_LINES)
                        val y0 = top + 8 + (sayStrip - 16 - shown.size * 22) / 2
                        g.color = Color(247, 244, 236)
                        for ((k, line) in shown.withIndex()) g.drawString(line, (img.width - fm.stringWidth(line)) / 2, y0 + 17 + k * 22)
                    }
                }
                val (ox, oy) = layer.cardOffset(now)
                g.drawImage(c, (ox * s).roundToInt(), (oy * s).roundToInt(), null)
                layer.draw(g, s, now, c)
                strokes?.add(if (effects) Edges.lastStrokes?.copyOf() else null)
                solid?.add(if (effects) Edges.lastSolid?.copyOf() else null)
                g.dispose()
                out += img
                t += 0.04
            }
        }
        return out
    }
}

fun main(args: Array<String>) {
    val scenario = File(args.getOrNull(0) ?: error("a scenario file"))
    val out = File(args.getOrNull(1) ?: "build/sandbox").apply { mkdirs() }
    val official = File(args.getOrNull(2) ?: error("the official 2.0.6 ROM"))
    val sb = Sandbox(PcHns.build(official.readBytes()))
    println("sandbox: ROM art ${if (sb.art.ok) "read from the game" else "not for this ROM (code art)"}")
    val steps = sb.steps(scenario.readText())
    val name = scenario.nameWithoutExtension
    // SANDBOX_PNG=1: the frames as a PNG sequence, for a video at 25 frames a second (=bare: with no captions).
    if (System.getenv("SANDBOX_PNG") != null) {
        val dir = File(out, name).apply { deleteRecursively(); mkdirs() }
        val frames = sb.frames(steps, captions = System.getenv("SANDBOX_PNG") != "bare", autoTheme = System.getenv("SANDBOX_AUTOTHEME") != null)
        frames.forEachIndexed { i, f -> ImageIO.write(f, "png", File(dir, "%05d.png".format(i))) }
        println("sandbox: $name, ${frames.size} frames -> ${dir.absolutePath}")
        return
    }
    val frames = sb.frames(steps)
    FxGif.gif(File(out, "$name.gif"), frames, 4)
    FxGif.sheet(frames, File(out, "$name-sheet.png"))
    ImageIO.write(frames.last(), "png", File(out, "$name-end.png"))
    println("sandbox: $name, ${steps.size} steps, ${frames.size} frames, ${"%.1f".format(frames.size * 0.04)} s -> ${File(out, "$name.gif").absolutePath}")
}
