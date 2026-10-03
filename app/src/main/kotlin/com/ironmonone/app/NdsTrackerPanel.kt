package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironmonone.tracker.nds.NdsMoveInfo
import com.ironmonone.tracker.nds.NdsMoveRules
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState

/**
 * The Platinum tracker, rendered through the same PC-tracker blocks the GBA
 * panel uses, so the two consoles look identical rather than each growing its
 * own layout. No sprites yet: Gen 4 art lives in compressed archives inside the
 * ROM rather than in a flat table the emulator exposes.
 */

/**
 * Gen 4's real per-move split, read from the ROM's move table rather than
 * derived from the type. That derivation is a Gen 3 rule and is simply WRONG
 * here: Gen 4 is where the split stopped following the type, so a physical
 * Fire move or a special Rock move exists and must not be mislabelled.
 */
private fun categoryOf(category: String): String = when (category.uppercase()) {
    "PHYSICAL" -> "PHY"
    "SPECIAL" -> "SPE"
    "STATUS" -> "STA"
    else -> "?"
}

/**
 * Who a card's moves are aimed at, the reference's opposingPokemon: its types and
 * held item (MainScreen.setUpMoveEffectiveness), its species for its weight and
 * its battle stat stages (checkForVariableMoves), and the Hidden Power type the
 * tracker keeps for the run. [showEffect] is whether the effectiveness marks
 * draw; the variable powers do not wait for it.
 */
internal data class NdsMoveContext(
    val targetTypes: List<String>,
    val targetHeldItem: Int,
    val hiddenPowerType: String,
    val showEffect: Boolean = true,
    val targetSpecies: Int = 0,
    val targetStages: Map<String, Int> = emptyMap(),
)

/**
 * MainScreen.setUpMoves works out effectiveness only with an opposing Pokemon
 * (lua:547-549), MainScreen.show clears it outside battle (lua:1013-1016), and
 * the setting (SHOW_MOVE_EFFECTIVENESS, on by default) and the pause after each
 * new opponent (moveEffectivenessEnabled) gate it ([show], lua:362). The
 * reference draws one of your Pokemon in battle, the one on the field, against
 * the [opponent] (the locked one while an opponent is locked,
 * Program.getPokemonToDraw lua:545-548), and the opponent against it; only the
 * card of the Pokemon on the field is aimed. Types are the ones the cards show,
 * the randomizer's (the reference reads its static PokemonData instead).
 */
internal fun ndsMoveContext(
    state: NdsTrackerState, card: NdsTrackedMon, enemyCard: Boolean, hiddenPowerType: String, show: Boolean, opponent: NdsTrackedMon? = state.enemy,
    /** Your Pokemon on the field the view shows (DsViewState.shownPlayer): in a double battle not always the first. */
    shownActive: NdsTrackedMon? = null,
): NdsMoveContext? {
    if (!state.inBattle) return null
    val enemy = opponent ?: return null
    val active = shownActive ?: state.playerActive ?: state.party.firstOrNull() ?: return null
    val target = when {
        enemyCard -> active
        card.mon.pid == active.mon.pid -> enemy
        else -> return null
    }
    val types = listOfNotNull(target.info?.type1, target.info?.type2).filter { it.isNotBlank() }
    // Your side's live stages ride on the party entry of the Pokemon on the field (NdsTracker.read);
    // the opponent carries its own.
    val stages = if (enemyCard) (state.party.firstOrNull { it.mon.pid == active.mon.pid } ?: active).statStages else enemy.statStages
    return NdsMoveContext(types, target.mon.heldItem, hiddenPowerType, show, target.mon.species, stages)
}

/** NdsMoveRules.effectivenessDelayFrames at 60 frames a second, for the game [badgeSet] names. */
internal fun ndsEffectivenessDelayMs(badgeSet: String, firstOfBattle: Boolean): Long =
    NdsMoveRules.effectivenessDelayFrames(if (badgeSet.startsWith("BW")) 5 else 4, firstOfBattle) * 1000L / 60

/**
 * BattleHandlerBase._logNewEnemy (lua:126-137): each new opponent turns move
 * effectiveness off (Program.disableMoveEffectiveness) until _setUpDelay frames
 * have passed, a battle's first opponent counted apart on Gen 5. False during
 * that pause. Kept out of PlayScreen, which sits at ART's verifier limit.
 */
@Composable
internal fun rememberDsEffectivenessReady(state: NdsTrackerState?): Boolean {
    var ready by remember { mutableStateOf(true) }
    var enemiesThisBattle by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(state?.inBattle) { if (state?.inBattle != true) enemiesThisBattle = 0 }
    // A double or triple battle's other opponents count too: each new one is _logNewEnemy's for its own slot.
    androidx.compose.runtime.LaunchedEffect(state?.enemy?.mon?.pid, state?.enemyBattlers?.drop(1)?.map { it?.mon?.pid }) {
        if (state?.enemy == null) return@LaunchedEffect
        enemiesThisBattle++
        ready = false
        kotlinx.coroutines.delay(ndsEffectivenessDelayMs(state.badgeSet, enemiesThisBattle == 1))
        ready = true
    }
    return ready
}

/** The mark beside a move's power, or null for none (Drawing: only 0, 1/4, 1/2, 2 and 4 draw). */
internal fun ndsMoveEffect(m: NdsMoveInfo, ctx: NdsMoveContext?): Double? =
    ctx?.takeIf { it.showEffect }?.let { NdsMoveRules.effectiveness(m, it.targetTypes, it.targetHeldItem, it.hiddenPowerType) }?.takeIf { it != 1.0 }

private fun typesOf(p: NdsTrackedMon): List<String> = listOfNotNull(p.info?.type1, p.info?.type2).filter { it.isNotBlank() }

/**
 * One DS move row for [user]'s move [m] (MainScreen.readMovesIntoUI, lua:443-522):
 *
 * - the move table's facts, WT, <HP, VAR and the rest printed as MoveData's
 *   text (lua:507), unless Return's own rule (NdsMoveRules.returnPower) or, with
 *   "Calculate variable damage" on, checkForVariableMoves (lua:420-441, the
 *   opponent from [ctx]) gives a number;
 * - your own Hidden Power named and coloured by the run's type (lua:450-454,
 *   482-485: the type's name for a moment after the arrows, which the row
 *   carries), your own Judgment coloured by its plate (lua:455-460);
 * - the power drawn as STAB in battle (lua:503-506, MoveUtils.isSTAB) and the
 *   effectiveness mark against [ctx]'s target.
 */
internal fun ndsMoveRow(
    m: NdsMoveInfo, user: NdsTrackedMon, pp: Int, ppMax: Int?, inBattle: Boolean, ctx: NdsMoveContext?,
    own: Boolean = true,
    hiddenPowerType: String = StatMarks.DS_HIDDEN_POWER_TYPES[0],
    hiddenPowerJustChanged: Boolean = false,
    calcVariable: Boolean = TrackerOptions.calculateVariableDamage,
): PcMove {
    val hiddenPower = own && m.name == "Hidden Power"
    val plate = if (own && m.name == "Judgment") NdsMoveRules.PLATE_TO_TYPE[user.mon.heldItem] else null
    val shownType = if (hiddenPower) hiddenPowerType else plate ?: m.type
    val target = ctx?.let { NdsMoveRules.Side(weightKg = com.ironmonone.tracker.nds.NdsLogData.weight(it.targetSpecies), statStages = it.targetStages) }
    val self = NdsMoveRules.Side(user.mon.curHp, user.mon.maxHp, com.ironmonone.tracker.nds.NdsLogData.weight(user.mon.species))
    val power = (if (calcVariable) NdsMoveRules.variablePower(m.name, pp, self, target, !own, inBattle) else null)
        ?: NdsMoveRules.returnPower(m.name, user.mon.friendship, !own)
    return PcMove(
        id = m.id,
        name = if (hiddenPower && hiddenPowerJustChanged) hiddenPowerType.lowercase().replaceFirstChar { it.uppercase() } else m.name,
        pp = pp, ppMax = ppMax, power = m.power,
        powerText = power ?: m.powerText.ifEmpty { null }, acc = m.accuracy,
        color = pcTypeColorByName(shownType), typeName = shownType, category = categoryOf(m.category),
        stab = inBattle && NdsMoveRules.isStab(m, typesOf(user), user.mon.heldItem),
        effect = ndsMoveEffect(m, ctx),
        hiddenPowerArrows = hiddenPower,
    )
}

private fun movesOf(
    p: NdsTrackedMon, inBattle: Boolean = false, ctx: NdsMoveContext? = null, own: Boolean = true,
    hiddenPowerType: String = StatMarks.DS_HIDDEN_POWER_TYPES[0], hiddenPowerJustChanged: Boolean = false,
): List<PcMove> =
    p.moves.mapIndexed { i, m ->
        // Base PP raised by this mon's PP Ups, same rule as Gen 3.
        val ppMax = m.pp.takeIf { it > 0 }?.let { base -> base + (base / 5) * p.mon.ppUps.getOrElse(i) { 0 } }
        ndsMoveRow(m, p, p.mon.pp.getOrElse(i) { 0 }, ppMax, inBattle, ctx, own, hiddenPowerType, hiddenPowerJustChanged)
    }

/**
 * The enemy's move rows: what it has used across the WHOLE run, most recent
 * first, this battle's live rows winning where they overlap (a move seen in an
 * earlier battle is drawn from the table at base PP). The panel used to draw
 * only this battle, so a second meeting with a species started blind. A move
 * it may have forgotten since carries MoveUtils.getStars' "*" (readMovesIntoUI,
 * MainScreen.lua:487-490), judged on the four slots shown, each at the level
 * it was last seen at (this battle's, the level now).
 */
internal fun enemyMovesOf(e: NdsTrackedMon, runWide: List<StatMarks.SeenMove>, moveInfoFor: (Int) -> NdsMoveInfo?, inBattle: Boolean = false, ctx: NdsMoveContext? = null): List<PcMove> {
    val now = movesOf(e, inBattle, ctx, own = false)
    val merged = (runWide.mapNotNull { sm -> now.firstOrNull { it.id == sm.id } ?: moveInfoFor(sm.id)?.let { ndsMoveRow(it, e, it.pp, null, inBattle, ctx, own = false) } } + now)
        .distinctBy { if (it.id != 0) it.id.toString() else it.name }
    val seenAt = merged.take(4).map { r -> r.id to (runWide.firstOrNull { it.id == r.id }?.lastLv ?: e.mon.level) }
    val stars = NdsMoveRules.stars(seenAt, e.mon.level, e.moveLevels)
    return merged.mapIndexed { i, r -> if (stars.getOrElse(i) { false }) r.copy(name = r.name + "*") else r }
}

/**
 * CoverageCalcScreen's starting types (CoverageCalcScreen.lua:545-554): each of
 * your moves that is not a status move and has a power, Hidden Power by the
 * run's type.
 */
internal fun ndsCoverageSeed(moves: List<NdsMoveInfo>, hiddenPowerType: String): List<String> =
    CoverageCalc.seedTypes(
        moves.filter { !NdsMoveRules.noPower(it) }
            .map { Triple(it.id, it.category, if (it.name == "Hidden Power") hiddenPowerType else it.type) },
        excluded = emptySet(),
    )

/** MainScreen's move hover (lua:1026-1044, text set at lua:518-520): the move's description, nothing for an empty row. */
internal fun ndsMoveDescription(r: PcMove, gen: Int): String? =
    r.id.takeIf { it > 0 && !r.blank }?.let { com.ironmonone.tracker.nds.NdsLogData.moveDescription(it, gen) }?.takeIf { it.isNotBlank() }

/**
 * MainScreen.setUpEvo: "Lv. N (evo)" on both cards, the evolution picked by
 * gender where it differs, "---" for none. On your own Pokemon a friendship
 * evolution draws the bar: FRIEND filled green column by column at
 * (friendship - base) / (220 - base), and READY in green once it reaches 1.
 * The reference uses 220 here, not the ROM's value, and base 0 where its data
 * sets none. It had been missing from the DS card entirely (2026-09-28).
 */
internal fun ndsEvoLabel(m: com.ironmonone.tracker.nds.Gen4.Mon, own: Boolean): com.ironmonone.tracker.EvoText.Label {
    val raw = com.ironmonone.tracker.nds.NdsLogData.evoFor(m.species, m.isFemale).ifEmpty { "---" }
    if (raw == "FRIEND" && own) {
        val base = com.ironmonone.tracker.nds.NdsLogData.baseFriendship(m.species)
        val progress = (m.friendship - base).toFloat() / (220 - base)
        return if (progress >= 1f) com.ironmonone.tracker.EvoText.Label("READY", com.ironmonone.tracker.EvoText.Tone.READY)
        else com.ironmonone.tracker.EvoText.Label("FRIEND", com.ironmonone.tracker.EvoText.Tone.PLAIN, fill = progress.coerceAtLeast(0f))
    }
    return com.ironmonone.tracker.EvoText.Label(raw, com.ironmonone.tracker.EvoText.Tone.PLAIN)
}

/** The DS card's HP, item and ability lines; a null [hp] draws no HP row at all. */
internal data class NdsHeadLines(val hp: String?, val item: String, val ability: String)

/**
 * MainScreen.setUpMainPokemonInfo, and setEnemySpecificControls for an opponent.
 * Your own Pokemon: "HP: cur/max" (MainScreen.lua:883), the held item by
 * GEN_5_ITEMS with "---" for none (lua:860-889), the ability. An opponent's HP
 * line is hidden (lua:872, pokemonHP.setVisibility(not isEnemy); the "HP: ?/?"
 * it is then given at lua:581 never shows), its item line reads "Total seen"
 * and its ability line "Last level" (lua:600-601). The enemy card showed the
 * opponent's exact HP until 2026-09-29.
 */
internal fun ndsHeadLines(
    m: com.ironmonone.tracker.nds.Gen4.Mon,
    enemy: Boolean,
    itemName: String = "",
    abilityName: String = "",
    encounters: Int = 0,
    lastLevel: Int? = null,
): NdsHeadLines =
    if (enemy) NdsHeadLines(
        hp = null,
        item = "Total seen: $encounters",
        ability = "Last level: " + (lastLevel?.toString() ?: "---"),
    )
    else NdsHeadLines(
        hp = "${m.curHp}/${m.maxHp}",
        item = if (m.heldItem == 0) "---" else itemName,
        ability = abilityName,
    )

private fun typeChipsOf(p: NdsTrackedMon): List<Pair<String, androidx.compose.ui.graphics.Color>> {
    val info = p.info ?: return emptyList()
    val chips = mutableListOf<Pair<String, androidx.compose.ui.graphics.Color>>()
    if (info.type1.isNotBlank()) chips += info.type1 to pcTypeColorByName(info.type1)
    if (info.type2.isNotBlank() && info.type2 != info.type1) {
        chips += info.type2 to pcTypeColorByName(info.type2)
    }
    return chips
}

/**
 * The DS heals box on one card: MainScreen.setUpMiscInfo's healFrame, "Heals:"
 * over "Status items:" (lua:801-809), each opening its list on a tap (the hover,
 * onItemBagInfoHover, lua:265-284), and the Pokecenter counter beside them when
 * it shows ([pokecenter], null when not).
 */
internal data class NdsHealsView(
    val heals: String,
    val status: String,
    val healingList: List<String>,
    val statusList: List<String>,
    val pokecenter: Int?,
    /** Your ACC and EVA stages in battle, beside the heals (accEvaFrame); null when they do not show. */
    val accEva: Pair<Int, Int>? = null,
)

/**
 * Which card carries the heals box and what it reads. The reference shows it on
 * your own Pokemon only (healFrame hidden for an opponent, lua:812), measured
 * against playerPokemon (NdsTrackerState.healsPid), "% " or " HP" by "Bag heals
 * show HP instead" ([showHp]). ACC and EVA (accEvaFrame, setUpMiscInfo lua:791-
 * 793 and setUpStatStages lua:336-358) show there in battle with "Show accuracy
 * and evasion" on ([accEvaOn]): your side's stages, which NdsTracker puts on the
 * Pokemon on the field. The Pokecenter counter (survivalHealFrame, lua:794-799) needs its
 * setting and gives way to both the tourney points and ACC and EVA.
 */
internal fun ndsHealsView(
    state: NdsTrackerState, card: NdsTrackedMon, showHp: Boolean,
    pokecenterOn: Boolean, pokecenterCount: Int, tourneyOn: Boolean, accEvaOn: Boolean,
    /**
     * Program.getHealingTotals' playerPokemon: the Pokemon of yours the view shows (DsViewState.shownPlayer), which in a
     * double battle is not always the first on the field. The heals are a share of its max HP.
     */
    shownCarrier: NdsTrackedMon? = null,
): NdsHealsView? {
    val carrier = shownCarrier ?: state.playerPokemon
    if (carrier == null || card.mon.pid != carrier.mon.pid) return null
    val maxHp = card.mon.maxHp
    val stages = carrier.statStages
    val accEva = if (accEvaOn && state.inBattle) (stages["ACC"] ?: 6) to (stages["EVA"] ?: 6) else null
    return NdsHealsView(
        heals = com.ironmonone.tracker.nds.NdsHeals.healsLine(state.healingItems, maxHp, showHp),
        status = com.ironmonone.tracker.nds.NdsHeals.statusLine(state.statusItems),
        healingList = com.ironmonone.tracker.nds.NdsHeals.healingList(state.healingItems),
        statusList = com.ironmonone.tracker.nds.NdsHeals.statusList(state.statusItems),
        pokecenter = pokecenterCount.takeIf { pokecenterOn && accEva == null && !tourneyOn },
        accEva = accEva,
    )
}

/**
 * The moves header on both cards, MoveUtils.getMoveHeader (MoveUtils.lua:134-149), which MainScreen.setUpMoves sets for
 * whichever Pokemon is drawn, the opponent included (lua:524-526): "Moves: 3/12 (14)", the level-up moves learned by its
 * level, all of them, and the next one's level until every one is learned. The opponent's card said only "Moves", and
 * your own had the Gen 3 tracker's wording, no colon (rc32 audit P2 #35). The levels are the bundled table both trackers
 * use, so nothing hidden is shown. "Moves" alone for a species the table lacks.
 */
internal fun ndsMovesHeader(m: NdsTrackedMon): String =
    if (m.movesTotal > 0) "Moves: ${m.movesLearned}/${m.movesTotal}" + (m.nextMoveLevel?.let { " ($it)" } ?: "") else "Moves"

/**
 * MainScreen.onPokemonLevelHover / setUpEXPBar (lua:134-145, 323-334): your own
 * Pokemon's bar fraction while its level is held, when "Experience bar" is on
 * ([on]); the opponent's card never has one. By the species' growth rate from the
 * run's sidecar (rc32 audit P3 #118): Fluctuating, the reference's only curve, when a
 * sidecar from before the rate was written gives none, and no bar where there is no
 * sidecar at all (a game not randomized here), whose curves this cannot know.
 */
internal fun ndsExpFraction(p: NdsTrackedMon, on: Boolean): Double? {
    val info = p.info ?: return null
    if (!on) return null
    val rate = info.growthRate.takeIf { it >= 0 } ?: com.ironmonone.tracker.nds.NdsExperience.FLUCTUATING
    return com.ironmonone.tracker.nds.NdsExperience.fraction(p.mon.level, p.mon.experience, rate)
}

/** IconDrawer LEFT_ARROW and RIGHT_ARROW (IconDrawer.lua:998-1021), 3 by 5, in top box text. */
private val DS_LEFT_ARROW = listOf("001", "010", "100", "010", "001")
private val DS_RIGHT_ARROW = listOf("100", "010", "001", "010", "100")

@Composable
private fun NdsHealsBlock(v: NdsHealsView, onList: (String, List<String>) -> Unit, onPokecenter: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.rp, vertical = 1.rp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            PixText(v.heals, PcRef.FONT, Pc.Text, Modifier.clickable { onList("Healing", v.healingList) })
            PixText(v.status, PcRef.FONT, Pc.Text, Modifier.clickable { onList("Status", v.statusList) })
        }
        v.accEva?.let { (acc, eva) ->
            // accEvaFrame: "ACC" over "EVA", each with its stage marks.
            Column(Modifier.width(30.rp)) {
                PcStatRow("ACC", "", acc)
                PcStatRow("EVA", "", eva)
            }
        }
        v.pokecenter?.let { n ->
            // survivalHealFrame: the heart (images/icons/heart.png) over "<", the count, ">".
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                remember { PcAssets.icon(ctx, "heart") }?.let { heart ->
                    androidx.compose.foundation.Image(heart, "Pokecenter heals", Modifier.width(12.rp).height(10.rp),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PcPixelImage(DS_LEFT_ARROW, Pc.Text, Modifier.clickable { onPokecenter(false) }.padding(horizontal = 2.rp))
                    PixText(n.toString(), PcRef.FONT, Pc.Text)
                    PcPixelImage(DS_RIGHT_ARROW, Pc.Text, Modifier.clickable { onPokecenter(true) }.padding(horizontal = 2.rp))
                }
            }
        }
    }
}

@Composable
private fun NdsPartyCard(
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    onTypeDefenses: ((String, String, String) -> Unit)? = null,
    p: NdsTrackedMon,
    /** The heals box, on the card of the Pokemon it is measured against (ndsHealsView). */
    heals: NdsHealsView? = null,
    /** A heals list opened: ("Healing" or "Status", its lines). */
    onHealsList: (String, List<String>) -> Unit = { _, _ -> },
    onPokecenter: (Boolean) -> Unit = {},
    /** MainScreen's hover text on your ability and held item; a tap on a phone. */
    onInfo: ((String, String, String) -> Unit)? = null,
    gen: Int = 4,
    /** Its moves against the opponent, when this is the Pokemon on the field (ndsMoveContext). */
    moveCtx: NdsMoveContext? = null,
    /** Program.isInBattle, for the STAB colour. */
    inBattle: Boolean = false,
    /** Tracker.getCurrentHiddenPowerType, and whether the arrows were just pressed. */
    hiddenPowerType: String = StatMarks.DS_HIDDEN_POWER_TYPES[0],
    hiddenPowerJustChanged: Boolean = false,
    /** MainScreen.onChangeHiddenPower: true for ">" (forward). */
    onHiddenPower: ((Boolean) -> Unit)? = null,
    /** Past the run's BST line, still as it joined (BstRule): the X. */
    bstBroken: Boolean = false,
    onBstTap: (() -> Unit)? = null,
    /** This run's banned moves (MoveRule): the X on a move and the line on its card. */
    markMove: (PcMove) -> PcMove = { it },
) {
    val m = p.mon
    val context = androidx.compose.ui.platform.LocalContext.current
    val sprite = remember(m.species, m.shiny, m.form) { RomFormSprites.sprite(context, m.species, m.form, m.shiny) ?: PcAssets.dsSprite(context, m.species, m.shiny) }
    // MainScreen.lua:860-889: GEN_5_ITEMS[heldItem].name, "---" for no item (entry 0)
    // and blank for an id the table lacks.
    val lines = ndsHeadLines(m, enemy = false, itemName = p.itemName, abilityName = p.abilityName)
    PcMonCard(head = {
        PcHeadBlock(
            name = p.speciesName + (if (m.shiny) " *" else ""),
            status = if (m.curHp <= 0) "FNT" else p.statusCondition,
            level = m.level, curHp = m.curHp, maxHp = m.maxHp,
            typeChips = typeChipsOf(p),
            onTypesTap = p.info?.let { i -> onTypeDefenses?.let { cb -> { cb(p.speciesName, i.type1, i.type2) } } },
            hpText = lines.hp, showHp = lines.hp != null,
            itemLine = lines.item,
            abilityLine = lines.ability,
            onAbilityTap = onInfo?.let { cb -> { cb(p.abilityName, "Ability",
                com.ironmonone.tracker.nds.NdsLogData.abilityDescription(m.abilityId, gen)) } },
            onItemTap = onInfo?.takeIf { m.heldItem != 0 && p.itemName.isNotBlank() }?.let { cb -> { cb(p.itemName, "Held item",
                com.ironmonone.tracker.nds.NdsLogData.heldItemDescription(m.heldItem, m.nature)) } },
            evo = ndsEvoLabel(m, own = true),
            levelPrefix = "Lv. ",
            holdExpFraction = ndsExpFraction(p, TrackerOptions.dsExpBar),
            sprite = sprite,
            // The Walking Pals icon by its national number, behind the GBA panel's two switches; the still sprite
            // with them off, with no sheet, or for an egg (whose species is what will hatch).
            iconSpecies = if (m.isEgg) 0 else m.species,
            iconDex = WalkingPals.Dex.NATIONAL,
            // A shiny walks as its shiny (Blake, 2026-10-03).
            iconLook = WalkingPals.Look(shiny = m.shiny),
            belowHead = heals?.let { v -> { NdsHealsBlock(v, onHealsList, onPokecenter) } },
        ) {
            PcStatRow("HP", "${m.maxHp}", p.statStages["HP"], nature = m.nature)
            PcStatRow("ATK", "${m.atk}", p.statStages["ATK"], nature = m.nature)
            PcStatRow("DEF", "${m.def}", p.statStages["DEF"], nature = m.nature)
            PcStatRow("SPA", "${m.spAtk}", p.statStages["SPA"], nature = m.nature)
            PcStatRow("SPD", "${m.spDef}", p.statStages["SPD"], nature = m.nature)
            PcStatRow("SPE", "${m.spe}", p.statStages["SPE"], nature = m.nature)
            PcStatRow("BST", p.info?.bst?.toString() ?: "?", broken = bstBroken, onBrokenTap = onBstTap)
        }
    }) {
        PcMovesSection(
            movesOf(p, inBattle, moveCtx, own = true, hiddenPowerType, hiddenPowerJustChanged).map(markMove),
            header = ndsMovesHeader(p),
            onHeaderTap = onMoveHistory?.let { cb -> { cb(p.mon.species, p.speciesName, p.mon.level) } },
            // A banned move's card opens with why (MoveRule), even for a move with no description.
            onMoveTap = onInfo?.let { cb -> { r -> listOfNotNull(r.banLine, ndsMoveDescription(r, gen)).joinToString("\n\n").ifEmpty { null }?.let { d -> cb(r.name.removeSuffix("*"), "", d) } } },
            onHiddenPower = onHiddenPower,
        )
    }
}

@Composable
private fun NdsEnemyCard(
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    onTypeDefenses: ((String, String, String) -> Unit)? = null,
    e: NdsTrackedMon,
    /** Tracker.getLastLevelSeen, or null before any battle with this species has ended. */
    lastLevel: Int?,
    marks: IntArray,
    encounters: Int,
    onCycleMark: (Int) -> Unit,
    note: String,
    onEditNote: () -> Unit,
    /** Every move this species has used this run, most recent first (the reference's trackMove). */
    movesSeenRunWide: List<StatMarks.SeenMove> = emptyList(),
    moveInfoFor: (Int) -> NdsMoveInfo? = { null },
    /** The encounter frame's pin and "seen/total" (wild battles in areas with vanilla data). */
    encounterLine: (@Composable () -> Unit)? = null,
    /** Its moves against your Pokemon on the field (ndsMoveContext). */
    moveCtx: NdsMoveContext? = null,
    /** A move's description on a tap: (name, description). */
    onMoveInfo: ((String, String) -> Unit)? = null,
    gen: Int = 4,
    /** The lock icon: null hides it ("Enable enemy locking" off), else whether this opponent is locked. */
    lock: Boolean? = null,
    onLock: () -> Unit = {},
    /** A wild one past the run's BST line (BstRule): the X. */
    bstBroken: Boolean = false,
    onBstTap: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sprite = remember(e.mon.species, e.mon.form) { RomFormSprites.sprite(context, e.mon.species, e.mon.form, false) ?: PcAssets.dsSprite(context, e.mon.species, false) }
    // MainScreen.setEnemySpecificControls: no HP row, the item line reads "Total seen"
    // and the ability line "Last level" (an ability it reveals goes into its note,
    // Tracker.trackAbilityNote).
    val lines = ndsHeadLines(e.mon, enemy = true, encounters = encounters, lastLevel = lastLevel)
    PcMonCard(head = {
        PcHeadBlock(
            name = e.speciesName,
            status = if (e.mon.curHp <= 0) "FNT" else e.statusCondition,
            level = e.mon.level,
            curHp = e.mon.curHp, maxHp = e.mon.maxHp,
            typeChips = typeChipsOf(e),
            onTypesTap = e.info?.let { i -> onTypeDefenses?.let { cb -> { cb(e.speciesName, i.type1, i.type2) } } },
            hpText = lines.hp, showHp = lines.hp != null,
            itemLine = lines.item,
            abilityLine = lines.ability,
            evo = ndsEvoLabel(e.mon, own = false),
            levelPrefix = "Lv. ",
            sprite = sprite,
            iconSpecies = if (e.mon.isEgg) 0 else e.mon.species,
            iconDex = WalkingPals.Dex.NATIONAL,
            iconLook = WalkingPals.Look(shiny = e.mon.shiny),
        ) {
            // Enemy stats are unknown: this column is the notebook.
            PcMarkColumn(marks, onCycleMark)
            PcStatRow("BST", e.info?.bst?.toString() ?: "?", broken = bstBroken, onBrokenTap = onBstTap)
            // Live stage chevrons, the reference's in-battle markers.
            e.statStages.filterKeys { it != "ACC" && it != "EVA" }
                .filterValues { it != 6 }
                .forEach { (n, st) -> PcStatRow(n, "", st) }
        }
    }) {
        // Beside the head (a wide card) the side column's top is the card's own edge.
        if (!LocalMovesBeside.current) androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(1.dp).background(TrackerLook.divider))
        // infoBottomFrame (MainScreenUIInitializer.lua:657-718): the lock icon, then the encounter frame.
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            lock?.let { DsLockIcon(it, onLock); Spacer(Modifier.width(3.dp)) }
            encounterLine?.invoke()
        }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(1.dp).background(TrackerLook.divider))
        // The reference's tracked moves for this opponent: only what it has used, this run.
        PcMovesSection(enemyMovesOf(e, movesSeenRunWide, moveInfoFor, inBattle = true, ctx = moveCtx), header = ndsMovesHeader(e),
            onHeaderTap = onMoveHistory?.let { cb -> { cb(e.mon.species, e.speciesName, e.mon.level) } },
            onMoveTap = onMoveInfo?.let { cb -> { r -> ndsMoveDescription(r, gen)?.let { d -> cb(r.name.removeSuffix("*"), d) } } })
        PcNoteRow(note, onEditNote)
    }
}

/**
 * The DS tracker's closing lines, from PlaythroughConstants.RUN_OVER_MESSAGES.
 *
 * The DS tracker does something the GBA one does not: it works out HOW the run
 * ended and picks the line from that. Losing to a Shedinja gets its own set;
 * so does losing to something a hundred BST below you. The "exclusive" causes
 * draw only from their own list, while a standard loss draws from the twelve
 * defaults - which is why these are separate lists rather than one pool.
 */
private val NDS_RUN_OVER_MESSAGES: Map<com.ironmonone.tracker.nds.NdsRunOver, List<String>> =
    mapOf(
        com.ironmonone.tracker.nds.NdsRunOver.WON to listOf(
            "Congratulations!",
            "My god... you actually did it...",
            "I'm speechless.",
            "The end of a long, arduous journey...",
            "On this day, the planets aligned...",
        ),
        com.ironmonone.tracker.nds.NdsRunOver.SHEDINJA to listOf(
            "Never feels good to lose to that.",
            "There are over 20 fire moves in the game, and you didn't roll a single one.",
            "It was bound to happen at some point.",
            "The one Pokemon you didn't want to see...",
        ),
        com.ironmonone.tracker.nds.NdsRunOver.IMPOSTER to listOf(
            "Sometimes, you really just can't face yourself.",
            "Looking in the mirror really is that painful.",
            "Dark Link was a lot easier than this...",
            "Does this mean we can ban it now?",
        ),
        com.ironmonone.tracker.nds.NdsRunOver.ENEMY_LOWER_BST to listOf(
            "Sometimes, the weaker triumph.",
            "A surprising outcome.",
            "Miracles really can happen.",
            "I don't think anyone saw that coming.",
            "Huh?",
            "Surely that Pokemon had Huge Power.",
        ),
        com.ironmonone.tracker.nds.NdsRunOver.STANDARD to listOf(
            "There's always next time...",
            "Some things were just not meant to be.",
            "Oh well.",
            "Could have been worse, I guess. Or not.",
            "Against all odds... you did not triumph.",
            "How unfortunate.",
            "That's just the way it goes sometimes.",
            "You should definitely pick the left ball next attempt.",
            "Having fun yet?",
            "The house always wins.",
            "Anything that can go wrong, will go wrong.",
            "Looks like your luck finally ran out.",
        ),
    )

/** The pool the DS tracker draws its closing line from for [cause]; the popup uses the same one. */
internal fun ndsRunOverLines(cause: com.ironmonone.tracker.nds.NdsRunOver): List<String> =
    NDS_RUN_OVER_MESSAGES[cause] ?: NDS_RUN_OVER_MESSAGES.getValue(com.ironmonone.tracker.nds.NdsRunOver.STANDARD)

/**
 * The DS end-of-run card. Same shape as the GBA one, but the message comes from
 * the cause rather than a single pool.
 *
 * The line is DeathQuotes' draw for this run, one per run and the same one the
 * game over box shows: a message that reshuffles on every tracker poll is unreadable.
 */
@Composable
fun PcNdsRunOver(
    cause: com.ironmonone.tracker.nds.NdsRunOver,
    attempt: Int,
    party: List<NdsTrackedMon>,
) {
    val won = cause == com.ironmonone.tracker.nds.NdsRunOver.WON
    val lines = NDS_RUN_OVER_MESSAGES[cause]
        ?: NDS_RUN_OVER_MESSAGES.getValue(com.ironmonone.tracker.nds.NdsRunOver.STANDARD)
    val line = DeathQuotes.shown(DeathQuotes.dsSource(cause), attempt, lines, forLoss = !won)
    PcCard {
        Column(Modifier.fillMaxWidth().padding(6.dp)) {
            PixText(if (won) "R u n  W o n" else "R u n  O v e r", 11, Pc.Gold)
            Spacer(Modifier.height(5.dp))
            Row {
                PixText("Attempt:", 8, Pc.Text, Modifier.width(66.dp))
                PixText("$attempt", 8, Pc.Text)
            }
            Spacer(Modifier.height(5.dp))
            PixText(line, 9, if (won) Pc.Positive else Pc.Negative)
        }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(1.dp).background(Pc.Border))
        Column(Modifier.padding(4.dp)) {
            PixText("Final team", 8, Pc.Text)
            Spacer(Modifier.height(3.dp))
            party.forEach { p ->
                val m = p.mon
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    PixText(
                        p.speciesName, 8,
                        if (m.curHp == 0) Pc.Negative else Pc.Text,
                        Modifier.weight(1f),
                    )
                    PixText("Lv.${m.level}", 7, Pc.Dim, Modifier.width(46.dp))
                    PixText(
                        "${m.maxHp}/${m.atk}/${m.def}/${m.spAtk}/${m.spDef}/${m.spe}",
                        7, Pc.Dim,
                    )
                }
            }
        }
    }
}

@Composable
fun NdsTrackerPanel(
    /** The startup favorites, their icons shown before a party exists, as the DS tracker's title screen shows them. */
    favoriteLine: FavoritesShown? = null,
    /** RandomBallScreen: 1, 2 or 3 for Left, Middle, Right; null hides it (option off, or no tracker yet). */
    randomBall: Int? = null,
    /** Move History for a card: (species, name, level). */
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    /** Type Defenses for a card: (name, type1, type2) as the sidecar names them. */
    onTypeDefenses: ((String, String, String) -> Unit)? = null,
    state: NdsTrackerState?,
    modifier: Modifier = Modifier,
    onFlee: () -> Unit = {},
    enemyMarks: IntArray = IntArray(StatMarks.COUNT),
    enemyEncounters: Int = 0,
    onCycleMark: (Int) -> Unit = {},
    enemyNote: String = "",
    onEditNote: () -> Unit = {},
    attempt: Int = 0,
    /**
     * Not drawn on the main panel since 2026-09-30 (IronMON rules check): the DS tracker's main screen has no coverage,
     * only its Coverage Calc screen, which is COVERAGE CALC in Tracker Setup here. Kept so Play's calls stand.
     */
    @Suppress("UNUSED_PARAMETER") coverage: Map<Double, List<Int>> = emptyMap(),
    /** The enemy's ability, once a battle trigger has revealed it this run. */
    /** The opponent's last level seen (the enemy card's ability line). */
    enemyLastLevel: Int? = null,
    movesSeenRunWide: List<StatMarks.SeenMove> = emptyList(),
    moveInfoFor: (Int) -> NdsMoveInfo? = { null },
    /** Opens the tracker's gear (the reference's SettingsGear). */
    onGear: (() -> Unit)? = null,
    /** After SETUP in the first row: landscape's corner arrow (TrackerCornerMenu); null in portrait. */
    headerTrailing: (@Composable () -> Unit)? = null,
    /** TimerScreen: the run clock, when the option is on. */
    timer: RunTimer? = null,
    /** LOCATION_DATA encounters for the current area (null hides the encounter frame). */
    encounterArea: com.ironmonone.tracker.nds.NdsEncounterTables.Area? = null,
    /** Tracker.getEncounterData(area).encountersSeen: species to the levels met at. */
    encountersSeen: Map<Int, List<Int>> = emptyMap(),
    speciesNameOf: (Int) -> String = { "#$it" },
    /** Tracker.getCurrentHiddenPowerType: the run's one Hidden Power type (StatMarks.dsHiddenPowerType). */
    hiddenPowerType: String = StatMarks.DS_HIDDEN_POWER_TYPES[0],
    /** False during the pause after a new opponent (BattleHandlerBase._logNewEnemy, moveEffectivenessEnabled). */
    effectivenessReady: Boolean = true,
    /** Tracker.increaseHiddenPowerType / decreaseHiddenPowerType (StatMarks.stepDsHiddenPower): true for ">". */
    onStepHiddenPower: ((Boolean) -> Unit)? = null,
    /** Tracker.getPokecenterCount (StatMarks.dsPokecenterCount), and its "<" and ">" (true for ">"). */
    pokecenterCount: Int = 10,
    onPokecenter: ((Boolean) -> Unit)? = null,
    /** Your Pokemon AND the opponent together instead of swapping between them: landscape, as on GBA. */
    stackBoth: Boolean = false,
    /** Kaizo's BST line (BstRule), from the run in Play; a test hands its own. */
    bstLines: BstRule.Lines? = bstLinesInPlay(attempt),
    /** What each of your Pokemon joined the run as, beside the run's marks. */
    joinedForms: JoinedForms? = joinedFormsInPlay(attempt, bstLines),
    /** The run's banned moves (MoveRule), from the run in Play; a test hands its own. */
    moveRules: MoveRule.Rules? = moveRulesInPlay(attempt),
) {
    // The run-over card is a Kaizo IronMON run's only (PlayRules, 2026-09-30).
    val ironmonOver = ironmonGameOverCard(state?.runOver != null)
    // SHOW_MOVE_EFFECTIVENESS (on by default, MiscConstants.lua:41): the gear's "Show move effectiveness".
    val showEffectiveness = TrackerOptions.showMoveEffectiveness && effectivenessReady
    // Same reference canvas as the GBA panel. Without it this panel would keep
    // the shared boxes' new REFERENCE-pixel sizes at 1dp each, i.e. the right
    // proportions at the wrong scale - the two trackers must not drift apart.
    var dsInfo by remember { mutableStateOf<Triple<String, String?, String?>?>(null) }
    // MainScreen.onChangeHiddenPower: the row names the new type for 90 frames, scaled to the
    // emulator's speed, so a second and a half (justChangedHiddenPower, lua:296-310).
    var hiddenPowerChangedAt by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(hiddenPowerChangedAt) {
        if (hiddenPowerChangedAt != 0L) { kotlinx.coroutines.delay(1500); hiddenPowerChangedAt = 0L }
    }
    var encounterOpen by remember { mutableStateOf(false) }
    if (encounterOpen) encounterArea?.let { a ->
        DsEncounterDialog(a, encountersSeen, speciesNameOf) { encounterOpen = false }
    } ?: run { encounterOpen = false }
    dsInfo?.let { (title, sub, body) -> PcInfoDialog(title, sub, body?.ifBlank { null }) { dsInfo = null } }
    PcCanvas(modifier.fillMaxWidth()) {
      // The Main background colour, and the player's image over it (TrackerBackdrop.kt).
      Column(Modifier.fillMaxWidth().then(trackerBackdrop()).padding(PcRef.MARGIN.rp)) {
          // The reference's gear sits at the top of the tracker screen; SETUP is its NavigationMenu.ButtonSetup.
          // In a battle it ends the battle banner (2026-10-02, "wasted space"); otherwise it has the top bar.
          // The attempt, in a Kaizo IronMON run (2026-10-02: "the tracker needs attempt count").
          val attemptShown = attempt.takeIf { !LocalAttemptInTitle.current && ironmonRunInPlay(it) }
          // What each of your Pokemon joined the run as (BstRule), read from the whole party so one that evolves
          // out of sight is known by what it was (2026-10-02).
          // A tap on a red BST: its value, the line, and whose it is (null key: a wild one).
          var bstSheet by remember { mutableStateOf<Triple<Int, Int, Long?>?>(null) }
          var joinedVersion by remember { mutableIntStateOf(0) }
          bstSheet?.let { (bst, line, key) ->
              BstRuleSheet(bst, line, own = key != null, onEvolved = { key?.let { joinedForms?.markEvolved(it) }; joinedVersion++; bstSheet = null }) { bstSheet = null }
          }
          if (joinedForms != null && state != null) {
              val party = state.party.map { it.mon.pid to it.mon.species }
              androidx.compose.runtime.LaunchedEffect(joinedForms, party) { party.forEach { (pid, sp) -> joinedForms.joinedAs(pid, sp) } }
          }
          val bannerShows = state != null && state.inBattle &&
              !(state.runOver != null && state.runOver != com.ironmonone.tracker.nds.NdsRunOver.WON && ironmonOver)
          onGear?.takeIf { !bannerShows }?.let { g ->
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                  attemptShown?.let {
                      PixText("ATTEMPT $it", PcRef.FONT - 1, Pc.Text, weight = androidx.compose.ui.text.font.FontWeight.Medium)
                  }
                  Spacer(Modifier.weight(1f))
                  // RepelDrawer: the DS tracker draws its own three item icons.
                  if (TrackerOptions.showRepel && state != null && !state.inBattle && state.repelSteps > 0) {
                      PcRepelBar(state.repelSteps, state.repelDuration, dsIcons = true)
                      Spacer(Modifier.width(6.dp))
                  }
                  TrackerGearButton { g() }
                  headerTrailing?.invoke()
              }
              Spacer(Modifier.height(2.rp))
          }
          timer?.let { RunTimerLine(it); Spacer(Modifier.height(3.dp)) }
          NuzlockeNdsPanel(state) // the Nuzlocke run's area, cap and graveyard, when this game has one (2026-09-30)
        when {
            state == null -> PcCard {
                PixText("DS tracker: waiting for the game...", 8, Pc.Dim,
                    Modifier.padding(6.dp))
            }

            !state.located && !state.inBattle -> PcCard {
                Column(Modifier.padding(6.dp)) {
                    // The memory address line was a debug read-out in front of players (audit, 2026-09-27).
                    PixText("No Pokemon yet. The tracker fills in when you get your first one.", 8, Pc.Dim, wrap = true)
                    // The random ball row, and the favorites' icons beside it where they fit (FavoriteIcons).
                    DsBallAndFavorites(randomBall, hgss = state.badgeSet == "HGSS", favoriteLine?.icons.orEmpty())
                }
            }

            // A lost run goes above the team, not under it, while its battle lasts. A win
            // ends the run as the final battle ends: the popup (RunOverScreen) says so and
            // the team stays, as the reference's main screen does.
            state.runOver != null && state.runOver != com.ironmonone.tracker.nds.NdsRunOver.WON && ironmonOver -> {
                // Bound locally: runOver comes from another module, so it
                // cannot be smart-cast in place.
                val cause = state.runOver!!
                PcNdsRunOver(cause, attempt, state.party)
            }

            else -> {
                // ONE Pokemon at a time, as the DS tracker's main screen draws it
                // (Program.getPokemonToDraw, lua:537-555): your playerPokemon, or the
                // opponent, the locked one while one is locked. Portrait swaps them from
                // the battle banner (Start, CHANGE_VIEW, in the reference); landscape
                // stacks both, yours on top, as the GBA panel does. This used to stack the
                // opponent over all six of your cards.
                val view = dsView
                // The animated icons do not walk in battle, as on the GBA panel (Battle.inActiveBattle).
                androidx.compose.runtime.SideEffect { SpriteMotion.inBattle = state.inBattle }
                androidx.compose.runtime.LaunchedEffect(attempt) { view.forAttempt(attempt) }
                androidx.compose.runtime.LaunchedEffect(state.inBattle, view.locked) { view.onRead(state) }
                androidx.compose.runtime.LaunchedEffect(effectivenessReady) { view.onPause(effectivenessReady, state, TrackerOptions.dsAutoSwapToEnemy) }
                val shownEnemy = view.shownEnemy(state)
                // Your Pokemon the view shows: in a double or triple battle, the slot it is on (DsViewState.shownPlayer).
                val shownPlayer = view.shownPlayer(state)
                // With no Pokemon of yours read (a battle before the party is found), the opponent shows.
                val showEnemy = shownEnemy != null && (stackBoth || view.viewingEnemy || shownPlayer == null)
                // A single battle has no swap when both are on screen; none during the pause after a new opponent. A
                // double or triple battle's swap walks the Pokemon on the field (DsViewState.next).
                val onSwap = if (view.canSwap(state, effectivenessReady, stackBoth)) { { view.swap(state, effectivenessReady, stackBoth) } } else null
                val side = view.sideWords(state, stackBoth)
                if (state.inBattle) {
                    PcBattleBanner(state.isWildBattle, onFlee, viewingOwn = if (side == null) !showEnemy else view.offersFoe(state, stackBoth),
                        onSwapView = onSwap, onGear = onGear, trailing = headerTrailing, attempt = attemptShown,
                        side = side, swapSpoken = view.swapSpoken(state, stackBoth))
                    Spacer(Modifier.height(2.rp))
                } else if (onSwap != null) {
                    // After the battle a locked opponent stays in reach (readMemory keeps it while locked).
                    DsLockedBanner(viewingOwn = !showEnemy, onSwap)
                    Spacer(Modifier.height(4.dp))
                }
                if (stackBoth || !showEnemy) shownPlayer?.let { p ->
                    NdsPartyCard(onMoveHistory = onMoveHistory, onTypeDefenses = onTypeDefenses, p,
                        heals = ndsHealsView(state, p, TrackerOptions.healsWhole, TrackerOptions.dsPokecenterHeals,
                            pokecenterCount, TrackerOptions.tourneyTracker, TrackerOptions.dsAccEva, shownCarrier = p),
                        onHealsList = { kind, lines ->
                            dsInfo = Triple("$kind Items", "", lines.joinToString("\n").ifEmpty { com.ironmonone.tracker.nds.NdsHeals.emptyText(kind) })
                        },
                        onPokecenter = { up -> onPokecenter?.invoke(up) },
                        onInfo = { t, sub, body -> dsInfo = Triple(t, sub, body) },
                        moveCtx = ndsMoveContext(state, p, enemyCard = false, hiddenPowerType, showEffectiveness, opponent = shownEnemy, shownActive = p.takeIf { state.playerBattlers.size > 1 }),
                        inBattle = state.inBattle,
                        hiddenPowerType = hiddenPowerType, hiddenPowerJustChanged = hiddenPowerChangedAt != 0L,
                        onHiddenPower = onStepHiddenPower?.let { step -> { forward -> step(forward); hiddenPowerChangedAt = System.currentTimeMillis() } },
                        gen = if (state.badgeSet.startsWith("BW")) 5 else 4,
                        bstBroken = joinedVersion >= 0 && BstRule.ownBreaks(p.info?.bst, bstLines, joinedForms, p.mon.pid, p.mon.species),
                        onBstTap = { bstSheet = Triple(p.info?.bst ?: 0, bstLines?.own ?: 0, p.mon.pid) },
                        markMove = MoveRule.ndsMark(moveRules, p, state))
                }
                if (showEnemy) shownEnemy?.let {
                    // readTrackedEncountersIntoLabel: only in a wild battle, and only where the
                    // area has vanilla data.
                    val area = encounterArea?.takeIf { state.isWildBattle }
                    NdsEnemyCard(onMoveHistory = onMoveHistory, onTypeDefenses = onTypeDefenses, it, enemyLastLevel, enemyMarks,
                        enemyEncounters, onCycleMark, enemyNote, onEditNote,
                        movesSeenRunWide, moveInfoFor,
                        encounterLine = area?.let { a -> { DsEncounterLine(a, encountersSeen.size) { encounterOpen = true } } },
                        moveCtx = ndsMoveContext(state, it, enemyCard = true, hiddenPowerType, showEffectiveness, opponent = it, shownActive = shownPlayer.takeIf { state.playerBattlers.size > 1 }),
                        onMoveInfo = { n, d -> dsInfo = Triple(n, "", d) },
                        gen = if (state.badgeSet.startsWith("BW")) 5 else 4,
                        lock = (view.locked != null).takeIf { TrackerOptions.dsEnemyLocking },
                        onLock = { view.toggleLock(state, TrackerOptions.dsEnemyLocking) },
                        bstBroken = state.isWildBattle && BstRule.wildBreaks(it.info?.bst, bstLines),
                        onBstTap = { bstSheet = Triple(it.info?.bst ?: 0, bstLines?.wild ?: 0, null) })
                }
                PcCarousel(
                    inBattle = state.inBattle,
                    badges = state.badges,
                    badgeSet = state.badgeSet,
                    note = enemyNote,
                    onEditNote = onEditNote,
                    // NdsEnemyCard ends with the note row, as the DS tracker's card does.
                    notesInCard = true,
                    encounters = enemyEncounters,
                    leagueBeaten = state.leagueBeaten,
                )
            }
        }
    }
    }
}

/**
 * MainScreen.updateBadgeLayout (lua:1348-1377) for HeartGold and SoulSilver: the badge rows as
 * (badge bits, badge art set), Johto in bits 0-7 of [badges] and Kanto in 8-15. With "Show both
 * badge sets" on, the reference's default (MiscConstants.lua:82), both rows show from the start,
 * Kanto first when it is the primary set; with it off there is one row, Johto until the League is
 * beaten and Kanto after (Program.lua:583-586, MainScreen.lua:1285-1295), and the primary set
 * does not come into it. The Kanto row used to wait for the first Kanto badge.
 */
internal fun hgssBadgeRows(badges: Int, showBoth: Boolean, kantoFirst: Boolean, leagueBeaten: Boolean): List<Pair<Int, String>> {
    val johto = (badges and 0xFF) to "HGSS_J"
    val kanto = ((badges shr 8) and 0xFF) to "HGSS_K"
    return when {
        showBoth -> if (kantoFirst) listOf(kanto, johto) else listOf(johto, kanto)
        leagueBeaten -> listOf(kanto)
        else -> listOf(johto)
    }
}

/** The part of the version after "+": the commit this build came from. */
@androidx.compose.runtime.Composable
private fun appBuildId(): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()?.substringAfter("+", "?") ?: "?"
}

/**
 * RandomBallScreen.lua, drawn here: three balls with the rolled one lit, and
 * "Random ball: Left" under them. HGSS shows its three coloured balls (blue,
 * green, red) with the middle one raised, as the lab table has them; the
 * other DS games show three red balls in a row.
 */
@androidx.compose.runtime.Composable
fun RandomBallRow(pick: Int, hgss: Boolean) {
    val labels = listOf("Left", "Middle", "Right")
    val colours = if (hgss) listOf(androidx.compose.ui.graphics.Color(0xFF3F7FD6), androidx.compose.ui.graphics.Color(0xFF3FA65A), androidx.compose.ui.graphics.Color(0xFFD63F3F))
        else List(3) { androidx.compose.ui.graphics.Color(0xFFD63F3F) }
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            for (i in 1..3) {
                val lit = i == pick
                val raise = if (hgss && i == 2) 8.dp else 0.dp
                androidx.compose.foundation.Canvas(Modifier.padding(start = if (i == 1) 0.dp else 10.dp, bottom = raise).size(16.dp)) {
                    val r = size.minDimension / 2f
                    val c = androidx.compose.ui.geometry.Offset(r, r)
                    val body = if (lit) colours[i - 1] else Pc.Dim
                    drawCircle(body, r, c)
                    drawArc(if (lit) androidx.compose.ui.graphics.Color.White else Pc.Page, 0f, 180f, true, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, size.height))
                    drawCircle(Pc.Ground, r * 0.28f, c)
                    drawCircle(if (lit) androidx.compose.ui.graphics.Color.White else Pc.Dim, r * 0.16f, c)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        PixText("Random ball: " + labels[pick - 1], 7, Pc.Text)
    }
}
