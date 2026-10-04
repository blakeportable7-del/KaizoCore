package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.tracker.EnemyInfo
import com.ironmonone.tracker.Gen3Types
import com.ironmonone.tracker.MoveRow
import com.ironmonone.tracker.TrackedMon
import com.ironmonone.tracker.TrackerState

/**
 * The IronMON tracker, recreated to match the PC tracker's own interface rather
 * than restyled into this app's Gen 3 chrome: black ground, hairline boxes, a
 * two-column head with the sprite and type chips on the left and the stat block
 * ruled off on the right, then the Moves bar with PP / Pow / Acc.
 *
 * The enemy's stat column is the reference's own STAT_STAGE buttons: an 8x8
 * marking box per stat, cycling blank / + / -- / =, at the reference's
 * offsets. It used to be six tinted note cells instead - the same information,
 * but not the same thing to look at.
 *
 * One substitution, forced rather than chosen: Constants.Font names
 * "Franklin Gothic Medium", which Android does not have, so the panel uses
 * sans-serif-condensed. Everything else here is taken from Constants.lua and
 * TrackerScreen.lua rather than designed.
 */

/**
 * The PC tracker's own default theme, taken from its Theme.lua rather than
 * eyeballed. That file documents the default as a theme code string:
 *
 *   FFFFFF FFFFFF 00FF00 FF0000 FFFF00 FFFFFF AAAAAA 222222 AAAAAA 222222 000000
 *   [text] [l.box text] [positive] [negative] [intermediate] [header]
 *   [u.border] [u.fill] [l.border] [l.fill] [main background]
 *
 * So the boxes are dark grey on a black page, not black on black, and the gold
 * on held item and ability is "Intermediate text" = pure yellow.
 */
/**
 * The GBA tracker panel. Layout and colours live in PcTracker.kt so the DS panel
 * renders identically instead of drifting into a second design.
 */

@Composable
private fun PartyCard(
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    onTypeDefenses: ((String, Int, Int) -> Unit)? = null,
    p: TrackedMon,
    spriteFor: (Int) -> androidx.compose.ui.graphics.ImageBitmap?,
    healPercent: Int = -1,
    healCount: Int = 0,
    /** The HP the heals add up to, for "Show heals as whole number" (TrackerState.healHp). */
    healHp: Int = 0,
    onHealsTap: (() -> Unit)? = null,
    onMoveInfo: ((PcMove) -> Unit)? = null,
    onAbilityInfo: ((String) -> Unit)? = null,
    onNameInfo: (() -> Unit)? = null,
    moveCtx: MoveContext? = null,
    /** Which attempt this is, for the PC heal counter. */
    attempt: Int = 0,
    /** A run's game: only then is the PC heal counter the run's to show (rc33 audit P1 #57). */
    runScoped: Boolean = true,
    /**
     * "Hide stats until summary shown", before this attempt has opened a
     * summary: icon, name and level only, as the reference's default Pokemon.
     */
    hidden: Boolean = false,
    /** Battle.inActiveBattle, for the Acc/Eva row (TrackerScreen.lua:1437). */
    inBattle: Boolean = false,
    /** How this game numbers species, for the Walking Pals icon (WalkingPals.trackerDex). */
    iconDex: WalkingPals.Dex = WalkingPals.Dex.GEN3,
    /** The game (TrackerState.routeVersion), for the form a retail game draws Deoxys in on the Walking Pals icon. */
    routeVersion: String = "",
    /** Past the run's BST line, still as it joined (BstRule): the X. */
    bstBroken: Boolean = false,
    onBstTap: (() -> Unit)? = null,
    /** This run's banned moves (MoveRule): the X on a move and the line on its card. */
    markMove: (PcMove) -> PcMove = { it },
    /** The game's generation: "Display gender" is the Game Boy Advance trackers' (CardGender). */
    generation: Int = 3,
) {
    val m = p.mon
    val dash = "---"
    // DataHelper.lua:140-151: hidden, the reference draws a stand-in whose stages are all neutral.
    val stages = if (hidden) emptyMap<String, Int>() else p.statStages
    PcMonCard(head = {
        PcHeadBlock(
            // "Show nicknames": the nickname in place of the species when it has one
            // that differs from the species name (DataHelper.lua:159).
            name = (if (TrackerOptions.showNicknames && m.nickname.isNotBlank() && !m.nickname.equals(p.speciesName, ignoreCase = true)) m.nickname
                else p.speciesName) + (if (m.shiny) " *" else ""),
            status = if (m.curHp <= 0) "FNT" else p.statusCondition,
            level = m.level, curHp = m.curHp, maxHp = m.maxHp,
            // DataHelper.lua:145-148: hidden, the stand-in keeps the species, so its types, BST,
            // evolution and moves header still show (:168, :169, :205, :253).
            typeChips = listOfNotNull(
                p.base?.type1?.let { Gen3Types.name(it) to pcTypeColor(it) },
                p.base?.type2?.takeIf { it != p.base?.type1 }
                    ?.let { Gen3Types.name(it) to pcTypeColor(it) },
            ),
            onTypesTap = p.base?.let { b -> onTypeDefenses?.let { cb -> { cb(p.speciesName, b.type1, b.type2) } } },
            itemLine = if (hidden) "" else p.itemName.takeIf { it != "-" } ?: "",
            abilityLine = if (hidden) dash else p.abilityName,
            hpText = dash.takeIf { hidden },
            onAbilityTap = onAbilityInfo?.let { cb -> abilityTapName(p.abilityName)?.let { name -> { cb(name) } } },
            onNameTap = onNameInfo,
            // The game's own picture where it is not the species' plain one (shiny, Unown's letter, Deoxys's form).
            sprite = romPicture(p.picture) ?: spriteFor(m.species),
            iconSpecies = m.species,
            iconDex = iconDex,
            // A shiny walks as its shiny, Unown as its letter (PalForms).
            iconLook = PalForms.ofMon(m, generation, iconDex, routeVersion),
            evo = p.evo,
            // Hidden, the reference's stand-in has no gender of the Pokemon's own (Program.lua:1725).
            gender = if (TrackerOptions.displayGender && !hidden) CardGender.of(generation, p.base?.genderRatio ?: 255, m.pid) else null,
            expFraction = if (TrackerOptions.showExpBar && p.expTotal > 0) HiddenCard.expFraction(hidden, p.expNow, p.expTotal) else null,
            // Only the lead carries the Heals strip: the number is a share of
            // the lead's max HP, so repeating it under every party member would
            // print the same percentage against six different Pokemon.
            belowHead = if (healPercent >= 0) {
                { val (pct, count, whole) = HiddenCard.heals(hidden, healPercent, healCount, healHp)
                  PcHealsBlock(pct, count, wholeHp = whole,
                    pcHealsAttempt = attempt.takeIf { runScoped && TrackerOptions.trackPcHeals }, onTap = onHealsTap) }
            } else null,
        ) {
            PcStatRow("HP", if (hidden) dash else "${m.maxHp}", stages["HP"], nature = m.nature.takeIf { !hidden }, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            PcStatRow("ATK", if (hidden) dash else "${m.atk}", stages["ATK"], nature = m.nature.takeIf { !hidden }, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            PcStatRow("DEF", if (hidden) dash else "${m.def}", stages["DEF"], nature = m.nature.takeIf { !hidden }, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            if (p.base?.singleSpecial == true) {
                // Gen 1: one Special stat. The Gen 1 reference tracker lists it as SPA, once.
                PcStatRow("SPA", if (hidden) dash else "${m.spAtk}", stages["SPA"], nature = m.nature.takeIf { !hidden }, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            } else {
                PcStatRow("SPA", if (hidden) dash else "${m.spAtk}", stages["SPA"], nature = m.nature.takeIf { !hidden }, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
                PcStatRow("SPD", if (hidden) dash else "${m.spDef}", stages["SPD"], nature = m.nature.takeIf { !hidden }, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            }
            PcStatRow("SPE", if (hidden) dash else "${m.spe}", stages["SPE"], nature = m.nature.takeIf { !hidden }, rightJustify = TrackerOptions.rightJustifiedNumbers, colorNumber = TrackerOptions.colorStatNumbers)
            // TrackerScreen.lua:1435-1448: in battle, a moved accuracy or evasion takes BST's place.
            val acc = stages["ACC"] ?: 6; val eva = stages["EVA"] ?: 6
            if (StageChevrons.accEvaReplacesBst(inBattle, acc, eva)) PcAccEvaRow(acc, eva)
            else PcStatRow("BST", p.base?.bst?.toString() ?: "?", rightJustify = TrackerOptions.rightJustifiedNumbers, broken = bstBroken, onBrokenTap = onBstTap)
        }
    }) {
        // "Moves 3/11 (17)" - learned so far / total this species learns, and
        // the level the next one arrives at, exactly as the PC tracker shows it.
        PcMovesSection(
            if (hidden) emptyList() else p.moveRows.map { markMove(it.toPcMove(moveCtx)) },
            referenceColumns = true, rightJustify = TrackerOptions.rightJustifiedNumbers,
            header = if (p.movesTotal > 0) "Moves ${p.movesLearned}/${p.movesTotal}" else "Moves",
            nextLevel = p.nextMoveLevel.takeIf { p.movesTotal > 0 },
            nextHot = p.nextMoveLevel?.let { m.level + 1 >= it } == true,
            onMoveTap = onMoveInfo,
            onHeaderTap = onMoveHistory?.let { cb -> { cb(m.species, p.speciesName, m.level) } },
        )
    }
}

@Composable
private fun EnemyCard(
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    onTypeDefenses: ((String, Int, Int) -> Unit)? = null,
    e: EnemyInfo,
    revealedAbility: String?,
    revealedAbility2: String?,
    spriteFor: (Int) -> androidx.compose.ui.graphics.ImageBitmap?,
    marks: IntArray,
    onCycleMark: (Int) -> Unit,
    movesSeenRunWide: List<StatMarks.SeenMove> = emptyList(),
    moveRowFor: (Int) -> MoveRow? = { null },
    lastSeenLevel: Int? = null,
    isWild: Boolean = false,
    encounters: Int = 0,
    routeName: String? = null,
    /** FireRed and LeafGreen (2026-09-29): the map mark for this place, drawn after its name in a wild battle (FrlgMapMark); null for none. */
    mapMark: (@Composable () -> Unit)? = null,
    /** TrackerScreen.Buttons.RouteDetails: a wild battle's encounter box opens the route info. */
    onRouteDetails: (() -> Unit)? = null,
    /** TrackerScreen.Buttons.PokemonIcon: the viewed Pokemon, the enemy included, opens its info. */
    onPokemonInfo: (() -> Unit)? = null,
    team: List<Boolean> = emptyList(),
    /** The Game Boy references write "Team:" before the balls (TrackerScreen.lua:827); Gen 3 dropped the word. */
    teamLabel: String? = null,
    onMoveInfo: ((PcMove) -> Unit)? = null,
    /** TrackerScreen.lua AbilityUpper (1) and AbilityLower (2): the ability that line shows, or the notepad. */
    onAbilityLine: ((Int) -> Unit)? = null,
    /** The species' learnset levels from the ROM, for the move count. */
    moveLevels: List<Int> = emptyList(),
    moveCtx: MoveContext? = null,
    /** Which parts were randomized, for what may be shown (InfoRules). */
    rand: com.ironmonone.tracker.RandomizedFlags? = null,
    catchText: String? = null,
    onCatchTap: (() -> Unit)? = null,
    /** "Hide stats until summary shown" (SummaryChecks.hides): the reference's stand-in (EnemyView). */
    hidden: Boolean = false,
    /** How this game numbers species, for the Walking Pals icon (WalkingPals.trackerDex). */
    iconDex: WalkingPals.Dex = WalkingPals.Dex.GEN3,
    /** The opposing party (TrackerState.enemyParty) and the game, for the Walking Pals icon's shiny and form (PalForms). */
    enemyParty: List<com.ironmonone.tracker.EnemyPartyMon> = emptyList(),
    routeVersion: String = "",
    /** The run's BST lines (BstRule): a wild one past them gets the X. */
    bstLines: BstRule.Lines? = null,
    onBstTap: (() -> Unit)? = null,
    /** The game's generation: "Display gender" is the Game Boy Advance trackers' (CardGender). */
    generation: Int = 3,
) {
    PcMonCard(head = {
        PcHeadBlock(
            name = e.speciesName,
            status = EnemyView.status(e, hidden),
            level = e.level, curHp = e.curHp, maxHp = e.maxHp,
            encounterLine =
                if (lastSeenLevel != null) "Last seen Lv.$lastSeenLevel"
                else "New encounter",
            onTypesTap = onTypeDefenses?.let { cb -> { cb(e.speciesName, e.type1, e.type2) } },
            // TrackerScreen.lua:1135: "Reveal info if randomized" off hides randomized types as "?".
            typeChips = InfoRules.typeIcons(GhostCard.types(e), InfoRules.hidesRandomizedTypes(rand)).map { (name, id) -> name to pcTypeColor(id) },
            // DataHelper.lua:234 puts the two possible abilities on the two
            // lines, the first suffixed " /" - it does not join them with a
            // slash onto one line, which is what made this overflow.
            //
            // Only where abilities are not randomized, or in Open Book. Otherwise the
            // reference shows the tracked ones: nothing yet reads "---" twice, one
            // revealed reads "Name /" over "?" (DataHelper.lua:242).
            itemLine = when {
                InfoRules.canShowAbilities(rand) -> e.abilityGuess.substringBefore(" / ").let { if (" / " in e.abilityGuess) "$it /" else it }
                revealedAbility != null -> "$revealedAbility /"
                else -> "---"
            },
            abilityLine = when {
                InfoRules.canShowAbilities(rand) -> e.abilityGuess.substringAfter(" / ", "---")
                revealedAbility2 != null -> revealedAbility2
                revealedAbility != null -> "?"
                else -> "---"
            },
            onItemTap = onAbilityLine?.let { cb -> { cb(1) } },
            onAbilityTap = onAbilityLine?.let { cb -> { cb(2) } },
            // A ghost's stand-in id is not a species, so its name opens no info screen.
            onNameTap = if (e.isGhost) null else onPokemonInfo,
            // The ghost stand-in's id is not a species: draw the pack's ghost (GhostCard.SPRITE).
            sprite = if (e.isGhost) PcAssets.gbaSprite(androidx.compose.ui.platform.LocalContext.current, GhostCard.SPRITE)
                else romPicture(e.picture) ?: spriteFor(e.species),
            // The ghost stand-in has no Walking Pals sheet either: its still sprite, never a species found by its id.
            iconSpecies = if (e.isGhost) 0 else e.species,
            iconDex = iconDex,
            iconLook = PalForms.ofEnemy(e, enemyParty, generation, iconDex, routeVersion),
            evo = e.evo,
            gender = if (TrackerOptions.displayGender) CardGender.of(generation, e.base?.genderRatio ?: 255, e.pid) else null,
            // The box under the card: how often this has been seen, and for a
            // trainer the row of pokeballs showing how many they have left.
            belowHead = {
                // The map mark (FireRed and LeafGreen, a place with pictures) follows the place's name, at the
                // height of the text, so the card keeps the PC tracker's shape (FrlgMapMark compact, 2026-09-29).
                val wildMark = mapMark.takeIf { isWild }
                Column(
                    Modifier.fillMaxWidth()
                        .then(if (isWild && onRouteDetails != null) Modifier.clickable { onRouteDetails() } else Modifier)
                        .padding(horizontal = 2.rp, vertical = 1.rp)
                ) {
                    PixText(
                        (if (isWild) "Seen (Wild): " else "Seen (Trainer): ") + encounters,
                        PcRef.FONT, Pc.Text,
                    )
                    if (isWild && wildMark != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        // A long name ("Seafoam Islands B4F") wraps rather than run under the mark.
                        PixText(routeName ?: "", PcRef.FONT, Pc.Text, Modifier.weight(1f, fill = false), wrap = true)
                        wildMark()
                    }
                    else if (isWild) PixText(routeName ?: "", PcRef.FONT, Pc.Text)
                    // The label at x + 11 and the balls at x + 40 there, 29 apart.
                    else if (teamLabel != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        PixText(teamLabel, PcRef.FONT, Pc.Text, Modifier.width(29.rp)); PcTrainerTeam(team)
                    }
                    else PcTrainerTeam(team)
                }
            },
        ) {
            // DataHelper.lua:186: an unrandomized opponent (or Open Book) shows its
            // base stats, in Intermediate text; otherwise the marking boxes.
            val b = e.base
            if (b != null && InfoRules.canShowStats(rand)) {
                val rj = TrackerOptions.rightJustifiedNumbers
                PcStatRow("HP", "${b.hp}", rightJustify = rj, valueColor = Pc.Gold)
                PcStatRow("ATK", "${b.atk}", rightJustify = rj, valueColor = Pc.Gold)
                PcStatRow("DEF", "${b.def}", rightJustify = rj, valueColor = Pc.Gold)
                PcStatRow("SPA", "${b.spAtk}", rightJustify = rj, valueColor = Pc.Gold)
                if (!b.singleSpecial) PcStatRow("SPD", "${b.spDef}", rightJustify = rj, valueColor = Pc.Gold)
                PcStatRow("SPE", "${b.spe}", rightJustify = rj, valueColor = Pc.Gold)
            } else PcMarkColumn(marks, onCycleMark, singleSpecial = e.base?.singleSpecial == true)
            // TrackerScreen.lua:1435-1448: a moved accuracy or evasion takes BST's place; hidden, the stand-in's are neutral.
            val acc = if (hidden) 6 else e.statStages["ACC"] ?: 6
            val eva = if (hidden) 6 else e.statStages["EVA"] ?: 6
            if (StageChevrons.accEvaReplacesBst(true, acc, eva)) PcAccEvaRow(acc, eva)
            else PcStatRow("BST", GhostCard.bst(e), rightJustify = TrackerOptions.rightJustifiedNumbers,
                broken = isWild && !e.isGhost && BstRule.wildBreaks(e.base?.bst, bstLines), onBrokenTap = onBstTap)
            // Live stage chevrons for the enemy, when any stat has moved.
            EnemyView.stageRows(e, hidden).forEach { (n, st) -> PcStatRow(n, "", st) }
        }
    }) {
        // The enemy gets the SAME moves table as the player, which is what
        // the reference does - it fills the ordinary moves area from tracked
        // moves rather than printing a sentence. The asterisk is the
        // reference's own marker for "more tracked than will fit"
        // (TrackerScreen.lua:1487).
        // Tracker.getMoves: the WHOLE run's sightings for this species, most recent
        // first. A move used in an earlier battle is drawn from the ROM's table at
        // its base PP; this battle's rows win where they overlap. Before 2026-09-06
        // this only ever drew the current battle, so every encounter started blind.
        val learned = com.ironmonone.tracker.LearnedMoves.of(moveLevels, e.level)
        // Utils.calculateMoveStars: a tracked move it may have forgotten since.
        // DataHelper.lua:258: an unrandomized learnset (or Open Book) shows its
        // actual moves at their live PP; otherwise the tracked ones, with stars.
        val actual = InfoRules.canShowMoves(rand)
        // All four seen this battle: those four, used at this level, so none is starred (Tracker.getMoves).
        val starred = if (actual || EnemyView.fourKnown(e)) emptySet() else com.ironmonone.tracker.MoveStars.of(movesSeenRunWide.map { it.id to it.lastLv }, e.level, moveLevels)
        val (shownRows, seenCount) = EnemyView.moveRows(e, movesSeenRunWide, moveRowFor, actual, hidden)
        PcMovesSection(
            rows = shownRows.map { r -> r.toPcMove(moveCtx).let { if (r.id in starred) it.copy(name = it.name + "*") else it } },
            referenceColumns = true, rightJustify = TrackerOptions.rightJustifiedNumbers,
            catchText = catchText,
            onCatchTap = onCatchTap,
            nextLevel = learned.next.takeIf { learned.total > 0 },
            // Utils.getMovesLearnedHeader counts for the opponent too, at ITS
            // level: "Moves* 1/5 (9)", the asterisk (no space) once more than
            // four of its moves have been seen. This read "Moves *" with no count.
            header = "Moves" + (if (!actual && seenCount > 4) "*" else "") +
                (if (learned.total > 0) " ${learned.learned}/${learned.total}" else ""),
            onHeaderTap = onMoveHistory?.let { cb -> { cb(e.species, e.speciesName, e.level) } },
            onMoveTap = onMoveInfo,
        )
    }
}

/**
 * The opponent's card while "Hide stats until summary shown" hides it (DataHelper.lua:140-151):
 * the reference draws a blank stand-in with only the opponent's id and level, so the card keeps
 * its icon, name, level, types, abilities, marks and tracked moves, but has no status, no stat
 * stages and no moves of its own. Otherwise these are the card's usual parts.
 */
internal object EnemyView {
    fun status(e: EnemyInfo, hidden: Boolean): String = when {
        hidden -> ""
        e.curHp <= 0 -> "FNT"
        else -> e.statusCondition
    }

    /** The stats that have moved from neutral, drawn as rows under BST. */
    fun stageRows(e: EnemyInfo, hidden: Boolean): Map<String, Int> =
        if (hidden) emptyMap() else e.statStages.filterKeys { it != "ACC" && it != "EVA" }.filterValues { it != 6 }

    /**
     * The move rows, and how many moves it has been seen to use. [actual] (canShowMoves): its own
     * four at their live PP, blank while [hidden]. Otherwise the run's tracked moves
     * (Tracker.getMoves), this battle's rows winning where they overlap, except while [hidden]:
     * DataHelper.lua:325-331 takes a tracked move's PP from the stand-in, which has none, so it
     * stays at its base PP. Once all four of its moves have been used this battle, those four are its rows
     * (Tracker.BattleNotes.FourMovesIfAllKnown, Tracker.lua:389-393 and 604-606): the run's older sightings came first
     * and could show a move this one does not have in place of one it used (rc33 audit P1 #56).
     */
    fun moveRows(e: EnemyInfo, seenRunWide: List<StatMarks.SeenMove>, moveRowFor: (Int) -> MoveRow?, actual: Boolean, hidden: Boolean): Pair<List<MoveRow>, Int> {
        val thisBattle = e.moveRows.distinctBy { it.id }.map { r -> if (hidden) moveRowFor(r.id) ?: r else r }
        val seen = (seenRunWide.mapNotNull { sm -> thisBattle.firstOrNull { it.id == sm.id } ?: moveRowFor(sm.id) } + thisBattle).distinctBy { it.id }
        val rows = when {
            actual && hidden -> emptyList()
            actual -> e.moves.mapIndexedNotNull { i, id -> if (id == 0) null else moveRowFor(id)?.copy(pp = e.movePps.getOrElse(i) { 0 }) }
            fourKnown(e) -> thisBattle.take(4)
            else -> seen.take(4)
        }
        return rows to seen.size
    }

    /** All four of its moves have been used this battle. */
    fun fourKnown(e: EnemyInfo): Boolean = e.moveRows.distinctBy { it.id }.size >= 4
}

/**
 * "Hide stats until summary shown" on your own card's Heals strip and experience bar (DataHelper.lua:140-151). The
 * reference zeroes the heals while the card is hidden (DataHelper.lua:374-377), so its strip reads "0% HP (0)", and its
 * stand-in has 0 of 100 experience (Program.lua:1721-1722), an empty bar. Both showed the real numbers here, which give
 * the hidden max HP away (rc32 audit P2 #98).
 */
internal object HiddenCard {
    /** The percent, count and whole HP the strip prints. */
    fun heals(hidden: Boolean, percent: Int, count: Int, wholeHp: Int): Triple<Int, Int, Int> =
        if (hidden) Triple(0, 0, 0) else Triple(percent, count, wholeHp)

    /** The experience bar's fill: empty while hidden. */
    fun expFraction(hidden: Boolean, now: Int, total: Int): Float =
        if (hidden || total <= 0) 0f else now.toFloat() / total
}

/**
 * "Display gender" on a card: Gen 3's rule, the personality's low byte against the species' ratio (Gender3,
 * TrackerScreen.lua:1334). Only on a Game Boy Advance game, where Tracker Setup offers the switch (TrackerGearDialog's
 * gen3 scope): Gold, Silver and Crystal's tracker builds its personality from the species and the trainer id, so every
 * one of your Pokemon of a species took the one gender the trainer id decided, and every opponent read female, with no
 * switch there to turn it off (rc32 audit P2 #97). No Game Boy reference draws a gender.
 */
internal object CardGender {
    fun of(generation: Int, ratio: Int, pid: Long): Int? = if (generation < 3) null else com.ironmonone.tracker.Gender3.of(ratio, pid)
}

/**
 * InfoScreen.showNextPokemon: the species [delta] ids on from [from], wrapping at [total],
 * over Gen 3's empty 252-276 slots (only a Gen 3 game has them; Gold/Silver/Crystal end at 251).
 */
internal fun stepSpeciesId(from: Int, delta: Int, total: Int): Int {
    var n = from + delta
    if (n < 1) n = total
    else if (n > total) n = 1
    if (total > 276 && n in 252..276) n = if (delta > 0) 277 else 251
    return n
}

@Composable
fun TrackerPanel(
    /** Trainer Info for the opponent, from the TRAINER BATTLE banner. */
    onTrainerInfo: (() -> Unit)? = null,
    /** StatMarkingScoreSheet, from the game-over card. */
    onGradeNotes: (() -> Unit)? = null,
    /** RandomEvosScreen for a species, from Pokemon info; null when there is no table for it. */
    onRandomEvos: ((Int) -> Unit)? = null,
    hasRandomEvos: (Int) -> Boolean = { false },
    /** Move History for a card: (species, name, level). */
    onMoveHistory: ((Int, String, Int) -> Unit)? = null,
    /** Type Defenses for a card: (name, type1, type2) in Gen 3 ids. */
    onTypeDefenses: ((String, Int, Int) -> Unit)? = null,
    state: TrackerState?,
    modifier: Modifier = Modifier,
    ballCall: String? = null,
    favoriteLine: FavoritesShown? = null,
    spriteFor: (Int) -> androidx.compose.ui.graphics.ImageBitmap? = { null },
    unsupportedNote: String? = null,
    /** Opens the tracker's gear (the reference's SettingsGear). */
    onGear: (() -> Unit)? = null,
    /** After SETUP in the first row: landscape's corner arrow (TrackerCornerMenu); null in portrait. */
    headerTrailing: (@Composable () -> Unit)? = null,
    enemyMarks: IntArray = IntArray(StatMarks.COUNT),
    enemyEncounters: Int = 0,
    /** Level this species was at the previous time it was met, if ever. */
    enemyLastSeenLevel: Int? = null,
    /**
     * Show your lead AND the enemy together instead of swapping between them.
     * True in landscape, where the column is tall enough for both.
     */
    stackBoth: Boolean = false,
    onCycleMark: (Int) -> Unit = {},
    enemyNote: String = "",
    onEditNote: () -> Unit = {},
    onRerollBall: (() -> Unit)? = null,
    routeName: String? = null,
    /** Every move this species has shown across the WHOLE run, not just now, most recent first. */
    movesSeenRunWide: List<StatMarks.SeenMove> = emptyList(),
    /** A row for a move id seen in an earlier battle, from the ROM's move table. */
    moveRowFor: (Int) -> MoveRow? = { null },
    /** The enemy's ability, if a battle trigger has revealed it this run. */
    revealedEnemyAbility: String? = null,
    /** The enemy species' second tracked ability (DataHelper.lua:247). */
    revealedEnemyAbility2: String? = null,
    routeSeen: Int = 0,
    routeTotal: Int = 0,
    routeTrainers: Int = 0,
    routeBosses: Int = 0,
    steps: Int = 0,
    onMoveDescription: ((Int) -> String?)? = null,
    onAbilityDescription: ((String) -> String?)? = null,
    onWeight: ((Int) -> String?)? = null,
    /** The info screen's evolution lines (PanelLookups.evolutionDetails). */
    onEvolution: ((Int) -> List<String>)? = null,
    onEffectiveness: ((Int) -> Map<Double, List<String>>)? = null,
    onMoveLevels: ((Int) -> List<Int>)? = null,
    onSpeciesNote: ((Int) -> String)? = null,
    /** The route info screen's data for a map id (the current one when null). */
    onRouteSource: ((Int?) -> RouteInfoSource?)? = null,
    /** RouteData.AvailableRoutes, for the route lookup. */
    onRouteLookup: (() -> List<Pair<Int, String>>)? = null,
    /** The carousel's route area: the battle's encounter area where RouteData has it, else Walking. */
    routeArea: String? = null,
    /** Base stats of any species, for the info screen a route icon opens. */
    onSpeciesBase: ((Int) -> com.ironmonone.tracker.BaseStats?)? = null,
    /** How many species ids this game has (411 in Gen 3; the Nat. Dex expansion goes further). */
    speciesTotal: Int = 411,
    /** Opens the note editor for any species (InfoScreen's NotepadTracking). */
    onEditNoteFor: ((Int) -> Unit)? = null,
    /** A Survival run's Pokemon Center limit (PcHeals.limitForLastRun), or null. */
    pcHealsLimit: PcHeals.Limit? = null,
    /** Calc Atk, opened from the last attack line (CalcAtkScreen). */
    onCalcAtk: (() -> Unit)? = null,
    /** Heals in Bag (TrackerScreen.Buttons.HealsInBag), Trainers on Route and Battle Details, from their taps. */
    onHealsInBag: (() -> Unit)? = null,
    onTrainersOnRoute: (() -> Unit)? = null,
    onBattleDetails: (() -> Unit)? = null,
    onSpeciesName: ((Int) -> String)? = null,
    attempt: Int = 0,
    coverage: Map<Double, List<Int>> = emptyMap(),
    /** Tapping a wild battle's catch rate opens Catch Rates, as the reference's header button does. */
    onCatchRates: (() -> Unit)? = null,
    /**
     * The game's generation (1 Red/Blue/Yellow, 2 Gold/Silver/Crystal, 3 GBA):
     * which reference's move table and type chart the move rows follow.
     */
    generation: Int = 3,
    /** The Nat. Dex expansion, whose species run past Gen 3's 411: its chart has Fairy, and Steel no longer resists Ghost or Dark. */
    natDex: Boolean = speciesTotal > 411,
    /**
     * The game in Play is a run (PlayScreen: session.isRun). Only then are the PC heal count and the summary check the
     * run's: a library game or a Nuzlocke passed the run's attempt and read and wrote its counts (rc33 audit P1 #57).
     * Outside a run the summary check is kept for the session in memory.
     */
    runScoped: Boolean = true,
    /** Kaizo's BST line (BstRule), from the run in Play; a test hands its own. */
    bstLines: BstRule.Lines? = bstLinesInPlay(attempt),
    /** What each of your Pokemon joined the run as, beside the run's marks. */
    joinedForms: JoinedForms? = joinedFormsInPlay(attempt, bstLines),
    /** The run's banned moves (MoveRule), from the run in Play; a test hands its own. */
    moveRules: MoveRule.Rules? = moveRulesInPlay(attempt),
    /** MaxDex 1.0 in Play (MaxDexInfo): its own numbering for the Walking Pals icons, and Freeze-Dry is super effective on Water. */
    maxDex: Boolean = maxDexInPlay(attempt),
) {
    // The tracker's own game-over card is a Kaizo IronMON run's only (PlayRules, 2026-09-30).
    val ironmonOver = ironmonGameOverCard(state?.gameOver != null)
    // What the info screen is currently explaining, if anything.
    var info by remember {
        mutableStateOf<Triple<String, String?, String?>?>(null)
    }
    // The Pokemon info screen is its own mode, with its own shape.
    var monInfo by remember { mutableStateOf<TrackedMon?>(null) }
    // The move info screen (InfoScreen.lua:861), plus Blake's matchup line.
    var moveInfo by remember { mutableStateOf<MoveDetail?>(null) }
    var routeInfoOpen by remember { mutableStateOf(false) }
    // InfoScreen ROUTE_INFO: species info for an icon tapped on it.
    var speciesInfo by remember { mutableStateOf<Int?>(null) }
    if (routeInfoOpen) {
        val src = onRouteSource?.invoke(null)
        if (src == null) routeInfoOpen = false
        else PcRouteInfoScreen(
            start = src,
            startArea = state?.encounterArea,
            gameDataRandomized = state?.randomized?.gameData ?: true,
            spriteFor = spriteFor,
            lookupRoutes = { onRouteLookup?.invoke() ?: emptyList() },
            sourceFor = { id -> onRouteSource?.invoke(id) },
            onPokemon = if (onSpeciesBase != null) { sp -> speciesInfo = sp } else null,
            // FireRed and LeafGreen: the map mark after whichever place the screen shows (FrlgPictures, 2026-09-29).
            picturesFor = FrlgPictures.lookupFor(state?.badgeSet),
        ) { routeInfoOpen = false }
    }
    fun stepSpecies(from: Int, delta: Int): Int = stepSpeciesId(from, delta, speciesTotal)
    // DataHelper.lua:432: the info screen's types, or "?" where InfoRules.infoScreenHidesTypes says so.
    fun infoTypes(base: com.ironmonone.tracker.BaseStats?, species: Int) = InfoRules.typeIcons(
        listOfNotNull(
            base?.type1?.let { Gen3Types.name(it) to it },
            base?.type2?.takeIf { it != base.type1 }?.let { Gen3Types.name(it) to it },
        ),
        // DataHelper.lua:423: your lead is Battle.getViewedPokemon(true), never an Egg (RC35-NOTICED N #34), and in a
        // double battle the one of yours the view shows (GbaViewState.own).
        InfoRules.infoScreenHidesTypes(state?.randomized, ownLead = species == gbaView.own(state)?.mon?.species),
    ).map { (name, id) -> name to pcTypeColor(id) }
    // Built each time a lookup opens. A remembered list was computed while the tracker was
    // still attaching, so every name read "#id", and PlayScreen's name lambda never changes
    // identity, so it was never rebuilt: the lookup showed no names at all.
    val lookupNames: () -> List<Pair<Int, String>> = {
        (1..speciesTotal).filter { it !in 252..276 }
            .map { it to (onSpeciesName?.invoke(it) ?: "#$it") }
            .filter { !it.second.startsWith("#") && it.second.isNotBlank() && it.second != "?" }
            .sortedBy { it.second.lowercase() }
    }
    speciesInfo?.let { sp ->
        val base = onSpeciesBase?.invoke(sp)
        PcPokemonInfo(
            onPrevious = { speciesInfo = stepSpecies(sp, -1) },
            onNext = { speciesInfo = stepSpecies(sp, 1) },
            lookup = lookupNames, onLookup = { speciesInfo = it },
            onHistory = onMoveHistory?.let { cb -> { cb(sp, onSpeciesName?.invoke(sp) ?: "#$sp", 0) } },
            onResistances = if (base != null && onTypeDefenses != null) { { onTypeDefenses(onSpeciesName?.invoke(sp) ?: "#$sp", base.type1, base.type2) } } else null,
            onEditNote = onEditNoteFor?.let { cb -> { cb(sp) } },
            name = onSpeciesName?.invoke(sp) ?: "#$sp",
            types = infoTypes(base, sp),
            bst = base?.bst?.toString() ?: "?",
            weight = onWeight?.invoke(sp),
            evolution = onEvolution?.invoke(sp) ?: emptyList(),
            effectiveness = onEffectiveness?.invoke(sp) ?: emptyMap(),
            moveLevels = onMoveLevels?.invoke(sp) ?: emptyList(),
            // The level of the Pokemon in battle, or your lead, when it is this species (DataHelper.lua:452-465).
            level = InfoScreenLines.viewedLevel(sp, state),
            note = onSpeciesNote?.invoke(sp) ?: "",
        ) { speciesInfo = null }
    }
    monInfo?.let { p ->
        PcPokemonInfo(
            onPrevious = { monInfo = null; speciesInfo = stepSpecies(p.mon.species, -1) },
            onNext = { monInfo = null; speciesInfo = stepSpecies(p.mon.species, 1) },
            lookup = lookupNames, onLookup = { monInfo = null; speciesInfo = it },
            onHistory = onMoveHistory?.let { cb -> { cb(p.mon.species, p.speciesName, p.mon.level) } },
            onResistances = p.base?.let { b -> onTypeDefenses?.let { cb -> { cb(p.speciesName, b.type1, b.type2) } } },
            onEditNote = onEditNoteFor?.let { cb -> { cb(p.mon.species) } },
            name = p.speciesName,
            types = infoTypes(p.base, p.mon.species),
            bst = p.base?.bst?.toString() ?: "?",
            weight = onWeight?.invoke(p.mon.species),
            evolution = onEvolution?.invoke(p.mon.species) ?: emptyList(),
            effectiveness = onEffectiveness?.invoke(p.mon.species) ?: emptyMap(),
            moveLevels = onMoveLevels?.invoke(p.mon.species) ?: emptyList(),
            level = p.mon.level,
            note = onSpeciesNote?.invoke(p.mon.species) ?: "",
            // Coverage lives here now, not beside the card: the reference
            // keeps it on its own CoverageCalcScreen.
            coverage = coverage,
            onRandomEvos = if (onRandomEvos != null && hasRandomEvos(p.mon.species)) { { onRandomEvos(p.mon.species) } } else null,
        ) { monInfo = null }
    }
    moveInfo?.let { PcMoveInfoDialog(it) { moveInfo = null } }
    // Program.checkForStarterSelection: while a starter's ball is being
    // confirmed, its info screen; back to the tracker once the choice closes.
    val starter = state?.starterOffered?.takeIf { TrackerOptions.showStarterBallInfo }
    var starterClosed by remember { mutableStateOf<Int?>(null) }
    if (starter == null) starterClosed = null
    // Not on the second screen: its window cannot host a dialog (LocalOnSecondScreen, rc33 audit P0-7).
    if (starter != null && starter != starterClosed && !LocalOnSecondScreen.current) {
        val base = state?.starterBase
        PcPokemonInfo(
            name = onSpeciesName?.invoke(starter) ?: "#$starter",
            types = infoTypes(base, starter),
            bst = base?.bst?.toString() ?: "?",
            weight = onWeight?.invoke(starter),
            evolution = onEvolution?.invoke(starter) ?: emptyList(),
            effectiveness = onEffectiveness?.invoke(starter) ?: emptyMap(),
            moveLevels = onMoveLevels?.invoke(starter) ?: emptyList(),
            level = 5,
            note = onSpeciesNote?.invoke(starter) ?: "",
        ) { starterClosed = starter }
    }
    info?.let { (title, sub, body) ->
        PcInfoDialog(title, sub, body) { info = null }
    }

    // Everything below is measured in the reference's own 150-pixel-wide
    // coordinate system (see PcCanvas), so the panel is a scaled copy of the
    // PC tracker rather than a layout that reflows to whatever width the pane
    // happens to be. Reflowing is what wrapped "Golisopod-M" onto three lines.
    PcCanvas(modifier.fillMaxWidth()) {
      // The Main background colour, and the player's image over it (TrackerBackdrop.kt).
      Column(Modifier.fillMaxWidth().then(trackerBackdrop()).padding(trackerMargin())) {
          // The reference's gear sits at the top of the tracker screen; SETUP is its NavigationMenu.ButtonSetup.
          // In a battle it ends the battle banner; otherwise it shares a slim bar with the route the player is on.
          // It had a row to itself, empty but for it (2026-10-02, Blake: "Lots of wasted space").
          // The same tests, in the same order, as the when below that draws the banner.
          // The attempt, in a Kaizo IronMON run (2026-10-02: "the tracker needs attempt count").
          val attemptShown = attempt.takeIf { !LocalAttemptInTitle.current && ironmonRunInPlay(it) }
          // What each of your Pokemon joined the run as (BstRule), read from the whole party so one that evolves
          // out of sight is known by what it was (2026-10-02).
          // A tap on a red BST: its value, the line, and whose it is (null key: a wild one).
          var bstSheet by remember { mutableStateOf<Triple<Int, Int, Long?>?>(null) }
          var joinedVersion by remember { mutableIntStateOf(0) }
          bstSheet?.let { (bst, line, key) ->
              BstRuleSheet(bst, line, own = key != null, onEvolved = { key?.let { joinedForms?.markEvolved(it) }; joinedVersion++; bstSheet = null },
                  legendary = bstLines?.legendary) { bstSheet = null }
          }
          if (joinedForms != null && state != null && !state.unreadable) {
              val party = state.party.map { BstRule.keyOf(it.mon, generation) to it.mon.species }
              androidx.compose.runtime.LaunchedEffect(joinedForms, party) { party.forEach { (pid, sp) -> joinedForms.joinedAs(pid, sp) } }
          }
          val bannerShows = state != null && unsupportedNote == null && !state.unreadable && state.partyCount != 0 &&
              !(ironmonOver && state.gameOver != null) && state.inBattle
          onGear?.takeIf { !bannerShows }?.let { g ->
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                  attemptShown?.let {
                      PixText("ATTEMPT $it", PcRef.FONT - 1, Pc.Text, weight = androidx.compose.ui.text.font.FontWeight.Medium)
                      Spacer(Modifier.width(6.rp))
                  }
                  AreaName(routeName.orEmpty(), Modifier.weight(1f))
                  // Program.ActiveRepel:shouldDisplay - only while one is running, never in
                  // battle, off the map or in the Hall of Fame, and the reference puts it in the top right.
                  if (TrackerOptions.showRepel && state != null && state.repelVisible) {
                      PcRepelBar(state.repelSteps, state.repelDuration)
                      Spacer(Modifier.width(6.dp))
                  }
                  TrackerGearButton { g() }
                  headerTrailing?.invoke()
              }
              Spacer(Modifier.height(2.rp))
          }
          // TeamViewArea: the reference draws it under the game; here it heads the panel.
          if (TrackerOptions.showTeamView && state != null && !state.unreadable && state.party.isNotEmpty()) {
              PcTeamView(
                  party = state.party, spriteFor = spriteFor,
                  onMon = { monInfo = it },
                  onTypes = onTypeDefenses?.let { cb -> { p -> p.base?.let { b -> cb(p.speciesName, b.type1, b.type2) } } },
                  onAbility = { p -> info = Triple(p.abilityName, "Ability", onAbilityDescription?.invoke(p.abilityName)) },
                  // PokemonData.Values.EggId, 412; past Gen 3's 411 species (Nat. Dex) the bundled pack's egg is 1284.
                  eggSpecies = if (speciesTotal > 411) 1284 else 412,
              )
              Spacer(Modifier.height(3.dp))
          }
          NuzlockePanel(state) // the Nuzlocke run's area, cap and graveyard, when this game has one (2026-09-29)
        when {
            unsupportedNote != null -> PcCard {
                PixText(unsupportedNote, 8, Pc.Negative, Modifier.padding(6.dp))
            }

            state == null -> PcCard {
                PixText("Tracker: waiting for the game...", 8, Pc.Dim, Modifier.padding(6.dp))
            }

            // The addresses for this ROM are wrong. Say so, loudly, rather
            // than render a Pokemon that does not exist.
            state.unreadable -> PcCard {
                Column(Modifier.padding(6.dp)) {
                    PixText("TRACKER CANNOT READ THIS ROM", 9, Pc.Negative)
                    Spacer(Modifier.height(4.dp))
                    PixText("The party decoded into impossible values, so the", 8, Pc.Dim)
                    PixText("memory addresses for this build are wrong.", 8, Pc.Dim)
                    Spacer(Modifier.height(4.dp))
                    PixText("Nothing here would be trustworthy, so it is hidden.", 8, Pc.Dim)
                    if (state.diagnostics.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        // Name the build and the addresses, so a screenshot of
                        // this card is enough to identify which ROM and which
                        // resolution went wrong.
                        PixText(state.diagnostics, 7, Pc.Dim)
                    }
                }
            }

            state.partyCount == 0 -> PcCard {
                Column(Modifier.padding(6.dp)) {
                    // The favorites' icons, as the PC trackers' new-game screens draw them (FavoriteIcons), by the cards'
                    // own sprites. They wrap: a Nat. Dex run may name nine (FavoriteRules).
                    favoriteLine?.icons?.takeIf { it.isNotEmpty() }?.let {
                        FavoriteIconRow(it, { sp -> spriteFor(sp) }); Spacer(Modifier.height(3.dp))
                    }
                    // In the lab, a ball holding a favorite the mode lets you take (FavoriteBall), and never the others.
                    if (state.inLab) favoriteLine?.balls?.forEach {
                        PixText(it, 9, Pc.Positive, wrap = true); Spacer(Modifier.height(3.dp))
                    }
                    // Lab only, the way canShowBallPicker() gates it: in the
                    // lab, with no Pokemon. It used to show anywhere the party
                    // was empty.
                    ballCall?.takeIf { state.inLab && TrackerOptions.ballPickerShows() }?.let {
                        PcBallPicker(
                            when (it) { "LEFT" -> 0; "MIDDLE" -> 1; else -> 2 },
                            onReroll = onRerollBall)
                        Spacer(Modifier.height(3.dp))
                    }
                    // This used to print the player's map position ("pos 0,0"),
                    // a debug readout, as the only line a new player saw here.
                    if (!(state.inLab && TrackerOptions.ballPickerShows() && ballCall != null)) {
                        PixText("No Pokemon yet. The tracker fills in when you get your first one.", 8, Pc.Dim, wrap = true)
                    }
                }
            }

            // A finished run is the headline: it goes above the team, not
            // under it, so you are not scrolling to find out you lost.
            state.gameOver != null && ironmonOver -> {
                PcGameOver(
                    won = state.gameOver == com.ironmonone.tracker.GameOver.WON,
                    attempt = attempt,
                    party = state.party,
                    onGrade = onGradeNotes,
                )
            }

            else -> {
                // ONE Pokemon, which is what the reference shows.
                //
                // Battle.getViewedPokemon (Battle.lua:257) returns a single
                // mon: your active one outside a battle, and inside one
                // whichever side is being viewed. The whole panel is built
                // around that - a 96x52 box, a 44x75 stat column, four move
                // rows - so stacking the entire party as six cards was never
                // the reference's screen, just this one's.
                //
                // In battle it opens on the ENEMY, matching the reference's
                // "Auto swap to enemy" default.
                // Battle.inActiveBattle: the animated icons do not walk in battle.
                var sessionSummary by remember { mutableStateOf(false) }
                androidx.compose.runtime.SideEffect {
                    SpriteMotion.inBattle = state.inBattle
                    if (runScoped) {
                        // "Track PC Heals" auto-tracking watches the game's heal statistics.
                        PcHeals.arm(attempt, pcHealsLimit, Integer.bitCount(state.badges))
                        PcHeals.observe(attempt, state.centerHealsStat)
                        PcHeals.observeBadges(attempt, Integer.bitCount(state.badges), pcHealsLimit)
                        // Program.lua:552: opening a summary in the game reveals the card for this attempt.
                        if (state.summaryOpen) SummaryChecks.mark(attempt)
                    } else if (state.summaryOpen) sessionSummary = true
                }
                val hideStats = if (runScoped) SummaryChecks.hides(attempt, state.gameDataRandomized, generation)
                    else SummaryChecks.hidesStats(TrackerOptions.hideStatsUntilSummary, state.gameDataRandomized, -1, generation) && !sessionSummary
                // Which Pokemon the battle shows (GbaViewState: Battle.isViewingOwn and isViewingLeft), one view for every
                // panel. A battle opens on the opponent with "Auto swap to enemy" on, and each Pokemon the opponent sends
                // out turns the view to it again, on its side (Battle.lua:309-316, 945-952), not only the first.
                val view = gbaView
                androidx.compose.runtime.LaunchedEffect(state.inBattle, state.enemyOnField) {
                    view.onRead(state, TrackerOptions.autoSwapToEnemy(gameBoy = generation < 3))
                }
                val viewingOwn = !state.inBattle || view.view.own
                if (state.inBattle) {
                    PcBattleBanner(
                        state.isWildBattle,
                        viewingOwn = view.offersFoe(state, stackBoth),
                        // A single battle has no swap when both are already on screen: it would toggle a view that
                        // is not hidden. A double battle's swap walks the Pokemon on the field (GbaViewState.next).
                        onSwapView = if (view.canSwap(state, stackBoth)) { { view.swap(state, stackBoth) } } else null,
                        onTrainerTap = onTrainerInfo,
                        onGear = onGear,
                        weather = state.weather,
                        trailing = headerTrailing,
                        attempt = attemptShown,
                        side = view.sideWords(state, stackBoth),
                        swapSpoken = view.swapSpoken(state, stackBoth),
                    )
                    Spacer(Modifier.height(2.rp))
                }
                val enemy = view.foe(state)?.takeIf { stackBoth || !viewingOwn }

                // LANDSCAPE shows both cards at once, yours on top: there is
                // vertical room for two, and marking the enemy's stats while
                // your own are still visible is the point. Portrait keeps the
                // reference's swap, which is what fits one column.
                //
                // The enemy card is emitted AFTER your lead. In swap mode
                // exactly one of the two is ever visible, so that order is
                // invisible there - it only sets the stacking order here.
                // Your Pokemon on the field in a battle, your lead otherwise (TrackerState.onField): slot 1 is
                // not it after a switch (rc33 audit P1). The tracker puts its stat stages on it. In a double battle
                // the one the view shows (GbaViewState.own), the right-hand one with its own stages and heals.
                val ownHeals = view.heals(state)
                listOfNotNull(view.own(state)).take(if (enemy != null && !stackBoth) 0 else 1).forEach { p ->
                    PartyCard(onMoveHistory = onMoveHistory, onTypeDefenses = onTypeDefenses, p, spriteFor,
                        healPercent = ownHeals.percent,
                        healCount = ownHeals.count,
                        healHp = ownHeals.hp,
                        onHealsTap = onHealsInBag,
                        onMoveInfo = { mv ->
                            moveInfo = detailOf(mv, onMoveDescription?.invoke(mv.id), noRomData = moveRowFor(mv.id)?.let { it.pp == 0 && (it.power ?: 0) == 0 } == true, gen1 = generation == 1, natDex = natDex)
                                // Your own Hidden Power: the info screen gets the type arrows.
                                .copy(hiddenPowerPid = p.mon.pid.takeIf { mv.id == com.ironmonone.tracker.MoveRules.HIDDEN_POWER })
                        },
                        onAbilityInfo = { name ->
                            info = Triple(name, "Ability", onAbilityDescription?.invoke(name))
                        },
                        onNameInfo = { monInfo = p },
                        // Against the opponent shown, or its partner once it has fainted (GbaViewState.ownTarget).
                        moveCtx = ownMoveContext(p, view.ownTarget(state), state.weather, onWeight)
                            .copy(hideEffectiveness = InfoRules.hideEffectiveness(state.randomized, state.isGhostBattle, own = true), generation = generation, natDex = natDex, maxDex = maxDex),
                        attempt = attempt,
                        hidden = hideStats,
                        iconDex = WalkingPals.trackerDex(generation, speciesTotal, maxDex),
                        inBattle = state.inBattle,
                        runScoped = runScoped,
                        bstBroken = joinedVersion >= 0 && BstRule.ownBreaks(p.base?.bst, bstLines, joinedForms, BstRule.keyOf(p.mon, generation), p.mon.species, p.speciesName),
                        // A legendary past MaxDex's legendary line keeps its X whatever it evolved from: no "it evolved" button.
                        onBstTap = { bstSheet = Triple(p.base?.bst ?: 0, bstLines?.own ?: 0, BstRule.keyOf(p.mon, generation).takeUnless { BstRule.legendaryBreaks(p.base?.bst, bstLines, p.speciesName) }) },
                        routeVersion = state.routeVersion,
                        generation = generation,
                        markMove = MoveRule.gbaMark(moveRules, p, state))
                }
                if (enemy != null) {
                    EnemyCard(onMoveHistory = onMoveHistory, onTypeDefenses = onTypeDefenses, enemy, revealedEnemyAbility, revealedEnemyAbility2, spriteFor,
                        enemyMarks, onCycleMark, movesSeenRunWide, moveRowFor,
                        lastSeenLevel = enemyLastSeenLevel,
                        isWild = state.isWildBattle,
                        encounters = enemyEncounters,
                        routeName = routeName,
                        // Only FireRed and LeafGreen, and only where the place has pictures: placeFor answers null otherwise (2026-09-29).
                        mapMark = FrlgPictures.placeFor(state.badgeSet, state.mapId)?.let { place ->
                            val mark: @Composable () -> Unit = { FrlgMapMark(place, compact = true) }
                            mark
                        },
                        onRouteDetails = if (state.encounterArea != null && routeArea == state.encounterArea) { { routeInfoOpen = true } } else null,
                        onPokemonInfo = if (onSpeciesBase != null) { { speciesInfo = enemy.species } } else null,
                        team = state.enemyTeam,
                        teamLabel = "Team:".takeIf { generation < 3 },
                        enemyParty = state.enemyParty,
                        routeVersion = state.routeVersion,
                        moveLevels = onMoveLevels?.invoke(enemy.species) ?: emptyList(),
                        // Against your Pokemon shown, or your other one once it has fainted (GbaViewState.foeTarget).
                        moveCtx = enemyMoveContext(enemy, view.foeTarget(state), state.weather, onWeight)
                            .copy(hide = InfoRules.hiddenMoveInfo(state.randomized), hideEffectiveness = InfoRules.hideEffectiveness(state.randomized, state.isGhostBattle, own = false), generation = generation, natDex = natDex, maxDex = maxDex),
                        rand = state.randomized,
                        catchText = state.catchPercent?.takeIf { state.isWildBattle && TrackerOptions.showCatchRate }?.let { pct ->
                            // DataHelper.lua:402-406 works it from the viewed Pokemon, the blank stand-in while hidden.
                            "~ ${if (hideStats) 0 else pct}%  to catch" },
                        onCatchTap = onCatchRates,
                        // DataHelper.lua:144: the viewed Pokemon is hidden, the opponent included.
                        hidden = hideStats,
                        onAbilityLine = { line ->
                            // canShowUnknownAbilities: the species' two possible abilities;
                            // otherwise the tracked ones. Nothing there opens the notepad.
                            val name = if (InfoRules.canShowAbilities(state.randomized))
                                enemy.abilityGuess.split(" / ").getOrNull(line - 1)?.trim()
                            else if (line == 1) revealedEnemyAbility else revealedEnemyAbility2
                            if (name.isNullOrBlank() || name == "?" || name == "-" || name == "---") onEditNote()
                            else info = Triple(name, "Ability", onAbilityDescription?.invoke(name))
                        },
                        onMoveInfo = { mv ->
                            moveInfo = detailOf(mv, onMoveDescription?.invoke(mv.id), noRomData = moveRowFor(mv.id)?.let { it.pp == 0 && (it.power ?: 0) == 0 } == true, gen1 = generation == 1, natDex = natDex)
                        },
                        iconDex = WalkingPals.trackerDex(generation, speciesTotal, maxDex),
                        bstLines = bstLines,
                        onBstTap = { bstSheet = Triple(enemy.base?.bst ?: 0, bstLines?.wild ?: 0, null) },
                        generation = generation)
                }
                // The PC tracker's fourth area: one rotating strip, not a stack
                // of permanent rows.
                PcCarousel(
                    inBattle = state.inBattle,
                    viewingOwn = !state.inBattle || viewingOwn,
                    isWildBattle = state.isWildBattle,
                    leadLevel = state.lead?.mon?.level ?: 0,
                    routeTrainersDefeated = state.routeTrainersDefeated,
                    badges = state.badges,
                    badgeSet = state.badgeSet,
                    note = enemyNote,
                    onEditNote = onEditNote,
                    lastAttack = state.lastAttackMove?.takeIf { TrackerOptions.showLastDamage }?.let { mv ->
                        com.ironmonone.tracker.LastAttack.text(mv, state.lastAttackDamage, state.lastAttackTeams)
                    },
                    // The Pokemon on the field the view shows (Battle.getViewedPokemon(true), TrackerScreen.lua:742).
                    lastAttackLethal = com.ironmonone.tracker.LastAttack.lethal(state.lastAttackDamage, view.own(state)?.mon?.curHp),
                    battleDetailsSummary = BattleSummary.line(state.battleSummaries, view.viewedBattler(state)),
                    routeName = routeName,
                    routeSeen = routeSeen,
                    routeTotal = routeTotal,
                    routeArea = routeArea,
                    routeTrainers = routeTrainers,
                    routeBosses = routeBosses,
                    steps = steps,
                    pedometerAllowed = state.mapId != null && state.gameOver == null,
                    onRouteTap = { routeInfoOpen = true },
                    onTrainersTap = onTrainersOnRoute,
                    onBattleDetailsTap = onBattleDetails,
                    // Calc Atk is the Gen 3 tracker's extension and uses the Gen 3 formula: not on a Game Boy game.
                    onLastAttackTap = onCalcAtk?.takeIf { generation >= 3 && state.inBattle && (state.isWildBattle || !TrackerOptions.calcAtkWildOnly) },
                )
            }
        }

    }
    }
}


/**
 * The ability a card's ability line opens, or null for none: the Game Boy trackers write "-" there, and a tap opened a
 * "-" ability card (rc32 audit P3 #43). The opponent's lines already send these to the notepad.
 */
internal fun abilityTapName(name: String): String? = name.trim().takeIf { it.isNotEmpty() && it != "-" && it != "---" }

/**
 * A move popup's contents from a row and its description. The chart lines
 * are general facts about a damaging move's type - never about the opponent,
 * which is the player's to work out (Blake, 2026-09-05).
 */
internal fun detailOf(mv: PcMove, summary: String?, noRomData: Boolean = false, gen1: Boolean = false, natDex: Boolean = false): MoveDetail =
    MoveDetail(
        name = mv.name, typeId = mv.type.takeUnless { noRomData }, typeName = mv.typeName.takeUnless { noRomData },
        category = mv.category.takeUnless { noRomData }, contact = mv.contact,
        noRomData = noRomData,
        pp = mv.pp, ppMax = mv.ppMax, power = mv.power, acc = mv.acc,
        priority = mv.priority, summary = summary,
        // Red, Blue and Yellow: the Gen 1 tracker's chart, as its move rows use.
        // Only with "Type matchups in move info" on (TrackerOptions.showTypeMatchups, off by default).
        typeChart = if (TrackerOptions.showTypeMatchups && (mv.power ?: 0) > 0) MoveMatchup.general(mv.type, gen1, natDex) else null,
        // Your own Hidden Power's chart follows its type arrows (MoveInfoText.chart), so it keeps what that needs.
        chartShown = TrackerOptions.showTypeMatchups && (mv.power ?: 0) > 0, natDex = natDex,
        powerText = mv.powerText,
        // What the row hides stays hidden on the card (MoveInfoText).
        ppText = mv.ppText, accText = mv.accText,
        banLine = mv.banLine,
    )
