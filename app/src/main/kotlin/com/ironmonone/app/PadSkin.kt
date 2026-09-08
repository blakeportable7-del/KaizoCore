package com.ironmonone.app

import java.io.File

/**
 * How the on-screen controls are drawn. CLASSIC is the app's own framed
 * pixel look. MODERN is what the store emulators draw: round A and B, a
 * disc d-pad with a cross, pill Select and Start, capsule shoulders, soft
 * translucency and a brighter fill while pressed. Positions and sizes come
 * from PadLayout either way; the skin only changes the paint.
 */
enum class PadSkin(val label: String) {
    CLASSIC("Classic"), MODERN("Modern"),
    /** 2.1: outlined shapes on a clear ground, the look of My Boy!'s controls, drawn here. */
    OUTLINE("Outline");

    fun next(): PadSkin = entries[(ordinal + 1) % entries.size]

    companion object {
        /** No file, or an unknown name, is the OUTLINE skin: the emulator look is the default since 2026-09-08. */
        fun parse(s: String?): PadSkin = entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: OUTLINE
        fun load(f: File): PadSkin = parse(runCatching { f.readText() }.getOrNull())
        fun save(f: File, skin: PadSkin) { runCatching { f.parentFile?.mkdirs(); f.writeText(skin.name) } }
    }
}
