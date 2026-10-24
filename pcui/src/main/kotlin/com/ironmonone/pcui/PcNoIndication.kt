package com.ironmonone.pcui

/**
 * No pressed or hovered look on anything clickable (LocalIndication): Compose's default without a theme greys a control
 * while it is pressed or hovered, and the panel's click is a press and a release at a spot the scene then keeps hovering,
 * so the clicked box stayed tinted in the next picture and the tint's animation asked for a second drawing of every click
 * (2026-10-07). besteon's buttons have no pressed look either.
 */
internal object PcNoIndication : androidx.compose.foundation.Indication {
    private object Plain : androidx.compose.foundation.IndicationInstance {
        override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawIndication() { drawContent() }
    }

    @androidx.compose.runtime.Composable
    override fun rememberUpdatedInstance(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.foundation.IndicationInstance = Plain
}
