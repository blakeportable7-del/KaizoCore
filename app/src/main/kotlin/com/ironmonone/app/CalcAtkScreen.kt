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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ironmonone.tracker.CalcAtk

/**
 * Calc Atk's screen (UTDZac's "Attacking Damage Calc." extension, v1.2, MIT): the enemy's
 * attacking stat estimated from the damage it just did, low roll over high roll, with every
 * variable of the formula shown and tappable to change, as CalcAtkScreen draws them. [fill] is
 * what the extension fills in by itself (CalcAtk.autoFill); null opens it empty.
 *
 * The extension's low-confidence mark (LabelConfidence) is left out: it compares the estimate with
 * the opponent's real ATK and SPA, which the tracker otherwise keeps hidden, so it is never shown
 * (Blake, 2026-10-02, rc32 audit P3 #22). The dialog is not given those stats at all.
 */
@Composable
fun CalcAtkDialog(fill: CalcAtk.Fill?, onClose: () -> Unit) {
    val empty = CalcAtk.Inputs(level = 0, damage = 0, defense = 0, power = 0)
    var inputs by remember { mutableStateOf(fill?.inputs ?: empty) }
    var editing by remember { mutableStateOf<String?>(null) }
    val estimate = CalcAtk.estimate(inputs)
    val found = CalcAtk.found(estimate)

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 520.dp).background(Pc.Page).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Its words in sp, following the phone's font size, and its rows and buttons 48dp (rc32 audit P2 #19,
            // rc35 follow-up N #29): they were 7 to 12dp text, 40dp rows and the tracker's small buttons.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DialogText("ATTACKING DAMAGE CALC.", 15, Pc.Text, Modifier.weight(1f), heading = true)
                PcTap("X", 9, Pc.Dim, spoken = "Close") { onClose() }
            }
            // The estimate: the low roll's stat in the negative colour over the high roll's.
            Row(
                Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    DialogText("Attack stat estimate", 12, Pc.Dim)
                    if (found) {
                        DialogText("${estimate.first}", 16, Pc.Negative)
                        DialogText("${estimate.second}", 16, Pc.Positive)
                    } else DialogText("---", 16, Pc.Dim)
                }
                GearButton("CLEAR", Modifier) { inputs = empty }
            }
            if (fill?.guessed == true) DialogText("The move's power is a guess.", 12, Pc.Dim)
            // Survival chances (2026-10-06): the same move again, at the attack this worked out and your HP now. Only
            // what this hit showed goes in: the enemy's real stats are never read.
            if (found && fill != null) calcAtkSurvival(inputs, estimate, fill.ownHp, fill.ownMaxHp)?.let {
                DialogText(it, 12, Pc.Text)
                DialogText("Every damage roll counted, and a critical hit 1 time in 16.", 12, Pc.Dim)
            }

            @Composable
            fun value(key: String, label: String, text: String) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = PcMin.DIALOG_TOUCH_DP.dp).clickable(role = Role.Button) { editing = key }.padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DialogText(label, 13, Pc.Text, Modifier.weight(1f))
                    DialogText(text, 13, Pc.Gold)
                }
            }
            fun mult(v: Double) = when (v) { 0.0 -> "0x"; 0.25 -> "1/4x"; 0.5 -> "1/2x"; 1.0 -> "1x"; 2.0 -> "2x"; 4.0 -> "4x"; else -> "${v}x" }
            value("level", "Enemy Pok\u00e9mon:", "Lv.${inputs.level}")
            value("damage", "Damage:", "${inputs.damage}")
            value("defense", "Your DEF/SPD:", "${inputs.defense}")
            value("power", "Move Power:", "${inputs.power}")
            value("effectiveness", "Move Effectiveness:", mult(inputs.effectiveness))
            value("other", "Other Multiplier(s):", mult(inputs.other))
            GearToggle("STAB", inputs.stab) { inputs = inputs.copy(stab = it) }
            GearToggle("Crit", inputs.crit) { inputs = inputs.copy(crit = it) }
            // The extension's weather box has three states: off, boosted, halved.
            GearToggle(
                when (inputs.weather) { CalcAtk.WEATHER_BOOSTED -> "Weather (1.5x)"; CalcAtk.WEATHER_HALVED -> "Weather (0.5x)"; else -> "Weather" },
                inputs.weather != CalcAtk.WEATHER_NONE,
            ) { inputs = inputs.copy(weather = (inputs.weather + 1) % 3) }
            GearToggle("Burned", inputs.burned) { inputs = inputs.copy(burned = it) }
            GearToggle("Screen/Reflect", inputs.screen) { inputs = inputs.copy(screen = it) }
        }
    }

    editing?.let { key ->
        var draft by remember(key) {
            mutableStateOf(when (key) {
                "level" -> inputs.level.toString(); "damage" -> inputs.damage.toString()
                "defense" -> inputs.defense.toString(); "power" -> inputs.power.toString()
                "effectiveness" -> inputs.effectiveness.toString(); else -> inputs.other.toString()
            })
        }
        Dialog(onDismissRequest = { editing = null }) {
            Column(Modifier.background(Pc.Ground).border(1.dp, Pc.Border).padding(12.dp)) {
                DialogText("Edit value", 14, Pc.Text, heading = true)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Pc.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GearButton("SAVE", Modifier.weight(1f)) {
                        // The extension: tonumber(text) or the default, so anything unreadable resets it.
                        val n = draft.trim().toDoubleOrNull()
                        inputs = when (key) {
                            "level" -> inputs.copy(level = n?.toInt() ?: 0)
                            "damage" -> inputs.copy(damage = n?.toInt() ?: 0)
                            "defense" -> inputs.copy(defense = n?.toInt() ?: 0)
                            "power" -> inputs.copy(power = n?.toInt() ?: 0)
                            "effectiveness" -> inputs.copy(effectiveness = n ?: 1.0)
                            else -> inputs.copy(other = n ?: 1.0)
                        }
                        editing = null
                    }
                    GearButton("CANCEL", Modifier.weight(1f)) { editing = null }
                }
            }
        }
    }
}

/**
 * Calc Atk's auto-fill from the tracker's last read (CalcAtk.lua autoApplyValues): the move the
 * enemy last used and the damage it did (DamageWatch), your Pokemon and the enemy the tracker
 * shows (Battle.getViewedPokemon(true) and (false), CalcAtk.lua:241-242) and the battle weather.
 * Null when there is no hit to work from.
 */
fun calcAtkFill(t: com.ironmonone.tracker.GbaTracker, s: com.ironmonone.tracker.TrackerState): CalcAtk.Fill? {
    val id = s.lastAttackMoveId.takeIf { it > 0 } ?: return null
    val row = t.moveRowFor(id) ?: return null
    // The Pokemon that was hit: slot 1 is not it after a switch (rc33 audit P1), and in a double battle it is the one of
    // yours the view shows (GbaViewState), as the opponent is.
    val own = gbaView.own(s) ?: return null
    val enemy = gbaView.foe(s) ?: return null
    return calcAtkAutoFill(t, s, id, row, own, enemy)?.copy(ownHp = own.mon.curHp, ownMaxHp = own.mon.maxHp)
}

private fun calcAtkAutoFill(
    t: com.ironmonone.tracker.GbaTracker, s: com.ironmonone.tracker.TrackerState, id: Int, row: com.ironmonone.tracker.MoveRow,
    own: com.ironmonone.tracker.TrackedMon, enemy: com.ironmonone.tracker.EnemyInfo,
): CalcAtk.Fill? {
    return CalcAtk.autoFill(
        moveId = id, power = com.ironmonone.tracker.MoveRules.basePower(id, row.power), type = row.type, category = row.category,
        damage = s.lastAttackDamage,
        ownTypes = typesOf(own), ownDef = own.mon.def, ownSpd = own.mon.spDef,
        ownWeightKg = t.weight(own.mon.species)?.toDoubleOrNull(),
        enemyTypes = typesOf(enemy), enemyLevel = enemy.level,
        enemyCurHp = enemy.curHp, enemyMaxHp = enemy.maxHp, enemyBurned = enemy.statusCondition == "BRN",
        enemyBaseFriendship = enemy.base?.baseFriendship, wild = s.isWildBattle, weatherWord = s.weatherWord,
        natDex = t.expandedSpeciesIds,
        maxDex = t.nameSet == "maxdex",
        weatherName = s.weather,
    )
}
