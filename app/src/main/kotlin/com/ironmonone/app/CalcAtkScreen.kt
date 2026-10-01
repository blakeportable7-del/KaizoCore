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
 * [enemyStats] is the enemy's real ATK and SPA, used only as the extension uses them: its
 * "*" beside the estimate when neither is within 10% of it (LabelConfidence), which says the
 * formula is missing a multiplier. Null skips that check, as the extension does without an enemy.
 */
@Composable
fun CalcAtkDialog(fill: CalcAtk.Fill?, enemyStats: Pair<Int, Int>?, onClose: () -> Unit) {
    val empty = CalcAtk.Inputs(level = 0, damage = 0, defense = 0, power = 0)
    var inputs by remember { mutableStateOf(fill?.inputs ?: empty) }
    var editing by remember { mutableStateOf<String?>(null) }
    val estimate = CalcAtk.estimate(inputs)
    val found = CalcAtk.found(estimate)
    // LabelConfidence.checkAccuracyOfCalc: 10% either side of the estimate.
    val confident = enemyStats == null || !found || run {
        val lo = estimate.first - estimate.first * 0.1
        val hi = estimate.second + estimate.second * 0.1
        enemyStats.first.toDouble() in lo..hi || enemyStats.second.toDouble() in lo..hi
    }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 520.dp).background(Pc.Page).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText("ATTACKING DAMAGE CALC.", 9, Pc.Text, Modifier.weight(1f))
                PcTap("X", 9, Pc.Dim, spoken = "Close") { onClose() }
            }
            // The estimate: the low roll's stat in the negative colour over the high roll's.
            Row(
                Modifier.fillMaxWidth().border(1.dp, Pc.Border).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    PixText("Attack stat estimate", 8, Pc.Dim)
                    if (found) {
                        PixText("${estimate.first}" + if (!confident) " *" else "", 12, Pc.Negative)
                        PixText("${estimate.second}", 12, Pc.Positive)
                    } else PixText("---", 12, Pc.Dim)
                }
                PcSmallButton("CLEAR") { inputs = empty }
            }
            if (fill?.guessed == true) PixText("The move's power is a guess.", 7, Pc.Dim, wrap = true)
            if (!confident) PixText("* Something in the formula is missing, so this is off.", 7, Pc.Dim, wrap = true)

            @Composable
            fun value(key: String, label: String, text: String) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { editing = key }.padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixText(label, 8, Pc.Text, Modifier.weight(1f))
                    PixText(text, 8, Pc.Gold)
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
                PixText("Edit value", 10, Pc.Text)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Pc.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PcSmallButton("SAVE") {
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
                    Spacer(Modifier.width(4.dp))
                    PcSmallButton("CANCEL") { editing = null }
                }
            }
        }
    }
}

/**
 * Calc Atk's auto-fill from the tracker's last read (CalcAtk.lua autoApplyValues): the move the
 * enemy last used and the damage it did (DamageWatch), your lead, the enemy on the field and the
 * battle weather. Null when there is no hit to work from.
 */
fun calcAtkFill(t: com.ironmonone.tracker.GbaTracker, s: com.ironmonone.tracker.TrackerState): CalcAtk.Fill? {
    val id = s.lastAttackMoveId.takeIf { it > 0 } ?: return null
    val row = t.moveRowFor(id) ?: return null
    val own = s.party.firstOrNull() ?: return null
    val enemy = s.enemy ?: return null
    return CalcAtk.autoFill(
        moveId = id, power = com.ironmonone.tracker.MoveRules.basePower(id, row.power), type = row.type, category = row.category,
        damage = s.lastAttackDamage,
        ownTypes = listOfNotNull(own.base?.type1, own.base?.type2), ownDef = own.mon.def, ownSpd = own.mon.spDef,
        ownWeightKg = t.weight(own.mon.species)?.toDoubleOrNull(),
        enemyTypes = listOf(enemy.type1, enemy.type2), enemyLevel = enemy.level,
        enemyCurHp = enemy.curHp, enemyMaxHp = enemy.maxHp, enemyBurned = enemy.statusCondition == "BRN",
        enemyBaseFriendship = enemy.base?.baseFriendship, wild = s.isWildBattle, weatherWord = s.weatherWord,
    )
}
