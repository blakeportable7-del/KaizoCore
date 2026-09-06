package com.ironmonone.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironmonone.app.gen3.Gen3
import com.ironmonone.app.gen3.Gen3Box

/**
 * Shell components. Everything that is not the tracker panel is built from
 * these, so a screen cannot quietly grow its own dialect.
 *
 * The tracker panel is NOT in scope here - it is a clone of the PC tracker and
 * takes its metrics from that reference, not from this file.
 */

/** The phases the randomizer actually has. See [ProgressPanel]. */
enum class RunPhase(val label: String) {
    ROTATING("Preparing files"),
    RANDOMIZING("Randomizing on this phone"),
    FINISHING("Saving run"),
    /** The Prepare screen: patching a supplied ROM into a stored base. */
    PATCHING("Patching ROM"),
}

/**
 * A framed progress panel for the app's long operations.
 *
 * ## Why this is indeterminate, and why the phases are what they are
 *
 * Randomizing takes 25-45s and previously showed a bare progress bar whose
 * only explanation sat at the bottom of a scrolling page, off-screen at the
 * moment it mattered. The screen looked frozen.
 *
 * The engine exposes NO progress callback - `randomize()` is a single blocking
 * call - so there is no honest percentage to show, and inventing one would be
 * a lie that gets less true the longer it runs. What IS real: the caller runs
 * three discrete steps (rotate files, randomize, save), so those are reported
 * as phases, and the elapsed second count is measured rather than guessed.
 *
 * If the engine ever grows a progress callback, this is where it lands.
 */
@Composable
fun ProgressPanel(phase: RunPhase, modifier: Modifier = Modifier) {
    var seconds by remember(phase) { mutableIntStateOf(0) }
    LaunchedEffect(phase) {
        seconds = 0
        while (true) {
            kotlinx.coroutines.delay(1000)
            seconds += 1
        }
    }
    Gen3Box(modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    phase.label.uppercase(),
                    fontFamily = Gen3.PixelFont,
                    fontSize = 10.sp,
                    color = Shell.inkOnPaper,
                )
                Text(
                    "${seconds}s",
                    fontFamily = Gen3.PixelFont,
                    fontSize = 10.sp,
                    color = Shell.hintOnPaper,
                )
            }
            Spacer(Modifier.height(8.dp))
            IndeterminateBar()
            if (phase == RunPhase.RANDOMIZING) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Twenty to forty seconds is normal. Leaving this screen is fine; " +
                        "closing the app is not.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Shell.hintOnPaper,
                )
            }
        }
    }
}

/**
 * A hard-edged sweeping bar, in the shell's language rather than Material's.
 *
 * Motion only - it carries no information, so a device that refuses to animate
 * loses nothing but the sweep. The panel's phase label and second count are
 * plain text and never depend on an animation running.
 */
@Composable
private fun IndeterminateBar() {
    val t = rememberInfiniteTransition(label = "bar")
    val x by t.animateFloat(
        initialValue = -0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    // The sweeping segment must be its OWN node. Putting the background on the
    // node whose size the layout block expands painted the full width solid -
    // a "progress bar" that was always 100%, which is worse than none.
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().height(6.dp).background(Gen3.InkShadow),
    ) {
        val track = maxWidth
        Box(
            Modifier
                .offset(x = track * x)
                .width(track * 0.35f)
                .height(6.dp)
                .background(Shell.inkOnPaper),
        )
    }
}

/**
 * The outcome of an action, shown where the action was taken.
 *
 * The randomizer's "New run ready (seed ...)" line used to render at the very
 * bottom of a scrolling column - below the fold exactly when it mattered, so
 * the primary action of the app appeared to do nothing. This is anchored by
 * its caller instead, next to the button that caused it.
 */
@Composable
fun StatusBanner(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    Gen3Box(modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) Shell.dangerOnPaper else Shell.inkOnPaper,
        )
    }
}

// ---------------------------------------------------------------------------
// Selection, rows, dialogs and status: the shell's replacements for the stock
// Material widgets that were showing up on RUN, KEYS and LIBRARY.
// ---------------------------------------------------------------------------

/**
 * A selector in the shell's language: a hard square that fills in, not a
 * Material circle with a ripple.
 *
 * Deliberately NOT clickable itself - the whole row is the target, which is
 * both easier to hit and the behaviour the rows already had.
 */
@Composable
fun ShellRadio(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(18.dp)
            .background(Shell.frame)
            .padding(2.dp)
            .background(Shell.paper),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(8.dp).background(Shell.inkOnPaper))
        }
    }
}

/**
 * One row of a settings list on paper: label left, value right, whole row
 * tappable and at least [Shell.touchTarget] tall.
 */
@Composable
fun ShellListRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    valueColor: Color = Shell.inkOnPaper,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .heightIn(min = Shell.touchTarget)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Shell.inkOnPaper)
        Text(
            value,
            fontFamily = Gen3.PixelFont,
            fontSize = 10.sp,
            color = valueColor,
        )
    }
}

/** A hairline on paper. */
@Composable
fun ShellDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Gen3.InkShadow))
}

/**
 * The shell's busy indicator: the same sweeping bar the progress panel uses,
 * so a spinner never appears in an app that has no other circles in it.
 */
@Composable
fun ShellBusy(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 12.dp)) { IndeterminateBar() }
}

/**
 * A modal in shell language - a framed paper panel, not a Material AlertDialog
 * with rounded corners and a tonal scrim.
 */
@Composable
fun ShellDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Gen3Box(Modifier.fillMaxWidth()) {
            Column {
                Text(
                    title.uppercase(),
                    fontFamily = Gen3.PixelFont,
                    fontSize = 11.sp,
                    color = Shell.inkOnPaper,
                )
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

/**
 * An empty state: a headline and an explanation, on paper.
 *
 * These used to be loose grey text on the black page, measured at 2.03:1 -
 * which reads as a disabled control rather than as the app telling you
 * something. The copy was always good; it just looked switched off.
 */
@Composable
fun EmptyState(headline: String, detail: String, modifier: Modifier = Modifier) {
    Gen3Box(modifier.fillMaxWidth()) {
        Column {
            Text(
                headline.uppercase(),
                fontFamily = Gen3.PixelFont,
                fontSize = 10.sp,
                color = Shell.inkOnPaper,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Shell.inkOnPaper,
            )
        }
    }
}

/**
 * A full-screen scene behind a tab's content.
 *
 * ## The rule that makes this safe
 *
 * Screen-filling artwork is the fastest way to wreck a contrast sweep, so this
 * component owns the guarantee rather than trusting each screen: the scene is
 * always drawn under a SCRIM, and the app's readable text sits on opaque paper
 * cards which the scrim cannot affect at all.
 *
 * That ordering is why this is possible now and would not have been before
 * M2 - de-Materializing moved essentially every string in the shell onto a
 * card. Exactly one place still paints text straight onto the page.
 *
 * The art is pixel art, so it is upscaled with NO filtering: a 256x160 scene
 * blown up to a phone should look like big honest pixels, not a blurred
 * photograph. [scrim] is measured, not eyeballed - see the contrast check in
 * the plan's verification protocol.
 */
@Composable
fun ScreenBackground(
    asset: String?,
    modifier: Modifier = Modifier,
    scrim: Float = 0.62f,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().background(Shell.night)) {
        if (asset != null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val bmp = remember(asset) { PcAssets.background(context, asset) }
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                )
                Box(
                    Modifier.fillMaxSize()
                        .background(Shell.night.copy(alpha = scrim)),
                )
            }
        }
        content()
    }
}

/** A square check in the shell's language. Row-level click, like ShellRadio. */
@Composable
fun ShellCheck(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(18.dp).background(Shell.frame).padding(2.dp).background(Shell.paper),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                "x",
                fontFamily = Gen3.PixelFont,
                fontSize = 10.sp,
                color = Shell.inkOnPaper,
            )
        }
    }
}

/**
 * A small segmented choice, for enums with only a couple of values.
 *
 * The editor rendered EVERY enum as a stacked radio list, so a two-value
 * choice ate ~230px of a phone screen and a long one ate the whole screen.
 * Short lists go inline; longer ones get a dialog (see the editor).
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ShellSegmented(
    values: List<String>,
    selected: String,
    label: (String) -> String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // FlowRow, not Row. A Row silently CLIPS chips past the right edge: they
    // are not drawn and cannot be tapped.
    //
    // The caller only picks this control when it thinks the labels fit, but it
    // decides that by COUNTING CHARACTERS, which is not a width. At the
    // system font scale of 1.3 that estimate is wrong: Unchanged/Shuffle/
    // Random measures 24 characters, passes the check, and overflows. Verified
    // on a device - the chips wrap to a second line here, so under a Row
    // "Random" was clipped and unselectable for anyone using large text.
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        values.forEach { v ->
            val on = v == selected
            Box(
                Modifier
                    .background(Shell.frame)
                    .padding(2.dp)
                    .background(if (on) Shell.inkOnPaper else Shell.paper)
                    .clickable { onSelect(v) }
                    .heightIn(min = 34.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(v),
                    fontFamily = Gen3.PixelFont,
                    fontSize = 9.sp,
                    color = if (on) Shell.paper else Shell.inkOnPaper,
                )
            }
        }
    }
}

/** A -/+ stepper for an integer setting. */
@Composable
fun ShellStepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepKey("-") { onChange((value - 1).coerceIn(range)) }
        Text(
            "$value",
            fontFamily = Gen3.PixelFont,
            fontSize = 11.sp,
            color = Shell.inkOnPaper,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        StepKey("+") { onChange((value + 1).coerceIn(range)) }
    }
}

@Composable
private fun StepKey(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(Shell.touchTarget)
            .background(Shell.frame).padding(2.dp).background(Shell.paper)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontFamily = Gen3.PixelFont, fontSize = 12.sp, color = Shell.inkOnPaper)
    }
}
