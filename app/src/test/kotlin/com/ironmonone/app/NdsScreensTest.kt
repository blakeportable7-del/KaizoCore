package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals

class NdsScreensTest {
    @Test
    fun `every stored layout name maps to a value the classic core accepts`() {
        val accepted = setOf("Top/Bottom", "Bottom/Top", "Left/Right", "Right/Left", "Top Only", "Bottom Only", "Hybrid Top", "Hybrid Bottom")
        for (l in PadLayout.DS_LAYOUTS) assertEquals(true, NdsScreens.classicName(l) in accepted, l)
        assertEquals("Left/Right", NdsScreens.classicName("left-right"))
        assertEquals("126", NdsScreens.classicGap(128)); assertEquals("0", NdsScreens.classicGap(0))
    }

    /**
     * rc32 audit P2 #34: the picker offered "Rotated left" and "Rotated right", which the classic core cannot draw, so
     * both showed the stacked layout. Every choice it offers now draws something of its own.
     */
    @Test
    fun `every arrangement the picker offers is a different picture`() {
        val values = NdsScreens.choices.map { NdsScreens.classicName(it) }
        assertEquals(values.size, values.toSet().size, "two choices give the core the same layout: ${NdsScreens.choices.zip(values)}")
        assertEquals(false, NdsScreens.choices.any { it.startsWith("rotate-") })
        assertEquals(8, NdsScreens.choices.size)
        // A layout saved with a rotated arrangement still loads, and the picker shows it as the stacked one it draws.
        assertEquals(true, "rotate-left" in PadLayout.DS_LAYOUTS && "rotate-right" in PadLayout.DS_LAYOUTS)
        assertEquals("top-bottom", NdsScreens.shownAs("rotate-left"))
        assertEquals("top-bottom", NdsScreens.shownAs("rotate-right"))
        assertEquals("hybrid-top", NdsScreens.shownAs("hybrid-top"))
        assertEquals(null, NdsScreens.shownAs(null))
        val dialog = java.io.File("src/main/kotlin/com/ironmonone/app/SideScreens.kt").readText()
            .substringAfter("fun DsScreensDialog(").substringBefore("\n}\n")
        assertEquals(true, "(listOf<String?>(null) + NdsScreens.choices).forEach" in dialog, "the dialog lists the choices")
        assertEquals(true, "ShellRadio(k == NdsScreens.shownAs(current))" in dialog)
    }

    @Test
    fun `a phone column goes side by side, a tablet column stacks, no size is side by side`() {
        assertEquals("left-right", NdsScreens.autoLayout(550, 360))
        assertEquals("left-right", NdsScreens.autoLayout(640, 400))
        // Blake's tablet, 2026-09-08: a 1480x1509 px column beside the tracker.
        assertEquals("top-bottom", NdsScreens.autoLayout(1480, 1509))
        assertEquals("top-bottom", NdsScreens.autoLayout(800, 900))
        assertEquals("left-right", NdsScreens.autoLayout(0, 0))
    }
}
