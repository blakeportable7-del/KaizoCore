package com.ironmonone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The tracker's scrolling column, with a sign that there is more below (Blake, 2026-10-02, on whether the docked tracker
 * scrolls: it did, and nothing said so). A small arrow sits at the foot while the column can scroll further; a tap on
 * it scrolls most of a screen down. Only the arrow takes touches, so a swipe anywhere scrolls as before.
 */
@Composable
internal fun TrackerScroll(modifier: Modifier, background: Color? = Pc.Page, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().then(if (background != null) Modifier.background(background) else Modifier).verticalScroll(scroll),
            content = content,
        )
        if (scroll.canScrollForward) MoreBelow(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)) {
            scope.launch { scroll.animateScrollTo((scroll.value + scroll.viewportSize * 3 / 4).coerceAtMost(scroll.maxValue)) }
        }
    }
}

/** A down arrow on a dark disc, readable over any tracker theme or player image. */
@Composable
private fun MoreBelow(modifier: Modifier, onClick: () -> Unit) {
    // The tracker's touch box (PcMin.TOUCH_DP) around a smaller disc.
    Box(
        modifier.size(PcMin.TOUCH_DP.dp)
            .clickable(role = Role.Button) { onClick() }
            .semantics { contentDescription = "More below. Scroll down" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(width = 12.dp, height = 7.dp)) {
                val w = 2.dp.toPx()
                drawLine(Color.White, Offset(0f, 0f), Offset(size.width / 2f, size.height), strokeWidth = w, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(size.width, 0f), Offset(size.width / 2f, size.height), strokeWidth = w, cap = StrokeCap.Round)
            }
        }
    }
}
