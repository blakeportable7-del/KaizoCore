package com.ironmonone.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The frame the tracker's info popups share (a move, an ability, a name),
 * redrawn 2026-09-15 on Blake's "less wasted space, larger text, better
 * design". The old boxes took the dialog's full width with 8-unit text and a
 * CLOSE button on its own row; this one is at most 440dp wide, the title is in
 * the app's pixel face with the X beside it, and the text below is 12 to 16.
 */
@Composable
fun InfoCard(
    title: String,
    onDismiss: () -> Unit,
    titleColor: Color = Pc.Gold,
    /** Beside the title, before the X: the move's type, a category tag. */
    headerExtra: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.widthIn(max = 440.dp).fillMaxWidth().heightIn(max = 460.dp)
            .background(Pc.Ground).border(2.dp, Pc.Border),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Pc.Page).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont, fontSize = 13.sp, color = titleColor,
                maxLines = 2, modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            headerExtra()
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(34.dp).border(1.dp, Pc.Border).clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) { Text("X", fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont, fontSize = 12.sp, color = Pc.Text) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pc.Border.copy(alpha = 0.5f)))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp), content = content)
    }
}

/** [InfoCard] in its own window, sized by the card rather than the platform's dialog width. */
@Composable
fun InfoSheet(
    title: String,
    onDismiss: () -> Unit,
    titleColor: Color = Pc.Gold,
    headerExtra: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            InfoCard(title, onDismiss, titleColor, headerExtra, content)
        }
    }
}

/** A type's icon at 1.5x the tracker's 30x12, for the popups; a coloured tag for a type with no icon. */
@Composable
fun InfoTypeTag(typeName: String, color: Color) {
    val ctx = LocalContext.current
    val icon = remember(typeName) { PcAssets.typeIcon(ctx, typeName) }
    if (icon != null) {
        Image(icon, typeName, Modifier.width(45.dp).height(18.dp), filterQuality = FilterQuality.None)
    } else {
        Box(Modifier.width(45.dp).height(18.dp).background(color), contentAlignment = Alignment.Center) {
            PixText(typeName.uppercase(), 9, Color.White)
        }
    }
}

/** A small outlined tag in the header: "PHYSICAL", "SPECIAL", "STATUS". */
@Composable
fun InfoTag(label: String, color: Color) {
    Box(Modifier.border(1.dp, color).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(label, fontFamily = com.ironmonone.app.gen3.Gen3.PixelFont, fontSize = 8.sp, color = color)
    }
}

/** One figure of a strip: the value large, its label under it. */
@Composable
fun RowScope.InfoStat(label: String, value: String, valueColor: Color = Pc.Text) {
    Column(
        Modifier.weight(1f).background(Pc.Page).border(1.dp, Pc.Border.copy(alpha = 0.6f)).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixText(value, 16, valueColor)
        Spacer(Modifier.height(4.dp))
        PixText(label, 10, Pc.Dim)
    }
}

/** A labelled paragraph: the label in the accent, the text at 13. */
@Composable
fun InfoParagraph(label: String?, text: String, textColor: Color = Pc.Text) {
    if (label != null) {
        PixText(label, 11, Pc.Gold)
        Spacer(Modifier.height(4.dp))
    }
    PixText(text, 13, textColor, wrap = true)
}
