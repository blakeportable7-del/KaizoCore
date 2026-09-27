package com.ironmonone.app.gen3

import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.R

/**
 * Gen 3 skin (docs/DESIGN_GEN3_UI.md): usability first, then the costume.
 *
 * The Emerald text box is drawn procedurally - a dark frame, a light bevel, paper
 * fill - because that IS the real frame's geometry; no ripped assets needed. Pixel
 * font is Press Start 2P (OFL, license bundled in assets), used ONLY for headers and
 * labels at readable sizes; body text stays the system face, which is the design
 * doc's readability rule, not a compromise.
 */
object Gen3 {
    // Palette per the design doc, sampled from Emerald's UI family.
    val FrameDark = Color(0xFF2E3036)
    val FrameBevel = Color(0xFF34363C)
    val Paper = Color(0xFF1C1D22)
    val Ink = Color(0xFFECEDEE)
    val InkShadow = Color(0xFF5A5D66)
    val Emerald = Color(0xFF40A058)
    val EmeraldDeep = Color(0xFF2E7842)
    val HpGreen = Color(0xFF58D080)
    val HpAmber = Color(0xFFF8B050)
    val HpRed = Color(0xFFF05838)
    val ExpBlue = Color(0xFF40C8F8)
    val Sky = Color(0xFFA9D8F8)
    val MenuBlue = Color(0xFF3050A8)   // the save-menu blue family

    // The PC tracker's default theme, so the shell and the tracker agree.
    val PcPage = Color(0xFF000000)
    val PcGround = Color(0xFF222222)
    val PcBorder = Color(0xFFAAAAAA)
    val PcText = Color(0xFFFFFFFF)
    val PcGold = Color(0xFFFFFF00)

    val PixelFont = FontFamily(Font(R.font.press_start_2p))

    /**
     * The app shell runs the PC tracker's own palette, not the Emerald green it
     * was first skinned in: black page, grey hairlines, yellow accent. The
     * tracker panel is a faithful copy of the PC tool, and a green shell around
     * it read as two different programs.
     *
     * MenuBlue is deliberately not the secondary any more - it was tinting
     * status lines like "Loaded slot 1." blue, the one colour the PC tracker's
     * theme has no slot for.
     */
    // The modern dark shell (2026-09-27). Material pieces that still read the
    // scheme (text fields, menus) follow the same page, card and accent.
    val Scheme = darkColorScheme(
        primary = Color(0xFFCF3A3F),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFFFF8589),
        onSecondary = Color(0xFF121316),
        background = Color(0xFF121316),
        onBackground = Color(0xFFECEDEE),
        surface = Color(0xFF1C1D22),
        onSurface = Color(0xFFECEDEE),
        surfaceVariant = Color(0xFF2A2C33),
        onSurfaceVariant = Color(0xFFA0A3AD),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF121316),
        outline = Color(0xFF34363C),
    )
}

/**
 * A card: the one container in the app. Rounded, a step lighter than the page,
 * with a hairline so it holds its edge on any screen. Named for the Emerald
 * text box it replaced, so every call site moved with it.
 */
@Composable
fun Gen3Box(
    modifier: Modifier = Modifier,
    paper: Color = Gen3.Paper,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(com.ironmonone.app.Shell.cardRadius)
    Box(
        modifier
            .clip(shape)
            .background(paper)
            .border(1.dp, com.ironmonone.app.Shell.hairline, shape)
            .padding(14.dp),
    ) { content() }
}

/**
 * A button. [accent] is the screen's main action: filled red. Every other
 * button is a quiet raised surface. Labels written in capitals are shown in
 * sentence case (Shell.label).
 */
@Composable
fun Gen3Button(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val S = com.ironmonone.app.Shell
    val face = when {
        !enabled -> Color(0xFF1F2025)
        accent -> if (pressed) S.accentPressed else S.accent
        pressed -> S.raisedPressed
        else -> S.raised
    }
    val shape = RoundedCornerShape(S.controlRadius)
    Box(
        modifier
            // 48dp, Android's minimum touch target.
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(face)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
            ) {
                haptics.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                )
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            S.label(text),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            // A label that wraps stops looking like a button.
            maxLines = 1,
            softWrap = false,
            color = when {
                !enabled -> Color(0xFF6B6E78)
                accent -> S.onAccent
                else -> S.inkOnPaper
            },
        )
    }
}

/** A section label on the page: small, spaced capitals in the hint colour. */
@Composable
fun Gen3Header(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(vertical = 4.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        color = com.ironmonone.app.Shell.hintOnNight,
    )
}
