package com.ironmonone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ironmonone.app.gen3.Gen3Button

/**
 * The game over lines, from the tracker's gear (2026-09-30): the switch, whether the built-in lines stay in, and the
 * player's own list to add to, edit and remove from. Every change is kept as it is made (DeathQuotes), so there is no
 * Save to forget. It is drawn as the tracker's own screens are, in the tracker's palette, and says nothing that
 * DeathQuotesCopy does not hold. It lives in its own file because PlayScreen has no room for it.
 */
@Composable
fun DeathQuotesDialog(onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    /** The line being changed, or null while a new one is being typed. */
    var editing by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val own = DeathQuotes.lines

    fun submit() {
        val at = editing
        val result = if (at == null) DeathQuotes.add(draft) else DeathQuotes.replace(at, draft)
        message = DeathQuotesCopy.message(result)
        if (result == DeathQuotes.Edit.OK) { draft = ""; editing = null }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(320.dp).heightIn(max = 560.dp).background(Pc.Ground).border(1.dp, Pc.Border)
                .verticalScroll(rememberScrollState()).padding(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PixText(DeathQuotesCopy.TITLE, 10, Pc.Gold, Modifier.weight(1f))
                PcTap(DeathQuotesCopy.CLOSE_MARK, 9, Pc.Dim, DeathQuotesCopy.CLOSE) { onDismiss() }
            }
            Spacer(Modifier.height(4.dp))
            PixText(DeathQuotesCopy.INTRO, 7, Pc.Dim, wrap = true)
            Spacer(Modifier.height(3.dp))
            PixText(DeathQuotesCopy.LOSSES_ONLY, 7, Pc.Dim, wrap = true)
            Spacer(Modifier.height(6.dp))
            GearToggle(DeathQuotesCopy.USE_MINE, DeathQuotes.enabled) { DeathQuotes.useOwn(it) }
            GearToggle(DeathQuotesCopy.KEEP_BUILT_IN, DeathQuotes.keepBuiltIn) { DeathQuotes.useBuiltIn(it) }
            DeathQuotesCopy.poolNote(DeathQuotes.enabled, DeathQuotes.keepBuiltIn, own.size)?.let {
                Spacer(Modifier.height(2.dp))
                PixText(it, 7, Pc.Gold, wrap = true)
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(DeathQuotes.MAX_LENGTH); message = null },
                singleLine = true,
                textStyle = TextStyle(color = Pc.Text),
                label = { PixText(DeathQuotesCopy.TYPE_A_LINE, 7, Pc.Dim) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            PixText(DeathQuotesCopy.LIMIT, 7, Pc.Dim)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gen3Button(if (editing == null) DeathQuotesCopy.ADD else DeathQuotesCopy.SAVE, accent = true) { submit() }
                if (editing != null) Gen3Button(DeathQuotesCopy.CANCEL) { editing = null; draft = ""; message = null }
            }
            message?.let {
                Spacer(Modifier.height(4.dp))
                PixText(it, 7, Pc.Negative, wrap = true)
            }
            Spacer(Modifier.height(8.dp))
            PixText(DeathQuotesCopy.count(own.size), 8, Pc.Text, wrap = true)
            Spacer(Modifier.height(2.dp))
            own.forEachIndexed { i, line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixText(line, 7, if (editing == i) Pc.Gold else Pc.Text, Modifier.weight(1f), wrap = true)
                    LineAction(DeathQuotesCopy.EDIT, DeathQuotesCopy.spoken(DeathQuotesCopy.EDIT, line)) {
                        editing = i; draft = line; message = null
                    }
                    LineAction(DeathQuotesCopy.REMOVE, DeathQuotesCopy.spoken(DeathQuotesCopy.REMOVE, line)) {
                        DeathQuotes.remove(i)
                        // The line being changed is gone, or a line above it went and it moved up one.
                        val at = editing
                        if (at == i) { editing = null; draft = "" } else if (at != null && at > i) editing = at - 1
                        message = null
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Gen3Button(DeathQuotesCopy.CLOSE) { onDismiss() }
        }
    }
}

/** A word on a line of the list that does something when tapped, in a 48dp touch area and announced with the line it is for. */
@Composable
private fun LineAction(text: String, spoken: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
            .clickable(onClickLabel = spoken) { onClick() }
            .semantics { contentDescription = spoken }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) { PixText(text, 7, Pc.Gold) }
}
