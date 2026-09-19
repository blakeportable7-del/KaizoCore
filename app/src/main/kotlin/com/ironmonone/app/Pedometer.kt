package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Program.Pedometer: the game's step statistic, less the count at the last
 * "Reset", and an optional goal. Like the reference, the goal and the reset
 * live only in memory and start over with the tracker.
 */
internal object Pedometer {
    var lastResetCount by mutableStateOf(0)
    var goalSteps by mutableStateOf(0)

    fun current(total: Int): Int = maxOf(total - lastResetCount, 0)

    /** PedometerReset: "Reset" while steps are counted since a reset, "Total" to go back to the game's total. */
    fun buttonLabel(total: Int): String = if (current(total) <= 0) "Total" else "Reset"

    fun press(total: Int) {
        if (buttonLabel(total) == "Reset") lastResetCount = total else lastResetCount = 0
    }
}

/** Constants.PixelImages.CLOCK, 10 by 10. */
private val CLOCK = listOf(
    "0011111100",
    "0100000010",
    "1000100001",
    "1000100001",
    "1000100001",
    "1000111001",
    "1000000001",
    "1000000001",
    "0100000010",
    "0011111100",
)

/**
 * The PEDOMETER carousel item: the clock, "Steps: 1,234" (capped at 999,999,
 * green once the goal is reached), and the Goal and Reset/Total buttons.
 */
@Composable
internal fun PcPedometerLine(totalSteps: Int) {
    var editing by remember { mutableStateOf(false) }
    val steps = Pedometer.current(totalSteps).coerceAtMost(999999)
    val reached = Pedometer.goalSteps != 0 && steps >= Pedometer.goalSteps
    Row(
        Modifier.fillMaxWidth().background(Pc.Ground).border(1.dp, Pc.Border)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PcPixelImage(CLOCK, Pc.Text)
        Spacer(Modifier.width(6.dp))
        PixText("Steps: " + "%,d".format(steps), 8, if (reached) Pc.Positive else Pc.Text, Modifier.weight(1f))
        PcBoxButton("Goal", if (Pedometer.goalSteps == 0) Pc.Text else Pc.Gold) { editing = true }
        Spacer(Modifier.width(4.dp))
        PcBoxButton(Pedometer.buttonLabel(totalSteps), Pc.Text) { Pedometer.press(totalSteps) }
    }
    if (editing) StepGoalDialog { editing = false }
}

@Composable
private fun PcBoxButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    PixText(label, 8, color,
        Modifier.border(1.dp, Pc.Border).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp))
}

/** TrackerScreen.openEditStepGoalWindow, in the reference's words. */
@Composable
private fun StepGoalDialog(onDone: () -> Unit) {
    var text by remember { mutableStateOf(Pedometer.goalSteps.toString()) }
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Pc.Ground,
        title = { PixText("Choose a Step Goal", 10, Pc.Gold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PixText("Pedometer will change color when your goal is reached.", 8, Pc.Text)
                PixText("[Set to 0 to turn off]", 8, Pc.Dim)
                PixText("How many steps to reach your goal?", 8, Pc.Text)
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(7) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                text.toIntOrNull()?.let { Pedometer.goalSteps = it }
                onDone()
            }) { PixText("Save", 8, Pc.Text) }
        },
        dismissButton = { TextButton(onClick = onDone) { PixText("Cancel", 8, Pc.Text) } },
    )
}
